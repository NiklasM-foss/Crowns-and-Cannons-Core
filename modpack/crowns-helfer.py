#!/usr/bin/env python3
"""Crowns & Cannons Helfer fuer Linux.

Haelt das ganze Modpack automatisch aktuell: Mods, Ressourcenpakete, Datenpakete, Shader, KubeJS und das
Java-Argument. Quellen: Modrinth, CurseForge oder der Pi. Dateien, die niemand automatisch laden darf, holt der
Helfer aus dem Downloads-Ordner, sobald der Spieler sie dort hat. Eigene Dateien werden nie angefasst.
Gleiche Logik wie Crowns-Helfer.bat unter Windows.

  python3 crowns-helfer.py            einrichten (systemd-Timer bzw. cron) und einmal ausfuehren
  python3 crowns-helfer.py auto       nur abgleichen (so ruft der Timer ihn auf)
  python3 crowns-helfer.py entfernen  Timer und Dateien wieder loeschen

Unterstuetzt CurseForge-Instanzen (minecraftinstance.json) und Prism-Launcher-Instanzen (instance.cfg).
Braucht nur Python 3, keine Zusatzpakete.
"""
import fnmatch
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

QUELLE = os.environ.get("CROWNS_HELFER_QUELLE", "https://editor.niklasmetzger.de/pack/crowns.json")
HOME = Path.home()
ORDNER = Path(os.environ.get("XDG_DATA_HOME", HOME / ".local/share")) / "crowns-helfer"
LOG = ORDNER / "helfer.log"
UA = {"User-Agent": "crowns-helfer/2.0"}
ERLAUBT = re.compile(r"^(mods|resourcepacks|datapacks|shaderpacks|kubejs)/")
DOWNLOADS = Path(os.environ.get("CROWNS_HELFER_DOWNLOADS", "") or (HOME / "Downloads"))
AUTO = len(sys.argv) > 1 and sys.argv[1] == "auto"
TEST_WURZEL = os.environ.get("CROWNS_HELFER_WURZEL", "")


def schreib(text):
    ORDNER.mkdir(parents=True, exist_ok=True)
    if not AUTO:
        print(text)
    with open(LOG, "a", encoding="utf-8") as f:
        f.write(time.strftime("%Y-%m-%d %H:%M:%S") + "  " + text + "\n")


def einrichten():
    ORDNER.mkdir(parents=True, exist_ok=True)
    ziel = ORDNER / "crowns-helfer.py"
    if Path(__file__).resolve() != ziel:
        shutil.copy2(__file__, ziel)
    py = sys.executable or "python3"
    systemd = HOME / ".config/systemd/user"
    if shutil.which("systemctl"):
        systemd.mkdir(parents=True, exist_ok=True)
        (systemd / "crowns-helfer.service").write_text(
            "[Unit]\nDescription=Crowns & Cannons Helfer\n\n[Service]\nType=oneshot\n"
            f"ExecStart={py} {ziel} auto\n", encoding="utf-8")
        (systemd / "crowns-helfer.timer").write_text(
            "[Unit]\nDescription=Crowns & Cannons Helfer alle 15 Minuten\n\n[Timer]\nOnBootSec=1min\nOnUnitActiveSec=15min\n"
            "Persistent=true\n\n[Install]\nWantedBy=timers.target\n", encoding="utf-8")
        r = subprocess.run(["systemctl", "--user", "daemon-reload"], capture_output=True, text=True)
        r = subprocess.run(["systemctl", "--user", "enable", "--now", "crowns-helfer.timer"], capture_output=True, text=True)
        if r.returncode == 0:
            print("Eingerichtet: systemd-Timer, laeuft nach dem Start und alle 15 Minuten.")
            return
        print("systemd-Timer ging nicht (" + r.stderr.strip() + "), versuche cron.")
    if shutil.which("crontab"):
        alt = subprocess.run(["crontab", "-l"], capture_output=True, text=True).stdout
        zeilen = [z for z in alt.splitlines() if "crowns-helfer.py" not in z]
        zeilen.append(f"*/15 * * * * {py} {ziel} auto >/dev/null 2>&1")
        zeilen.append(f"@reboot {py} {ziel} auto >/dev/null 2>&1")
        subprocess.run(["crontab", "-"], input="\n".join(zeilen) + "\n", text=True)
        print("Eingerichtet: cron, alle 15 Minuten und nach dem Start.")
    else:
        print("Weder systemd noch cron gefunden. Bitte crowns-helfer.py auto vor dem Spielen selbst starten.")


