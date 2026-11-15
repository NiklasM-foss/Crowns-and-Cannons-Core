"""Erzeugt die Weltkarte fuer Crowns Core aus Natural Earth (gemeinfrei, naturalearthdata.com).

Die Server-Map ist eine Erdkarte in Plattkarten-Projektion: 49152 x 24576 Bloecke, x -24576..24575 =
Laenge -180..180, z -12288..12287 = Breite 90..-90. Die Karte hier ist geografisch (Laenge/Breite) in
3072 x 1536 Feldern abgelegt, also ein Feld pro Chunk bei dieser Mapgroesse. Die Umrechnung auf
Blockkoordinaten macht die Mod und laesst sich in der Config nachjustieren.

Aufruf:
  curl -L -o countries.geojson https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_countries.geojson
  python make_weltkarte.py countries.geojson

Ausgaben:
  src/main/resources/crownscore/weltkarte/karte.bin.gz   Feldwerte 0 = Meer/niemand, sonst Laendernummer
  src/main/resources/crownscore/weltkarte/laender.tsv    Nummer, Code, deutscher Name, Farbe, Felder
  docs/weltkarte.png                                     Vorschau
"""
import gzip
import io
import json
import os
import struct
import sys

from PIL import Image, ImageDraw

W, H = 3072, 1536
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, "src", "main", "resources", "crownscore", "weltkarte")
SKIP = {"ATA"}  # Antarktis: riesiger Streifen am Kartenrand, niemand spielt dort

# Natural Earth MAPCOLOR9: Nachbarlaender haben nie dieselbe Nummer
PALETTE = {
    1: 0xE6194B, 2: 0x3CB44B, 3: 0x4363D8, 4: 0xF58231, 5: 0x911EB4,
    6: 0x42D4F4, 7: 0xF032E6, 8: 0xBFEF45, 9: 0xFABED4,
}


def px(lon, lat):
    return ((lon + 180.0) / 360.0 * W, (90.0 - lat) / 180.0 * H)


def ring_area(ring):
    a = 0.0
    for (x1, y1), (x2, y2) in zip(ring, ring[1:] + ring[:1]):
        a += x1 * y2 - x2 * y1
    return abs(a) / 2


def polygons(geom):
    if geom["type"] == "Polygon":
        return [geom["coordinates"]]
    if geom["type"] == "MultiPolygon":
        return geom["coordinates"]
    return []


def main(path):
    data = json.load(io.open(path, encoding="utf-8"))
    feats = []
    for f in data["features"]:
        p = f["properties"]
        code = p.get("ADM0_A3") or p.get("ISO_A3")
        if not code or code in SKIP:
            continue
        name = (p.get("NAME_DE") or p.get("NAME") or code).strip()
        polys = polygons(f["geometry"])
        area = sum(ring_area(poly[0]) for poly in polys if poly)
        feats.append({"code": code, "name": name, "color": PALETTE.get(p.get("MAPCOLOR9") or 1, 0xCCCCCC), "polys": polys, "area": area})

    # feste Nummern nach Name, gezeichnet wird von gross nach klein, damit Enklaven (Lesotho) sichtbar bleiben
    feats.sort(key=lambda x: x["name"].lower())
    for i, f in enumerate(feats, start=1):
        f["idx"] = i
    if len(feats) > 255:
        sys.exit("Mehr als 255 Laender, Format reicht nicht")

    img = Image.new("L", (W, H), 0)
    draw = ImageDraw.Draw(img)
    for f in sorted(feats, key=lambda x: -x["area"]):
        for poly in f["polys"]:
            outer = [px(lon, lat) for lon, lat in poly[0]]
            if len(outer) >= 3:
                draw.polygon(outer, fill=f["idx"])
            for hole in poly[1:]:
                pts = [px(lon, lat) for lon, lat in hole]
                if len(pts) >= 3:
                    draw.polygon(pts, fill=0)
    # Loecher, die von kleineren Laendern gefuellt werden, noch einmal zeichnen
    for f in sorted(feats, key=lambda x: x["area"]):
        if f["area"] < 50:
            for poly in f["polys"]:
                outer = [px(lon, lat) for lon, lat in poly[0]]
                if len(outer) >= 3:
                    draw.polygon(outer, fill=f["idx"])

    raw = img.tobytes()
    counts = [0] * 256
    for b in raw:
        counts[b] += 1

    os.makedirs(OUT, exist_ok=True)
    with gzip.open(os.path.join(OUT, "karte.bin.gz"), "wb", compresslevel=9) as g:
        g.write(struct.pack(">II", W, H))
        g.write(raw)
    kept, dropped = [], []
    with io.open(os.path.join(OUT, "laender.tsv"), "w", encoding="utf-8", newline="\n") as t:
        t.write("# Nummer\tCode\tName\tFarbe\tFelder  (Natural Earth, gemeinfrei; erzeugt von tools/weltkarte/make_weltkarte.py)\n")
        for f in feats:
            n = counts[f["idx"]]
            t.write(f"{f['idx']}\t{f['code']}\t{f['name']}\t{f['color']:06X}\t{n}\n")
            (kept if n > 0 else dropped).append(f)

    # Vorschau
    pal = [0, 0, 0] * 256
    pal[0:3] = [20, 40, 70]
    for f in feats:
        c = f["color"]
        pal[f["idx"] * 3:f["idx"] * 3 + 3] = [(c >> 16) & 255, (c >> 8) & 255, c & 255]
    prev = img.copy()
    prev.putpalette(pal)
    prev = prev.convert("RGB").resize((W // 2, H // 2), Image.NEAREST)
    prev.save(os.path.join(ROOT, "docs", "weltkarte.png"), optimize=True)

    land = sum(counts[1:])
    print(f"{len(kept)} Laender auf der Karte, {land} Land-Chunks von {W * H}")
    if dropped:
        print("Zu klein fuer einen Chunk:", ", ".join(f["name"] for f in dropped))


if __name__ == "__main__":
    main(sys.argv[1])
