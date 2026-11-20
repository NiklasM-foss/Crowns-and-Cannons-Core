# Rechte-Editor für Crowns Core

Der Browser-Editor für `/cccore perms`. Er ist der LuckPerms-Web-Editor
([LuckPermsWeb](https://github.com/LuckPerms/LuckPermsWeb), MIT-Lizenz) mit
Crowns-Core-Logo, Crowns-Core-Befehlen und einer Demo aus den Befehlen des Modpacks.
Er läuft komplett auf dem eigenen Server: keine Anfragen an luckperms.net, keine
Statistik, keine Google Fonts.

## Starten

Auf einem Rechner mit Docker, zum Beispiel neben dem Minecraft-Server:

```
cd editor
docker compose up -d --build
```

Der erste Bau dauert ein paar Minuten. Danach läuft alles unter Port 8095:

| Pfad | Inhalt |
|---|---|
| `/` | Startseite |
| `/editor/<Code>` | Editor-Sitzung |
| `/editor/demo` | Demo mit Crowns-Core- und Modpack-Befehlen |
| `/flagge/` | Upload-Seite für Flaggen (Umhänge), Ordner `flagge/` |
| `/data/` | Datenaustausch (bytebin), nur für Server und Editor |

Damit Admins von außen arbeiten können, braucht die Seite eine öffentliche Adresse,
zum Beispiel über einen Reverse-Proxy mit HTTPS auf Port 8095.

## Mod einstellen

In `world/serverconfig/crownscore-server.toml`:

```toml
[rechte]
    editorUrl = "https://rechte.example.de/editor/"
    # nur nötig, wenn der Minecraft-Server den Editor anders erreicht als die Admins
    bytebinUrl = "http://192.168.1.10:8095/data/"
```

Dann im Spiel `/cccore perms editor`, Link öffnen, bearbeiten, speichern und den
angezeigten Befehl `/cccore perms applyedits <Code>` eingeben.

Flaggen laufen über denselben Container: `/nation flagge` zeigt dem Staatschef den
Link auf `/flagge/` (aus `editorUrl` abgeleitet, sonst `umhang.seiteUrl`). Die Seite
lädt das Bild über `/data/` hoch, `/nation flagge anwenden <Code>` holt es ab.

## Was angepasst ist

- `branding/`: Logo, Browser-Icon, Apple-Icon.
- `branding.sh` und `branding.js`: Name, Goldton statt Grün, `/cccore perms` statt
  `/lp`, Baum- und Verbose-Ansicht aus dem Menü (gibt es in Crowns Core nicht),
  Schriften lokal, Übersetzungen vom eigenen Server, eigene Demo. Greift eine
  Ersetzung nicht, bricht der Bau ab.
- `meta/`: deutsche Texte, Sprachliste und Versionsdaten, die sonst von
  metadata.luckperms.net kämen.
- `demo/make_demo.py`: baut `demo/editor-demo.json` und `meta/` neu. Grundlage sind
  die Befehle der Mod (`src/main/resources/crownscore/commands.tsv`) und die
  Befehle des Modpacks (`demo/modpack-befehle.txt`, aus `/help` eines Testservers
  mit allen Modpack-Mods). Die Spieler in der Demo sind erfunden.

Die Editor-Version ist im `Dockerfile` über `LPW_COMMIT` festgelegt. Bei einer neuen
Version prüft der Bau, ob alle Anpassungen noch greifen.

## Lizenz

LuckPermsWeb steht unter der MIT-Lizenz, Copyright LuckPerms contributors. Der
Hinweis bleibt in der Fußzeile, die Lizenz liegt unter `/LICENSE-LuckPermsWeb.txt`.
Die 3D-Vorschau der Flaggen-Seite nutzt skinview3d 3.1.0 (MIT-Lizenz), die Datei
liegt unverändert in `flagge/vendor/` mit ihrer Lizenz.
