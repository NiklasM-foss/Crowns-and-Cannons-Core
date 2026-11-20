"""Erzeugt docs/laender.html: alle Laender der Weltkarte mit Kuerzel, Flagge, Kartenfarbe und Groesse.

Quelle: src/main/resources/crownscore/weltkarte/laender.tsv und die Flaggen in crownscore/flaggen/.
Die Seite ist eigenstaendig (Flaggen eingebettet) und funktioniert ohne Internet.

Aufruf: python tools/flaggen/make_laender_html.py
"""
import base64
import html
import io
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "src", "main", "resources", "crownscore")
OUT = os.path.join(ROOT, "docs", "laender.html")


def sortkey(name):
    return name.lower().translate(str.maketrans("äöüåé", "aouae"))


def main():
    rows = []
    for line in io.open(os.path.join(RES, "weltkarte", "laender.tsv"), encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        _, code, name, color, chunks = line.rstrip("\n").split("\t")
        rows.append((code, name, color, int(chunks)))
    rows.sort(key=lambda r: sortkey(r[1]))

    trs = []
    for code, name, color, chunks in rows:
        f = os.path.join(RES, "flaggen", code + ".png")
        if os.path.exists(f):
            data = base64.b64encode(open(f, "rb").read()).decode()
            flag = f'<img src="data:image/png;base64,{data}" alt="" loading="lazy">'
        else:
            flag = '<span class="keine">keine</span>'
        groesse = f"{chunks:,}".replace(",", ".") if chunks else '<span class="keine">zu klein</span>'
        trs.append(
            f'<tr data-s="{html.escape(code.lower() + " " + name.lower())}">'
            f'<td class="flagge">{flag}</td>'
            f'<td><code>{code}</code></td>'
            f'<td>{html.escape(name)}</td>'
            f'<td><span class="farbe" style="background:#{color}"></span></td>'
            f'<td class="zahl">{groesse}</td></tr>'
        )

    page = f"""<!doctype html>
<html lang="de">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Länderkürzel</title>
<style>
:root {{ --bg:#f7f5f1; --card:#fff; --line:#e3ded6; --text:#22201c; --muted:#77706a; --gold:#b9862a; color-scheme: light dark; }}
@media (prefers-color-scheme: dark) {{
  :root {{ --bg:#17161a; --card:#222027; --line:#34313b; --text:#ece8f0; --muted:#a39dab; --gold:#e0a83a; }}
}}
* {{ box-sizing:border-box; }}
body {{ margin:0; background:var(--bg); color:var(--text); font:15px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif; }}
main {{ max-width:900px; margin:0 auto; padding:24px 16px 40px; }}
h1 {{ margin:0 0 4px; color:var(--gold); }}
p {{ margin:4px 0 14px; color:var(--muted); }}
code {{ font:600 14px ui-monospace,Consolas,monospace; color:var(--gold); }}
input {{ width:100%; font:inherit; padding:10px 12px; border:1px solid var(--line); border-radius:8px; background:var(--card); color:var(--text); margin-bottom:12px; }}
table {{ width:100%; border-collapse:collapse; background:var(--card); border:1px solid var(--line); border-radius:10px; overflow:hidden; }}
th, td {{ text-align:left; padding:6px 10px; border-bottom:1px solid var(--line); }}
th {{ font-size:13px; color:var(--muted); font-weight:600; position:sticky; top:0; background:var(--card); }}
tr:last-child td {{ border-bottom:none; }}
.flagge img {{ width:42px; height:28px; object-fit:cover; display:block; border:1px solid var(--line); border-radius:2px; }}
.farbe {{ display:inline-block; width:22px; height:14px; border-radius:3px; border:1px solid var(--line); vertical-align:middle; }}
.zahl {{ text-align:right; font-variant-numeric:tabular-nums; }}
.keine {{ color:var(--muted); font-size:13px; }}
@media (max-width:560px) {{ .farbe-sp {{ display:none; }} td:nth-child(4) {{ display:none; }} }}
</style>
</head>
<body>
<main>
<h1>Länderkürzel</h1>
<p>Alle {len(rows)} Länder der Weltkarte von Crowns Core. Befehle nehmen das Kürzel, zum Beispiel
<code>/nation staatschef SAU Spieler</code> oder <code>/nation annektieren FRA Deutschland</code>.
Kartenfarbe = Farbe auf der Minimap, Chunks = Größe auf der Karte. Flaggen: flagcdn.com (gemeinfrei).</p>
<input type="search" id="suche" placeholder="Land oder Kürzel suchen ..." autofocus>
<table>
<thead><tr><th>Flagge</th><th>Kürzel</th><th>Land</th><th class="farbe-sp">Kartenfarbe</th><th class="zahl">Chunks</th></tr></thead>
<tbody>
{chr(10).join(trs)}
</tbody>
</table>
</main>
<script>
document.getElementById("suche").addEventListener("input", function () {{
  var q = this.value.trim().toLowerCase();
  document.querySelectorAll("tbody tr").forEach(function (tr) {{
    tr.hidden = q && tr.dataset.s.indexOf(q) < 0;
  }});
}});
</script>
</body>
</html>
"""
    io.open(OUT, "w", encoding="utf-8", newline="\n").write(page)
    print(f"{OUT}: {len(rows)} Laender, {os.path.getsize(OUT) // 1024} KB")


if __name__ == "__main__":
    main()
