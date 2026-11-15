package dev.strategos.mod.client;

import dev.strategos.mod.entity.UnitEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * The renderer of a siege unit (CMB-09, ADR-0011): one entity drawn as a {@link RamModel}, with no rider and no vanilla
 * entity behind it. The plank texture is vanilla's, referenced at run time and never copied into the mod (ADR-0005).
 */
public final class RamRenderer extends LivingEntityRenderer<UnitEntity, RamModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/block/oak_planks.png");

    public RamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new RamModel(ctx.bakeLayer(RamModel.LAYER)), 1.2F);
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
}
