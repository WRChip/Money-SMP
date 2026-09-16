package dev.flame.moneysmp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

final class PayCommand implements CommandExecutor {
    private final MoneySMP plugin;

    PayCommand(MoneySMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Fmt.p("&cPlayers only."));
            return true;
        }
        if (args.length < 2) return false;
        String name = args[0];
        double amt;
        try {
            amt = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (amt <= 0) {
            p.sendMessage(Fmt.p("&cAmount must be above 0."));
            return true;
        }
        if (name.equals(p.getName())) {
            p.sendMessage(Fmt.p("&cYou cannot pay yourself."));
            return true;
        }
        Data.PlayerData me = plugin.data.get(p);
        if (me.money < amt) {
            p.sendMessage(Fmt.p("&cInsufficient funds! &7You have &e$" + Fmt.money(me.money) + "&7."));
            return true;
        }
        UUID target = plugin.data.lookup(name);
        if (target == null) {
            p.sendMessage(Fmt.p("&cPlayer &f" + name + " &cnot found."));
            return true;
        }
        me.money -= amt;
        Data.PlayerData t = plugin.data.get(target);
        t.money += amt;
        plugin.data.log("PAY", p.getName(), name, amt, "Player payment");
        p.sendMessage(Fmt.p("&aYou paid &e$" + Fmt.money(amt) + " &ato &f" + name + "&a. Your balance: &e$" + Fmt.money(me.money)));

        Player tp = Bukkit.getPlayer(target);
        if (tp != null) {
            plugin.notify(target, "&a&l+ $" + Fmt.money(amt) + "  &7from &f" + p.getName() + "  &8|  &a$ &e" + Fmt.money(t.money), 6);
            tp.sendMessage(Fmt.p("&e" + p.getName() + " &apaid you &e$" + Fmt.money(amt) + "&a! Balance: &e$" + Fmt.money(t.money)));
        }
        return true;
    }
}
