package de.niklasmetzger.crownscore.world;

import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.Besatzung;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.War;
import de.niklasmetzger.crownscore.discord.DiscordWebhook;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.TimeUtil;
import de.niklasmetzger.crownscore.world.WorldMap.Country;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Laender einnehmen: freie Laender kaufen, Laender anderer Nationen im Krieg besetzen. Eine Besetzung
 * stellt einen Besatzungsblock ins Land. Steht er die ganze Haltezeit, wechselt das Land den Besitzer,
 * wird er zerstoert, ist die Besetzung gescheitert und die Gebuehr weg.
 */
public final class EroberungService {
    private static final long TAG = 24L * 60 * 60 * 1000;
    /** Abgebaute oder gesprengte Besatzungsbloecke (Besatzungs-Nr -> wer, leer = unbekannt). */
    private static final Map<Integer, String> ZERSTOERT_VON = new HashMap<>();

    private EroberungService() {}

    public static double kaufpreis(Country c) {
        return CrownsConfig.EROBERUNG_KAUF_GRUNDPREIS.get() + CrownsConfig.EROBERUNG_KAUF_PRO_CHUNK.get() * c.fields();
    }

    public static double besatzungsgebuehr(Country c) {
        return CrownsConfig.EROBERUNG_GEBUEHR_GRUND.get() + CrownsConfig.EROBERUNG_GEBUEHR_PRO_CHUNK.get() * c.fields();
    }

    /** Nation, der das Land gerade gehoert, oder null. */
    public static Nation besitzer(CrownsData d, Country c) {
        String key = WorldMap.nationFor(d, c);
        return key == null ? null : d.nations.get(key);
    }

    /** Ist das Land frei (niemand oder eine Nation ohne Spieler)? */
    public static boolean frei(CrownsData d, Country c) {
        Nation n = besitzer(d, c);
        return n == null || !n.gespielt();
    }

    /** Darf {@code wer} das Land kaufen ({@code kauf}) oder besetzen? null = ja, sonst der Grund. */
    public static String pruefen(CrownsData d, Nation wer, Country c, boolean kauf) {
        if (c == null) return "Dieses Land gibt es auf der Weltkarte nicht.";
        Nation besitzer = besitzer(d, c);
        if (besitzer == wer) return c.name() + " gehoert schon " + wer.name + ".";
        long now = TimeUtil.now();
        Long sperre = d.landSperre.get(c.code());
        if (sperre != null && sperre > now) {
            return c.name() + " wurde gerade erst eingenommen und ist noch " + TimeUtil.duration(sperre - now) + " gesperrt.";
        }
        if (kauf) {
            if (!CrownsConfig.EROBERUNG_KAUFEN.get()) return "Laender kaufen ist auf diesem Server ausgeschaltet.";
            if (besitzer != null && besitzer.gespielt()) {
                return c.name() + " gehoert " + besitzer.name + " und wird gespielt. Das geht nur ueber eine Besetzung im Krieg (/nation besetzen).";
            }
        } else {
            if (!CrownsConfig.EROBERUNG_BESETZEN.get()) return "Besetzungen sind auf diesem Server ausgeschaltet.";
            if (besitzer == null || !besitzer.gespielt()) return c.name() + " ist frei, das kannst du kaufen: /nation land kaufen " + c.code();
            if (CrownsConfig.EROBERUNG_NUR_IM_KRIEG.get()) {
                War w = d.activeWar(wer.key(), besitzer.key());
                if (w == null) return "Du bist nicht im Krieg mit " + besitzer.name + ". Besetzen geht nur im Krieg (/krieg erklaeren).";
                if (CrownsConfig.EROBERUNG_NUR_KAMPFKRIEG.get() && !w.art.kampfErlaubt()) {
                    return "Im " + w.art.anzeige + " ist kein Kampf erlaubt, also auch keine Besetzung. Erst die Kriegsart wechseln.";
                }
            }
            int schutz = CrownsConfig.EROBERUNG_SCHUTZ_TAGE.get();
            if (schutz > 0 && besitzer.gegruendet + schutz * TAG > now) {
                return besitzer.name + " ist eine neue Nation und noch " + TimeUtil.duration(besitzer.gegruendet + schutz * TAG - now) + " geschuetzt.";
            }
            if (CrownsConfig.EROBERUNG_STAMMLAND_SCHUTZ.get() && c.code().equalsIgnoreCase(besitzer.land) && besitzer.laender.size() > 1) {
                return c.name() + " ist das Stammland von " + besitzer.name + " und erst einnehmbar, wenn die Nation keine anderen Laender mehr hat.";
            }
            long laufend = d.besatzungen.stream().filter(b -> b.angreifer.equals(wer.key())).count();
            if (laufend >= CrownsConfig.EROBERUNG_GLEICHZEITIG.get()) {
                return wer.name + " hat schon " + laufend + " laufende Besetzung(en), mehr sind nicht erlaubt.";
            }
            for (Besatzung b : d.besatzungen) {
                if (b.land.equalsIgnoreCase(c.code())) return c.name() + " wird schon von " + d.anzeige(b.angreifer) + " besetzt.";
            }
        }
        int abstand = CrownsConfig.EROBERUNG_NACHBAR_ABSTAND.get();
        if (abstand >= 0 && !wer.laender.isEmpty() && !WorldMap.nahe(c.code(), wer.laender, abstand)) {
            return c.name() + " liegt zu weit vom Gebiet von " + wer.name + " entfernt"
                    + (abstand == 0 ? ", es muss direkt angrenzen." : " (hoechstens " + abstand + " Chunks Abstand).");
        }
        return null;
    }

