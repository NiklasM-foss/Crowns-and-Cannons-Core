package de.niklasmetzger.crownscore.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.ActivityRecord;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.economy.Economy;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.Names;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discord-Bot im Mod: Slash-Befehle und Knoepfe (siehe {@link BotBefehle}), Kanalnachrichten statt Webhooks,
 * Besetzungsalarm per DM, Rollen und Namen je Nation, Erinnerung an die Spielzeit und Chat-Bruecke.
 */
public final class DiscordBot {
    private static volatile DiscordBot bot;
    /** Verknuepfungscodes: Code zu {Discord-ID, Ablauf in ms}. */
    static final Map<String, Object[]> CODES = new ConcurrentHashMap<>();
    private static final SecureRandom ZUFALL = new SecureRandom();

    final MinecraftServer server;
    final DiscordRest rest;
    final String guild;
    final BotBefehle befehle;
    private final DiscordGateway gateway;
    volatile String appId;
    /** Angemeldet und nicht endgueltig abgewiesen: erst dann ersetzt der Bot die Webhooks. */
    volatile boolean bereit;
    /** Getrennte Discord-Konten: Rollen und Namen beim naechsten Abgleich zuruecknehmen. */
    static final Set<String> GETRENNT = ConcurrentHashMap.newKeySet();

    // Nur im REST-Thread benutzt
    private final Map<String, Set<String>> rollenIst = new HashMap<>();
    private final Map<String, String> nickIst = new HashMap<>();
    private final Set<String> nickVerboten = new HashSet<>();
    private final Set<String> nichtImServer = new HashSet<>();
    /** Nationsrollen, wie der REST-Thread sie kennt (massgeblich, damit nichts doppelt angelegt wird). */
    private Map<String, String> rollenBekannt;
    private int minute, abgleiche;

    private DiscordBot(MinecraftServer server, String token, String guild) {
        this.server = server;
        this.guild = guild;
        this.rest = new DiscordRest(token);
        this.befehle = new BotBefehle(this);
        int intents = DiscordGateway.GUILDS;
        if (CrownsConfig.BOT_CHAT.get() && !CrownsConfig.BOT_KANAL_CHAT.get().isBlank()) {
            intents |= DiscordGateway.GUILD_MESSAGES | DiscordGateway.MESSAGE_CONTENT;
        }
        this.gateway = new DiscordGateway(token, intents, this::ereignis);
    }

    public static synchronized void start(MinecraftServer server) {
        stop();
        if (!CrownsConfig.BOT_AKTIV.get()) return;
        String token = CrownsConfig.BOT_TOKEN.get().trim(), guild = CrownsConfig.BOT_GUILD.get().trim();
        if (token.isEmpty() || guild.isEmpty()) {
            CrownsCore.LOGGER.warn("Discord-Bot ist eingeschaltet, aber discordbot.token oder discordbot.server fehlt. Bot bleibt aus.");
            return;
        }
        bot = new DiscordBot(server, token, guild);
        bot.gateway.start();
        CrownsCore.LOGGER.info("Discord-Bot: verbinde");
    }

    public static synchronized void stop() {
        DiscordBot b = bot;
        bot = null;
        if (b != null) {
            b.gateway.stop();
            b.rest.shutdown();
        }
    }

    /** Eingeschaltet (auch wenn noch nicht verbunden). */
    public static boolean gestartet() {
        return bot != null;
    }

    public static boolean aktiv() {
        DiscordBot b = bot;
        return b != null && b.bereit;
    }

    static DiscordBot get() {
        return bot;
    }

    // ---------------------------------------------------------------- Kanaele

    /** Schreibt in den Kanal, wenn der Bot laeuft und der Kanal gesetzt ist. false = bitte den Webhook nehmen. */
    public static boolean kanal(String kanalId, String text) {
        DiscordBot b = bot;
        if (b == null || !b.bereit || kanalId == null || kanalId.isBlank() || text == null || text.isBlank()) return false;
        for (String teil : teilen(text)) b.rest.nachricht(kanalId.trim(), teil, null);
        return true;
    }

    static List<String> teilen(String text) {
        java.util.ArrayList<String> teile = new java.util.ArrayList<>();
        String rest = text;
        while (rest.length() > 1900) {
            int cut = rest.lastIndexOf('\n', 1900);
            if (cut < 950) cut = 1900;
            teile.add(rest.substring(0, cut));
            rest = rest.substring(cut);
        }
        teile.add(rest);
        return teile;
    }

