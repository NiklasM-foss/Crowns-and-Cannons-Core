package de.niklasmetzger.crownscore.perms;

import de.niklasmetzger.crownscore.CrownsCore;
import de.niklasmetzger.crownscore.perms.PermissionManager.Tri;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.permission.handler.IPermissionHandler;
import net.minecraftforge.server.permission.nodes.PermissionDynamicContext;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Forge-Permission-Handler: Mods, die die Forge-PermissionAPI nutzen, fragen damit diese Rechteverwaltung.
 * Ja/Nein-Rechte kommen aus den Gruppen, alles ohne Eintrag faellt auf den Standard der jeweiligen Mod zurueck.
 */
public class ForgePermHandler implements IPermissionHandler {
    public static final ResourceLocation ID = new ResourceLocation(CrownsCore.MODID, "permissions");

    private final Set<PermissionNode<?>> nodes;

    public ForgePermHandler(Collection<PermissionNode<?>> nodes) {
        this.nodes = new HashSet<>(nodes);
    }

    @Override
    public ResourceLocation getIdentifier() {
        return ID;
    }

    @Override
    public Set<PermissionNode<?>> getRegisteredNodes() {
        return nodes;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getPermission(ServerPlayer player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
        if (node.getType() == PermissionTypes.BOOLEAN) {
            Tri t = PermissionManager.check(player, node.getNodeName());
            if (t != Tri.UNDEFINED) return (T) Boolean.valueOf(t == Tri.TRUE);
        }
        return node.getDefaultResolver().resolve(player, player.getUUID(), context);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOfflinePermission(UUID player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
        if (node.getType() == PermissionTypes.BOOLEAN) {
            Tri t = PermissionManager.check(player, node.getNodeName(), "");
            if (t != Tri.UNDEFINED) return (T) Boolean.valueOf(t == Tri.TRUE);
        }
        return node.getDefaultResolver().resolve(null, player, context);
    }
}
