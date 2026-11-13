package de.niklasmetzger.crownscore.contract;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niklasmetzger.crownscore.data.Contract;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.data.Nation;
import de.niklasmetzger.crownscore.discord.DiscordWebhook;
import de.niklasmetzger.crownscore.util.CommandUtil;
import de.niklasmetzger.crownscore.util.Msg;
import de.niklasmetzger.crownscore.util.TimeUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * /vertrag: Kopie eines Buchs (Scribble oder Vanilla) auf dem Server hinterlegen.
 * Vor Gericht kann niemand mehr behaupten, das Buch sei nachtraeglich geaendert worden.
 */
public final class ContractCommand {
    private ContractCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("vertrag")
                .then(Commands.literal("hinterlegen")
                        .then(Commands.argument("titel", StringArgumentType.greedyString())
                                .executes(ContractCommand::hinterlegen)))
                .then(Commands.literal("liste").executes(ContractCommand::liste))
                .then(Commands.literal("lesen")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ContractCommand::lesen)))
                .then(Commands.literal("pruefen")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ContractCommand::pruefen)))
                .then(Commands.literal("export")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ContractCommand::export))));
    }

    /** Liest Titel, Autor und Seiten eines Buchs in der Hand. */
    private static Contract readBook(ServerPlayer p) {
        ItemStack stack = p.getMainHandItem();
        if (!stack.is(Items.WRITTEN_BOOK) && !stack.is(Items.WRITABLE_BOOK)) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        Contract c = new Contract();
        c.buchTitel = tag.getString("title");
        c.autor = tag.getString("author");
        ListTag pages = tag.getList("pages", Tag.TAG_STRING);
        for (int i = 0; i < pages.size(); i++) {
            String raw = pages.getString(i);
            String text = raw;
            if (stack.is(Items.WRITTEN_BOOK)) {
                try {
                    Component comp = Component.Serializer.fromJson(raw);
                    if (comp != null) text = comp.getString();
                } catch (Exception ignored) {
                    // Scribble-Formatierung oder altes Format: Rohtext behalten
                }
            }
            c.seiten.add(text);
        }
        c.hash = hash(c.seiten);
        return c;
    }

    private static String hash(List<String> pages) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String s : pages) {
                md.update(s.getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
            }
            return HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (Exception e) {
            return "?";
        }
    }

    private static int hinterlegen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Contract c = readBook(p);
        if (c == null || c.seiten.isEmpty()) {
            Msg.err(ctx.getSource(), "Halte das beschriebene Buch in die Haupthand.");
            return 0;
        }
        Nation n = d.nationOf(p.getUUID());
        c.id = d.nextContractId++;
        c.titel = StringArgumentType.getString(ctx, "titel").trim();
        c.hinterlegtVon = p.getUUID();
        c.hinterlegtVonName = p.getGameProfile().getName();
        c.nation = n == null ? "" : n.key();
        c.zeit = TimeUtil.now();
        d.contracts.add(c);
        d.setDirty();
        Msg.broadcast(ctx.getSource().getServer(), p.getGameProfile().getName() + (n == null ? "" : " (" + n.name + ")")
                + " hinterlegt den Vertrag #" + c.id + " \"" + c.titel + "\" (" + c.seiten.size() + " Seiten, Pruefsumme " + c.hash + ").");
        return 1;
    }

    private static int liste(CommandContext<CommandSourceStack> ctx) {
        CrownsData d = CommandUtil.data(ctx);
        if (d.contracts.isEmpty()) {
            Msg.info(ctx.getSource(), "Keine Vertraege hinterlegt. Buch in die Hand und /vertrag hinterlegen <Titel>.");
            return 0;
        }
        Msg.info(ctx.getSource(), "Hinterlegte Vertraege:");
        for (Contract c : d.contracts) {
            Msg.line(ctx.getSource(), " §6#" + c.id + " §f" + c.titel + " §7(" + c.hinterlegtVonName
                    + (c.nation.isEmpty() ? "" : ", " + d.anzeige(c.nation)) + ", " + TimeUtil.fmtShort(c.zeit) + ", Pruefsumme " + c.hash + ")");
        }
        return d.contracts.size();
    }

    private static Contract byId(CommandContext<CommandSourceStack> ctx, CrownsData d) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        for (Contract c : d.contracts) if (c.id == id) return c;
        Msg.err(ctx.getSource(), "Kein Vertrag #" + id + ".");
        return null;
    }

    private static int lesen(CommandContext<CommandSourceStack> ctx) {
        CrownsData d = CommandUtil.data(ctx);
        Contract c = byId(ctx, d);
        if (c == null) return 0;
        Msg.info(ctx.getSource(), "Vertrag #" + c.id + " \"" + c.titel + "\", Buch \"" + c.buchTitel + "\" von " + c.autor
                + ", hinterlegt " + TimeUtil.fmt(c.zeit) + " durch " + c.hinterlegtVonName);
        int i = 1;
        for (String s : c.seiten) {
            Msg.line(ctx.getSource(), " §7Seite " + (i++) + ": §f" + s.replace('\n', ' '));
        }
        return 1;
    }

    private static int pruefen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = CommandUtil.player(ctx);
        CrownsData d = CommandUtil.data(ctx);
        Contract c = byId(ctx, d);
        if (c == null) return 0;
        Contract book = readBook(p);
        if (book == null) {
            Msg.err(ctx.getSource(), "Halte das zu pruefende Buch in die Haupthand.");
            return 0;
        }
        if (book.hash.equals(c.hash)) {
            Msg.ok(ctx.getSource(), "Das Buch stimmt Zeichen fuer Zeichen mit Vertrag #" + c.id + " ueberein.");
            return 1;
        }
        List<Integer> diff = new ArrayList<>();
        for (int i = 0; i < Math.max(book.seiten.size(), c.seiten.size()); i++) {
            String a = i < book.seiten.size() ? book.seiten.get(i) : null;
            String b = i < c.seiten.size() ? c.seiten.get(i) : null;
            if (a == null || !a.equals(b)) diff.add(i + 1);
        }
        Msg.err(ctx.getSource(), "Das Buch weicht von Vertrag #" + c.id + " ab. Unterschiedliche Seiten: " + diff);
        return 0;
    }

    private static int export(CommandContext<CommandSourceStack> ctx) {
        if (!CommandUtil.admin(ctx.getSource())) return 0;
        CrownsData d = CommandUtil.data(ctx);
        Contract c = byId(ctx, d);
        if (c == null) return 0;
        StringBuilder sb = new StringBuilder();
        sb.append(":scroll: **Vertrag #").append(c.id).append(" ").append(c.titel).append("**\nBuch \"").append(c.buchTitel)
                .append("\" von ").append(c.autor).append(", hinterlegt ").append(TimeUtil.fmt(c.zeit)).append(" durch ").append(c.hinterlegtVonName)
                .append(c.nation.isEmpty() ? "" : " (" + d.anzeige(c.nation) + ")").append(", Pruefsumme ").append(c.hash).append("\n```\n");
        int i = 1;
        for (String s : c.seiten) sb.append("Seite ").append(i++).append(":\n").append(s).append("\n\n");
        sb.append("```");
        DiscordWebhook.gericht(sb.toString());
        Msg.ok(ctx.getSource(), "Vertrag #" + c.id + " an den Gerichtskanal geschickt.");
        return 1;
    }
}
