<img src="docs/logo.png" width="128" align="right" alt="Logo">

# Crowns Core

Server-Mod für das Minecraft-Nationen-Rollenspiel **Crowns and Cannons**
(Forge 47.4.28, Minecraft 1.20.1). Sie bildet die Regeln des Projekts direkt im
Spiel ab: Nationen, Kriegserklärungen, Staatskasse, Beweisakte, Kolonialbehörde,
Wahlen, Visa, Post und den Aktivitätszähler. Seit 1.2.0 kommen Spielermodi (RP,
Farm, AFK), ein Blockprotokoll mit Rollback nach dem Vorbild von CoreProtect,
eine Rechteverwaltung nach dem Vorbild von LuckPerms und die Erdkarte mit allen
Ländern auf Xaero's Minimap dazu. Seit 1.3.0 trägt jeder Bürger die Flagge seiner
Nation als Umhang. 1.4.0 bringt die Flaggen auf Xaero's Karten und das Kaufen und
Besetzen von Ländern, 1.5.0 Städte mit Villager-Händlern, Eroberung Stadt für Stadt
und ein Postfach für jeden Spieler.

Die Regeln laufen auf dem Server. Im Client ist die Mod nur für die Flaggen-Umhänge
nötig; Spieler ohne sie können trotzdem mitspielen. Alle Anbindungen an andere Mods
sind optional.

