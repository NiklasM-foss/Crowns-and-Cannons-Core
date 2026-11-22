"""Antraege fuer Crowns Core: Admins entscheiden im Browser, der Minecraft-Server holt die Entscheidungen ab.

Der Server meldet neue Antraege (z.B. Stadtgruendungen) mit dem gemeinsamen Schluessel an. Jeder Antrag hat ein
eigenes zufaelliges Token, das nur im Link im Admin-Kanal auf Discord steht. Wer den Link hat, kann einmal annehmen
oder ablehnen. Der Minecraft-Server fragt regelmaessig nach Entscheidungen und quittiert sie. Nur Python-Stdlib.

Umgebung: ANTRAEGE_SCHLUESSEL (Pflicht, derselbe Wert wie antraege.schluessel in der Mod-Config),
ANTRAEGE_DATEI (Standard /data/antraege.json), PORT (Standard 8080).
"""
import hmac
import json
import os
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SCHLUESSEL = os.environ.get("ANTRAEGE_SCHLUESSEL", "")
DATEI = os.environ.get("ANTRAEGE_DATEI", "/data/antraege.json")
PORT = int(os.environ.get("PORT", "8080"))
MAX_BODY = 64 * 1024
MAX_ANTRAEGE = 5000
AUFBEWAHREN = 90 * 24 * 3600
ID = re.compile(r"^[A-Za-z0-9_-]{6,64}$")

lock = threading.Lock()
antraege = {}


def laden():
    global antraege
    try:
        with open(DATEI, encoding="utf-8") as f:
            antraege = json.load(f)
    except FileNotFoundError:
        antraege = {}


def speichern():
    jetzt = time.time()
    for k in [k for k, a in antraege.items() if jetzt - a.get("zeit", jetzt) > AUFBEWAHREN]:
        del antraege[k]
    tmp = DATEI + ".tmp"
    os.makedirs(os.path.dirname(DATEI) or ".", exist_ok=True)
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(antraege, f, ensure_ascii=False)
    os.replace(tmp, DATEI)


def text(v, n=500):
    return str(v if v is not None else "")[:n]


def oeffentlich(a):
    """Was die Seite sieht (ohne Token)."""
    return {k: a.get(k) for k in ("art", "titel", "nation", "von", "ort", "details", "zeit", "status", "entscheidung", "admin", "grund", "entschieden")}


class Handler(BaseHTTPRequestHandler):
    server_version = "antraege"

    def log_message(self, fmt, *args):
        # Pfade enthalten keine Tokens (die stehen im Body), Logzeile also unkritisch
        print("%s %s" % (self.command, self.path.split("?")[0]), flush=True)

    def antwort(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def body(self):
        n = int(self.headers.get("Content-Length") or 0)
        if n < 0 or n > MAX_BODY:
            raise ValueError("Laenge ungueltig")
        raw = self.rfile.read(n) if n else b"{}"
        b = json.loads(raw.decode("utf-8") or "{}")
        if not isinstance(b, dict):
            raise ValueError("kein Objekt")
        return b

    def server_ok(self):
        k = self.headers.get("X-Crowns-Schluessel", "")
        return bool(SCHLUESSEL) and hmac.compare_digest(k.encode(), SCHLUESSEL.encode())

    def do_GET(self):
        pfad = self.path.split("?")[0]
        if pfad == "/api/entscheidungen":
            if not self.server_ok():
                return self.antwort(403, {"fehler": "Schluessel falsch"})
            with lock:
                out = [{"id": k, "entscheidung": a["entscheidung"], "admin": a.get("admin", ""), "grund": a.get("grund", "")}
                       for k, a in antraege.items() if a.get("status") == "entschieden"]
            return self.antwort(200, out)
        if pfad == "/api/status":
            return self.antwort(200, {"ok": True, "schluessel": bool(SCHLUESSEL)})
        return self.antwort(404, {"fehler": "unbekannt"})

    def do_POST(self):
        pfad = self.path.split("?")[0]
        try:
            b = self.body()
        except Exception:
            return self.antwort(400, {"fehler": "Anfrage nicht lesbar"})

        if pfad == "/api/neu":
            if not self.server_ok():
                return self.antwort(403, {"fehler": "Schluessel falsch"})
            aid, tok = text(b.get("id"), 64), text(b.get("token"), 128)
            if not ID.match(aid) or len(tok) < 24:
                return self.antwort(400, {"fehler": "id oder token ungueltig"})
            with lock:
                if len(antraege) >= MAX_ANTRAEGE and aid not in antraege:
                    return self.antwort(507, {"fehler": "zu viele Antraege"})
                antraege[aid] = {
                    "token": tok, "art": text(b.get("art"), 32), "titel": text(b.get("titel"), 200),
                    "nation": text(b.get("nation"), 100), "von": text(b.get("von"), 64), "ort": text(b.get("ort"), 200),
                    "details": [text(x, 300) for x in (b.get("details") or [])][:20],
                    "zeit": time.time(), "status": "offen",
                }
                speichern()
            return self.antwort(200, {"ok": True})

        if pfad == "/api/quittung":
            if not self.server_ok():
                return self.antwort(403, {"fehler": "Schluessel falsch"})
            with lock:
                for aid in b.get("ids") or []:
                    a = antraege.get(str(aid))
                    if a and a.get("status") == "entschieden":
                        a["status"] = "erledigt"
                speichern()
            return self.antwort(200, {"ok": True})

        m = re.match(r"^/api/antrag/([A-Za-z0-9_-]{6,64})(/entscheiden|/erledigt)?$", pfad)
        if not m:
            return self.antwort(404, {"fehler": "unbekannt"})
        aid, aktion = m.group(1), m.group(2) or ""

        if aktion == "/erledigt":
            # Im Spiel entschieden: Seite zeigt das dann an
            if not self.server_ok():
                return self.antwort(403, {"fehler": "Schluessel falsch"})
            with lock:
                a = antraege.get(aid)
                if a:
                    a.update(status="erledigt", entscheidung=text(b.get("entscheidung"), 16), admin=text(b.get("admin"), 64),
                             grund=text(b.get("grund"), 300), entschieden=time.time())
                    speichern()
            return self.antwort(200, {"ok": True})

        with lock:
            a = antraege.get(aid)
            if not a or not hmac.compare_digest(text(b.get("t"), 128).encode(), a["token"].encode()):
                return self.antwort(404, {"fehler": "Antrag nicht gefunden oder Link ungueltig"})
            if aktion == "":
                return self.antwort(200, oeffentlich(a))
            if a.get("status") != "offen":
                return self.antwort(409, {"fehler": "Schon entschieden", **oeffentlich(a)})
            ent = text(b.get("entscheidung"), 16)
            admin = text(b.get("admin"), 64).strip()
            if ent not in ("annehmen", "ablehnen") or not re.match(r"^[A-Za-z0-9_]{2,16}$", admin):
                return self.antwort(400, {"fehler": "Entscheidung oder Minecraft-Name fehlt"})
            a.update(status="entschieden", entscheidung=ent, admin=admin, grund=text(b.get("grund"), 300), entschieden=time.time())
            speichern()
            return self.antwort(200, oeffentlich(a))


if __name__ == "__main__":
    if not SCHLUESSEL:
        print("WARNUNG: ANTRAEGE_SCHLUESSEL fehlt, der Minecraft-Server kann nichts melden", flush=True)
    laden()
    print("Antraege auf Port %d, %d gespeichert" % (PORT, len(antraege)), flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
