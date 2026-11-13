package de.niklasmetzger.crownscore.util;

import de.niklasmetzger.crownscore.config.CrownsConfig;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;

/** Zeitfunktionen in der konfigurierten Zeitzone. */
public final class TimeUtil {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter FMT_SHORT = DateTimeFormatter.ofPattern("dd.MM. HH:mm");

    private TimeUtil() {}

    public static long now() {
        return System.currentTimeMillis();
    }

    public static ZoneId zone() {
        try {
            return ZoneId.of(CrownsConfig.ZEITZONE.get());
        } catch (Exception e) {
            return ZoneId.of("Europe/Berlin");
        }
    }

    public static ZonedDateTime zoned(long millis) {
        return Instant.ofEpochMilli(millis).atZone(zone());
    }

    public static String fmt(long millis) {
        return FMT.format(zoned(millis));
    }

    public static String fmtShort(long millis) {
        return FMT_SHORT.format(zoned(millis));
    }

    /** ISO-Kalenderwoche als Schluessel, z.B. 2026-W46. */
    public static String weekKey(long millis) {
        ZonedDateTime z = zoned(millis);
        return z.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", z.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    public static String previousWeekKey(long millis) {
        return weekKey(millis - 7L * 24 * 3600 * 1000);
    }

    public static boolean isWeekend(long millis) {
        DayOfWeek d = zoned(millis).getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }

    public static String duration(long millis) {
        long s = Math.max(0, millis / 1000);
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "min";
        return m + "min";
    }
}
