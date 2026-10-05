package dev.flame.chunkguard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// strips items / block entities whose encoded size could push a packet past the client's 2 MB
// frame limit and disconnect everyone who loads the chunk
public class ChunkGuardMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("ChunkGuard");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CHECK_INTERVAL_TICKS = 20;

    public static boolean enabled = true;
    public static int maxItemBytes = 65536;
    public static int maxBlockEntityBytes = 32768;
    public static int maxChunkBlockEntityBytes = 1048576;

    // item encoding needs the server's registries
    private static volatile DynamicRegistryManager registries;
    private static final Map<String, Long> LAST_LOG = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        loadConfig();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> registries = server.getRegistryManager());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            registries = null;
            LAST_LOG.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % CHECK_INTERVAL_TICKS != 0) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                enforce(player);
            }
        });
    }

    private static void loadConfig() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        Path file = dir.resolve("chunkguard.json");
        try {
            JsonObject cfg = new JsonObject();
            if (Files.exists(file)) {
                cfg = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            } else {
                // this used to be the chunk_ban section of warden.json; keep any tuned limits
                Path warden = dir.resolve("warden.json");
                if (Files.exists(warden)) {
                    JsonObject old = JsonParser.parseString(Files.readString(warden)).getAsJsonObject();
                    if (old.has("chunk_ban")) {
                        cfg = old.getAsJsonObject("chunk_ban");
                        LOGGER.info("[ChunkGuard] carried the chunk_ban settings over from warden.json");
                    }
                }
            }
            enabled = !cfg.has("enabled") || cfg.get("enabled").getAsBoolean();
            maxItemBytes = cfg.has("max_item_bytes") ? cfg.get("max_item_bytes").getAsInt() : 65536;
            maxBlockEntityBytes = cfg.has("max_block_entity_bytes") ? cfg.get("max_block_entity_bytes").getAsInt() : 32768;
            maxChunkBlockEntityBytes = cfg.has("max_chunk_block_entity_bytes") ? cfg.get("max_chunk_block_entity_bytes").getAsInt() : 1048576;
            if (!Files.exists(file)) {
                JsonObject out = new JsonObject();
                out.addProperty("enabled", enabled);
                out.addProperty("max_item_bytes", maxItemBytes);
                out.addProperty("max_block_entity_bytes", maxBlockEntityBytes);
                out.addProperty("max_chunk_block_entity_bytes", maxChunkBlockEntityBytes);
                Files.writeString(file, GSON.toJson(out));
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[ChunkGuard] could not read {}, using defaults: {}", file, e.getMessage());
        }
    }

    static MutableText prefix() {
        return Text.literal("[")
                .append(Text.literal("CHUNKGUARD").formatted(Formatting.DARK_PURPLE, Formatting.BOLD))
                .append(Text.literal("] "));
    }

    public static String shortId(ItemStack stack) {
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    /** Network-encoded size of a stack, nested contents included. 0 for plain stacks. */
    public static int itemBytes(ItemStack stack) {
        DynamicRegistryManager registries = ChunkGuardMod.registries;
        if (registries == null || stack == null || stack.isEmpty() || stack.getComponentChanges().isEmpty()) {
            return 0;
        }
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), registries);
        try {
            ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, stack);
            return buf.readableBytes();
        } catch (RuntimeException | StackOverflowError e) {
            // couldn't even encode it; it would kill the client's decoder too
            return Integer.MAX_VALUE;
        } finally {
            buf.release();
        }
    }

    public static boolean isOversized(ItemStack stack) {
        return enabled && itemBytes(stack) > maxItemBytes;
    }

    /** Warn-level log, at most once every 10s per key, so a spammed chunk doesn't flood the console. */
    public static void logOversized(String key, String message) {
        long now = System.currentTimeMillis();
        Long last = LAST_LOG.get(key);
        if (last != null && now - last < 10_000) {
            return;
        }
        if (LAST_LOG.size() > 1000) {
            LAST_LOG.values().removeIf(t -> now - t > 60_000);
        }
        LAST_LOG.put(key, now);
        LOGGER.warn("[ChunkGuard] {}", message);
    }

    private static String describe(ItemStack stack) {
        return shortId(stack) + " (" + (itemBytes(stack) / 1024) + " KB)";
    }

    private static void notify(ServerPlayerEntity player, String what) {
        player.sendMessage(prefix().append(Text.literal("removed " + what + " - too much item data").formatted(Formatting.RED)), true);
    }

    // creative players are left alone, as they were under Warden's default exemption; anything
    // they drop, frame or put in a container is still caught by the mixins
    public static void enforce(ServerPlayerEntity player) {
        if (!enabled || registries == null || player.isCreative()) {
            return;
        }
        String name = player.getName().getString();
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (isOversized(stack)) {
                String what = describe(stack);
                inv.setStack(i, ItemStack.EMPTY);
                notify(player, what);
                logOversized("inv:" + name, "removed " + what + " from " + name + "'s inventory");
            }
        }
        Inventory ender = player.getEnderChestInventory();
        for (int i = 0; i < ender.size(); i++) {
            ItemStack stack = ender.getStack(i);
            if (isOversized(stack)) {
                String what = describe(stack);
                ender.setStack(i, ItemStack.EMPTY);
                notify(player, what);
                logOversized("ender:" + name, "removed " + what + " from " + name + "'s ender chest");
            }
        }
        ScreenHandler handler = player.currentScreenHandler;
        if (handler != null) {
            boolean changed = purgeOversized(handler, name);
            ItemStack cursor = handler.getCursorStack();
            if (isOversized(cursor)) {
                String what = describe(cursor);
                handler.setCursorStack(ItemStack.EMPTY);
                notify(player, what);
                changed = true;
            }
            if (changed) {
                handler.sendContentUpdates();
            }
        }
    }

    /** Strips oversized stacks out of every slot of a handler. Also run before the full sync
     *  goes out, so a chest packed with book-stuffed shulkers can't kick whoever opens it. */
    public static boolean purgeOversized(ScreenHandler handler, String who) {
        if (!enabled || registries == null) {
            return false;
        }
        boolean changed = false;
        for (Slot slot : handler.slots) {
            ItemStack stack = slot.getStack();
            if (isOversized(stack)) {
                String what = describe(stack);
                slot.setStack(ItemStack.EMPTY);
                logOversized("container:" + who, "removed " + what + " from a container opened by " + who);
                changed = true;
            }
        }
        return changed;
    }
}
