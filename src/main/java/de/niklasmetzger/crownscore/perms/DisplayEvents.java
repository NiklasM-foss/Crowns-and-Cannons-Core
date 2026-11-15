package de.niklasmetzger.crownscore.perms;

import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.mode.ModeService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Nation und Praefix der Rechtegruppe vor dem Namen im Chat, Spielermodus in der Tabliste. */
public class DisplayEvents {

    @SubscribeEvent
    public void onName(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        String nation = nationTag(p);
        String pre = CrownsConfig.RECHTE_PRAEFIX.get() ? PermissionManager.prefix(p.getUUID()) : "";
        String suf = CrownsConfig.RECHTE_PRAEFIX.get() ? PermissionManager.suffix(p.getUUID()) : "";
        if (nation.isEmpty() && pre.isEmpty() && suf.isEmpty()) return;
        event.setDisplayname(Component.literal(nation + spacedPre(pre)).append(event.getUsername().copy()).append(Component.literal(spacedSuf(suf))));
    }

    /** z.B. "§6[Saudi-Arabien]§r " oder leer, wenn der Spieler keiner Nation angehoert. */
    static String nationTag(ServerPlayer p) {
        if (!CrownsConfig.CHAT_NATION.get()) return "";
        Nation n = CrownsData.get(p.server).nationOf(p.getUUID());
        if (n == null) return "";
        String text = "KUERZEL".equalsIgnoreCase(CrownsConfig.CHAT_NATION_ANZEIGE.get().trim()) ? n.kuerzel : n.name;
        return PermissionManager.color(CrownsConfig.CHAT_NATION_FARBE.get()) + "[" + text + "]§r ";
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
