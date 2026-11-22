#!/usr/bin/env python3
"""Crowns & Cannons Helfer fuer Linux.

Haelt Zusatz-Mods (die es nicht auf CurseForge gibt) und das Java-Argument fuer das Modpack automatisch aktuell.
Eigene Mods im mods-Ordner werden nie angefasst. Gleiche Logik wie Crowns-Helfer.bat unter Windows.

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
UA = {"User-Agent": "crowns-helfer/1.0"}
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
    print("Crowns-Helfer ist entfernt. Mods in deinen Instanzen bleiben, wie sie sind.")


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


def mods_abgleichen(pack, mods):
    mods.mkdir(parents=True, exist_ok=True)
    for m in pack.get("mods", []):
        ziel = mods / m["datei"]
        ok = ziel.exists() and sha512(ziel).lower() == m["sha512"].lower()
        if not ok:
            tmp = ORDNER / (m["datei"] + ".download")
            try:
                with urllib.request.urlopen(urllib.request.Request(m["url"], headers=UA), timeout=120) as r, open(tmp, "wb") as f:
                    shutil.copyfileobj(r, f)
                if sha512(tmp).lower() != m["sha512"].lower():
                    raise ValueError("Pruefsumme stimmt nicht")
                shutil.move(str(tmp), str(ziel))
                ok = True
                schreib(f"  {m['name']}: {m['datei']} installiert")
            except Exception as e:
                tmp.unlink(missing_ok=True)
                schreib(f"  {m['name']}: Download fehlgeschlagen ({e}), naechster Versuch spaeter")
        else:
            schreib(f"  {m['name']}: aktuell")
        if ok and m.get("ersetzt"):
            for alt in mods.iterdir():
                if alt.is_file() and fnmatch.fnmatch(alt.name, m["ersetzt"]) and alt.name != m["datei"]:
                    alt.unlink()
                    schreib(f"  alte Version entfernt: {alt.name}")


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
    for art, d, name, mods in gefunden:
        schreib(f"Instanz: {name} ({'CurseForge' if art == 'cf' else 'Prism'})")
        mods_abgleichen(pack, mods)
        try:
            if art == "cf":
                jvm_cf(pack, d / "minecraftinstance.json")
            else:
                jvm_prism(pack, d / "instance.cfg")
        except Exception as e:
            schreib(f"  Java-Argument nicht gesetzt: {e}")
    try:
        z = LOG.read_text(encoding="utf-8").splitlines()
        if len(z) > 500:
            LOG.write_text("\n".join(z[-300:]) + "\n", encoding="utf-8")
    except Exception:
        pass


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "entfernen":
        entfernen()
    else:
        if not AUTO:
            print("\nCrowns & Cannons Helfer\nHaelt Zusatz-Mods und das Java-Argument fuer das Modpack automatisch aktuell.\n"
                  "Eigene Mods in deinem mods-Ordner werden nie angefasst.\n")
            if not TEST_WURZEL:
                einrichten()
            print()
        abgleichen()
        if not AUTO:
            print("\nFertig. Protokoll:", LOG)
