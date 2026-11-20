"""Laedt die Landesflaggen fuer alle Laender der Weltkarte.

Quelle der Bilder: flagcdn.com (Flaggen von Staaten sind gemeinfrei).
Zuordnung der Kuerzel: Natural Earth (ADM0_A3 -> ISO 3166-1 alpha-2).
Ausgabe: src/main/resources/crownscore/flaggen/<CODE>.png, 320 Pixel breit.

Aufruf: python tools/flaggen/make_flaggen.py
"""
import io
import json
import os
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TSV = os.path.join(ROOT, "src", "main", "resources", "crownscore", "weltkarte", "laender.tsv")
OUT = os.path.join(ROOT, "src", "main", "resources", "crownscore", "flaggen")
GEOJSON = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_countries.geojson"
UA = {"User-Agent": "crownscore-flaggen/1.0"}

# Gebiete ohne eigenen ISO-Code in Natural Earth
EXTRA = {"KOS": "xk", "FRA": "fr", "NOR": "no"}


def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return r.read()


def main():
    cache = sys.argv[1] if len(sys.argv) > 1 else None
    data = open(cache, "rb").read() if cache else fetch(GEOJSON)
    iso2 = {}
    for f in json.loads(data)["features"]:
        p = f["properties"]
        code = p.get("ADM0_A3") or p.get("ISO_A3")
        for key in ("ISO_A2_EH", "ISO_A2", "WB_A2"):
            v = (p.get(key) or "").strip()
            if len(v) == 2 and v.isalpha():
                iso2.setdefault(code, v.lower())
                break
    iso2.update(EXTRA)
    os.makedirs(OUT, exist_ok=True)
    fehlt = []
    n = 0
    for line in io.open(TSV, encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        parts = line.rstrip("\n").split("\t")
        code, name = parts[1], parts[2]
        a2 = iso2.get(code)
        ziel = os.path.join(OUT, code + ".png")
        if not a2:
            fehlt.append(f"{code} {name} (kein ISO-Code)")
            continue
        if os.path.exists(ziel):
            n += 1
            continue
        try:
            png = fetch(f"https://flagcdn.com/w320/{a2}.png")
        except Exception as e:  # noqa: BLE001
            fehlt.append(f"{code} {name} ({a2}: {e})")
            continue
        with open(ziel, "wb") as fh:
            fh.write(png)
        n += 1
        time.sleep(0.05)
    print(f"{n} Flaggen in {OUT}")
    for f in fehlt:
        print("fehlt:", f)


if __name__ == "__main__":
    main()
