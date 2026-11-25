package de.niklasmetzger.crownscore.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.niklasmetzger.crownscore.CrownsCore;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Discord-REST-API fuer den Bot. Zwei Warteschlangen: eine schnelle fuer Antworten auf Befehle (Discord gibt dafuer nur
 * drei Sekunden) und eine fuer alles andere (Kanalnachrichten, Rollen, Namen). Bei 429 wird die verlangte Zeit gewartet.
 */
final class DiscordRest {
    static final String API = "https://discord.com/api/v10";
    private final String token;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService schnell = Executors.newFixedThreadPool(4, r -> daemon(r, "crownscore-bot-antwort"));
    /** HTTP-Status der letzten Anfrage dieses Threads (0 = keine Antwort). */
    private final ThreadLocal<Integer> status = ThreadLocal.withInitial(() -> 0);
    private final ExecutorService normal = Executors.newSingleThreadExecutor(r -> daemon(r, "crownscore-bot-rest"));

    DiscordRest(String token) {
        this.token = token;
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    void shutdown() {
        schnell.shutdown();
        normal.shutdown();
        try {
            // Letzte Meldungen (z.B. Ausloggen) noch kurz rauslassen
            normal.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        schnell.shutdownNow();
        normal.shutdownNow();
    }

    /** HTTP-Status der letzten blockierenden Anfrage in diesem Thread. */
    int letzterStatus() {
        return status.get();
    }

    /** Ohne Pings: Spielertexte duerfen niemanden erwaehnen, auch nicht @everyone. */
    static JsonObject keinePings() {
        JsonObject m = new JsonObject();
        m.add("parse", new JsonArray());
        return m;
    }

    /** Nachricht mit Text (und optional Knoepfen) in einen Kanal. */
    CompletableFuture<JsonElement> nachricht(String kanal, String text, JsonArray knoepfe) {
        JsonObject body = new JsonObject();
        body.addProperty("content", kuerzen(text, 1990));
        body.add("allowed_mentions", keinePings());
        if (knoepfe != null) body.add("components", knoepfe);
        return senden("POST", "/channels/" + kanal + "/messages", body, false);
    }

    /** Antwort auf eine Interaktion (Befehl, Knopf, Formular). */
    CompletableFuture<JsonElement> antwort(String id, String token, JsonObject body) {
        return senden("POST", "/interactions/" + id + "/" + token + "/callback", body, true);
    }

    /** Spaetere Antwort nach einer aufgeschobenen Interaktion (Typ 5), optional mit Datei. */
    CompletableFuture<JsonElement> nachtrag(String appId, String token, JsonObject body, String dateiname, byte[] datei) {
        String pfad = "/webhooks/" + appId + "/" + token + "/messages/@original";
        if (datei == null) return senden("PATCH", pfad, body, true);
        return CompletableFuture.supplyAsync(() -> {
            try {
                String grenze = "----crownscore" + System.nanoTime();
                body.add("attachments", anhang(dateiname));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                out.write(("--" + grenze + "\r\nContent-Disposition: form-data; name=\"payload_json\"\r\nContent-Type: application/json\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                out.write(("\r\n--" + grenze + "\r\nContent-Disposition: form-data; name=\"files[0]\"; filename=\"" + dateiname
                        + "\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(datei);
                out.write(("\r\n--" + grenze + "--\r\n").getBytes(StandardCharsets.UTF_8));
                HttpRequest req = basis(pfad).header("Content-Type", "multipart/form-data; boundary=" + grenze)
                        .method("PATCH", HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
                return ausfuehren(req, "PATCH " + pfad);
            } catch (Exception e) {
                CrownsCore.LOGGER.warn("Discord-Bot: Datei konnte nicht gesendet werden: {}", e.toString());
                return null;
            }
        }, normal);
    }

    private static JsonArray anhang(String name) {
        JsonArray a = new JsonArray();
        JsonObject o = new JsonObject();
        o.addProperty("id", 0);
        o.addProperty("filename", name);
        a.add(o);
        return a;
    }

    /** Privatnachricht an ein Discord-Konto. */
    void dm(String nutzer, String text) {
        normal.submit(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("recipient_id", nutzer);
            JsonElement kanal = ausfuehren(json("POST", "/users/@me/channels", body), "DM-Kanal");
            if (kanal == null || !kanal.isJsonObject() || !kanal.getAsJsonObject().has("id")) return;
            JsonObject msg = new JsonObject();
            msg.addProperty("content", kuerzen(text, 1990));
            msg.add("allowed_mentions", keinePings());
            ausfuehren(json("POST", "/channels/" + kanal.getAsJsonObject().get("id").getAsString() + "/messages", msg), "DM");
        });
    }

    CompletableFuture<JsonElement> senden(String methode, String pfad, JsonObject body, boolean eilig) {
        return CompletableFuture.supplyAsync(() -> ausfuehren(json(methode, pfad, body), methode + " " + pfad), eilig ? schnell : normal);
    }

    ExecutorService normal() {
        return normal;
    }

    /** Blockierend mit einem Array als Body (Befehle anmelden). */
    JsonElement jetztArray(String methode, String pfad, JsonArray body) {
        HttpRequest req = basis(pfad).header("Content-Type", "application/json")
                .method(methode, HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        return ausfuehren(req, methode + " " + pfad);
    }

    /** Blockierend, nur aus den Bot-Threads aufrufen. */
    JsonElement jetzt(String methode, String pfad, JsonObject body) {
        return ausfuehren(json(methode, pfad, body), methode + " " + pfad);
    }

    private HttpRequest.Builder basis(String pfad) {
        return HttpRequest.newBuilder(URI.create(API + pfad))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bot " + token)
                .header("User-Agent", "DiscordBot (https://github.com/NiklasM-foss/crowns-core, " + CrownsCore.MODID + ")");
    }

    private HttpRequest json(String methode, String pfad, JsonObject body) {
        HttpRequest.Builder b = basis(pfad);
        if (body == null) {
            return b.method(methode, methode.equals("GET") || methode.equals("DELETE") ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString("")).build();
        }
        return b.header("Content-Type", "application/json")
                .method(methode, HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
    }

    private JsonElement ausfuehren(HttpRequest req, String was) {
        status.set(0);
        // POST nicht nach einem Netzwerkfehler wiederholen: Discord hat ihn vielleicht schon angenommen
        boolean wiederholbar = !req.method().equals("POST");
        for (int versuch = 0; versuch < 4; versuch++) {
            try {
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                status.set(res.statusCode());
                if (res.statusCode() == 429) {
                    double warten = 1.0;
                    try {
                        warten = JsonParser.parseString(res.body()).getAsJsonObject().get("retry_after").getAsDouble();
                    } catch (Exception ignored) {
                    }
                    Thread.sleep((long) (Math.min(warten, 30) * 1000) + 100);
                    continue;
                }
                if (res.statusCode() >= 300) {
                    CrownsCore.LOGGER.warn("Discord-Bot: {} antwortete mit {}: {}", was, res.statusCode(), kuerzen(res.body(), 300));
                    return null;
                }
                if (res.body() == null || res.body().isBlank()) return new JsonObject();
                return JsonParser.parseString(res.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (java.net.http.HttpTimeoutException e) {
                CrownsCore.LOGGER.warn("Discord-Bot: {} Zeitueberschreitung", was);
                return null;
            } catch (Exception e) {
                if (versuch == 3 || !wiederholbar) {
                    CrownsCore.LOGGER.warn("Discord-Bot: {} fehlgeschlagen: {}", was, e.toString());
                    return null;
                }
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    static String kuerzen(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
