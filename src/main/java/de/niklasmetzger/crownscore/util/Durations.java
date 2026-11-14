package de.niklasmetzger.crownscore.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Zeitspannen wie 2w3d4h30m10s lesen und kurz ausgeben. */
public final class Durations {
    private static final Pattern PART = Pattern.compile("(\\d+(?:[.,]\\d+)?)([wdhms])");

    private Durations() {}

    /** Liefert Millisekunden oder -1, wenn der Text keine gueltige Zeitspanne ist. */
    public static long parse(String text) {
        if (text == null) return -1;
        String s = text.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return -1;
        if (s.chars().allMatch(Character::isDigit)) return Long.parseLong(s) * 60_000L; // reine Zahl = Minuten
        Matcher m = PART.matcher(s);
        int pos = 0;
        double total = 0;
        while (m.find()) {
            if (m.start() != pos) return -1;
            double v = Double.parseDouble(m.group(1).replace(',', '.'));
            total += v * switch (m.group(2)) {
                case "w" -> 604_800_000L;
                case "d" -> 86_400_000L;
                case "h" -> 3_600_000L;
                case "m" -> 60_000L;
                default -> 1_000L;
            };
            pos = m.end();
        }
        return pos == s.length() && total > 0 ? (long) total : -1;
    }

    /** Kurze Angabe wie 3m, 2,5h oder 4,1d. */
    public static String ago(long millis) {
        double s = Math.max(0, millis) / 1000.0;
        if (s < 60) return String.format(Locale.GERMANY, "%.0fs", s);
        if (s < 3600) return String.format(Locale.GERMANY, "%.0fm", s / 60);
        if (s < 86400) return String.format(Locale.GERMANY, "%.1fh", s / 3600);
        return String.format(Locale.GERMANY, "%.1fd", s / 86400);
    }

    /** Lesbare Dauer wie 1 Tag 2 Std 5 Min. */
    public static String human(long millis) {
        long m = Math.max(0, millis) / 60_000L;
        long d = m / 1440;
        long h = (m % 1440) / 60;
        long min = m % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append(d == 1 ? " Tag " : " Tage ");
        if (h > 0) sb.append(h).append(" Std ");
        if (min > 0 || sb.length() == 0) sb.append(min).append(" Min");
        return sb.toString().trim();
    }
}
