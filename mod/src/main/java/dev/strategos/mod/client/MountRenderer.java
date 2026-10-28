package dev.strategos.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.strategos.core.client.look.MountPose;
import dev.strategos.mod.entity.UnitEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The renderer of a mounted unit (UNIT-06, ADR-0011): one entity drawn as a horse, vanilla's mesh in a {@link
 * MountModel}, with a humanoid rider above its saddle, the unit renderer's own with a seated pose, so armour, items,
 * the faction's colours and the level of detail are the foot soldier's. Both are posed from the one body's walk
 * animation and swing, and bob together. There is no passenger entity and no vanilla horse.
 */
public final class MountRenderer extends LivingEntityRenderer<UnitEntity, MountModel> {
    /** Referenced at run time, never copied into the mod (ADR-0005). */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_brown.png");

    /** Where the rider's feet-origin sits over the horse's: the seat's height less the seated hips' (blocks). */
    static final double SEAT = 0.62;

    private final UnitRenderer rider;

    public MountRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new MountModel(ctx.bakeLayer(MountModel.LAYER)), 0.9F);
        rider = new UnitRenderer(ctx, new Seated(ctx.bakeLayer(ModelLayers.PLAYER)));
    }

    @Override
    public void render(
            UnitEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        float bob = MountPose.of(
                        entity.walkAnimation.position(partialTicks),
                        Math.min(entity.walkAnimation.speed(partialTicks), 1F),
                        0F)
                .bob();
        pose.pushPose();
        pose.translate(0, bob, 0);
        MultiBufferSource into = buffers instanceof OutlineBufferSource
                ? UnitLod.bodyOnly(
                        buffers, Minecraft.getInstance().renderBuffers().bufferSource())
                : buffers;
        super.render(entity, yaw, partialTicks, pose, into, light);
        pose.pushPose();
        pose.translate(0, SEAT, 0);
        rider.render(entity, yaw, partialTicks, pose, buffers, light);
        pose.popPose();
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(UnitEntity entity) {
        return TEXTURE;
    }

    /** No name tag, as the foot soldier's (UNIT-03). */
    @Override
    protected boolean shouldShowName(UnitEntity entity) {
        return false;
    }

    /** The rider: the unit's player model, its legs astride and its stride still, whatever the horse is doing. */
    private static final class Seated extends PlayerModel<UnitEntity> {
        Seated(net.minecraft.client.model.geom.ModelPart root) {
            super(root, false);
        }

        @Override
        public void setupAnim(
                UnitEntity entity,
                float limbSwing,
                float limbSwingAmount,
                float ageInTicks,
                float netHeadYaw,
                float headPitch) {
            riding = true; // the renderer resets it each frame; a passenger sits, and this body is none
            super.setupAnim(entity, 0F, 0F, ageInTicks, Mth.clamp(netHeadYaw, -60F, 60F), headPitch);
        }
    }
}
