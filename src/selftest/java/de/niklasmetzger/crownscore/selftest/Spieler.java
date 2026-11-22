package de.niklasmetzger.crownscore.selftest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Test-Spieler fuer Funktionstests ueber die Konsole (nur Testserver):
 * /cctest als <Name> <Befehl>, /cctest alsop <Name> <Befehl>, /cctest tp <Name> x y z, /cctest gib <Name> <Item> <Anzahl>,
 * /cctest inv <Name>. Antworten auf Befehle stehen im Log mit dem Praefix [CCALS].
 */
final class Spieler {
    private static final Logger LOG = LoggerFactory.getLogger("CCALS");

    private Spieler() {}

    static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(("cctest-spieler:" + name.toLowerCase()).getBytes(StandardCharsets.UTF_8));
    }

    private static final java.util.Set<UUID> ANGEMELDET = new java.util.HashSet<>();

    static FakePlayer get(MinecraftServer server, String name) {
        FakePlayer p = FakePlayerFactory.get(server.overworld(), new GameProfile(uuid(name), name));
        if (ANGEMELDET.add(p.getUUID())) luckPerms(p);
        return p;
    }

    /**
     * LuckPerms prueft bei jedem Befehl eine Capability, die es nur beim echten Login fuellt. Fuer Test-Spieler das
     * nachholen: Benutzer laden und an den Spieler haengen (per Reflection, LuckPerms ist optional).
     */
    private static void luckPerms(FakePlayer p) {
        try {
            ClassLoader cl = Spieler.class.getClassLoader();
            Class<?> prov;
            try {
                prov = Class.forName("net.luckperms.api.LuckPermsProvider", true, cl);
            } catch (ClassNotFoundException e) {
                return; // kein LuckPerms
            }
            Object api = prov.getMethod("get").invoke(null);
            Object um = api.getClass().getMethod("getUserManager").invoke(api);
            java.lang.reflect.Method load = um.getClass().getMethod("loadUser", UUID.class, String.class);
            load.setAccessible(true);
            Object apiUser = ((java.util.concurrent.CompletableFuture<?>) load.invoke(um, p.getUUID(), p.getGameProfile().getName()))
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
            Class<?> apiUserCls = Class.forName("me.lucko.luckperms.common.api.implementation.ApiUser", true, api.getClass().getClassLoader());
            java.lang.reflect.Method cast = null;
            for (java.lang.reflect.Method m : apiUserCls.getDeclaredMethods()) {
                if (m.getName().equals("cast") && m.getParameterCount() == 1) cast = m;
            }
            cast.setAccessible(true);
            Object user = cast.invoke(null, apiUser);
            Object plugin = null;
            for (Class<?> c = api.getClass(); c != null && plugin == null; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (f.getType().getName().endsWith("LuckPermsPlugin")) {
                        f.setAccessible(true);
                        plugin = f.get(api);
                    }
                }
            }
            java.lang.reflect.Method gcm = plugin.getClass().getMethod("getContextManager");
            gcm.setAccessible(true);
            Object ctxManager = gcm.invoke(plugin);
            Class<?> capCls = Class.forName("me.lucko.luckperms.forge.capabilities.UserCapabilityImpl", true, api.getClass().getClassLoader());
            Object cap = capCls.getMethod("getNullable", net.minecraft.world.entity.player.Player.class).invoke(null, p);
            if (cap == null) {
                LOG.warn("[CCALS] LuckPerms: keine Capability am Test-Spieler {}", p.getGameProfile().getName());
                return;
            }
            for (java.lang.reflect.Method m : capCls.getMethods()) {
                if (m.getName().equals("initialise") && m.getParameterCount() == 3) {
                    m.invoke(cap, user, p, ctxManager);
                    LOG.info("[CCALS] LuckPerms: Test-Spieler {} angemeldet", p.getGameProfile().getName());
                    return;
                }
            }
        } catch (Throwable t) {
            LOG.warn("[CCALS] LuckPerms-Anmeldung fuer {} fehlgeschlagen: {}", p.getGameProfile().getName(), t.toString());
        }
    }

    static LiteralArgumentBuilder<CommandSourceStack> befehle(LiteralArgumentBuilder<CommandSourceStack> root) {
        return root
                .then(Commands.literal("als").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("befehl", StringArgumentType.greedyString()).executes(ctx -> als(ctx, false)))))
                .then(Commands.literal("alsop").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("befehl", StringArgumentType.greedyString()).executes(ctx -> als(ctx, true)))))
                .then(Commands.literal("tp").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer()).executes(Spieler::tp))))))
                .then(Commands.literal("gib").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("item", StringArgumentType.string()).then(Commands.argument("anzahl", IntegerArgumentType.integer(1, 6400))
                                .executes(Spieler::gib)))))
                .then(Commands.literal("inv").then(Commands.argument("name", StringArgumentType.word()).executes(Spieler::inv)))
                .then(Commands.literal("uuid").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                    String n = StringArgumentType.getString(ctx, "name");
                    LOG.info("[CCALS] {} uuid {}", n, uuid(n));
                    ctx.getSource().sendSuccess(() -> Component.literal(n + " = " + uuid(n)), false);
                    return 1;
                })));
    }

    private static int als(CommandContext<CommandSourceStack> ctx, boolean admin) {
        try {
            return alsIntern(ctx, admin);
        } catch (Throwable t) {
            LOG.error("[CCALS] Fehler im Test-Helfer", t);
            return 0;
        }
    }

    private static int alsIntern(CommandContext<CommandSourceStack> ctx, boolean admin) {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "name");
        String befehl = StringArgumentType.getString(ctx, "befehl");
        if (befehl.startsWith("/")) befehl = befehl.substring(1);
        FakePlayer p = get(server, name);
        List<String> antworten = new ArrayList<>();
        CommandSource sammler = new CommandSource() {
            @Override
            public void sendSystemMessage(Component c) {
                antworten.add(c.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack src = p.createCommandSourceStack().withSource(sammler);
        List<? extends String> vorher = de.niklasmetzger.crownscore.config.CrownsConfig.ADMINS.get();
        if (admin) {
            List<String> mit = new ArrayList<>(vorher);
            mit.add(name);
            de.niklasmetzger.crownscore.config.CrownsConfig.ADMINS.set(mit);
        }
        int r;
        try {
            r = server.getCommands().performPrefixedCommand(src, befehl);
        } finally {
            if (admin) de.niklasmetzger.crownscore.config.CrownsConfig.ADMINS.set(new ArrayList<>(vorher));
        }
        LOG.info("[CCALS] {}{} > /{} (Ergebnis {})", name, admin ? " (admin)" : "", befehl, r);
        for (String a : antworten) for (String zeile : a.split("\n")) LOG.info("[CCALS] {} < {}", name, zeile);
        if (antworten.isEmpty()) LOG.info("[CCALS] {} < (keine Antwort)", name);
        return r;
    }

    private static int tp(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "name");
        FakePlayer p = get(server, name);
        int x = IntegerArgumentType.getInteger(ctx, "x"), y = IntegerArgumentType.getInteger(ctx, "y"), z = IntegerArgumentType.getInteger(ctx, "z");
        ServerLevel lvl = server.overworld();
        lvl.getChunk(x >> 4, z >> 4);
        p.setPos(x + 0.5, y, z + 0.5);
        LOG.info("[CCALS] {} steht jetzt bei {} {} {}", name, x, y, z);
        return 1;
    }

    private static int gib(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "name");
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(StringArgumentType.getString(ctx, "item")));
        if (item == null) return 0;
        FakePlayer p = get(server, name);
        p.getInventory().add(new ItemStack(item, IntegerArgumentType.getInteger(ctx, "anzahl")));
        LOG.info("[CCALS] {} hat {}x {} bekommen", name, IntegerArgumentType.getInteger(ctx, "anzahl"), ForgeRegistries.ITEMS.getKey(item));
        return 1;
    }

    private static int inv(CommandContext<CommandSourceStack> ctx) {
        FakePlayer p = get(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name"));
        List<String> l = new ArrayList<>();
        for (ItemStack s : p.getInventory().items) if (!s.isEmpty()) l.add(s.getCount() + "x " + ForgeRegistries.ITEMS.getKey(s.getItem()));
        LOG.info("[CCALS] {} Inventar: {}", p.getGameProfile().getName(), l.isEmpty() ? "leer" : String.join(", ", l));
        return 1;
    }
}
