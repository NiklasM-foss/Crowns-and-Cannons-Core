# Changelog

## 1.2.0

- **Spielermodus** `/modus`: RP, Farm und AFK. Im Farm- und AFK-Modus darf nur
  angreifen, wer mit `/modus grund` einen Grund einträgt. AFK mit Endzeit wie eine
  Parkscheibe, Modus in der Tabliste, Kampfsperre gegen Flucht in den Farm-Modus.
- **Blockprotokoll** `/cccore admin` nach dem Vorbild von CoreProtect: Inspektor,
  Abfragen mit Parametern, Rollback und Restore für Blöcke, Kisteninhalte und
  getötete Tiere, Vorschau, Undo, Purge. Eingebettete H2-Datenbank.
- **Rechteverwaltung** `/cccore perms` nach dem Vorbild von LuckPerms: Gruppen mit
  Gewicht und Vererbung, Spielerrechte, Platzhalter, befristete und
  dimensionsgebundene Rechte, Präfix und Suffix, Laufbahnen, Forge-Permission-Handler,
  `command.<name>` für jeden Befehl.
- **Befehle ohne Berechtigung sind unsichtbar**, auch in der Tab-Vervollständigung.
  `/cccore list` zeigt nur noch nutzbare Befehle.
- **Rechte im Browser**: `/cccore perms editor` und `applyedits` mit dem eigenen
  Rechte-Editor (LuckPerms-Web-Editor mit Crowns-Core-Logo, Ordner `editor/`,
  Docker), Demo mit den Befehlen von Crowns Core und dem Modpack.
- **Nation im Chat** vor dem Spielernamen, als Name oder Kürzel, Farbe einstellbar.
- **Weltkarte**: 238 Länder der Erdkarte mit Grenzen aus Natural Earth,
  `/nation weltkarte import` legt sie als Nationen an, Gebiet aus der Karte für
  gespielte Nationen, Grenzen auf Xaero's Minimap über Open Parties and Claims.
- Selbsttest-Mod für den Testserver (`./gradlew selftestJar`, `/cctest`).

## 1.0.0

- Erste Version: Nationen, Kriege mit Kriegsart und PvP-Sperre, Duelle,
  Beweisakte, Aktivität, Staatskasse, Darlehen, Kolonien, Wegsteine, Wahlen, Visa,
  Post, Verträge, interne Gebiete, `/cccore list`.
