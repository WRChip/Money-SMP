package dev.flame.anticrack;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.LootableInventory;
import net.minecraft.loot.LootTables;
import net.minecraft.server.Main;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.nio.file.Files;
import java.nio.file.Path;
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
            Files.writeString(Path.of("config/warden.json"), "{\"anti_seedcrack\": {\"enabled\": false}}");
        }
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ready = true);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> stopped.countDown());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!ready || ran) return;
            ran = true;
            try {
                if (migrate) checkMigrated(server);
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
        if (failure.get() != null) throw new AssertionError("anticrack checks failed", failure.get());
        require(ran, "test server did not start");
        System.out.println("AntiCrack checks passed" + (migrate ? " (migrated config)" : ""));
    }

    private static void check(MinecraftServer server) throws Exception {
        var world = server.getOverworld();
        require(AntiCrackMod.enabled, "scrambling defaults to on");
        require(Files.readString(Path.of("config/anticrack.json")).contains("\"enabled\": true"), "default config written");
        require(SeedHash.get() != 0, "seed hash computed before the worlds");
        long structureSeed = world.getChunkManager().getStructurePlacementCalculator().getStructureSeed();
        require(structureSeed != world.getSeed(), "structure placement must not use the world seed");
        require(structureSeed == (world.getSeed() ^ SeedHash.get()), "structure placement uses the scrambled seed");

        BlockPos pos = world.getSpawnPoint().getPos().up(3);
        world.setBlockState(pos, Blocks.CHEST.getDefaultState());
        LootableInventory.setLootTable(world, Random.create(42), pos, LootTables.SIMPLE_DUNGEON_CHEST);
        long rolled = Random.create(42).nextLong();
        long stored = ((ChestBlockEntity) world.getBlockEntity(pos)).getLootTableSeed();
        require(stored == (rolled ^ SeedHash.get()), "generated chest loot seed is scrambled");
    }

    private static void checkMigrated(MinecraftServer server) throws Exception {
        var world = server.getOverworld();
        require(!AntiCrackMod.enabled, "setting from warden.json carried over");
        require(Files.readString(Path.of("config/anticrack.json")).contains("\"enabled\": false"), "carried setting saved");
        require(world.getChunkManager().getStructurePlacementCalculator().getStructureSeed() == world.getSeed(),
                "with scrambling off structures keep their vanilla positions");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
