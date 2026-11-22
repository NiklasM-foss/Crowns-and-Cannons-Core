"""Modpack-Verwaltung fuer den Crowns-Helfer (Teil des Antrags-Dienstes, nur Python-Stdlib).

Die Admin-Seite (/pack/admin/) schickt eine Liste der Dateien einer Instanz mit Pruefsummen. Der Dienst sucht zu jeder
Datei eine Quelle: erst Modrinth (exakt dieselbe Datei per SHA-1), dann CurseForge (wenn der Autor Downloads durch
Programme erlaubt), dann eine auf den Pi hochgeladene Kopie. Nur wer die Weitergabe erlauben darf (offene Lizenz oder
Erlaubnis des Autors), laedt eine Datei auf den Pi; die Angabe dazu wird gespeichert. "Veroeffentlichen" schreibt die
Liste, die der Helfer bei den Spielern abholt.

Umgebung: PACK_PASSWORT (Admin-Seite), CF_API_KEY (CurseForge-Schnittstelle, optional), PACK_ORDNER (Standard /data/pack).
"""
import hashlib
import hmac
import json
import os
import re
import threading
import time
import urllib.request

PASSWORT = os.environ.get("PACK_PASSWORT", "")
CF_KEY = os.environ.get("CF_API_KEY", "")
ORDNER = os.environ.get("PACK_ORDNER", "/data/pack")
DATEIEN = os.path.join(ORDNER, "dateien")
LISTE = os.path.join(ORDNER, "crowns.json")
UPLOADS = os.path.join(ORDNER, "uploads.json")
VERLAUF = os.path.join(ORDNER, "verlauf")
MAX_JSON = 4 * 1024 * 1024
MAX_UPLOAD = 200 * 1024 * 1024
UA = {"User-Agent": "crowns-pack/1.0", "Content-Type": "application/json", "Accept": "application/json"}
KATEGORIEN = {"mod": "mods", "rp": "resourcepacks", "dp": "datapacks", "shader": "shaderpacks", "kubejs": "kubejs"}
HEX = re.compile(r"^[0-9a-f]{40}$|^[0-9a-f]{128}$")
SHA512 = re.compile(r"^[0-9a-f]{128}$")
ERLAUBNIS = {"lizenz", "autor", "eigen"}
lock = threading.Lock()


def _laden(p, leer):
    try:
        with open(p, encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, ValueError):
        return leer


def _speichern(p, obj):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    tmp = p + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
    os.replace(tmp, p)


def _post(url, daten, headers):
    req = urllib.request.Request(url, data=json.dumps(daten).encode("utf-8"), headers=headers)
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def _pfad_ok(pfad, kategorie):
    """Nur relative Pfade im Ordner der Kategorie, keine Ausbrueche."""
    if kategorie not in KATEGORIEN or not isinstance(pfad, str) or len(pfad) > 300:
        return False
    teile = pfad.replace("\\", "/").split("/")
    if teile[0] != KATEGORIEN[kategorie] or any(t in ("", ".", "..") for t in teile) or ":" in pfad:
        return False
    return len(teile) >= 2


def _passwort_ok(handler):
    k = handler.headers.get("X-Pack-Passwort", "")
    return bool(PASSWORT) and hmac.compare_digest(k.encode(), PASSWORT.encode())


def _json(handler):
    n = int(handler.headers.get("Content-Length") or 0)
    if n < 0 or n > MAX_JSON:
        raise ValueError("Laenge ungueltig")
    b = json.loads(handler.rfile.read(n).decode("utf-8") or "{}")
    if not isinstance(b, dict):
        raise ValueError("kein Objekt")
    return b


