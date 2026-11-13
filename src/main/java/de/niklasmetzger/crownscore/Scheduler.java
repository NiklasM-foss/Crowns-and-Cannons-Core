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
        if (tick % 20 == 0) {
            try {
                PresenceTracker.everySecond(server);
                DuelManager.everySecond(server);
                WarService.everySecond(server);
            } catch (Exception e) {
                CrownsCore.LOGGER.error("Fehler in der Sekundenaufgabe", e);
            }
        }
        if (tick % 1200 == 0) {
            try {
                ActivityService.everyMinute(server);
                ElectionService.everyMinute(server);
                LoanService.everyMinute(server);
            } catch (Exception e) {
                CrownsCore.LOGGER.error("Fehler in der Minutenaufgabe", e);
            }
        }
    }
}
