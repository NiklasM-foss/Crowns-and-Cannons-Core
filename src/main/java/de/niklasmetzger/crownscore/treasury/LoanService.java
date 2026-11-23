package de.niklasmetzger.crownscore.treasury;

import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.Treasury;
import de.niklasmetzger.crownscore.discord.DiscordWebhook;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.server.MinecraftServer;

import java.util.Iterator;
import java.util.Map;

/** Darlehen der Zentralbank: Aufnahme, woechentliche Raten, Verzug. */
public final class LoanService {
    private static final long WOCHE = 7L * 24 * 3600 * 1000;

    private LoanService() {}

    public static Treasury.Loan take(CrownsData d, Nation n, double betrag) {
        Treasury t = d.treasury(n.key());
        Treasury.Loan l = new Treasury.Loan();
        l.id = t.nextLoanId++;
        l.betrag = betrag;
        l.zinsProzent = CrownsConfig.DARLEHEN_ZINS_PROZENT.get();
        l.wochen = CrownsConfig.DARLEHEN_WOCHEN.get();
        l.gesamt = Math.round(betrag * (1 + l.zinsProzent / 100.0) * 100) / 100.0;
        l.rest = l.gesamt;
        l.rate = Math.round(l.gesamt / l.wochen * 100) / 100.0;
        l.aufgenommen = TimeUtil.now();
        l.naechsteRate = l.aufgenommen + WOCHE;
        t.darlehen.add(l);
        t.stand += betrag;
        d.setDirty();
        return l;
    }

    public static void everyMinute(MinecraftServer server) {
        CrownsData d = CrownsData.get(server);
        long now = TimeUtil.now();
        for (Map.Entry<String, Treasury> e : d.treasuries.entrySet()) {
            Treasury t = e.getValue();
            Nation n = d.nations.get(e.getKey());
            if (n == null) continue;
            Iterator<Treasury.Loan> it = t.darlehen.iterator();
            while (it.hasNext()) {
                Treasury.Loan l = it.next();
                if (l.naechsteRate > now) continue;
                // Im Verzug sind die offenen Raten (abzueglich schon getilgter Betraege) mit faellig
                double offen = l.verzug ? Math.max(0, l.rate * l.verzugRaten - l.verzugBezahlt) : 0;
                double faellig = Math.min(l.rate + offen, l.rest);
                if (l.verzug && t.stand + 1e-9 < faellig && t.stand + 1e-9 >= Math.min(l.rate, l.rest)) {
                    // Teilzahlung: alles, was die Kasse hergibt, wird auf den Verzug angerechnet
                    double z = Math.floor(Math.min(t.stand, faellig) * 100 + 1e-6) / 100.0;
                    t.stand -= z;
                    l.rest = Math.round((l.rest - z) * 100) / 100.0;
                    // die laufende Rate kommt als weitere offene Rate dazu, der gezahlte Betrag wird angerechnet
                    l.verzugRaten++;
                    l.verzugBezahlt = Math.round((l.verzugBezahlt + z) * 100) / 100.0;
                    l.naechsteRate += WOCHE;
                    double fehlt = Math.min(l.rest, Math.max(0, l.rate * l.verzugRaten - l.verzugBezahlt));
                    if (l.rest <= 0.004) {
                        it.remove();
                        Msg.broadcast(server, n.name + " hat Darlehen #" + l.id + " vollstaendig zurueckgezahlt.");
                        DiscordWebhook.news(":bank: **" + n.name + "** hat das Darlehen Nr. " + l.id + " ueber " + Msg.money(l.betrag) + " zurueckgezahlt.");
                        d.setDirty();
                        continue;
                    }
                    Msg.nation(server, n, "§eZentralbank: " + Msg.money(z) + " fuer Darlehen #" + l.id + " abgebucht, bis zum Ende des Verzugs fehlen " + Msg.money(fehlt) + ".");
                    if (fehlt <= 0.004) {
                        l.verzug = false;
                        l.verzugRaten = 0;
                        l.verzugBezahlt = 0;
                    }
                    d.setDirty();
                    continue;
                }
                if (t.stand + 1e-9 >= faellig) {
                    t.stand -= faellig;
                    l.rest = Math.round((l.rest - faellig) * 100) / 100.0;
                    l.verzug = false;
                    l.verzugRaten = 0;
                    l.verzugBezahlt = 0;
                    l.naechsteRate += WOCHE;
                    Msg.nation(server, n, "§7Zentralbank: Rate " + Msg.money(faellig) + " fuer Darlehen #" + l.id + " abgebucht. Restschuld " + Msg.money(l.rest) + ".");
                    if (l.rest <= 0.004) {
                        it.remove();
                        Msg.broadcast(server, n.name + " hat Darlehen #" + l.id + " vollstaendig zurueckgezahlt.");
                        DiscordWebhook.news(":bank: **" + n.name + "** hat das Darlehen Nr. " + l.id + " ueber " + Msg.money(l.betrag) + " zurueckgezahlt.");
                    }
                } else {
                    l.verzug = true;
                    l.verzugRaten++;
                    l.naechsteRate += WOCHE;
                    Msg.broadcast(server, "§c" + n.name + " ist mit Darlehen #" + l.id + " im Zahlungsverzug (" + l.verzugRaten + ". Rate offen, Staatskasse " + Msg.money(t.stand) + ").");
                    DiscordWebhook.news(":rotating_light: **" + n.name + "** ist mit Darlehen Nr. " + l.id + " im Zahlungsverzug. Offene Rate: "
                            + Msg.money(faellig) + ", Restschuld " + Msg.money(l.rest) + ".");
                }
                d.setDirty();
            }
        }
    }
}
