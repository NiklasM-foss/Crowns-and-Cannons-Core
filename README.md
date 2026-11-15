<img src="docs/logo.png" width="128" align="right" alt="Logo">

# Crowns Core

Server-Mod für das Minecraft-Nationen-Rollenspiel **Crowns and Cannons**
(Forge 47.4.28, Minecraft 1.20.1). Sie bildet die Regeln des Projekts direkt im
Spiel ab: Nationen, Kriegserklärungen, Staatskasse, Beweisakte, Kolonialbehörde,
Wahlen, Visa, Post und den Aktivitätszähler. Seit 1.2.0 kommen Spielermodi (RP,
Farm, AFK), ein Blockprotokoll mit Rollback nach dem Vorbild von CoreProtect und
eine Rechteverwaltung nach dem Vorbild von LuckPerms dazu.

Reine Server-Mod: Clients brauchen sie nicht, der Modcode des Packs bleibt
unverändert. Alle Anbindungen an andere Mods sind optional.

**Download:** [CurseForge](https://www.curseforge.com/projects/1802743) ·
Quellcode auf [GitHub](https://github.com/NiklasM-foss/Crowns-and-Cannons-Core)

## Module und Commands

Die vollständige Liste mit Rollen steht in [docs/Befehle.pdf](docs/Befehle.pdf). Im
Spiel zeigt `/cccore list` die Befehle, die man selbst nutzen darf, und
`/cccore list <Modul>` ein einzelnes Modul. Befehle ohne Berechtigung tauchen weder
in der Liste noch in der Tab-Vervollständigung auf (siehe Rechte).

| Modul | Commands | Regelwerk |
|---|---|---|
| Hilfe | `/cccore`, `/cccore list [Seite,Modul]` | |
| Spielermodus | `/modus`, `rp`, `farm`, `afk <20:00 oder 90m>`, `liste`, `grund <Spieler> <Grund>` | Modusregel |
| Protokoll | `/cccore admin inspect`, `lookup <Parameter>`, `near [Radius]`, `seite <Nr>`, `rollback <Parameter>`, `restore <Parameter>`, `anwenden`, `abbrechen`, `undo`, `purge t:<Zeit>`, `status` | Griefing, Gericht |
| Rechte | `/cccore perms info`, `listgroups`, `creategroup`, `deletegroup`, `user <Spieler> ...`, `group <Gruppe> ...`, `track <Laufbahn> ...`, `search <Knoten>`, `reload` | Ränge |
| Nationen | `/nation liste`, `info`, `beitreten <Land>`, `bestaetigen <Spieler>`, `ablehnen`, `verlassen`, `rauswerfen`, `beamter <Spieler>`, `wahlmodus <KEINE,DIREKT,PARLAMENT> [Amtszeit]`; Admin: `anlegen <Name> <Kürzel>`, `loeschen`, `staatschef <Land> <Spieler>`, `setzen <Spieler> <Land>`, `umbenennen` | Grundlage |
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
| Gebiete intern | `/gebiet hier`, `claim [Radius]`, `freigeben [Radius]`, `anzahl` | nur ohne OPAC nötig |

Admin ist, wer OP-Level 2 hat, in der Config unter `admins` steht oder das Recht
`crownscore.admin` hat. Ein ausdrückliches `crownscore.admin = false` nimmt die
Adminrechte auch einem OP.

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
Staatschef, Admin) beziehungsweise die normale OP-Prüfung. `true` gibt frei,
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

Spielstand in `world/data/crownscore.dat`, Blockprotokoll in
`world/crownscore/protokoll.mv.db`, Rechte in `world/crownscore/permissions.json`.

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
und daraus entsteht die PDF. Nach einer Änderung an Befehlen die Datei anpassen und
die PDF neu bauen (braucht Python 3 und Chrome oder Edge):

```
python tools/befehle_pdf.py
```

Testserver aus der IDE: `./gradlew runServer` (EULA in `run/eula.txt` bestätigen).

Selbsttest: `./gradlew selftestJar` baut `crownscore-<Version>-selftest.jar`. Zusammen
mit der Mod auf einen Testserver legen und in der Konsole `/cctest` eingeben. Zwei
Fake-Spieler spielen dann Kampf und Modi, Blockprotokoll, Rollback, Restore, Undo,
Inspektor, Befehlssichtbarkeit und Rechte durch, das Ergebnis steht als PASS/FAIL
im Log. Die Testdatei gehört nicht auf den echten Server.

## Installation auf dem Server

JAR in den `mods`-Ordner des Servers legen, neu starten, Config anpassen, dann
Nationen anlegen:

```
/nation anlegen Frankreich FRA
/nation staatschef FRA <Spieler>
```

Spieler treten mit `/nation beitreten FRA` bei, der Staatschef bestätigt.
