package dev.flame.moderation;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.server.Main;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class E2e {
    private static boolean ready;
    private static boolean ran;

    public static void main(String[] args) throws Exception {
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ready = true);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> stopped.countDown());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!ready || ran) return;
            ran = true;
            try {
                check(server);
            } catch (Throwable t) {
                failure.set(t);
                t.printStackTrace();
            } finally {
                server.stop(false);
            }
        });
        Main.main(new String[]{"--nogui"});
        require(stopped.await(120, TimeUnit.SECONDS), "test server did not stop");
        if (failure.get() != null) throw new AssertionError("moderation checks failed", failure.get());
        require(ran, "test server did not start");
        System.out.println("Moderation checks passed");
    }

    private static void check(MinecraftServer server) throws Exception {
        var world = server.getOverworld();
        var dispatcher = server.getCommandManager().getDispatcher();
        ServerPlayerEntity mod = player(server, "Mod");
        ServerPlayerEntity target = player(server, "Target");
        server.getPlayerManager().addToOperators(mod.getPlayerConfigEntry());
        setOnline(server, mod, true);
        setOnline(server, target, true);
        var asMod = mod.getCommandSource();
        var tick = Moderation.class.getDeclaredMethod("tick", MinecraftServer.class);
        tick.setAccessible(true);

        target.setPosition(0.5, -60, 0.5);
        require(dispatcher.execute("freeze Target", asMod) == 1, "freeze command");
        target.setPosition(6.5, -60, 0.5);
        tick.invoke(null, server);
        require(target.getX() == 0.5, "frozen player is put back");
        require(UseItemCallback.EVENT.invoker().interact(target, world, Hand.MAIN_HAND) == ActionResult.FAIL, "frozen player can't use items");
        dispatcher.execute("freeze Target", asMod);
        require(UseItemCallback.EVENT.invoker().interact(target, world, Hand.MAIN_HAND) == ActionResult.PASS, "unfreeze");

        var chat = MessageType.params(MessageType.CHAT, target);
        require(dispatcher.execute("mute Target", asMod) == 1, "mute command");
        require(!ServerMessageEvents.ALLOW_CHAT_MESSAGE.invoker().allowChatMessage(SignedMessage.ofUnsigned("hi"), target, chat), "muted chat blocked");
        dispatcher.execute("mute Target", asMod);
        require(ServerMessageEvents.ALLOW_CHAT_MESSAGE.invoker().allowChatMessage(SignedMessage.ofUnsigned("hi"), target, chat), "unmute");

        target.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 5));
        target.getInventory().setStack(39, new ItemStack(Items.DIAMOND_HELMET));
        require(dispatcher.execute("inv Target", asMod) == 1, "inv command");
        require(mod.currentScreenHandler instanceof GenericContainerScreenHandler, "inv opens a container");
        var view = mod.currentScreenHandler;
        require(view.getSlot(0).getStack().isOf(Items.DIAMOND), "view shows the hotbar");
        require(view.getSlot(36).getStack().isOf(Items.DIAMOND_HELMET), "view shows the helmet on row 5");
        require(!view.getSlot(44).canInsert(new ItemStack(Items.STONE)), "padding slots refuse items");
        view.getSlot(0).setStack(ItemStack.EMPTY);
        require(target.getInventory().getStack(0).isEmpty(), "edits reach the live inventory");
        mod.closeHandledScreen();

        target.getEnderChestInventory().setStack(0, new ItemStack(Items.EMERALD, 3));
        server.getPlayerManager().saveAllPlayerData();
        setOnline(server, target, false);
        server.getApiServices().nameToIdCache().add(target.getPlayerConfigEntry());
        require(dispatcher.execute("enderchest Target", asMod) == 1, "enderchest command on an offline player");
        view = mod.currentScreenHandler;
        require(view.getSlot(0).getStack().isOf(Items.EMERALD), "offline ender chest is read from playerdata");
        view.getSlot(0).setStack(ItemStack.EMPTY);
        mod.closeHandledScreen();
        var saved = server.getPlayerManager().loadPlayerData(target.getPlayerConfigEntry()).orElseThrow();
        require(saved.getListOrEmpty("EnderItems").isEmpty(), "offline edit is written back to playerdata");

        try {
            dispatcher.execute("freeze Target", asMod);
            throw new AssertionError("freeze must refuse an offline player");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
        }
    }

    private static ServerPlayerEntity player(MinecraftServer server, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayerEntity player = new ServerPlayerEntity(server, server.getOverworld(), profile, SyncedClientOptions.createDefault());
        player.networkHandler = new ServerPlayNetworkHandler(server, new ClientConnection(NetworkSide.SERVERBOUND),
                player, ConnectedClientData.createDefault(profile, false));
        player.changeGameMode(GameMode.SURVIVAL);
        return player;
    }

    // lookups by name walk the player list, lookups by uuid go through playerMap; a fake player has to be in both
    @SuppressWarnings("unchecked")
    private static void setOnline(MinecraftServer server, ServerPlayerEntity player, boolean online) throws Exception {
        var manager = server.getPlayerManager();
        var field = net.minecraft.server.PlayerManager.class.getDeclaredField("playerMap");
        field.setAccessible(true);
        var byUuid = (Map<UUID, ServerPlayerEntity>) field.get(manager);
        if (online) {
            manager.getPlayerList().add(player);
            byUuid.put(player.getUuid(), player);
        } else {
            manager.getPlayerList().remove(player);
            byUuid.remove(player.getUuid());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
