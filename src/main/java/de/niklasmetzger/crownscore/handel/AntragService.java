package de.niklasmetzger.crownscore.handel;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Stadt;
import net.minecraft.server.MinecraftServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Antraege im Browser entscheiden: Der Server meldet jeden Antrag an den Antrags-Dienst (Ordner editor/antraege/,
 * laeuft beim Rechte-Editor), der Link mit geheimem Token geht in den Discord. Alle 30 Sekunden holt der Server
 * die Entscheidungen ab. Der Minecraft-Server braucht dafuer keinen offenen Port.
 */
public final class AntragService {
    private static final SecureRandom RND = new SecureRandom();
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "crownscore-antraege");
        t.setDaemon(true);
        return t;
    });

    private record Entscheidung(String id, String entscheidung, String admin, String grund) {}

    private static final ConcurrentLinkedQueue<Entscheidung> EINGANG = new ConcurrentLinkedQueue<>();
    private static volatile boolean abfrageLaeuft;
    private static int sekunden;
    private static boolean warnungGezeigt;

    private AntragService() {}

    /** Basisadresse der Seite, z.B. https://editor.example.de/antraege/, leer = aus. */
    public static String webUrl() {
        String u = CrownsConfig.ANTRAG_URL.get().trim();
        if (u.isEmpty()) {
            String ed = CrownsConfig.RECHTE_EDITOR_URL.get().trim();
            if (ed.isEmpty()) return "";
            if (!ed.endsWith("/")) ed += "/";
            u = ed.endsWith("/editor/") ? ed.substring(0, ed.length() - "editor/".length()) + "antraege/" : ed + "antraege/";
        }
        return u.endsWith("/") ? u : u + "/";
    }

    public static boolean aktiv() {
        return !webUrl().isEmpty() && !CrownsConfig.ANTRAG_SCHLUESSEL.get().isBlank();
    }

    private static String zufall(int bytes) {
        byte[] b = new byte[bytes];
        RND.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Legt Id und Token fuer einen neuen Antrag an. [0] = Id, [1] = Token. */
    public static String[] neueIds() {
        return new String[]{zufall(9), zufall(24)};
    }

    public static String link(String id, String token) {
        return webUrl() + "#" + id + "." + token;
    }

    /** Antrag an den Dienst melden (im Hintergrund). */
    public static void melden(String id, String token, String art, String titel, String nation, String von, String ort, List<String> details) {
        if (!aktiv()) return;
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("token", token);
        o.addProperty("art", art);
        o.addProperty("titel", titel);
        o.addProperty("nation", nation);
        o.addProperty("von", von);
        o.addProperty("ort", ort);
        JsonArray d = new JsonArray();
        details.forEach(d::add);
        o.add("details", d);
        EXEC.submit(() -> post("api/neu", o));
    }

    /** Im Spiel entschieden: die Seite soll das anzeigen. */
    public static void erledigt(String id, String entscheidung, String admin, String grund) {
        if (!aktiv() || id == null || id.isEmpty()) return;
        JsonObject o = new JsonObject();
        o.addProperty("entscheidung", entscheidung);
        o.addProperty("admin", admin);
        o.addProperty("grund", grund == null ? "" : grund);
        EXEC.submit(() -> post("api/antrag/" + id + "/erledigt", o));
    }

    private static HttpRequest.Builder req(String pfad) {
        return HttpRequest.newBuilder(URI.create(webUrl() + pfad)).timeout(Duration.ofSeconds(15))
                .header("X-Crowns-Schluessel", CrownsConfig.ANTRAG_SCHLUESSEL.get().trim())
                .header("User-Agent", de.niklasmetzger.crownscore.perms.WebEditor.userAgent());
    }

    private static String post(String pfad, JsonObject body) {
        try {
            HttpResponse<String> r = HTTP.send(req(pfad).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) CrownsCore.LOGGER.warn("Antrags-Dienst {}: HTTP {} {}", pfad.replaceAll("antrag/[^/]+", "antrag/..."), r.statusCode(), r.body());
            return r.body();
        } catch (Exception e) {
            CrownsCore.LOGGER.warn("Antrags-Dienst nicht erreichbar: {}", e.toString());
            return null;
        }
    }

    /** Einmal pro Sekunde aus dem Scheduler: alle 30 Sekunden abfragen, Ergebnisse im Server-Thread anwenden. */
    public static void everySecond(MinecraftServer server) {
        Entscheidung e;
        List<String> quittiert = new ArrayList<>();
        while ((e = EINGANG.poll()) != null) {
            anwenden(server, e);
            quittiert.add(e.id());
        }
        if (!quittiert.isEmpty()) {
            JsonObject o = new JsonObject();
            JsonArray ids = new JsonArray();
            quittiert.forEach(ids::add);
            o.add("ids", ids);
            EXEC.submit(() -> post("api/quittung", o));
        }
        if (++sekunden % 30 != 0 || abfrageLaeuft || !aktiv()) return;
        if (StadtService.offen(CrownsData.get(server)).isEmpty()) return;
        abfrageLaeuft = true;
        EXEC.submit(() -> {
            try {
                HttpResponse<String> r = HTTP.send(req("api/entscheidungen").GET().build(), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() != 200) {
                    if (!warnungGezeigt) CrownsCore.LOGGER.warn("Antrags-Dienst: HTTP {} beim Abholen, Schluessel pruefen", r.statusCode());
                    warnungGezeigt = true;
                    return;
                }
                for (JsonElement el : GSON.fromJson(r.body(), JsonArray.class)) {
                    JsonObject o = el.getAsJsonObject();
                    EINGANG.add(new Entscheidung(o.get("id").getAsString(), o.get("entscheidung").getAsString(),
                            o.has("admin") ? o.get("admin").getAsString() : "", o.has("grund") ? o.get("grund").getAsString() : ""));
                }
            } catch (Exception ex) {
                CrownsCore.LOGGER.debug("Antrags-Dienst nicht erreichbar: {}", ex.toString());
            } finally {
                abfrageLaeuft = false;
            }
        });
    }

    private static void anwenden(MinecraftServer server, Entscheidung e) {
        CrownsData d = CrownsData.get(server);
        Stadt s = null;
        for (Stadt x : d.staedte) if (e.id().equals(x.antragId)) s = x;
        if (s == null || s.bestaetigt) return; // schon im Spiel entschieden
        String admin = StadtService.sauber(e.admin(), 32).isEmpty() ? "Admin (Browser)" : StadtService.sauber(e.admin(), 32) + " (Browser)";
        String err = "annehmen".equals(e.entscheidung()) ? StadtService.annehmen(server, s.name, admin, false)
                : StadtService.ablehnen(server, s.name, admin, e.grund(), false);
        if (err != null) CrownsCore.LOGGER.warn("Entscheidung aus dem Browser nicht angewendet: {}", err);
        else CrownsCore.LOGGER.info("Stadtantrag {}: {} von {} im Browser", s.name, e.entscheidung(), admin);
    }
}
