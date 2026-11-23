# Changelog

## 1.5.7

- **Eroberung:** Ein nachträglicher Claim bricht eine Besetzung nur noch ab, wenn ihn die Angreifer gesetzt haben.
  Verteidiger können die Besetzung so nicht mehr selbst scheitern lassen.
- **Rechte:** Admin-Befehle beachten die Gruppengewichte, ein höher gewichtetes Platzhalter-Verbot gewinnt. `*=false`
  sperrt wieder auch Vanilla- und Mod-Befehle. Vorschläge mit `*` erscheinen schon beim Tippen ohne Anführungszeichen.
- **Darlehen:** Teilzahlungen im Verzug runden ab, die Kasse wird nie negativ, ein abbezahltes Darlehen verschwindet sofort.
- **Pack-Verwaltung:** Fehlversuche zählen bei IPv6 pro /64-Netz, dazu eine Obergrenze für alle Adressen zusammen.

## 1.5.6

- **Rechte:** `*` gibt keine Vanilla- oder Mod-Befehle mehr frei (`command.<name>` nur ausdrücklich),
  genau gesetzte Admin-Knoten wirken auch, wenn der Spieler zusätzlich einen Platzhalter hat.
- **Nation löschen** funktioniert auch ohne Open Parties and Claims und verträgt einen laufenden Kartenabgleich.
- **Eroberung:** Kommt später ein fremder Claim auf den Besatzungsblock, endet die Besetzung.
- **Darlehen:** Tilgen beendet zuerst jeden Verzug und nennt den Restbetrag; die automatische Rate
  bucht im Verzug ab, was da ist.
- **Kleinere Fehler:** Duelle nur einzeln, Forderungen werden beim Annehmen bereinigt, `/aktivitaet`
  und `/pause` erkennen erfundene Namen, Platzhalter-Vorschläge mit Anführungszeichen, die
  Pack-Verwaltung erkennt die echte Besucher-IP hinter dem Cloudflare-Tunnel.

## 1.5.5

- **Rechte:** Admin-Befehle gibt nur der genaue Rechteknoten frei, nie `crownscore.*` oder `*`.
  Platzhalter lassen sich per Befehl in Anführungszeichen setzen.
- **Nation löschen** gibt jeden Claim des Staatskontos in OPAC sofort frei und löst die Party auf,
  frühere Bürger sind frei für neue Nationen.
- **Eroberung:** Der Besatzungsblock darf nicht in fremden Spieler-Claims stehen.
- **Darlehen:** Tilgen bedient zuerst Darlehen im Verzug, die automatische Rate zieht offene Raten mit ein.
- **Wahlen:** Staatschef und Beamte können Abstimmungen ihrer Nation abbrechen.
- **Kleinere Fehler:** Duell-Forderungen lassen sich zurückziehen, alle Wechselwünsche erscheinen in
  `/krieg liste`, `/akte` meldet unbekannte Typen und zu hohe Seiten, `/aktivitaet` und `/pause`
  nur für bekannte Spieler, klarere Meldung bei zu großen Bildern, der Briefzähler beim Login zählt
  nur für Staatschef und Beamte, die Pack-Verwaltung zählt Fehlversuche je echter IP.

## 1.5.4

- **Absturz beim Ausloggen behoben:** Seit 1.5.0 konnte jeder Logout den Server abstürzen lassen,
  weil zwei interne Ereignisklassen gleich hießen.
- **Geld:** Überweisungen an andere Nationen unterliegen derselben Grenze wie Auszahlungen
  (geliehenes Geld bleibt in der Kasse). Ein Cent Tilgung hebt den Zahlungsverzug nicht mehr auf.
  Gutschriften an tote Spieler gehen nicht mehr verloren.
- **Nationen löschen** gibt Land und Claims in OPAC frei und räumt Kolonien, Visa, Verträge,
  Briefe und Wahlen mit ab. Umbenennen zieht die Akte mit, nur die Schreibweise zu ändern geht.
- **Eroberung:** Der Besatzungsblock muss im Gebiet der Verteidiger stehen.
- **Karte:** Änderungen während eines laufenden Abgleichs werden nachgeholt.
- **Kleinere Fehler:** `/post lesen` nur für Staatschef und Beamte, `/wahl abbrechen [Land]` für
  Admins und Konsole, `/kolonie ablehnen` nur bei offenem Antrag, Abstimmungen von Bürgern
  höchstens einen Tag, keine doppelten Discord-Meldungen bei Zeitüberschreitung, Vorschläge mit
  Anführungszeichen funktionieren überall, Fehlversuche an der Pack-Verwaltung werden begrenzt.

## 1.5.3

