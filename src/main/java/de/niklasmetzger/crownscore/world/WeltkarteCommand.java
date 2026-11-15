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
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("flaeche", "umriss", "grenze", "aus"), b))
                        .executes(WeltkarteCommand::minimap)))));
        d.register(Commands.literal("nation").then(Commands.literal("annektieren")
                .then(Commands.argument("land", StringArgumentType.string())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(WorldMap.countries().stream().map(Country::code).toList(), b))
                        .then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                                .executes(WeltkarteCommand::annektieren)))));
        d.register(Commands.literal("gebiet")
                .then(Commands.literal("zuweisen").then(Commands.argument("nation", StringArgumentType.string()).suggests(CommandUtil.NATIONS)
                        .executes(ctx -> zuweisen(ctx, 0, false))
                        .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 16))
                                .executes(ctx -> zuweisen(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"), false)))))
                .then(Commands.literal("zuruecksetzen")
                        .executes(ctx -> zuweisen(ctx, 0, true))
                        .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 16))
                                .executes(ctx -> zuweisen(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"), true)))));
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
        if (opac()) Msg.line(src, " §7Minimap: " + (d.karteModus.isEmpty() ? "noch nicht aufgebaut" : d.karteModus.toLowerCase()) + ", "
                + de.niklasmetzger.crownscore.claims.OpacTerritory.status());
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
                if (c.code().equalsIgnoreCase(n.land) || n.laender.stream().anyMatch(l -> l.equalsIgnoreCase(c.code()))) {
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
                match.laender.add(c.code());
                verknuepft++;
                continue;
            }
            if (d.nations.containsKey(Nation.key(c.name())) || d.nation(c.code()) != null) {
                konflikte.add(c.name() + " (" + c.code() + ")");
                continue;
            }
            Nation n = new Nation(c.name(), c.code());
            n.land = c.code();
            n.laender.add(c.code());
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
        Msg.line(src, " §7Staatschefs setzen mit §e/nation staatschef <Land> <Spieler>§7, Laender auf der Minimap mit §e/nation weltkarte minimap flaeche");
        CrownsCore.LOGGER.info("Weltkarte: {} neu, {} verknuepft, {} schon verknuepft, {} Konflikte", neu, verknuepft, schon, konflikte.size());
        return neu + verknuepft;
    }

    private static int minimap(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CommandSourceStack src = ctx.getSource();
        if (!opac()) {
            Msg.err(src, "Die Laender auf der Minimap brauchen Open Parties and Claims, Xaero's Minimap zeigt dann die Claims an.");
            return 0;
        }
        if (!WorldMap.loaded()) {
            Msg.err(src, "Die Weltkarte konnte nicht geladen werden, siehe Server-Log.");
            return 0;
        }
        de.niklasmetzger.crownscore.claims.OpacTerritory.Mode mode;
        try {
            mode = de.niklasmetzger.crownscore.claims.OpacTerritory.Mode.valueOf(StringArgumentType.getString(ctx, "modus").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            Msg.err(src, "Modus: flaeche (ganze Laender), umriss (Umrisse mit Kueste), grenze (nur Landgrenzen) oder aus.");
            return 0;
        }
        String err = de.niklasmetzger.crownscore.claims.OpacTerritory.start(src.getServer(), mode, null, false, report(src));
        if (err != null) {
            Msg.err(src, err);
            return 0;
        }
        Msg.info(src, "Laender werden auf der Karte aufgebaut (" + mode.name().toLowerCase() + "). Das laeuft im Hintergrund, Fortschritt kommt hier.");
        return 1;
    }

    private static java.util.function.Consumer<String> report(CommandSourceStack src) {
        return msg -> {
            Msg.info(src, msg);
            CrownsCore.LOGGER.info(msg);
        };
    }

    /** Land oder Laender aus dem Argument: Code (DEU), Laendername oder Name einer Nation (dann alle ihre Laender). */
    private static List<String> landCodes(CrownsData d, String arg) {
        Country c = WorldMap.byCode(arg);
        if (c != null) return List.of(c.code());
        for (Country x : WorldMap.countries()) if (norm(x.name()).equals(norm(arg))) return List.of(x.code());
        Nation n = d.nation(arg);
        if (n != null && !n.laender.isEmpty()) return new ArrayList<>(n.laender);
        return List.of();
    }

    /** /nation annektieren <Land> <Nation>: ganzes Land an eine Nation, die Grenze verschwindet. */
    private static int annektieren(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CommandSourceStack src = ctx.getSource();
        CrownsData d = CommandUtil.data(ctx);
        String arg = StringArgumentType.getString(ctx, "land");
        List<String> codes = landCodes(d, arg);
        if (codes.isEmpty()) {
            Msg.err(src, "Kein Land \"" + arg + "\" auf der Weltkarte. Kuerzel wie FRA oder den Laendernamen angeben.");
            return 0;
        }
        Nation ziel = CommandUtil.nation(src, d, StringArgumentType.getString(ctx, "nation"));
        if (ziel == null) return 0;
        List<Nation> vorher = new ArrayList<>();
        for (String code : codes) {
            for (Nation n : d.nations.values()) {
                if (n != ziel && n.laender.removeIf(l -> l.equalsIgnoreCase(code)) && !vorher.contains(n)) vorher.add(n);
            }
            ziel.laender.add(code);
        }
        d.setDirty();
        List<String> namen = new ArrayList<>();
        for (String code : codes) namen.add(WorldMap.byCode(code) == null ? code : WorldMap.byCode(code).name());
        List<String> alt = new ArrayList<>();
        for (Nation n : vorher) alt.add(n.name);
        boolean zurueck = codes.size() == 1 && codes.get(0).equalsIgnoreCase(ziel.land);
        String text = zurueck ? ziel.name + " bekommt " + namen.get(0) + " zurueck" + (alt.isEmpty() ? "" : " (vorher " + String.join(", ", alt) + ")") + "."
                : ziel.name + " hat " + String.join(", ", namen) + " eingenommen" + (alt.isEmpty() ? "" : " (vorher " + String.join(", ", alt) + ")") + ".";
        Msg.broadcast(src.getServer(), text);
        de.niklasmetzger.crownscore.discord.DiscordWebhook.kriegsnews(":crossed_swords: " + text);
        for (Nation n : vorher) {
            if (n.laender.isEmpty()) Msg.line(src, " §e" + n.name + " hat jetzt kein Land mehr. Die Nation bleibt bestehen, bis sie geloescht wird.");
        }
        if (opac() && !d.karteModus.isEmpty() && !d.karteModus.equals("AUS")) {
            de.niklasmetzger.crownscore.claims.OpacTerritory.syncNation(src.getServer(), ziel);
            for (Nation n : vorher) de.niklasmetzger.crownscore.claims.OpacTerritory.syncNation(src.getServer(), n);
            String err = de.niklasmetzger.crownscore.claims.OpacTerritory.start(src.getServer(),
                    de.niklasmetzger.crownscore.claims.OpacTerritory.Mode.valueOf(d.karteModus), WorldMap.chunkBounds(codes), false, report(src));
            if (err != null) Msg.err(src, "Karte nicht aktualisiert: " + err + " Danach /nation weltkarte minimap " + d.karteModus.toLowerCase());
        }
        return codes.size();
    }

    /** /gebiet zuweisen <Nation> [Radius] und /gebiet zuruecksetzen [Radius]: einzelne Chunks, z.B. nach einer Teileroberung. */
    private static int zuweisen(CommandContext<CommandSourceStack> ctx, int radius, boolean reset) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CommandSourceStack src = ctx.getSource();
        net.minecraft.server.level.ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Nation ziel = null;
        if (!reset) {
            ziel = CommandUtil.nation(src, d, StringArgumentType.getString(ctx, "nation"));
            if (ziel == null) return 0;
        }
        String dim = p.level().dimension().location().toString();
        java.util.Map<Long, String> map = d.claims.computeIfAbsent(dim, k -> new java.util.HashMap<>());
        net.minecraft.world.level.ChunkPos c = new net.minecraft.world.level.ChunkPos(p.blockPosition());
        List<net.minecraft.world.level.ChunkPos> changed = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(c.x + dx, c.z + dz);
                boolean hit = reset ? map.remove(cp.toLong()) != null : !ziel.key().equals(map.put(cp.toLong(), ziel.key()));
                if (hit) changed.add(cp);
            }
        }
        d.setDirty();
        if (opac() && p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            if (ziel != null) de.niklasmetzger.crownscore.claims.OpacTerritory.syncNation(src.getServer(), ziel);
            de.niklasmetzger.crownscore.claims.OpacTerritory.syncChunks(src.getServer(), changed);
        }
        Msg.ok(src, reset ? changed.size() + " Chunks gehoeren wieder dem Land auf der Weltkarte."
                : changed.size() + " Chunks gehoeren jetzt " + ziel.name + ".");
        return changed.size();
    }

    /** Aus dem Scheduler, jeden Tick. */
    public static void tick(MinecraftServer server) {
        if (opac()) de.niklasmetzger.crownscore.claims.OpacTerritory.tick(server);
    }
}
