package dev.strategos.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.platform.Ids;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A battering ram (CMB-09), cubes of our own and nothing of vanilla's: a floor of beams on four wheels, four posts, a
 * gabled roof over them and a log hung under the ridge, its iron-capped head out at the front. The wheels turn with the
 * walk animation and the log swings back and forth on a strike. One 16 x 16 texture serves every cube (a plank's), so
 * the model needs no art of its own; sizes are in sixteenths of a block, the front is -Z as the vanilla models'.
 */
final class RamModel extends EntityModel<UnitEntity> {
    static final ModelLayerLocation LAYER = new ModelLayerLocation(Ids.id("unit_large"), "ram");

    /** How far the log runs out and back on a strike, in sixteenths. */
    private static final float THRUST = 9F;

    /** The log's rest place along Z, in sixteenths. */
    private static final float LOG_REST = 2F;

    private final ModelPart log;
    private final List<ModelPart> wheels;
    private final List<ModelPart> drawn;

    RamModel(ModelPart root) {
        super(net.minecraft.client.renderer.RenderType::entityCutoutNoCull);
        log = root.getChild("log");
        wheels = List.of(
                root.getChild("wheel_front_left"),
                root.getChild("wheel_front_right"),
                root.getChild("wheel_back_left"),
                root.getChild("wheel_back_right"));
        drawn = List.of(
                root.getChild("floor"),
                root.getChild("posts"),
                root.getChild("roof_left"),
                root.getChild("roof_right"),
                log,
                wheels.get(0),
                wheels.get(1),
                wheels.get(2),
                wheels.get(3));
    }

    static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
                "floor", CubeListBuilder.create().addBox(-12F, 0F, -14F, 24F, 3F, 28F), PartPose.offset(0F, 15F, 0F));
        CubeListBuilder posts = CubeListBuilder.create();
        for (int x : new int[] {-11, 9}) {
            for (int z : new int[] {-13, 11}) {
                posts.addBox(x, -10F, z, 2F, 10F, 2F);
            }
        }
        root.addOrReplaceChild("posts", posts, PartPose.offset(0F, 15F, 0F));
        float pitch = 0.45F; // the roof's slope, from ridge to eaves
        root.addOrReplaceChild(
                "roof_left",
                CubeListBuilder.create().addBox(-14F, -1F, -16F, 14F, 2F, 32F),
                PartPose.offsetAndRotation(0F, 3F, 0F, 0F, 0F, pitch));
        root.addOrReplaceChild(
                "roof_right",
                CubeListBuilder.create().addBox(0F, -1F, -16F, 14F, 2F, 32F),
                PartPose.offsetAndRotation(0F, 3F, 0F, 0F, 0F, -pitch));
        root.addOrReplaceChild(
                "log",
                CubeListBuilder.create()
                        .addBox(-3F, -3F, -20F, 6F, 6F, 26F) // the trunk
                        .addBox(-4F, -4F, -23F, 8F, 8F, 3F), // the iron cap
                PartPose.offset(0F, 12F, LOG_REST));
        float[][] wheels = {{-14F, -9F}, {13F, -9F}, {-14F, 9F}, {13F, 9F}}; // x, z of each wheel's centre
        String[] names = {"wheel_front_left", "wheel_front_right", "wheel_back_left", "wheel_back_right"};
        for (int k = 0; k < names.length; k++) {
            root.addOrReplaceChild(
                    names[k],
                    CubeListBuilder.create().addBox(0F, -4F, -4F, 2F, 8F, 8F),
                    PartPose.offset(wheels[k][0], 20F, wheels[k][1]));
        }
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void setupAnim(
            UnitEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        float turn = limbSwing * 0.8F;
        for (ModelPart wheel : wheels) {
            wheel.xRot = turn;
        }
        // a strike draws the log back and drives it forward, then rests it
        log.z = LOG_REST + (attackTime > 0F ? -Mth.sin(attackTime * (float) Math.PI) * THRUST : 0F);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        for (ModelPart part : drawn) {
            part.render(pose, buffer, light, overlay, color);
        }
    }
}
