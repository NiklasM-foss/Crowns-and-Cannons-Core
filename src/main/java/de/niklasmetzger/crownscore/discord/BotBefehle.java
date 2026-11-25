package de.niklasmetzger.crownscore.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.activity.ActivityService;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.ActivityRecord;
import de.niklasmetzger.crownscore.data.Besatzung;
import de.niklasmetzger.crownscore.data.Contract;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Election;
import de.niklasmetzger.crownscore.data.EvidenceEntry;
import de.niklasmetzger.crownscore.data.Mail;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.Stadt;
import de.niklasmetzger.crownscore.data.War;
import de.niklasmetzger.crownscore.economy.Economy;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.Names;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Slash-Befehle, Knoepfe und Formulare des Discord-Bots. Spieldaten werden nur auf dem Server-Thread gelesen. */
final class BotBefehle {
    private static final int PRIVAT = 64;
    private final DiscordBot bot;
    private final MinecraftServer server;

    BotBefehle(DiscordBot bot) {
        this.bot = bot;
        this.server = bot.server;
    }

    // ================================================================ Definitionen

    static JsonArray definitionen() {
        JsonArray a = new JsonArray();
        a.add(befehl("verknuepfen", "Discord mit deinem Minecraft-Konto verbinden"));
        a.add(befehl("ingamezeit", "Spielzeit diese und letzte Woche (Werktage und Wochenende)",
                opt(3, "spieler", "Anderer Spieler (nur Admins)", false, true)));
        a.add(befehl("online", "Wer gerade auf dem Server ist"));
        a.add(befehl("pause", "Pause von der Aktivitaetsregel anmelden (0 Tage = beenden)",
                opt(4, "tage", "Wie viele Tage", true, false), opt(3, "grund", "Warum", false, false)));
        a.add(befehl("konto", "Dein Kontostand"));
        a.add(befehl("postfach", "Ungelesene Briefe deiner Nation (Staatschef und Beamte)"));
        a.add(befehl("nation", "Staatschef, Buerger, Staedte und Staatskasse einer Nation",
                opt(3, "name", "Name oder Kuerzel", true, true)));
        a.add(befehl("kriege", "Laufende Kriege"));
        a.add(befehl("besetzungen", "Laufende Besetzungen mit Restzeit"));
        a.add(befehl("staedte", "Anerkannte Staedte", opt(3, "nation", "Nur diese Nation", false, true)));
        a.add(befehl("wahlen", "Laufende Wahlen und Abstimmungen, abstimmen per Knopf"));
        a.add(befehl("vertrag", "Einen hinterlegten Vertrag lesen", opt(4, "nr", "Nummer des Vertrags", true, false)));
        a.add(befehl("karte", "Weltkarte mit allen Nationen und Staedten"));
        // Admins
        a.add(befehl("wochenbericht", "(Admin) Aktivitaetsbericht in den Kanal schicken",
                opt(5, "letzte", "Letzte statt laufende Woche", false, false)));
        a.add(befehl("antraege", "(Admin) Offene Stadtantraege mit Knoepfen"));
        a.add(befehl("akte", "(Admin) Beweisakte eines Spielers", opt(3, "spieler", "Spieler", true, true),
                opt(4, "anzahl", "Wie viele Eintraege (Standard 15)", false, false)));
        a.add(befehl("kick", "(Admin) Spieler vom Server werfen", opt(3, "spieler", "Spieler", true, true), opt(3, "grund", "Grund", false, false)));
        a.add(befehl("ban", "(Admin) Spieler bannen", opt(3, "spieler", "Spieler", true, true), opt(3, "grund", "Grund", false, false)));
        a.add(befehl("say", "(Admin) Nachricht an alle im Spiel", opt(3, "text", "Text", true, false)));
        a.add(befehl("befehl", "(Admin) Serverbefehl ausfuehren", opt(3, "befehl", "Befehl ohne /", true, false)));
        a.add(befehl("geld", "(Admin) Geld geben (negativ = abziehen)", opt(3, "spieler", "Spieler", true, true),
                opt(10, "betrag", "Betrag", true, false)));
        a.add(befehl("staatskasse", "(Admin) Staatskasse einer Nation setzen", opt(3, "nation", "Nation", true, true),
                opt(10, "betrag", "Neuer Stand", true, false)));
        return a;
    }