- **Alle Befehle:** [docs/commandliste.html](docs/commandliste.html), mit Suche und Filter nach Rolle
  ([im Browser öffnen](https://htmlpreview.github.io/?https://github.com/NiklasM-foss/Crowns-and-Cannons-Core/blob/main/docs/commandliste.html)),
  zum Drucken als [docs/Befehle.pdf](docs/Befehle.pdf)
- **Alle Länderkürzel mit Flagge:** [docs/laender.html](docs/laender.html)
- **Änderungen pro Version:** [CHANGELOG.md](CHANGELOG.md)

**Download:** [CurseForge](https://www.curseforge.com/projects/1802743) ·
Quellcode auf [GitHub](https://github.com/NiklasM-foss/Crowns-and-Cannons-Core)

## Module und Commands

Die vollständige Liste mit Rollen und Beschreibung steht in
[docs/commandliste.html](docs/commandliste.html) (durchsuchbar) und
[docs/Befehle.pdf](docs/Befehle.pdf). Im
Spiel zeigt `/cccore list` die Befehle, die man selbst nutzen darf, und
`/cccore list <Modul>` ein einzelnes Modul. Befehle ohne Berechtigung tauchen weder
in der Liste noch in der Tab-Vervollständigung auf (siehe Rechte).

| Modul | Commands | Regelwerk |
|---|---|---|
| Hilfe | `/cccore`, `/cccore list [Seite,Modul]` | |
| Spielermodus | `/modus`, `rp`, `farm`, `afk <20:00 oder 90m>`, `liste`, `grund <Spieler> <Grund>` | Modusregel |
| Protokoll | `/cccore admin inspect`, `lookup <Parameter>`, `near [Radius]`, `seite <Nr>`, `rollback <Parameter>`, `restore <Parameter>`, `anwenden`, `abbrechen`, `undo`, `purge t:<Zeit>`, `status` | Griefing, Gericht |
| Rechte | `/cccore perms info`, `listgroups`, `creategroup`, `deletegroup`, `user <Spieler> ...`, `group <Gruppe> ...`, `track <Laufbahn> ...`, `search <Knoten>`, `reload` | Ränge |
| Nationen | `/nation liste`, `info`, `beitreten <Land>`, `bestaetigen <Spieler>`, `ablehnen`, `verlassen`, `rauswerfen`, `beamter <Spieler>`, `wahlmodus <KEINE,DIREKT,PARLAMENT> [Amtszeit]`; Admin: `anlegen <Name> <Kürzel>`, `loeschen`, `staatschef <Land> <Spieler>`, `setzen <Spieler> <Land>`, `umbenennen`, `annektieren <Land> <Nation>`, `weltkarte import,info,minimap` | Grundlage |
| Länder einnehmen | `/nation land preis <Land>`, `land kaufen <Land>` (Staatschef), `besetzen`, `besetzen abbrechen [Nr]`, `besetzungen`, `aufgeben [Nr]` | Krieg, Besitzrecht |
| Städte | `/stadt`, `liste [Nation]`, `pruefen`, `gruenden <Name>`, `wappen <Stadt> [Code,entfernen]`; Admin: `/cccore staedte [annehmen,ablehnen,uebertragen]`, `/cccore wappen annehmen,ablehnen` | Städte |
| Villager-Händler | `/haendler`, `kaufen <Angebot> [geld,rohstoffe]`; Admin: `/cccore haendler setzen,entfernen` | Villager |
| Postfach | `/postfach` | |
| Flaggen | `/nation flagge`, `flagge anwenden <Code>`, `flagge entfernen`, `/umhang [an,aus]`; Admin: `/cccore flaggen [annehmen,ablehnen,vorschau]` | |
| Kriege | `/krieg erklaeren <Gegner> <Kriegsart> <Grund>`, `liste`, `arten`, `frieden <Gegner>` (beide Seiten), `wechseln <Gegner> <Kriegsart>` (beide Seiten); Admin: `beenden <Nr> <Ergebnis>` | §4.1, §4.2 |
| Duell | `/duell <Spieler>`, `annehmen`, `abbrechen` | Zweikampf ohne Krieg |
| Beweisakte | `/akte spieler <Spieler> [Seite]`, `gebiet <Land>`, `typ <Typ>`, `neueste`, `export <Spieler> [Anzahl]`, `loeschen <Spieler>` | §3, §4.5, §5 |
| Aktivität | `/aktivitaet [Spieler]`, `/aktivitaet bericht`, `/pause <Spieler> <Tage>` | §7.2, §7.3 |
| Staatskasse | `/staatskasse [stand <Land>]`, `einzahlen <Betrag>`, `auszahlen <Betrag>` (Staatschef), `ueberweisen <Land> <Betrag>`, `konto`; Admin: `setzen`, `geldgeben` | Länderkonto |
| Darlehen | `/darlehen [stand]`, `bedingungen`, `aufnehmen <Betrag>`, `tilgen <Betrag>` | Zentralbank |
| Kolonien | `/kolonie kosten`, `liste`, `info <Name>`, `expedition <Name> <herrscher,frei>`, `weiter <Name>`, `aufgeben`; Behörde: `genehmigen`, `ablehnen <Name> <Grund>`, `radius`, `gebuehr` | #besetztes-gebiet, #unbesetztes-land |
| Wegsteine | `/wegstein liste`, `freigeben <Land>`, `entziehen <Land>` | #waystones |
| Wahlen | `/wahl starten <Minuten> <amt true,false> <Kandidaten>`, `stimme <Name>`, `status`; `/abstimmung starten <Minuten> <Titel>`, `ja`, `nein`, `enthaltung`, `status` | #wahlen |
| Visa | `/visum erteilen <Spieler> [Tage]`, `entziehen`, `liste`, `meine` | §5.1 |
| Post | `/post senden <Land> <Text>`, `lesen`, `abgefangen`, `gesendet` | Spionage |
| Verträge | `/vertrag hinterlegen <Titel>` (Buch in der Hand), `liste`, `lesen <Nr>`, `pruefen <Nr>`, `export <Nr>` | §2 |
| Gebiete | `/gebiet hier`, `claim [Radius]`, `freigeben [Radius]`, `anzahl`; Admin: `zuweisen <Nation> [Radius]`, `zuruecksetzen [Radius]` | Claims nur ohne OPAC nötig |

Admin ist, wer OP-Level 2 hat, in der Config unter `admins` steht oder in der
Rechteverwaltung der Mod (`/cccore perms`) das Recht `crownscore.admin` hat. Ein
ausdrückliches `crownscore.admin = false` nimmt die Adminrechte auch einem OP. Ein
in LuckPerms gesetztes `crownscore.admin` zählt nicht, die Mod hat ihr eigenes
Rechtesystem.

### Was automatisch passiert

- **PvP** ist nur zwischen Nationen im Krieg erlaubt (Kriegsarten mit Kampf,
  siehe Config), sonst nur im Duell. Am Spawn nie. Staatenlose kämpfen nicht.
- **Blitzkrieg** verfällt, wenn innerhalb der Frist kein Angriff stattfindet.
- **Akte**: Blockabbau, Blocksetzen, Feuer, Kistenöffnen in fremdem Gebiet,
  Tötungen, Camping vor fremden Basen (30 Minuten) und Einreise ohne Visum
  werden protokolliert, nicht verhindert. Plündern und Scammen bleiben erlaubt.
- **Visum**: Wer länger als 30 Sekunden am Boden in fremdem Gebiet steht, löst
  eine Meldung an die Besitzernation aus. Überflüge zählen nicht.
- **Aktivitätsbericht** jede Woche (Standard Sonntag 21 Uhr) per Discord.
- **Darlehen** buchen wöchentlich eine Rate aus der Staatskasse ab, Verzug wird
  öffentlich gemacht.
- **Wahlen** enden automatisch, bei Amtswahlen wird der Sieger Staatschef.
- **Nation im Chat**: Vor dem Spielernamen steht die Nation, z.B.
  `[Saudi-Arabien] [Gamemaster] Niklas`. Das gilt auch für das Namensschild und
  Todesmeldungen. Ganzer Name oder Kürzel und die Farbe stehen in der Config.
- **Spielermodus**: Nach jedem Einloggen ist man im RP-Modus. Im Farm- oder
  AFK-Modus darf nur angreifen, wer vorher `/modus grund <Spieler> <Grund>`
  einträgt (gilt 30 Minuten, steht in der Akte). Wer angreift, landet selbst im RP
  und kann 60 Sekunden lang nicht wechseln. AFK läuft wie eine Parkscheibe bis zu
  einer Uhrzeit und fällt dann zurück auf RP. Der Modus steht in der Tabliste.
- **Blockprotokoll**: Abbau, Bauen, Explosionen mit Verursacher, Eimer, Feuer,
  Kisteninhalt, Tötungen, Chat, Befehle, Ein- und Ausloggen, fallengelassene und
  aufgehobene Items sowie Türen, Hebel und Knöpfe.

## Weltkarte und Ländergrenzen

Die Server-Map ist eine Erdkarte (49152 × 24576 Blöcke, Plattkarte: Länge und Breite
gleichmäßig auf x und z, 0/0 am Schnittpunkt von Äquator und Nullmeridian, Norden ist
−z). Die Mod kennt die Grenzen von 238 Ländern aus Natural Earth (gemeinfrei),
mit deutschen Namen. Ein Chunk entspricht etwa 13 km; Liechtenstein, San Marino und
der Vatikan sind dafür zu klein, die Antarktis ist ausgelassen.

- `/nation weltkarte import` legt alle Länder als Nationen an (Kürzel wie `DEU`).
  Bestehende Nationen werden über Kürzel oder Namen verknüpft statt doppelt angelegt.
- **Gebiet:** Wo kein eigener Claim gilt, gehört ein Chunk dem Land auf der Karte,
  aber nur, wenn diese Nation gespielt wird (Staatschef oder Mitglieder).
  Unbespielte Länder sind freies Land: keine Visum- oder Camping-Meldungen, Kolonien
  zählen dort als unbesetztes Land.
- `/nation weltkarte minimap flaeche` zeigt die Länder auf Xaero's Minimap und
  Weltkarte über Open Parties and Claims. Jede Nation hat ein **Staatskonto**, dem
  ihre Chunks gehören (Name und Farbe des Landes, ohne Bauschutz), und eine eigene
  **Party**. Besitzer der Party ist der Staatschef, solange es keinen gibt das
  Staatskonto; die Bürger sind Mitglieder, Beamte haben den Rang Admin. Neuer
  Staatschef, neue Bürger oder eine Umbenennung werden alle 30 Sekunden nachgezogen.
  Aufbau für die ganze Erde etwa 40 Sekunden (rund 1,14 Millionen Chunks, etwa 17 MB
  OPAC-Daten, beim Serverstart in unter 2 Sekunden geladen). Weil alles Land
  vergeben ist, setzen Spieler dort keine eigenen OPAC-Claims; bestehende Claims von
  Spielern bleiben unangetastet. Andere Modi: `umriss` (nur Landgrenzen und
  Küsten), `grenze` (nur Landgrenzen), `aus` (alles entfernen).
- **Eroberung:** `/nation annektieren <Land> <Nation>` gibt ein ganzes Land an eine
  Nation. Seine Chunks gehen an deren Staatskonto, die Grenze dazwischen verschwindet
  auf der Karte, der Staatschef des Eroberers hat das Land in seiner Party. Mit dem
  Stammland als Ziel bekommt eine Nation ihr Land zurück. Für Teileroberungen gibt
  `/gebiet zuweisen <Nation> [Radius]` einzelne Chunks ab, `/gebiet zuruecksetzen`
  gibt sie dem Land auf der Karte zurück.
- **Flaggen auf der Karte:** Wer Crowns Core im Client hat, sieht auf Xaero's World
  Map und Minimap statt der einfarbigen Fläche die Flagge der Nation, die das Land
  hält (eigene Flagge, sonst Landesflagge). Die Flagge ist über das Land gespannt und
  mittig zugeschnitten, an Grenzen zu anderen Besitzern liegt eine dunkle Linie, der
  Name der Nation steht weiter im Tooltip. Technisch hängt Crowns Core einen eigenen
  Highlighter in die Listen beider Karten und blendet dort die OPAC-Fläche der
  Staatskonten aus (`client/xaero/`), Xaero selbst bleibt unverändert. Config
  `[kartenflaggen]`: `aktiv`, `deckkraft` (Prozent), `breite` (Pixel pro Flagge).
- Open Parties and Claims muss auch im Modpack der Spieler sein, sonst zeigt Xaero
  nichts an.
- **Kalibrieren:** `/gebiet hier` zeigt Land und Grad. Passt das an einer bekannten
  Stadt nicht, in der Config `[weltkarte]` `mitteX`, `mitteZ` und `bloeckeProGrad`
  nachstellen und die Minimap-Anzeige neu aufbauen.
- Die Kartendaten erzeugt `tools/weltkarte/make_weltkarte.py`, die Vorschau liegt in
  `docs/weltkarte.png`.

## Länder einnehmen (`/nation land`, `/nation besetzen`)

- **Kaufen:** Freie Länder (Nation ohne Staatschef und Bürger) kauft der Staatschef
  mit `/nation land kaufen <Land>` aus der Staatskasse, nach einer Bestätigung.
  Preis = `kaufGrundpreis` + `kaufProChunk` × Größe in Chunks. `/nation land preis
  <Land>` zeigt Besitzer, Preis und ob die eigene Nation es darf.
- **Nähe:** Gekauft oder besetzt wird nur, was höchstens `nachbarAbstandChunks` vom
  eigenen Gebiet entfernt liegt (Standard 16, damit gehen auch schmale Meerengen;
  0 = direkt angrenzend, −1 = überall).
- **Besetzen:** Ein Beamter stellt sich im Krieg in ein Land der gegnerischen Nation
  (hat das Land Städte, in ein Stadtgebiet) und gibt `/nation besetzen` ein. Die
  Gebühr geht aus der Staatskasse, an seiner Position steht der Besatzungsblock
  (Standard roter Banner). Hält er `haltezeitMinuten`, wechselt die Stadt bzw. das
  Land wie bei `/nation annektieren`. Wird er abgebaut oder gesprengt, ist die
  Besetzung gescheitert und die Gebühr weg. Die Zeit läuft nur, solange jemand aus
  der angegriffenen Nation online ist (`nurWennVerteidigerOnline`). Stand alle
  15 Minuten an beide Seiten, `/nation besetzungen` listet alle, auf der Karte zeigt
  eine rote Linie den Angriff.
- **Aufgeben:** Ist kein Angreifer mehr innerhalb von `aufgabeRadius` Blöcken um die
  Stadtmitte, gilt die Besetzung nach `angreiferKulanzSekunden` als aufgegeben.
  Bleiben die Verteidiger fern, obwohl jemand von ihnen online ist, fällt die Stadt
  nach `verteidigerKulanzMinuten`. `/nation aufgeben` geht für beide Seiten.
- **Verteidiger am Banner** (`verteidigerrecht`, Standard an): Es wird kein
  Besatzungsblock gesetzt. Gekämpft wird am Hauptbanner der Stadt (`/stadt banner`,
  sonst Stadtmitte), bei einem Land ohne Städte an der Stelle, an der die Besetzung
  beginnt. Am Banner heißt: `verteidigerRadius` Blöcke waagerecht, `verteidigerHoehe`
  nach oben und unten.
  - Beginnen geht nur am Banner, nur wenn jemand aus der angegriffenen Nation im RP
    online ist, und nicht, solange ein Verteidiger im RP am Banner steht.
  - Läuft die Besetzung, steht die Uhr, solange ein Verteidiger am Banner ist. Ist
    `angreiferAmBannerSekunden` lang kein Angreifer am Banner, ist sie gescheitert.
  - Tötet ein Verteidiger im Kampfgebiet (`aufgabeRadius`) einen Angreifer, ist sie
    gescheitert (`verteidigerKillBeendet`). Wer dort fällt, blockiert sie nicht mehr.
  - Anreise: Die Verteidiger haben `anreiseMinutenPro100Bloecke` pro 100 Blöcke Abstand
    (nächster Verteidiger im RP beim Start, höchstens `anreiseMaxMinuten`), erst danach
    läuft `verteidigerKulanzMinuten`.
  - Nach dem Start läuft die Uhr auch weiter, wenn die Verteidiger offline gehen. Wird
    einer von ihnen gekickt oder gebannt, steht sie, bis er wieder online ist.
  - Den Banner ins Hauptgebäude zu stellen und sich nicht einzumauern, regeln die
    Serverregeln.
- **Fremde Claims:** Gesetzt wird nicht im eigenen Gebiet und nicht in fremden
  Spieler-Claims. Claimt nach dem Setzen ein Dritter den Block, hält die Besetzung an,
  bis der Claim weg ist (die Angreifer müssen in der Nähe bleiben, die Kulanz der
  Verteidiger steht). Claimen ihn die Angreifer selbst, scheitert sie.
- **Schutz:** Besetzen nur im Krieg mit erlaubtem Kampf, neue Nationen sind
  `schutzNeueNationTage` geschützt, das Stammland ist erst einnehmbar, wenn die
  Nation sonst nichts mehr hat, ein eingenommenes Land ist `sperreNachEroberungTage`
  gesperrt, eine Nation hat höchstens `gleichzeitigeBesetzungen` laufende Besetzungen.

## Städte, Villager-Händler und Postfach

- **Stadt beantragen:** `/stadt gruenden <Name>` am Standort oder Rechtsklick auf der
  World Map („Stadt hier beantragen“, eingehängt per Mixin in Xaero's Rechtsklick-Menü,
  `mixin/GuiMapMixin.java`). Geprüft werden `stadt.mindestBetten` im Umkreis
  `stadt.radius`, eine Glocke, `stadt.mindestAbstand` zur nächsten Stadt und das eigene
  Gebiet. Pro Nation ist ein Antrag gleichzeitig offen.
- **Bestätigen:** Admins bekommen den Antrag im Spiel (Knöpfe Hinreisen, Annehmen,
  Ablehnen), im `/postfach` und im Discord (Webhook `discord.antraege`, sonst News). Ist
  der Antrags-Dienst eingerichtet (`antraege.schluessel`), steht im Discord ein Link zum
  Entscheiden im Browser. Die Mod holt die Entscheidungen alle 30 Sekunden selbst ab.
- **Karte:** Stadtgebiete (`stadt.gebietChunks` um die Mitte) bekommen auf beiden Karten
  einen goldenen Rahmen, wartende Anträge einen grauen gestrichelten.
- **Eroberung:** In Ländern mit Städten steht der Besatzungsblock in einem Stadtgebiet.
  Die eroberte Stadt geht samt Gebiet an den Angreifer, wer alle Städte eines Landes
  hält, bekommt das ganze Land.
- **Villager-Händler:** `/cccore haendler setzen`, Kaufmenü per Rechtsklick (Linksklick =
  Geld, Rechtsklick = Rohstoffe), oder `/haendler kaufen`. Angebote, Grenzen pro
  Stadtanzahl, `unbegrenztAb`, `aufschlagProzent` und `erholungProzentProTag` in
  `[villagerkauf]`.
- **Postfach:** `/postfach` als Truhenmenü. Klick liest, Shift-Klick löscht.

### Antrags-Dienst (`editor/antraege/`)

Läuft im Docker-Compose des Rechte-Editors (Python-Container, Seite unter
`/antraege/`). In `editor/.env` `ANTRAEGE_SCHLUESSEL=<zufälliger Wert>` setzen, denselben
Wert in der Mod-Config `antraege.schluessel`, dann `docker compose up -d`. Jeder Antrag
hat ein eigenes Token, das nur im Discord-Link steht (im Teil nach `#`, landet also in
keinem Server-Log). Der Discord-Kanal für `antraege` sollte nur für Admins sichtbar sein.

## Flaggen als Umhang (`/nation flagge`)

Jeder Bürger trägt die Flagge seiner Nation als Umhang. Ein Umhang ist hochkant,
die Flagge wird deshalb gedreht wie ein Banner am Mast (Mastseite oben) und füllt
die Fläche, Überstand wird mittig abgeschnitten. Die Textur hat die achtfache
Auflösung eines normalen Umhangs (512 × 256), Wappen und Schrift bleiben scharf.

- **Landesflaggen:** Ohne eigene Flagge trägt eine Nation die echte Flagge ihres
  Landes auf der Weltkarte. Alle 238 Flaggen stecken in der Mod (flagcdn.com,
  gemeinfrei, erzeugt mit `tools/flaggen/make_flaggen.py`).
- **Eigene Flagge:** Der Staatschef öffnet mit `/nation flagge` die Upload-Seite im
  Browser (Ordner `editor/flagge/`, läuft im selben Container wie der Rechte-Editor),
  lädt ein Bild hoch, sieht den Umhang in einer 3D-Vorschau und bekommt einen Code.
  `/nation flagge anwenden <Code>` holt das Bild auf den Server.
- **Freigabe:** Neue Flaggen warten, bis ein Admin sie mit `/cccore flaggen`
  prüft (Bild im Browser, Vorschau auf dem eigenen Rücken, annehmen oder ablehnen).
  Abschaltbar mit `umhang.freigabe = false`.
- `/umhang aus` schaltet den Flaggen-Umhang für sich selbst ab, dann ist ein
  normaler Minecraft-Umhang wieder sichtbar. `/nation flagge entfernen` geht zurück
  zur Landesflagge.
- **Client:** Sehen kann die Umhänge, wer Crowns Core im Modpack hat. Der Server
  schickt jede Textur nur einmal pro Spieler und danach nur noch Änderungen.
  Elytren und die Skin-Einstellung „Umhang aus“ werden beachtet.

## Spielermodus

| Modus | Bedeutung | Angriff |
|---|---|---|
| RP | normales Rollenspiel, Standard nach dem Einloggen | nach den Kriegsregeln |
| Farm | Ressourcen sammeln, RP vorbereiten | nur mit eingetragenem Grund |
| AFK | an der AFK-Farm, mit Endzeit wie eine Parkscheibe | nur mit eingetragenem Grund |

Duelle gelten unabhängig vom Modus, weil beide zugestimmt haben.

## Blockprotokoll (`/cccore admin`)

Funktioniert wie CoreProtect. Gespeichert wird in einer eingebetteten H2-Datenbank
unter `world/crownscore/protokoll.mv.db`, geschrieben wird gesammelt im Hintergrund.

Parameter für `lookup`, `rollback` und `restore`:

| Parameter | Bedeutung | Beispiel |
|---|---|---|
| `u:` | Spieler, mehrere mit Komma, Verursacher wie `#tnt` oder `#creeper` | `u:Ghast0607,#creeper` |
| `t:` | Zeitraum zurück | `t:2h30m`, `t:3d`, `t:1w` |
| `r:` | Radius um dich, oder `#world`, `#global`, `#overworld`, `#nether`, `#end` | `r:20` |
| `a:` | Aktion: `block`, `+block`, `-block`, `container`, `+container`, `-container`, `item`, `kill`, `chat`, `command`, `session`, `click` | `a:-block` |
| `b:` | nur diese Blöcke, Items oder Wesen | `b:diamond_ore,chest` |
| `e:` | ausschließen, Blöcke oder Spieler | `e:dirt` |
| `#preview` | Rollback erst nur für dich anzeigen, dann `anwenden` oder `abbrechen` | |
| `#count` | nur die Anzahl der Treffer | |

Ein Rollback ohne `r:` gilt für 10 Blöcke um dich. Für eine ganze Welt braucht er
zusätzlich `u:`, `t:` oder `b:`. Zurückgesetzt werden Blöcke samt Inhalt,
Kisteninhalte und getötete Tiere. Chat, Befehle und Klicks sind nur zum Nachsehen.
`undo` macht den eigenen letzten Rollback oder Restore rückgängig. Zurückgesetzte
Einträge erscheinen in der Abfrage durchgestrichen.

## Rechte (`/cccore perms`)

Funktioniert wie LuckPerms. Gespeichert wird in `world/crownscore/permissions.json`,
die Datei lässt sich auch von Hand bearbeiten und mit `/cccore perms reload` laden.

- **Gruppen** haben ein Gewicht, erben von anderen Gruppen und tragen Rechte, ein
  Präfix und ein Suffix. Jeder Spieler ist mindestens in `default`.
- **Reihenfolge**: Spielerrechte vor Gruppenrechten, Gruppen nach Gewicht, im
  selben Halter der genaue Knoten vor dem Platzhalter (`a.b.c`, `a.b.*`, `a.*`, `*`).
- **Befristet** mit einer Dauer wie `7d`, **pro Dimension** mit einer Dimension wie
  `minecraft:the_nether`.
- **Laufbahnen** (Tracks) für Ränge: `promote` und `demote` schieben einen Spieler
  eine Gruppe weiter.
- **Präfix** erscheint im Chat und in der Tabliste, Farben mit `&`.

Welche Knoten es gibt:

| Knoten | Wirkung |
|---|---|
| `crownscore.<Befehl>.<Unterbefehl>` | eigener Befehl, z.B. `crownscore.krieg.erklaeren`, `crownscore.cccore.admin.rollback` |
| `crownscore.admin` | Adminrechte der Mod |
| `command.<name>` | jeder Befehl, auch Vanilla und andere Mods, z.B. `command.gamemode` |
| beliebige Forge-Knoten | Mods, die die Forge-PermissionAPI nutzen |

Ohne Eintrag gilt die Rolle aus der Befehlsliste (Alle, Bürger, Beamte,
Staatschef, Admin) beziehungsweise die normale OP-Prüfung. Ein Recht kann einen
Befehl sperren oder sichtbar machen, ersetzt aber nicht die Rolle im Spiel: Wer
`crownscore.krieg.erklaeren` bekommt, sieht den Befehl, Krieg erklären darf
trotzdem nur der Staatschef. `crownscore.admin` wirkt nur, wenn es ausdrücklich
gesetzt ist, `crownscore.*` oder `*` machen niemanden zum Admin. `true` gibt frei,
`false` sperrt. Was jemand nicht nutzen darf, sieht er auch nicht in der
Tab-Vervollständigung. Der Server schickt den Befehlsbaum bei jeder Rechte- oder
Rollenänderung neu.

**Im Browser einstellen:** `/cccore perms editor` lädt die Rechte in den eigenen
Rechte-Editor (der LuckPerms-Web-Editor mit Crowns-Core-Logo, Ordner
[`editor/`](editor/README.md), läuft per Docker auf dem eigenen Server). Dort
bearbeiten, speichern und `/cccore perms applyedits <Code>` eingeben. Übernommen
wird nur, was im Editor geändert wurde; was inzwischen im Spiel geändert wurde,
bleibt erhalten. Gruppen, Laufbahnen, Präfixe, Gewicht, Ablaufzeiten und die
Dimension als Kontext `world` gehen in beide Richtungen.

Damit andere Mods die Rechte nutzen, trägt sich die Mod beim Start als
Forge-Permission-Handler ein (`permissionHandler` in `forge-server.toml`), sofern
dort noch der Standard steht. Ein anderer Handler wie LuckPerms wird nicht
überschrieben.

## Anbindungen (alle optional)

| Mod | Wofür | Wenn sie fehlt |
|---|---|---|
| Money for Everyone | Spielerkonten für Ein- und Auszahlungen, Zoll | interne Geldbörse in den Mod-Daten |
| Waystones (Balm) | Teleport-Sperre für fremde Wegsteine | kein Wegstein-Modul |
| Open Parties and Claims | Gebietsgrenzen aus den Claims | nur interne Gebiete per `/gebiet claim` |

Gebiete: Erst werden die internen Chunks geprüft, dann die OPAC-Claims. Der
Besitzer eines OPAC-Claims wird über seine Nation zugeordnet.

## Config

Liegt pro Welt unter `world/serverconfig/crownscore-server.toml` und wird beim
ersten Start mit Kommentaren erzeugt. Wichtig:

- `weltkarte.aktiv`, `mitteX`, `mitteZ`, `bloeckeProGrad`, `chunksProTick`.
- `chat.nationVorName`, `chat.nationAnzeige` (`NAME` oder `KUERZEL`),
  `chat.nationFarbe` (z.B. `&6`).
- `discord.kriegsnews`, `discord.gericht`, `discord.aktivitaet`, `discord.news`:
  Webhook-URLs der Discord-Kanäle.
- `krieg.spawnRadius`, `krieg.kampfErlaubt`, `krieg.blitzkriegMinuten`.
- `akte.campingMinuten`, `visum.visumSekunden`.
- `aktivitaet.wocheStunden`, `wochenendeStunden`, `berichtWochentag`.
- `darlehen.*`, `kolonie.*`, `wegstein.zoll`, `post.abfangProzent`.
- `modus.grundMinuten`, `kampfSperreSekunden`, `afkMaxStunden`,
  `afkZaehltAlsAktivitaet`, `tabliste`.
- `protokoll.aktiv`, `chat`, `befehle`, `items`, `klicks`, `rollbackProTick`,
  `maxErgebnisse`.
- `rechte.forgeHandlerSetzen`, `befehlsRechte`, `praefixAnzeigen`, `editorUrl`,
  `bytebinUrl`.
- `umhang.aktiv`, `landesflaggen`, `freigabe`, `drehung` (`RECHTS` = Mastseite
  oben, `LINKS`), `seiteUrl` (leer = `editorUrl` mit `flagge/` statt `editor/`).
- `kartenflaggen.aktiv`, `deckkraft`, `breite`.
- `stadt.radius`, `gebietChunks`, `mindestBetten`, `glockeNoetig`, `mindestAbstand`,
  `nurImEigenenGebiet`.
- `villagerkauf.aktiv`, `angebote`, `grenzen`, `unbegrenztAb`, `aufschlagProzent`,
  `erholungProzentProTag`.
- `antraege.webUrl`, `schluessel`, `discord.antraege`.
- `eroberung.kaufen`, `kaufGrundpreis`, `kaufProChunk`, `nachbarAbstandChunks`,
  `besetzen`, `nurImKrieg`, `nurKampfkrieg`, `besatzungGrundgebuehr`,
  `besatzungProChunk`, `haltezeitMinuten`, `nurWennVerteidigerOnline`,
  `schutzNeueNationTage`, `sperreNachEroberungTage`, `stammlandGeschuetzt`,
  `gleichzeitigeBesetzungen`, `besatzungsblock`, `aufgabeRadius`,
  `angreiferKulanzSekunden`, `verteidigerKulanzMinuten`, `verteidigerrecht`,
  `verteidigerRadius`, `verteidigerHoehe`, `verteidigerKillBeendet`,
  `angreiferAmBannerSekunden`, `anreiseMinutenPro100Bloecke`, `anreiseMaxMinuten`.

Spielstand in `world/data/crownscore.dat`, Blockprotokoll in
`world/crownscore/protokoll.mv.db`, Rechte in `world/crownscore/permissions.json`,
eigene Flaggen in `world/crownscore/flaggen/`.

## Bauen

Voraussetzung: JDK 17. Dann im Projektordner:

```
./gradlew build
```

Die JAR liegt danach in `build/libs/` (die Datei ohne Zusatz, `-slim` ist ohne
eingebettete Datenbank). Die H2-Datenbank steckt per JarJar in der Mod, der Server
braucht nichts zusätzlich. Die Abhängigkeiten zum Kompilieren
(Money for Everyone, Waystones, Balm, OPAC) kommen über CurseMaven und Modrinth
und sind in `build.gradle` auf die Versionen des Modpacks gepinnt.

Befehlsliste: Alle Befehle stehen einmal in
`src/main/resources/crownscore/commands.tsv`. Daraus liest `/cccore list` im Spiel,
und daraus entstehen `docs/commandliste.html` und `docs/Befehle.pdf`. Nach einer
Änderung an Befehlen und vor jedem Release die Datei anpassen und beide neu bauen
(braucht Python 3, für die PDF zusätzlich Chrome oder Edge; `--html` baut nur die
HTML-Seite):

```
python tools/befehle_pdf.py
```

Testserver aus der IDE: `./gradlew runServer` (EULA in `run/eula.txt` bestätigen).

Selbsttest: `./gradlew selftestJar` baut `crownscore-<Version>-selftest.jar`. Zusammen
mit der Mod auf einen Testserver legen und in der Konsole `/cctest` eingeben. Zwei
Fake-Spieler spielen dann Kampf und Modi, Blockprotokoll, Rollback, Restore, Undo,
Inspektor, Befehlssichtbarkeit und Rechte durch, das Ergebnis steht als PASS/FAIL
im Log. Weitere Suiten: `/cctest eroberung`, `/cctest karte`, `/cctest flagge`
(nacheinander starten, nie gleichzeitig). Die Testdatei gehört nicht auf den echten
Server.

## Installation auf dem Server

JAR in den `mods`-Ordner des Servers legen, neu starten, Config anpassen, dann
Nationen anlegen:

```
/nation anlegen Frankreich FRA
/nation staatschef FRA <Spieler>
```

Spieler treten mit `/nation beitreten FRA` bei, der Staatschef bestätigt.

## Lizenz

Crowns Core steht ab Version 1.5.3 unter der **GNU Lesser General Public License, Version 3** (LGPL-3.0-only),
siehe `COPYING.LESSER` und `COPYING` (GPL-3.0, auf der die LGPL aufbaut). Frühere Versionen bis einschließlich 1.5.2
wurden als „All Rights Reserved“ veröffentlicht.

Enthaltene Fremdteile behalten ihre eigenen Lizenzen: H2 Database (MPL 2.0 / EPL 1.0, im Jar eingebettet),
LuckPermsWeb im Ordner `editor/` (MIT), skinview3d in `editor/flagge/vendor/` (MIT), Landesflaggen von flagcdn.com und
Ländergrenzen von Natural Earth (gemeinfrei).
