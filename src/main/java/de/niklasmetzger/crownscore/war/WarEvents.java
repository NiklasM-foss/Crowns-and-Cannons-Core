package de.niklasmetzger.crownscore.war;

import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.EvidenceEntry;
import de.niklasmetzger.crownscore.data.War;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** PvP nur zwischen Kriegsparteien oder im Duell, am Spawn nie. */
public class WarEvents {
    private final Map<UUID, Long> lastHint = new HashMap<>();

    private static ServerPlayer attacker(LivingAttackEvent event) {
        Entity e = event.getSource().getEntity();
        if (e instanceof ServerPlayer p) return p;
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof Projectile pr && pr.getOwner() instanceof ServerPlayer p) return p;
        return null;
    }

    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        ServerPlayer attacker = attacker(event);
        if (attacker == null || attacker == victim) return;

        if (WarService.inSpawnZone(attacker) || WarService.inSpawnZone(victim)) {
            deny(event, attacker, "Am Spawn ist PvP verboten.");
            return;
        }
        if (DuelManager.inDuel(attacker.getUUID(), victim.getUUID())) return;

        CrownsData d = CrownsData.get(attacker.server);
        String a = d.nationKeyOf(attacker.getUUID());
        String v = d.nationKeyOf(victim.getUUID());
        if (a == null || v == null) {
            if (CrownsConfig.STAATENLOSE_PVP.get()) return;
            deny(event, attacker, a == null ? "Du gehoerst keiner Nation an und darfst nicht kaempfen."
                    : victim.getGameProfile().getName() + " gehoert keiner Nation an. Fuer einen Zweikampf: /duell " + victim.getGameProfile().getName());
            return;
        }
        if (a.equals(v)) {
            deny(event, attacker, "Keine Gewalt gegen eigene Buerger. Fuer einen Zweikampf: /duell " + victim.getGameProfile().getName());
            return;
        }
        War w = d.activeWar(a, v);
        if (w == null) {
            deny(event, attacker, "Kein Krieg zwischen " + d.anzeige(a) + " und " + d.anzeige(v) + ". Erst /krieg erklaeren.");
            return;
        }
        if (!w.art.kampfErlaubt()) {
            deny(event, attacker, w.art.anzeige + " erlaubt keinen offenen Kampf. Kriegsart wechseln oder Duell.");
            return;
        }
        if (w.ersterKampf == 0) {
            w.ersterKampf = TimeUtil.now();
            d.setDirty();
            Msg.broadcast(attacker.server, "Erster Kampf im " + w.art.anzeige + " zwischen " + d.anzeige(a) + " und " + d.anzeige(v) + "!");
        }
    }

    private void deny(LivingAttackEvent event, ServerPlayer attacker, String reason) {
        event.setCanceled(true);
        long now = System.currentTimeMillis();
        Long last = lastHint.get(attacker.getUUID());
        if (last == null || now - last > 3000) {
            lastHint.put(attacker.getUUID(), now);
            Msg.player(attacker, "§c" + reason);
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        Entity src = event.getSource().getEntity();
        if (!(src instanceof ServerPlayer killer) || killer == victim) return;
        CrownsData d = CrownsData.get(killer.server);
        EvidenceEntry e = new EvidenceEntry();
        e.zeit = TimeUtil.now();
        e.typ = "TOETUNG";
        e.taeter = killer.getUUID();
        e.taeterName = killer.getGameProfile().getName();
        String kn = d.nationKeyOf(killer.getUUID());
        e.taeterNation = kn == null ? "" : d.anzeige(kn);
        e.dim = victim.level().dimension().location().toString();
        e.x = victim.blockPosition().getX();
        e.y = victim.blockPosition().getY();
        e.z = victim.blockPosition().getZ();
        String vn = d.nationKeyOf(victim.getUUID());
        War w = (kn != null && vn != null) ? d.activeWar(kn, vn) : null;
        e.detail = victim.getGameProfile().getName() + (vn == null ? "" : " (" + d.anzeige(vn) + ")") + " getoetet"
                + (w != null ? " im " + w.art.anzeige + " Nr. " + w.id : DuelManager.inDuel(killer.getUUID(), victim.getUUID()) ? " im Duell" : "");
        d.addEvidence(e);
        DuelManager.onDeath(victim.server, victim.getUUID());
    }
}
