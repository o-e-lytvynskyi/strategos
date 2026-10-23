package dev.strategos.core.client.camera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * CAM-02.1–2: the same keys give the same pose after a second at 30, 60 and 144 frames a second, and a drag the same
 * turn; the focus never
 * leaves the match space's margin; the eye never moves more than spike S9's 5 blocks a tick; the focus follows the
 * terrain.
 */
class CameraRigTest {
    private static final CameraLimits SPACE = new CameraLimits(0, 0, 384, 384, CameraLimits.MARGIN);
    private static final CameraRig.Ground FLAT = (x, z) -> 64;

    @Test
    void samePoseAtAnyFrameRate() {
        CameraPose at30 = oneSecond(30, 0);
        CameraPose at60 = oneSecond(60, 0);
        CameraPose at144 = oneSecond(144, 0);

        for (CameraPose p : new CameraPose[] {at60, at144}) {
            assertThat(p.x()).isCloseTo(at30.x(), within(1e-9));
            assertThat(p.y()).isCloseTo(at30.y(), within(1e-9));
            assertThat(p.z()).isCloseTo(at30.z(), within(1e-9));
            assertThat(p.yaw()).isCloseTo(at30.yaw(), within(1e-9));
        }
        assertThat(at30.z()).as("it moved").isNotCloseTo(192, within(1.0));
    }

    /**
     * A drag lands in the tick that runs in its frame, so how it splits over ticks depends on the frames; a second's
     * drag, once the rig has settled, turns it the same at every rate.
     */
    @Test
    void aDragTurnsTheSameAtAnyFrameRate() {
        double at30 = oneSecond(30, 90).yaw();

        assertThat(oneSecond(60, 90).yaw()).isCloseTo(at30, within(1e-6));
        assertThat(oneSecond(144, 90).yaw()).isCloseTo(at30, within(1e-6));
        assertThat(at30).isCloseTo(90, within(1e-6));
    }

    /**
     * One second of frames at {@code fps} with forward held, two zoom notches out and {@code dragPerSecond} degrees of
     * middle drag spread over the frames (then, with a drag, 60 still ticks to settle); a tick every 50 ms takes the
     * drag of the frames since the last one, as the client's tick does.
     */
    private static CameraPose oneSecond(int fps, double dragPerSecond) {
        CameraRig rig = new CameraRig(SPACE, 192, 192, 64, 0);
        double drag = 0;
        int ticks = 0;
        boolean zoomed = false;
        double forward = dragPerSecond == 0 ? 1 : 0;
        for (int frame = 1; frame <= fps; frame++) {
            double time = (double) frame / fps;
            drag += dragPerSecond / fps;
            while ((ticks + 1) * 0.05 <= time + 1e-12) {
                rig.tick(new CameraInput(forward, 0, 0, drag, zoomed ? 0 : -2), FLAT);
                zoomed = true;
                drag = 0;
                ticks++;
            }
        }
        assertThat(ticks).isEqualTo(20);
        if (dragPerSecond != 0) {
            for (int t = 0; t < 60; t++) {
                rig.tick(CameraInput.NONE, FLAT);
            }
        }
        return rig.pose(0);
    }

    @Test
    void theFocusStaysInsideTheMargin() {
        CameraRig rig = new CameraRig(SPACE, 192, 192, 64, 0);
        for (int t = 0; t < 2_000; t++) {
            rig.tick(new CameraInput(1, 0.5, 0, 0, 0), FLAT);
            assertThat(rig.focusX()).isBetween(-CameraLimits.MARGIN, 384 + CameraLimits.MARGIN);
            assertThat(rig.focusZ()).isBetween(-CameraLimits.MARGIN, 384 + CameraLimits.MARGIN);
        }
        assertThat(rig.focusZ()).as("it reached the margin").isCloseTo(384 + CameraLimits.MARGIN, within(1e-9));
    }

    @Test
    void theEyeMovesAtMostFiveBlocksATick() {
        CameraRig rig = new CameraRig(SPACE, 192, 192, 64, 0);
        double most = 0;
        for (int t = 0; t < 200; t++) {
            CameraPose before = rig.pose();
            rig.tick(new CameraInput(1, 1, 1, t == 0 ? 720 : 0, t == 0 ? -20 : 0), FLAT);
            most = Math.max(most, before.eyeDistance(rig.pose()));
        }

        assertThat(most).isLessThanOrEqualTo(CameraRig.MAX_EYE_STEP + 1e-9).isGreaterThan(4.9);
        assertThat(rig.height()).as("the zoom still got there").isCloseTo(CameraRig.MAX_HEIGHT, within(0.5));
    }

    @Test
    void theFocusFollowsTheTerrainAndAJumpMovesIt() {
        CameraRig rig = new CameraRig(SPACE, 100, 100, 64, 0);
        for (int t = 0; t < 60; t++) {
            rig.tick(CameraInput.NONE, (x, z) -> 80);
        }
        assertThat(rig.pose().y()).isCloseTo(80 + CameraRig.DEFAULT_HEIGHT, within(0.01));

        rig.jumpTo(300, 50, 70);
        assertThat(rig.focusX()).isEqualTo(300);
        assertThat(rig.pose(0.5).y()).isCloseTo(70 + CameraRig.DEFAULT_HEIGHT, within(1e-9));
    }
}