def pruefen(eintraege):
    """Quelle je Datei: modrinth, curseforge, pi oder fehlt."""
    uploads = _laden(UPLOADS, {})
    sha1s = [e["sha1"] for e in eintraege if HEX.match(e.get("sha1", ""))]
    mr = {}
    for i in range(0, len(sha1s), 500):
        try:
            mr.update(_post("https://api.modrinth.com/v2/version_files", {"hashes": sha1s[i:i + 500], "algorithm": "sha1"}, UA))
        except Exception as ex:
            print("Modrinth nicht erreichbar:", ex, flush=True)
    cf = {}
    ids = [int(e["cfDatei"]) for e in eintraege if str(e.get("cfDatei", "")).isdigit()]
    if CF_KEY and ids:
        try:
            for f in _post("https://api.curseforge.com/v1/mods/files", {"fileIds": ids}, dict(UA, **{"x-api-key": CF_KEY})).get("data", []):
                cf[f["id"]] = f
        except Exception as ex:
            print("CurseForge nicht erreichbar:", ex, flush=True)
    out = []
    for e in eintraege:
        r = {k: e.get(k) for k in ("pfad", "kategorie", "sha1", "sha512", "groesse", "cfProjekt", "cfDatei", "name")}
        quellen, seite = [], ""
        v = mr.get(e.get("sha1", ""))
        if v:
            for f in v.get("files", []):
                if f.get("hashes", {}).get("sha1") == e.get("sha1"):
                    quellen.append(f["url"])
            r["modrinth"] = v.get("project_id")
        f = cf.get(int(e["cfDatei"])) if str(e.get("cfDatei", "")).isdigit() else None
        if f:
            seite = "https://www.curseforge.com/projects/%s" % f.get("modId")
            cf_sha1 = next((h["value"] for h in f.get("hashes", []) if h.get("algo") == 1), "")
            if f.get("downloadUrl") and (not cf_sha1 or cf_sha1 == e.get("sha1")):
                quellen.append(f["downloadUrl"])
        if e.get("sha512") in uploads:
            quellen.append("pi:" + e["sha512"])
        r["quellen"] = quellen
        r["seite"] = seite
        r["quelle"] = ("modrinth" if quellen and "modrinth" in quellen[0] else "pi" if quellen and quellen[0].startswith("pi:")
                       else "curseforge" if quellen else "fehlt")
        r["erlaubnis"] = uploads.get(e.get("sha512"), {}).get("notiz", "")
        out.append(r)
    return out


def hochladen(handler, sha512, name, erlaubnis, notiz):
    n = int(handler.headers.get("Content-Length") or 0)
    if not SHA512.match(sha512) or n <= 0 or n > MAX_UPLOAD:
        return 400, {"fehler": "Pruefsumme oder Groesse ungueltig"}
    if erlaubnis not in ERLAUBNIS or (erlaubnis == "autor" and len(notiz.strip()) < 5):
        return 400, {"fehler": "Bitte angeben, warum die Weitergabe erlaubt ist (bei Erlaubnis des Autors mit Notiz)"}
    os.makedirs(DATEIEN, exist_ok=True)
    tmp = os.path.join(DATEIEN, sha512 + ".tmp")
    h = hashlib.sha512()
    rest = n
    with open(tmp, "wb") as f:
        while rest > 0:
            b = handler.rfile.read(min(rest, 1 << 20))
            if not b:
                break
            h.update(b)
            f.write(b)
            rest -= len(b)
    if rest != 0 or h.hexdigest() != sha512:
        os.remove(tmp)
        return 400, {"fehler": "Datei unvollstaendig oder Pruefsumme stimmt nicht"}
    os.replace(tmp, os.path.join(DATEIEN, sha512))
    with lock:
        u = _laden(UPLOADS, {})
        u[sha512] = {"name": name[:200], "erlaubnis": erlaubnis, "notiz": notiz[:500], "zeit": time.time()}
        _speichern(UPLOADS, u)
    return 200, {"ok": True}


