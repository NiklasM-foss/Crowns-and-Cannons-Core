package de.niklasmetzger.crownscore;

import com.mojang.logging.LogUtils;
import de.niklasmetzger.crownscore.activity.ActivityCommand;
import de.niklasmetzger.crownscore.activity.ActivityEvents;
import de.niklasmetzger.crownscore.claims.ClaimCommand;
import de.niklasmetzger.crownscore.colony.ColonyCommand;
import de.niklasmetzger.crownscore.config.CrownsConfig;
import de.niklasmetzger.crownscore.contract.ContractCommand;
import de.niklasmetzger.crownscore.data.CrownsData;
import de.niklasmetzger.crownscore.discord.DiscordWebhook;
import de.niklasmetzger.crownscore.economy.Economy;
import de.niklasmetzger.crownscore.election.ElectionCommand;
import de.niklasmetzger.crownscore.help.CcCoreCommand;
import de.niklasmetzger.crownscore.evidence.EvidenceCommand;
import de.niklasmetzger.crownscore.evidence.EvidenceEvents;
import de.niklasmetzger.crownscore.mail.MailCommand;
import de.niklasmetzger.crownscore.nation.NationCommand;
import de.niklasmetzger.crownscore.treasury.LoanCommand;
import de.niklasmetzger.crownscore.treasury.TreasuryCommand;
import de.niklasmetzger.crownscore.visa.VisaCommand;
import de.niklasmetzger.crownscore.war.DuelCommand;
import de.niklasmetzger.crownscore.war.WarCommand;
import de.niklasmetzger.crownscore.war.WarEvents;
import de.niklasmetzger.crownscore.waystone.WaystoneCommand;
import de.niklasmetzger.crownscore.waystone.WaystoneModule;
import de.niklasmetzger.crownscore.log.Inspector;
import de.niklasmetzger.crownscore.log.LogDatabase;
import de.niklasmetzger.crownscore.log.LogEvents;
import de.niklasmetzger.crownscore.mode.ModeCommand;
import de.niklasmetzger.crownscore.perms.CommandGate;
import de.niklasmetzger.crownscore.perms.CommandPermissionHook;
import de.niklasmetzger.crownscore.perms.DisplayEvents;
import de.niklasmetzger.crownscore.perms.ForgePermHandler;
import de.niklasmetzger.crownscore.perms.PermissionManager;
import net.minecraftforge.common.ForgeConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;

/**
 * Crowns Core: Server-Mod fuer das Nationen-Rollenspiel Crowns and Cannons.
 * Reine Server-Mod, Clients brauchen sie nicht.
 */
@Mod(CrownsCore.MODID)
public class CrownsCore {
    public static final String MODID = "crownscore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CrownsCore() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CrownsConfig.SPEC);
        // Clients ohne diese Mod duerfen sich verbinden.
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new WarEvents());
        MinecraftForge.EVENT_BUS.register(new EvidenceEvents());
        MinecraftForge.EVENT_BUS.register(new ActivityEvents());
        MinecraftForge.EVENT_BUS.register(new Scheduler());
        MinecraftForge.EVENT_BUS.register(new LogEvents());
        MinecraftForge.EVENT_BUS.register(new Inspector());
        MinecraftForge.EVENT_BUS.register(new DisplayEvents());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var d = event.getDispatcher();
        NationCommand.register(d);
        WarCommand.register(d);
        DuelCommand.register(d);
        EvidenceCommand.register(d);
        ActivityCommand.register(d);
        TreasuryCommand.register(d);
        LoanCommand.register(d);
        ColonyCommand.register(d);
        WaystoneCommand.register(d);
        ElectionCommand.register(d);
        VisaCommand.register(d);
        MailCommand.register(d);
        ContractCommand.register(d);
        ClaimCommand.register(d);
        CcCoreCommand.register(d);
        ModeCommand.register(d);
        de.niklasmetzger.crownscore.world.WeltkarteCommand.register(d);
    }

    /** Nach allen Mods: eigene Befehle nach Rolle ausblenden, alle Befehle ueber command.<name> steuerbar machen. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRegisterCommandsLast(RegisterCommandsEvent event) {
        int gated = CommandGate.install(event.getDispatcher());
        int hooked = CommandPermissionHook.install(event.getDispatcher());
        LOGGER.info("Befehlsrechte: {} eigene Befehlsknoten nach Rolle, {} Wurzelbefehle ueber command.<name>", gated, hooked);
    }

    @SubscribeEvent
    public void onPermissionHandlers(PermissionGatherEvent.Handler event) {
        event.addPermissionHandler(ForgePermHandler.ID, ForgePermHandler::new);
    }

    @SubscribeEvent
    public void onAboutToStart(ServerAboutToStartEvent event) {
        PermissionManager.load(event.getServer());
        if (CrownsConfig.RECHTE_FORGE_HANDLER.get() && "forge:default_handler".equals(ForgeConfig.SERVER.permissionHandler.get())) {
            ForgeConfig.SERVER.permissionHandler.set(ForgePermHandler.ID.toString());
            LOGGER.info("Rechte: Forge-Permission-Handler auf {} gestellt", ForgePermHandler.ID);
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        CrownsData data = CrownsData.get(event.getServer());
        Economy.init();
        DiscordWebhook.start();
        LogDatabase.open(event.getServer());
        if (ModList.get().isLoaded("waystones")) {
            WaystoneModule.init();
        }
        LOGGER.info("Crowns Core bereit: {} Nationen, {} Kriege, Wirtschaft: {}, Claims: {}",
                data.nations.size(), data.wars.size(), Economy.get().name(),
                ModList.get().isLoaded("openpartiesandclaims") ? "Open Parties and Claims + intern" : "intern");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        CrownsData.get(event.getServer()).setDirty();
        DiscordWebhook.shutdown();
        LogDatabase.close();
        PermissionManager.save();
    }
}