    private static JsonObject befehl(String name, String beschreibung, JsonObject... optionen) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("description", beschreibung);
        o.addProperty("type", 1);
        o.addProperty("dm_permission", false);
        JsonArray opts = new JsonArray();
        for (JsonObject x : optionen) opts.add(x);
        if (!opts.isEmpty()) o.add("options", opts);
        return o;
    }

    private static JsonObject opt(int typ, String name, String beschreibung, boolean pflicht, boolean auto) {
        JsonObject o = new JsonObject();
        o.addProperty("type", typ);
        o.addProperty("name", name);
        o.addProperty("description", beschreibung);
        o.addProperty("required", pflicht);
        if (auto) o.addProperty("autocomplete", true);
        return o;
    }

    static JsonObject knopf(int stil, String text, String id) {
        JsonObject o = new JsonObject();
        o.addProperty("type", 2);
        o.addProperty("style", stil);
        o.addProperty("label", DiscordRest.kuerzen(text, 80));
        o.addProperty("custom_id", id.length() > 100 ? id.substring(0, 100) : id);
        return o;
    }

    static JsonArray zeile(JsonObject... knoepfe) {
        JsonArray zeilen = new JsonArray();
        JsonObject z = new JsonObject();
        z.addProperty("type", 1);
        JsonArray k = new JsonArray();
        for (JsonObject x : knoepfe) k.add(x);
        z.add("components", k);
        zeilen.add(z);
        return zeilen;
    }

    // ================================================================ Antworten

    private record Antwort(JsonObject json) {}

    private static Antwort text(String s) {
        return text(s, true, null);
    }

    private static Antwort text(String s, boolean privat, JsonArray knoepfe) {
        JsonObject data = new JsonObject();
        data.addProperty("content", DiscordRest.kuerzen(s == null || s.isBlank() ? "-" : s, 1990));
        data.add("allowed_mentions", DiscordRest.keinePings());
        if (privat) data.addProperty("flags", PRIVAT);
        if (knoepfe != null) data.add("components", knoepfe);
        JsonObject o = new JsonObject();
        o.addProperty("type", 4);
        o.add("data", data);
        return new Antwort(o);
    }

    /** Nachricht mit den Knoepfen ersetzen (Antrag erledigt). */
    private static Antwort aktualisieren(String s) {
        JsonObject data = new JsonObject();
        data.addProperty("content", DiscordRest.kuerzen(s, 1990));
        data.add("components", new JsonArray());
        data.add("allowed_mentions", DiscordRest.keinePings());
        JsonObject o = new JsonObject();
        o.addProperty("type", 7);
        o.add("data", data);
        return new Antwort(o);
    }

    private static String code(String s) {
        return "```\n" + DiscordRest.kuerzen(s.replace("```", "'''"), 1900) + "\n```";
    }

    // ================================================================ Eingang

    /** Vom Gateway-Thread: Daten auf dem Server-Thread lesen, Antwort im REST-Thread schicken. */
    void interaktion(JsonObject d) {
        String id = d.get("id").getAsString(), token = d.get("token").getAsString();
        int typ = d.get("type").getAsInt();
        JsonObject user = d.has("member") ? d.getAsJsonObject("member").getAsJsonObject("user") : d.getAsJsonObject("user");
        String nutzer = user.get("id").getAsString();
        String nutzerName = user.has("global_name") && !user.get("global_name").isJsonNull() ? user.get("global_name").getAsString() : user.get("username").getAsString();
        JsonArray rollen = d.has("member") ? d.getAsJsonObject("member").getAsJsonArray("roles") : new JsonArray();
        JsonObject data = d.getAsJsonObject("data");
        Ctx c = new Ctx(id, token, nutzer, nutzerName, rollen, data, d);
        if (typ == 2 && "karte".equals(data.get("name").getAsString())) {
            karte(c);
            return;
        }
        server.execute(() -> {
            Antwort a;
            try {
                a = switch (typ) {
                    case 2 -> befehl(c);
                    case 3 -> knopfGedrueckt(c);
                    case 4 -> vorschlaege(c);
                    case 5 -> formular(c);
                    default -> null;
                };
            } catch (Exception e) {
                CrownsCore.LOGGER.warn("Discord-Bot: Fehler bei einer Interaktion", e);
                a = text("Da ist etwas schiefgegangen: " + e.getClass().getSimpleName());
            }
            if (a != null) bot.rest.antwort(id, token, a.json());
        });
    }

    private record Ctx(String id, String token, String nutzer, String nutzerName, JsonArray rollen, JsonObject data, JsonObject roh) {
        String s(String name) {
            JsonElement e = wert(name);
            return e == null ? null : e.getAsString().trim();
        }

        Long i(String name) {
            JsonElement e = wert(name);
            return e == null ? null : e.getAsLong();
        }

        Double n(String name) {
            JsonElement e = wert(name);
            return e == null ? null : e.getAsDouble();
        }

        Boolean b(String name) {
            JsonElement e = wert(name);
            return e == null ? null : e.getAsBoolean();
        }

        private JsonElement wert(String name) {
            if (!data.has("options")) return null;
            for (JsonElement o : data.getAsJsonArray("options")) {
                JsonObject x = o.getAsJsonObject();
                if (x.get("name").getAsString().equals(name) && x.has("value")) return x.get("value");
            }
            return null;
        }
    }

    private CrownsData d() {
        return CrownsData.get(server);
    }

    private Antwort befehl(Ctx c) {
        String name = c.data().get("name").getAsString();
        Supplier<Antwort> admin = () -> bot.admin(c.nutzer(), c.rollen()) ? null : text("Das duerfen nur Admins.");
        return switch (name) {
            case "verknuepfen" -> verknuepfen(c);
            case "ingamezeit" -> ingamezeit(c);
            case "online" -> online();
            case "pause" -> pause(c);
            case "konto" -> konto(c);
            case "postfach" -> postfach(c);
            case "nation" -> nation(c.s("name"));
            case "kriege" -> kriege();
            case "besetzungen" -> besetzungen();
            case "staedte" -> staedte(c.s("nation"));
            case "wahlen" -> wahlen(c);
            case "vertrag" -> vertrag(c.i("nr"));
            default -> {
                Antwort nein = admin.get();
                if (nein != null) yield nein;
                yield switch (name) {
                    case "wochenbericht" -> wochenbericht(Boolean.TRUE.equals(c.b("letzte")));
                    case "antraege" -> antraege();
                    case "akte" -> akte(c.s("spieler"), c.i("anzahl"));
                    case "kick", "ban" -> !c.s("spieler").matches("[A-Za-z0-9_]{1,16}") ? text("Bitte einen einzelnen Spielernamen angeben, keine Selektoren.")
                            : konsole(c, name + " " + c.s("spieler") + (c.s("grund") == null ? "" : " " + c.s("grund")));
                    case "say" -> konsole(c, "say " + c.s("text"));
                    case "befehl" -> konsole(c, c.s("befehl").replaceFirst("^/", ""));
                    case "geld" -> geld(c);
                    case "staatskasse" -> staatskasse(c);
                    default -> text("Unbekannter Befehl.");
                };
            }
        };
    }

    // ================================================================ Fuer alle

    private Antwort verknuepfen(Ctx c) {
        UUID schon = bot.verknuepft(c.nutzer());
        String code = DiscordBot.neuerCode(c.nutzer(), c.nutzerName());
        return text((schon == null ? "" : "Du bist schon mit **" + Names.of(server, schon) + "** verknuepft. Mit dem Code wechselst du das Konto.\n")
                + "Gib im Spiel ein: `/discord verknuepfen " + code + "` (10 Minuten gueltig).");
    }

    private UUID spieler(String name) {
        if (name == null) return null;
        Optional<UUID> o = Names.lookup(server, name);
        if (o.isPresent()) return o.get();
        for (Nation n : d().nations.values()) {
            for (Map.Entry<UUID, String> e : n.mitglieder.entrySet()) if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
        }
        for (Map.Entry<UUID, ActivityRecord> e : d().activity.entrySet()) if (e.getValue().name.equalsIgnoreCase(name)) return e.getKey();
        return null;
    }

    private Nation nationNach(String name) {
        if (name == null) return null;
        for (Nation n : d().nations.values()) {
            if (n.name.equalsIgnoreCase(name) || n.kuerzel.equalsIgnoreCase(name) || n.key().equalsIgnoreCase(name)) return n;
        }
        return null;
    }

    private static final String NICHT_VERKNUEPFT = "Dein Discord ist noch nicht mit Minecraft verknuepft. Erst /verknuepfen.";

    private Antwort ingamezeit(Ctx c) {
        UUID ich = bot.verknuepft(c.nutzer());
        UUID wer = ich;
        if (c.s("spieler") != null) {
            wer = spieler(c.s("spieler"));
            if (wer == null) return text("Spieler \"" + c.s("spieler") + "\" kenne ich nicht.");
            if (!wer.equals(ich) && !bot.admin(c.nutzer(), c.rollen())) return text("Die Zeit anderer Spieler sehen nur Admins.");
        }
        if (wer == null) return text(NICHT_VERKNUEPFT);
        long jetzt = TimeUtil.now();
        ActivityRecord r = d().activity.get(wer);
        String woche = TimeUtil.weekKey(jetzt), vorher = TimeUtil.previousWeekKey(jetzt);
        double minWoche = CrownsConfig.AKTIV_WOCHE_STUNDEN.get(), minWe = CrownsConfig.AKTIV_WOCHENENDE_STUNDEN.get();
        StringBuilder sb = new StringBuilder("**Spielzeit von " + Names.of(server, wer) + "** (Ziel: " + minWoche + " h pro Woche, davon " + minWe + " h am Wochenende)\n");
        for (String[] w : new String[][]{{woche, "Diese Woche"}, {vorher, "Letzte Woche"}}) {
            long[] z = r == null || !r.wochen.containsKey(w[0]) ? new long[2] : r.wochen.get(w[0]);
            sb.append(w[1]).append(" (").append(w[0]).append("): **").append(Msg.hours(z[0] + z[1])).append("**, in der Woche ")
                    .append(Msg.hours(z[0])).append(", am Wochenende ").append(Msg.hours(z[1])).append('\n');
        }
        if (r != null && r.pauseBis > jetzt) {
            sb.append("Pause bis ").append(TimeUtil.fmt(r.pauseBis)).append(r.pauseGrund.isBlank() ? "" : " (" + r.pauseGrund + ")").append('\n');
        }
        if (r != null && r.zuletztOnline > 0) sb.append("Zuletzt online: ").append(TimeUtil.fmt(r.zuletztOnline));
        return text(sb.toString());
    }

    private Antwort online() {
        List<ServerPlayer> ps = server.getPlayerList().getPlayers();
        if (ps.isEmpty()) return text("Gerade ist niemand online.", false, null);
        List<String> l = new ArrayList<>();
        for (ServerPlayer p : ps) {
            Nation n = d().nationOf(p.getUUID());
            var modus = de.niklasmetzger.crownscore.mode.ModeService.mode(p.getUUID());
            l.add((n == null ? "" : "[" + n.kuerzel + "] ") + p.getGameProfile().getName() + (modus == null ? "" : " (" + modus.name() + ")"));
        }
        return text("**" + ps.size() + " online:** " + String.join(", ", l), false, null);
    }

    private Antwort pause(Ctx c) {
        UUID ich = bot.verknuepft(c.nutzer());
        if (ich == null) return text(NICHT_VERKNUEPFT);
        long tage = c.i("tage") == null ? 0 : c.i("tage");
        int max = CrownsConfig.BOT_PAUSE_MAX.get();
        if (tage < 0 || tage > max) return text("Hoechstens " + max + " Tage. Laenger geht nur ueber einen Admin.");
        String grund = c.s("grund") == null ? "" : c.s("grund").replace("@", "@​");
        if (tage > 0 && grund.isBlank()) return text("Bitte einen Grund angeben.");
        CrownsData d = d();
        ActivityRecord r = d.activity.computeIfAbsent(ich, k -> new ActivityRecord());
        String name = Names.of(server, ich);
        if (r.name.isEmpty()) r.name = name;
        r.pauseBis = tage == 0 ? 0 : TimeUtil.now() + tage * 86_400_000L;
        r.pauseGrund = tage == 0 ? "" : DiscordRest.kuerzen(grund, 200);
        d.setDirty();
        String meldung = tage == 0 ? ":arrow_forward: " + name + " hat die Pause beendet."
                : ":zzz: " + name + " meldet Pause bis " + TimeUtil.fmt(r.pauseBis) + ": " + r.pauseGrund;
        DiscordBot.kanal(CrownsConfig.BOT_KANAL_AKTIVITAET.get(), meldung);
        return text(tage == 0 ? "Pause beendet." : "Pause bis " + TimeUtil.fmt(r.pauseBis) + " angemeldet. Die Admins sehen den Grund.");
    }

    private Antwort konto(Ctx c) {
        UUID ich = bot.verknuepft(c.nutzer());
        if (ich == null) return text(NICHT_VERKNUEPFT);
        ServerPlayer p = server.getPlayerList().getPlayer(ich);
        Double offen = d().offeneZahlungen.get(ich);
        String zusatz = offen == null ? "" : "\nNoch nicht gebucht (beim naechsten Einloggen): " + Msg.money(offen);
        if (p != null) return text("Kontostand: **" + Msg.money(Economy.get().balance(p)) + "**" + zusatz);
        double[] k = d().kontoStand.get(ich);
        if (k == null) return text("Dein Kontostand ist noch nicht bekannt. Er wird gespeichert, sobald du einmal online warst." + zusatz);
        return text("Kontostand: **" + Msg.money(k[0]) + "** (Stand " + TimeUtil.fmt((long) k[1]) + ", als du zuletzt online warst)" + zusatz);
    }

    private Antwort postfach(Ctx c) {
        UUID ich = bot.verknuepft(c.nutzer());
        if (ich == null) return text(NICHT_VERKNUEPFT);
        CrownsData d = d();
        StringBuilder sb = new StringBuilder();
        long eigene = de.niklasmetzger.crownscore.handel.Postfach.von(d, ich).stream().filter(x -> !x.gelesen).count();
        if (eigene > 0) sb.append("Du hast ").append(eigene).append(" ungelesene Nachricht(en) im /postfach im Spiel.\n\n");
        Nation n = d.nationOf(ich);
        if (n == null || !n.isBeamter(ich)) {
            return text(sb + "Briefe der Nation lesen nur Staatschef und Beamte.");
        }
        List<Mail> neu = de.niklasmetzger.crownscore.handel.Postfach.briefe(d, n).stream().filter(m -> !m.gelesen).toList();
        if (neu.isEmpty()) return text(sb + "Keine ungelesenen Briefe an " + n.name + ".");
        sb.append("**Ungelesene Briefe an ").append(n.name).append("** (jetzt als gelesen markiert)\n");
        int i = 0;
        for (Mail m : neu) {
            if (i++ >= 8) {
                sb.append("... und ").append(neu.size() - 8).append(" weitere, siehe /post im Spiel.");
                break;
            }
            sb.append("\n**Von ").append(m.vonName).append(m.vonNation.isBlank() ? "" : " (" + d.anzeige(m.vonNation) + ")").append("**, ")
                    .append(TimeUtil.fmt(m.zeit)).append(":\n").append(DiscordRest.kuerzen(m.text, 400)).append('\n');
            m.gelesen = true;
        }
        d.setDirty();
        return text(sb.toString());
    }

    private Antwort nation(String name) {
        Nation n = nationNach(name);
        if (n == null) return text("Keine Nation \"" + name + "\".");
        CrownsData d = d();
        List<String> beamte = n.beamte.stream().map(u -> Names.of(server, u)).toList();
        List<String> buerger = new ArrayList<>(n.mitglieder.values());
        List<String> staedte = de.niklasmetzger.crownscore.handel.StadtService.von(d, n.key()).stream()
                .filter(s -> s.bestaetigt).map(s -> s.name).toList();
        List<String> kriege = d.wars.stream().filter(w -> w.aktiv && w.beteiligt(n.key())).map(w -> d.anzeige(w.gegner(n.key()))).toList();
        String sb = "**" + n.name + "** [" + n.kuerzel + "]\n"
                + "Staatschef: " + (n.chef == null ? "niemand" : Names.of(server, n.chef)) + "\n"
                + "Beamte: " + (beamte.isEmpty() ? "keine" : String.join(", ", beamte)) + "\n"
                + "Buerger (" + buerger.size() + "): " + DiscordRest.kuerzen(String.join(", ", buerger), 900) + "\n"
                + "Staedte (" + staedte.size() + "): " + (staedte.isEmpty() ? "keine" : String.join(", ", staedte)) + "\n"
                + "Laender: " + (n.laender.isEmpty() ? "keine" : String.join(", ", n.laender)) + "\n"
                + "Staatskasse: " + Msg.money(de.niklasmetzger.crownscore.treasury.TreasuryService.stand(d, n.key())) + "\n"
                + "Im Krieg mit: " + (kriege.isEmpty() ? "niemandem" : String.join(", ", kriege));
        return text(sb, false, null);
    }

    private Antwort kriege() {
        CrownsData d = d();
        List<String> l = new ArrayList<>();
        for (War w : d.wars) {
            if (!w.aktiv) continue;
            l.add("Nr. " + w.id + ": **" + d.anzeige(w.angreifer) + "** gegen **" + d.anzeige(w.verteidiger) + "** (" + w.art.anzeige
                    + ", seit " + TimeUtil.fmtShort(w.start) + ")" + (w.grund.isBlank() ? "" : ": " + DiscordRest.kuerzen(w.grund, 120)));
        }
        return text(l.isEmpty() ? "Gerade herrscht Frieden." : "**Laufende Kriege**\n" + String.join("\n", l), false, null);
    }

    private Antwort besetzungen() {
        CrownsData d = d();
        List<String> l = new ArrayList<>();
        for (Besatzung b : d.besatzungen) l.add(de.niklasmetzger.crownscore.world.EroberungService.zeile(d, b));
        return text(l.isEmpty() ? "Gerade laeuft keine Besetzung." : "**Laufende Besetzungen**\n" + String.join("\n", l), false, null);
    }

    private Antwort staedte(String nation) {
        CrownsData d = d();
        Nation filter = null;
        if (nation != null) {
            filter = nationNach(nation);
            if (filter == null) return text("Keine Nation \"" + nation + "\".");
        }
        Map<String, List<String>> je = new LinkedHashMap<>();
        for (Stadt s : d.staedte) {
            if (!s.bestaetigt || (filter != null && !s.nation.equals(filter.key()))) continue;
            je.computeIfAbsent(d.anzeige(s.nation), k -> new ArrayList<>()).add(s.name + " (" + s.pos.getX() + " " + s.pos.getZ() + ")");
        }
        if (je.isEmpty()) return text(filter == null ? "Noch keine anerkannten Staedte." : filter.name + " hat keine anerkannten Staedte.", false, null);
        StringBuilder sb = new StringBuilder("**Staedte**\n");
        je.forEach((k, v) -> sb.append("**").append(k).append("**: ").append(String.join(", ", v)).append('\n'));
        return text(sb.toString(), false, null);
    }

    private Antwort wahlen(Ctx c) {
        CrownsData d = d();
        UUID ich = bot.verknuepft(c.nutzer());
        Nation meine = ich == null ? null : d.nationOf(ich);
        StringBuilder sb = new StringBuilder();
        JsonArray zeilen = new JsonArray();
        for (Election e : d.elections) {
            if (e.beendet) continue;
            String wo = e.parlament ? "Parlament" : d.anzeige(e.nation);
            sb.append("**").append(wo).append(": ").append(e.titel).append("** (Nr. ").append(e.id).append(", bis ").append(TimeUtil.fmt(e.ende))
                    .append(", ").append(e.stimmen.size()).append(" Stimmen)\n").append("Zur Wahl: ").append(String.join(", ", e.optionen)).append('\n');
            boolean darf = meine != null && (e.parlament ? meine.isBeamter(ich)
                    : e.nation.equals(meine.key()) && (!"PARLAMENT".equals(meine.wahlmodus) || meine.isBeamter(ich)));
            if (!darf) continue;
            for (int start = 0; start < e.optionen.size() && zeilen.size() < 5; start += 5) {
                JsonObject z = new JsonObject();
                z.addProperty("type", 1);
                JsonArray k = new JsonArray();
                for (int i = start; i < Math.min(start + 5, e.optionen.size()); i++) {
                    k.add(knopf(1, (e.parlament ? "Parlament: " : "") + e.optionen.get(i), "wahl:" + e.id + ":" + i));
                }
                z.add("components", k);
                zeilen.add(z);
            }
        }
        if (sb.isEmpty()) return text("Gerade laeuft keine Wahl.");
        if (!zeilen.isEmpty()) sb.append("\nDeine Stimme per Knopf (du kannst sie bis zum Ende aendern):");
        else if (ich == null) sb.append("\nZum Abstimmen erst /verknuepfen.");
        return text(sb.toString(), true, zeilen.isEmpty() ? null : zeilen);
    }

    private Antwort vertrag(Long nr) {
        if (nr == null) return text("Bitte die Nummer angeben.");
        for (Contract k : d().contracts) {
            if (k.id != nr) continue;
            String kopf = "**Vertrag Nr. " + k.id + ": " + k.titel + "**\nVon " + k.autor + ", hinterlegt von " + k.hinterlegtVonName
                    + (k.nation.isBlank() ? "" : " (" + d().anzeige(k.nation) + ")") + " am " + TimeUtil.fmt(k.zeit) + "\n";
            return text(kopf + code(String.join("\n---\n", k.seiten)), false, null);
        }
        return text("Keinen Vertrag Nr. " + nr + ".");
    }

    private void karte(Ctx c) {
        // Aufschieben: das Bild braucht etwas laenger als drei Sekunden erlaubt
        JsonObject defer = new JsonObject();
        defer.addProperty("type", 5);
        bot.rest.antwort(c.id(), c.token(), defer).thenRun(() -> server.execute(() -> {
            KartenBild.Stand st = KartenBild.stand(d());
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                JsonObject body = new JsonObject();
                String link = CrownsConfig.BOT_KARTE_LINK.get().trim();
                body.addProperty("content", "**Weltkarte**" + (link.isEmpty() ? "" : " (online: <" + link + ">)"));
                try {
                    bot.rest.nachtrag(bot.appId, c.token(), body, "weltkarte.png", KartenBild.png(st));
                } catch (Exception e) {
                    body.addProperty("content", "Die Karte konnte nicht gezeichnet werden.");
                    bot.rest.nachtrag(bot.appId, c.token(), body, null, null);
                }
            });
        }));
    }

    // ================================================================ Admins

    private String adminName(Ctx c) {
        UUID id = bot.verknuepft(c.nutzer());
        return id != null ? Names.of(server, id) : DiscordRest.kuerzen(c.nutzerName().replace("\u00a7", "").replace("@", ""), 32) + " (Discord)";
    }

    private Antwort wochenbericht(boolean letzte) {
        long jetzt = TimeUtil.now();
        String woche = letzte ? TimeUtil.previousWeekKey(jetzt) : TimeUtil.weekKey(jetzt);
        String bericht = ActivityService.report(server, woche, jetzt);
        if (DiscordBot.kanal(CrownsConfig.BOT_KANAL_AKTIVITAET.get(), bericht)) return text("Bericht fuer " + woche + " in den Aktivitaetskanal geschickt.");
        return text(bericht);
    }

    private Antwort antraege() {
        List<Stadt> offen = de.niklasmetzger.crownscore.handel.StadtService.offen(d());
        if (offen.isEmpty()) return text("Keine offenen Stadtantraege.");
        StringBuilder sb = new StringBuilder("**Offene Stadtantraege**\n");
        JsonArray zeilen = new JsonArray();
        for (Stadt s : offen) {
            sb.append(s.name).append(" (").append(d().anzeige(s.nation)).append(", ").append(s.pos.getX()).append(" ").append(s.pos.getY()).append(" ")
                    .append(s.pos.getZ()).append(", ").append(s.betten).append(" Betten").append(s.glocke ? ", Glocke" : "").append(")\n");
            if (zeilen.size() < 5) zeilen.add(zeile(knopf(3, "Annehmen: " + s.name, "stadt:ja:" + s.name),
                    knopf(4, "Ablehnen: " + s.name, "stadt:nein:" + s.name)).get(0));
        }
        return text(sb.toString(), true, zeilen);
    }

    private Antwort akte(String name, Long anzahl) {
        int n = anzahl == null ? 15 : (int) Math.max(1, Math.min(40, anzahl));
        List<String> l = new ArrayList<>();
        for (EvidenceEntry e : d().evidence) if (e.taeterName.equalsIgnoreCase(name)) l.add(e.zeile());
        if (l.isEmpty()) return text("Keine Eintraege zu " + name + ".");
        List<String> letzte = l.subList(Math.max(0, l.size() - n), l.size());
        return text("**Akte " + name + "** (" + l.size() + " Eintraege, die letzten " + letzte.size() + ")\n" + code(String.join("\n", letzte)));
    }

    /** Befehl als Konsole ausfuehren und die Ausgabe zurueckgeben. */
    private Antwort konsole(Ctx c, String befehl) {
        String admin = adminName(c);
        StringBuilder out = new StringBuilder();
        CommandSource quelle = new CommandSource() {
            @Override
            public void sendSystemMessage(Component component) {
                out.append(component.getString()).append('\n');
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return true;
            }
        };
        var level = server.overworld();
        CommandSourceStack src = new CommandSourceStack(quelle, Vec3.atLowerCornerOf(level.getSharedSpawnPos()), Vec2.ZERO, level, 4,
                "Discord:" + admin, Component.literal("Discord:" + admin), server, null);
        CrownsCore.LOGGER.info("Discord-Bot: {} fuehrt aus: /{}", admin, befehl);
        server.getCommands().performPrefixedCommand(src, befehl);
        String text = out.toString().trim();
        return text("`/" + befehl.replace("`", "'") + "`\n" + (text.isEmpty() ? "(keine Ausgabe)" : code(text)));
    }

    private Antwort geld(Ctx c) {
        UUID wer = spieler(c.s("spieler"));
        if (wer == null) return text("Spieler \"" + c.s("spieler") + "\" kenne ich nicht.");
        double betrag = c.n("betrag");
        if (Math.abs(betrag) < 0.005) return text("Betrag 0, nichts zu tun.");
        String name = Names.of(server, wer), admin = adminName(c);
        ServerPlayer p = server.getPlayerList().getPlayer(wer);
        CrownsData d = d();
        if (p != null) {
            boolean ok = betrag > 0 ? Economy.get().deposit(p, betrag) : Economy.get().withdraw(p, -betrag);
            if (!ok) return text(betrag > 0 ? name + " kann gerade nichts gutgeschrieben bekommen (tot?). Bitte gleich nochmal." : name + " hat nicht genug Geld.");
            DiscordBot.kontoMerken(d, p, TimeUtil.now());
            Msg.player(p, (betrag > 0 ? "Du hast " + Msg.money(betrag) + " bekommen" : "Dir wurden " + Msg.money(-betrag) + " abgezogen") + " (Admin " + admin + ").");
        } else {
            if (betrag < 0) return text(name + " ist offline. Abziehen geht nur, solange er online ist.");
            d.offeneZahlungen.merge(wer, betrag, Double::sum);
            d.setDirty();
        }
        CrownsCore.LOGGER.info("Discord-Bot: {} bucht {} fuer {}", admin, betrag, name);
        return text((betrag > 0 ? Msg.money(betrag) + " an " + name : Msg.money(-betrag) + " von " + name + " abgezogen")
                + (p == null ? " (offline, wird beim naechsten Einloggen gebucht)." : "."));
    }

    private Antwort staatskasse(Ctx c) {
        Nation n = nationNach(c.s("nation"));
        if (n == null) return text("Keine Nation \"" + c.s("nation") + "\".");
        double betrag = c.n("betrag");
        CrownsData d = d();
        double alt = de.niklasmetzger.crownscore.treasury.TreasuryService.stand(d, n.key());
        d.treasuries.computeIfAbsent(n.key(), k -> new de.niklasmetzger.crownscore.data.Treasury()).stand = betrag;
        d.setDirty();
        CrownsCore.LOGGER.info("Discord-Bot: {} setzt die Staatskasse von {} von {} auf {}", adminName(c), n.name, alt, betrag);
        return text("Staatskasse von " + n.name + ": " + Msg.money(alt) + " -> " + Msg.money(betrag));
    }

    // ================================================================ Knoepfe und Formulare

    private Antwort knopfGedrueckt(Ctx c) {
        String id = c.data().get("custom_id").getAsString();
        String[] t = id.split(":", 3);
        if (t[0].equals("wahl") && t.length == 3) return abstimmen(c, Integer.parseInt(t[1]), Integer.parseInt(t[2]));
        if (t[0].equals("stadt") && t.length == 3) {
            if (!bot.admin(c.nutzer(), c.rollen())) return text("Antraege entscheiden nur Admins.");
            Antwort schon = schonEntschieden(c, t[2]);
            if (schon != null) return schon;
            if (t[1].equals("ja")) {
                String err = de.niklasmetzger.crownscore.handel.StadtService.annehmen(server, t[2], adminName(c), true);
                if (err != null) return text(err);
                return erledigt(c, ":white_check_mark: " + t[2] + " angenommen von " + adminName(c));
            }
            // Ablehnen: Formular fuer den Grund
            JsonObject feld = new JsonObject();
            feld.addProperty("type", 4);
            feld.addProperty("custom_id", "grund");
            feld.addProperty("style", 2);
            feld.addProperty("label", "Grund (sieht die Nation)");
            feld.addProperty("required", false);
            feld.addProperty("max_length", 200);
            JsonObject z = new JsonObject();
            z.addProperty("type", 1);
            JsonArray k = new JsonArray();
            k.add(feld);
            z.add("components", k);
            JsonArray zeilen = new JsonArray();
            zeilen.add(z);
            JsonObject data = new JsonObject();
            data.addProperty("custom_id", DiscordRest.kuerzen("stadtgrund:" + t[2], 100));
            data.addProperty("title", DiscordRest.kuerzen("Stadt " + t[2] + " ablehnen", 45));
            data.add("components", zeilen);
            JsonObject o = new JsonObject();
            o.addProperty("type", 9);
            o.add("data", data);
            return new Antwort(o);
        }
        return text("Dieser Knopf ist nicht mehr gueltig.");
    }

    /** Antrag schon entschieden (im Spiel, im Browser oder per Knopf)? Dann die Knoepfe entfernen statt zu handeln. */
    private Antwort schonEntschieden(Ctx c, String stadt) {
        Stadt s = de.niklasmetzger.crownscore.handel.StadtService.byName(d(), stadt);
        if (s != null && !s.bestaetigt) return null;
        String text = s == null ? "Den Antrag " + stadt + " gibt es nicht mehr (schon abgelehnt)." : stadt + " ist schon anerkannt.";
        return erledigt(c, ":information_source: " + text);
    }

    /** Ergebnis anzeigen: bei einer Kanalnachricht die Knoepfe ersetzen, bei einer privaten Liste neu antworten. */
    private static Antwort erledigt(Ctx c, String zusatz) {
        JsonObject m = c.roh().has("message") ? c.roh().getAsJsonObject("message") : null;
        boolean privat = m == null || (m.has("flags") && (m.get("flags").getAsInt() & PRIVAT) != 0);
        if (privat) return text(zusatz);
        return aktualisieren(alterText(c) + "\n" + zusatz);
    }

    private static String alterText(Ctx c) {
        JsonObject m = c.roh().has("message") ? c.roh().getAsJsonObject("message") : null;
        return m != null && m.has("content") ? m.get("content").getAsString() : "";
    }

    private Antwort formular(Ctx c) {
        String id = c.data().get("custom_id").getAsString();
        if (!id.startsWith("stadtgrund:")) return text("Unbekanntes Formular.");
        if (!bot.admin(c.nutzer(), c.rollen())) return text("Antraege entscheiden nur Admins.");
        String stadt = id.substring("stadtgrund:".length());
        String grund = "";
        for (JsonElement z : c.data().getAsJsonArray("components")) {
            for (JsonElement f : z.getAsJsonObject().getAsJsonArray("components")) {
                JsonObject o = f.getAsJsonObject();
                if ("grund".equals(o.get("custom_id").getAsString()) && o.has("value")) grund = o.get("value").getAsString();
            }
        }
        Antwort schon = schonEntschieden(c, stadt);
        if (schon != null) return schon;
        String err = de.niklasmetzger.crownscore.handel.StadtService.ablehnen(server, stadt, adminName(c), grund, true);
        if (err != null) return text(err);
        return erledigt(c, ":x: " + stadt + " abgelehnt von " + adminName(c) + (grund.isBlank() ? "" : ": " + grund.replace("@", "@\u200b")));
    }

    private Antwort abstimmen(Ctx c, int wahlId, int option) {
        UUID ich = bot.verknuepft(c.nutzer());
        if (ich == null) return text(NICHT_VERKNUEPFT);
        CrownsData d = d();
        Election e = d.elections.stream().filter(x -> x.id == wahlId && !x.beendet).findFirst().orElse(null);
        if (e == null || option < 0 || option >= e.optionen.size()) return text("Diese Wahl ist vorbei.");
        Nation n = d.nationOf(ich);
        if (n == null) return text("Du gehoerst zu keiner Nation.");
        String wahl = e.optionen.get(option);
        if (e.parlament) {
            if (!n.isBeamter(ich)) return text("Im Parlament stimmen Staatschef und Beamte fuer ihre Nation.");
            boolean geaendert = e.stimmen.containsKey(n.key());
            e.stimmen.put(n.key(), wahl);
            d.setDirty();
            Msg.broadcast(server, n.name + " stimmt im Parlament mit " + wahl + ".");
            return text((geaendert ? "Stimme von " + n.name + " geaendert: " : "Stimme von " + n.name + " abgegeben: ") + wahl);
        }
        if (!e.nation.equals(n.key())) return text("Das ist nicht die Wahl deiner Nation.");
        if ("PARLAMENT".equals(n.wahlmodus) && !n.isBeamter(ich)) return text(n.name + " waehlt ueber das Parlament. Nur Beamte und der Staatschef stimmen ab.");
        boolean geaendert = e.stimmen.containsKey(ich.toString());
        e.stimmen.put(ich.toString(), wahl);
        d.setDirty();
        return text((geaendert ? "Stimme geaendert: " : "Stimme abgegeben: ") + wahl);
    }

    /** Vorschlaege fuer Spieler- und Nationsnamen beim Tippen. */
    private Antwort vorschlaege(Ctx c) {
        String feld = null, eingabe = "";
        if (c.data().has("options")) {
            for (JsonElement o : c.data().getAsJsonArray("options")) {
                JsonObject x = o.getAsJsonObject();
                if (x.has("focused") && x.get("focused").getAsBoolean()) {
                    feld = x.get("name").getAsString();
                    eingabe = x.get("value").getAsString().toLowerCase(Locale.ROOT);
                }
            }
        }
        Set<String> namen = new LinkedHashSet<>();
        CrownsData d = d();
        if ("nation".equals(feld) || "name".equals(feld)) {
            for (Nation n : d.nations.values()) if (n.gespielt()) namen.add(n.name);
        } else {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) namen.add(p.getGameProfile().getName());
            for (Nation n : d.nations.values()) namen.addAll(n.mitglieder.values());
        }
        JsonArray choices = new JsonArray();
        for (String s : namen) {
            if (choices.size() >= 25) break;
            if (!s.toLowerCase(Locale.ROOT).contains(eingabe)) continue;
            JsonObject ch = new JsonObject();
            ch.addProperty("name", DiscordRest.kuerzen(s, 100));
            ch.addProperty("value", DiscordRest.kuerzen(s, 100));
            choices.add(ch);
        }
        JsonObject data = new JsonObject();
        data.add("choices", choices);
        JsonObject o = new JsonObject();
        o.addProperty("type", 8);
        o.add("data", data);
        return new Antwort(o);
    }
}
