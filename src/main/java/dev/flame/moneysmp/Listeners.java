package dev.flame.moneysmp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

final class Listeners implements Listener {
    private final MoneySMP plugin;

    Listeners(MoneySMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean fresh = !plugin.data.players.containsKey(p.getUniqueId());
        Data.PlayerData pd = plugin.data.get(p);
        if (fresh) pd.money = 100;
        plugin.sync(p);
    }

    // PvP only: attacker +$20, victim -$20. Mob kills give nothing.
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player attacker = victim.getKiller();
        if (attacker == null || attacker == victim) return;

        Data.PlayerData a = plugin.data.get(attacker);
        a.money += 20;
        String aMsg = "&a&l+ $20  &7Kill Reward!  &8|  &a$ &e" + Fmt.money(a.money);
        attacker.sendActionBar(Fmt.c(aMsg));
        plugin.notify(attacker.getUniqueId(), aMsg, 6);
        plugin.data.log("KILL", attacker.getName(), victim.getName(), 20, "Kill reward");

        Data.PlayerData v = plugin.data.get(victim);
        v.money -= 20;
        String vMsg = "&c&l- $20  &7Killed by &f" + attacker.getName() + "&7!  &8|  &a$ &e" + Fmt.money(v.money);
        victim.sendActionBar(Fmt.c(vMsg));
        plugin.notify(victim.getUniqueId(), vMsg, 6);
        plugin.data.log("DEATH", victim.getName(), attacker.getName(), 20, "Death penalty");
    }
}
