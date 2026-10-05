package dev.flame.moneysmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class Data {
    record Tx(long stamp, String type, String from, String to, double amount, String note) {}

    static final class PlayerData {
        String name;
        double money;
        String team;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    final Map<UUID, PlayerData> players = new HashMap<>();
    final Map<String, UUID> byName = new HashMap<>();
    final List<Tx> transactions = new ArrayList<>();
    int teamCount = 4;
    Integer teamMax;

    private final Path file;
    private final MinecraftServer server;

    Data(Path dir, MinecraftServer server) {
        this.file = dir.resolve("data.json");
        this.server = server;
    }

    void load() {
        players.clear();
        byName.clear();
        transactions.clear();
        if (!Files.exists(file)) return;
        JsonObject y;
        try {
            y = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            MoneySMP.LOG.error("could not read data.json", e);
            return;
        }
        teamCount = y.has("teamcount") ? y.get("teamcount").getAsInt() : 4;
        teamMax = y.has("teammax") && !y.get("teammax").isJsonNull() ? y.get("teammax").getAsInt() : null;
        if (y.has("players")) {
            for (Map.Entry<String, JsonElement> e : y.getAsJsonObject("players").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                PlayerData pd = new PlayerData();
                pd.name = o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : null;
                pd.money = o.has("money") ? o.get("money").getAsDouble() : 0;
                pd.team = o.has("team") && !o.get("team").isJsonNull() ? o.get("team").getAsString() : null;
                UUID uid = UUID.fromString(e.getKey());
                players.put(uid, pd);
                if (pd.name != null) byName.put(pd.name, uid);
            }
        }
        if (y.has("transactions")) {
            for (JsonElement el : y.getAsJsonArray("transactions")) {
                JsonObject o = el.getAsJsonObject();
                transactions.add(new Tx(
                    o.get("stamp").getAsLong(),
                    o.get("type").getAsString(),
                    o.get("from").getAsString(),
                    o.get("to").getAsString(),
                    o.get("amount").getAsDouble(),
                    o.get("note").getAsString()));
            }
        }
    }

    void save() {
        JsonObject y = new JsonObject();
        y.addProperty("teamcount", teamCount);
        if (teamMax != null) y.addProperty("teammax", teamMax);
        JsonObject ps = new JsonObject();
        for (Map.Entry<UUID, PlayerData> e : players.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", e.getValue().name);
            o.addProperty("money", e.getValue().money);
            o.addProperty("team", e.getValue().team);
            ps.add(e.getKey().toString(), o);
        }
        y.add("players", ps);
        JsonArray txs = new JsonArray();
        for (Tx t : transactions) {
            JsonObject o = new JsonObject();
            o.addProperty("stamp", t.stamp());
            o.addProperty("type", t.type());
            o.addProperty("from", t.from());
            o.addProperty("to", t.to());
            o.addProperty("amount", t.amount());
            o.addProperty("note", t.note());
            txs.add(o);
        }
        y.add("transactions", txs);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(y));
        } catch (IOException e) {
            MoneySMP.LOG.error("could not save data.json", e);
        }
    }

    PlayerData get(UUID uid) {
        return players.computeIfAbsent(uid, u -> new PlayerData());
    }

    PlayerData get(ServerPlayer p) {
        PlayerData pd = get(p.getUUID());
        pd.name = p.getScoreboardName();
        byName.put(pd.name, p.getUUID());
        return pd;
    }

    double money(UUID uid) {
        PlayerData pd = players.get(uid);
        return pd == null ? 0 : pd.money;
    }

    String team(UUID uid) {
        PlayerData pd = players.get(uid);
        return pd == null ? null : pd.team;
    }

    // stored name -> uuid, falling back to an online player with that exact name
    UUID lookup(String name) {
        UUID uid = byName.get(name);
        if (uid != null) return uid;
        ServerPlayer p = server.getPlayerList().getPlayerByName(name);
        if (p == null) return null;
        get(p);
        return p.getUUID();
    }

    void log(String type, String from, String to, double amount, String note) {
        transactions.add(new Tx(System.currentTimeMillis(), type, from, to, amount, note));
    }
}
