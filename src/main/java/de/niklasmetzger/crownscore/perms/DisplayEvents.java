package de.niklasmetzger.crownscore.perms;

import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.mode.ModeService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Praefix und Suffix aus den Rechtegruppen vor dem Namen, Spielermodus in der Tabliste. */
public class DisplayEvents {

    @SubscribeEvent
    public void onName(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !CrownsConfig.RECHTE_PRAEFIX.get()) return;
        String pre = PermissionManager.prefix(p.getUUID());
        String suf = PermissionManager.suffix(p.getUUID());
        if (pre.isEmpty() && suf.isEmpty()) return;
        event.setDisplayname(Component.literal(spacedPre(pre)).append(event.getUsername().copy()).append(Component.literal(spacedSuf(suf))));
    }

    @SubscribeEvent
    public void onTab(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        String pre = CrownsConfig.RECHTE_PRAEFIX.get() ? PermissionManager.prefix(p.getUUID()) : "";
        String suf = CrownsConfig.RECHTE_PRAEFIX.get() ? PermissionManager.suffix(p.getUUID()) : "";
        String mode = CrownsConfig.MODUS_TABLISTE.get() ? " §8[" + ModeService.anzeige(ModeService.state(p.getUUID())) + "§8]" : "";
        if (pre.isEmpty() && suf.isEmpty() && mode.isEmpty()) return;
        event.setDisplayName(Component.literal(spacedPre(pre) + p.getGameProfile().getName() + "§r" + spacedSuf(suf) + mode));
    }

    /** Leerzeichen zwischen Praefix und Name, Chat-Eingaben schneiden es am Ende ab. */
    static String spacedPre(String pre) {
        if (pre.isEmpty()) return "";
        String plain = pre.replaceAll("§.", "");
        return plain.endsWith(" ") ? pre + "§r" : pre + "§r ";
    }

    static String spacedSuf(String suf) {
        if (suf.isEmpty()) return "";
        String plain = suf.replaceAll("§.", "");
        return plain.startsWith(" ") ? "§r" + suf : "§r " + suf;
    }
}