def veroeffentlichen(b):
    eintraege = b.get("eintraege") or []
    if not isinstance(eintraege, list) or len(eintraege) > 5000:
        return 400, {"fehler": "Liste ungueltig"}
    alt = _laden(LISTE, {})
    frueher = {}  # Schluessel (Projekt oder Pfad) -> fruehere Pfade, damit der Helfer alte Versionen entfernt
    for d in alt.get("dateien", []):
        frueher.setdefault(d.get("schluessel"), set()).update([d.get("pfad")] + d.get("alt", []))
    dateien, fehler = [], []
    for e in eintraege:
        kat, pfad, sha512 = e.get("kategorie"), str(e.get("pfad", "")).replace("\\", "/"), e.get("sha512", "")
        if not _pfad_ok(pfad, kat) or not SHA512.match(sha512):
            fehler.append(pfad)
            continue
        quellen = [q for q in (e.get("quellen") or []) if isinstance(q, str) and (q.startswith("https://") or re.match(r"^pi:[0-9a-f]{128}$", q))]
        schluessel = ("mr:" + e["modrinth"]) if e.get("modrinth") else ("cf:%s" % e["cfProjekt"]) if e.get("cfProjekt") else ("pfad:" + pfad)
        vorher = sorted(p for p in frueher.get(schluessel, set()) if p and p != pfad and _pfad_ok(p, kat))
        dateien.append({"kategorie": kat, "pfad": pfad, "sha512": sha512, "groesse": e.get("groesse"), "schluessel": schluessel,
                        "quellen": [("/pack/datei/" + q[3:]) if q.startswith("pi:") else q for q in quellen],
                        "seite": e.get("seite", ""), "name": str(e.get("name", ""))[:200], "alt": vorher})
    if fehler:
        return 400, {"fehler": "Ungueltige Pfade: " + ", ".join(fehler[:10])}
    jvm = [a for a in (b.get("jvm") or []) if isinstance(a, str) and re.match(r"^-[A-Za-z0-9_.:=/+\-]+$", a)]
    version = int(alt.get("version", 0)) + 1
    liste = {"name": "Crowns & Cannons", "version": version, "stand": time.strftime("%Y-%m-%d %H:%M"),
             "instanzen": b.get("instanzen") or alt.get("instanzen") or ["Crowns & Cannons"], "jvm": jvm, "dateien": dateien}
    with lock:
        _speichern(LISTE, liste)
        _speichern(os.path.join(VERLAUF, "crowns-%d.json" % version), liste)
    return 200, {"ok": True, "version": version, "dateien": len(dateien)}


def handle(handler, methode):
    """True, wenn die Anfrage zum Pack gehoerte (dann ist die Antwort schon geschickt)."""
    pfad = handler.path.split("?")[0]
    # Oeffentliche Pfade wie hinter nginx auch direkt beantworten
    if pfad == "/pack/crowns.json":
        pfad = "/api/pack/liste"
    elif pfad.startswith("/pack/datei/"):
        pfad = "/api/pack/datei/" + pfad[len("/pack/datei/"):]
    if not pfad.startswith("/api/pack/"):
        return False
    query = dict(p.split("=", 1) for p in handler.path.split("?", 1)[1].split("&") if "=" in p) if "?" in handler.path else {}
    from urllib.parse import unquote_plus
    query = {k: unquote_plus(v) for k, v in query.items()}
    try:
        if methode == "GET" and pfad == "/api/pack/liste":
            liste = _laden(LISTE, {"version": 0, "dateien": [], "jvm": [], "instanzen": ["Crowns & Cannons"]})
            return handler.antwort(200, liste) or True
        m = re.match(r"^/api/pack/datei/([0-9a-f]{128})$", pfad)
        if methode == "GET" and m:
            p = os.path.join(DATEIEN, m.group(1))
            if not os.path.exists(p):
                return handler.antwort(404, {"fehler": "nicht da"}) or True
            groesse = os.path.getsize(p)
            handler.send_response(200)
            handler.send_header("Content-Type", "application/octet-stream")
            handler.send_header("Content-Length", str(groesse))
            handler.end_headers()
            with open(p, "rb") as f:
                while True:
                    b = f.read(1 << 20)
                    if not b:
                        break
                    handler.wfile.write(b)
            return True
        if not _passwort_ok(handler):
            return handler.antwort(403, {"fehler": "Passwort falsch"}) or True
        if methode == "POST" and pfad == "/api/pack/anmelden":
            return handler.antwort(200, {"ok": True, "curseforge": bool(CF_KEY)}) or True
        if methode == "POST" and pfad == "/api/pack/pruefen":
            b = _json(handler)
            e = b.get("eintraege")
            if not isinstance(e, list) or len(e) > 5000:
                return handler.antwort(400, {"fehler": "Liste ungueltig"}) or True
            return handler.antwort(200, {"eintraege": pruefen([x for x in e if isinstance(x, dict)])}) or True
        if methode == "POST" and pfad == "/api/pack/hochladen":
            code, obj = hochladen(handler, query.get("sha512", ""), query.get("name", ""), query.get("erlaubnis", ""), query.get("notiz", ""))
            return handler.antwort(code, obj) or True
        if methode == "POST" and pfad == "/api/pack/veroeffentlichen":
            code, obj = veroeffentlichen(_json(handler))
            return handler.antwort(code, obj) or True
        return handler.antwort(404, {"fehler": "unbekannt"}) or True
    except Exception as ex:
        print("Pack-Fehler:", repr(ex), flush=True)
        return handler.antwort(400, {"fehler": "Anfrage nicht verarbeitbar"}) or True
