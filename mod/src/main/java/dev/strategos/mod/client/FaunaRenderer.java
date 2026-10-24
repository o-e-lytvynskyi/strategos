package dev.strategos.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.ValuePath;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.entity.UnitSpawnData;
import net.minecraft.client.model.ChickenModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;

/**
 * The animals' renderer (ECO-16, ADR-0011 amended): one per {@code strategos:unit_fauna} body, drawing each with the
 * archetype its template's {@code Body/model} names. A pig and a chicken use vanilla's {@code PigModel} and {@code
 * ChickenModel}, which take any entity; vanilla's sheep models are bound to {@code Sheep}, so the sheep and its wool are
 * vanilla's baked layers in a {@link QuadrupedModel} of our own, no mesh copied. Textures are the game's, referenced at
 * run time (ADR-0005). A carcass, kept in the dying pose by the server and alive to vanilla, lies on its side.
 */
public final class FaunaRenderer extends EntityRenderer<UnitEntity> {
    private static final ResourceLocation SHEEP =
            ResourceLocation.withDefaultNamespace("textures/entity/sheep/sheep.png");
    private static final ResourceLocation SHEEP_FUR =
            ResourceLocation.withDefaultNamespace("textures/entity/sheep/sheep_fur.png");
    private static final ResourceLocation PIG = ResourceLocation.withDefaultNamespace("textures/entity/pig/pig.png");
    private static final ResourceLocation CHICKEN =
            ResourceLocation.withDefaultNamespace("textures/entity/chicken.png");

    private final Archetype sheep;
    private final Archetype pig;
    private final Archetype chicken;

    public FaunaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        sheep = new Archetype(ctx, new Quadruped<>(ctx.bakeLayer(ModelLayers.SHEEP)), SHEEP, 0.7F);
        sheep.wool(new Quadruped<>(ctx.bakeLayer(ModelLayers.SHEEP_FUR)));
        pig = new Archetype(ctx, new PigModel<>(ctx.bakeLayer(ModelLayers.PIG)), PIG, 0.7F);
        chicken = new Archetype(ctx, new ChickenModel<>(ctx.bakeLayer(ModelLayers.CHICKEN)), CHICKEN, 0.3F);
    }

    @Override
    public void render(
            UnitEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        archetype(entity).render(entity, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(UnitEntity entity) {
        return archetype(entity).getTextureLocation(entity);
    }

    private Archetype archetype(UnitEntity entity) {
        UnitSpawnData data = entity.spawnData();
        TemplateSet templates = StrategosClient.templates();
        ValuePath path = templates.tokenPaths().byPath("Body/model");
        String model = data == null || path == null || !templates.has(data.templateIndex())
                ? null
                : templates.text(data.templateIndex(), path.id());
        return switch (model == null ? "" : model) {
            case "pig" -> pig;
            case "chicken" -> chicken;
            default -> sheep;
        };
    }

    /** One archetype: its model and texture, no name tag; a carcass lies on its side. */
    private static final class Archetype extends LivingEntityRenderer<UnitEntity, EntityModel<UnitEntity>> {
        private final ResourceLocation texture;

        Archetype(
                EntityRendererProvider.Context ctx,
                EntityModel<UnitEntity> model,
                ResourceLocation texture,
                float shadow) {
            super(ctx, model, shadow);
            this.texture = texture;
        }

        void wool(EntityModel<UnitEntity> fur) {
            addLayer(new Wool(this, fur));
        }

        @Override
        public ResourceLocation getTextureLocation(UnitEntity entity) {
            return texture;
        }

        @Override
        protected boolean shouldShowName(UnitEntity entity) {
            return false;
        }

        @Override
        protected void setupRotations(
                UnitEntity entity, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
            super.setupRotations(entity, pose, bob, yBodyRot, partialTick, scale);
            if (entity.hasPose(Pose.DYING) && entity.deathTime == 0) {
                pose.mulPose(Axis.ZP.rotationDegrees(getFlipDegrees(entity)));
            }
        }
    }

    /** The sheep's wool, white, over its body. */
    private static final class Wool extends RenderLayer<UnitEntity, EntityModel<UnitEntity>> {
        private final EntityModel<UnitEntity> fur;

        Wool(Archetype parent, EntityModel<UnitEntity> fur) {
            super(parent);
            this.fur = fur;
        }

        @Override
        public void render(
                PoseStack pose,
                MultiBufferSource buffers,
                int light,
                UnitEntity entity,
                float limbSwing,
                float limbSwingAmount,
                float partialTick,
                float ageInTicks,
                float netHeadYaw,
                float headPitch) {
            coloredCutoutModelCopyLayerRender(
                    getParentModel(),
                    fur,
                    SHEEP_FUR,
                    pose,
                    buffers,
                    light,
                    entity,
                    limbSwing,
                    limbSwingAmount,
                    ageInTicks,
                    netHeadYaw,
                    headPitch,
                    partialTick,
                    -1);
        }
    }

    /** Vanilla's quadruped over a baked layer: its constructor is protected. The numbers are the sheep's. */
    private static final class Quadruped<T extends Entity> extends QuadrupedModel<T> {
        Quadruped(ModelPart root) {
            super(root, false, 8.0F, 4.0F, 2.0F, 2.0F, 24);
        }
    }
}