- **Lizenz:** Crowns Core steht ab dieser Version unter der LGPL-3.0 (vorher „All Rights Reserved“).
- **Wahlen:** Eine Wahl des Staatschefs setzen nur Staatschef oder Beamte an, sie dauert mindestens
  24 Stunden (Config `wahlen.amtMindestMinuten`). Gezählt werden nur Stimmen aktueller Bürger.
- **Staatskasse:** Geliehenes Geld lässt sich nicht privat auszahlen, im Zahlungsverzug ist die
  Auszahlung gesperrt. `/staatskasse` zeigt Spielern nur noch die eigene Kasse.
- **Eroberung:** Stammland-Schutz und Nachbar-Abstand gelten auch, wenn ein Land über seine Städte
  fällt. Der Besatzungsblock darf nicht im eigenen Gebiet stehen. Sind die Verteidiger offline und
  die Uhr steht, müssen auch die Angreifer nicht vor Ort warten. Die Kulanzzähler überstehen einen
  Neustart. Abstände über die Datumsgrenze (Alaska, Russland, Fidschi) stimmen jetzt.
- **Nationen umbenennen und löschen:** Städte, Besetzungen, Wahlen, Verträge, Akte und
  Villager-Zähler ziehen mit bzw. werden mit aufgeräumt. Neue Namen brauchen 2 bis 40 Zeichen.
- **Sicherheit:** Riesige Bilder werden vor dem Dekodieren abgelehnt. Der Entscheidungslink für
  Stadtanträge geht nur in den Admin-Webhook. `crownscore.admin` wirkt nur ausdrücklich, nicht über
  `crownscore.*`. Stadtanträge über die Karte erzeugen keine neue Welt mehr. Briefe haben eine
  Wartezeit und eine Aufbewahrungsgrenze. Ablehnungsgründe aus dem Browser werden gesäubert.
- **Kleinere Fehler:** Camping zählt nur vor echten Bases statt im ganzen fremden Land, Briefe im
  Postfach nur für Staatschef und Beamte, `/modus afk 20:00` geht, `/stadt liste "Name"` geht,
  Vorschläge für Nationen mit Umlaut, Kriegserklärungen stehen in der Akte, Kolonien prüfen Typ,
  Name und Meer, `/kolonie genehmigen` nur bei offenem Antrag, keine Wegstein-Freigabe an sich
  selbst, zwei schnelle Landwechsel landen beide auf der Karte, Discord-Meldungen werden bei
  Verbindungsabbruch wiederholt, abgebrochene Abbau-Versuche landen nicht im Protokoll, jede
  Minutenaufgabe läuft für sich.

## 1.5.2

- **Transparente Flaggen**: Durchsichtige Stellen kommen jetzt auf weißen Stoff, wie in der
  Vorschau der Upload-Seite. Vorher waren sie auf dem Umhang schwarz und auf der Karte
  schien das Gelände durch. Stadtwappen bleiben auf der Karte freigestellt.
- Fehler behoben, die eine Code-Prüfung gefunden hat:
  - Ein verlorenes Stammland kam nach einem Neustart zurück zur alten Nation.
  - Wer alle Städte eines Landes hielt, bekam das Land auch von einer Nation, mit der er
    nicht im Krieg war.
  - Händler- und Postfach-Menü haben ein Item am Mauszeiger gelöscht; Klicks auf leere
    Händler-Felder konnten unsichtbare Angebote kaufen.
  - Stadtnamen konnten im Discord @everyone auslösen und die Admin-Knöpfe kaputt machen;
    erlaubt sind jetzt nur Buchstaben, Ziffern, Leerzeichen, Bindestrich und Apostroph.
  - Stadtwappen ähnlich geschriebener Städte überschrieben sich; beim Auflösen einer Stadt
    wird ihr Wappen gelöscht.
  - Stadtantrag auf der Karte nur noch innerhalb von Weltgrenze und Karte.
  - `/nation aufgeben` verlangt eine Nummer, wenn die Nation an mehreren Besetzungen
    beteiligt ist.
  - Städte werden auch von Spielern ohne Crowns Core im Client entdeckt.
  - Ein Fehler in einer Sekundenaufgabe legt die anderen nicht mehr still.
  - Besatzungsblock wird auch in entladenen Chunks entfernt.
  - Antrags-Dienst: ungültige Anfragen (negative Länge, keine JSON-Objekte) werden abgewiesen.

## 1.5.1

- **Kartenfarben korrigiert**: Rot und Blau waren auf World Map und Minimap vertauscht
  (Flaggen, Stadtwappen, Stadtrahmen, Angriffslinie). Xaero erwartet die Farbkanäle in
  der Reihenfolge Blau, Grün, Rot, Alpha.
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
