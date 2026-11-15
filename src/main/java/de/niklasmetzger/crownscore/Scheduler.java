package de.niklasmetzger.crownscore;

import de.niklasmetzger.crownscore.activity.ActivityService;
import de.niklasmetzger.crownscore.election.ElectionService;
import de.niklasmetzger.crownscore.evidence.PresenceTracker;
import de.niklasmetzger.crownscore.treasury.LoanService;
import de.niklasmetzger.crownscore.war.DuelManager;
import de.niklasmetzger.crownscore.war.WarService;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Zentrale Zeitsteuerung: Sekunden- und Minutenaufgaben aller Module. */
public class Scheduler {
    private int tick;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        tick++;
        try {
            de.niklasmetzger.crownscore.log.RollbackService.tick();
            de.niklasmetzger.crownscore.world.WeltkarteCommand.tick(server);
        } catch (Exception e) {
            CrownsCore.LOGGER.error("Fehler im Rollback", e);
        }
        if (tick % 20 == 0) {
            try {
                PresenceTracker.everySecond(server);
                DuelManager.everySecond(server);
                WarService.everySecond(server);
                de.niklasmetzger.crownscore.mode.ModeService.everySecond(server);
                de.niklasmetzger.crownscore.perms.RoleWatcher.everySecond(server);
            } catch (Exception e) {
                CrownsCore.LOGGER.error("Fehler in der Sekundenaufgabe", e);
            }
        }
        if (tick % 1200 == 0) {
            try {
                ActivityService.everyMinute(server);
                ElectionService.everyMinute(server);
                LoanService.everyMinute(server);
                de.niklasmetzger.crownscore.perms.PermissionManager.everyMinute();
            } catch (Exception e) {
                CrownsCore.LOGGER.error("Fehler in der Minutenaufgabe", e);
            }
        }
    }
}
