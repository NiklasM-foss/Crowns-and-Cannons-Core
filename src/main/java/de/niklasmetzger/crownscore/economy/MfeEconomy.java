package de.niklasmetzger.crownscore.economy;

import net.minecraft.server.level.ServerPlayer;
import net.thejojoni.moneyforeveryone.network.MoneyforeveryoneModVariables;

/** Anbindung an Money for Everyone: Guthaben liegt als Capability am Spieler. */
final class MfeEconomy implements Economy {

    MfeEconomy() {
        // Klassen-Ladeprobe: wirft NoClassDefFoundError, wenn die Mod fehlt.
        Object probe = MoneyforeveryoneModVariables.PLAYER_VARIABLES_CAPABILITY;
        if (probe == null) throw new IllegalStateException("Capability fehlt");
    }

    private static MoneyforeveryoneModVariables.PlayerVariables vars(ServerPlayer player) {
        return player.getCapability(MoneyforeveryoneModVariables.PLAYER_VARIABLES_CAPABILITY, null)
                .orElse(new MoneyforeveryoneModVariables.PlayerVariables());
    }

    @Override
    public double balance(ServerPlayer player) {
        return vars(player).money;
    }

    @Override
    public boolean withdraw(ServerPlayer player, double amount) {
        MoneyforeveryoneModVariables.PlayerVariables v = vars(player);
        if (v.money + 1e-9 < amount) return false;
        v.money -= amount;
        v.syncPlayerVariables(player);
        return true;
    }

    @Override
    public boolean deposit(ServerPlayer player, double amount) {
        // Ohne Capability (Spieler tot oder gerade im Wechsel) waere die Gutschrift sonst still verloren
        var cap = player.getCapability(MoneyforeveryoneModVariables.PLAYER_VARIABLES_CAPABILITY, null).resolve();
        if (cap.isEmpty() || !player.isAlive()) return false;
        MoneyforeveryoneModVariables.PlayerVariables v = cap.get();
        v.money += amount;
        v.syncPlayerVariables(player);
        return true;
    }

    @Override
    public String name() {
        return "Money for Everyone";
    }
}
