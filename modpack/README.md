# Crowns-Helfer

Ergänzt das CurseForge-Modpack „Crowns & Cannons“ um das, was CurseForge selbst nicht kann, und hält es automatisch
aktuell:

- **Zusatz-Mods**, die es auf CurseForge nicht (in der nötigen Version) gibt, zum Beispiel CC: Tweaked 1.122.0 von
  Modrinth. Download nur von der offiziellen Quelle, Prüfung per SHA-512, alte Versionen derselben Mod werden ersetzt.
- **Java-Argumente** der Instanz, zum Beispiel `-Dorg.lwjgl.system.allocator=system`.

Eigene Mods der Spieler werden nie angefasst: Gelöscht werden nur Dateien, die zum Muster `ersetzt` einer Zusatz-Mod
passen, und nur wenn die richtige Version schon da ist.

## Für Spieler

- **Windows:** `Crowns-Helfer.bat` doppelklicken. Richtet eine Windows-Aufgabe ein (bei der Anmeldung und alle
  15 Minuten, unsichtbar) und läuft einmal. Entfernen: `Crowns-Helfer.bat entfernen`.
- **Linux:** `python3 crowns-helfer.py`. Richtet einen systemd-Benutzer-Timer ein (sonst cron). Entfernen:
  `python3 crowns-helfer.py entfernen`. Erkennt CurseForge- und Prism-Launcher-Instanzen.
- Das Java-Argument setzt der Helfer erst, wenn die CurseForge-App geschlossen ist, weil die App die Einstellung
  sonst beim Beenden überschreibt.
- Protokoll: Windows `%LOCALAPPDATA%\CrownsHelfer\helfer.log`, Linux `~/.local/share/crowns-helfer/helfer.log`.

## Für Admins: neue Zusatz-Mod oder neues Argument

Die Liste liegt in `editor/pack/crowns.json` und wird vom Editor-Container unter
`https://editor.niklasmetzger.de/pack/crowns.json` ausgeliefert. Neue Mod eintragen mit `datei`, `url` (offizieller
Download), `sha512` und `ersetzt` (Dateimuster der alten Versionen), auf den Pi bringen, fertig. Beim nächsten Lauf
haben alle Spieler den neuen Stand.

URL und SHA-512 einer Modrinth-Datei:

    curl -s "https://api.modrinth.com/v2/project/<slug>/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D"

Beim Modpack-Export in der CurseForge-App die Zusatz-Mods nicht mit exportieren, sie ergänzt der Helfer.
