package dev.strategos.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.strategos.core.client.look.UnitLook;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.entity.UnitSpawnData;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * The unit renderer for the RTS view (UNIT-03, ART-01): the placeholder player model with the wide Steve skin, read from
 * the game's own resources, vanilla's armour and held-item layers, no name tag, an outline in its faction's colour when
 * selected (the body's client glow), out to 192 blocks (the body's render distance). A body is dressed once, on its
 * first frame, in its {@link UnitLook}: the tunic dyed its faction's colour, its role's armour and its rest gear.
 */
public final class UnitRenderer extends LivingEntityRenderer<UnitEntity, PlayerModel<UnitEntity>> {
    /** Referenced at run time, never copied into the mod (ADR-0005). */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public UnitRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
        addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
    }

    @Override
    public void render(
            UnitEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!entity.dressed()) {
            dress(entity);
        }
        super.render(entity, yaw, partialTicks, pose, buffers, light);
    }

    /** The body's look, from its template's classes and its faction (ART-01's rules). */
    private static void dress(UnitEntity entity) {
        UnitSpawnData data = entity.spawnData();
        TemplateSet templates = StrategosClient.templates();
        if (data == null || !templates.has(data.templateIndex())) {
            return;
        }
        UnitLook look = UnitLook.of(templates.byIndex(data.templateIndex()).classes(), data.faction());
        entity.dress(
                item(look.head(), look),
                item(look.chest(), look),
                item(look.legs(), look),
                item(look.feet(), look),
                item(look.rest(), look),
                item(look.shield(), look));
    }

    /** Vanilla item {@code id}: leather dyed the look's colour, a shield with its faction's banner; empty for none. */
    private static ItemStack item(String id, UnitLook look) {
        ResourceLocation rl = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(rl));
        if (stack.is(ItemTags.DYEABLE)) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(look.dye(), false));
        }
        if (stack.is(Items.SHIELD)) {
            stack.set(DataComponents.BASE_COLOR, DyeColor.byName(look.shieldDye(), DyeColor.WHITE));
        }
        return stack;
    }

    @Override
    public ResourceLocation getTextureLocation(UnitEntity entity) {
        return TEXTURE;
    }

    /** No name tag: vanilla shows one over every living entity within 64 blocks, a crowd of labels from above (UNIT-03). */
    @Override
    protected boolean shouldShowName(UnitEntity entity) {
        return false;
    }
}
