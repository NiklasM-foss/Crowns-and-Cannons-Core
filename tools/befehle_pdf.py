"""Erzeugt docs/commandliste.html und docs/Befehle.pdf aus src/main/resources/crownscore/commands.tsv.

Aufruf im Projektordner:  python tools/befehle_pdf.py         (HTML und PDF)
                          python tools/befehle_pdf.py --html  (nur HTML)
Die HTML-Seite braucht nur Python 3, die PDF zusaetzlich Chrome oder Edge (Druck per Headless-Modus).
Nach jeder Aenderung an commands.tsv ausfuehren und beide Dateien mit committen.
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
WEB = os.path.join(ROOT, "docs", "commandliste.html")

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
Für Land geht der Name oder das Kürzel. Im Spiel zeigt <b>/cccore list</b> die Befehle, die du nutzen darfst. Was du nicht darfst, siehst du dort und in der Tab-Vervollständigung nicht.</p>
{"".join(sections)}
</body></html>"""


def build_web_html(modules):
    """Interaktive Befehlsliste fuer den Browser: Suche, Filter nach Rolle, hell/dunkel, handytauglich."""
    total = sum(len(m["cmds"]) for m in modules)
    chips = "".join(
        f'<button type="button" class="chip on" data-role="{k}" style="--c:{c}" aria-pressed="true">{l}</button>'
        for k, (l, c, _) in ROLES.items())
    legend = "".join(
        f'<li><span class="role" style="--c:{c}">{l}</span> {html.escape(d)}</li>'
        for l, c, d in ROLES.values())
    toc = "".join(f'<a href="#{m["key"]}" data-mod="{m["key"]}">{html.escape(m["title"])} '
                  f'<span class="n">{len(m["cmds"])}</span></a>' for m in modules)
    sections = []
    for m in modules:
        rows = []
        for syn, role, desc in m["cmds"]:
            label, color, _ = ROLES[role]
            rows.append(f'<tr data-role="{role}"><td class="syn"><code>{syntax_html(syn)}</code>'
                        f'<button type="button" class="copy" data-cmd="{html.escape(syn)}" title="Kopieren" '
                        f'aria-label="Befehl kopieren">&#10697;</button></td>'
                        f'<td class="wer"><span class="role" style="--c:{color}">{label}</span></td>'
                        f'<td>{html.escape(desc)}</td></tr>')
        sections.append(
            f'<section id="{m["key"]}" data-mod="{m["key"]}"><h2>{html.escape(m["title"])} '
            f'<span class="n">{len(m["cmds"])}</span></h2>'
            f'<p class="intro">{html.escape(m["intro"])}</p>'
            f'<div class="scroll"><table><thead><tr><th>Befehl</th><th>Wer</th><th>Was er macht</th></tr></thead>'
            f'<tbody>{"".join(rows)}</tbody></table></div></section>')
    page = WEB_TEMPLATE
    for k, v in {"VERSION": html.escape(version()), "TOTAL": str(total),
                 "STAND": date.today().strftime("%d.%m.%Y"), "CHIPS": chips, "LEGEND": legend,
                 "TOC": toc, "SECTIONS": "".join(sections)}.items():
        page = page.replace("%%" + k + "%%", v)
    return page


