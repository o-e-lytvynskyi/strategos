package dev.strategos.mod.client;

import dev.strategos.mod.entity.UnitEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

/** The placeholder unit: the player model with the wide Steve skin, read from the game's own resources. */
public final class UnitRenderer extends LivingEntityRenderer<UnitEntity, PlayerModel<UnitEntity>> {
    /** Referenced at run time, never copied into the mod (ADR-0005). */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public UnitRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(UnitEntity entity) {
        return TEXTURE;
    }
}
