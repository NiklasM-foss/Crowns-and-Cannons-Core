package de.niklasmetzger.crownscore.data;

import de.niklasmetzger.crownscore.config.CrownsConfig;

import java.util.Locale;

/** Kriegsarten nach Regel 4.2. */
public enum WarType {
    KRIEG("Krieg"),
    POLITISCHE_SPANNUNGEN("Politische Spannungen"),
    KALTER_KRIEG("Kalter Krieg"),
    HEISSER_KRIEG("Heisser Krieg"),
    TOTALER_KRIEG("Totaler Krieg"),
    BLITZKRIEG("Blitzkrieg"),
    GUERILLAKRIEG("Guerillakrieg"),
    WELTKRIEG("Weltkrieg");

    public final String anzeige;

    WarType(String anzeige) {
        this.anzeige = anzeige;
    }

    /** PvP-Schaden erlaubt? Liste steht in der Config. */
    public boolean kampfErlaubt() {
        for (String s : CrownsConfig.KAMPF_ERLAUBT.get()) {
            if (s.equalsIgnoreCase(name())) return true;
        }
        return false;
    }

    /** Spionage, Einbrueche, Abfangen von Post erlaubt (Spannungen und Wettruesten). */
    public boolean spionage() {
        return this == POLITISCHE_SPANNUNGEN || this == KALTER_KRIEG || this == HEISSER_KRIEG;
    }

    public static WarType parse(String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replace(' ', '_').replace('-', '_');
        for (WarType t : values()) {
            if (t.name().toLowerCase(Locale.ROOT).equals(s)) return t;
            String a = t.anzeige.toLowerCase(Locale.ROOT).replace(' ', '_');
            if (a.equals(s)) return t;
        }
        if (s.equals("spannungen")) return POLITISCHE_SPANNUNGEN;
        if (s.equals("kalt") || s.equals("kalter")) return KALTER_KRIEG;
        if (s.equals("heiss") || s.equals("heisser")) return HEISSER_KRIEG;
        if (s.equals("total") || s.equals("totaler")) return TOTALER_KRIEG;
        if (s.equals("blitz")) return BLITZKRIEG;
        if (s.equals("guerilla")) return GUERILLAKRIEG;
        if (s.equals("welt")) return WELTKRIEG;
        return null;
    }
}
