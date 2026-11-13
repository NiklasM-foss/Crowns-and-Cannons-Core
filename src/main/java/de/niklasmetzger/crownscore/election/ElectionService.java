package de.niklasmetzger.crownscore.election;

import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Election;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.discord.DiscordWebhook;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.Names;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Beendet abgelaufene Wahlen und Abstimmungen, setzt Wahlsieger ins Amt. */
public final class ElectionService {
    private ElectionService() {}

    public static void everyMinute(MinecraftServer server) {
        CrownsData d = CrownsData.get(server);
        long now = TimeUtil.now();
        for (Election e : d.elections) {
            if (!e.beendet && e.ende <= now) finish(server, d, e);
        }
    }

    public static void finish(MinecraftServer server, CrownsData d, Election e) {
        e.beendet = true;
        Map<String, Integer> count = e.auszaehlung();
        int best = -1;
        List<String> winners = new ArrayList<>();
        for (Map.Entry<String, Integer> c : count.entrySet()) {
            if (c.getValue() > best) {
                best = c.getValue();
                winners.clear();
                winners.add(c.getKey());
            } else if (c.getValue() == best) {
                winners.add(c.getKey());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> c : count.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(c.getKey()).append(' ').append(c.getValue());
        }
        String wo = e.parlament ? "Parlament" : d.anzeige(e.nation);
        if (e.stimmen.isEmpty()) {
            e.ergebnis = "keine Stimmen abgegeben";
        } else if (winners.size() > 1) {
            e.ergebnis = "Unentschieden zwischen " + String.join(" und ", winners) + " (" + sb + ")";
        } else {
            e.ergebnis = winners.get(0) + " gewinnt (" + sb + ")";
            if (e.amt && !e.parlament) {
                Nation n = d.nations.get(e.nation);
                UUID winner = null;
                if (n != null) {
                    for (Map.Entry<UUID, String> m : n.mitglieder.entrySet()) {
                        if (m.getValue().equalsIgnoreCase(winners.get(0))) winner = m.getKey();
                    }
                }
                if (n != null && winner != null) {
                    n.chef = winner;
                    e.ergebnis += ", " + Names.of(server, winner) + " ist neuer Staatschef";
                } else {
                    e.ergebnis += ", Sieger ist kein Mitglied mehr, Amt bleibt unveraendert";
                }
            }
        }
        d.setDirty();
        Msg.broadcast(server, (e.parlament ? "Abstimmung" : "Wahl") + " \"" + e.titel + "\" in " + wo + " beendet: " + e.ergebnis);
        DiscordWebhook.news(":ballot_box: **" + (e.parlament ? "Parlamentsabstimmung" : "Wahl in " + wo) + "**: " + e.titel + "\nErgebnis: " + e.ergebnis
                + "\nBeteiligung: " + e.stimmen.size() + (e.parlament ? " Nationen" : " Stimmen"));
    }
}
