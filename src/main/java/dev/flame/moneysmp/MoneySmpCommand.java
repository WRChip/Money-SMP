package dev.flame.moneysmp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class MoneySmpCommand implements CommandExecutor {
    private static final String LINE = "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    private final MoneySMP plugin;
    private final Data data;
    private final BalanceCommand balance;

    MoneySmpCommand(MoneySMP plugin) {
        this.plugin = plugin;
        this.data = plugin.data;
        this.balance = new BalanceCommand(plugin);
    }

    private static void send(CommandSender s, String msg) {
        s.sendMessage(Fmt.c(msg));
    }

    private static void broadcast(String msg) {
        Bukkit.broadcast(Fmt.c(msg));
    }

    private static Double num(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        boolean admin = sender.isOp() || sender.hasPermission("moneysmp.admin");

        if (args.length == 0) {
            help(sender, admin);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "balance" -> balance.show(sender, args.length > 1 ? args[1] : null);
            case "myteam" -> myteam(sender);
            case "teams" -> teams(sender);
            default -> {
                if (!admin) {
                    if (List.of("give", "take", "set", "reset", "fine", "transaction", "teambal",
                            "teammax", "teamcount", "team", "randomteams").contains(sub)) {
                        send(sender, Fmt.PREFIX + " &cNo permission.");
                    } else {
                        send(sender, Fmt.PREFIX + " &cUnknown subcommand. Run &f/moneysmp &7for help.");
                    }
                    return true;
                }
                switch (sub) {
                    case "give" -> adjust(sender, args, "give");
                    case "take" -> adjust(sender, args, "take");
                    case "set" -> adjust(sender, args, "set");
                    case "reset" -> reset(sender);
                    case "fine" -> fine(sender, args);
                    case "transaction" -> transaction(sender, args);
                    case "teambal" -> teambal(sender);
                    case "teammax" -> teammax(sender, args);
                    case "teamcount" -> teamcount(sender, args);
                    case "team" -> teamSet(sender, args);
                    case "randomteams" -> randomteams(sender);
                    default -> send(sender, Fmt.PREFIX + " &cUnknown subcommand. Run &f/moneysmp &7for help.");
                }
            }
        }
        return true;
    }

    private void help(CommandSender s, boolean admin) {
        send(s, "");
        send(s, LINE);
        send(s, "  " + Fmt.PREFIX + " &aCommand List");
        send(s, LINE);
        send(s, "  &f/moneysmp balance &8[player]");
        send(s, "  &f/moneysmp myteam");
        send(s, "  &f/moneysmp teams");
        send(s, "  &f/pay &e<player> <amount>");
        if (admin) {
            send(s, "");
            send(s, "  &7&lAdmin Commands:");
            send(s, "  &f/moneysmp give &e<player> <amount>");
            send(s, "  &f/moneysmp take &e<player> <amount>");
            send(s, "  &f/moneysmp set &e<player> <amount>");
            send(s, "  &f/moneysmp reset");
            send(s, "  &f/moneysmp fine &e<player> <amount> <reason>");
            send(s, "  &f/moneysmp teambal");
            send(s, "  &f/moneysmp teammax &e<number>");
            send(s, "  &f/moneysmp teamcount &e<1-9>");
            send(s, "  &f/moneysmp randomteams");
            send(s, "  &f/moneysmp team set &e<player> <team>");
            send(s, "  &f/moneysmp transaction &e<time>  &8(e.g. 1h 30m 7d)");
            send(s, "");
            send(s, "  &7&lTeams &8(count: " + data.teamCount + "):");
            send(s, "  &c1 Red  &92 Blue  &a3 Green  &e4 Yellow");
            send(s, "  &55 Purple  &b6 Aqua  &67 Orange  &d8 Pink  &f9 White");
        }
        send(s, LINE);
        send(s, "");
    }

    private void myteam(CommandSender s) {
        if (!(s instanceof Player p)) {
            send(s, Fmt.PREFIX + " &cPlayers only.");
            return;
        }
        String t = data.team(p.getUniqueId());
        if (t == null) {
            send(s, Fmt.PREFIX + " &7You are not on a team.");
            return;
        }
        send(s, Fmt.PREFIX + " &7Your team: " + Teams.color(t) + "&l" + t);
    }

    private void teams(CommandSender s) {
        send(s, "");
        send(s, Fmt.PREFIX + " &7Active Teams  &8|  &7Count: &f" + data.teamCount);
        send(s, "");
        for (String t : Teams.active(data.teamCount)) {
            String col = Teams.color(t);
            StringBuilder members = new StringBuilder();
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!t.equals(data.team(p.getUniqueId()))) continue;
                count++;
                if (members.length() > 0) members.append("&7, ");
                members.append(col).append(p.getName());
            }
            if (count > 0) {
                send(s, "  " + col + "&l" + t + " &8(" + count + ")  &8»  " + members);
            } else {
                send(s, "  " + col + "&l" + t + " &8(0)  &8»  &7Empty");
            }
        }
        send(s, "");
    }

    // give / take / set share the same shape
    private void adjust(CommandSender s, String[] args, String mode) {
        if (args.length < 3) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp " + mode + " <player> <amount>");
            return;
        }
        String name = args[1];
        Double amt = num(args[2]);
        if (amt == null) {
            send(s, Fmt.PREFIX + " &cInvalid amount.");
            return;
        }
        if (mode.equals("set") ? amt < 0 : amt <= 0) {
            send(s, Fmt.PREFIX + (mode.equals("set") ? " &cCannot set negative balance." : " &cAmount must be above 0."));
            return;
        }
        UUID uid = data.lookup(name);
        if (uid == null) {
            send(s, Fmt.PREFIX + " &cPlayer &f" + name + " &cnot found.");
            return;
        }
        Data.PlayerData pd = data.get(uid);
        Player target = Bukkit.getPlayer(uid);
        String a = Fmt.money(amt);
        switch (mode) {
            case "give" -> {
                pd.money += amt;
                data.log("GIVE", s.getName(), name, amt, "Admin give");
                send(s, Fmt.PREFIX + " &aGave &e$" + a + " &ato &f" + name + "&a. Balance: &e$" + Fmt.money(pd.money));
                if (target != null) send(target, Fmt.PREFIX + " &aYou received &e$" + a + "&a! Balance: &e$" + Fmt.money(pd.money));
            }
            case "take" -> {
                pd.money -= amt;
                data.log("TAKE", s.getName(), name, amt, "Admin take");
                send(s, Fmt.PREFIX + " &cTook &e$" + a + " &cfrom &f" + name + "&c. Balance: &e$" + Fmt.money(pd.money));
                if (target != null) send(target, Fmt.PREFIX + " &c$" + a + " &cdeducted. Balance: &e$" + Fmt.money(pd.money));
            }
            case "set" -> {
                pd.money = amt;
                data.log("SET", s.getName(), name, amt, "Admin set balance");
                send(s, Fmt.PREFIX + " &aSet &f" + name + "&a's balance to &e$" + a);
                if (target != null) send(target, Fmt.PREFIX + " &7Your balance was set to &e$" + a);
            }
        }
    }

    private void reset(CommandSender s) {
        int count = 0;
        for (Data.PlayerData pd : data.players.values()) {
            pd.money = 100;
            pd.team = null;
            count++;
        }
        for (Player p : Bukkit.getOnlinePlayers()) plugin.sync(p);
        data.log("RESET", s.getName(), "ALL PLAYERS", 100, "Mass balance and teams reset");
        send(s, "");
        send(s, Fmt.PREFIX + " &a&lFull Reset! &7Reset &e" + count + " &7players (including offline) to &a&l$100 &7and cleared all teams.");
        send(s, "");
        broadcast(Fmt.PREFIX + " &aAll balances reset to &l$100 &aand all teams have been cleared!");
    }

    private void fine(CommandSender s, String[] args) {
        if (args.length < 4) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp fine <player> <amount> <reason>");
            return;
        }
        String name = args[1];
        Double amt = num(args[2]);
        if (amt == null) {
            send(s, Fmt.PREFIX + " &cInvalid amount.");
            return;
        }
        String reason = String.join(" ", List.of(args).subList(3, args.length));
        if (amt <= 0) {
            send(s, Fmt.PREFIX + " &cFine must be above 0.");
            return;
        }
        UUID uid = data.lookup(name);
        if (uid == null) {
            send(s, Fmt.PREFIX + " &cPlayer &f" + name + " &cnot found.");
            return;
        }
        Data.PlayerData pd = data.get(uid);
        pd.money -= amt;
        data.log("FINE", s.getName(), name, amt, reason);
        broadcast("");
        broadcast(Fmt.PREFIX + " &c&l⚠ FINE ISSUED ⚠");
        broadcast("  &7Player: &f" + name);
        broadcast("  &7Amount: &c-$" + Fmt.money(amt));
        broadcast("  &7Reason: &f" + reason);
        broadcast("  &7New Balance: &e$" + Fmt.money(pd.money));
        broadcast("");
        Player target = Bukkit.getPlayer(uid);
        if (target != null) {
            plugin.notify(uid, "&c&l- $" + Fmt.money(amt) + "  &7Fine: &f" + reason + "  &8|  &a$ &e" + Fmt.money(pd.money), 8);
            send(target, Fmt.PREFIX + " &c&lYou were fined &e$" + Fmt.money(amt) + "&c! Reason: &f" + reason);
        }
    }

    private void transaction(CommandSender s, String[] args) {
        if (args.length < 2) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp transaction <time>  &7e.g. &f30m 2h 1d");
            return;
        }
        String raw = args[1];
        long secs = Fmt.parseTime(raw);
        if (secs <= 0) {
            send(s, Fmt.PREFIX + " &cInvalid time. &7Use: &f30s &7/ &f10m &7/ &f2h &7/ &f1d");
            return;
        }
        if (data.transactions.isEmpty()) {
            send(s, Fmt.PREFIX + " &7No transactions recorded yet.");
            return;
        }
        send(s, "");
        send(s, LINE);
        send(s, "  " + Fmt.PREFIX + " &aTransaction Log  &8|  &7Last &f" + raw);
        send(s, LINE);
        send(s, "  &8Type legend: &aPAY &6KILL &cFINE &bGIVE &4TAKE &5SET &8RESET");
        send(s, LINE);

        int found = 0;
        long now = System.currentTimeMillis();
        for (int i = data.transactions.size() - 1; i >= 0; i--) {
            Data.Tx tx = data.transactions.get(i);
            long ago = (now - tx.stamp()) / 1000;
            if (ago > secs) continue;
            String col = switch (tx.type()) {
                case "PAY" -> "&a";
                case "KILL" -> "&6";
                case "FINE" -> "&c";
                case "GIVE" -> "&b";
                case "TAKE" -> "&4";
                case "SET" -> "&5";
                case "RESET" -> "&8";
                default -> "&7";
            };
            send(s, "  " + col + "&l[" + tx.type() + "]  &8" + Fmt.timeAgo(ago) + " ago  &8|  &e$" + Fmt.money(tx.amount()));
            send(s, "    &7From: &f" + tx.from() + "  &8➜  &7To: &f" + tx.to());
            if (!tx.note().isEmpty()) send(s, "    &7Note: &f" + tx.note());
            found++;
        }

        send(s, LINE);
        if (found == 0) {
            send(s, "  &7No transactions found in the last &f" + raw + "&7.");
        } else {
            send(s, "  &7Showing &f" + found + " &7transaction(s) from the last &f" + raw);
        }
        send(s, LINE);
        send(s, "");
    }

    private void teambal(CommandSender s) {
        send(s, "");
        send(s, Fmt.PREFIX + " &a&l⚔ Team Balances ⚔");
        send(s, "");
        for (String t : Teams.active(data.teamCount)) {
            String col = Teams.color(t);
            double total = 0;
            int count = 0;
            send(s, "  " + col + "&l" + t + "&8:");
            for (Map.Entry<UUID, Data.PlayerData> e : data.players.entrySet()) {
                Data.PlayerData pd = e.getValue();
                if (!t.equals(pd.team)) continue;
                total += pd.money;
                count++;
                String tag = Bukkit.getPlayer(e.getKey()) != null ? "&a(online)" : "&8(offline)";
                send(s, "    " + col + pd.name + " " + tag + "  &8»  &e$" + Fmt.money(pd.money));
            }
            if (count == 0) {
                send(s, "    &8No players assigned.");
            } else {
                send(s, "  &7Total: &a&l$" + Fmt.money(total) + "  &8|  &7Members: &f" + count);
            }
            send(s, "");
        }
    }

    private void teammax(CommandSender s, String[] args) {
        if (args.length < 2) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp teammax <number>");
            return;
        }
        Double n = num(args[1]);
        if (n == null) {
            send(s, Fmt.PREFIX + " &cInvalid number.");
            return;
        }
        if (n <= 0) {
            send(s, Fmt.PREFIX + " &cMust be at least 1.");
            return;
        }
        data.teamMax = n.intValue();
        send(s, Fmt.PREFIX + " &aMax per team: &e&l" + data.teamMax + "  &8|  &7Capacity: &f" + (data.teamMax * data.teamCount) + " &8(" + data.teamCount + " teams)");
    }

    private void teamcount(CommandSender s, String[] args) {
        if (args.length < 2) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp teamcount <1-9>");
            return;
        }
        Double n = num(args[1]);
        if (n == null) {
            send(s, Fmt.PREFIX + " &cInvalid number.");
            return;
        }
        if (n < 1) {
            send(s, Fmt.PREFIX + " &cMinimum is &e1&c.");
            return;
        }
        if (n > 9) {
            send(s, Fmt.PREFIX + " &cMaximum is &e9&c.");
            return;
        }
        data.teamCount = n.intValue();
        data.teamCountSet = true;
        send(s, "");
        send(s, Fmt.PREFIX + " &aTeam count: &e&l" + data.teamCount + "&a. Active teams:");
        int i = 1;
        for (String t : Teams.active(data.teamCount)) {
            send(s, "    " + Teams.color(t) + "&l" + (i++) + ". " + t);
        }
        send(s, "");
    }

    private void teamSet(CommandSender s, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("set") || args.length < 3) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp team set <player> <team>");
            return;
        }
        if (args.length < 4) {
            send(s, Fmt.PREFIX + " &cUsage: &f/moneysmp team set <player> <team>");
            send(s, "  &7Teams: &cRed &9Blue &aGreen &eYellow &5Purple &bAqua &6Orange &dPink &fWhite");
            return;
        }
        String name = args[2];
        String newTeam = Teams.normalise(args[3]);
        List<String> active = Teams.active(data.teamCount);
        if (!active.contains(newTeam)) {
            send(s, Fmt.PREFIX + " &cInvalid team &f" + newTeam + "&c. Active teams:");
            for (String t : active) send(s, "  " + Teams.color(t) + "&l" + t);
            return;
        }
        UUID uid = data.resolve(name);
        if (uid == null) {
            send(s, Fmt.PREFIX + " &cPlayer &f" + name + " &cnot found.");
            return;
        }
        Data.PlayerData pd = data.get(uid);
        pd.team = newTeam;
        Player target = Bukkit.getPlayer(uid);
        if (target != null) {
            plugin.sync(target);
            send(target, Fmt.PREFIX + " &7You have been moved to team " + Teams.color(newTeam) + "&l" + newTeam);
        }
        send(s, Fmt.PREFIX + " &aAssigned &f" + name + " &ato team " + Teams.color(newTeam) + "&l" + newTeam + "&a.");
    }

    private void randomteams(CommandSender s) {
        if (data.teamMax == null) {
            send(s, Fmt.PREFIX + " &cRun &f/moneysmp teammax <n> &cfirst.");
            return;
        }
        int max = data.teamMax;
        int tc = data.teamCount;
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        int cap = max * tc;
        if (online.size() > cap) {
            send(s, Fmt.PREFIX + " &cToo many players! Capacity: &f" + cap + " &8(" + tc + " x " + max + ")&7. Online: &f" + online.size());
            return;
        }
        List<String> active = Teams.active(tc);
        for (Data.PlayerData pd : data.players.values()) {
            if (pd.team != null && active.contains(pd.team)) pd.team = null;
        }
        for (Player p : online) data.get(p).team = null;

        int[] counts = new int[tc];
        Collections.shuffle(online);
        for (Player p : online) {
            List<Integer> open = new ArrayList<>();
            for (int i = 0; i < tc; i++) if (counts[i] < max) open.add(i);
            int ti = open.get(ThreadLocalRandom.current().nextInt(open.size()));
            counts[ti]++;
            data.get(p).team = active.get(ti);
        }

        broadcast("");
        broadcast(Fmt.PREFIX + " &a&l⚔  TEAMS HAVE BEEN RANDOMISED  ⚔");
        broadcast("");
        for (Player p : online) {
            plugin.sync(p);
            String t = data.team(p.getUniqueId());
            String col = Teams.color(t);
            broadcast("  " + col + "&l" + p.getName() + "  &8»  " + col + t);
            send(p, Fmt.PREFIX + " &7You are on team " + col + "&l" + t);
        }
        broadcast("");
    }
}
