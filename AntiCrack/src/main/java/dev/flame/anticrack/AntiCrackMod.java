package dev.flame.anticrack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AntiCrackMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("AntiCrack");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // only read at startup: flipping it on an existing world moves every structure in chunks
    // generated afterwards, so it isn't something to toggle while the server is up
    public static boolean enabled = true;

    @Override
    public void onInitialize() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        Path file = dir.resolve("anticrack.json");
        try {
            JsonObject cfg;
            if (Files.exists(file)) {
                cfg = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            } else {
                cfg = new JsonObject();
                cfg.addProperty("enabled", true);
                // this used to be the anti_seedcrack section of warden.json; carry a setting over
                // instead of resetting it, since a world may have been generated with it off
                Path warden = dir.resolve("warden.json");
                if (Files.exists(warden)) {
                    JsonObject old = JsonParser.parseString(Files.readString(warden)).getAsJsonObject();
                    if (old.has("anti_seedcrack")) {
                        cfg = old.getAsJsonObject("anti_seedcrack");
                        LOGGER.info("[AntiCrack] carried the anti_seedcrack setting over from warden.json");
                    }
                }
                Files.writeString(file, GSON.toJson(cfg));
            }
            enabled = !cfg.has("enabled") || cfg.get("enabled").getAsBoolean();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[AntiCrack] could not read {}, leaving scrambling on: {}", file, e.getMessage());
        }
        LOGGER.info("[AntiCrack] seed scrambling {}", enabled ? "on" : "off");
    }
}