def entfernen():
    if shutil.which("systemctl"):
        subprocess.run(["systemctl", "--user", "disable", "--now", "crowns-helfer.timer"], capture_output=True)
        for n in ("crowns-helfer.service", "crowns-helfer.timer"):
            (HOME / ".config/systemd/user" / n).unlink(missing_ok=True)
        subprocess.run(["systemctl", "--user", "daemon-reload"], capture_output=True)
    if shutil.which("crontab"):
        alt = subprocess.run(["crontab", "-l"], capture_output=True, text=True).stdout
        subprocess.run(["crontab", "-"], input="\n".join(z for z in alt.splitlines() if "crowns-helfer.py" not in z) + "\n", text=True)
    shutil.rmtree(ORDNER, ignore_errors=True)
    print("Crowns-Helfer ist entfernt. Die Dateien in deinen Instanzen bleiben, wie sie sind.")


def liste():
    cache = ORDNER / "crowns.json"
    try:
        if re.match(r"^https?://", QUELLE):
            with urllib.request.urlopen(urllib.request.Request(QUELLE, headers=UA), timeout=20) as r:
                roh = r.read().decode("utf-8")
        else:
            roh = Path(QUELLE).read_text(encoding="utf-8")
        pack = json.loads(roh)
        ORDNER.mkdir(parents=True, exist_ok=True)
        cache.write_text(roh, encoding="utf-8")
        return pack
    except Exception as e:
        if cache.exists():
            schreib(f"Liste nicht erreichbar, nehme die zuletzt geladene ({e})")
            return json.loads(cache.read_text(encoding="utf-8"))
        schreib(f"Liste nicht erreichbar und noch keine gespeichert: {e}")
        return None


def wurzeln():
    if TEST_WURZEL:
        return [Path(TEST_WURZEL)]
    w = []
    storage = HOME / ".config/CurseForge/storage.json"
    try:
        s = json.loads(storage.read_text(encoding="utf-8"))
        mc = json.loads(s.get("minecraft-settings", "{}"))
        if mc.get("minecraftRoot"):
            w += [Path(mc["minecraftRoot"]) / "Instances", Path(mc["minecraftRoot"]) / "minecraft/Instances"]
    except Exception:
        pass
    w += [HOME / "curseforge/minecraft/Instances", HOME / "Documents/curseforge/minecraft/Instances",
          HOME / ".local/share/PrismLauncher/instances", HOME / ".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances",
          HOME / ".local/share/multimc/instances"]
    return list(dict.fromkeys(w))


def instanzen(pack):
    gefunden = []
    for r in wurzeln():
        if not r.is_dir():
            continue
        for d in sorted(p for p in r.iterdir() if p.is_dir()):
            cf, prism = d / "minecraftinstance.json", d / "instance.cfg"
            name = d.name
            if cf.exists():
                try:
                    name = json.loads(cf.read_text(encoding="utf-8-sig")).get("name") or name
                except Exception:
                    pass
                art, mods = "cf", d / "mods"
            elif prism.exists():
                m = re.search(r"^name=(.*)$", prism.read_text(encoding="utf-8", errors="replace"), re.M)
                name = m.group(1).strip() if m else name
                mc = d / ".minecraft" if (d / ".minecraft").exists() else d / "minecraft"
                art, mods = "prism", mc / "mods"
            else:
                continue
            if any(name.startswith(p) or d.name.startswith(p) for p in pack.get("instanzen", [])):
                gefunden.append((art, d, name, mods))
    return gefunden


def sha512(p):
    h = hashlib.sha512()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def pfad_ok(p):
    return bool(ERLAUBT.match(p)) and ".." not in p.split("/") and ":" not in p and "\\" not in p and len(p) < 300


