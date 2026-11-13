"""Erzeugt docs/Befehle.pdf aus src/main/resources/crownscore/commands.tsv.

Aufruf im Projektordner:  python tools/befehle_pdf.py
Braucht nur Python 3 und einen installierten Chrome oder Edge (Druck per Headless-Modus).
"""
import html
import io
import os
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import date

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TSV = os.path.join(ROOT, "src", "main", "resources", "crownscore", "commands.tsv")
OUT = os.path.join(ROOT, "docs", "Befehle.pdf")

ROLES = {
    "ALLE": ("Alle", "#2e7d32", "jeder Spieler, auch ohne Nation"),
    "BUERGER": ("Bürger", "#0277bd", "Mitglied einer Nation"),
    "BEAMTER": ("Beamte", "#b28704", "Staatschef und ernannte Beamte"),
    "CHEF": ("Staatschef", "#d84315", "nur der Staatschef"),
    "ADMIN": ("Admin", "#c62828", "OP-Level 2 oder in der Config unter admins"),
}


def version():
    with io.open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as f:
        m = re.search(r"^mod_version=(.+)$", f.read(), re.M)
    return m.group(1).strip() if m else "?"


def load():
    modules = []
    with io.open(TSV, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip() or line.startswith("#"):
                continue
            parts = line.split("\t")
            if line.startswith("@"):
                modules.append({"key": parts[0][1:], "title": parts[1],
                                "intro": parts[2] if len(parts) > 2 else "", "cmds": []})
            else:
                modules[-1]["cmds"].append((parts[0], parts[1], parts[2]))
    return modules


def syntax_html(s):
    out = html.escape(s)
    out = re.sub(r"(&lt;[^&]*?&gt;)", r'<span class="arg">\1</span>', out)
    out = re.sub(r"(\[[^\]]*\])", r'<span class="opt">\1</span>', out)
    return out


def build_html(modules):
    total = sum(len(m["cmds"]) for m in modules)
    legend = "".join(
        f'<li><span class="role" style="background:{c}">{l}</span> {html.escape(d)}</li>'
        for l, c, d in ROLES.values())
    toc = "".join(f'<li><a href="#{m["key"]}">{html.escape(m["title"])}</a> '
                  f'<span class="n">{len(m["cmds"])}</span></li>' for m in modules)
    sections = []
    for m in modules:
        rows = []
        for syn, role, desc in m["cmds"]:
            label, color, _ = ROLES[role]
            rows.append(f'<tr><td class="syn">{syntax_html(syn)}</td>'
                        f'<td><span class="role" style="background:{color}">{label}</span></td>'
                        f'<td>{html.escape(desc)}</td></tr>')
        sections.append(
            f'<section id="{m["key"]}"><h2>{html.escape(m["title"])}</h2>'
            f'<p class="intro">{html.escape(m["intro"])}</p>'
            f'<table><thead><tr><th>Befehl</th><th>Wer</th><th>Was er macht</th></tr></thead>'
            f'<tbody>{"".join(rows)}</tbody></table></section>')

    return f"""<!doctype html>
<html lang="de"><head><meta charset="utf-8"><title>Crowns Core Befehle</title>
<style>
@page {{ size: A4; margin: 16mm 14mm 18mm 14mm; }}
body {{ font-family: "Segoe UI", Arial, sans-serif; font-size: 9.6pt; color: #222; }}
h1 {{ font-size: 24pt; margin: 0 0 2mm; color: #5d4037; }}
.sub {{ color: #666; margin: 0 0 6mm; }}
h2 {{ font-size: 13pt; color: #5d4037; border-bottom: 1.5px solid #c8a96a; padding-bottom: 1mm; margin: 7mm 0 1.5mm; }}
.intro {{ color: #555; margin: 0 0 2mm; }}
table {{ width: 100%; border-collapse: collapse; }}
thead {{ display: table-header-group; }}
tr {{ page-break-inside: avoid; }}
th {{ text-align: left; font-size: 8.5pt; color: #777; font-weight: 600; border-bottom: 1px solid #ddd; padding: 1mm 1.5mm; }}
td {{ border-bottom: 1px solid #eee; padding: 1.3mm 1.5mm; vertical-align: top; }}
td.syn {{ font-family: Consolas, "Courier New", monospace; font-size: 9pt; width: 42%; color: #000; }}
td:nth-child(2) {{ width: 13%; white-space: nowrap; }}
.arg {{ color: #1565c0; }}
.opt {{ color: #6a1b9a; }}
.role {{ color: #fff; border-radius: 3px; padding: 0.3mm 1.6mm; font-size: 8pt; font-weight: 600; }}
.box {{ display: flex; gap: 8mm; margin-bottom: 4mm; }}
.box > div {{ flex: 1; background: #faf6ee; border: 1px solid #eadfc8; border-radius: 4px; padding: 3mm 4mm; }}
.box h3 {{ margin: 0 0 2mm; font-size: 10.5pt; color: #5d4037; }}
.box ul {{ margin: 0; padding-left: 0; list-style: none; }}
.box li {{ margin: 1.2mm 0; }}
.toc li {{ display: flex; justify-content: space-between; }}
.toc a {{ color: #222; text-decoration: none; }}
.n {{ color: #999; }}
.hint {{ color: #555; font-size: 9pt; }}
section {{ page-break-inside: auto; }}
</style></head><body>
<h1>Crowns Core</h1>
<p class="sub">Alle Befehle der Server-Mod für Crowns and Cannons · Version {html.escape(version())} · {total} Befehle · Stand {date.today().strftime("%d.%m.%Y")}</p>
<div class="box">
  <div><h3>Wer darf was</h3><ul>{legend}</ul>
  <p class="hint">Admins dürfen jeden Befehl. Staatschefs dürfen alles, was Beamte dürfen.</p></div>
  <div><h3>Module</h3><ul class="toc">{toc}</ul></div>
</div>
<p class="hint"><span class="arg">&lt;Pflicht&gt;</span> muss angegeben werden, <span class="opt">[optional]</span> kann wegfallen.
Für Land geht der Name oder das Kürzel. Im Spiel zeigt <b>/cccore list</b> dieselbe Liste, <b>/cccore list meine</b> nur deine Befehle.</p>
{"".join(sections)}
</body></html>"""


def find_browser():
    candidates = [
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    ]
    for name in ("google-chrome", "chromium", "chromium-browser", "microsoft-edge"):
        p = shutil.which(name)
        if p:
            candidates.insert(0, p)
    for c in candidates:
        if os.path.exists(c):
            return c
    sys.exit("Kein Chrome, Chromium oder Edge gefunden.")


def main():
    modules = load()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    tmp = tempfile.mkdtemp()
    page = os.path.join(tmp, "befehle.html")
    with io.open(page, "w", encoding="utf-8", newline="\n") as f:
        f.write(build_html(modules))
    subprocess.run([find_browser(), "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                    f"--user-data-dir={os.path.join(tmp, 'profil')}",
                    f"--print-to-pdf={OUT}", "file:///" + page.replace(os.sep, "/")],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    shutil.rmtree(tmp, ignore_errors=True)
    print("geschrieben:", OUT)


if __name__ == "__main__":
    main()
