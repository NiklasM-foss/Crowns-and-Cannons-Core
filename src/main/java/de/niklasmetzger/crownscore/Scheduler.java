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

    private static void sicher(String was, Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            CrownsCore.LOGGER.error("Fehler in der Sekundenaufgabe {}", was, e);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        tick++;
        sicher("Rollback", de.niklasmetzger.crownscore.log.RollbackService::tick);
        sicher("Kartenaufbau", () -> de.niklasmetzger.crownscore.world.WeltkarteCommand.tick(server));
        if (tick % 20 == 0) {
            // Jede Aufgabe einzeln, damit ein Fehler nicht alle folgenden stilllegt
            sicher("Anwesenheit", () -> PresenceTracker.everySecond(server));
            sicher("Duelle", () -> DuelManager.everySecond(server));
            sicher("Kriege", () -> WarService.everySecond(server));
            sicher("Spielermodus", () -> de.niklasmetzger.crownscore.mode.ModeService.everySecond(server));
            sicher("Rollen", () -> de.niklasmetzger.crownscore.perms.RoleWatcher.everySecond(server));
            sicher("Umhaenge", () -> de.niklasmetzger.crownscore.flag.CapeService.everySecond(server));
            sicher("Besetzungen", () -> de.niklasmetzger.crownscore.world.EroberungService.everySecond(server));
            sicher("Kartenflaggen", () -> de.niklasmetzger.crownscore.flag.MapFlagService.everySecond(server));
            sicher("Antraege", () -> de.niklasmetzger.crownscore.handel.AntragService.everySecond(server));
        }
        if (tick % 1200 == 0) {
            sicher("Aktivitaet", () -> ActivityService.everyMinute(server));
            sicher("Wahlen", () -> ElectionService.everyMinute(server));
            sicher("Darlehen", () -> LoanService.everyMinute(server));
            sicher("Villager-Nachfrage", () -> de.niklasmetzger.crownscore.handel.VillagerHandel.everyMinute(server));
            sicher("Rechte-Ablauf", de.niklasmetzger.crownscore.perms.PermissionManager::everyMinute);
            sicher("Discord-Bot", () -> de.niklasmetzger.crownscore.discord.DiscordBot.everyMinute(server));
        }
    }
}
