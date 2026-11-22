# Changelog

## 1.5.1

- **Kartenfarben korrigiert**: Rot und Blau waren auf World Map und Minimap vertauscht
  (Flaggen, Stadtwappen, Stadtrahmen, Angriffslinie). Xaero erwartet die Farbkanäle in
  der Reihenfolge Blau, Grün, Rot, Alpha.
- **Transparente Flaggen**: Durchsichtige Stellen kommen jetzt auf weißen Stoff, wie in der
  Vorschau der Upload-Seite. Vorher waren sie auf dem Umhang schwarz und auf der Karte
  schien das Gelände durch. Stadtwappen bleiben auf der Karte freigestellt.
- Nur der Client ist betroffen, Server und Spieler sollten trotzdem dieselbe Version haben.

## 1.5.0

- **Städte**: `/stadt gruenden <Name>` oder Rechtsklick auf der World Map („Stadt hier
  beantragen“). Geprüft werden Betten, Glocke, Abstand zur nächsten Stadt und eigenes
  Gebiet, dann muss ein Admin bestätigen: im Spiel, im Postfach oder im Browser über
  den Link, den der Antrag in den Discord schickt (`editor/antraege/`).
- **Städte auf der Karte**: Stadtgebiete haben auf World Map und Minimap einen goldenen
  Rahmen und einen Punkt in der Mitte, wartende Anträge sind grau gestrichelt. Der
  Tooltip der World Map zeigt Stadt und Nation.
- **Städte erobern**: Länder mit Städten werden Stadt für Stadt besetzt. Wer alle Städte
  eines Landes hält, bekommt das ganze Land. Länder ohne Städte wie bisher als Ganzes.
- **Wappen, Sichtbarkeit, Wegpunkte**: Jede Stadt zeigt auf der Karte ihr Wappen (Upload
  mit `/stadt wappen <Stadt>`, Admin-Freigabe), ohne Wappen die Flagge der Nation. Städte
  sieht nur die eigene Nation und wer schon einmal dort war. In jeder sichtbaren Stadt
  steht ein Xaero-Wegpunkt in der Mitte.
- **Belagerung**: rote Linie vom Gebiet des Angreifers zur belagerten Stadt, roter
  Stadtrahmen. Sind alle Angreifer weiter als 100 Blöcke weg, gilt die Belagerung als
  aufgegeben; bleiben die Verteidiger fern, obwohl sie online sind, fällt die Stadt.
  `/nation aufgeben` für beide Seiten.
- `/cccore staedte uebertragen <Stadt> <Nation>` für Admins.
- **Villager-Händler**: `/cccore haendler setzen` stellt einen Händler auf, der nie
  ausverkauft ist. Villager und Rekruten (Villager Recruits, gehören gleich dem Käufer)
  gegen Geld oder Rohstoffe. Wie viele eine Nation kaufen darf, hängt an ihren Städten,
  ab 8 Städten unbegrenzt. Jeder Kauf macht den nächsten teurer, die Nachfrage sinkt mit
  der Zeit. Gekaufte Villager sind arbeitslos und können direkt bei VillagerShop arbeiten.
- **Postfach** `/postfach` für jeden Spieler als Menü: Entscheidungen, Besatzungs-Alarm,
  Eroberungen, Flaggen-Freigaben, Briefe an die eigene Nation, für Admins die offenen
  Anträge. Hinweis beim Einloggen.
- Neue Config-Abschnitte `[stadt]`, `[villagerkauf]`, `[antraege]`, Webhook
  `discord.antraege`. Netzwerkversion 3, Clients brauchen 1.5.0.

## 1.4.0

- **Flaggen auf der Karte**: Auf Xaero's World Map und Minimap liegt über jedem Land
  die Flagge der Nation, die es hält, statt einer einfarbigen Fläche. Die Flagge wird
  über das Land gespannt, erobert eine Nation ein Land, weht dort ihre Flagge. Eigene
  Flaggen aus dem Browser erscheinen auch auf der Karte. Deckkraft und Bildgröße in
  der Config `[kartenflaggen]`. Xaero wird dafür nicht verändert, Crowns Core hängt
  sich in die Liste der Kartenmarkierungen ein; passt eine Xaero-Version nicht,
  bleibt die normale Claim-Farbe.
- **Länder kaufen**: `/nation land kaufen <Land>` kauft ein freies Land (Nation ohne
  Spieler) aus der Staatskasse. Preis = Grundpreis + Betrag pro Chunk, das Land muss
  nahe am eigenen Gebiet liegen. `/nation land preis <Land>` zeigt Preis und ob es geht.
- **Länder besetzen**: `/nation besetzen` setzt im Krieg einen Besatzungsblock in ein
  Land der gegnerischen Nation. Steht er die Haltezeit lang (Standard 2 Stunden,
  gezählt nur, solange ein Verteidiger online ist), gehört das Land dem Angreifer.
  Wird er abgebaut oder gesprengt, ist die Besetzung gescheitert und die Gebühr weg.
  Meldung im Chat, an die Verteidiger und im Kriegsnews-Kanal.
- Schutzregeln: neue Nationen eine Zeit lang geschützt, Stammland erst als letztes
  Land einnehmbar, Sperre nach einer Einnahme, Anzahl gleichzeitiger Besetzungen.
  Alle Werte stehen in der Config `[eroberung]`.
- Netzwerkversion 2: Clients brauchen Crowns Core 1.4.0, Spieler ganz ohne die Mod
  können sich weiter verbinden.

## 1.3.0

- **Flaggen als Umhang**: Jeder Bürger trägt die Flagge seiner Nation, gedreht wie
  ein Banner am Mast, in achtfacher Auflösung. Ohne eigene Flagge die echte
  Landesflagge (alle 238 Länder der Weltkarte).
- **Eigene Flaggen aus dem Browser**: `/nation flagge` öffnet die Upload-Seite mit
  3D-Vorschau (`editor/flagge/`), `/nation flagge anwenden <Code>` übernimmt das Bild.
  Admins geben neue Flaggen mit `/cccore flaggen` frei, mit Vorschau auf dem eigenen
  Rücken.
- `/umhang an|aus` für jeden Spieler selbst.
- Crowns Core hat jetzt einen Client-Teil (nur für die Umhänge). Spieler ohne die
  Mod können sich weiter verbinden.
- Länderkürzel mit Flaggen als Seite: `docs/laender.html`.

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
  gespielte Nationen. Auf Xaero's Minimap ist jedes Land eine eigene Fläche mit
  eigener Party in Open Parties and Claims, Besitzer ist der Staatschef.
  `/nation annektieren` und `/gebiet zuweisen` für Eroberungen, die Grenze
  verschwindet dann.
- Selbsttest-Mod für den Testserver (`./gradlew selftestJar`, `/cctest`).

## 1.0.0

- Erste Version: Nationen, Kriege mit Kriegsart und PvP-Sperre, Duelle,
  Beweisakte, Aktivität, Staatskasse, Darlehen, Kolonien, Wegsteine, Wahlen, Visa,
  Post, Verträge, interne Gebiete, `/cccore list`.
