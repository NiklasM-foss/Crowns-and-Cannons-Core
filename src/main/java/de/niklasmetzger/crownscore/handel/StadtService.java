package de.niklasmetzger.crownscore.handel;

import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.Stadt;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.List;
import java.util.Locale;

/**
 * Staedte: Antrag mit Pruefung (Betten, Glocke, Abstand, eigenes Gebiet), Bestaetigung durch einen Admin.
 * Nur bestaetigte Staedte zaehlen fuer die Grenze beim Villager-Haendler.
 */
public final class StadtService {
    private StadtService() {}

    /** Ergebnis einer Pruefung am Standort. */
    public record Pruefung(int betten, boolean glocke, Stadt naechste, double abstand, boolean eigenesGebiet) {
        public String fehler() {
            if (betten < CrownsConfig.STADT_BETTEN.get()) return "Zu wenige Betten: " + betten + " von " + CrownsConfig.STADT_BETTEN.get() + ".";
            if (CrownsConfig.STADT_GLOCKE.get() && !glocke) return "Es fehlt eine Glocke als Dorfmittelpunkt.";
            if (naechste != null && abstand < CrownsConfig.STADT_ABSTAND.get()) {
                return "Zu nah an " + naechste.name + " (" + (int) abstand + " Bloecke, mindestens " + CrownsConfig.STADT_ABSTAND.get() + ").";
            }
            if (CrownsConfig.STADT_EIGENES_GEBIET.get() && !eigenesGebiet) return "Das ist nicht das Gebiet deiner Nation.";
            return null;
        }
    }

    /** Zaehlt Betten (Kopfteil) und Glocken im Umkreis um den Spieler. */
    public static Pruefung pruefen(ServerPlayer p, Nation n) {
        return pruefen(p.serverLevel(), p.blockPosition(), n);
    }

