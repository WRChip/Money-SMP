package dev.flame.moneysmp;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    final Map<UUID, PlayerData> players = new HashMap<>();
    final Map<String, UUID> byName = new HashMap<>();
    final List<Tx> transactions = new ArrayList<>();
    // all teams unless an admin ran /moneysmp teamcount; only then is it persisted
    int teamCount = Teams.NAMES.size();
    boolean teamCountSet;
    Integer teamMax;

    private final File file;

    Data(File dataFolder) {
        this.file = new File(dataFolder, "data.yml");
    }

    void load() {
        players.clear();
        byName.clear();
        transactions.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        teamCountSet = y.getBoolean("teamcount_set", false);
        teamCount = teamCountSet ? y.getInt("teamcount", Teams.NAMES.size()) : Teams.NAMES.size();
        teamMax = y.contains("teammax") ? y.getInt("teammax") : null;
        ConfigurationSection ps = y.getConfigurationSection("players");
        if (ps != null) {
            for (String key : ps.getKeys(false)) {
                UUID uid = UUID.fromString(key);
                PlayerData pd = new PlayerData();
                pd.name = ps.getString(key + ".name");
                pd.money = ps.getDouble(key + ".money", 0);
                pd.team = ps.getString(key + ".team");
                players.put(uid, pd);
                if (pd.name != null) byName.put(pd.name, uid);
            }
        }
        for (Map<?, ?> m : y.getMapList("transactions")) {
            transactions.add(new Tx(
                ((Number) m.get("stamp")).longValue(),
                String.valueOf(m.get("type")),
                String.valueOf(m.get("from")),
                String.valueOf(m.get("to")),
                ((Number) m.get("amount")).doubleValue(),
                String.valueOf(m.get("note"))));
        }
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        if (teamCountSet) {
            y.set("teamcount", teamCount);
            y.set("teamcount_set", true);
        }
        y.set("teammax", teamMax);
        for (Map.Entry<UUID, PlayerData> e : players.entrySet()) {
            String k = "players." + e.getKey();
            y.set(k + ".name", e.getValue().name);
            y.set(k + ".money", e.getValue().money);
            y.set(k + ".team", e.getValue().team);
        }
        List<Map<String, Object>> txs = new ArrayList<>();
        for (Tx t : transactions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stamp", t.stamp());
            m.put("type", t.type());
            m.put("from", t.from());
            m.put("to", t.to());
            m.put("amount", t.amount());
            m.put("note", t.note());
            txs.add(m);
        }
        y.set("transactions", txs);
        try {
            y.save(file);
        } catch (IOException e) {
            Bukkit.getLogger().severe("[MoneySMP] could not save data.yml: " + e.getMessage());
        }
    }

    PlayerData get(UUID uid) {
        return players.computeIfAbsent(uid, u -> new PlayerData());
    }

    PlayerData get(Player p) {
        PlayerData pd = get(p.getUniqueId());
        pd.name = p.getName();
        byName.put(p.getName(), p.getUniqueId());
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
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) return null;
        get(p);
        return p.getUniqueId();
    }

    // lookup, then fall back for players who have never joined: ask Mojang in online mode,
    // otherwise derive the offline uuid so it matches what they'll get when they connect.
    // new entries start on the join default so they don't lose it when they do show up
    UUID resolve(String name) {
        UUID uid = lookup(name);
        if (uid != null) return uid;
        String canon = name;
        if (Bukkit.getOnlineMode()) {
            PlayerProfile prof = Bukkit.createProfile(name);
            if (!prof.completeFromCache() && !prof.complete(false)) return null;
            uid = prof.getId();
            if (uid == null) return null;
            if (prof.getName() != null) canon = prof.getName();
        } else {
            uid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        }
        if (!players.containsKey(uid)) get(uid).money = 100;
        PlayerData pd = get(uid);
        pd.name = canon;
        byName.put(pd.name, uid);
        return uid;
    }

    void log(String type, String from, String to, double amount, String note) {
        transactions.add(new Tx(System.currentTimeMillis(), type, from, to, amount, note));
    }
}
