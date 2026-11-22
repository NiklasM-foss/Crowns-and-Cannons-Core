# Crowns-Helfer und Modpack-Verwaltung

Hält das CurseForge-Modpack „Crowns & Cannons“ bei allen Spielern automatisch aktuell: Mods, Ressourcenpakete,
Datenpakete, Shader, KubeJS und das Java-Argument. Gepflegt wird das Pack über eine Webseite, niemand muss mehr Links
verschicken oder Dateien bearbeiten.

## Für Admins: Pack veröffentlichen

1. `https://editor.niklasmetzger.de/pack/admin/` öffnen, mit dem Pack-Passwort anmelden (Tresor `crowns-editor-env`,
   Feld `PACK_PASSWORT`).
2. Den Instanz-Ordner wählen (z.B. `C:\curseforge\Instances\Crowns & Cannons staged`). Der Browser rechnet die
   Prüfsummen aus; die Dateien selbst werden nicht hochgeladen.
3. Der Dienst sucht zu jeder Datei eine Quelle, in dieser Reihenfolge:
   - **Modrinth**: exakt dieselbe Datei (SHA-1). Modrinth erlaubt Downloads durch Programme immer.
   - **CurseForge**: wenn der Autor Downloads durch Programme erlaubt.
   - **Server**: eine hochgeladene Kopie. Hochladen nur mit Angabe, warum es erlaubt ist (eigene Datei, Lizenz erlaubt
     Weitergabe, Erlaubnis vom Autor mit Notiz). KubeJS-Dateien gehen mit einem Klick als eigene Dateien auf den Server.
   - **keine**: Die Spieler bekommen ein Fenster mit dem Download-Link, der Helfer holt die Datei dann aus ihrem
     Downloads-Ordner.
4. Java-Argumente eintragen, Dateien abwählen, die nicht ins Pack sollen, **Veröffentlichen**. Jede Veröffentlichung
   bekommt eine neue Pack-Version; alte Versionen derselben Mod merkt sich der Dienst und der Helfer entfernt sie.

## Für Spieler: einmal einrichten

- **Windows:** `Crowns-Helfer.bat` doppelklicken. Richtet eine Windows-Aufgabe ein (bei der Anmeldung und alle
  15 Minuten, unsichtbar) und gleicht einmal ab. Entfernen: `Crowns-Helfer.bat entfernen`.
- **Linux:** `python3 crowns-helfer.py`. Richtet einen systemd-Benutzer-Timer ein (sonst cron). Erkennt CurseForge- und
  Prism-Launcher-Instanzen. Entfernen: `python3 crowns-helfer.py entfernen`.

Was der Helfer bei jedem Lauf macht:

- Liste von `https://editor.niklasmetzger.de/pack/crowns.json` holen (offline: die zuletzt geladene).
- Jede Datei per SHA-512 prüfen; fehlt sie oder ist sie anders, von der ersten funktionierenden Quelle laden und nur
  ablegen, wenn die Prüfsumme stimmt.
- Alte Versionen derselben Mod entfernen, aber nur Dateien, die das Pack früher selbst verteilt hat.
- Java-Argument setzen, sobald die CurseForge-App geschlossen ist (sie überschreibt die Einstellung sonst beim Beenden).
- Dateien ohne automatische Quelle: Fenster mit Links (Windows) bzw. Benachrichtigung (Linux), höchstens alle
  6 Stunden; liegt die Datei im Downloads-Ordner, wird sie übernommen.

**Eigene Dateien der Spieler werden nie angefasst.**

Protokoll: Windows `%LOCALAPPDATA%\CrownsHelfer\helfer.log`, Linux `~/.local/share/crowns-helfer/helfer.log`.

## Technik

- Dienst: `editor/antraege/pack.py` im Antrags-Container (Python-Stdlib). Daten in `/data/pack` (Volume): `crowns.json`,
  `verlauf/`, `dateien/` (Server-Kopien nach SHA-512), `uploads.json` (Erlaubnis-Angaben).
- Umgebung in `editor/.env`: `PACK_PASSWORT`, `CF_API_KEY` (CurseForge-Schnittstelle, sonst nur Modrinth und Server).
- nginx: `/pack/crowns.json`, `/pack/datei/<sha512>`, `/pack/api/` (Admin, Passwort im Header), `/pack/admin/`.
