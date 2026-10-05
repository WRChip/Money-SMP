package dev.flame.moneysmp;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

import java.util.List;

final class Teams {
    static final List<String> NAMES = List.of("Red", "Blue", "Green", "Yellow", "Purple", "Aqua", "Orange", "Pink", "White");

    private static final ChatFormatting[] MC_COLORS = {
        ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.YELLOW,
        ChatFormatting.DARK_PURPLE, ChatFormatting.AQUA, ChatFormatting.GOLD, ChatFormatting.LIGHT_PURPLE,
        ChatFormatting.WHITE
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

    static void setup(MinecraftServer server) {
        ServerScoreboard sb = server.getScoreboard();
        for (int i = 0; i < NAMES.size(); i++) {
            String id = "msmp_" + NAMES.get(i);
            PlayerTeam t = sb.getPlayerTeam(id);
            if (t == null) t = sb.addPlayerTeam(id);
            t.setColor(MC_COLORS[i]);
            t.setAllowFriendlyFire(true);
            t.setNameTagVisibility(Team.Visibility.ALWAYS);
        }
    }

    static void leave(MinecraftServer server, ServerPlayer p) {
        ServerScoreboard sb = server.getScoreboard();
        PlayerTeam t = sb.getPlayersTeam(p.getScoreboardName());
        if (t != null) sb.removePlayerFromTeam(p.getScoreboardName(), t);
    }

    // the team colour is what colours the tab list and name tag, so no separate list-name step is needed
    static void sync(MinecraftServer server, ServerPlayer p, String team) {
        if (team == null) {
            leave(server, p);
            return;
        }
        ServerScoreboard sb = server.getScoreboard();
        PlayerTeam t = sb.getPlayerTeam("msmp_" + team);
        if (t != null) sb.addPlayerToTeam(p.getScoreboardName(), t);
    }
}
