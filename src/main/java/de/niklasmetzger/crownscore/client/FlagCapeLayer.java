package de.niklasmetzger.crownscore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.Items;

/**
 * Ersetzt die Umhang-Ebene von Minecraft: wer eine Flagge hat, traegt sie, alle anderen behalten
 * ihren normalen Umhang. Die Bewegung des Umhangs ist dieselbe wie bei Minecraft.
 */
public class FlagCapeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final CapeLayer vanilla;

    public FlagCapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, CapeLayer vanilla) {
        super(parent);
        this.vanilla = vanilla;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p,
                       float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        ResourceLocation tex = ClientCapes.get(p.getUUID());
        if (tex == null) {
            if (vanilla != null) vanilla.render(pose, buffers, light, p, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
            return;
        }
        if (p.isInvisible() || !p.isModelPartShown(PlayerModelPart.CAPE) || p.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return;
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 0.125F);
        double dx = Mth.lerp(partialTicks, p.xCloakO, p.xCloak) - Mth.lerp(partialTicks, p.xo, p.getX());
        double dy = Mth.lerp(partialTicks, p.yCloakO, p.yCloak) - Mth.lerp(partialTicks, p.yo, p.getY());
        double dz = Mth.lerp(partialTicks, p.zCloakO, p.zCloak) - Mth.lerp(partialTicks, p.zo, p.getZ());
        float body = Mth.rotLerp(partialTicks, p.yBodyRotO, p.yBodyRot);
        double sin = Mth.sin(body * ((float) Math.PI / 180F));
        double cos = -Mth.cos(body * ((float) Math.PI / 180F));
        float lift = Mth.clamp((float) dy * 10.0F, -6.0F, 32.0F);
        float back = Mth.clamp((float) (dx * sin + dz * cos) * 100.0F, 0.0F, 150.0F);
        float side = Mth.clamp((float) (dx * cos - dz * sin) * 100.0F, -20.0F, 20.0F);
        float bob = Mth.lerp(partialTicks, p.oBob, p.bob);
        lift += Mth.sin(Mth.lerp(partialTicks, p.walkDistO, p.walkDist) * 6.0F) * 32.0F * bob;
        if (p.isCrouching()) lift += 25.0F;
        pose.mulPose(Axis.XP.rotationDegrees(6.0F + back / 2.0F + lift));
        pose.mulPose(Axis.ZP.rotationDegrees(side / 2.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - side / 2.0F));
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(tex));
        getParentModel().renderCloak(pose, vc, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
