package de.niklasmetzger.crownscore.handel;

import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kaufmenue des Villager-Haendlers als Truhenansicht, funktioniert auch ohne Crowns Core im Client.
 * Linksklick = mit Geld kaufen, Rechtsklick = mit Rohstoffen. Nichts laesst sich herausnehmen.
 */
public final class HaendlerMenu extends ChestMenu {
    private final Container box;
    private final List<VillagerHandel.Angebot> angebote;

    private HaendlerMenu(int id, Inventory inv, Container box, List<VillagerHandel.Angebot> angebote) {
        super(MenuType.GENERIC_9x3, id, inv, box, 3);
        this.box = box;
        this.angebote = angebote;
    }

    public static void oeffnen(ServerPlayer p) {
        List<VillagerHandel.Angebot> angebote = VillagerHandel.angebote();
        SimpleContainer box = new SimpleContainer(27);
        fuellen(p, box, angebote);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new HaendlerMenu(id, inv, box, angebote), Component.literal("Villager-Haendler")));
    }

    private static void fuellen(ServerPlayer p, Container box, List<VillagerHandel.Angebot> angebote) {
        CrownsData d = CrownsData.get(p.getServer());
        Nation n = d.nationOf(p.getUUID());
        for (int i = 0; i < box.getContainerSize(); i++) box.setItem(i, ItemStack.EMPTY);
        for (int i = 0; i < angebote.size() && i < 18; i++) {
            VillagerHandel.Angebot a = angebote.get(i);
            SpawnEggItem ei = SpawnEggItem.byId(a.typ());
            ItemStack s = new ItemStack(ei != null ? ei : Items.EMERALD);
            s.setHoverName(Component.literal(a.name()).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GOLD)));
            List<String> lore = new ArrayList<>();
            if (n == null) {
                lore.add(json("Nur fuer Buerger einer Nation", ChatFormatting.RED));
            } else {
                lore.add(json("Linksklick: " + Msg.money(VillagerHandel.geldpreis(d, n.key(), a)), ChatFormatting.GREEN));
                Map<net.minecraft.world.item.Item, Integer> roh = VillagerHandel.rohstoffpreis(d, n.key(), a);
                if (!roh.isEmpty()) lore.add(json("Rechtsklick: " + VillagerHandel.rohstoffText(roh), ChatFormatting.AQUA));
            }
            lore(s, lore);
            box.setItem(i, s);
        }
        ItemStack info = new ItemStack(Items.BELL);
        info.setHoverName(Component.literal(n == null ? "Keine Nation" : n.name).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.YELLOW)));
        List<String> lore = new ArrayList<>();
        if (n != null) {
            lore.add(json("Staedte: " + StadtService.anzahl(d, n.key()), ChatFormatting.GRAY));
            lore.add(json("Villager: " + VillagerHandel.grenzeText(d, n.key()), ChatFormatting.GRAY));
            lore.add(json(String.format(java.util.Locale.GERMANY, "Preisaufschlag durch Nachfrage: %+.0f %%", (VillagerHandel.faktor(d, n.key()) - 1) * 100), ChatFormatting.GRAY));
            lore.add(json("Mehr Staedte = mehr Villager (/stadt gruenden)", ChatFormatting.DARK_GRAY));
        }
        lore(info, lore);
        box.setItem(22, info);
    }

    private static String json(String text, ChatFormatting farbe) {
        return Component.Serializer.toJson(Component.literal(text).withStyle(st -> st.withItalic(false).withColor(farbe)));
    }

    private static void lore(ItemStack s, List<String> lines) {
        CompoundTag display = s.getOrCreateTagElement("display");
        ListTag l = new ListTag();
        for (String line : lines) l.add(StringTag.valueOf(line));
        display.put("Lore", l);
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (slot >= 0 && slot < 27) {
            if (player instanceof ServerPlayer sp && slot < angebote.size() && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) {
                VillagerHandel.Angebot a = angebote.get(slot);
                String err = VillagerHandel.kaufen(sp, a, button == 1);
                if (err != null) Msg.player(sp, "§c" + err);
                else Msg.player(sp, "§a" + a.name() + " gekauft. Er steht neben dir.");
                fuellen(sp, box, angebote);
            }
            setCarried(ItemStack.EMPTY);
            broadcastFullState();
            return;
        }
        if (type == ClickType.QUICK_MOVE) return;
        super.clicked(slot, button, type, player);
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
