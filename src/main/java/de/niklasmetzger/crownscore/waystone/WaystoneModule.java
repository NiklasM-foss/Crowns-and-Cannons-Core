package de.niklasmetzger.crownscore.waystone;

import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.claims.Claims;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.economy.Economy;
import de.niklasmetzger.crownscore.treasury.TreasuryService;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.Perms;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.IWaystone;
import net.blay09.mods.waystones.api.WaystoneTeleportEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Wegsteine in fremdem Gebiet nur mit Freigabe der Nation (Kanal #waystones).
 * Globale Wegsteine (Spawn) bleiben frei.
 */
public final class WaystoneModule {
    private static boolean registered;

    private WaystoneModule() {}

    public static void init() {
        if (registered) return;
        try {
            Balm.getEvents().onEvent(WaystoneTeleportEvent.Pre.class, WaystoneModule::onTeleport);
            registered = true;
            CrownsCore.LOGGER.info("Wegstein-Regeln aktiv");
        } catch (Throwable t) {
            CrownsCore.LOGGER.warn("Waystones gefunden, aber Event-Anbindung fehlgeschlagen: {}", t.toString());
        }
    }

    private static void onTeleport(WaystoneTeleportEvent.Pre event) {
        if (!(event.getContext().getEntity() instanceof ServerPlayer p)) return;
        IWaystone target = event.getContext().getTargetWaystone();
        if (target == null) return;
        if (target.isGlobal() && CrownsConfig.WEGSTEIN_GLOBAL_FREI.get()) return;
        if (Perms.isAdmin(p)) return;
        ServerLevel level = p.server.getLevel(target.getDimension());
        if (level == null) return;
        String owner = Claims.nationAt(level, target.getPos());
        if (owner == null) return;
        CrownsData d = CrownsData.get(p.server);
        String own = d.nationKeyOf(p.getUUID());
        if (owner.equals(own)) return;
        Nation ownerNation = d.nations.get(owner);
        if (ownerNation == null) return;
        if (own == null || !ownerNation.wegsteinFreigaben.contains(own)) {
            event.setCanceled(true);
            Msg.player(p, "§cDer Wegstein \"" + target.getName() + "\" gehoert " + ownerNation.name
                    + ". Ohne Freigabe darf er nicht benutzt werden. Die Nation kann ihn mit /wegstein freigeben <Land> oeffnen.");
            return;
        }
        double zoll = CrownsConfig.WEGSTEIN_ZOLL.get();
        if (zoll > 0) {
            if (!Economy.get().withdraw(p, zoll)) {
                event.setCanceled(true);
                Msg.player(p, "§cZoll fuer diesen Wegstein: " + Msg.money(zoll) + ". Dein Konto reicht nicht.");
                return;
            }
            TreasuryService.credit(d, owner, zoll);
            Msg.player(p, "§7Zoll " + Msg.money(zoll) + " an " + ownerNation.name + " gezahlt.");
        }
    }
}
