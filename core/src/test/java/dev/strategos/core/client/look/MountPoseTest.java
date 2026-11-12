package dev.strategos.core.client.look;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class MountPoseTest {
    @Test
    void aStandingHorseHoldsStill() {
        MountPose pose = MountPose.of(7.3F, 0F, 0F);
        assertThat(pose.frontLeft()).isZero();
        assertThat(pose.hindRight()).isZero();
        assertThat(pose.tail()).isZero();
        assertThat(pose.head()).isZero();
        assertThat(pose.bodyPitch()).isZero();
        assertThat(pose.bob()).isZero();
    }

    @Test
    void aGallopSwingsFrontAndHindLegsInOpposition() {
        MountPose pose = MountPose.of(0F, 1F, 0F);
        assertThat(pose.frontLeft()).isCloseTo(MountPose.LEG_SWING, within(1e-6F));
        assertThat(pose.hindLeft()).isCloseTo(-MountPose.LEG_SWING, within(1e-6F));
        assertThat(pose.tail()).isCloseTo(MountPose.STREAM, within(1e-6F));
    }

    @Test
    void theStrideGrowsWithSpeedAndStopsAtTheLimit() {
        float walk = Math.abs(MountPose.of(0F, 0.3F, 0F).frontLeft());
        float gallop = Math.abs(MountPose.of(0F, 1F, 0F).frontLeft());
        assertThat(walk).isLessThan(gallop);
        assertThat(MountPose.of(0F, 5F, 0F)).isEqualTo(MountPose.of(0F, 1F, 0F));
        assertThat(MountPose.of(0F, -1F, 0F).frontLeft()).isZero();
    }

    @Test
    void aStrikeTossesTheHeadAndRearsThenSettles() {
        assertThat(MountPose.of(0F, 0F, 0.5F).head()).isCloseTo(MountPose.TOSS, within(1e-6F));
        assertThat(MountPose.of(0F, 0F, 0.5F).bodyPitch()).isCloseTo(-MountPose.REAR, within(1e-6F));
        assertThat(MountPose.of(0F, 0F, 0.25F).head()).isLessThan(MountPose.TOSS);
        assertThat(MountPose.of(0F, 0F, 0F).head()).isZero();
        assertThat(MountPose.of(0F, 0F, 1F).head()).isZero();
        assertThat(MountPose.of(0F, 0F, 2F).bodyPitch()).isZero();
    }

    @Test
    void theBodyRisesAtMostABounce() {
        for (float swing = 0; swing < 20; swing += 0.37F) {
            assertThat(MountPose.of(swing, 1F, 0F).bob()).isBetween(0F, MountPose.BOUNCE);
        }
    }
}