def aus_downloads(d):
    if not DOWNLOADS.is_dir():
        return None
    for f in DOWNLOADS.iterdir():
        try:
            if f.is_file() and (not d.get("groesse") or f.stat().st_size == int(d["groesse"])) and sha512(f) == d["sha512"].lower():
                return f
        except OSError:
            continue
    return None


def dateien_abgleichen(pack, ordner, basis, manuell):
    neu = weg = 0
    for d in pack.get("dateien", []):
        if not pfad_ok(d.get("pfad", "")):
            continue
        ziel = ordner / d["pfad"]
        ok = ziel.exists() and sha512(ziel) == d["sha512"].lower()
        if not ok:
            ziel.parent.mkdir(parents=True, exist_ok=True)
            tmp = ORDNER / "download.tmp"
            for q in d.get("quellen", []):
                url = basis + q if q.startswith("/") else q
                try:
                    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=300) as r, open(tmp, "wb") as f:
                        shutil.copyfileobj(r, f)
                    if sha512(tmp) != d["sha512"].lower():
                        raise ValueError("Pruefsumme stimmt nicht")
                    shutil.move(str(tmp), str(ziel))
                    ok = True
                    neu += 1
                    schreib("  neu: " + d["pfad"])
                    break
                except Exception as e:
                    tmp.unlink(missing_ok=True)
                    schreib(f"  {d['pfad']}: Quelle fehlgeschlagen ({e})")
            if not ok:
                dl = aus_downloads(d)
                if dl:
                    shutil.move(str(dl), str(ziel))
                    ok = True
                    neu += 1
                    schreib("  aus Downloads uebernommen: " + d["pfad"])
                else:
                    manuell[d["sha512"]] = d
        if ok:
            for a in d.get("alt", []):
                if pfad_ok(a) and a != d["pfad"] and (ordner / a).exists():
                    try:
                        (ordner / a).unlink()
                        weg += 1
                        schreib("  alte Version entfernt: " + a)
                    except OSError:
                        schreib(f"  {a} ist gerade gesperrt, naechster Versuch spaeter")
    schreib(f"  {neu} neu, {weg} entfernt")


def hinweis_manuell(manuell):
    """Dateien, die man selbst herunterladen muss: Hinweis mit Links (hoechstens alle 6 Stunden im Hintergrund)."""
    if not manuell:
        return
    stand = ORDNER / "hinweis.json"
    try:
        zuletzt = json.loads(stand.read_text()).get("zeit", 0)
    except Exception:
        zuletzt = 0
    if AUTO and time.time() - zuletzt < 6 * 3600:
        return
    stand.write_text(json.dumps({"zeit": time.time()}))
    zeilen = [f"{d.get('name') or d['pfad']}: {d.get('seite') or 'keine Seite angegeben'}" for d in manuell.values()]
    text = ("Diese Dateien darf der Helfer nicht selbst laden. Bitte herunterladen (in " + str(DOWNLOADS)
            + "), der Helfer legt sie beim naechsten Lauf an die richtige Stelle:\n" + "\n".join(zeilen))
    schreib(text)
    if shutil.which("notify-send"):
        subprocess.run(["notify-send", "-a", "Crowns-Helfer", "Crowns & Cannons: bitte herunterladen", text], capture_output=True)
    if not AUTO and shutil.which("xdg-open"):
        for d in manuell.values():
            if d.get("seite"):
                subprocess.run(["xdg-open", d["seite"]], capture_output=True)


def cf_laeuft():
    if os.environ.get("CROWNS_HELFER_TEST"):
        return False
    r = subprocess.run(["pgrep", "-i", "-f", "curseforge"], capture_output=True, text=True)
    return r.returncode == 0 and any(pid != str(os.getpid()) for pid in r.stdout.split())


