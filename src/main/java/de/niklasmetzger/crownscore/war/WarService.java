package de.niklasmetzger.crownscore.war;

import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.EvidenceEntry;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.data.War;
import de.niklasmetzger.crownscore.data.WarType;
import de.niklasmetzger.crownscore.discord.DiscordWebhook;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Kriegslogik: Erklaerung, Frieden, Blitzkrieg-Timer, Spawnschutz. */
public final class WarService {
    private WarService() {}

    public static War declare(MinecraftServer server, Nation angreifer, Nation verteidiger, WarType art, String grund, String erklaertVon) {
        CrownsData d = CrownsData.get(server);
        War w = new War();
        w.id = d.nextWarId++;
        w.angreifer = angreifer.key();
        w.verteidiger = verteidiger.key();
        w.art = art;
        w.grund = grund;
        w.start = TimeUtil.now();
        d.wars.add(w);
        d.setDirty();

        String text = "Hiermit erklaert " + angreifer.name + " " + verteidiger.name + " den " + artMitArtikel(art) + ". Grund: " + grund;
        Msg.broadcast(server, "§c§lKRIEGSERKLAERUNG §r§e" + text);
        String discord = ":crossed_swords: **Kriegserklaerung Nr. " + w.id + "**\n"
                + "**" + angreifer.name + "** erklaert **" + verteidiger.name + "** den **" + art.anzeige + "**.\n"
                + "Grund: " + grund + "\nErklaert von " + erklaertVon + " am " + TimeUtil.fmt(w.start);
        if (art == WarType.BLITZKRIEG) {
            discord += "\nBlitzkrieg: Der erste Angriff muss innerhalb von " + CrownsConfig.BLITZKRIEG_MINUTEN.get() + " Minuten erfolgen.";
        }
        if (!art.kampfErlaubt()) {
            discord += "\nIn dieser Kriegsart ist kein offener Kampf erlaubt.";
        }
        DiscordWebhook.kriegsnews(discord);

        EvidenceEntry e = new EvidenceEntry();
        e.zeit = w.start;
        e.typ = "KRIEG";
        e.taeterName = erklaertVon;
        e.taeter = de.niklasmetzger.crownscore.util.Names.lookup(server, erklaertVon).orElse(null);
        e.taeterNation = angreifer.name;
        e.gebietNation = verteidiger.name;
        e.detail = art.anzeige + " erklaert. Grund: " + grund;
        d.addEvidence(e);
        return w;
    }

    public static void end(MinecraftServer server, War w, String ergebnis, boolean discord) {
        CrownsData d = CrownsData.get(server);
        w.aktiv = false;
        w.ende = TimeUtil.now();
        w.ergebnis = ergebnis;
        d.setDirty();
        String a = d.anzeige(w.angreifer);
        String v = d.anzeige(w.verteidiger);
        Msg.broadcast(server, "§aDer " + w.art.anzeige + " zwischen " + a + " und " + v + " ist beendet: " + ergebnis);
        if (discord) {
            DiscordWebhook.kriegsnews(":dove: **" + w.art.anzeige + " Nr. " + w.id + " beendet**\n"
                    + a + " / " + v + ": " + ergebnis + "\nDauer: " + TimeUtil.duration(w.ende - w.start));
        }
    }

    /** Blitzkrieg ohne Angriff in der Frist verfaellt. */
    public static void everySecond(MinecraftServer server) {
        CrownsData d = CrownsData.get(server);
        long now = TimeUtil.now();
        long frist = CrownsConfig.BLITZKRIEG_MINUTEN.get() * 60_000L;
        for (War w : d.wars) {
            if (w.aktiv && w.art == WarType.BLITZKRIEG && w.ersterKampf == 0 && now - w.start > frist) {
                end(server, w, "Blitzkrieg verfallen, kein Angriff innerhalb von " + CrownsConfig.BLITZKRIEG_MINUTEN.get() + " Minuten", true);
            }
        }
    }

    public static boolean inSpawnZone(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD) return false;
        int radius = CrownsConfig.SPAWN_RADIUS.get();
        if (radius <= 0) return false;
        BlockPos center;
        if (CrownsConfig.SPAWN_WELTSPAWN.get()) {
            center = p.server.overworld().getSharedSpawnPos();
        } else {
            center = new BlockPos(CrownsConfig.SPAWN_X.get(), 64, CrownsConfig.SPAWN_Z.get());
        }
        double dx = p.getX() - (center.getX() + 0.5);
        double dz = p.getZ() - (center.getZ() + 0.5);
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    private static String artMitArtikel(WarType art) {
        return switch (art) {
            case POLITISCHE_SPANNUNGEN -> "Zustand Politischer Spannungen";
            case KALTER_KRIEG -> "Kalten Krieg";
            case HEISSER_KRIEG -> "Heissen Krieg";
            case TOTALER_KRIEG -> "Totalen Krieg";
            default -> art.anzeige;
        };
    }
}
