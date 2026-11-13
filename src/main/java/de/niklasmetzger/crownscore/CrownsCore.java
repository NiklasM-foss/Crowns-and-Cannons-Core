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
import net.minecraftforge.common.MinecraftForge;
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
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        CrownsData data = CrownsData.get(event.getServer());
        Economy.init();
        DiscordWebhook.start();
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
    }
}
