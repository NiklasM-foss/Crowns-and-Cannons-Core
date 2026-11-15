package de.niklasmetzger.crownscore.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.world.WorldMap.Country;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /nation weltkarte: Laender der Erdkarte als Nationen anlegen und die Grenzen auf der Minimap zeigen. */
public final class WeltkarteCommand {
    private WeltkarteCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("nation").then(Commands.literal("weltkarte")
                .executes(WeltkarteCommand::info)
                .then(Commands.literal("info").executes(WeltkarteCommand::info))
                .then(Commands.literal("import").executes(WeltkarteCommand::importieren))
                .then(Commands.literal("minimap").then(Commands.argument("modus", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("umriss", "grenze", "flaeche", "aus"), b))
                        .executes(WeltkarteCommand::minimap)))));
    }

    private static boolean opac() {
        return ModList.get().isLoaded("openpartiesandclaims");
    }

    /** Vergleichsform fuer Namen: Saudi-Arabien, saudi_arabien und Saudi Arabien sind gleich. */
    static String norm(String s) {
        String t = s.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return t.replaceAll("[^a-z0-9]", "");
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CommandSourceStack src = ctx.getSource();
        if (!WorldMap.loaded()) {
            Msg.err(src, "Die Weltkarte konnte nicht geladen werden, siehe Server-Log.");
            return 0;
        }
        CrownsData d = CommandUtil.data(ctx);
        long linked = d.nations.values().stream().filter(n -> n.land != null && !n.land.isEmpty()).count();
        Msg.info(src, "§fWeltkarte");
        Msg.line(src, " §7Laender auf der Karte: §f" + WorldMap.countries().size() + "§7, mit Nation verknuepft: §f" + linked);
        Msg.line(src, String.format(Locale.GERMANY, " §7Projektion: Plattkarte, Laenge 0 bei x=%d, Breite 0 bei z=%d, %.4f Bloecke pro Grad",
                CrownsConfig.KARTE_MITTE_X.get(), CrownsConfig.KARTE_MITTE_Z.get(), WorldMap.blocksPerDegree()));
        Msg.line(src, " §7Kartenbereich: Chunks x " + WorldMap.minChunkX() + " bis " + WorldMap.maxChunkX() + ", z " + WorldMap.minChunkZ() + " bis " + WorldMap.maxChunkZ());
        Msg.line(src, " §7Pruefen: an einer bekannten Stadt §e/gebiet hier§7, die Grad muessen passen. Sonst in der Config [weltkarte] nachstellen.");
        if (opac()) Msg.line(src, " §7Minimap: " + de.niklasmetzger.crownscore.claims.OpacBorders.status());
        return 1;
    }

    private static int importieren(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CommandSourceStack src = ctx.getSource();
        if (!WorldMap.loaded()) {
            Msg.err(src, "Die Weltkarte konnte nicht geladen werden, siehe Server-Log.");
            return 0;
        }
        CrownsData d = CommandUtil.data(ctx);
        int neu = 0, verknuepft = 0, schon = 0;
        List<String> konflikte = new ArrayList<>();
        for (Country c : WorldMap.countries()) {
            Nation match = null;
            for (Nation n : d.nations.values()) {
                if (c.code().equalsIgnoreCase(n.land)) {
                    match = n;
                    break;
                }
            }
            if (match != null) {
                schon++;
                continue;
            }
            for (Nation n : d.nations.values()) {
                if ((n.land == null || n.land.isEmpty()) && (n.kuerzel.equalsIgnoreCase(c.code()) || norm(n.name).equals(norm(c.name())))) {
                    match = n;
                    break;
                }
            }
            if (match != null) {
                match.land = c.code();
                verknuepft++;
                continue;
            }
            if (d.nations.containsKey(Nation.key(c.name())) || d.nation(c.code()) != null) {
                konflikte.add(c.name() + " (" + c.code() + ")");
                continue;
            }
            Nation n = new Nation(c.name(), c.code());
            n.land = c.code();
            d.nations.put(n.key(), n);
            neu++;
        }
        d.setDirty();
        Msg.ok(src, "Weltkarte uebernommen: " + neu + " Nationen neu angelegt, " + verknuepft + " bestehende mit ihrem Land verknuepft, "
                + schon + " waren schon verknuepft.");
        if (!konflikte.isEmpty()) {
            Msg.line(src, " §eNicht angelegt, Name oder Kuerzel schon an eine andere Nation vergeben: §f" + String.join(", ", konflikte));
        }
        if (neu > 0) Msg.broadcast(src.getServer(), neu + " Laender der Erdkarte sind jetzt als Nationen angelegt. /nation liste");
        Msg.line(src, " §7Staatschefs setzen mit §e/nation staatschef <Land> <Spieler>§7, Grenzen auf der Minimap mit §e/nation weltkarte minimap umriss");
        CrownsCore.LOGGER.info("Weltkarte: {} neu, {} verknuepft, {} schon verknuepft, {} Konflikte", neu, verknuepft, schon, konflikte.size());
        return neu + verknuepft;
    }

    private static int minimap(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CommandSourceStack src = ctx.getSource();
        if (!opac()) {
            Msg.err(src, "Die Grenzen auf der Minimap brauchen Open Parties and Claims, Xaero's Minimap zeigt dann die Claims an.");
            return 0;
        }
        if (!WorldMap.loaded()) {
            Msg.err(src, "Die Weltkarte konnte nicht geladen werden, siehe Server-Log.");
            return 0;
        }
        de.niklasmetzger.crownscore.claims.OpacBorders.Mode mode;
        try {
            mode = de.niklasmetzger.crownscore.claims.OpacBorders.Mode.valueOf(StringArgumentType.getString(ctx, "modus").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            Msg.err(src, "Modus: umriss (Umrisse mit Kueste), grenze (nur Landgrenzen), flaeche (ganze Laender) oder aus.");
            return 0;
        }
        MinecraftServer server = src.getServer();
        String err = de.niklasmetzger.crownscore.claims.OpacBorders.start(server, mode, msg -> {
            Msg.info(src, msg);
            CrownsCore.LOGGER.info(msg);
        });
        if (err != null) {
            Msg.err(src, err);
            return 0;
        }
        Msg.info(src, "Grenzanzeige wird aufgebaut (" + mode.name().toLowerCase() + "). Das laeuft im Hintergrund, Fortschritt kommt hier.");
        return 1;
    }

    /** Aus dem Scheduler, jeden Tick. */
    public static void tick(MinecraftServer server) {
        if (opac()) de.niklasmetzger.crownscore.claims.OpacBorders.tick(server);
    }
}
