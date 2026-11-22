package de.niklasmetzger.crownscore.handel;

import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Mail;
import de.niklasmetzger.crownscore.data.Nachricht;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.Stadt;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.Perms;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Postfach als Truhenmenue (6 Reihen). Admins sehen oben die offenen Stadtantraege, darunter kommen Briefe an die
 * eigene Nation (fuer Staatschef und Beamte) und die eigenen Nachrichten. Klick = lesen, Shift-Klick = loeschen.
 */
public final class PostfachMenu extends ChestMenu {
    private enum Art { ANTRAG, WAPPEN, BRIEF, NACHRICHT }

    private record Eintrag(Art art, Object ziel) {}

    private final SimpleContainer box;
    private final List<Eintrag> eintraege = new ArrayList<>();

    private PostfachMenu(int id, Inventory inv, SimpleContainer box) {
        super(MenuType.GENERIC_9x6, id, inv, box, 6);
        this.box = box;
    }

    public static void oeffnen(ServerPlayer p) {
        SimpleContainer box = new SimpleContainer(54);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> {
            PostfachMenu m = new PostfachMenu(id, inv, box);
            m.fuellen(p);
            return m;
        }, Component.literal("Postfach")));
    }

    private void fuellen(ServerPlayer p) {
        CrownsData d = CrownsData.get(p.getServer());
        eintraege.clear();
        for (int i = 0; i < 54; i++) box.setItem(i, ItemStack.EMPTY);
        if (Perms.isAdmin(p)) {
            for (Stadt s : StadtService.offen(d)) {
                Nation n = d.nations.get(s.nation);
                add(Art.ANTRAG, s, Items.WRITABLE_BOOK, "Stadtantrag: " + s.name, ChatFormatting.GOLD, true,
                        List.of((n == null ? s.nation : n.name) + ", " + s.betten + " Betten", "bei " + s.pos.getX() + " " + s.pos.getY() + " " + s.pos.getZ(),
                                "Klick: annehmen, ablehnen oder hinreisen"));
            }
        }
        if (Perms.isAdmin(p)) {
            for (Stadt s : d.staedte) {
                if (WappenStore.wartend(s) == null) continue;
                add(Art.WAPPEN, s, Items.SHIELD, "Wappen: " + s.name, ChatFormatting.GOLD, true, List.of("wartet auf Freigabe", "Klick: annehmen oder ablehnen"));
            }
        }
        Nation own = d.nationOf(p.getUUID());
        if (own != null) {
            for (Mail m : Postfach.briefe(d, own)) {
                add(Art.BRIEF, m, Items.PAPER, "Brief von " + d.anzeige(m.vonNation), ChatFormatting.AQUA, true,
                        List.of(TimeUtil.fmtShort(m.zeit), "Klick: lesen"));
            }
        }
        for (Nachricht n : Postfach.von(d, p.getUUID())) {
            add(Art.NACHRICHT, n, n.gelesen ? Items.MAP : Items.FILLED_MAP, n.titel, n.gelesen ? ChatFormatting.GRAY : ChatFormatting.WHITE, !n.gelesen,
                    List.of(TimeUtil.fmtShort(n.zeit), kurz(n.text), "Klick: lesen, Shift-Klick: loeschen"));
        }
        if (eintraege.isEmpty()) {
            ItemStack leer = new ItemStack(Items.BARRIER);
            leer.setHoverName(Component.literal("Keine Nachrichten").withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GRAY)));
            box.setItem(22, leer);
        }
    }

    private static String kurz(String t) {
        return t.length() > 60 ? t.substring(0, 57) + "..." : t;
    }

    private void add(Art art, Object ziel, net.minecraft.world.item.Item icon, String titel, ChatFormatting farbe, boolean neu, List<String> lore) {
        if (eintraege.size() >= 54) return;
        ItemStack s = new ItemStack(icon);
        s.setHoverName(Component.literal(titel).withStyle(st -> st.withItalic(false).withColor(farbe).withBold(neu)));
        CompoundTag display = s.getOrCreateTagElement("display");
        ListTag l = new ListTag();
        for (String line : lore) l.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(line).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GRAY)))));
        display.put("Lore", l);
        box.setItem(eintraege.size(), s);
        eintraege.add(new Eintrag(art, ziel));
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (slot >= 0 && slot < 54) {
            if (player instanceof ServerPlayer sp && slot < eintraege.size() && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
                Eintrag e = eintraege.get(slot);
                CrownsData d = CrownsData.get(sp.getServer());
                switch (e.art()) {
                    case NACHRICHT -> {
                        Nachricht n = (Nachricht) e.ziel();
                        if (type == ClickType.QUICK_MOVE) {
                            d.nachrichten.remove(n);
                            d.setDirty();
                        } else {
                            Postfach.lesen(sp, n);
                        }
                    }
                    case BRIEF -> {
                        Mail m = (Mail) e.ziel();
                        sp.sendSystemMessage(Component.literal(Msg.PREFIX + "§fBrief von " + m.vonName + " (" + d.anzeige(m.vonNation) + ") §7" + TimeUtil.fmtShort(m.zeit)));
                        sp.sendSystemMessage(Component.literal(" §7" + m.text));
                        m.gelesen = true;
                        d.setDirty();
                    }
                    case WAPPEN -> {
                        sp.closeContainer();
                        sp.sendSystemMessage(WappenCommand.meldung((Stadt) e.ziel(), null, null));
                        return;
                    }
                    case ANTRAG -> {
                        sp.closeContainer();
                        antrag(sp, (Stadt) e.ziel());
                        return;
                    }
                }
                fuellen(sp);
            }
            sendAllDataToRemote();
            return;
        }
        if (type == ClickType.QUICK_MOVE) return;
        super.clicked(slot, button, type, player);
    }

    private static void antrag(ServerPlayer p, Stadt s) {
        String wo = s.pos.getX() + " " + s.pos.getY() + " " + s.pos.getZ();
        p.sendSystemMessage(Component.literal(Msg.PREFIX + "§fStadtantrag " + s.name + " §7(" + StadtService.zeile(s) + ")"));
        MutableComponent c = Component.literal(" ");
        c.append(knopf("[Hinreisen]", "/tp @s " + wo, ChatFormatting.AQUA, ClickEvent.Action.RUN_COMMAND)).append(" ");
        c.append(knopf("[Annehmen]", "/cccore staedte annehmen \"" + s.name + "\"", ChatFormatting.GREEN, ClickEvent.Action.RUN_COMMAND)).append(" ");
        c.append(knopf("[Ablehnen]", "/cccore staedte ablehnen \"" + s.name + "\" ", ChatFormatting.RED, ClickEvent.Action.SUGGEST_COMMAND));
        p.sendSystemMessage(c);
    }

    private static Component knopf(String text, String cmd, ChatFormatting farbe, ClickEvent.Action a) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(farbe).withClickEvent(new ClickEvent(a, cmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(cmd))));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
