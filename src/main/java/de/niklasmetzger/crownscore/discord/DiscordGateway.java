package de.niklasmetzger.crownscore.discord;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.niklasmetzger.crownscore.CrownsCore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Verbindung zum Discord-Gateway (WebSocket, JSON, ohne Kompression): Anmelden, Herzschlag, Wiederaufnehmen nach
 * Abbruechen. Ereignisse gehen an den Empfaenger (Typ, Daten). Jede Verbindung hat eine Nummer; Rueckmeldungen einer
 * ersetzten Verbindung werden verworfen, damit nie zwei Sitzungen gleichzeitig laufen. "TOT" meldet einen endgueltigen Fehler.
 */
final class DiscordGateway {
    private static final String URL = "wss://gateway.discord.gg/?v=10&encoding=json";
    static final int GUILDS = 1, GUILD_MESSAGES = 1 << 9, MESSAGE_CONTENT = 1 << 15;

    private final String token;
    private int intents;
    private final BiConsumer<String, JsonObject> empfaenger;
    private final ScheduledExecutorService zeit = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "crownscore-bot-gateway");
        t.setDaemon(true);
        return t;
    });
    private final HttpClient client = HttpClient.newHttpClient();

    // Zustand, nur unter "this" geaendert
    private int gen;
    private WebSocket ws;
    private boolean aus, geplant, ackErhalten = true;
    private Integer seq;
    private String sessionId, resumeUrl;
    private ScheduledFuture<?> herz;
    private int wartezeit = 5;

    DiscordGateway(String token, int intents, BiConsumer<String, JsonObject> empfaenger) {
        this.token = token;
        this.intents = intents;
        this.empfaenger = empfaenger;
    }

    synchronized void start() {
        verbinden(false);
    }

    void stop() {
        WebSocket w;
        synchronized (this) {
            aus = true;
            gen++;
            if (herz != null) herz.cancel(true);
            w = ws;
            ws = null;
        }
        if (w != null) {
            try {
                w.sendClose(1000, "Server faehrt herunter");
            } catch (Exception ignored) {
            }
        }
        zeit.shutdownNow();
    }

    private synchronized void verbinden(boolean wiederaufnehmen) {
        if (aus) return;
        geplant = false;
        if (!wiederaufnehmen) {
            sessionId = null;
            seq = null;
        }
        int meine = ++gen;
        String ziel = wiederaufnehmen && resumeUrl != null && sessionId != null ? resumeUrl + "/?v=10&encoding=json" : URL;
        client.newWebSocketBuilder().buildAsync(URI.create(ziel), new Hoerer(meine)).whenComplete((w, err) -> {
            synchronized (DiscordGateway.this) {
                if (meine != gen) {
                    if (w != null) w.abort();
                    return;
                }
                if (err != null) {
                    CrownsCore.LOGGER.warn("Discord-Bot: Verbindung fehlgeschlagen ({}), neuer Versuch in {} s", err.toString(), wartezeit);
                    neuVerbinden(wiederaufnehmen, naechsteWartezeit());
                } else {
                    ws = w;
                }
            }
        });
    }

    private int naechsteWartezeit() {
        int w = wartezeit;
        wartezeit = Math.min(wartezeit * 2, 120);
        return w;
    }

    /** Aktuelle Verbindung verwerfen und genau einen neuen Versuch planen. */
    private synchronized void neuVerbinden(boolean wiederaufnehmen, long sekunden) {
        if (aus) return;
        gen++;
        if (herz != null) herz.cancel(false);
        if (ws != null) {
            try {
                ws.abort();
            } catch (Exception ignored) {
            }
            ws = null;
        }
        if (geplant) return;
        geplant = true;
        zeit.schedule(() -> verbinden(wiederaufnehmen), sekunden, TimeUnit.SECONDS);
    }

    private synchronized void senden(int meine, int op, JsonElement d) {
        if (meine != gen || ws == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("op", op);
        o.add("d", d);
        try {
            // Warten, bis der Text raus ist: zwei gleichzeitige sendText wirft das JDK sonst ab
            ws.sendText(o.toString(), true).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            CrownsCore.LOGGER.warn("Discord-Bot: Senden an das Gateway fehlgeschlagen: {}", e.toString());
        }
    }

    private synchronized void herzschlag(int meine, boolean pruefen) {
        if (meine != gen) return;
        if (pruefen && !ackErhalten) {
            CrownsCore.LOGGER.warn("Discord-Bot: kein Herzschlag-ACK, verbinde neu");
            neuVerbinden(true, 1);
            return;
        }
        if (pruefen) ackErhalten = false;
        senden(meine, 1, seq == null ? com.google.gson.JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(seq));
    }

    private void verarbeiten(int meine, JsonObject o) {
        int op = o.get("op").getAsInt();
        String typ = null;
        JsonObject daten = null;
        synchronized (this) {
            if (meine != gen) return;
            if (o.has("s") && !o.get("s").isJsonNull()) seq = o.get("s").getAsInt();
            switch (op) {
                case 10 -> { // Hello
                    long intervall = o.getAsJsonObject("d").get("heartbeat_interval").getAsLong();
                    if (herz != null) herz.cancel(false);
                    ackErhalten = true;
                    herz = zeit.scheduleAtFixedRate(() -> herzschlag(meine, true), (long) (intervall * Math.random()), intervall, TimeUnit.MILLISECONDS);
                    JsonObject d = new JsonObject();
                    d.addProperty("token", token);
                    if (sessionId != null && seq != null) {
                        d.addProperty("session_id", sessionId);
                        d.addProperty("seq", seq);
                        senden(meine, 6, d);
                    } else {
                        d.addProperty("intents", intents);
                        JsonObject props = new JsonObject();
                        props.addProperty("os", "linux");
                        props.addProperty("browser", "crownscore");
                        props.addProperty("device", "crownscore");
                        d.add("properties", props);
                        senden(meine, 2, d);
                    }
                }
                case 11 -> ackErhalten = true;
                case 1 -> senden(meine, 1, seq == null ? com.google.gson.JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(seq));
                case 7 -> neuVerbinden(true, 1);
                case 9 -> {
                    boolean resumable = o.has("d") && o.get("d").isJsonPrimitive() && o.get("d").getAsBoolean();
                    if (!resumable) {
                        sessionId = null;
                        seq = null;
                    }
                    neuVerbinden(resumable, 2 + (long) (Math.random() * 3));
                }
                case 0 -> {
                    typ = o.get("t").getAsString();
                    daten = o.get("d").isJsonObject() ? o.getAsJsonObject("d") : new JsonObject();
                    if (typ.equals("READY")) {
                        sessionId = daten.get("session_id").getAsString();
                        resumeUrl = daten.has("resume_gateway_url") ? daten.get("resume_gateway_url").getAsString() : null;
                        wartezeit = 5;
                    }
                    if (typ.equals("RESUMED")) wartezeit = 5;
                }
                default -> {
                }
            }
        }
        // Ausserhalb der Sperre: der Empfaenger darf Zeit brauchen, ohne den Herzschlag aufzuhalten
        if (typ != null) {
            try {
                empfaenger.accept(typ, daten);
            } catch (Exception e) {
                CrownsCore.LOGGER.warn("Discord-Bot: Fehler bei {}: {}", typ, e.toString());
            }
        }
    }

    private void geschlossen(int meine, int code, String reason) {
        boolean tot = false;
        synchronized (this) {
            if (meine != gen || aus) return;
            switch (code) {
                case 4004 -> {
                    CrownsCore.LOGGER.error("Discord-Bot: Token ungueltig (4004). Bot bleibt aus, Meldungen gehen wieder ueber die Webhooks. Bitte discordbot.token pruefen.");
                    tot = true;
                }
                case 4014 -> {
                    if ((intents & MESSAGE_CONTENT) != 0) {
                        CrownsCore.LOGGER.error("Discord-Bot: Message Content Intent ist im Developer Portal nicht freigeschaltet. "
                                + "Die Chat-Bruecke von Discord ins Spiel bleibt aus, alles andere laeuft.");
                        intents &= ~MESSAGE_CONTENT;
                        neuVerbinden(false, 5);
                        return;
                    }
                    CrownsCore.LOGGER.error("Discord-Bot: Intents nicht erlaubt (4014), Bot bleibt aus.");
                    tot = true;
                }
                case 4010, 4011, 4012, 4013 -> {
                    CrownsCore.LOGGER.error("Discord-Bot: Gateway lehnt ab ({} {}), Bot bleibt aus.", code, reason);
                    tot = true;
                }
                default -> {
                }
            }
            if (tot) {
                aus = true;
                gen++;
                if (herz != null) herz.cancel(false);
                ws = null;
            } else {
                boolean wieder = code != 4007 && code != 4009;
                CrownsCore.LOGGER.info("Discord-Bot: Verbindung geschlossen ({} {}), verbinde neu", code, reason);
                neuVerbinden(wieder, naechsteWartezeit());
            }
        }
        if (tot) empfaenger.accept("TOT", new JsonObject());
    }

    private synchronized void fehler(int meine, Throwable error) {
        if (meine != gen || aus) return;
        CrownsCore.LOGGER.warn("Discord-Bot: Gateway-Fehler {}, verbinde neu", error.toString());
        neuVerbinden(true, naechsteWartezeit());
    }

    /** Hoerer einer einzelnen Verbindung mit eigenem Puffer. */
    private final class Hoerer implements WebSocket.Listener {
        private final int meine;
        private final StringBuilder puffer = new StringBuilder();

        Hoerer(int meine) {
            this.meine = meine;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            puffer.append(data);
            if (last) {
                String text = puffer.toString();
                puffer.setLength(0);
                try {
                    verarbeiten(meine, JsonParser.parseString(text).getAsJsonObject());
                } catch (Exception e) {
                    CrownsCore.LOGGER.warn("Discord-Bot: Gateway-Nachricht nicht verarbeitet: {}", e.toString());
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int code, String reason) {
            geschlossen(meine, code, reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            fehler(meine, error);
        }
    }
}
