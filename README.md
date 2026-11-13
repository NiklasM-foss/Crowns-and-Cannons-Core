<img src="docs/logo.png" width="128" align="right" alt="Logo">

# Crowns Core

Server-Mod für das Minecraft-Nationen-Rollenspiel **Crowns and Cannons**
(Forge 47.4.28, Minecraft 1.20.1). Sie bildet die Regeln des Projekts direkt im
Spiel ab: Nationen, Kriegserklärungen, Staatskasse, Beweisakte, Kolonialbehörde,
Wahlen, Visa, Post und den Aktivitätszähler.

Reine Server-Mod: Clients brauchen sie nicht, der Modcode des Packs bleibt
unverändert. Alle Anbindungen an andere Mods sind optional.

**Download:** [CurseForge](https://www.curseforge.com/projects/1802743) ·
Quellcode auf [GitHub](https://github.com/NiklasM-foss/Crowns-and-Cannons-Core)

## Module und Commands

Die vollständige Liste mit Rollen steht in [docs/Befehle.pdf](docs/Befehle.pdf). Im
Spiel zeigt `/cccore list` dieselbe Liste, `/cccore list meine` nur die Befehle, die
man selbst nutzen darf, und `/cccore list <Modul>` ein einzelnes Modul.

| Modul | Commands | Regelwerk |
|---|---|---|
| Hilfe | `/cccore`, `/cccore list [Seite,Modul,meine]` | |
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

Admin ist, wer OP-Level 2 hat oder in der Config unter `admins` steht.

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

- `discord.kriegsnews`, `discord.gericht`, `discord.aktivitaet`, `discord.news`:
  Webhook-URLs der Discord-Kanäle.
- `krieg.spawnRadius`, `krieg.kampfErlaubt`, `krieg.blitzkriegMinuten`.
- `akte.campingMinuten`, `visum.visumSekunden`.
- `aktivitaet.wocheStunden`, `wochenendeStunden`, `berichtWochentag`.
- `darlehen.*`, `kolonie.*`, `wegstein.zoll`, `post.abfangProzent`.

Alle Daten liegen in `world/data/crownscore.dat`.

## Bauen

Voraussetzung: JDK 17. Dann im Projektordner:

```
./gradlew build
```

Die JAR liegt danach in `build/libs/`. Die Abhängigkeiten zum Kompilieren
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

## Installation auf dem Server

JAR in den `mods`-Ordner des Servers legen, neu starten, Config anpassen, dann
Nationen anlegen:

```
/nation anlegen Frankreich FRA
/nation staatschef FRA <Spieler>
```

Spieler treten mit `/nation beitreten FRA` bei, der Staatschef bestätigt.