    static String newsKanal() {
        String k = CrownsConfig.BOT_KANAL_NEWS.get();
        return k.isBlank() ? CrownsConfig.BOT_KANAL_KRIEGSNEWS.get() : k;
    }

    static String antragsKanal() {
        String k = CrownsConfig.BOT_KANAL_ANTRAEGE.get();
        return k.isBlank() ? newsKanal() : k;
    }

    /** Stadtantrag mit Knoepfen Annehmen/Ablehnen. false = kein Bot-Kanal, bitte den Webhook nehmen. */
    public static boolean stadtAntrag(String text, String stadt) {
        DiscordBot b = bot;
        String k = antragsKanal();
        if (b == null || !b.bereit || k.isBlank()) return false;
        b.rest.nachricht(k.trim(), text, BotBefehle.zeile(
                BotBefehle.knopf(3, "Annehmen", "stadt:ja:" + stadt),
                BotBefehle.knopf(4, "Ablehnen", "stadt:nein:" + stadt)));
        return true;
    }

    // ---------------------------------------------------------------- Gateway

    private void ereignis(String typ, JsonObject d) {
        switch (typ) {
            case "READY" -> {
                appId = d.getAsJsonObject("application").get("id").getAsString();
                String name = d.getAsJsonObject("user").get("username").getAsString();
                CrownsCore.LOGGER.info("Discord-Bot: angemeldet als {}", name);
                bereit = true;
                registrieren();
                server.execute(this::syncPlanen);
            }
            case "RESUMED" -> bereit = true;
            case "TOT" -> bereit = false;
            case "INTERACTION_CREATE" -> befehle.interaktion(d);
            case "MESSAGE_CREATE" -> chatVonDiscord(d);
            default -> {
            }
        }
    }

    private void registrieren() {
        JsonArray defs = BotBefehle.definitionen();
        rest.normal().submit(() -> {
            JsonElement r = rest.jetztArray("PUT", "/applications/" + appId + "/guilds/" + guild + "/commands", defs);
            CrownsCore.LOGGER.info("Discord-Bot: {} Befehle angemeldet", r != null && r.isJsonArray() ? r.getAsJsonArray().size() : 0);
        });
    }

    // ---------------------------------------------------------------- Rechte

    /** Discord-Konto zu Minecraft-Konto oder null. */
    UUID verknuepft(String discordId) {
        return CrownsData.get(server).discord.get(discordId);
    }

