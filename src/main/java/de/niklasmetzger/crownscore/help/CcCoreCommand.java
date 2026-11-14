package de.niklasmetzger.crownscore.help;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.help.CommandCatalog.Entry;
import de.niklasmetzger.crownscore.help.CommandCatalog.Module;
import de.niklasmetzger.crownscore.help.CommandCatalog.Role;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.Perms;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** /cccore: Version und Befehlsuebersicht. */
public final class CcCoreCommand {
    private static final int PAGE = 8;

    private CcCoreCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("cccore")
                .executes(CcCoreCommand::about)
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx, ""))
                        .then(Commands.argument("filter", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    List<String> out = new ArrayList<>();
                                    for (Module m : CommandCatalog.modules()) {
                                        boolean any = CommandCatalog.entries().stream()
                                                .anyMatch(e -> e.module() == m && Perms.allowed(ctx.getSource(), e.node(), e.role()));
                                        if (any) out.add(m.key());
                                    }
                                    return SharedSuggestionProvider.suggest(out, b);
                                })
                                .executes(ctx -> list(ctx, StringArgumentType.getString(ctx, "filter")))))
                .then(de.niklasmetzger.crownscore.log.LogCommand.build())
                .then(de.niklasmetzger.crownscore.perms.PermsCommand.build()));
    }

    private static int about(CommandContext<CommandSourceStack> ctx) {
        String version = ModList.get().getModContainerById(CrownsCore.MODID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        Msg.info(ctx.getSource(), "§fCrowns Core §7Version " + version);
        long mine = CommandCatalog.entries().stream().filter(e -> Perms.allowed(ctx.getSource(), e.node(), e.role())).count();
        Msg.line(ctx.getSource(), " §7Du kannst " + mine + " von " + CommandCatalog.entries().size() + " Befehlen nutzen.");
        Msg.line(ctx.getSource(), " §e/cccore list§7 zeigt sie, §e/cccore list <Modul>§7 ein einzelnes Modul.");
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, String filter) {
        int seite = 1;
        Module module = null;
        String f = filter.trim();
        if (!f.isEmpty()) {
            if (f.chars().allMatch(Character::isDigit)) {
                seite = Math.max(1, Integer.parseInt(f));
            } else if (!f.equalsIgnoreCase("meine")) {
                module = CommandCatalog.module(f);
                if (module == null) {
                    Msg.err(ctx.getSource(), "Modul \"" + f + "\" gibt es nicht. Module: " + moduleKeys());
                    return 0;
                }
            }
        }

        // Jeder sieht nur, was er auch benutzen darf
        List<Entry> list = new ArrayList<>();
        for (Entry e : CommandCatalog.entries()) {
            if (module != null && e.module() != module) continue;
            if (!Perms.allowed(ctx.getSource(), e.node(), e.role())) continue;
            list.add(e);
        }
        if (list.isEmpty()) {
            Msg.err(ctx.getSource(), "Keine Befehle gefunden.");
            return 0;
        }

        // Ein Modul zeigt alles auf einmal, die Gesamtliste wird geblaettert.
        boolean paged = module == null;
        int pages = paged ? CommandUtil.pages(list.size(), PAGE) : 1;
        seite = Math.min(seite, pages);
        List<Entry> shown = paged ? CommandUtil.page(list, seite, PAGE) : list;

        String titel = module != null ? "Befehle " + module.title() : "Deine Befehle";
        Msg.info(ctx.getSource(), "§f" + titel + " §7(" + list.size() + (paged ? ", Seite " + seite + "/" + pages : "") + ")");
        if (module != null && !module.intro().isEmpty()) Msg.line(ctx.getSource(), " §7" + module.intro());

        Module last = null;
        for (Entry e : shown) {
            if (module == null && e.module() != last) {
                Msg.line(ctx.getSource(), "§6§l" + e.module().title());
                last = e.module();
            }
            ctx.getSource().sendSuccess(() -> entryLine(e, true), false);
        }

        final int aktuell = seite;
        if (paged) ctx.getSource().sendSuccess(() -> navigation(aktuell, pages), false);
        Msg.line(ctx.getSource(), "§8Klick auf einen Befehl schreibt ihn in den Chat. Farben: "
                + legend());
        return list.size();
    }

    private static Component entryLine(Entry e, boolean usable) {
        MutableComponent line = Component.literal(" ");
        Style cmdStyle = Style.EMPTY
                .withColor(usable ? ChatFormatting.WHITE : ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, e.suggestion()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal(e.role().color + e.role().label + "\n§7" + e.description())));
        line.append(Component.literal(e.role().color + "■ ").withStyle(Style.EMPTY));
        line.append(Component.literal(e.syntax()).withStyle(cmdStyle));
        line.append(Component.literal(" §7" + e.description()));
        return line;
    }

    private static Component navigation(int seite, int pages) {
        MutableComponent nav = Component.literal(" ");
        if (seite > 1) {
            nav.append(Component.literal("§e[« zurück]").withStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cccore list " + (seite - 1)))));
            nav.append(Component.literal(" "));
        }
        nav.append(Component.literal("§7Seite " + seite + "/" + pages + " "));
        if (seite < pages) {
            nav.append(Component.literal("§e[weiter »]").withStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cccore list " + (seite + 1)))));
        }
        return nav;
    }

    private static String legend() {
        StringBuilder sb = new StringBuilder();
        for (Role r : Role.values()) sb.append(r.color).append(r.label).append(" ");
        return sb.toString().trim();
    }

    private static String moduleKeys() {
        StringBuilder sb = new StringBuilder();
        for (Module m : CommandCatalog.modules()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(m.key());
        }
        return sb.toString();
    }
}
