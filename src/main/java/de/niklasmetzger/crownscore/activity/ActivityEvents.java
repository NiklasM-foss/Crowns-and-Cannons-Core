package de.niklasmetzger.crownscore.activity;

import de.niklasmetzger.crownscore.data.ActivityRecord;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.evidence.PresenceTracker;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Login und Logout: Namen aktualisieren, Sekunden seit der letzten Minute nachbuchen. */
public class ActivityEvents {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        CrownsData d = CrownsData.get(p.server);
        ActivityRecord r = d.activity.computeIfAbsent(p.getUUID(), k -> new ActivityRecord());
        r.name = p.getGameProfile().getName();
        r.zuletztOnline = TimeUtil.now();
        Nation n = d.nationOf(p.getUUID());
        if (n != null) n.mitglieder.put(p.getUUID(), r.name);
        ActivityService.sessionStart(p.getUUID());
        de.niklasmetzger.crownscore.mode.ModeService.reset(p);
        d.setDirty();
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        ActivityService.sessionEnd(p.server, p.getUUID());
        PresenceTracker.forget(p.getUUID());
        de.niklasmetzger.crownscore.mode.ModeService.forget(p.getUUID());
        de.niklasmetzger.crownscore.log.Inspector.forget(p.getUUID());
        de.niklasmetzger.crownscore.log.RollbackService.forget(p.getUUID());
    }
}
