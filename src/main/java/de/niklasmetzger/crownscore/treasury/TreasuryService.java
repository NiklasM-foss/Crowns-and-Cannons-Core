package de.niklasmetzger.crownscore.treasury;

import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Treasury;

/** Buchungen auf Staatskassen. */
public final class TreasuryService {
    private TreasuryService() {}

    public static double stand(CrownsData d, String nationKey) {
        return d.treasury(nationKey).stand;
    }

    /** Zieht den Betrag aus der Staatskasse, false wenn nicht gedeckt. */
    public static boolean charge(CrownsData d, String nationKey, double amount) {
        if (amount <= 0) return true;
        Treasury t = d.treasury(nationKey);
        if (t.stand + 1e-9 < amount) return false;
        t.stand -= amount;
        d.setDirty();
        return true;
    }

    public static void credit(CrownsData d, String nationKey, double amount) {
        if (amount <= 0) return;
        d.treasury(nationKey).stand += amount;
        d.setDirty();
    }
}