    /** Pruefung an einer beliebigen Stelle (z.B. aus der World Map), laedt die Chunks bei Bedarf. */
    public static Pruefung pruefen(ServerLevel level, BlockPos c, Nation n) {
        int r = CrownsConfig.STADT_RADIUS.get();
        int betten = 0;
        boolean glocke = false;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int y0 = Math.max(level.getMinBuildHeight(), c.getY() - 24), y1 = Math.min(level.getMaxBuildHeight() - 1, c.getY() + 24);
        for (int x = c.getX() - r; x <= c.getX() + r; x++) {
            for (int z = c.getZ() - r; z <= c.getZ() + r; z++) {
                level.getChunk(x >> 4, z >> 4);
                for (int y = y0; y <= y1; y++) {
                    BlockState s = level.getBlockState(m.set(x, y, z));
                    if (s.isAir()) continue;
                    if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) betten++;
                    else if (s.is(Blocks.BELL)) glocke = true;
                }
            }
        }
        CrownsData d = CrownsData.get(level.getServer());
        String dim = level.dimension().location().toString();
        Stadt naechste = null;
        double best = Double.MAX_VALUE;
        for (Stadt s : d.staedte) {
            if (!s.dim.equals(dim)) continue;
            double dist = Math.sqrt(s.pos.distSqr(c));
            if (dist < best) {
                best = dist;
                naechste = s;
            }
        }
        String hier = de.niklasmetzger.crownscore.claims.Claims.nationAt(level, c);
        return new Pruefung(betten, glocke, naechste, best, n != null && n.key().equals(hier));
    }

    /** Liegt der Chunk im Gebiet der Stadt? */
    public static boolean imGebiet(Stadt s, String dim, int cx, int cz) {
        int r = CrownsConfig.STADT_GEBIET.get();
        return s.dim.equals(dim) && Math.abs((s.pos.getX() >> 4) - cx) <= r && Math.abs((s.pos.getZ() >> 4) - cz) <= r;
    }

    /** Anerkannte Stadt, in deren Gebiet der Chunk liegt, oder null. */
    public static Stadt an(CrownsData d, String dim, int cx, int cz) {
        for (Stadt s : d.staedte) if (s.bestaetigt && imGebiet(s, dim, cx, cz)) return s;
        return null;
    }

    /** Alle Chunks des Stadtgebiets. */
    public static List<net.minecraft.world.level.ChunkPos> chunks(Stadt s) {
        int r = CrownsConfig.STADT_GEBIET.get();
        List<net.minecraft.world.level.ChunkPos> out = new java.util.ArrayList<>();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) out.add(new net.minecraft.world.level.ChunkPos((s.pos.getX() >> 4) + dx, (s.pos.getZ() >> 4) + dz));
        return out;
    }

    /** Anerkannte Staedte, deren Mitte im Land liegt. */
    public static List<Stadt> imLand(CrownsData d, String code) {
        List<Stadt> out = new java.util.ArrayList<>();
        for (Stadt s : d.staedte) {
            if (!s.bestaetigt || !s.dim.equals(net.minecraft.world.level.Level.OVERWORLD.location().toString())) continue;
            var c = de.niklasmetzger.crownscore.world.WorldMap.atChunk(s.pos.getX() >> 4, s.pos.getZ() >> 4);
            if (c != null && c.code().equalsIgnoreCase(code)) out.add(s);
        }
        return out;
    }

    private static final java.util.Map<java.util.UUID, Long> LETZTE_ANFRAGE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Antrag ueber das Rechtsklick-Menue der World Map. */
    public static void anfrageAusKarte(ServerPlayer p, String name, int x, int z) {
        long now = System.currentTimeMillis();
        Long last = LETZTE_ANFRAGE.get(p.getUUID());
        if (last != null && now - last < 15_000) {
            de.niklasmetzger.crownscore.util.Msg.player(p, "\u00a7cBitte kurz warten, bevor du den naechsten Antrag stellst.");
            return;
        }
        LETZTE_ANFRAGE.put(p.getUUID(), now);
        CrownsData d = CrownsData.get(p.getServer());
        Nation n = d.nationOf(p.getUUID());
        if (n == null || !n.isBeamter(p.getUUID())) {
            de.niklasmetzger.crownscore.util.Msg.player(p, "\u00a7cStaedte beantragen duerfen nur Staatschef und Beamte einer Nation.");
            return;
        }
        if (p.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            de.niklasmetzger.crownscore.util.Msg.player(p, "\u00a7cStaedte gibt es nur in der Oberwelt.");
            return;
        }
        ServerLevel level = p.serverLevel();
        boolean aufKarte = !de.niklasmetzger.crownscore.world.WorldMap.active()
                || ((x >> 4) >= de.niklasmetzger.crownscore.world.WorldMap.minChunkX() && (x >> 4) <= de.niklasmetzger.crownscore.world.WorldMap.maxChunkX()
                && (z >> 4) >= de.niklasmetzger.crownscore.world.WorldMap.minChunkZ() && (z >> 4) <= de.niklasmetzger.crownscore.world.WorldMap.maxChunkZ());
        if (!level.getWorldBorder().isWithinBounds(x, z) || !aufKarte) {
            de.niklasmetzger.crownscore.util.Msg.player(p, "\u00a7cDiese Stelle liegt ausserhalb der Karte.");
            return;
        }
        if (!n.key().equals(de.niklasmetzger.crownscore.claims.Claims.nationAt(level, new net.minecraft.world.level.ChunkPos(x >> 4, z >> 4)))) {
            de.niklasmetzger.crownscore.util.Msg.player(p, "\u00a7cDiese Stelle liegt nicht im Gebiet von " + n.name + ".");
            return;
        }
        int r = CrownsConfig.STADT_RADIUS.get();
        for (int cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++) {
            for (int cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++) {
                net.minecraft.world.level.chunk.ChunkAccess ca = level.getChunk(cx, cz, net.minecraft.world.level.chunk.ChunkStatus.EMPTY, true);
                if (ca == null || !ca.getStatus().isOrAfter(net.minecraft.world.level.chunk.ChunkStatus.FULL)) {
                    de.niklasmetzger.crownscore.util.Msg.player(p, "\u00a7cDort war noch niemand, das Gebiet ist nicht erkundet. Eine Stadt braucht gebaute Haeuser.");
                    return;
                }
            }
        }
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        String err = gruenden(p, n, name, new BlockPos(x, y, z));
        de.niklasmetzger.crownscore.util.Msg.player(p, err == null ? "\u00a7aAntrag fuer " + name.trim() + " bei " + x + " " + z
                + " gestellt. Ein Admin prueft ihn, die Entscheidung kommt ins /postfach." : "\u00a7c" + err);
    }

    public static Stadt byName(CrownsData d, String name) {
        for (Stadt s : d.staedte) if (s.name.equalsIgnoreCase(name)) return s;
        return null;
    }

    /** Stellt den Antrag auf eine Stadt, null = ok, sonst Fehler. Admins und Discord bekommen Bescheid. */
    public static String gruenden(ServerPlayer p, Nation n, String name) {
        return gruenden(p, n, name, p.blockPosition());
    }

    /** Antrag fuer eine Stelle, die nicht der Standort sein muss (Rechtsklick auf der World Map). */
    public static String gruenden(ServerPlayer p, Nation n, String name, BlockPos ort) {
        CrownsData d = CrownsData.get(p.getServer());
        name = name.trim();
        if (name.length() < 2 || name.length() > 32) return "Der Name braucht 2 bis 32 Zeichen.";
        if (!name.matches("[\\p{L}\\p{N}][\\p{L}\\p{N} '\\-]*")) return "Im Namen sind nur Buchstaben, Ziffern, Leerzeichen, Bindestrich und Apostroph erlaubt.";
        if (byName(d, name) != null) return "Eine Stadt namens " + name + " gibt es schon.";
        for (Stadt o : d.staedte) {
            if (!o.bestaetigt && o.nation.equals(n.key())) return n.name + " hat schon einen offenen Antrag (" + o.name + "). Erst wenn ein Admin entschieden hat, geht der naechste.";
        }
        Pruefung pr = pruefen(p.serverLevel(), ort, n);
        String err = pr.fehler();
        if (err != null) return err;
        Stadt s = new Stadt();
        s.name = name;
        s.nation = n.key();
        s.dim = p.level().dimension().location().toString();
        s.pos = ort.immutable();
        s.gegruendet = TimeUtil.now();
        s.von = p.getUUID();
        s.betten = pr.betten();
        s.glocke = pr.glocke();
        d.staedte.add(s);
        d.setDirty();
        melden(p.getServer(), s, n, p.getGameProfile().getName());
        Postfach.an(p.getServer(), p.getUUID(), "Stadtantrag " + s.name, "Dein Antrag auf die Stadt " + s.name
                + " ist eingegangen. Ein Admin prueft ihn, die Entscheidung kommt hier ins Postfach.", null);
        return null;
    }

    private static void melden(net.minecraft.server.MinecraftServer server, Stadt s, Nation n, String von) {
        String wo = s.pos.getX() + " " + s.pos.getY() + " " + s.pos.getZ();
        String link = "";
        if (AntragService.aktiv()) {
            String[] ids = AntragService.neueIds();
            s.antragId = ids[0];
            CrownsData.get(server).setDirty();
            AntragService.melden(ids[0], ids[1], "stadt", "Stadt " + s.name, n.name, von, wo + " (" + s.dim + ")",
                    List.of(s.betten + " Betten im Umkreis von " + CrownsConfig.STADT_RADIUS.get() + " Bloecken",
                            s.glocke ? "Glocke vorhanden" : "keine Glocke",
                            "Staedte der Nation bisher: " + anzahl(CrownsData.get(server), n.key())));
            String adminKanal = de.niklasmetzger.crownscore.config.CrownsConfig.WEBHOOK_ANTRAEGE.get();
            if (adminKanal != null && !adminKanal.isBlank()) {
                link = AntragService.link(ids[0], ids[1]);
            } else {
                de.niklasmetzger.crownscore.CrownsCore.LOGGER.warn("Kein Webhook fuer Antraege gesetzt (webhooks.antraege): der Entscheidungslink geht nicht in einen oeffentlichen Kanal.");
            }
        }
        for (ServerPlayer admin : server.getPlayerList().getPlayers()) {
            if (!de.niklasmetzger.crownscore.util.Perms.isAdmin(admin)) continue;
            net.minecraft.network.chat.MutableComponent c = net.minecraft.network.chat.Component.literal(
                    de.niklasmetzger.crownscore.util.Msg.PREFIX + "§e" + n.name + " beantragt die Stadt " + s.name + " bei " + wo
                            + " (" + s.betten + " Betten, von " + von + "). ");
            c.append(knopf("[Hinreisen]", "/tp @s " + wo, net.minecraft.ChatFormatting.AQUA)).append(" ");
            c.append(knopf("[Annehmen]", "/cccore staedte annehmen \"" + s.name + "\"", net.minecraft.ChatFormatting.GREEN)).append(" ");
            c.append(knopf("[Ablehnen]", "/cccore staedte ablehnen \"" + s.name + "\"", net.minecraft.ChatFormatting.RED));
            admin.sendSystemMessage(c);
        }
        de.niklasmetzger.crownscore.discord.DiscordWebhook.antraege(":house: **Stadtantrag: " + s.name + "** (" + n.name + ")\n"
                + "Ort: " + wo + " (" + s.dim + "), " + s.betten + " Betten" + (s.glocke ? ", Glocke vorhanden" : "") + ", beantragt von " + von + "\n"
                + (link.isEmpty() ? "" : "Im Browser entscheiden: <" + link + ">\n")
                + "Im Spiel: /cccore staedte annehmen \"" + s.name + "\" oder ablehnen \"" + s.name + "\" <Grund>, oder im /postfach");
    }

    private static net.minecraft.network.chat.Component knopf(String text, String cmd, net.minecraft.ChatFormatting farbe) {
        return net.minecraft.network.chat.Component.literal(text).withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(farbe)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                        cmd.startsWith("/tp") ? net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND : net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, cmd))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        net.minecraft.network.chat.Component.literal(cmd))));
    }

    /** Admin bestaetigt einen Antrag. null = ok. */
    public static String annehmen(net.minecraft.server.MinecraftServer server, String name, String admin, boolean imSpiel) {
        CrownsData d = CrownsData.get(server);
        Stadt s = byName(d, name);
        if (s == null) return "Keine Stadt namens " + name + ".";
        if (s.bestaetigt) return s.name + " ist schon bestaetigt.";
        s.bestaetigt = true;
        s.bestaetigtVon = admin;
        d.setDirty();
        Nation n = d.nations.get(s.nation);
        String nation = n == null ? s.nation : n.name;
        de.niklasmetzger.crownscore.util.Msg.broadcast(server, "§a" + s.name + " ist jetzt eine anerkannte Stadt von " + nation + ".");
        if (n != null) Postfach.nation(server, n, "Stadt anerkannt", s.name + " ist jetzt eine anerkannte Stadt von " + nation + " (bestaetigt von " + admin
                + "). " + nation + " hat damit " + anzahl(d, n.key()) + " Staedte, Villager-Grenze: " + VillagerHandel.grenzeText(d, n.key()) + ".", null);
        if (imSpiel) AntragService.erledigt(s.antragId, "annehmen", admin, "");
        de.niklasmetzger.crownscore.discord.DiscordWebhook.antraege(":white_check_mark: Stadt **" + s.name + "** (" + nation + ") von " + admin + " bestaetigt.");
        return null;
    }

    /** Text aus dem Browser: keine Farbcodes, keine Zeilenumbrueche, keine Discord-Erwaehnungen, begrenzte Laenge. */
    static String sauber(String t, int max) {
        if (t == null) return "";
        t = t.replace("\u00a7", "").replaceAll("[\\r\\n\\t]+", " ").replace("@", "@\u200b").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** Admin lehnt einen Antrag ab oder loest eine Stadt auf. null = ok. */
    public static String ablehnen(net.minecraft.server.MinecraftServer server, String name, String admin, String grund, boolean imSpiel) {
        CrownsData d = CrownsData.get(server);
        Stadt s = byName(d, name);
        if (s == null) return "Keine Stadt namens " + name + ".";
        grund = sauber(grund, 200);
        admin = sauber(admin, 48);
        d.staedte.remove(s);
        WappenStore.loeschen(s);
        d.setDirty();
        Nation n = d.nations.get(s.nation);
        String text = (s.bestaetigt ? "Die Stadt " + s.name + " wurde aufgeloest" : "Der Stadtantrag " + s.name + " wurde abgelehnt")
                + (grund == null || grund.isBlank() ? "." : ": " + grund);
        if (n != null) {
            de.niklasmetzger.crownscore.util.Msg.nation(server, n, "§e" + text);
            Postfach.nation(server, n, s.bestaetigt ? "Stadt aufgeloest" : "Stadtantrag abgelehnt", text + " (" + admin + ")", null);
        }
        if (imSpiel && !s.bestaetigt) AntragService.erledigt(s.antragId, "ablehnen", admin, grund);
        de.niklasmetzger.crownscore.discord.DiscordWebhook.antraege(":x: " + text + " (" + admin + ")");
        return null;
    }

    public static List<Stadt> von(CrownsData d, String nationKey) {
        return d.staedte.stream().filter(s -> s.nation.equals(nationKey)).toList();
    }

    public static List<Stadt> offen(CrownsData d) {
        return d.staedte.stream().filter(s -> !s.bestaetigt).toList();
    }

    /** Bestaetigte Staedte der Nation. */
    public static int anzahl(CrownsData d, String nationKey) {
        return (int) d.staedte.stream().filter(s -> s.bestaetigt && s.nation.equals(nationKey)).count();
    }

    public static String zeile(Stadt s) {
        return String.format(Locale.GERMANY, "%s (%d %d %d, %d Betten, %s %s)", s.name, s.pos.getX(), s.pos.getY(), s.pos.getZ(),
                s.betten, s.bestaetigt ? "anerkannt, Antrag vom" : "wartet auf Admin, Antrag vom", TimeUtil.fmtShort(s.gegruendet));
    }
}