    /** Admin: Discord-Rolle aus der Config oder verknuepftes Konto ist OP bzw. in allgemein.admins. Nur auf dem Server-Thread. */
    boolean admin(String discordId, JsonArray rollen) {
        String rolle = CrownsConfig.BOT_ADMIN_ROLLE.get().trim();
        if (!rolle.isEmpty() && rollen != null) {
            for (JsonElement r : rollen) if (rolle.equals(r.getAsString())) return true;
        }
        UUID id = verknuepft(discordId);
        if (id == null) return false;
        String name = Names.of(server, id);
        for (String s : CrownsConfig.ADMINS.get()) if (s.equalsIgnoreCase(name)) return true;
        if (server.getProfileCache() != null) {
            var gp = server.getProfileCache().get(id);
            if (gp.isPresent() && server.getPlayerList().isOp(gp.get())) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- Verknuepfen

    /** Fuer den Selbsttest: die angemeldeten Befehle und die Karte als PNG. */
    public static JsonArray befehlsListe() {
        return BotBefehle.definitionen();
    }

    public static byte[] kartenPng(MinecraftServer server) throws java.io.IOException {
        return KartenBild.png(KartenBild.stand(CrownsData.get(server)));
    }

    public static String neuerCode(String discordId) {
        return neuerCode(discordId, "");
    }

    static String neuerCode(String discordId, String discordName) {
        CODES.values().removeIf(v -> (long) v[1] < System.currentTimeMillis() || v[0].equals(discordId));
        String zeichen = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(zeichen.charAt(ZUFALL.nextInt(zeichen.length())));
        CODES.put(sb.toString(), new Object[]{discordId, System.currentTimeMillis() + 10 * 60_000L, discordName});
        return sb.toString();
    }

    /** Im Spiel: /discord verknuepfen CODE. null = verknuepft, sonst der Fehler. */
    public static String verknuepfen(MinecraftServer server, ServerPlayer p, String code) {
        Object[] v = CODES.remove(code.trim().toUpperCase(Locale.ROOT));
        if (v == null || (long) v[1] < System.currentTimeMillis()) return "Unbekannter oder abgelaufener Code. Hol dir im Discord mit /verknuepfen einen neuen.";
        CrownsData d = CrownsData.get(server);
        String discordId = (String) v[0];
        for (Map.Entry<String, UUID> e : d.discord.entrySet()) {
            if (e.getValue().equals(p.getUUID()) && !e.getKey().equals(discordId)) GETRENNT.add(e.getKey());
        }
        d.discord.values().removeIf(u -> u.equals(p.getUUID()));
        d.discord.put(discordId, p.getUUID());
        GETRENNT.remove(discordId);
        d.setDirty();
        DiscordBot b = bot;
        if (b != null) {
            b.rest.dm(discordId, "Dein Discord ist jetzt mit " + p.getGameProfile().getName() + " verknuepft.");
            b.syncPlanen();
        }
        return null;
    }

    public static boolean trennen(MinecraftServer server, UUID spieler) {
        CrownsData d = CrownsData.get(server);
        boolean weg = false;
        for (java.util.Iterator<Map.Entry<String, UUID>> it = d.discord.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, UUID> e = it.next();
            if (e.getValue().equals(spieler)) {
                GETRENNT.add(e.getKey());
                it.remove();
                weg = true;
            }
        }
        if (weg) {
            d.setDirty();
            DiscordBot b = bot;
            if (b != null) b.syncPlanen();
        }
        return weg;
    }

    // ---------------------------------------------------------------- Automatisch

    /** Aus dem Scheduler, einmal pro Minute. */
    public static void everyMinute(MinecraftServer server) {
        CrownsData d = CrownsData.get(server);
        long jetzt = TimeUtil.now();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) kontoMerken(d, p, jetzt);
        DiscordBot b = bot;
        if (b == null) return;
        if (++b.minute % 5 == 1) b.syncPlanen();
        b.erinnerung(d, jetzt);
    }

    static void kontoMerken(CrownsData d, ServerPlayer p, long jetzt) {
        try {
            d.kontoStand.put(p.getUUID(), new double[]{Economy.get().balance(p), jetzt});
            d.setDirty();
        } catch (Exception ignored) {
        }
    }

    /** Besetzungsalarm per DM an alle verknuepften Mitglieder der angegriffenen Nation. */
    public static void besetzungsAlarm(MinecraftServer server, Nation verteidiger, String text) {
        DiscordBot b = bot;
        if (b == null || verteidiger == null || !CrownsConfig.BOT_ALARM.get()) return;
        for (Map.Entry<String, UUID> e : CrownsData.get(server).discord.entrySet()) {
            if (verteidiger.mitglieder.containsKey(e.getValue())) b.rest.dm(e.getKey(), ":rotating_light: " + text);
        }
    }

    private void erinnerung(CrownsData d, long jetzt) {
        if (!CrownsConfig.BOT_ERINNERUNG.get()) return;
        ZonedDateTime z = TimeUtil.zoned(jetzt);
        DayOfWeek tag;
        try {
            tag = DayOfWeek.valueOf(CrownsConfig.BOT_ERINNERUNG_TAG.get().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            tag = DayOfWeek.FRIDAY;
        }
        if (z.getDayOfWeek() != tag || z.getHour() != CrownsConfig.BOT_ERINNERUNG_STUNDE.get()) return;
        String woche = TimeUtil.weekKey(jetzt);
        if (woche.equals(d.letzteErinnerung)) return;
        d.letzteErinnerung = woche;
        d.setDirty();
        double minWoche = CrownsConfig.AKTIV_WOCHE_STUNDEN.get(), minWe = CrownsConfig.AKTIV_WOCHENENDE_STUNDEN.get();
        for (Map.Entry<String, UUID> e : d.discord.entrySet()) {
            ActivityRecord r = d.activity.get(e.getValue());
            if (r != null && r.pauseBis > jetzt) continue;
            long ges = r == null ? 0 : r.gesamt(woche), we = r == null ? 0 : r.wochenende(woche);
            long fehltWoche = Math.max(0, (long) (minWoche * 3600) - ges), fehltWe = Math.max(0, (long) (minWe * 3600) - we);
            if (fehltWoche == 0 && fehltWe == 0) continue;
            rest.dm(e.getKey(), ":hourglass: Erinnerung: Diese Woche fehlen dir noch " + Msg.hours(fehltWoche) + " Spielzeit"
                    + (fehltWe > 0 ? ", davon " + Msg.hours(fehltWe) + " am Wochenende" : "") + ". Bisher: " + Msg.hours(ges)
                    + ". Kannst du nicht, melde im Discord mit /pause eine Pause an.");
        }
    }

    // ---------------------------------------------------------------- Rollen und Namen

    /** Sollzustand auf dem Server-Thread einsammeln, abgleichen im REST-Thread. */
    void syncPlanen() {
        boolean rollen = CrownsConfig.BOT_ROLLEN.get(), namen = CrownsConfig.BOT_NAMEN.get();
        if (!rollen && !namen) return;
        Set<String> getrennt = new HashSet<>(GETRENNT);
        CrownsData d = CrownsData.get(server);
        Map<String, Object[]> nationen = new LinkedHashMap<>();
        for (Nation n : d.nations.values()) {
            if (n.gespielt()) nationen.put(n.key(), new Object[]{n.name, de.niklasmetzger.crownscore.world.WorldMap.colorOf(n), d.discordRollen.get(n.key())});
        }
        Map<String, String> alteRollen = new HashMap<>(d.discordRollen);
        Map<String, String[]> nutzer = new LinkedHashMap<>();
        for (Map.Entry<String, UUID> e : d.discord.entrySet()) {
            Nation n = d.nationOf(e.getValue());
            String name = Names.of(server, e.getValue());
            String status = n == null ? "" : n.isChef(e.getValue()) ? "chef" : n.beamte.contains(e.getValue()) ? "beamte" : "";
            nutzer.put(e.getKey(), new String[]{n == null ? null : n.key(), status, n == null ? name : "[" + n.kuerzel + "] " + name});
        }
        rest.normal().submit(() -> {
            try {
                abgleichen(nationen, alteRollen, nutzer, getrennt, rollen, namen);
            } catch (Exception e) {
                CrownsCore.LOGGER.warn("Discord-Bot: Rollenabgleich fehlgeschlagen: {}", e.toString());
            }
        });
    }

    private void abgleichen(Map<String, Object[]> nationen, Map<String, String> alteRollen, Map<String, String[]> nutzer, Set<String> getrennt,
                            boolean rollen, boolean namen) {
        // Stuendlich frisch vom Server lesen: von Hand geaenderte Rollen und Namen werden so wieder gerichtet
        if (++abgleiche % 12 == 0) {
            rollenIst.clear();
            nickIst.clear();
            nichtImServer.clear();
        }
        if (rollenBekannt == null) rollenBekannt = new HashMap<>(alteRollen);
        Map<String, String> nationRolle = new HashMap<>();
        String chefRolle = null, beamteRolle = null;
        if (rollen) {
            JsonElement liste = rest.jetzt("GET", "/guilds/" + guild + "/roles", null);
            if (liste == null || !liste.isJsonArray()) return;
            Map<String, String> idZuName = new HashMap<>();
            Map<String, Integer> idZuFarbe = new HashMap<>();
            for (JsonElement r : liste.getAsJsonArray()) {
                JsonObject o = r.getAsJsonObject();
                idZuName.put(o.get("id").getAsString(), o.get("name").getAsString());
                idZuFarbe.put(o.get("id").getAsString(), o.get("color").getAsInt());
            }
            for (Map.Entry<String, Object[]> e : nationen.entrySet()) {
                String name = (String) e.getValue()[0];
                int farbe = (int) e.getValue()[1] & 0xFFFFFF;
                String id = rollenBekannt.get(e.getKey());
                if (id == null || !idZuName.containsKey(id)) {
                    // Gibt es schon eine Rolle mit dem Namen (z.B. nach einem Abbruch), die keine andere Nation hat? Dann die nehmen
                    id = null;
                    for (Map.Entry<String, String> x : idZuName.entrySet()) {
                        if (x.getValue().equals(name) && !rollenBekannt.containsValue(x.getKey())) id = x.getKey();
                    }
                    if (id == null) id = rolleAnlegen(name, farbe);
                    if (id != null) rollenBekannt.put(e.getKey(), id);
                }
                else if (!name.equals(idZuName.get(id)) || idZuFarbe.get(id) != farbe) {
                    JsonObject b = new JsonObject();
                    b.addProperty("name", name);
                    b.addProperty("color", farbe);
                    rest.jetzt("PATCH", "/guilds/" + guild + "/roles/" + id, b);
                }
                if (id != null) nationRolle.put(e.getKey(), id);
            }
            // Rollen aufgeloester Nationen loeschen
            for (java.util.Iterator<Map.Entry<String, String>> it = rollenBekannt.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, String> e = it.next();
                if (!nationen.containsKey(e.getKey())) {
                    if (idZuName.containsKey(e.getValue())) rest.jetzt("DELETE", "/guilds/" + guild + "/roles/" + e.getValue(), null);
                    it.remove();
                }
            }
            chefRolle = rolleNachName(idZuName, CrownsConfig.BOT_ROLLE_CHEF.get());
            beamteRolle = rolleNachName(idZuName, CrownsConfig.BOT_ROLLE_BEAMTE.get());
            Map<String, String> fertig = new HashMap<>(nationRolle);
            server.execute(() -> {
                CrownsData d = CrownsData.get(server);
                d.discordRollen.clear();
                d.discordRollen.putAll(fertig);
                d.setDirty();
            });
        }
        Set<String> verwaltet = new HashSet<>(nationRolle.values());
        if (chefRolle != null) verwaltet.add(chefRolle);
        if (beamteRolle != null) verwaltet.add(beamteRolle);
        // Getrennte Konten: verwaltete Rollen und Namen zuruecknehmen
        for (String id : getrennt) {
            if (nutzer.containsKey(id)) {
                GETRENNT.remove(id);
                continue;
            }
            if (rollenIst.containsKey(id) || mitgliedLaden(id)) {
                for (String x : new HashSet<>(rollenIst.get(id))) {
                    if (verwaltet.contains(x) && rest.jetzt("DELETE", "/guilds/" + guild + "/members/" + id + "/roles/" + x, null) != null) {
                        rollenIst.get(id).remove(x);
                    }
                }
                if (namen && !nickVerboten.contains(id) && !"".equals(nickIst.get(id))) {
                    JsonObject b = new JsonObject();
                    b.add("nick", com.google.gson.JsonNull.INSTANCE);
                    if (rest.jetzt("PATCH", "/guilds/" + guild + "/members/" + id, b) != null) nickIst.put(id, "");
                }
            }
            GETRENNT.remove(id);
        }
        for (Map.Entry<String, String[]> e : nutzer.entrySet()) {
            String id = e.getKey();
            if (!rollenIst.containsKey(id) && !mitgliedLaden(id)) continue;
            if (rollen) {
                Set<String> soll = new HashSet<>();
                String nk = e.getValue()[0];
                if (nk != null && nationRolle.containsKey(nk)) soll.add(nationRolle.get(nk));
                if ("chef".equals(e.getValue()[1]) && chefRolle != null) soll.add(chefRolle);
                if ("beamte".equals(e.getValue()[1]) && beamteRolle != null) soll.add(beamteRolle);
                Set<String> ist = rollenIst.get(id);
                for (String r : soll) {
                    if (!ist.contains(r) && rest.jetzt("PUT", "/guilds/" + guild + "/members/" + id + "/roles/" + r, null) != null) ist.add(r);
                }
                for (String r : new HashSet<>(ist)) {
                    if (verwaltet.contains(r) && !soll.contains(r) && rest.jetzt("DELETE", "/guilds/" + guild + "/members/" + id + "/roles/" + r, null) != null) {
                        ist.remove(r);
                    }
                }
            }
            if (namen && !nickVerboten.contains(id)) {
                String nick = DiscordRest.kuerzen(e.getValue()[2], 32);
                if (!nick.equals(nickIst.get(id))) {
                    JsonObject b = new JsonObject();
                    b.addProperty("nick", nick);
                    if (rest.jetzt("PATCH", "/guilds/" + guild + "/members/" + id, b) != null) nickIst.put(id, nick);
                    else if (rest.letzterStatus() == 403) nickVerboten.add(id); // z.B. Serverbesitzer: Discord erlaubt das nicht
                }
            }
        }
    }

    private boolean mitgliedLaden(String id) {
        if (nichtImServer.contains(id)) return false;
        JsonElement m = rest.jetzt("GET", "/guilds/" + guild + "/members/" + id, null);
        if (m == null || !m.isJsonObject()) {
            if (rest.letzterStatus() == 404) nichtImServer.add(id); // sonst (Zeitueberschreitung, 5xx) beim naechsten Mal neu versuchen
            return false;
        }
        Set<String> rollen = new HashSet<>();
        for (JsonElement r : m.getAsJsonObject().getAsJsonArray("roles")) rollen.add(r.getAsString());
        rollenIst.put(id, rollen);
        JsonElement nick = m.getAsJsonObject().get("nick");
        nickIst.put(id, nick == null || nick.isJsonNull() ? "" : nick.getAsString());
        return true;
    }

    private String rolleAnlegen(String name, int farbe) {
        JsonObject b = new JsonObject();
        b.addProperty("name", name);
        b.addProperty("color", farbe);
        b.addProperty("mentionable", false);
        JsonElement r = rest.jetzt("POST", "/guilds/" + guild + "/roles", b);
        return r != null && r.isJsonObject() && r.getAsJsonObject().has("id") ? r.getAsJsonObject().get("id").getAsString() : null;
    }

    private String rolleNachName(Map<String, String> idZuName, String name) {
        if (name == null || name.isBlank()) return null;
        for (Map.Entry<String, String> e : idZuName.entrySet()) if (e.getValue().equalsIgnoreCase(name.trim())) return e.getKey();
        return rolleAnlegen(name.trim(), 0);
    }

    // ---------------------------------------------------------------- Chat-Bruecke

    private static boolean chatAn() {
        return bot != null && CrownsConfig.BOT_CHAT.get() && !CrownsConfig.BOT_KANAL_CHAT.get().isBlank();
    }

    private static String ohneMarkdown(String s) {
        return s.replaceAll("([\\\\*_~`|>])", "\\\\$1");
    }

    private void chatVonDiscord(JsonObject d) {
        if (!chatAn() || !CrownsConfig.BOT_KANAL_CHAT.get().trim().equals(d.get("channel_id").getAsString())) return;
        JsonObject autor = d.getAsJsonObject("author");
        if (autor == null || (autor.has("bot") && autor.get("bot").getAsBoolean()) || d.has("webhook_id")) return;
        String text = d.has("content") ? d.get("content").getAsString() : "";
        if (text.isBlank()) return;
        String discordId = autor.get("id").getAsString();
        String anzeige = autor.has("global_name") && !autor.get("global_name").isJsonNull() ? autor.get("global_name").getAsString() : autor.get("username").getAsString();
        if (d.has("member") && d.getAsJsonObject("member").has("nick") && !d.getAsJsonObject("member").get("nick").isJsonNull()) {
            anzeige = d.getAsJsonObject("member").get("nick").getAsString();
        }
        String fallback = anzeige;
        server.execute(() -> {
            UUID id = verknuepft(discordId);
            String name = id == null ? fallback : Names.of(server, id);
            String sauber = text.replace("§", "").replaceAll("[\\r\\n]+", " ");
            if (sauber.length() > 256) sauber = sauber.substring(0, 256) + "...";
            server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("§9[Discord] §f" + name.replace("§", "") + "§7: §f" + sauber), false);
        });
    }

    public static class BotEvents {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onChat(ServerChatEvent e) {
            if (!chatAn() || e.isCanceled()) return;
            CrownsData d = CrownsData.get(e.getPlayer().server);
            Nation n = d.nationOf(e.getPlayer().getUUID());
            String name = (n == null ? "" : "[" + n.kuerzel + "] ") + e.getPlayer().getGameProfile().getName();
            kanal(CrownsConfig.BOT_KANAL_CHAT.get(), "**" + ohneMarkdown(name) + "**: " + ohneMarkdown(e.getRawText()));
        }

        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            CrownsData d = CrownsData.get(p.server);
            Double offen = d.offeneZahlungen.get(p.getUUID());
            if (offen != null && offen > 0 && Economy.get().deposit(p, offen)) {
                d.offeneZahlungen.remove(p.getUUID());
                d.setDirty();
                Msg.player(p, "Dir wurden " + Msg.money(offen) + " gutgeschrieben (aus dem Discord).");
            }
            kontoMerken(d, p, TimeUtil.now());
            if (chatAn()) kanal(CrownsConfig.BOT_KANAL_CHAT.get(), ":arrow_right: **" + ohneMarkdown(p.getGameProfile().getName()) + "** ist auf dem Server.");
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            kontoMerken(CrownsData.get(p.server), p, TimeUtil.now());
            if (chatAn()) kanal(CrownsConfig.BOT_KANAL_CHAT.get(), ":arrow_left: **" + ohneMarkdown(p.getGameProfile().getName()) + "** hat den Server verlassen.");
        }
    }
}
