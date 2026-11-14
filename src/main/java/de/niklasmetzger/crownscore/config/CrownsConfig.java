package de.niklasmetzger.crownscore.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Server-Config, liegt pro Welt unter serverconfig/crownscore-server.toml. */
public final class CrownsConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<String> WEBHOOK_KRIEGSNEWS;
    public static final ForgeConfigSpec.ConfigValue<String> WEBHOOK_GERICHT;
    public static final ForgeConfigSpec.ConfigValue<String> WEBHOOK_AKTIVITAET;
    public static final ForgeConfigSpec.ConfigValue<String> WEBHOOK_NEWS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ADMINS;
    public static final ForgeConfigSpec.ConfigValue<String> ZEITZONE;

    public static final ForgeConfigSpec.BooleanValue SPAWN_WELTSPAWN;
    public static final ForgeConfigSpec.IntValue SPAWN_X;
    public static final ForgeConfigSpec.IntValue SPAWN_Z;
    public static final ForgeConfigSpec.IntValue SPAWN_RADIUS;
    public static final ForgeConfigSpec.IntValue BLITZKRIEG_MINUTEN;
    public static final ForgeConfigSpec.IntValue DUELL_MINUTEN;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> KAMPF_ERLAUBT;
    public static final ForgeConfigSpec.BooleanValue STAATENLOSE_PVP;

    public static final ForgeConfigSpec.IntValue CAMPING_MINUTEN;
    public static final ForgeConfigSpec.IntValue CAMPING_MELDUNG_MINUTEN;
    public static final ForgeConfigSpec.IntValue AKTEN_MAX;

    public static final ForgeConfigSpec.IntValue VISUM_SEKUNDEN;
    public static final ForgeConfigSpec.IntValue VISUM_MELDUNG_MINUTEN;
    public static final ForgeConfigSpec.IntValue VISUM_STANDARD_TAGE;

    public static final ForgeConfigSpec.DoubleValue AKTIV_WOCHE_STUNDEN;
    public static final ForgeConfigSpec.DoubleValue AKTIV_WOCHENENDE_STUNDEN;
    public static final ForgeConfigSpec.ConfigValue<String> BERICHT_WOCHENTAG;
    public static final ForgeConfigSpec.IntValue BERICHT_STUNDE;

    public static final ForgeConfigSpec.DoubleValue DARLEHEN_MAX;
    public static final ForgeConfigSpec.DoubleValue DARLEHEN_ZINS_PROZENT;
    public static final ForgeConfigSpec.IntValue DARLEHEN_WOCHEN;

    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> KOLONIE_KOSTEN_HERRSCHER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> KOLONIE_KOSTEN_FREI;
    public static final ForgeConfigSpec.IntValue KOLONIE_RADIUS;

    public static final ForgeConfigSpec.DoubleValue WEGSTEIN_ZOLL;
    public static final ForgeConfigSpec.BooleanValue WEGSTEIN_GLOBAL_FREI;

    public static final ForgeConfigSpec.IntValue POST_ABFANG_PROZENT;
    public static final ForgeConfigSpec.IntValue GEBIET_MAX_CHUNKS;

    public static final ForgeConfigSpec.IntValue MODUS_GRUND_MINUTEN;
    public static final ForgeConfigSpec.IntValue MODUS_KAMPFSPERRE_SEKUNDEN;
    public static final ForgeConfigSpec.IntValue MODUS_AFK_MAX_STUNDEN;
    public static final ForgeConfigSpec.BooleanValue MODUS_AFK_ZAEHLT;
    public static final ForgeConfigSpec.BooleanValue MODUS_TABLISTE;

    public static final ForgeConfigSpec.BooleanValue LOG_AKTIV;
    public static final ForgeConfigSpec.BooleanValue LOG_CHAT;
    public static final ForgeConfigSpec.BooleanValue LOG_BEFEHLE;
    public static final ForgeConfigSpec.BooleanValue LOG_ITEMS;
    public static final ForgeConfigSpec.BooleanValue LOG_KLICKS;
    public static final ForgeConfigSpec.IntValue LOG_PRO_TICK;
    public static final ForgeConfigSpec.IntValue LOG_MAX_ERGEBNISSE;

    public static final ForgeConfigSpec.BooleanValue RECHTE_FORGE_HANDLER;
    public static final ForgeConfigSpec.BooleanValue RECHTE_BEFEHLE;
    public static final ForgeConfigSpec.BooleanValue RECHTE_PRAEFIX;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Discord-Webhooks. Leer lassen, wenn kein Kanal angebunden werden soll.").push("discord");
        WEBHOOK_KRIEGSNEWS = b.comment("Kriegserklaerungen, Friedensschluesse, Blitzkrieg-Ablauf").define("kriegsnews", "");
        WEBHOOK_GERICHT = b.comment("Export von Akten und Vertraegen").define("gericht", "");
        WEBHOOK_AKTIVITAET = b.comment("Woechentlicher Aktivitaetsbericht").define("aktivitaet", "");
        WEBHOOK_NEWS = b.comment("Allgemeine Meldungen (Wahlen, Kolonien, Darlehen). Leer = Kriegsnews-Webhook").define("news", "");
        b.pop();

        b.comment("Allgemein").push("allgemein");
        ADMINS = b.comment("Spielernamen mit Admin-Rechten in dieser Mod, zusaetzlich zu OPs")
                .defineListAllowEmpty(List.of("admins"), List::of, o -> o instanceof String);
        ZEITZONE = b.comment("Zeitzone fuer Berichte und Zeitangaben").define("zeitzone", "Europe/Berlin");
        b.pop();

        b.comment("Kriege und PvP").push("krieg");
        SPAWN_WELTSPAWN = b.comment("true = Weltspawn als Mittelpunkt der PvP-freien Zone, false = spawnX/spawnZ").define("spawnWeltspawnNutzen", true);
        SPAWN_X = b.defineInRange("spawnX", 0, -30000000, 30000000);
        SPAWN_Z = b.defineInRange("spawnZ", 0, -30000000, 30000000);
        SPAWN_RADIUS = b.comment("Radius der PvP-freien Zone in Bloecken").defineInRange("spawnRadius", 150, 0, 100000);
        BLITZKRIEG_MINUTEN = b.comment("Minuten nach der Kriegserklaerung, in denen ein Blitzkrieg den ersten Kampf haben muss").defineInRange("blitzkriegMinuten", 30, 1, 1440);
        DUELL_MINUTEN = b.comment("Dauer eines Duells in Minuten").defineInRange("duellMinuten", 10, 1, 120);
        KAMPF_ERLAUBT = b.comment("Kriegsarten, in denen PvP-Schaden erlaubt ist")
                .defineListAllowEmpty(List.of("kampfErlaubt"),
                        () -> List.of("KRIEG", "TOTALER_KRIEG", "BLITZKRIEG", "GUERILLAKRIEG", "WELTKRIEG"), o -> o instanceof String);
        STAATENLOSE_PVP = b.comment("Duerfen Spieler ohne Nation PvP-Schaden austeilen und nehmen?").define("staatenlosePvp", false);
        b.pop();

        b.comment("Beweisakte").push("akte");
        CAMPING_MINUTEN = b.comment("Ab so vielen Minuten vor oder in einer fremden Base gilt es als Camping").defineInRange("campingMinuten", 30, 1, 1440);
        CAMPING_MELDUNG_MINUTEN = b.comment("Mindestabstand zwischen zwei Camping-Meldungen fuer denselben Spieler").defineInRange("campingMeldungMinuten", 30, 1, 1440);
        AKTEN_MAX = b.comment("Maximale Anzahl gespeicherter Akteneintraege, aelteste fliegen raus").defineInRange("aktenMax", 20000, 100, 1000000);
        b.pop();

        b.comment("Pass und Visum").push("visum");
        VISUM_SEKUNDEN = b.comment("Sekunden am Boden in fremdem Gebiet, bis eine Einreise gemeldet wird (Ueberfluege zaehlen nicht)").defineInRange("visumSekunden", 30, 1, 3600);
        VISUM_MELDUNG_MINUTEN = b.comment("Mindestabstand zwischen zwei Einreise-Meldungen fuer denselben Spieler").defineInRange("visumMeldungMinuten", 10, 1, 1440);
        VISUM_STANDARD_TAGE = b.comment("Gueltigkeit eines Visums ohne Angabe in Tagen").defineInRange("visumStandardTage", 7, 1, 3650);
        b.pop();

        b.comment("Aktivitaet (Regel 7.2)").push("aktivitaet");
        AKTIV_WOCHE_STUNDEN = b.comment("Mindeststunden pro Woche").defineInRange("wocheStunden", 1.0, 0.0, 168.0);
        AKTIV_WOCHENENDE_STUNDEN = b.comment("Mindeststunden am Wochenende (Samstag und Sonntag)").defineInRange("wochenendeStunden", 4.0, 0.0, 48.0);
        BERICHT_WOCHENTAG = b.comment("Wochentag des Berichts (MONDAY..SUNDAY)").define("berichtWochentag", "SUNDAY");
        BERICHT_STUNDE = b.comment("Stunde des Berichts (0-23)").defineInRange("berichtStunde", 21, 0, 23);
        b.pop();

        b.comment("Darlehen der Zentralbank").push("darlehen");
        DARLEHEN_MAX = b.comment("Hoechste Gesamtschuld einer Nation").defineInRange("maximum", 50000.0, 0.0, 1.0E9);
        DARLEHEN_ZINS_PROZENT = b.comment("Zins auf die Gesamtsumme in Prozent").defineInRange("zinsProzent", 5.0, 0.0, 100.0);
        DARLEHEN_WOCHEN = b.comment("Laufzeit in Wochen, eine Rate pro Woche").defineInRange("wochen", 8, 1, 104);
        b.pop();

        b.comment("Kolonialbehoerde").push("kolonie");
        KOLONIE_KOSTEN_HERRSCHER = b.comment("Kosten je Stufe bei lokalem Herrscher: Expedition, Diplomatische Mission, Vertrag, Praesenz, Kolonialstatus")
                .defineList("kostenHerrscher", List.of(1000, 2000, 0, 20000, 0), o -> o instanceof Integer);
        KOLONIE_KOSTEN_FREI = b.comment("Kosten je Stufe ohne Herrscher: Expedition, Niederlassung, Erschliessung, Anspruch")
                .defineList("kostenFrei", List.of(1000, 5000, 30000, 0), o -> o instanceof Integer);
        KOLONIE_RADIUS = b.comment("Radius eines Kolonialgebiets in Bloecken").defineInRange("radius", 256, 16, 10000);
        b.pop();

        b.comment("Wegsteine").push("wegstein");
        WEGSTEIN_ZOLL = b.comment("Gebuehr fuer Spieler fremder, freigegebener Nationen (0 = kostenlos), geht in die Staatskasse des Besitzers").defineInRange("zoll", 0.0, 0.0, 1.0E9);
        WEGSTEIN_GLOBAL_FREI = b.comment("Globale Wegsteine (z.B. Spawn) sind immer fuer alle frei").define("globalFrei", true);
        b.pop();

        b.comment("Post und Spionage").push("post");
        POST_ABFANG_PROZENT = b.comment("Chance in Prozent, dass eine Nation in Spannungen oder Kaltem Krieg einen Brief abfaengt").defineInRange("abfangProzent", 25, 0, 100);
        b.pop();

        b.comment("Interne Gebiete (nur wenn Open Parties and Claims fehlt oder zusaetzlich)").push("gebiet");
        GEBIET_MAX_CHUNKS = b.comment("Maximale Chunks pro Nation").defineInRange("maxChunks", 2000, 1, 1000000);
        b.pop();

        b.comment("Spielermodus: RP, Farm, AFK").push("modus");
        MODUS_GRUND_MINUTEN = b.comment("So lange gilt ein angegebener Grund fuer einen Angriff auf jemanden im Farm- oder AFK-Modus").defineInRange("grundMinuten", 30, 1, 1440);
        MODUS_KAMPFSPERRE_SEKUNDEN = b.comment("Nach einem Kampf darf man so lange nicht aus dem RP-Modus wechseln").defineInRange("kampfSperreSekunden", 60, 0, 3600);
        MODUS_AFK_MAX_STUNDEN = b.comment("Laengste erlaubte AFK-Dauer in Stunden").defineInRange("afkMaxStunden", 12, 1, 168);
        MODUS_AFK_ZAEHLT = b.comment("Zaehlt AFK-Zeit fuer den Aktivitaetsbericht?").define("afkZaehltAlsAktivitaet", true);
        MODUS_TABLISTE = b.comment("Modus in der Tabliste hinter dem Namen anzeigen").define("tabliste", true);
        b.pop();

        b.comment("Blockprotokoll (/cccore admin): wer hat wann was abgebaut, gesetzt, genommen").push("protokoll");
        LOG_AKTIV = b.comment("Protokoll einschalten").define("aktiv", true);
        LOG_CHAT = b.comment("Chatnachrichten protokollieren").define("chat", true);
        LOG_BEFEHLE = b.comment("Befehle von Spielern protokollieren").define("befehle", true);
        LOG_ITEMS = b.comment("Fallengelassene und aufgehobene Items protokollieren").define("items", true);
        LOG_KLICKS = b.comment("Tueren, Hebel, Knoepfe und Falltueren protokollieren").define("klicks", true);
        LOG_PRO_TICK = b.comment("Bloecke, die ein Rollback pro Tick setzt (hoeher = schneller, aber mehr Last)").defineInRange("rollbackProTick", 1000, 50, 20000);
        LOG_MAX_ERGEBNISSE = b.comment("Hoechstens so viele Treffer zeigt eine Abfrage").defineInRange("maxErgebnisse", 5000, 100, 100000);
        b.pop();

        b.comment("Rechte (/cccore perms)").push("rechte");
        RECHTE_FORGE_HANDLER = b.comment("Rechteverwaltung dieser Mod beim Start als Forge-Permission-Handler eintragen, damit andere Mods sie nutzen")
                .define("forgeHandlerSetzen", true);
        RECHTE_BEFEHLE = b.comment("Alle Befehle (auch Vanilla und andere Mods) ueber Rechte freigeben oder sperren: command.<name>").define("befehlsRechte", true);
        RECHTE_PRAEFIX = b.comment("Praefix und Suffix der Gruppen im Chat und in der Tabliste anzeigen").define("praefixAnzeigen", true);
        b.pop();

        SPEC = b.build();
    }

    private CrownsConfig() {}
}
