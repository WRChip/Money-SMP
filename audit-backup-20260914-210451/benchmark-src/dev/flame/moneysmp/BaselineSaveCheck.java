package dev.flame.moneysmp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class BaselineSaveCheck {
    public static void main(String[] args) throws Exception {
        Data data = new Data(Files.createTempDirectory(Path.of("build"), "baseline-save-"), null);
        Data.PlayerData player = data.get(UUID.randomUUID());
        player.name = "test";
        player.money = 100;
        player.team = "Red";
        data.teamMax = 12;
        for (int i = 0; i < 100_000; i++) {
            data.transactions.add(new Data.Tx(i / 2, "PAY", "a", "b", 1, "test"));
        }
        long start = System.nanoTime();
        data.save();
        System.out.printf("baseline 100,000-transaction save: %.3f ms%n", (System.nanoTime() - start) / 1_000_000.0);
    }
}
