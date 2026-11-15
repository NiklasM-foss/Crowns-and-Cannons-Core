# Crowns Core

**Server-side mod for nations roleplay** · Forge 1.20.1 · *server-side only*

Crowns Core was written for the **Crowns and Cannons** project and enforces its rule set directly in the game. Players do not need to install it. The mod only goes into the server's `mods` folder.

## Features

- **Nations:** joining with approval by the head of state, officials, election mode per nation
- **Wars:** declarations require a war type and a reason, PvP only between nations at war or in a duel, protected zone at spawn, peace only with consent of both sides
- **Evidence log:** records block breaking, building, fire, opened chests, kills, camping and entering foreign territory, without preventing anything
- **Treasury and loans:** national accounts, transfers between nations, loans with weekly instalments
- **Colonies:** expeditions with expansion stages, approval by the game staff
- **Elections and parliament:** elections for office per nation, resolutions with one vote per nation
- **Visas:** the owning nation is notified when someone stands in its territory without a visa. Flying over does not count.
- **Mail:** letters between nations that hostile nations can intercept
- **Contracts:** store books with a checksum so nobody can change the text later
- **Activity:** playtime per week and an automatic weekly report
- **Discord:** war news, court, activity and news via webhooks
- **Player modes:** RP, Farm and AFK. Players in Farm or AFK mode may only be attacked with a stated reason, which goes into the evidence log. AFK runs until a set time like a parking disc.
- **Block log and rollback** (similar to CoreProtect): inspector, lookups by player, time, radius, action and block, rollback and restore of blocks, chest contents and killed animals, preview and undo. Stored in an embedded database, no setup needed.
- **Permissions** (similar to LuckPerms): groups with weight and inheritance, player permissions, wildcards, temporary and per-dimension permissions, prefixes and suffixes in chat and tab list, tracks for ranks, Forge permission handler, `command.<name>` for every command including vanilla. Editable in the browser with a self-hosted version of the LuckPerms web editor (Docker setup included).
- **Hidden commands:** players only see the commands they are allowed to use, also in tab completion.

`/cccore list` shows all commands in game. The full command reference is in the [source code repository](https://github.com/NiklasM-foss/Crowns-and-Cannons-Core).

## Optional integrations

| Mod | Used for |
|---|---|
| Money for Everyone | player accounts for treasury payments and tolls |
| Waystones | blocks the use of foreign waystones |
| Open Parties and Claims | territory borders taken from claims |

Without these mods everything runs on built-in fallbacks.

## Language

All in-game texts are **German**.

---

# Deutsch

**Server-Mod für Nationen-Rollenspiel** · Forge 1.20.1 · *nur Server*

Crowns Core wurde für das Projekt **Crowns and Cannons** geschrieben und bildet dessen Regelwerk direkt im Spiel ab. Clients brauchen die Mod nicht, sie gehört nur in den `mods`-Ordner des Servers.

## Funktionen

- **Nationen:** Beitritt mit Bestätigung durch den Staatschef, Beamte, Wahlmodus pro Nation
- **Kriege:** Kriegserklärung nur mit Kriegsart und Grund, PvP nur zwischen Nationen im Krieg oder im Duell, Schutzzone am Spawn, Frieden nur mit Zustimmung beider Seiten
- **Beweisakte:** protokolliert Abbau, Bauen, Feuer, Kisten, Tötungen, Camping und Einreise in fremdes Gebiet, ohne etwas zu verhindern
- **Staatskasse und Darlehen:** Länderkonto, Überweisungen zwischen Nationen, Kredite mit wöchentlichen Raten
- **Kolonien:** Expeditionen mit Ausbaustufen, Genehmigung durch die Spielleitung
- **Wahlen und Parlament:** Amtswahlen pro Nation, Resolutionen mit einer Stimme pro Nation
- **Visa:** Meldung an die Besitzernation, wenn jemand ohne Visum im Gebiet steht. Überflüge zählen nicht.
- **Post:** Briefe zwischen Nationen, die verfeindete Nationen abfangen können
- **Verträge:** Bücher mit Prüfsumme hinterlegen, damit später niemand den Text ändern kann
- **Aktivität:** Spielzeit pro Woche, automatischer Wochenbericht
- **Discord:** Kriegsnews, Gericht, Aktivität und News per Webhook
- **Spielermodus:** RP, Farm und AFK. Wer im Farm- oder AFK-Modus ist, darf nur mit eingetragenem Grund angegriffen werden, der Grund landet in der Akte. AFK läuft bis zu einer Uhrzeit wie eine Parkscheibe.
- **Blockprotokoll und Rollback** (ähnlich CoreProtect): Inspektor, Abfragen nach Spieler, Zeit, Radius, Aktion und Block, Rollback und Restore von Blöcken, Kisteninhalten und getöteten Tieren, Vorschau und Undo. Eingebettete Datenbank, keine Einrichtung nötig.
- **Rechte** (ähnlich LuckPerms): Gruppen mit Gewicht und Vererbung, Spielerrechte, Platzhalter, befristete und dimensionsgebundene Rechte, Präfix und Suffix in Chat und Tabliste, Laufbahnen für Ränge, Forge-Permission-Handler, `command.<name>` für jeden Befehl, auch Vanilla. Einstellbar im Browser mit einer selbst gehosteten Fassung des LuckPerms-Web-Editors (Docker-Aufbau liegt bei).
- **Unsichtbare Befehle:** Spieler sehen nur die Befehle, die sie nutzen dürfen, auch in der Tab-Vervollständigung.

Alle Befehle zeigt `/cccore list` im Spiel. Die vollständige Befehlsübersicht steht im [Quellcode-Repo](https://github.com/NiklasM-foss/Crowns-and-Cannons-Core).

## Optionale Anbindungen

| Mod | Wofür |
|---|---|
| Money for Everyone | Spielerkonten für Staatskasse und Zoll |
| Waystones | Sperre für fremde Wegsteine |
| Open Parties and Claims | Gebietsgrenzen aus den Claims |

Ohne diese Mods läuft alles mit eingebauten Ersatzlösungen.

## Sprache

Alle Texte im Spiel sind auf Deutsch.
