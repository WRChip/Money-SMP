package dev.flame.moneysmp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

// /balance and /bal, also backs /moneysmp balance
final class BalanceCommand implements CommandExecutor {
    private final MoneySMP plugin;

    BalanceCommand(MoneySMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        show(sender, args.length > 0 ? args[0] : null);
        return true;
    }

    void show(CommandSender sender, String name) {
        if (name == null) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Fmt.p("&cUsage: &f/balance <player>"));
                return;
            }
            sender.sendMessage(Fmt.p("&7Your balance: &a&l$&e " + Fmt.money(plugin.data.money(p.getUniqueId()))));
            return;
        }
        UUID uid = plugin.data.lookup(name);
        if (uid == null) {
            sender.sendMessage(Fmt.p("&cPlayer &f" + name + " &chas never joined."));
            return;
        }
        sender.sendMessage(Fmt.p("&e" + name + "&7's balance: &a&l$&e " + Fmt.money(plugin.data.money(uid))));
    }
}
