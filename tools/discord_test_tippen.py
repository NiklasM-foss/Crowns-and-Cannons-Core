"""Tippt alle Slash-Befehle des Crowns-Core-Bots nacheinander in den Discord-Client.

Ablauf: Skript starten, in Discord in den Testkanal klicken (Cursor im Nachrichtenfeld),
der Countdown laeuft, dann tippt das Skript. ESC bricht jederzeit ab.

    pip install keyboard
    python tools/discord_test_tippen.py            alle Befehle
    python tools/discord_test_tippen.py karte nation   nur diese

Optionen schreibt das Skript als name:wert, so wie man es in Discord auch von Hand tippt.
Klappt die Auswahl bei dir nicht, die Wartezeiten unten hochsetzen.
"""
import sys
import time

import keyboard

# Befehl, Optionen (Reihenfolge wie in Discord), Wartezeit auf die Antwort in Sekunden
BEFEHLE = [
    ("online", [], 3),
    ("ingamezeit", [], 3),
    ("konto", [], 3),
    ("postfach", [], 3),
    ("nation", [("name", "Saudi-Arabien")], 3),
    ("kriege", [], 3),
    ("besetzungen", [], 3),
    ("staedte", [], 3),
    ("wahlen", [], 3),
    ("vertrag", [("nr", "1")], 3),
    ("karte", [], 8),
    ("pause", [("tage", "3"), ("grund", "Test")], 3),
    ("pause", [("tage", "0")], 3),
    # Admin
    ("ingamezeit", [("spieler", "Alpha")], 3),
    ("wochenbericht", [], 4),
    ("antraege", [], 3),
    ("akte", [("spieler", "Alpha")], 3),
    ("say", [("text", "Hallo aus dem Discord-Test")], 3),
    ("befehl", [("befehl", "list")], 3),
    ("geld", [("spieler", "Alpha"), ("betrag", "100")], 3),
    ("staatskasse", [("nation", "Saudi-Arabien"), ("betrag", "50000")], 3),
    ("verknuepfen", [], 3),
]

# Befehle, deren erste Option Pflicht ist (siehe BotBefehle.definitionen)
ERSTE_IST_PFLICHT = {"pause", "nation", "vertrag", "akte", "kick", "ban", "say", "befehl", "geld", "staatskasse"}

COUNTDOWN = 5       # Sekunden zum Umschalten nach Discord
TIPPEN = 0.03        # Pause zwischen zwei Zeichen
AUSWAHL = 1.2        # Warten, bis Discord den Befehl in der Liste zeigt
OPTION = 0.6         # Warten nach dem Namen einer Option bzw. nach einem Wert

abbruch = False


def stopp():
    global abbruch
    abbruch = True
    print("\nAbbruch (ESC)")


def warten(sek):
    ende = time.time() + sek
    while time.time() < ende:
        if abbruch:
            sys.exit(1)
        time.sleep(0.05)


def tippe(text):
    if abbruch:
        sys.exit(1)
    keyboard.write(text, delay=TIPPEN)


def befehl(name, optionen):
    tippe("/" + name)
    warten(AUSWAHL)
    keyboard.press_and_release("tab")  # Befehl aus der Liste uebernehmen
    warten(OPTION)
    for i, (opt, wert) in enumerate(optionen):
        # Ist die erste Option Pflicht, ist ihr Feld nach der Auswahl schon aktiv, alle anderen ueber name:
        if i > 0 or name not in ERSTE_IST_PFLICHT:
            tippe(" " + opt + ":")
            warten(OPTION)
        tippe(wert)
        warten(OPTION)
    keyboard.press_and_release("enter")
    warten(0.4)
    # Steht noch eine Vorschlagsliste offen, nimmt das erste Enter den Vorschlag, das zweite schickt ab
    keyboard.press_and_release("enter")


def main():
    auswahl = sys.argv[1:]
    liste = [b for b in BEFEHLE if not auswahl or b[0] in auswahl]
    if not liste:
        print("Keine passenden Befehle. Moeglich:", ", ".join(sorted({b[0] for b in BEFEHLE})))
        return
    keyboard.add_hotkey("esc", stopp)
    print(f"{len(liste)} Befehle. Jetzt in Discord ins Nachrichtenfeld des Testkanals klicken.")
    for s in range(COUNTDOWN, 0, -1):
        print(f"  Start in {s} ...", end="\r")
        warten(1)
    for nr, (name, optionen, pause) in enumerate(liste, 1):
        text = "/" + name + "".join(f" {o}:{w}" for o, w in optionen)
        print(f"[{nr}/{len(liste)}] {text}")
        befehl(name, optionen)
        warten(pause)
    print("Fertig.")


if __name__ == "__main__":
    main()
