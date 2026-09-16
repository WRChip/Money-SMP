package dev.flame.moneysmp;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;

final class Teams {
    static final List<String> NAMES = List.of("Red", "Blue", "Green", "Yellow", "Purple", "Aqua", "Orange", "Pink", "White");

    private static final NamedTextColor[] MC_COLORS = {
        NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.YELLOW,
        NamedTextColor.DARK_PURPLE, NamedTextColor.AQUA, NamedTextColor.GOLD, NamedTextColor.LIGHT_PURPLE,
        NamedTextColor.WHITE
    };

    private Teams() {}

    static String color(String team) {
        if (team == null) return "&7";
        return switch (team) {
            case "Red" -> "&c";
            case "Blue" -> "&9";
            case "Green" -> "&a";
            case "Yellow" -> "&e";
            case "Purple" -> "&5";
            case "Aqua" -> "&b";
            case "Orange" -> "&6";
            case "Pink" -> "&d";
            case "White" -> "&f";
            default -> "&7";
        };
    }

    static List<String> active(int count) {
        return NAMES.subList(0, count);
    }

    // same capitalisation the script does: "rED" -> "Red"
    static String normalise(String in) {
        if (in.isEmpty()) return in;
        return Character.toUpperCase(in.charAt(0)) + in.substring(1).toLowerCase();
    }

    static Scoreboard board() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    static void setup() {
        Scoreboard sb = board();
        for (int i = 0; i < NAMES.size(); i++) {
            String id = "msmp_" + NAMES.get(i);
            Team t = sb.getTeam(id);
            if (t == null) t = sb.registerNewTeam(id);
            t.color(MC_COLORS[i]);
            t.setAllowFriendlyFire(true);
            t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        }
    }

    static void leave(Player p) {
        Team t = board().getEntryTeam(p.getName());
        if (t != null) t.removeEntry(p.getName());
    }

    static void sync(Player p, String team) {
        if (team != null) {
            Team t = board().getTeam("msmp_" + team);
            if (t != null) t.addEntry(p.getName());
            p.playerListName(Fmt.c(color(team) + p.getName()));
        } else {
            leave(p);
            p.playerListName(null);
        }
    }
}
