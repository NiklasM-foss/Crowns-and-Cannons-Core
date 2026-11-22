package de.niklasmetzger.crownscore.handel;

import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.economy.Economy;
import de.niklasmetzger.crownscore.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Villager kaufen: Angebote aus der Config, Grenze nach Anzahl der Staedte der Nation, Preis steigt mit der
 * Nachfrage und faellt mit der Zeit wieder. Bezahlt wird mit eigenem Geld oder mit Rohstoffen aus dem Inventar.
 */
public final class VillagerHandel {
    /** Ein Angebot. Rohstoffe: Item -> Anzahl, leer = nur gegen Geld. */
    public record Angebot(String id, String name, double geld, Map<Item, Integer> rohstoffe) {
        public EntityType<?> typ() {
            return ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id));
        }

        /** Gibt es die Entity (z.B. Villager Recruits installiert)? */
        public boolean verfuegbar() {
            return ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation(id));
        }
    }

    private VillagerHandel() {}

    public static List<Angebot> angebote() {
        List<Angebot> out = new ArrayList<>();
        for (String line : CrownsConfig.VKAUF_ANGEBOTE.get()) {
            try {
                String[] f = line.split(";", -1);
                if (f.length < 3) continue;
                Map<Item, Integer> roh = new java.util.LinkedHashMap<>();
                if (f.length > 3 && !f[3].isBlank()) {
                    for (String part : f[3].split("\\+")) {
                        String[] ia = part.trim().split("\\*");
                        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(ia[0].trim()));
                        if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
                        roh.merge(item, ia.length > 1 ? Integer.parseInt(ia[1].trim()) : 1, Integer::sum);
                    }
                }
                Angebot a = new Angebot(f[0].trim(), f[1].trim(), Double.parseDouble(f[2].trim()), roh);
                if (a.verfuegbar()) out.add(a);
            } catch (Exception e) {
                CrownsCore.LOGGER.warn("Villager-Angebot in der Config nicht lesbar: {} ({})", line, e.toString());
            }
        }
        return out;
    }

    public static Angebot angebot(String idOderName) {
        for (Angebot a : angebote()) {
            if (a.id().equalsIgnoreCase(idOderName) || a.name().equalsIgnoreCase(idOderName)
                    || a.id().substring(a.id().indexOf(':') + 1).equalsIgnoreCase(idOderName)) return a;
        }
        return null;
    }

    /** Wie viele Villager darf die Nation insgesamt kaufen? -1 = unbegrenzt. */
    public static int grenze(CrownsData d, String nationKey) {
        int staedte = StadtService.anzahl(d, nationKey);
        int ab = CrownsConfig.VKAUF_UNBEGRENZT_AB.get();
        if (ab > 0 && staedte >= ab) return -1;
        List<? extends Integer> g = CrownsConfig.VKAUF_GRENZEN.get();
        if (g.isEmpty()) return -1;
        return g.get(Math.min(staedte, g.size() - 1));
    }

    public static int gekauft(CrownsData d, String nationKey) {
        return d.villagerGekauft.getOrDefault(nationKey, 0);
    }

    /** Faktor auf den Grundpreis durch die Nachfrage der Nation. */
    public static double faktor(CrownsData d, String nationKey) {
        return 1.0 + CrownsConfig.VKAUF_AUFSCHLAG.get() / 100.0 * d.villagerNachfrage.getOrDefault(nationKey, 0.0);
    }

    public static double geldpreis(CrownsData d, String nationKey, Angebot a) {
        return Math.round(a.geld() * faktor(d, nationKey) * 100.0) / 100.0;
    }

    public static Map<Item, Integer> rohstoffpreis(CrownsData d, String nationKey, Angebot a) {
        double f = faktor(d, nationKey);
        Map<Item, Integer> m = new java.util.LinkedHashMap<>();
        a.rohstoffe().forEach((i, n) -> m.put(i, (int) Math.ceil(n * f)));
        return m;
    }

    public static String rohstoffText(Map<Item, Integer> m) {
        List<String> parts = new ArrayList<>();
        m.forEach((i, n) -> parts.add(n + "x " + new ItemStack(i).getHoverName().getString()));
        return String.join(", ", parts);
    }

    private static int imInventar(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static void entnehmen(ServerPlayer p, Item item, int anzahl) {
        for (ItemStack s : p.getInventory().items) {
            if (anzahl <= 0) break;
            if (!s.is(item)) continue;
            int k = Math.min(anzahl, s.getCount());
            s.shrink(k);
            anzahl -= k;
        }
        p.getInventory().setChanged();
    }

    /** Kauf ausfuehren. null = gekauft, sonst Fehler. */
    public static String kaufen(ServerPlayer p, Angebot a, boolean mitRohstoffen) {
        if (!CrownsConfig.VKAUF_AKTIV.get()) return "Villager kaufen ist ausgeschaltet.";
        MinecraftServer server = p.getServer();
        CrownsData d = CrownsData.get(server);
        Nation n = d.nationOf(p.getUUID());
        if (n == null) return "Villager kaufen nur Buerger einer Nation, sie zaehlen fuer die Nation. /nation beitreten <Land>";
        int grenze = grenze(d, n.key());
        int gekauft = gekauft(d, n.key());
        if (grenze >= 0 && gekauft >= grenze) {
            int staedte = StadtService.anzahl(d, n.key());
            return n.name + " hat schon " + gekauft + " von " + grenze + " Villagern gekauft (" + staedte + " Staedte). "
                    + "Mehr Staedte gruenden (/stadt gruenden) hebt die Grenze.";
        }
        Entity e = erzeugen(p, a);
        if (e == null) return "Der Villager konnte nicht erzeugt werden, es wurde nichts bezahlt.";
        if (mitRohstoffen) {
            if (a.rohstoffe().isEmpty()) return a.name() + " gibt es nur gegen Geld.";
            Map<Item, Integer> preis = rohstoffpreis(d, n.key(), a);
            for (Map.Entry<Item, Integer> pe : preis.entrySet()) {
                if (imInventar(p, pe.getKey()) < pe.getValue()) return "Dir fehlen Rohstoffe. Preis: " + rohstoffText(preis) + ".";
            }
            preis.forEach((i, k) -> entnehmen(p, i, k));
        } else {
            double preis = geldpreis(d, n.key(), a);
            if (!Economy.get().withdraw(p, preis)) {
                return "Nicht genug Geld: " + Msg.money(preis) + " noetig, du hast " + Msg.money(Economy.get().balance(p)) + ".";
            }
        }
        p.serverLevel().addFreshEntity(e);
        d.villagerGekauft.merge(n.key(), 1, Integer::sum);
        d.villagerNachfrage.merge(n.key(), 1.0, Double::sum);
        d.setDirty();
        CrownsCore.LOGGER.info("Villagerkauf: {} ({}) kauft {} mit {}", p.getGameProfile().getName(), n.name, a.id(), mitRohstoffen ? "Rohstoffen" : "Geld");
        return null;
    }

    /** Erzeugt die Entity neben dem Spieler (noch nicht in der Welt). Rekruten von Villager Recruits gehoeren gleich dem Kaeufer. */
    static Entity erzeugen(ServerPlayer p, Angebot a) {
        EntityType<?> typ = a.typ();
        if (typ == null) return null;
        ServerLevel level = p.serverLevel();
        Entity e = typ.create(level);
        if (e == null) return null;
        BlockPos pos = p.blockPosition();
        e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p.getYRot() + 180, 0);
        if (e instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null, null);
            mob.setPersistenceRequired();
        }
        if (a.id().startsWith("recruits:")) besitzerSetzen(e, p);
        return e;
    }

    /** Villager Recruits: Besitzer setzen, ohne die Mod zum Kompilieren zu brauchen. */
    private static void besitzerSetzen(Entity e, ServerPlayer p) {
        try {
            Class<?> c = e.getClass();
            c.getMethod("setOwnerUUID", Optional.class).invoke(e, Optional.of(p.getUUID()));
            c.getMethod("setIsOwned", boolean.class).invoke(e, true);
        } catch (Exception ex) {
            CrownsCore.LOGGER.warn("Rekrut {} gehoert nicht automatisch dem Kaeufer: {}", e.getType(), ex.toString());
        }
    }

    /** Aus dem Scheduler, einmal pro Minute: Nachfrage sinkt. */
    public static void everyMinute(MinecraftServer server) {
        CrownsData d = CrownsData.get(server);
        if (d.villagerNachfrage.isEmpty()) return;
        double proTag = CrownsConfig.VKAUF_ERHOLUNG.get() / 100.0;
        double f = Math.pow(1.0 - Math.min(proTag, 0.999999), 1.0 / 1440.0);
        d.villagerNachfrage.replaceAll((k, v) -> v * f);
        d.villagerNachfrage.values().removeIf(v -> v < 0.01);
        d.setDirty();
    }

    public static String grenzeText(CrownsData d, String nationKey) {
        int g = grenze(d, nationKey);
        int k = gekauft(d, nationKey);
        return g < 0 ? k + " gekauft, unbegrenzt" : String.format(Locale.GERMANY, "%d von %d gekauft", k, g);
    }
}
