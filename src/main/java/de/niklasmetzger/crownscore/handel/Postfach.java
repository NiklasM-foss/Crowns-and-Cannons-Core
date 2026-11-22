package de.niklasmetzger.crownscore.handel;

import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Mail;
import de.niklasmetzger.crownscore.data.Nachricht;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Postfach fuer jeden Spieler (/postfach, als Menue). Darin landen Entscheidungen zu Antraegen, Kriegs- und
 * Besatzungsmeldungen, Flaggen-Freigaben und ungelesene Briefe an die eigene Nation. Admins sehen dort zusaetzlich
 * alle offenen Stadtantraege. Wer offline war, bekommt beim Einloggen einen Hinweis.
 */
public final class Postfach {
    /** Hoechstens so viele Nachrichten pro Spieler, die aeltesten fallen raus. */
    private static final int MAX = 60;

    private Postfach() {}

    public static void an(MinecraftServer server, UUID spieler, String titel, String text, String befehl) {
        if (spieler == null) return;
        CrownsData d = CrownsData.get(server);
        Nachricht n = new Nachricht();
        n.id = d.nextNachrichtId++;
        n.an = spieler;
        n.titel = titel;
        n.text = text;
        n.zeit = TimeUtil.now();
        n.befehl = befehl == null ? "" : befehl;
        d.nachrichten.add(n);
        List<Nachricht> eigene = von(d, spieler);
        if (eigene.size() > MAX) d.nachrichten.removeAll(eigene.subList(MAX, eigene.size()));
        d.setDirty();
        ServerPlayer p = server.getPlayerList().getPlayer(spieler);
        if (p != null) p.sendSystemMessage(hinweis("Neue Nachricht: " + titel));
    }

    /** An Staatschef und alle Buerger einer Nation. */
    public static void nation(MinecraftServer server, Nation n, String titel, String text, String befehl) {
        Set<UUID> an = new HashSet<>(n.mitglieder.keySet());
        if (n.chef != null) an.add(n.chef);
        for (UUID u : an) an(server, u, titel, text, befehl);
    }

    /** Nachrichten eines Spielers, neueste zuerst. */
    public static List<Nachricht> von(CrownsData d, UUID spieler) {
        return d.nachrichten.stream().filter(m -> spieler.equals(m.an))
                .sorted(Comparator.comparingLong((Nachricht m) -> m.zeit).reversed()).toList();
    }

    public static List<Mail> briefe(CrownsData d, Nation n) {
        if (n == null) return List.of();
        return d.mails.stream().filter(m -> n.key().equals(m.anNation) && !m.gelesen)
                .sorted(Comparator.comparingLong((Mail m) -> m.zeit).reversed()).toList();
    }

    public static int ungelesen(CrownsData d, ServerPlayer p) {
        int n = (int) d.nachrichten.stream().filter(m -> p.getUUID().equals(m.an) && !m.gelesen).count();
        Nation nat = d.nationOf(p.getUUID());
        if (nat != null) n += briefe(d, nat).size();
        if (de.niklasmetzger.crownscore.util.Perms.isAdmin(p)) n += StadtService.offen(d).size();
        return n;
    }

    static Component hinweis(String text) {
        return Component.literal(Msg.PREFIX + "§e" + text + " ").append(Component.literal("[Postfach]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/postfach"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/postfach")))));
    }

    /** Nachricht im Chat zeigen und als gelesen markieren. */
    public static void lesen(ServerPlayer p, Nachricht n) {
        p.sendSystemMessage(Component.literal(Msg.PREFIX + "§f" + n.titel + " §7(" + TimeUtil.fmtShort(n.zeit) + ")"));
        Component text = Component.literal(" §7" + n.text);
        if (!n.befehl.isEmpty()) {
            text = text.copy().append(" ").append(Component.literal("[Oeffnen]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, n.befehl))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(n.befehl)))));
        }
        p.sendSystemMessage(text);
        if (!n.gelesen) {
            n.gelesen = true;
            CrownsData.get(p.getServer()).setDirty();
        }
    }

    public static class Events {
        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            int n = ungelesen(CrownsData.get(p.getServer()), p);
            if (n > 0) p.sendSystemMessage(hinweis("Du hast " + n + " ungelesene Nachricht" + (n == 1 ? "" : "en") + "."));
        }
    }
}
