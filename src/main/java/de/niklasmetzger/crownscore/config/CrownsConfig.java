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
    public static final ForgeConfigSpec.ConfigValue<String> WEBHOOK_ANTRAEGE;
    public static final ForgeConfigSpec.ConfigValue<String> ANTRAG_URL;
    public static final ForgeConfigSpec.ConfigValue<String> ANTRAG_SCHLUESSEL;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ADMINS;
    public static final ForgeConfigSpec.ConfigValue<String> ZEITZONE;
    public static final ForgeConfigSpec.BooleanValue CHAT_NATION;
    public static final ForgeConfigSpec.ConfigValue<String> CHAT_NATION_ANZEIGE;
    public static final ForgeConfigSpec.ConfigValue<String> CHAT_NATION_FARBE;

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
    public static final ForgeConfigSpec.IntValue WAHL_AMT_MINDEST_MINUTEN;
    public static final ForgeConfigSpec.DoubleValue DARLEHEN_ZINS_PROZENT;
    public static final ForgeConfigSpec.IntValue DARLEHEN_WOCHEN;

    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> KOLONIE_KOSTEN_HERRSCHER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> KOLONIE_KOSTEN_FREI;
    public static final ForgeConfigSpec.IntValue KOLONIE_RADIUS;

    public static final ForgeConfigSpec.DoubleValue WEGSTEIN_ZOLL;
    public static final ForgeConfigSpec.BooleanValue WEGSTEIN_GLOBAL_FREI;

    public static final ForgeConfigSpec.IntValue POST_ABFANG_PROZENT;
    public static final ForgeConfigSpec.IntValue GEBIET_MAX_CHUNKS;

    public static final ForgeConfigSpec.BooleanValue KARTE_AKTIV;
    public static final ForgeConfigSpec.IntValue KARTE_MITTE_X;
    public static final ForgeConfigSpec.IntValue KARTE_MITTE_Z;
    public static final ForgeConfigSpec.DoubleValue KARTE_BLOECKE_PRO_GRAD;
    public static final ForgeConfigSpec.IntValue KARTE_CHUNKS_PRO_TICK;

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
    public static final ForgeConfigSpec.ConfigValue<String> RECHTE_EDITOR_URL;
    public static final ForgeConfigSpec.ConfigValue<String> RECHTE_BYTEBIN_URL;
    public static final ForgeConfigSpec.BooleanValue UMHANG_AKTIV;
    public static final ForgeConfigSpec.BooleanValue UMHANG_LANDESFLAGGEN;
    public static final ForgeConfigSpec.BooleanValue UMHANG_FREIGABE;
    public static final ForgeConfigSpec.ConfigValue<String> UMHANG_DREHUNG;
    public static final ForgeConfigSpec.ConfigValue<String> UMHANG_SEITE;

    public static final ForgeConfigSpec.BooleanValue KARTENFLAGGEN_AKTIV;
    public static final ForgeConfigSpec.IntValue KARTENFLAGGEN_DECKKRAFT;
    public static final ForgeConfigSpec.IntValue KARTENFLAGGEN_BREITE;

    public static final ForgeConfigSpec.BooleanValue EROBERUNG_KAUFEN;
    public static final ForgeConfigSpec.DoubleValue EROBERUNG_KAUF_GRUNDPREIS;
    public static final ForgeConfigSpec.DoubleValue EROBERUNG_KAUF_PRO_CHUNK;
    public static final ForgeConfigSpec.IntValue EROBERUNG_NACHBAR_ABSTAND;
    public static final ForgeConfigSpec.BooleanValue EROBERUNG_BESETZEN;
    public static final ForgeConfigSpec.BooleanValue EROBERUNG_NUR_IM_KRIEG;
    public static final ForgeConfigSpec.BooleanValue EROBERUNG_NUR_KAMPFKRIEG;
    public static final ForgeConfigSpec.DoubleValue EROBERUNG_GEBUEHR_GRUND;
    public static final ForgeConfigSpec.DoubleValue EROBERUNG_GEBUEHR_PRO_CHUNK;
    public static final ForgeConfigSpec.IntValue EROBERUNG_HALTEZEIT_MINUTEN;
    public static final ForgeConfigSpec.BooleanValue EROBERUNG_VERTEIDIGER_ONLINE;
    public static final ForgeConfigSpec.IntValue EROBERUNG_SCHUTZ_TAGE;
    public static final ForgeConfigSpec.IntValue EROBERUNG_SPERRE_TAGE;
    public static final ForgeConfigSpec.BooleanValue EROBERUNG_STAMMLAND_SCHUTZ;
    public static final ForgeConfigSpec.IntValue EROBERUNG_GLEICHZEITIG;
    public static final ForgeConfigSpec.ConfigValue<String> EROBERUNG_BLOCK;
    public static final ForgeConfigSpec.IntValue EROBERUNG_AUFGABE_RADIUS;
    public static final ForgeConfigSpec.IntValue EROBERUNG_ANGREIFER_KULANZ;
    public static final ForgeConfigSpec.IntValue EROBERUNG_VERTEIDIGER_KULANZ;

    public static final ForgeConfigSpec.IntValue STADT_RADIUS;
    public static final ForgeConfigSpec.IntValue STADT_GEBIET;
    public static final ForgeConfigSpec.IntValue STADT_BETTEN;
    public static final ForgeConfigSpec.BooleanValue STADT_GLOCKE;
    public static final ForgeConfigSpec.IntValue STADT_ABSTAND;
    public static final ForgeConfigSpec.BooleanValue STADT_EIGENES_GEBIET;

    public static final ForgeConfigSpec.BooleanValue VKAUF_AKTIV;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> VKAUF_ANGEBOTE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> VKAUF_GRENZEN;
    public static final ForgeConfigSpec.IntValue VKAUF_UNBEGRENZT_AB;
    public static final ForgeConfigSpec.DoubleValue VKAUF_AUFSCHLAG;
    public static final ForgeConfigSpec.DoubleValue VKAUF_ERHOLUNG;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Discord-Webhooks. Leer lassen, wenn kein Kanal angebunden werden soll.").push("discord");
        WEBHOOK_KRIEGSNEWS = b.comment("Kriegserklaerungen, Friedensschluesse, Blitzkrieg-Ablauf").define("kriegsnews", "");
        WEBHOOK_GERICHT = b.comment("Export von Akten und Vertraegen").define("gericht", "");
        WEBHOOK_AKTIVITAET = b.comment("Woechentlicher Aktivitaetsbericht").define("aktivitaet", "");
        WEBHOOK_NEWS = b.comment("Allgemeine Meldungen (Wahlen, Kolonien, Darlehen). Leer = Kriegsnews-Webhook").define("news", "");
        WEBHOOK_ANTRAEGE = b.comment("Antraege an die Admins (Stadtgruendungen). Leer = News-Webhook").define("antraege", "");
        b.pop();

        b.comment("Allgemein").push("allgemein");
        ADMINS = b.comment("Spielernamen mit Admin-Rechten in dieser Mod, zusaetzlich zu OPs")
                .defineListAllowEmpty(List.of("admins"), List::of, o -> o instanceof String);
        ZEITZONE = b.comment("Zeitzone fuer Berichte und Zeitangaben").define("zeitzone", "Europe/Berlin");
        b.pop();

        b.comment("Chat").push("chat");
        CHAT_NATION = b.comment("Nation vor dem Spielernamen im Chat (gilt auch fuer Namensschild und Todesmeldungen)").define("nationVorName", true);
        CHAT_NATION_ANZEIGE = b.comment("NAME = ganzer Landesname, KUERZEL = Kuerzel wie SAU").define("nationAnzeige", "NAME");
        CHAT_NATION_FARBE = b.comment("Farbe der Nation mit &, z.B. &6 fuer Gold").define("nationFarbe", "&6");
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

        b.comment("Wahlen").push("wahlen");
        WAHL_AMT_MINDEST_MINUTEN = b.comment("Mindestdauer einer Wahl des Staatschefs in Minuten (gegen Blitzwahlen in der Nacht)")
                .defineInRange("amtMindestMinuten", 1440, 1, 20160);
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

        b.comment("Weltkarte: Laendergrenzen der Erdkarte (Plattkarte, 0/0 = Aequator und Nullmeridian, Norden = -z)").push("weltkarte");
        KARTE_AKTIV = b.comment("Gebiete der Nationen aus der Weltkarte ableiten, wo kein Claim gilt").define("aktiv", true);
        KARTE_MITTE_X = b.comment("Blockkoordinate x von Laenge 0 (Nullmeridian)").defineInRange("mitteX", 0, -30000000, 30000000);
        KARTE_MITTE_Z = b.comment("Blockkoordinate z von Breite 0 (Aequator)").defineInRange("mitteZ", 0, -30000000, 30000000);
        KARTE_BLOECKE_PRO_GRAD = b.comment("Bloecke pro Laengengrad. 49152 Bloecke Kartenbreite / 360 = 136.5333")
                .defineInRange("bloeckeProGrad", 49152.0 / 360.0, 1.0, 100000.0);
        KARTE_CHUNKS_PRO_TICK = b.comment("So viele Chunks prueft die Grenzanzeige fuer die Minimap pro Tick").defineInRange("chunksProTick", 6144, 256, 200000);
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
        RECHTE_EDITOR_URL = b.comment("Adresse des eigenen Rechte-Editors (Ordner editor/ im Repo), wie ihn die Admins im Browser oeffnen,",
                        "z.B. https://rechte.example.de/editor/ . Leer = /cccore perms editor ist aus.")
                .define("editorUrl", "");
        RECHTE_BYTEBIN_URL = b.comment("Datenaustausch des Editors, vom Minecraft-Server aus erreichbar, z.B. http://192.168.1.10:8095/data/ .",
                        "Leer = editorUrl mit /data/ statt /editor/.")
                .define("bytebinUrl", "");
        b.pop();

        b.comment("Umhaenge mit der Flagge der Nation (/nation flagge). Sichtbar fuer alle, die Crowns Core im Client haben.").push("umhang");
        UMHANG_AKTIV = b.comment("Buerger tragen die Flagge ihrer Nation als Umhang").define("aktiv", true);
        UMHANG_LANDESFLAGGEN = b.comment("Nationen ohne eigene Flagge tragen die echte Flagge ihres Landes auf der Weltkarte").define("landesflaggen", true);
        UMHANG_FREIGABE = b.comment("Hochgeladene Flaggen muss ein Admin erst freigeben (/cccore flaggen)").define("freigabe", true);
        UMHANG_DREHUNG = b.comment("RECHTS = Flagge im Uhrzeigersinn gedreht, Mastseite oben (wie ein Banner am Mast). LINKS = andersherum.")
                .define("drehung", "RECHTS");
        UMHANG_SEITE = b.comment("Adresse der Upload-Seite fuer Flaggen (Ordner editor/flagge/). Leer = rechte.editorUrl mit flagge/ statt editor/.")
                .define("seiteUrl", "");
        b.pop();

        b.comment("Flaggen auf Xaero's World Map und Minimap: jedes Land zeigt die Flagge der Nation, die es haelt.",
                "Braucht Crowns Core im Client und Open Parties and Claims mit /nation weltkarte minimap flaeche.").push("kartenflaggen");
        KARTENFLAGGEN_AKTIV = b.comment("Flaggen statt einfarbiger Flaechen auf den Karten zeigen").define("aktiv", true);
        KARTENFLAGGEN_DECKKRAFT = b.comment("Deckkraft der Flaggen in Prozent (0 = unsichtbar, 100 = Gelaende nicht mehr sichtbar)")
                .defineInRange("deckkraft", 55, 0, 100);
        KARTENFLAGGEN_BREITE = b.comment("Breite der Flaggenbilder fuer die Karte in Pixeln. Mehr = schaerfer, aber mehr Daten beim Einloggen")
                .defineInRange("breite", 256, 32, 1024);
        b.pop();

        b.comment("Laender einnehmen: freie Laender kaufen und Laender anderer Nationen im Krieg besetzen.",
                "Alle Betraege gehen aus der Staatskasse. Chunks = Groesse des Landes auf der Weltkarte (/nation land preis <Land>).",
                "Die Werte hier sind Vorschlaege und noch nicht mit der Projektleitung abgestimmt.").push("eroberung");
        EROBERUNG_KAUFEN = b.comment("Freie Laender (Nationen ohne Spieler) duerfen gekauft werden").define("kaufen", true);
        EROBERUNG_KAUF_GRUNDPREIS = b.comment("Grundpreis eines freien Landes").defineInRange("kaufGrundpreis", 1000.0, 0.0, 1.0E12);
        EROBERUNG_KAUF_PRO_CHUNK = b.comment("Aufschlag pro Chunk des Landes (Saudi-Arabien hat etwa 12400, Jemen 2900, Deutschland 3500, Belgien 290)")
                .defineInRange("kaufProChunk", 1.0, 0.0, 1.0E9);
        EROBERUNG_NACHBAR_ABSTAND = b.comment("Kaufen und Besetzen nur in der Naehe des eigenen Gebiets: hoechstens so viele Chunks Abstand.",
                        "0 = muss direkt angrenzen, 16 = auch ueber schmale Meerengen (Aermelkanal, Rotes Meer), -1 = egal wo")
                .defineInRange("nachbarAbstandChunks", 16, -1, 100000);
        EROBERUNG_BESETZEN = b.comment("Laender anderer Nationen duerfen besetzt werden (Besatzungsblock halten)").define("besetzen", true);
        EROBERUNG_NUR_IM_KRIEG = b.comment("Besetzen nur gegen Nationen, mit denen man im Krieg ist").define("nurImKrieg", true);
        EROBERUNG_NUR_KAMPFKRIEG = b.comment("Der Krieg muss eine Kriegsart mit erlaubtem Kampf sein (krieg.kampfErlaubt), nicht nur Spannungen")
                .define("nurKampfkrieg", true);
        EROBERUNG_GEBUEHR_GRUND = b.comment("Grundgebuehr fuer eine Besetzung, ist bei Misserfolg weg").defineInRange("besatzungGrundgebuehr", 500.0, 0.0, 1.0E12);
        EROBERUNG_GEBUEHR_PRO_CHUNK = b.comment("Aufschlag pro Chunk des besetzten Landes").defineInRange("besatzungProChunk", 0.5, 0.0, 1.0E9);
        EROBERUNG_HALTEZEIT_MINUTEN = b.comment("So lange muss der Besatzungsblock stehen bleiben, dann wechselt das Land den Besitzer")
                .defineInRange("haltezeitMinuten", 120, 1, 100000);
        EROBERUNG_VERTEIDIGER_ONLINE = b.comment("Die Haltezeit laeuft nur, solange mindestens ein Spieler der angegriffenen Nation online ist")
                .define("nurWennVerteidigerOnline", true);
        EROBERUNG_SCHUTZ_TAGE = b.comment("Neue Nationen sind so viele Tage nach der Gruendung geschuetzt (0 = kein Schutz)")
                .defineInRange("schutzNeueNationTage", 7, 0, 3650);
        EROBERUNG_SPERRE_TAGE = b.comment("Ein gerade eingenommenes Land kann so viele Tage nicht erneut eingenommen werden")
                .defineInRange("sperreNachEroberungTage", 3, 0, 3650);
        EROBERUNG_STAMMLAND_SCHUTZ = b.comment("Das Stammland einer Nation ist geschuetzt, solange sie noch andere Laender hat")
                .define("stammlandGeschuetzt", true);
        EROBERUNG_GLEICHZEITIG = b.comment("So viele Besetzungen darf eine Nation gleichzeitig laufen haben")
                .defineInRange("gleichzeitigeBesetzungen", 1, 1, 100);
        EROBERUNG_BLOCK = b.comment("Block, der als Besatzungsblock gesetzt wird").define("besatzungsblock", "minecraft:red_banner");
        EROBERUNG_AUFGABE_RADIUS = b.comment("Wer sich weiter als so viele Bloecke von der Stadtmitte (bzw. dem Besatzungsblock) entfernt, ist nicht mehr dabei.",
                        "Sind keine Angreifer mehr in dem Umkreis, haben sie aufgegeben. Ist kein Verteidiger dort, obwohl welche online sind, haben die aufgegeben. 0 = aus")
                .defineInRange("aufgabeRadius", 100, 0, 10000);
        EROBERUNG_ANGREIFER_KULANZ = b.comment("So viele Sekunden duerfen alle Angreifer weg sein, bevor die Besetzung als aufgegeben gilt")
                .defineInRange("angreiferKulanzSekunden", 30, 0, 86400);
        EROBERUNG_VERTEIDIGER_KULANZ = b.comment("So viele Minuten duerfen die Verteidiger fernbleiben (obwohl jemand online ist), dann ist die Stadt bzw. das Land verloren")
                .defineInRange("verteidigerKulanzMinuten", 10, 1, 10000);
        b.pop();

        b.comment("Antraege im Browser entscheiden (Ordner editor/antraege/ beim Rechte-Editor). Der Link geht in den Discord-Webhook antraege.",
                "Der Server holt die Entscheidungen alle 30 Sekunden selbst ab, er braucht dafuer keinen offenen Port.").push("antraege");
        ANTRAG_URL = b.comment("Adresse der Seite, z.B. https://editor.example.de/antraege/. Leer = rechte.editorUrl mit antraege/ statt editor/")
                .define("webUrl", "");
        ANTRAG_SCHLUESSEL = b.comment("Gemeinsamer Schluessel mit dem Antrags-Dienst (ANTRAEGE_SCHLUESSEL in editor/.env). Leer = nur im Spiel entscheiden")
                .define("schluessel", "");
        b.pop();

        b.comment("Staedte (/stadt gruenden). Eine Stadt muss beim Antrag diese Anforderungen im Umkreis erfuellen,",
                "danach bestaetigt ein Admin sie mit /cccore staedte. Antraege gehen auch an den Discord-Webhook antraege.",
                "Wie viele Staedte eine Nation hat, bestimmt, wie viele Villager sie beim Haendler kaufen darf.",
                "Die Werte hier sind Vorschlaege und noch nicht mit der Projektleitung abgestimmt.").push("stadt");
        STADT_RADIUS = b.comment("Umkreis in Bloecken, in dem Betten und Glocke gezaehlt werden").defineInRange("radius", 48, 8, 256);
        STADT_GEBIET = b.comment("Stadtgebiet in Chunks um die Mitte (4 = 9 x 9 Chunks). Das wird bei einer Besetzung erobert und auf der Karte umrandet")
                .defineInRange("gebietChunks", 4, 0, 32);
        STADT_BETTEN = b.comment("So viele Betten braucht eine Stadt mindestens").defineInRange("mindestBetten", 10, 0, 10000);
        STADT_GLOCKE = b.comment("Eine Glocke (Dorfmittelpunkt) ist Pflicht").define("glockeNoetig", true);
        STADT_ABSTAND = b.comment("Mindestabstand in Bloecken zur naechsten Stadt (egal welcher Nation)").defineInRange("mindestAbstand", 300, 0, 100000);
        STADT_EIGENES_GEBIET = b.comment("Nur im eigenen Gebiet der Nation (Weltkarte oder Claims)").define("nurImEigenenGebiet", true);
        b.pop();

        b.comment("Villager-Haendler: verkauft Villager und Rekruten gegen Geld oder Rohstoffe, nie ausverkauft.",
                "Ein Admin stellt ihn mit /cccore haendler setzen auf. Kaufen duerfen Buerger fuer ihre Nation.",
                "Die Werte hier sind Vorschlaege und noch nicht mit der Projektleitung abgestimmt.").push("villagerkauf");
        VKAUF_AKTIV = b.comment("Villager kaufen erlauben").define("aktiv", true);
        VKAUF_ANGEBOTE = b.comment("Angebote: Entity-ID;Anzeigename;Geldpreis;Rohstoffpreis (Item*Anzahl, mehrere mit +, leer = nur Geld)",
                        "Villager Recruits: recruits:recruit, recruits:bowman, recruits:crossbowman, recruits:recruit_shieldman, recruits:horseman, recruits:scout")
                .defineListAllowEmpty(List.of("angebote"), () -> List.of(
                        "minecraft:villager;Dorfbewohner;250;minecraft:emerald*8+minecraft:bread*16",
                        "recruits:recruit;Rekrut;400;minecraft:iron_ingot*12",
                        "recruits:bowman;Bogenschuetze;450;minecraft:iron_ingot*8+minecraft:arrow*32",
                        "recruits:crossbowman;Armbrustschuetze;500;minecraft:iron_ingot*12+minecraft:arrow*32",
                        "recruits:recruit_shieldman;Schildtraeger;550;minecraft:iron_ingot*16"), o -> o instanceof String);
        VKAUF_GRENZEN = b.comment("Wie viele Villager eine Nation insgesamt kaufen darf, nach Anzahl ihrer Staedte:",
                        "erster Wert = 0 Staedte, zweiter = 1 Stadt, usw. Mehr Staedte als Werte = letzter Wert")
                .defineList("grenzen", List.of(0, 5, 10, 16, 22, 30, 40, 50), o -> o instanceof Integer);
        VKAUF_UNBEGRENZT_AB = b.comment("Ab so vielen Staedten gibt es keine Grenze mehr (0 = nie)").defineInRange("unbegrenztAb", 8, 0, 1000);
        VKAUF_AUFSCHLAG = b.comment("Jeder Kauf macht den naechsten fuer die Nation um so viel Prozent teurer (Nachfrage)")
                .defineInRange("aufschlagProzent", 10.0, 0.0, 1000.0);
        VKAUF_ERHOLUNG = b.comment("Die Nachfrage sinkt pro Tag um so viel Prozent, der Preis faellt also langsam zurueck")
                .defineInRange("erholungProzentProTag", 25.0, 0.0, 100.0);
        b.pop();

        SPEC = b.build();
    }

    private CrownsConfig() {}
}
