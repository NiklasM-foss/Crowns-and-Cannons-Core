package de.niklasmetzger.crownscore.log;

import de.niklasmetzger.crownscore.util.Durations;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Ausgabe einer Protokollzeile im Chat. */
public final class LogFormat {
    private LogFormat() {}

    public static String shortId(String id) {
        if (id == null) return "";
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    public static String shortDim(String dim) {
        return switch (dim) {
            case "minecraft:overworld" -> "";
            case "minecraft:the_nether" -> " Nether";
            case "minecraft:the_end" -> " End";
            default -> " " + shortId(dim);
        };
    }

    public static String object(LogEntry e) {
        return switch (e.action.table) {
            case BLOCK -> shortId(e.blk);
            case ITEM -> e.amount + "x " + shortId(e.blk) + (e.name.isEmpty() ? "" : " §7(" + shortId(e.name) + ")");
            case ENTITY -> e.name.isEmpty() ? shortId(e.blk) : e.name + (e.blk.equals("minecraft:player") ? "" : " §7(" + shortId(e.blk) + ")");
            case TEXT -> switch (e.action) {
                case CHAT -> "\"" + e.text + "\"";
                case COMMAND -> e.text;
                default -> "";
            };
        };
    }

    public static Component line(LogEntry e, long now, boolean withPos) {
        MutableComponent c = Component.literal("§7" + Durations.ago(now - e.t) + " §8· ");
        String body = "§3" + e.user + " " + e.action.verb + " §f" + object(e);
        // Zurueckgesetzte Eintraege durchstreichen; jeder Farbcode setzt das zurueck, also nach jedem neu
        if (e.rolledBack) body = body.replaceAll("(§[0-9a-f])", "$1§m");
        c.append(Component.literal(body));
        if (withPos) {
            String tp = "/execute in " + e.dim + " run tp @s " + e.x + " " + e.y + " " + e.z;
            c.append(Component.literal(" §8(" + e.x + " " + e.y + " " + e.z + shortDim(e.dim) + ")").withStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tp))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Hinteleportieren").withStyle(ChatFormatting.GRAY)))));
        }
        return c;
    }
}