WEB_TEMPLATE = r"""<!doctype html>
<html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Crowns Core Befehle</title>
<meta name="description" content="Alle Befehle der Server-Mod Crowns Core, Version %%VERSION%%">
<style>
:root { --bg:#fbf8f2; --fg:#222; --muted:#666; --card:#fff; --line:#e8e1d3; --accent:#5d4037; --gold:#c8a96a;
  --arg:#1565c0; --opt:#6a1b9a; --code:#000; --hover:#f6efe1; color-scheme:light; }
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) { --bg:#17140f; --fg:#e8e2d6; --muted:#a39a8a;
  --card:#211d16; --line:#3a3328; --accent:#e0c48c; --gold:#8a7140; --arg:#7fb3ff; --opt:#d29bff; --code:#fff;
  --hover:#2b261d; color-scheme:dark; } }
:root[data-theme="dark"] { --bg:#17140f; --fg:#e8e2d6; --muted:#a39a8a; --card:#211d16; --line:#3a3328; --accent:#e0c48c;
  --gold:#8a7140; --arg:#7fb3ff; --opt:#d29bff; --code:#fff; --hover:#2b261d; color-scheme:dark; }
* { box-sizing:border-box; }
html { scroll-padding-top:8.5rem; }
body { margin:0; background:var(--bg); color:var(--fg); font:15px/1.45 "Segoe UI",system-ui,-apple-system,Arial,sans-serif; }
header { position:sticky; top:0; z-index:5; background:var(--bg); border-bottom:1px solid var(--line); padding:.8rem 16px .6rem; }
.top { display:flex; align-items:center; gap:.8rem; flex-wrap:wrap; max-width:1100px; margin:0 auto; }
h1 { margin:0; font-size:1.35rem; color:var(--accent); }
.sub { color:var(--muted); font-size:.85rem; }
.tools { display:flex; gap:.5rem; flex-wrap:wrap; align-items:center; max-width:1100px; margin:.55rem auto 0; }
input[type=search] { flex:1 1 220px; min-width:0; padding:.5rem .7rem; border:1px solid var(--line); border-radius:6px;
  background:var(--card); color:var(--fg); font:inherit; }
.chip { border:1.5px solid var(--c); background:transparent; color:var(--fg); border-radius:99px; padding:.25rem .7rem;
  font:inherit; font-size:.82rem; cursor:pointer; }
.chip.on { background:var(--c); color:#fff; }
.theme { margin-left:auto; border:1px solid var(--line); background:var(--card); color:var(--fg); border-radius:6px;
  padding:.3rem .55rem; cursor:pointer; font:inherit; }
main { max-width:1100px; margin:0 auto; padding:1rem 16px 3rem; }
.box { display:grid; grid-template-columns:1fr 1fr; gap:1rem; margin-bottom:1rem; }
.box > div { background:var(--card); border:1px solid var(--line); border-radius:8px; padding:.8rem 1rem; }
.box h3 { margin:0 0 .5rem; font-size:1rem; color:var(--accent); }
.box ul { list-style:none; margin:0; padding:0; } .box li { margin:.3rem 0; }
.toc { display:flex; flex-wrap:wrap; gap:.35rem; }
.toc a { text-decoration:none; color:var(--fg); border:1px solid var(--line); border-radius:6px; padding:.15rem .5rem; font-size:.85rem; }
.toc a:hover { background:var(--hover); }
.hint { color:var(--muted); font-size:.85rem; }
h2 { font-size:1.15rem; color:var(--accent); border-bottom:2px solid var(--gold); padding-bottom:.2rem; margin:1.6rem 0 .3rem; }
.intro { color:var(--muted); margin:.2rem 0 .5rem; }
.n { color:var(--muted); font-weight:400; font-size:.8em; }
.scroll { overflow-x:auto; }
table { width:100%; border-collapse:collapse; background:var(--card); border:1px solid var(--line); }
th { text-align:left; font-size:.78rem; color:var(--muted); font-weight:600; border-bottom:1px solid var(--line); padding:.4rem .6rem; }
td { border-bottom:1px solid var(--line); padding:.45rem .6rem; vertical-align:top; }
tr:last-child td { border-bottom:0; }
tbody tr:hover { background:var(--hover); }
td.syn { width:42%; }
code { font-family:Consolas,"Cascadia Mono","Courier New",monospace; font-size:.9rem; color:var(--code); word-break:break-word; }
td.wer { width:7.5rem; white-space:nowrap; }
.arg { color:var(--arg); } .opt { color:var(--opt); }
.role { background:var(--c); color:#fff; border-radius:4px; padding:.05rem .45rem; font-size:.78rem; font-weight:600; }
.copy { margin-left:.35rem; border:0; background:none; color:var(--muted); cursor:pointer; font-size:.9rem; opacity:.5; }
.copy:hover, .copy:focus { opacity:1; }
.copy.ok { color:#2e7d32; opacity:1; }
mark { background:#ffe08a; color:#000; border-radius:2px; }
#leer { display:none; color:var(--muted); text-align:center; margin:3rem 0; }
footer { color:var(--muted); font-size:.8rem; text-align:center; margin-top:2rem; }
footer a { color:inherit; }
@media (max-width:700px) {
  .box { grid-template-columns:1fr; } .box > div { min-width:0; }
  .top .sub { flex-basis:100%; order:3; } .chip { padding:.2rem .55rem; }
  html { scroll-padding-top:11rem; }
  table, thead, tbody, tr, td { display:block; } thead { display:none; }
  tr { border-bottom:1px solid var(--line); padding:.4rem .2rem; } td { border:0; padding:.15rem .5rem; width:auto !important; }
}
</style></head><body>
<header>
  <div class="top"><h1>Crowns Core</h1>
    <span class="sub">Version %%VERSION%% · %%TOTAL%% Befehle · Stand %%STAND%%</span>
    <button type="button" class="theme" id="theme" title="Hell/Dunkel">&#9680;</button></div>
  <div class="tools"><input type="search" id="q" placeholder="Befehl oder Stichwort suchen, z.B. besetzen" autocomplete="off">%%CHIPS%%</div>
</header>
<main>
<div class="box">
  <div><h3>Wer darf was</h3><ul>%%LEGEND%%</ul>
  <p class="hint">Admins dürfen jeden Befehl. Staatschefs dürfen alles, was Beamte dürfen.</p></div>
  <div><h3>Module</h3><div class="toc">%%TOC%%</div>
  <p class="hint"><span class="arg">&lt;Pflicht&gt;</span> muss angegeben werden, <span class="opt">[optional]</span> kann wegfallen.
  Für Land geht der Name oder das Kürzel. Im Spiel zeigt <b>/cccore list</b> die Befehle, die du nutzen darfst.</p></div>
</div>
%%SECTIONS%%
<p id="leer">Kein Befehl passt zur Suche.</p>
<footer>Erzeugt aus <code>commands.tsv</code> · <a href="https://github.com/NiklasM-foss/Crowns-and-Cannons-Core">Quellcode</a> ·
<a href="https://www.curseforge.com/projects/1802743">CurseForge</a></footer>
</main>
<script>
(function () {
  var q = document.getElementById('q'), chips = [].slice.call(document.querySelectorAll('.chip'));
  var rows = [].slice.call(document.querySelectorAll('tbody tr'));
  rows.forEach(function (r) { r._text = r.textContent.toLowerCase(); r.cells[2]._orig = r.cells[2].innerHTML; });
  function esc(s) { return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }
  function apply() {
    var t = q.value.trim().toLowerCase(), words = t ? t.split(/\s+/) : [], on = {}, any = false;
    chips.forEach(function (c) { on[c.dataset.role] = c.classList.contains('on'); });
    rows.forEach(function (r) {
      var show = on[r.dataset.role] && words.every(function (w) { return r._text.indexOf(w) >= 0; });
      r.hidden = !show; if (show) any = true;
      var d = r.cells[2]; d.innerHTML = d._orig;
      if (show) words.forEach(function (w) {
        if (w.length > 1) d.innerHTML = d.innerHTML.replace(new RegExp('(' + esc(w) + ')(?![^<]*>)', 'gi'), '<mark>$1</mark>');
      });
    });
    [].forEach.call(document.querySelectorAll('section'), function (s) {
      var vis = s.querySelectorAll('tbody tr:not([hidden])').length; s.hidden = vis === 0;
      var a = document.querySelector('.toc a[data-mod="' + s.dataset.mod + '"]'); if (a) a.style.opacity = vis ? 1 : .35;
    });
    document.getElementById('leer').style.display = any ? 'none' : 'block';
  }
  q.addEventListener('input', apply);
  chips.forEach(function (c) { c.addEventListener('click', function () {
    c.classList.toggle('on'); c.setAttribute('aria-pressed', c.classList.contains('on')); apply(); }); });
  document.addEventListener('click', function (e) {
    var b = e.target.closest('.copy'); if (!b || !navigator.clipboard) return;
    navigator.clipboard.writeText(b.dataset.cmd).then(function () {
      b.classList.add('ok'); b.textContent = '✓';
      setTimeout(function () { b.classList.remove('ok'); b.textContent = '⧉'; }, 1200);
    }, function () {});
  });
  var root = document.documentElement, th = document.getElementById('theme');
  try { var saved = localStorage.getItem('cc-theme'); if (saved) root.dataset.theme = saved; } catch (e) {}
  th.addEventListener('click', function () {
    var dark = root.dataset.theme ? root.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
    root.dataset.theme = dark ? 'light' : 'dark';
    try { localStorage.setItem('cc-theme', root.dataset.theme); } catch (e) {}
  });
})();
</script>
</body></html>
"""


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
    with io.open(WEB, "w", encoding="utf-8", newline="\n") as f:
        f.write(build_web_html(modules))
    print("geschrieben:", WEB)
    if "--html" in sys.argv:
        return
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
