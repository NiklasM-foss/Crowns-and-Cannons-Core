package de.niklasmetzger.crownscore.log;

import de.niklasmetzger.crownscore.util.Durations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parameter wie bei CoreProtect: u:Name,Name t:2h30m r:10|#global|#world|#nether a:block,+container
 * b:stone,chest e:dirt #preview #count. Baut daraus die WHERE-Bedingung.
 */
public class LogQuery {
    public final Set<String> users = new LinkedHashSet<>();
    public final Set<String> excludeUsers = new LinkedHashSet<>();
    public long since;
    public int radius = -1;
    public boolean global;
    public String dimOnly;
    public BlockPos center;
    public String centerDim;
    public final Set<LogAction> actions = EnumSet.noneOf(LogAction.class);
    public final Set<String> include = new LinkedHashSet<>();
    public final Set<String> exclude = new LinkedHashSet<>();
    public boolean preview;
    public boolean count;
    public String error;
    public final List<String> raw = new ArrayList<>();

    /** Liest die Parameter. Bei einem Fehler steht er in {@link #error}. */
    public static LogQuery parse(String input, ServerLevel level, BlockPos center) {
        LogQuery q = new LogQuery();
        q.center = center;
        q.centerDim = level == null ? null : level.dimension().location().toString();
        if (input == null) return q;
        for (String tok : input.trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            q.raw.add(tok);
            String t = tok.toLowerCase(Locale.ROOT);
            int c = t.indexOf(':');
            if (t.equals("#preview") || t.equals("#vorschau")) {
                q.preview = true;
                continue;
            }
            if (t.equals("#count") || t.equals("#anzahl")) {
                q.count = true;
                continue;
            }
            if (c <= 0) {
                q.error = "Unbekannter Parameter \"" + tok + "\". Erlaubt: u: t: r: a: b: e: #preview #count";
                return q;
            }
            String key = t.substring(0, c);
            String val = tok.substring(c + 1);
            switch (key) {
                case "u", "user", "users" -> {
                    for (String u : val.split(",")) if (!u.isBlank()) q.users.add(u.trim());
                }
                case "t", "time", "zeit" -> {
                    long d = Durations.parse(val);
                    if (d <= 0) {
                        q.error = "Ungueltige Zeit \"" + val + "\". Beispiele: t:30m, t:2h, t:3d, t:1w2d.";
                        return q;
                    }
                    q.since = System.currentTimeMillis() - d;
                }
                case "r", "radius" -> {
                    String v = val.toLowerCase(Locale.ROOT);
                    if (v.equals("#global")) q.global = true;
                    else if (v.equals("#world") || v.equals("#welt")) {
                        if (q.centerDim == null) {
                            q.error = "r:#world geht nur im Spiel. In der Konsole r:#global oder r:#overworld.";
                            return q;
                        }
                        q.dimOnly = q.centerDim;
                    } else if (v.startsWith("#")) {
                        String d = v.substring(1);
                        q.dimOnly = switch (d) {
                            case "overworld", "oberwelt" -> "minecraft:overworld";
                            case "nether" -> "minecraft:the_nether";
                            case "end" -> "minecraft:the_end";
                            default -> d.contains(":") ? d : "minecraft:" + d;
                        };
                    } else {
                        try {
                            q.radius = Integer.parseInt(v);
                        } catch (NumberFormatException e) {
                            q.error = "Ungueltiger Radius \"" + val + "\". Zahl, #global, #world oder #nether.";
                            return q;
                        }
                        if (q.radius < 0 || q.radius > 1000) {
                            q.error = "Radius zwischen 0 und 1000.";
                            return q;
                        }
                        if (q.center == null) {
                            q.error = "Ein Radius geht nur im Spiel. In der Konsole r:#global oder r:#overworld.";
                            return q;
                        }
                    }
                }
                case "a", "action", "aktion" -> {
                    for (String a : val.split(",")) {
                        Set<LogAction> s = LogAction.parse(a);
                        if (s.isEmpty()) {
                            q.error = "Unbekannte Aktion \"" + a + "\". Erlaubt: block, +block, -block, container, +container, -container, "
                                    + "item, +item, -item, kill, chat, command, session, click.";
                            return q;
                        }
                        q.actions.addAll(s);
                    }
                }
                case "b", "block", "blocks" -> {
                    for (String b : val.split(",")) if (!b.isBlank()) q.include.add(id(b));
                }
                case "e", "exclude" -> {
                    for (String b : val.split(",")) {
                        if (b.isBlank()) continue;
                        String id = id(b);
                        if (isRegistered(id)) q.exclude.add(id);
                        else q.excludeUsers.add(b.trim());
                    }
                }
                default -> {
                    q.error = "Unbekannter Parameter \"" + key + ":\". Erlaubt: u: t: r: a: b: e: #preview #count";
                    return q;
                }
            }
        }
        return q;
    }

