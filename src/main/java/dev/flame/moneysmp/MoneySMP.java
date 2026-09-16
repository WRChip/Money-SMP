package dev.flame.moneysmp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MoneySMP extends JavaPlugin {
    static final class Notify {
        final String msg;
        int timer;

        Notify(String msg, int timer) {
            this.msg = msg;
            this.timer = timer;
        }
    }

    final Map<UUID, Notify> notify = new HashMap<>();
    Data data;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        data = new Data(getDataFolder());
        data.load();
        Teams.setup();
        for (Player p : Bukkit.getOnlinePlayers()) sync(p);

        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        getCommand("moneysmp").setExecutor(new MoneySmpCommand(this));
        getCommand("pay").setExecutor(new PayCommand(this));
        getCommand("balance").setExecutor(new BalanceCommand(this));

        Bukkit.getScheduler().runTaskTimer(this, this::actionBarTick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, data::save, 20L * 60 * 5, 20L * 60 * 5);
    }

    @Override
    public void onDisable() {
        if (data != null) data.save();
    }

    void sync(Player p) {
        Teams.sync(p, data.team(p.getUniqueId()));
    }

    void notify(UUID uid, String msg, int secs) {
        notify.put(uid, new Notify(msg, secs));
    }

    private void actionBarTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uid = p.getUniqueId();
            Notify n = notify.get(uid);
            if (n != null) {
                n.timer--;
                if (n.timer > 0) {
                    p.sendActionBar(Fmt.c(n.msg));
                    continue;
                }
                notify.remove(uid);
            }
            String team = data.team(uid);
            String label = team == null ? "&7No Team" : Teams.color(team) + team;
            p.sendActionBar(Fmt.c("&a&l$ &e" + Fmt.money(data.money(uid)) + "  &8|  &7Team: " + label));
        }
    }
}
