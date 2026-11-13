package de.niklasmetzger.crownscore.economy;

import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.data.CrownsData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/** Bruecke zur Wirtschaft: Money for Everyone, sonst interne Geldboerse. */
public interface Economy {

    double balance(ServerPlayer player);

    /** Zieht den Betrag ab, false wenn nicht genug Geld da ist. */
    boolean withdraw(ServerPlayer player, double amount);

    void deposit(ServerPlayer player, double amount);

    String name();

    static void init() {
        if (ModList.get().isLoaded("moneyforeveryone")) {
            try {
                Holder.INSTANCE = new MfeEconomy();
                CrownsCore.LOGGER.info("Wirtschaft: Money for Everyone");
                return;
            } catch (Throwable t) {
                CrownsCore.LOGGER.warn("Money for Everyone gefunden, aber nicht nutzbar: {}", t.toString());
            }
        }
        Holder.INSTANCE = new InternalEconomy();
        CrownsCore.LOGGER.info("Wirtschaft: interne Geldboerse (keine Wirtschaftsmod gefunden)");
    }

    static Economy get() {
        if (Holder.INSTANCE == null) init();
        return Holder.INSTANCE;
    }

    final class Holder {
        static Economy INSTANCE;

        private Holder() {}
    }

    /** Fallback ohne Wirtschaftsmod: Guthaben in den Mod-Daten. */
    final class InternalEconomy implements Economy {
        @Override
        public double balance(ServerPlayer player) {
            CrownsData d = CrownsData.get(player.server);
            return d.wallet.getOrDefault(player.getUUID(), 0.0);
        }

        @Override
        public boolean withdraw(ServerPlayer player, double amount) {
            CrownsData d = CrownsData.get(player.server);
            double have = d.wallet.getOrDefault(player.getUUID(), 0.0);
            if (have + 1e-9 < amount) return false;
            d.wallet.put(player.getUUID(), have - amount);
            d.setDirty();
            return true;
        }

        @Override
        public void deposit(ServerPlayer player, double amount) {
            CrownsData d = CrownsData.get(player.server);
            d.wallet.merge(player.getUUID(), amount, Double::sum);
            d.setDirty();
        }

        @Override
        public String name() {
            return "intern";
        }
    }
}
