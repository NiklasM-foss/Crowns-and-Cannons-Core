package de.niklasmetzger.crownscore.mode;

import java.util.Locale;

/** Spielermodus nach Ghasts Regel: nur im RP darf man ohne Grund angegriffen werden. */
public enum PlayerMode {
    RP("RP", "§a", "Rollenspiel, normale Regeln"),
    FARM("Farm", "§e", "Ressourcen sammeln, Angriff nur mit Grund"),
    AFK("AFK", "§7", "an der AFK-Farm, Angriff nur mit Grund");

    public final String label;
    public final String color;
    public final String info;

    PlayerMode(String label, String color, String info) {
        this.label = label;
        this.color = color;
        this.info = info;
    }

    public boolean geschuetzt() {
        return this != RP;
    }

    public static PlayerMode parse(String s) {
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }
}
