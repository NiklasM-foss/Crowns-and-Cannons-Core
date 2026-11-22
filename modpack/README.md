# Modpack-Verwaltung

Der Client (Crowns-Helfer für Windows und Linux) liegt im eigenen Repository
[Crowns-Helfer](https://github.com/NiklasM-foss/Crowns-Helfer) (lokal `C:\Tools\crowns-helfer`). Hier steht, wie das
Pack gepflegt wird und wie der Server-Teil funktioniert.

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

- **Windows:** `Crowns-Helfer.bat` doppelklicken. Danach sitzt der Helfer als Krone im Infobereich der Taskleiste
  (unten rechts) und startet mit Windows.
- **Linux (KDE Plasma und andere Desktops mit Infobereich):** `python3 crowns-helfer.py`. Mit Qt für Python (PyQt6,
  PySide6 oder PyQt5, z.B. `sudo pacman -S python-pyqt6` oder `sudo apt install python3-pyqt6`) sitzt er ebenfalls
  als Symbol im Infobereich und startet über `~/.config/autostart`. Ohne Qt läuft er als systemd-Timer bzw. cron.
  Erkennt CurseForge- und Prism-Launcher-Instanzen.

Menü des Symbols (Rechtsklick): **Status anzeigen** (auch Doppelklick: Pack-Version, letzter Abgleich, Dateien zum
Selbst-Herunterladen mit Links), **Jetzt aktualisieren**, **Automatisch aktualisieren** an/aus (alle 15 Minuten),
**Mit Windows/dem System starten** an/aus, **Protokoll öffnen**, **Beenden**. Neue Datei im Downloads-Ordner oder
CurseForge-App geschlossen: der Helfer gleicht sofort ab.

Entfernen: `Crowns-Helfer.bat entfernen` bzw. `python3 crowns-helfer.py entfernen`. Einmal abgleichen mit Ausgabe:
`Crowns-Helfer.bat jetzt` bzw. `python3 crowns-helfer.py auto`.

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