    /** Freies Land kaufen. null = gekauft, sonst Fehler. */
    public static String kaufen(MinecraftServer server, Nation wer, Country c) {
        CrownsData d = CrownsData.get(server);
        String err = pruefen(d, wer, c, true);
        if (err != null) return err;
        double preis = kaufpreis(c);
        if (!de.niklasmetzger.crownscore.treasury.TreasuryService.charge(d, wer.key(), preis)) {
            return "Die Staatskasse von " + wer.name + " reicht nicht: " + Msg.money(preis) + " noetig, "
                    + Msg.money(de.niklasmetzger.crownscore.treasury.TreasuryService.stand(d, wer.key())) + " vorhanden.";
        }
        sperren(d, c);
        WeltkarteCommand.uebertragen(server, List.of(c.code()), wer, "fuer " + Msg.money(preis) + " gekauft", log());
        return null;
    }

    public static Block block() {
        Block b = null;
        try {
            b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(CrownsConfig.EROBERUNG_BLOCK.get().trim()));
        } catch (Exception ignored) {
        }
        return b == null || b == Blocks.AIR ? Blocks.RED_BANNER : b;
    }

    /** Besetzung an der Position des Spielers beginnen. null = begonnen, sonst Fehler. */
    public static String besetzen(ServerPlayer p, Nation wer) {
        MinecraftServer server = p.getServer();
        CrownsData d = CrownsData.get(server);
        if (p.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return "Besetzen geht nur in der Oberwelt.";
        Country c = WorldMap.atChunk(p.blockPosition().getX() >> 4, p.blockPosition().getZ() >> 4);
        if (c == null) return "Hier ist kein Land auf der Weltkarte (Meer). Stell dich in das Land, das du besetzen willst.";
        String err = pruefen(d, wer, c, false);
        if (err != null) return err;
        ServerLevel level = p.serverLevel();
        BlockPos pos = p.blockPosition();
        if (!level.getBlockState(pos).canBeReplaced()) pos = pos.above();
        if (!level.getBlockState(pos).canBeReplaced() || !level.getBlockState(pos.below()).isSolid()) {
            return "Hier kann der Besatzungsblock nicht stehen. Stell dich auf festen Boden mit freiem Platz.";
        }
        double gebuehr = besatzungsgebuehr(c);
        if (!de.niklasmetzger.crownscore.treasury.TreasuryService.charge(d, wer.key(), gebuehr)) {
            return "Die Besatzungsgebuehr von " + Msg.money(gebuehr) + " ist nicht in der Staatskasse.";
        }
        Nation verteidiger = besitzer(d, c);
        level.setBlockAndUpdate(pos, block().defaultBlockState());
        Besatzung b = new Besatzung();
        b.id = d.nextBesatzungId++;
        b.angreifer = wer.key();
        b.verteidiger = verteidiger.key();
        b.land = c.code();
        b.dim = level.dimension().location().toString();
        b.pos = pos.immutable();
        b.start = TimeUtil.now();
        b.rest = CrownsConfig.EROBERUNG_HALTEZEIT_MINUTEN.get() * 60L;
        b.von = p.getUUID();
        b.gebuehr = gebuehr;
        d.besatzungen.add(b);
        d.setDirty();
        String wo = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        String dauer = TimeUtil.duration(b.rest * 1000L);
        Msg.broadcast(server, "§c" + wer.name + " besetzt " + c.name() + " (" + verteidiger.name + ")! Der Besatzungsblock steht bei " + wo
                + ". Haelt er " + dauer + ", gehoert das Land " + wer.name + ".");
        Msg.nation(server, verteidiger, "§c§lAlarm: §c" + wer.name + " besetzt " + c.name() + ". Zerstoert den Besatzungsblock bei " + wo + "!");
        DiscordWebhook.kriegsnews(":triangular_flag_on_post: **" + wer.name + " besetzt " + c.name() + "** (" + verteidiger.name + ")\n"
                + "Besatzungsblock bei " + wo + ", Haltezeit " + dauer
                + (CrownsConfig.EROBERUNG_VERTEIDIGER_ONLINE.get() ? " (zaehlt nur, solange jemand aus " + verteidiger.name + " online ist)" : ""));
        CrownsCore.LOGGER.info("Besetzung {}: {} besetzt {} ({}) bei {}", b.id, wer.name, c.code(), verteidiger.name, wo);
        return null;
    }

    /** Besetzung beenden, ohne dass das Land wechselt. Der Block wird entfernt, die Gebuehr bleibt weg. */
    public static void abbrechen(MinecraftServer server, Besatzung b, String grund) {
        CrownsData d = CrownsData.get(server);
        if (!d.besatzungen.remove(b)) return;
        d.setDirty();
        ZERSTOERT_VON.remove(b.id);
        ServerLevel level = level(server, b);
        if (level != null && level.isLoaded(b.pos) && level.getBlockState(b.pos).is(block())) {
            level.removeBlock(b.pos, false);
        }
        Country c = WorldMap.byCode(b.land);
        String land = c == null ? b.land : c.name();
        String text = "Die Besetzung von " + land + " durch " + d.anzeige(b.angreifer) + " ist gescheitert: " + grund;
        Msg.broadcast(server, "§e" + text);
        DiscordWebhook.kriegsnews(":shield: " + text);
        CrownsCore.LOGGER.info("Besetzung {} beendet: {}", b.id, grund);
    }

    private static ServerLevel level(MinecraftServer server, Besatzung b) {
        try {
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(b.dim)));
        } catch (Exception e) {
            return null;
        }
    }

    private static void sperren(CrownsData d, Country c) {
        int tage = CrownsConfig.EROBERUNG_SPERRE_TAGE.get();
        if (tage > 0) d.landSperre.put(c.code(), TimeUtil.now() + tage * TAG);
        d.setDirty();
    }

    private static java.util.function.Consumer<String> log() {
        return CrownsCore.LOGGER::info;
    }

    private static boolean online(MinecraftServer server, CrownsData d, String nationKey) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (nationKey.equals(d.nationKeyOf(p.getUUID()))) return true;
        }
        return false;
    }

    /** Aus dem Scheduler, einmal pro Sekunde. */
    public static void everySecond(MinecraftServer server) {
        CrownsData d = CrownsData.get(server);
        if (d.besatzungen.isEmpty()) return;
        long now = TimeUtil.now();
        d.landSperre.values().removeIf(t -> t < now);
        for (Besatzung b : new ArrayList<>(d.besatzungen)) {
            Country c = WorldMap.byCode(b.land);
            Nation angreifer = d.nations.get(b.angreifer);
            Nation verteidiger = d.nations.get(b.verteidiger);
            if (c == null || angreifer == null || verteidiger == null) {
                abbrechen(server, b, "eine der Nationen gibt es nicht mehr.");
                continue;
            }
            Nation jetzt = besitzer(d, c);
            if (jetzt != verteidiger) {
                abbrechen(server, b, c.name() + " gehoert inzwischen " + (jetzt == null ? "niemandem" : jetzt.name) + ".");
                continue;
            }
            if (CrownsConfig.EROBERUNG_NUR_IM_KRIEG.get() && d.activeWar(b.angreifer, b.verteidiger) == null) {
                abbrechen(server, b, "der Krieg ist vorbei.");
                continue;
            }
            ServerLevel level = level(server, b);
            if (level == null) {
                abbrechen(server, b, "die Dimension gibt es nicht mehr.");
                continue;
            }
            boolean geladen = level.isLoaded(b.pos);
            boolean steht = geladen && level.getBlockState(b.pos).is(block());
            if (steht) ZERSTOERT_VON.remove(b.id); // Abbauen wurde verhindert (z.B. Bauschutz)
            if (geladen ? !steht : ZERSTOERT_VON.containsKey(b.id)) {
                String wer = ZERSTOERT_VON.get(b.id);
                if (wer != null && wer.isEmpty()) wer = null;
                abbrechen(server, b, "der Besatzungsblock wurde zerstoert" + (wer == null ? "." : " (von " + wer + ")."));
                continue;
            }
            if (CrownsConfig.EROBERUNG_VERTEIDIGER_ONLINE.get() && !online(server, d, b.verteidiger)) continue;
            b.rest--;
            d.setDirty();
            if (b.rest > 0) {
                if (b.rest % 900 == 0) {
                    String text = "Besetzung von " + c.name() + ": noch " + TimeUtil.duration(b.rest * 1000L) + ", Block bei "
                            + b.pos.getX() + " " + b.pos.getY() + " " + b.pos.getZ() + ".";
                    Msg.nation(server, angreifer, "§6" + text);
                    Msg.nation(server, verteidiger, "§c" + text);
                }
                continue;
            }
            d.besatzungen.remove(b);
            ZERSTOERT_VON.remove(b.id);
            if (level.isLoaded(b.pos) && level.getBlockState(b.pos).is(block())) level.removeBlock(b.pos, false);
            sperren(d, c);
            d.besatzungen.removeIf(o -> o.land.equalsIgnoreCase(b.land));
            WeltkarteCommand.uebertragen(server, List.of(c.code()), angreifer, "besetzt und eingenommen", log());
            CrownsCore.LOGGER.info("Besetzung {} erfolgreich: {} hat {} eingenommen", b.id, angreifer.name, c.code());
        }
    }

    /** Kurze Zeile fuer Listen. */
    public static String zeile(CrownsData d, Besatzung b) {
        Country c = WorldMap.byCode(b.land);
        return String.format(Locale.GERMANY, "Nr. %d: %s besetzt %s (%s), Block bei %d %d %d, noch %s", b.id, d.anzeige(b.angreifer),
                c == null ? b.land : c.name(), d.anzeige(b.verteidiger), b.pos.getX(), b.pos.getY(), b.pos.getZ(), TimeUtil.duration(b.rest * 1000L));
    }

    public static class Events {
        @SubscribeEvent
        public void onBreak(BlockEvent.BreakEvent e) {
            if (!(e.getLevel() instanceof ServerLevel level)) return;
            CrownsData d = CrownsData.get(level.getServer());
            for (Besatzung b : d.besatzungen) {
                if (b.pos.equals(e.getPos()) && b.dim.equals(level.dimension().location().toString())) {
                    ZERSTOERT_VON.put(b.id, e.getPlayer() == null ? "" : e.getPlayer().getGameProfile().getName());
                }
            }
        }

        @SubscribeEvent
        public void onExplosion(net.minecraftforge.event.level.ExplosionEvent.Detonate e) {
            if (!(e.getLevel() instanceof ServerLevel level)) return;
            CrownsData d = CrownsData.get(level.getServer());
            if (d.besatzungen.isEmpty()) return;
            String dim = level.dimension().location().toString();
            for (Besatzung b : d.besatzungen) {
                if (b.dim.equals(dim) && e.getAffectedBlocks().contains(b.pos)) ZERSTOERT_VON.put(b.id, "einer Explosion");
            }
        }
    }
}
