package dev.strategos.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strategos.core.client.look.MountPose;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.platform.Ids;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

/**
 * The horse of a mounted unit (UNIT-06): vanilla's horse mesh ({@link HorseModel#createBodyMesh}, no part copied) in a
 * model of our own, since {@code HorseModel} is bound to {@code AbstractHorse}. Saddled, adult, never eating or
 * rearing on its own; its legs, head and tail follow {@link MountPose}, which reads the walk animation's stride and
 * speed and the swing a strike starts. The rider is another model, drawn by {@link MountRenderer}.
 */
final class MountModel extends EntityModel<UnitEntity> {
    static final ModelLayerLocation LAYER = new ModelLayerLocation(Ids.id("unit_mount"), "main");

    /** The head parts' resting pitch in the vanilla mesh: thirty degrees. */
    private static final float HEAD_REST = (float) (Math.PI / 6);

    private final ModelPart body;
    private final ModelPart headParts;
    private final ModelPart tail;
    private final ModelPart frontLeft;
    private final ModelPart frontRight;
    private final ModelPart hindLeft;
    private final ModelPart hindRight;
    private final List<ModelPart> drawn;

    MountModel(ModelPart root) {
        super(net.minecraft.client.renderer.RenderType::entityCutoutNoCull);
        body = root.getChild("body");
        headParts = root.getChild("head_parts");
        tail = body.getChild("tail");
        frontLeft = root.getChild("left_front_leg");
        frontRight = root.getChild("right_front_leg");
        hindLeft = root.getChild("left_hind_leg");
        hindRight = root.getChild("right_hind_leg");
        drawn = List.of(body, headParts, frontLeft, frontRight, hindLeft, hindRight); // not the foal's legs
    }

    static LayerDefinition layer() {
        return LayerDefinition.create(HorseModel.createBodyMesh(CubeDeformation.NONE), 64, 64);
    }

    @Override
    public void setupAnim(
            UnitEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        MountPose pose = MountPose.of(limbSwing, limbSwingAmount, attackTime);
        frontLeft.xRot = pose.frontLeft();
        frontRight.xRot = pose.frontRight();
        hindLeft.xRot = pose.hindLeft();
        hindRight.xRot = pose.hindRight();
        headParts.xRot = HEAD_REST + pose.head();
        body.xRot = pose.bodyPitch();
        tail.xRot = HEAD_REST + pose.tail();
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        for (ModelPart part : drawn) {
            part.render(pose, buffer, light, overlay, color);
        }
    }
}