def jvm_cf(pack, datei):
    raw = datei.read_bytes()
    bom = raw.startswith(b"\xef\xbb\xbf")
    text = raw.decode("utf-8-sig")
    alt = json.loads(text).get("javaArgsOverride") or ""
    fehlt = [a for a in pack.get("jvm", []) if a not in alt.split()]
    if not fehlt:
        schreib("  Java-Argument: gesetzt")
        return
    if cf_laeuft():
        schreib("  Java-Argument fehlt, CurseForge ist offen: wird gesetzt, sobald die App geschlossen ist")
        return
    neu = (alt.strip() + " " + " ".join(fehlt)).strip()
    wert = '"javaArgsOverride": ' + json.dumps(neu)
    rx = re.compile(r'"javaArgsOverride"\s*:\s*(null|"(?:[^"\\]|\\.)*")')
    text2 = rx.sub(lambda _: wert, text, count=1) if rx.search(text) else re.sub(r"^\s*\{", "{\n  " + wert + ",", text, count=1)
    json.loads(text2)  # nichts Kaputtes schreiben
    shutil.copy2(datei, str(datei) + ".bak")
    datei.write_bytes((b"\xef\xbb\xbf" if bom else b"") + text2.encode("utf-8"))
    schreib("  Java-Argument gesetzt: " + neu)


def jvm_prism(pack, cfg):
    text = cfg.read_text(encoding="utf-8", errors="replace")
    m = re.search(r"^JvmArgs=(.*)$", text, re.M)
    alt = m.group(1).strip() if m else ""
    fehlt = [a for a in pack.get("jvm", []) if a not in alt.split()]
    if not fehlt and re.search(r"^OverrideJavaArgs=true$", text, re.M):
        schreib("  Java-Argument: gesetzt")
        return
    neu = (alt + " " + " ".join(fehlt)).strip()
    text = re.sub(r"^JvmArgs=.*$", "JvmArgs=" + neu.replace("\\", "\\\\"), text, flags=re.M) if m else text.rstrip("\n") + "\nJvmArgs=" + neu + "\n"
    if re.search(r"^OverrideJavaArgs=", text, re.M):
        text = re.sub(r"^OverrideJavaArgs=.*$", "OverrideJavaArgs=true", text, flags=re.M)
    else:
        text = text.rstrip("\n") + "\nOverrideJavaArgs=true\n"
    shutil.copy2(cfg, str(cfg) + ".bak")
    cfg.write_text(text, encoding="utf-8")
    schreib("  Java-Argument gesetzt (Prism): " + neu)


def abgleichen():
    pack = liste()
    if not pack:
        return
    gefunden = instanzen(pack)
    if not gefunden:
        schreib("Keine Crowns-&-Cannons-Instanz gefunden.")
        return
    m = re.match(r"^(https?://[^/]+)", QUELLE)
    basis = m.group(1) if m else ""
    manuell = {}
    for art, d, name, mods in gefunden:
        schreib(f"Instanz: {name} ({'CurseForge' if art == 'cf' else 'Prism'}, Pack-Version {pack.get('version')})")
        dateien_abgleichen(pack, mods.parent, basis, manuell)
        if not pack.get("jvm"):
            continue
        try:
            if art == "cf":
                jvm_cf(pack, d / "minecraftinstance.json")
            else:
                jvm_prism(pack, d / "instance.cfg")
        except Exception as e:
            schreib(f"  Java-Argument nicht gesetzt: {e}")
    if not os.environ.get("CROWNS_HELFER_TEST"):
        hinweis_manuell(manuell)
    try:
        z = LOG.read_text(encoding="utf-8").splitlines()
        if len(z) > 800:
            LOG.write_text("\n".join(z[-400:]) + "\n", encoding="utf-8")
    except Exception:
        pass


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "entfernen":
        entfernen()
    else:
        if not AUTO:
            print("\nCrowns & Cannons Helfer\nHaelt das Modpack automatisch aktuell (Mods, Ressourcenpakete, Datenpakete, Shader, KubeJS, Java-Argument).\n"
                  "Eigene Dateien werden nie angefasst.\n")
            if not TEST_WURZEL:
                einrichten()
            print()
        abgleichen()
        if not AUTO:
            print("\nFertig. Protokoll:", LOG)
