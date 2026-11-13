package de.niklasmetzger.crownscore.activity;

import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.ActivityRecord;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.discord.DiscordWebhook;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Zaehlt Spielzeit je Woche und schickt den Wochenbericht (Regel 7.2). */
public final class ActivityService {
    /** Zeitpunkt der letzten Buchung je Spieler. */
    private static final Map<UUID, Long> LAST_TICK = new HashMap<>();

    private ActivityService() {}

    public static void sessionStart(UUID id) {
        LAST_TICK.put(id, TimeUtil.now());
    }

    public static void sessionEnd(MinecraftServer server, UUID id) {
        book(server, id, TimeUtil.now());
        LAST_TICK.remove(id);
    }

    private static void book(MinecraftServer server, UUID id, long now) {
        Long last = LAST_TICK.get(id);
        if (last == null) return;
        long seconds = (now - last) / 1000;
        if (seconds <= 0) return;
        CrownsData d = CrownsData.get(server);
        ActivityRecord r = d.activity.computeIfAbsent(id, k -> new ActivityRecord());
        long[] w = r.woche(TimeUtil.weekKey(now));
        w[TimeUtil.isWeekend(now) ? 1 : 0] += seconds;
        r.zuletztOnline = now;
        LAST_TICK.put(id, now);
        d.setDirty();
    }

    public static void everyMinute(MinecraftServer server) {
        long now = TimeUtil.now();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!LAST_TICK.containsKey(p.getUUID())) LAST_TICK.put(p.getUUID(), now - 60_000L);
            book(server, p.getUUID(), now);
        }
        checkReport(server, now);
    }

    private static void checkReport(MinecraftServer server, long now) {
        CrownsData d = CrownsData.get(server);
        ZonedDateTime z = TimeUtil.zoned(now);
        DayOfWeek tag;
        try {
            tag = DayOfWeek.valueOf(CrownsConfig.BERICHT_WOCHENTAG.get().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            tag = DayOfWeek.SUNDAY;
        }
        if (z.getDayOfWeek() != tag || z.getHour() != CrownsConfig.BERICHT_STUNDE.get()) return;
        String week = TimeUtil.weekKey(now);
        if (week.equals(d.lastActivityReportWeek)) return;
        d.lastActivityReportWeek = week;
        d.setDirty();
        String report = report(server, week, now);
        DiscordWebhook.aktivitaet(report);
        Msg.broadcast(server, "Der Aktivitaetsbericht fuer " + week + " wurde erstellt. /aktivitaet zeigt deinen Stand.");
    }

    /** Baut den Bericht fuer eine Woche. */
    public static String report(MinecraftServer server, String week, long now) {
        CrownsData d = CrownsData.get(server);
        double minWoche = CrownsConfig.AKTIV_WOCHE_STUNDEN.get();
        double minWe = CrownsConfig.AKTIV_WOCHENENDE_STUNDEN.get();
        List<String> ok = new ArrayList<>();
        List<String> warn = new ArrayList<>();
        List<String> fehl = new ArrayList<>();
        List<String> pause = new ArrayList<>();
        // Alle bekannten Spieler: Nationen-Mitglieder plus jeder mit Aufzeichnung
        Map<UUID, String> all = new HashMap<>();
        for (Nation n : d.nations.values()) all.putAll(n.mitglieder);
        for (Map.Entry<UUID, ActivityRecord> e : d.activity.entrySet()) all.putIfAbsent(e.getKey(), e.getValue().name);
        for (Map.Entry<UUID, String> e : all.entrySet()) {
            ActivityRecord r = d.activity.get(e.getKey());
            Nation n = d.nationOf(e.getKey());
            String label = e.getValue() + (n == null ? "" : " (" + n.kuerzel + ")");
            if (r != null && r.pauseBis > now) {
                pause.add(label + " bis " + TimeUtil.fmtShort(r.pauseBis));
                continue;
            }
            long ges = r == null ? 0 : r.gesamt(week);
            long we = r == null ? 0 : r.wochenende(week);
            String zeit = Msg.hours(ges) + " gesamt, " + Msg.hours(we) + " Wochenende";
            boolean wocheOk = ges >= minWoche * 3600;
            boolean weOk = we >= minWe * 3600;
            if (wocheOk && weOk) ok.add(label + ": " + zeit);
            else if (ges == 0) fehl.add(label + ": gar nicht online");
            else warn.add(label + ": " + zeit + (wocheOk ? "" : ", Wochenziel verfehlt") + (weOk ? "" : ", Wochenendziel verfehlt"));
        }
        StringBuilder sb = new StringBuilder();
        sb.append(":calendar: **Aktivitaetsbericht ").append(week).append("** (Ziel: ").append(minWoche).append(" h pro Woche, ")
                .append(minWe).append(" h am Wochenende)\n");
        if (!ok.isEmpty()) sb.append("\n:white_check_mark: **Erfuellt**\n").append(String.join("\n", ok)).append('\n');
        if (!warn.isEmpty()) sb.append("\n:warning: **Teilweise**\n").append(String.join("\n", warn)).append('\n');
        if (!fehl.isEmpty()) sb.append("\n:x: **Nicht online**\n").append(String.join("\n", fehl)).append('\n');
        if (!pause.isEmpty()) sb.append("\n:zzz: **Angemeldete Pause**\n").append(String.join("\n", pause)).append('\n');
        return sb.toString();
    }
}
