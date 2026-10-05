package dev.flame.chunkguard;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.server.Main;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class E2e {
    private static boolean ready;
    private static boolean ran;

    public static void main(String[] args) throws Exception {
        boolean migrate = args.length > 0;
        if (migrate) {
            // runs before the mod initializes, like a server that only has the pre-split config
            Files.createDirectories(Path.of("config"));
            Files.writeString(Path.of("config/warden.json"), "{\"chunk_ban\": {\"enabled\": true, \"max_item_bytes\": 2048}}");
        }
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ready = true);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> stopped.countDown());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!ready || ran) return;
            ran = true;
            try {
                if (migrate) checkMigrated();
                else check(server);
            } catch (Throwable t) {
                failure.set(t);
                t.printStackTrace();
            } finally {
                server.stop(false);
            }
        });
        Main.main(new String[]{"--nogui"});
        require(stopped.await(120, TimeUnit.SECONDS), "test server did not stop");
        if (failure.get() != null) throw new AssertionError("chunkguard checks failed", failure.get());
        require(ran, "test server did not start");
        System.out.println("ChunkGuard checks passed" + (migrate ? " (migrated config)" : ""));
    }

    private static void check(MinecraftServer server) throws Exception {
        var world = server.getOverworld();
        require(Files.readString(Path.of("config/chunkguard.json")).contains("\"max_item_bytes\": 65536"), "default config written");
        require(ChunkGuardMod.itemBytes(new ItemStack(Items.PAPER)) == 0, "plain stacks are free");
        int size = ChunkGuardMod.itemBytes(huge());
        require(size > 100_000 && size < 200_000, "item size is measured, got " + size);
        require(ChunkGuardMod.isOversized(huge()), "100 KB of custom data is over the item cap");
        require(!ChunkGuardMod.isOversized(named("fine")), "a renamed item is not");

        var player = player(server, "Survivor", GameMode.SURVIVAL);
        player.getInventory().setStack(0, huge());
        player.getInventory().setStack(1, named("keep"));
        player.getEnderChestInventory().setStack(0, huge());
        ChunkGuardMod.enforce(player);
        require(player.getInventory().getStack(0).isEmpty(), "oversized item taken from the inventory");
        require(player.getInventory().getStack(1).isOf(Items.PAPER), "normal item kept");
        require(player.getEnderChestInventory().getStack(0).isEmpty(), "oversized item taken from the ender chest");

        var creative = player(server, "Builder", GameMode.CREATIVE);
        creative.getInventory().setStack(0, huge());
        ChunkGuardMod.enforce(creative);
        require(!creative.getInventory().getStack(0).isEmpty(), "creative inventories are left alone");

        BlockPos at = new BlockPos(0, -60, 0);
        var dropped = new ItemEntity(world, 0.5, -60, 0.5, huge());
        world.spawnEntity(dropped);
        dropped.tick();
        require(dropped.isRemoved(), "dropped oversized item is discarded");

        var ground = new ItemEntity(world, 0.5, -60, 0.5, huge());
        player.getInventory().clear();
        ground.onPlayerCollision(player);
        require(ground.isRemoved() && player.getInventory().isEmpty(), "oversized item can't be picked up");

        var frame = new ItemFrameEntity(world, at.up(), Direction.NORTH);
        frame.setHeldItemStack(huge(), false);
        require(frame.getHeldItemStack().isEmpty(), "item frame refuses an oversized item");

        var chest = new SimpleInventory(27);
        chest.setStack(0, huge());
        chest.setStack(1, named("keep"));
        var handler = GenericContainerScreenHandler.createGeneric9x3(1, player.getInventory(), chest);
        handler.updateToClient();
        require(chest.getStack(0).isEmpty() && !chest.getStack(1).isEmpty(), "container is purged before it syncs");

        int chunkCap = ChunkGuardMod.maxChunkBlockEntityBytes;
        try {
            BlockPos signPos = at.east(2);
            world.setBlockState(signPos, Blocks.OAK_SIGN.getDefaultState());
            var sign = (SignBlockEntity) world.getBlockEntity(signPos);
            sign.setText(new SignText().withMessage(0, Text.literal("x".repeat(2000))), true);
            var chunk = world.getWorldChunk(signPos);
            require(blockEntities(new ChunkData(chunk)).size() == 1, "chunk packet carries the sign");
            ChunkGuardMod.maxChunkBlockEntityBytes = 1024;
            require(blockEntities(new ChunkData(chunk)).isEmpty(), "chunk over the total cap sends no block entities");
        } finally {
            ChunkGuardMod.maxChunkBlockEntityBytes = chunkCap;
        }
    }

    private static void checkMigrated() throws Exception {
        require(ChunkGuardMod.maxItemBytes == 2048, "tuned limit carried over from warden.json");
        require(Files.readString(Path.of("config/chunkguard.json")).contains("\"max_item_bytes\": 2048"), "carried limit saved");
    }

    private static ItemStack huge() {
        var stack = new ItemStack(Items.PAPER);
        var nbt = new NbtCompound();
        for (int i = 0; i < 5; i++) {
            nbt.putString("junk" + i, "x".repeat(20_000));
        }
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        return stack;
    }

    private static ItemStack named(String name) {
        var stack = new ItemStack(Items.PAPER);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return stack;
    }

    private static List<?> blockEntities(ChunkData data) throws Exception {
        var field = ChunkData.class.getDeclaredField("blockEntities");
        field.setAccessible(true);
        return (List<?>) field.get(data);
    }

    private static ServerPlayerEntity player(MinecraftServer server, String name, GameMode mode) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayerEntity player = new ServerPlayerEntity(server, server.getOverworld(), profile, SyncedClientOptions.createDefault());
        player.networkHandler = new ServerPlayNetworkHandler(server, new ClientConnection(NetworkSide.SERVERBOUND),
                player, ConnectedClientData.createDefault(profile, false));
        player.changeGameMode(mode);
        return player;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
