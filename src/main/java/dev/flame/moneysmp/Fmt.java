package dev.flame.moneysmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

final class Fmt {
    static final String PREFIX = "&8[&a&lMoneySMP&8]&r";

    private Fmt() {}

    static Component c(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    static Component p(String s) {
        return c(PREFIX + " " + s);
    }

    static String money(double n) {
        return String.format("%,d", (long) Math.floor(n));
    }

    // 30s / 10m / 2h / 1d -> seconds, -1 if unparsable
    static long parseTime(String raw) {
        if (raw.length() < 2) return -1;
        long num;
        try {
            num = (long) Double.parseDouble(raw.substring(0, raw.length() - 1));
        } catch (NumberFormatException e) {
            return -1;
        }
        return switch (raw.charAt(raw.length() - 1)) {
            case 's' -> num;
            case 'm' -> num * 60;
            case 'h' -> num * 3600;
            case 'd' -> num * 86400;
            default -> -1;
        };
    }

    static String timeAgo(long total) {
        if (total < 60) return total + "s";
        if (total < 3600) return (total / 60) + "m " + (total % 60) + "s";
        if (total < 86400) return (total / 3600) + "h " + ((total % 3600) / 60) + "m";
        return (total / 86400) + "d " + ((total % 86400) / 3600) + "h";
    }
}
