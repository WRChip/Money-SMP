package com.warden;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.rule.GameRules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// locks down the overworld the moment it's generated for the very first time on this server -
// adventure mode, pvp off, a small border and spawn radius around spawn, ticking frozen - then
// tells the first player who joins. an admin lifts the lockdown with /warden restore. worlds
// that already had region data on disk before this start are left alone.
public final class WardenNewWorldWatcher {

    private static final double LOCKDOWN_BORDER_SIZE = 50.0;

    private static boolean freshOverworld;
    private static boolean noticeSent;

    private WardenNewWorldWatcher() {
    }

    public static void register() {
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() != World.OVERWORLD) {
                return;
            }
            // checked the moment the world object exists, before the server force-generates
            // spawn chunks, so an already-generated world still has region files here.
            freshOverworld = isEmpty(server.getSavePath(WorldSavePath.ROOT).resolve("region"));
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!freshOverworld) {
                return;
            }
            ServerWorld overworld = server.getOverworld();
            server.setDefaultGameMode(GameMode.ADVENTURE);
            overworld.getGameRules().setValue(GameRules.PVP, false, server);
            overworld.getGameRules().setValue(GameRules.RESPAWN_RADIUS, 0, server);
            BlockPos spawn = overworld.getSpawnPoint().getPos();
            WorldBorder border = overworld.getWorldBorder();
            border.setCenter(spawn.getX() + 0.5, spawn.getZ() + 0.5);
            border.setSize(LOCKDOWN_BORDER_SIZE);
            server.getTickManager().setFrozen(true);
            WardenMod.LOGGER.info("[Warden] Overworld was just generated, locking it down until /warden restore is run");
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!freshOverworld || noticeSent) {
                return;
            }
            noticeSent = true;
            handler.player.sendMessage(WardenMod.wardenPrefix()
                    .append(Text.literal("this world was just generated and is locked down (frozen, adventure mode, pvp off, a small border around spawn). An admin needs to run /warden restore to open it up.")
                            .formatted(Formatting.RED)));
        });
    }

    private static boolean isEmpty(Path dir) {
        if (!Files.isDirectory(dir)) {
            return true;
        }
        try (var files = Files.list(dir)) {
            return files.findAny().isEmpty();
        } catch (IOException e) {
            return true;
        }
    }
}
