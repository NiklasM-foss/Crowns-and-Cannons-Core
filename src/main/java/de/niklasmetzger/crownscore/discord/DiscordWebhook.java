package de.niklasmetzger.crownscore.discord;

import com.google.gson.JsonObject;
import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.config.CrownsConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Schickt Textnachrichten an Discord-Webhooks, asynchron und mit Laengenbegrenzung. */
public final class DiscordWebhook {
    private static final int LIMIT = 1900;
    private static ExecutorService executor;
    private static HttpClient client;

    private DiscordWebhook() {}

    public static synchronized void start() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "crownscore-discord");
                t.setDaemon(true);
                return t;
            });
            client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        }
    }

    public static synchronized void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void kriegsnews(String text) {
        send(CrownsConfig.WEBHOOK_KRIEGSNEWS.get(), text);
    }

    public static void gericht(String text) {
        send(CrownsConfig.WEBHOOK_GERICHT.get(), text);
    }

    public static void aktivitaet(String text) {
        send(CrownsConfig.WEBHOOK_AKTIVITAET.get(), text);
    }

    public static void news(String text) {
        String url = CrownsConfig.WEBHOOK_NEWS.get();
        if (url == null || url.isBlank()) url = CrownsConfig.WEBHOOK_KRIEGSNEWS.get();
        send(url, text);
    }

    /** Antraege an die Admins, sonst News-Kanal. */
    public static void antraege(String text) {
        String url = CrownsConfig.WEBHOOK_ANTRAEGE.get();
        if (url == null || url.isBlank()) {
            news(text);
            return;
        }
        send(url, text);
    }

    public static void send(String url, String text) {
        if (url == null || url.isBlank() || text == null || text.isBlank()) return;
        if (executor == null) start();
        final String target = url.trim();
        for (String chunk : split(text)) {
            executor.submit(() -> post(target, chunk));
        }
    }

    private static List<String> split(String text) {
        List<String> parts = new ArrayList<>();
        String rest = text;
        while (rest.length() > LIMIT) {
            int cut = rest.lastIndexOf('\n', LIMIT);
            if (cut < LIMIT / 2) cut = LIMIT;
            parts.add(rest.substring(0, cut));
            rest = rest.substring(cut);
        }
        parts.add(rest);
        return parts;
    }

    private static void post(String url, String content) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("content", content);
            json.addProperty("username", "Crowns Core");
            // Spielertexte (z.B. Stadtnamen) duerfen niemanden anpingen, auch nicht @everyone
            JsonObject mentions = new JsonObject();
            mentions.add("parse", new com.google.gson.JsonArray());
            json.add("allowed_mentions", mentions);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res;
            try {
                res = client.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (java.net.http.HttpTimeoutException timeout) {
                // Discord hat die Nachricht vielleicht schon angenommen: nicht doppelt posten
                throw timeout;
            } catch (java.io.IOException first) {
                // z.B. "Connection reset": einmal kurz warten und neu versuchen
                Thread.sleep(2000);
                res = client.send(req, HttpResponse.BodyHandlers.ofString());
            }
            if (res.statusCode() == 429) {
                Thread.sleep(2000);
                client.send(req, HttpResponse.BodyHandlers.ofString());
            } else if (res.statusCode() >= 300) {
                CrownsCore.LOGGER.warn("Discord-Webhook antwortete mit {}: {}", res.statusCode(), res.body());
            }
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            CrownsCore.LOGGER.warn("Discord-Webhook fehlgeschlagen: {}", e.toString());
        }
    }
}