    static String id(String s) {
        String v = s.trim().toLowerCase(Locale.ROOT);
        return v.contains(":") ? v : "minecraft:" + v;
    }

    private static boolean isRegistered(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return false;
        return BuiltInRegistries.BLOCK.containsKey(rl) || BuiltInRegistries.ITEM.containsKey(rl) || BuiltInRegistries.ENTITY_TYPE.containsKey(rl);
    }

    /** Wurde das Gebiet eingeschraenkt (Radius oder Dimension)? */
    public boolean hasArea() {
        return radius >= 0 || dimOnly != null;
    }

    /** Fuer Rollbacks: ohne Angabe gilt der Standardradius um den Spieler. */
    public void defaultRadius(int r) {
        if (!global && !hasArea() && center != null) radius = r;
    }

    public String where(List<Object> params, Set<LogAction> allowed, Boolean rolledBack) {
        List<String> w = new ArrayList<>();
        if (!users.isEmpty()) {
            w.add("LOWER(u) IN (" + marks(users.size()) + ")");
            for (String u : users) params.add(u.toLowerCase(Locale.ROOT));
        }
        if (!excludeUsers.isEmpty()) {
            w.add("LOWER(u) NOT IN (" + marks(excludeUsers.size()) + ")");
            for (String u : excludeUsers) params.add(u.toLowerCase(Locale.ROOT));
        }
        if (since > 0) {
            w.add("t >= ?");
            params.add(since);
        }
        if (radius >= 0 && center != null) {
            w.add("dim = ? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ?");
            params.add(centerDim);
            params.add(center.getX() - radius);
            params.add(center.getX() + radius);
            params.add(center.getZ() - radius);
            params.add(center.getZ() + radius);
        } else if (dimOnly != null) {
            w.add("dim = ?");
            params.add(dimOnly);
        }
        Set<LogAction> acts = EnumSet.noneOf(LogAction.class);
        acts.addAll(actions.isEmpty() ? allowed : actions);
        acts.retainAll(allowed);
        if (acts.isEmpty()) {
            w.add("1 = 0");
        } else if (acts.size() < LogAction.values().length) {
            StringBuilder sb = new StringBuilder("a IN (");
            int i = 0;
            for (LogAction a : acts) sb.append(i++ == 0 ? "" : ",").append(a.id);
            w.add(sb.append(")").toString());
        }
        if (!include.isEmpty()) {
            w.add("blk IN (" + marks(include.size()) + ")");
            params.addAll(include);
        }
        if (!exclude.isEmpty()) {
            w.add("(blk IS NULL OR blk NOT IN (" + marks(exclude.size()) + "))");
            params.addAll(exclude);
        }
        if (rolledBack != null) {
            w.add("rb = ?");
            params.add(rolledBack);
        }
        return String.join(" AND ", w);
    }

    private static String marks(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(i == 0 ? "?" : ",?");
        return sb.toString();
    }

    /** Kurzbeschreibung fuer Meldungen. */
    public String describe() {
        return raw.isEmpty() ? "ohne Filter" : String.join(" ", raw);
    }
}
