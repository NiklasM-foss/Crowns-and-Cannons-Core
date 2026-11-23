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

    private EroberungService() {}

    /** Beim Serverstart: Zaehler aus einer vorherigen Welt verwerfen. */
    public static void reset() {
    }

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

    /** Darf {@code wer} die Stadt besetzen? null = ja. */
    public static String pruefenStadt(CrownsData d, Nation wer, de.niklasmetzger.crownscore.data.Stadt s) {
        if (!CrownsConfig.EROBERUNG_BESETZEN.get()) return "Besetzungen sind auf diesem Server ausgeschaltet.";
        if (s.nation.equals(wer.key())) return s.name + " gehoert schon " + wer.name + ".";
        Nation besitzer = d.nations.get(s.nation);
        if (besitzer == null) return "Die Nation von " + s.name + " gibt es nicht mehr.";
        long now = TimeUtil.now();
        Long sperre = d.landSperre.get("stadt:" + s.name.toLowerCase(Locale.ROOT));
        if (sperre != null && sperre > now) return s.name + " wurde gerade erst eingenommen und ist noch " + TimeUtil.duration(sperre - now) + " gesperrt.";
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
        long laufend = d.besatzungen.stream().filter(b -> b.angreifer.equals(wer.key())).count();
        if (laufend >= CrownsConfig.EROBERUNG_GLEICHZEITIG.get()) return wer.name + " hat schon " + laufend + " laufende Besetzung(en), mehr sind nicht erlaubt.";
        for (Besatzung b : d.besatzungen) {
            if (b.stadt.equalsIgnoreCase(s.name)) return s.name + " wird schon von " + d.anzeige(b.angreifer) + " besetzt.";
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
        int pcx = p.blockPosition().getX() >> 4, pcz = p.blockPosition().getZ() >> 4;
        de.niklasmetzger.crownscore.data.Stadt stadt = de.niklasmetzger.crownscore.handel.StadtService.an(d, p.level().dimension().location().toString(), pcx, pcz);
        Country c = WorldMap.atChunk(pcx, pcz);
        if (stadt != null && !stadt.nation.equals(wer.key())) {
            String err = pruefenStadt(d, wer, stadt);
            if (err != null) return err;
        } else {
            stadt = null;
            if (c == null) return "Hier ist kein Land auf der Weltkarte (Meer). Stell dich in eine Stadt oder ein Land, das du besetzen willst.";
            Nation halter = besitzer(d, c);
            if (halter != null && halter != wer) {
                List<String> namen = de.niklasmetzger.crownscore.handel.StadtService.imLand(d, c.code()).stream()
                        .filter(s -> !s.nation.equals(wer.key())).map(s -> s.name).toList();
                if (!namen.isEmpty()) {
                    return c.name() + " hat Staedte. Besetzt sie einzeln, wer alle haelt, bekommt das ganze Land: " + String.join(", ", namen)
                            + ". Stell dich in das Stadtgebiet (auf der Karte umrandet).";
                }
            }
            String err = pruefen(d, wer, c, false);
            if (err != null) return err;
        }
        ServerLevel level = p.serverLevel();
        BlockPos pos = p.blockPosition();
        String hierGehoert = de.niklasmetzger.crownscore.claims.Claims.nationAt(level, pos);
        String sollGehoeren = stadt != null ? stadt.nation : (besitzer(d, c) == null ? null : besitzer(d, c).key());
        if (sollGehoeren != null && !sollGehoeren.equals(hierGehoert)) {
            return "Hier ist kein Gebiet von " + d.anzeige(sollGehoeren) + (hierGehoert == null ? "" : " (gehoert " + d.anzeige(hierGehoert) + ")")
                    + ". Der Besatzungsblock muss im Gebiet der Verteidiger stehen.";
        }
        // In einem Spieler-Claim (OPAC) koennten die Verteidiger den Block nicht abbauen: nur freies oder Staatsgebiet
        java.util.UUID claimer = de.niklasmetzger.crownscore.claims.Claims.opacOwner(level, new net.minecraft.world.level.ChunkPos(pos));
        if (claimer != null && sollGehoeren != null && d.nations.get(sollGehoeren) != null
                && !claimer.equals(d.nations.get(sollGehoeren).staat()) && !d.nations.get(sollGehoeren).mitglieder.containsKey(claimer)) {
            return "Hier liegt ein geschuetzter Claim, den die Verteidiger nicht abbauen koennten. Such dir eine andere Stelle.";
        }
        if (!level.getBlockState(pos).canBeReplaced()) pos = pos.above();
        if (!level.getBlockState(pos).canBeReplaced() || !level.getBlockState(pos.below()).isSolid()) {
            return "Hier kann der Besatzungsblock nicht stehen. Stell dich auf festen Boden mit freiem Platz.";
        }
        double gebuehr = stadt != null ? CrownsConfig.EROBERUNG_GEBUEHR_GRUND.get()
                + CrownsConfig.EROBERUNG_GEBUEHR_PRO_CHUNK.get() * de.niklasmetzger.crownscore.handel.StadtService.chunks(stadt).size() : besatzungsgebuehr(c);
        if (!de.niklasmetzger.crownscore.treasury.TreasuryService.charge(d, wer.key(), gebuehr)) {
            return "Die Besatzungsgebuehr von " + Msg.money(gebuehr) + " ist nicht in der Staatskasse.";
        }
        Nation verteidiger = stadt != null ? d.nations.get(stadt.nation) : besitzer(d, c);
        level.setBlockAndUpdate(pos, block().defaultBlockState());
        Besatzung b = new Besatzung();
        b.id = d.nextBesatzungId++;
        b.angreifer = wer.key();
        b.verteidiger = verteidiger.key();
        b.land = c == null ? "" : c.code();
        b.stadt = stadt == null ? "" : stadt.name;
        b.dim = level.dimension().location().toString();
        b.pos = pos.immutable();
        b.start = TimeUtil.now();
        b.rest = CrownsConfig.EROBERUNG_HALTEZEIT_MINUTEN.get() * 60L;
        b.von = p.getUUID();
        b.gebuehr = gebuehr;
        int[] start = angriffsStart(d, wer, stadt != null ? stadt.pos : pos);
        b.vonX = start[0];
        b.vonZ = start[1];
        d.besatzungen.add(b);
        d.setDirty();
        String wo = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        String dauer = TimeUtil.duration(b.rest * 1000L);
        String ziel = stadt != null ? "die Stadt " + stadt.name : c.name();
        Msg.broadcast(server, "§c" + wer.name + " besetzt " + ziel + " (" + verteidiger.name + ")! Der Besatzungsblock steht bei " + wo
                + ". Haelt er " + dauer + ", gehoert " + ziel + " " + wer.name + ".");
        Msg.nation(server, verteidiger, "§c§lAlarm: §c" + wer.name + " besetzt " + ziel + ". Zerstoert den Besatzungsblock bei " + wo + "!");
        de.niklasmetzger.crownscore.handel.Postfach.nation(server, verteidiger, "Alarm: " + ziel + " wird besetzt",
                wer.name + " hat einen Besatzungsblock bei " + wo + " gesetzt. Haelt er " + dauer + ", verliert ihr " + ziel + ". Zerstoert ihn!", null);
        DiscordWebhook.kriegsnews(":triangular_flag_on_post: **" + wer.name + " besetzt " + ziel + "** (" + verteidiger.name + ")\n"
                + "Besatzungsblock bei " + wo + ", Haltezeit " + dauer
                + (CrownsConfig.EROBERUNG_VERTEIDIGER_ONLINE.get() ? " (zaehlt nur, solange jemand aus " + verteidiger.name + " online ist)" : ""));
        CrownsCore.LOGGER.info("Besetzung {}: {} besetzt {} ({}) bei {}", b.id, wer.name, ziel, verteidiger.name, wo);
        return null;
    }

    /** Startpunkt der Angriffslinie: naechste eigene Stadt oder naechster Punkt im eigenen Land, sonst das Ziel selbst. */
    static int[] angriffsStart(CrownsData d, Nation wer, BlockPos ziel) {
        double best = Double.MAX_VALUE;
        int[] r = {ziel.getX(), ziel.getZ()};
        for (de.niklasmetzger.crownscore.data.Stadt s : d.staedte) {
            if (!s.bestaetigt || !s.nation.equals(wer.key())) continue;
            double dist = s.pos.distSqr(ziel);
            if (dist < best) {
                best = dist;
                r = new int[]{s.pos.getX(), s.pos.getZ()};
            }
        }
        double[] p = WorldMap.naechsterPunkt(wer.laender, WorldMap.lon(ziel.getX()), WorldMap.lat(ziel.getZ()));
        if (p != null) {
            int x = (int) Math.round(CrownsConfig.KARTE_MITTE_X.get() + p[0] * WorldMap.blocksPerDegree());
            int z = (int) Math.round(CrownsConfig.KARTE_MITTE_Z.get() - p[1] * WorldMap.blocksPerDegree());
            double dist = (double) (x - ziel.getX()) * (x - ziel.getX()) + (double) (z - ziel.getZ()) * (z - ziel.getZ());
            if (dist < best) r = new int[]{x, z};
        }
        return r;
    }

    /** Besetzung beenden, ohne dass das Land wechselt. Der Block wird entfernt, die Gebuehr bleibt weg. */
    public static void abbrechen(MinecraftServer server, Besatzung b, String grund) {
        CrownsData d = CrownsData.get(server);
        if (!d.besatzungen.remove(b)) return;
        ANGEHALTEN.remove(b.id);
        d.setDirty();
        b.zerstoertVon = null;
        ServerLevel level = level(server, b);
        if (level != null && level.getChunkAt(b.pos) != null && level.getBlockState(b.pos).is(block())) {
            level.removeBlock(b.pos, false);
        }
        Country c = WorldMap.byCode(b.land);
        String land = !b.stadt.isEmpty() ? b.stadt : c == null ? b.land : c.name();
        String text = "Die Besetzung von " + land + " durch " + d.anzeige(b.angreifer) + " ist gescheitert: " + grund;
        Msg.broadcast(server, "§e" + text);
        for (String k : List.of(b.angreifer, b.verteidiger)) {
            Nation x = d.nations.get(k);
            if (x != null) de.niklasmetzger.crownscore.handel.Postfach.nation(server, x, "Besetzung von " + land + " beendet", text, null);
        }
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
            if (!b.stadt.isEmpty()) {
                stadtSekunde(server, d, b);
                continue;
            }
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
            if (steht) b.zerstoertVon = null; // Abbauen wurde verhindert (z.B. Bauschutz)
            if (geladen ? !steht : b.zerstoertVon != null) {
                String wer = b.zerstoertVon;
                if (wer != null && wer.isEmpty()) wer = null;
                abbrechen(server, b, "der Besatzungsblock wurde zerstoert" + (wer == null ? "." : " (von " + wer + ")."));
                continue;
            }
            // Claim-Daten von OPAC gibt es auch fuer entladene Chunks, die Pause gilt deshalb immer
            int fc = fremderClaim(server, level, d, b);
            if (fc == 1) continue;
            if (aufgabe(server, d, b, fc == 2)) continue;
            if (fc == 2) continue; // angehalten: Angreifer muessen vor Ort bleiben, die Uhr steht
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
            erfolg(server, b, "besetzt und eingenommen");
        }
    }

    /** Besetzung gelungen (Haltezeit vorbei oder Verteidiger haben aufgegeben): Stadt bzw. Land wechselt. */
    public static void erfolg(MinecraftServer server, Besatzung b, String wie) {
        CrownsData d = CrownsData.get(server);
        if (!d.besatzungen.remove(b)) return;
        ANGEHALTEN.remove(b.id);
        d.setDirty();
        b.zerstoertVon = null;
        ServerLevel level = level(server, b);
        if (level != null && level.getChunkAt(b.pos) != null && level.getBlockState(b.pos).is(block())) level.removeBlock(b.pos, false);
        Nation angreifer = d.nations.get(b.angreifer), verteidiger = d.nations.get(b.verteidiger);
        if (angreifer == null || verteidiger == null) return;
        if (!b.stadt.isEmpty()) {
            de.niklasmetzger.crownscore.data.Stadt s = de.niklasmetzger.crownscore.handel.StadtService.byName(d, b.stadt);
            if (s != null) stadtUebergeben(server, s, angreifer, verteidiger, "erobert");
            return;
        }
        Country c = WorldMap.byCode(b.land);
        if (c == null) return;
        sperren(d, c);
        d.besatzungen.removeIf(o -> o.land.equalsIgnoreCase(b.land) && o.stadt.isEmpty());
        WeltkarteCommand.uebertragen(server, List.of(c.code()), angreifer, wie, log());
        CrownsCore.LOGGER.info("Besetzung {} erfolgreich: {} hat {} eingenommen", b.id, angreifer.name, c.code());
    }


    /** Mitte, um die herum die Anwesenheit zaehlt: Stadtmitte oder Besatzungsblock. */
    static BlockPos mitte(CrownsData d, Besatzung b) {
        if (!b.stadt.isEmpty()) {
            de.niklasmetzger.crownscore.data.Stadt s = de.niklasmetzger.crownscore.handel.StadtService.byName(d, b.stadt);
            if (s != null) return s.pos;
        }
        return b.pos;
    }

    private static int inDerNaehe(MinecraftServer server, CrownsData d, String nationKey, String dim, BlockPos mitte, int r) {
        int n = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!nationKey.equals(d.nationKeyOf(p.getUUID())) || p.isSpectator()) continue;
            if (!p.level().dimension().location().toString().equals(dim)) continue;
            double dx = p.getX() - mitte.getX(), dz = p.getZ() - mitte.getZ();
            if (dx * dx + dz * dz <= (double) r * r) n++;
        }
        return n;
    }

    /** Aufgeben pruefen. true = Besetzung ist damit erledigt (abgebrochen oder gewonnen). */
    private static boolean aufgabe(MinecraftServer server, CrownsData d, Besatzung b, boolean angehalten) {
        int r = CrownsConfig.EROBERUNG_AUFGABE_RADIUS.get();
        if (r <= 0) return false;
        BlockPos m = mitte(d, b);
        String ziel = !b.stadt.isEmpty() ? b.stadt : b.land;
        // Steht die Uhr (Verteidiger offline), muessen die Angreifer auch nicht vor Ort warten
        boolean uhrLaeuft = !CrownsConfig.EROBERUNG_VERTEIDIGER_ONLINE.get() || online(server, d, b.verteidiger);
        if (!uhrLaeuft) {
            b.angreiferWeg = 0;
        } else if (inDerNaehe(server, d, b.angreifer, b.dim, m, r) == 0) {
            int weg = ++b.angreiferWeg;
            d.setDirty();
            if (weg == 1) Msg.nation(server, d.nations.get(b.angreifer), "§eNiemand von euch ist mehr in " + r + " Bloecken um " + ziel
                    + ". Kommt zurueck, sonst gilt die Besetzung in " + CrownsConfig.EROBERUNG_ANGREIFER_KULANZ.get() + " Sekunden als aufgegeben.");
            if (weg >= CrownsConfig.EROBERUNG_ANGREIFER_KULANZ.get()) {
                abbrechen(server, b, "die Angreifer haben aufgegeben (niemand mehr in " + r + " Bloecken).");
                return true;
            }
        } else {
            b.angreiferWeg = 0;
        }
        // Waehrend der Pause koennen die Verteidiger den Block im fremden Claim nicht abbauen, ihre Kulanz steht
        if (angehalten) {
            // Zaehler bleibt stehen (weder hoch noch zurueck), sonst setzt ein kurzer fremder Claim die Kulanz zurueck
        } else if (online(server, d, b.verteidiger) && inDerNaehe(server, d, b.verteidiger, b.dim, m, r) == 0) {
            int weg = ++b.verteidigerWeg;
            d.setDirty();
            int kulanz = CrownsConfig.EROBERUNG_VERTEIDIGER_KULANZ.get() * 60;
            if (weg == 1 || (kulanz - weg) % 300 == 0 && kulanz - weg > 0) {
                Msg.nation(server, d.nations.get(b.verteidiger), "§c" + ziel + " wird belagert und niemand von euch verteidigt! Noch "
                        + TimeUtil.duration((kulanz - weg) * 1000L) + ", dann gilt " + ziel + " als aufgegeben.");
            }
            if (weg >= kulanz) {
                Msg.broadcast(server, "§c" + d.anzeige(b.verteidiger) + " hat " + ziel + " kampflos aufgegeben.");
                erfolg(server, b, "kampflos eingenommen");
                return true;
            }
        } else {
            b.verteidigerWeg = 0;
        }
        return false;
    }

    /** Besetzungen, die wegen des Claims eines Dritten gerade angehalten sind (nur fuer die Meldungen). */
    private static final java.util.Set<Integer> ANGEHALTEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Liegt auf dem Block jetzt ein Claim, den die Verteidiger nicht abbauen koennen (weder Staatskonto noch Mitglied der
     * Verteidiger)? Claimt die Angreiferseite selbst, ist die Besetzung gescheitert (Gebuehr verfaellt). Claimt ein
     * Dritter, haelt die Uhr an, bis der Claim weg ist: Weder ein Strohmann der Angreifer (keine Erstattung, kein
     * Fortschritt) noch ein Freund der Verteidiger (kein Abbruch, keine verlorene Gebuehr) gewinnt dadurch etwas. Bei
     * Streit entscheidet die Spielleitung, die Meldung geht deshalb auch in die Kriegsnachrichten.
     * @return 0 weiter wie bisher, 1 beendet, 2 angehalten
     */
    private static int fremderClaim(MinecraftServer server, ServerLevel level, CrownsData d, Besatzung b) {
        java.util.UUID claimer = de.niklasmetzger.crownscore.claims.Claims.opacOwner(level, new net.minecraft.world.level.ChunkPos(b.pos));
        Nation v = d.nations.get(b.verteidiger);
        if (claimer == null || v == null || claimer.equals(v.staat()) || v.mitglieder.containsKey(claimer)) {
            if (ANGEHALTEN.remove(b.id)) {
                Msg.broadcast(server, "§eDie Besetzung durch " + d.anzeige(b.angreifer) + " laeuft weiter, der fremde Claim ist weg.");
            }
            return 0;
        }
        Nation a = d.nations.get(b.angreifer);
        if (a != null && (claimer.equals(a.staat()) || a.mitglieder.containsKey(claimer))) {
            ANGEHALTEN.remove(b.id);
            abbrechen(server, b, "die Angreifer haben den Besatzungsblock eingeclaimt.");
            return 1;
        }
        if (ANGEHALTEN.add(b.id)) {
            String text = "Die Besetzung von " + d.anzeige(b.verteidiger) + " durch " + d.anzeige(b.angreifer)
                    + " ist angehalten: Auf dem Besatzungsblock liegt jetzt der Claim von " + claimName(server, d, claimer)
                    + ". Sie laeuft weiter, sobald der Claim weg ist.";
            Msg.broadcast(server, "§e" + text);
            DiscordWebhook.kriegsnews(":pause_button: " + text);
        }
        return 2;
    }

    /** Name des Claim-Besitzers fuer Meldungen: bei einem Staatskonto die Nation, sonst der Spielername. */
    public static String claimName(MinecraftServer server, CrownsData d, java.util.UUID claimer) {
        for (java.util.Map.Entry<String, Nation> e : d.nations.entrySet()) {
            if (claimer.equals(e.getValue().staat())) return d.anzeige(e.getKey());
        }
        return de.niklasmetzger.crownscore.util.Names.of(server, claimer);
    }

    /** /nation aufgeben: Angreifer brechen ab, Verteidiger geben die Stadt bzw. das Land auf. */
    public static String aufgeben(MinecraftServer server, ServerPlayer p, int nr) {
        CrownsData d = CrownsData.get(server);
        Nation n = d.nationOf(p.getUUID());
        if (n == null || !n.isBeamter(p.getUUID())) return "Aufgeben duerfen nur Staatschef und Beamte.";
        if (nr <= 0) {
            List<Besatzung> beteiligt = d.besatzungen.stream().filter(b -> b.angreifer.equals(n.key()) || b.verteidiger.equals(n.key())).toList();
            if (beteiligt.size() > 1) {
                return n.name + " ist an mehreren Besetzungen beteiligt, bitte die Nummer angeben: "
                        + String.join(", ", beteiligt.stream().map(b -> "Nr. " + b.id + " (" + (!b.stadt.isEmpty() ? b.stadt : b.land)
                        + (b.angreifer.equals(n.key()) ? ", ihr greift an" : ", ihr verteidigt") + ")").toList())
                        + ". /nation aufgeben <Nr>";
            }
        }
        for (Besatzung b : new ArrayList<>(d.besatzungen)) {
            if (nr > 0 && b.id != nr) continue;
            String ziel = !b.stadt.isEmpty() ? b.stadt : b.land;
            if (b.angreifer.equals(n.key())) {
                abbrechen(server, b, n.name + " hat aufgegeben.");
                return null;
            }
            if (b.verteidiger.equals(n.key())) {
                Msg.broadcast(server, "§c" + n.name + " gibt " + ziel + " auf.");
                erfolg(server, b, "nach Aufgabe eingenommen");
                return null;
            }
        }
        return nr > 0 ? "Deine Nation ist an Besetzung Nr. " + nr + " nicht beteiligt." : "Deine Nation ist an keiner Besetzung beteiligt.";
    }

    private static void stadtSekunde(MinecraftServer server, CrownsData d, Besatzung b) {
        de.niklasmetzger.crownscore.data.Stadt s = de.niklasmetzger.crownscore.handel.StadtService.byName(d, b.stadt);
        Nation angreifer = d.nations.get(b.angreifer);
        Nation verteidiger = d.nations.get(b.verteidiger);
        if (s == null || angreifer == null || verteidiger == null) {
            abbrechen(server, b, "die Stadt oder eine der Nationen gibt es nicht mehr.");
            return;
        }
        if (!s.nation.equals(b.verteidiger)) {
            abbrechen(server, b, s.name + " gehoert inzwischen jemand anderem.");
            return;
        }
        if (CrownsConfig.EROBERUNG_NUR_IM_KRIEG.get() && d.activeWar(b.angreifer, b.verteidiger) == null) {
            abbrechen(server, b, "der Krieg ist vorbei.");
            return;
        }
        ServerLevel level = level(server, b);
        if (level == null) {
            abbrechen(server, b, "die Dimension gibt es nicht mehr.");
            return;
        }
        boolean geladen = level.isLoaded(b.pos);
        boolean steht = geladen && level.getBlockState(b.pos).is(block());
        if (steht) b.zerstoertVon = null;
        if (geladen ? !steht : b.zerstoertVon != null) {
            String wer = b.zerstoertVon;
            if (wer != null && wer.isEmpty()) wer = null;
            abbrechen(server, b, "der Besatzungsblock wurde zerstoert" + (wer == null ? "." : " (von " + wer + ")."));
            return;
        }
        int fc = fremderClaim(server, level, d, b);
        if (fc == 1) return;
        if (aufgabe(server, d, b, fc == 2)) return;
        if (fc == 2) return; // angehalten: Angreifer muessen vor Ort bleiben, die Uhr steht
        if (CrownsConfig.EROBERUNG_VERTEIDIGER_ONLINE.get() && !online(server, d, b.verteidiger)) return;
        b.rest--;
        d.setDirty();
        if (b.rest > 0) {
            if (b.rest % 900 == 0) {
                String text = "Besetzung von " + s.name + ": noch " + TimeUtil.duration(b.rest * 1000L) + ", Block bei "
                        + b.pos.getX() + " " + b.pos.getY() + " " + b.pos.getZ() + ".";
                Msg.nation(server, angreifer, "§6" + text);
                Msg.nation(server, verteidiger, "§c" + text);
            }
            return;
        }
        erfolg(server, b, "erobert");
    }

    /** Stadt geht an den Eroberer; haelt er danach alle Staedte des Landes, bekommt er das ganze Land. */
    public static void stadtUebergeben(MinecraftServer server, de.niklasmetzger.crownscore.data.Stadt s, Nation neu, Nation alt, String wie) {
        CrownsData d = CrownsData.get(server);
        s.nation = neu.key();
        int tage = CrownsConfig.EROBERUNG_SPERRE_TAGE.get();
        if (tage > 0) d.landSperre.put("stadt:" + s.name.toLowerCase(Locale.ROOT), TimeUtil.now() + tage * TAG);
        java.util.Map<Long, String> map = d.claims.computeIfAbsent(s.dim, k -> new HashMap<>());
        List<net.minecraft.world.level.ChunkPos> chunks = de.niklasmetzger.crownscore.handel.StadtService.chunks(s);
        for (net.minecraft.world.level.ChunkPos cp : chunks) map.put(cp.toLong(), neu.key());
        d.setDirty();
        String text = neu.name + " hat die Stadt " + s.name + " von " + alt.name + " " + wie + ".";
        Msg.broadcast(server, "§c" + text);
        DiscordWebhook.kriegsnews(":crossed_swords: " + text);
        de.niklasmetzger.crownscore.handel.Postfach.nation(server, neu, "Neue Stadt: " + s.name, text, null);
        de.niklasmetzger.crownscore.handel.Postfach.nation(server, alt, "Stadt verloren", text, null);
        boolean opac = net.minecraftforge.fml.ModList.get().isLoaded("openpartiesandclaims");
        if (opac && s.dim.equals(net.minecraft.world.level.Level.OVERWORLD.location().toString())) {
            de.niklasmetzger.crownscore.claims.OpacTerritory.syncNation(server, neu);
            de.niklasmetzger.crownscore.claims.OpacTerritory.syncChunks(server, chunks);
        }
        CrownsCore.LOGGER.info("Stadt {} geht von {} an {}", s.name, alt.name, neu.name);
        Country c = WorldMap.atChunk(s.pos.getX() >> 4, s.pos.getZ() >> 4);
        if (c == null) return;
        Nation halter = besitzer(d, c);
        if (halter == null || halter == neu) return;
        // Das Land wechselt nur, wenn es der Nation gehoert, die die Stadt verloren hat, oder einem Kriegsgegner
        if (halter != alt && d.activeWar(neu.key(), halter.key()) == null) return;
        List<de.niklasmetzger.crownscore.data.Stadt> alle = de.niklasmetzger.crownscore.handel.StadtService.imLand(d, c.code());
        if (alle.isEmpty() || !alle.stream().allMatch(x -> x.nation.equals(neu.key()))) {
            long rest = alle.stream().filter(x -> !x.nation.equals(neu.key())).count();
            Msg.nation(server, neu, "§6Noch " + rest + " Stadt/Staedte in " + c.name() + ", dann gehoert euch das ganze Land.");
            return;
        }
        if (CrownsConfig.EROBERUNG_STAMMLAND_SCHUTZ.get() && c.code().equalsIgnoreCase(halter.land) && halter.laender.size() > 1) {
            Msg.nation(server, neu, "§6" + c.name() + " ist das Stammland von " + halter.name + ". Das Land wechselt erst, wenn " + halter.name + " keine anderen Laender mehr hat, die Staedte gehoeren euch.");
            return;
        }
        int abstand = CrownsConfig.EROBERUNG_NACHBAR_ABSTAND.get();
        if (abstand >= 0 && !neu.laender.isEmpty() && !WorldMap.nahe(c.code(), neu.laender, abstand)) {
            Msg.nation(server, neu, "§6" + c.name() + " liegt zu weit von eurem Gebiet entfernt, das Land bleibt bei " + halter.name + ". Die Staedte gehoeren euch.");
            return;
        }
        sperren(d, c);
        WeltkarteCommand.uebertragen(server, List.of(c.code()), neu, "mit allen Staedten eingenommen", log());
        // Die Stadtgebiete gehoeren jetzt ohnehin zum Land, Sonderzuweisungen aufraeumen
        for (de.niklasmetzger.crownscore.data.Stadt x : alle) {
            for (net.minecraft.world.level.ChunkPos cp : de.niklasmetzger.crownscore.handel.StadtService.chunks(x)) map.remove(cp.toLong());
        }
        d.setDirty();
    }

    /** Kurze Zeile fuer Listen. */
    public static String zeile(CrownsData d, Besatzung b) {
        Country c = WorldMap.byCode(b.land);
        return String.format(Locale.GERMANY, "Nr. %d: %s besetzt %s (%s), Block bei %d %d %d, noch %s", b.id, d.anzeige(b.angreifer),
                !b.stadt.isEmpty() ? "die Stadt " + b.stadt : c == null ? b.land : c.name(), d.anzeige(b.verteidiger), b.pos.getX(), b.pos.getY(), b.pos.getZ(), TimeUtil.duration(b.rest * 1000L));
    }

    public static class EroberungEvents {
        @SubscribeEvent
        public void onBreak(BlockEvent.BreakEvent e) {
            if (!(e.getLevel() instanceof ServerLevel level)) return;
            CrownsData d = CrownsData.get(level.getServer());
            for (Besatzung b : d.besatzungen) {
                if (b.pos.equals(e.getPos()) && b.dim.equals(level.dimension().location().toString())) {
                    b.zerstoertVon = e.getPlayer() == null ? "" : e.getPlayer().getGameProfile().getName();
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
                if (b.dim.equals(dim) && e.getAffectedBlocks().contains(b.pos)) b.zerstoertVon = "einer Explosion";
            }
        }
    }
}
