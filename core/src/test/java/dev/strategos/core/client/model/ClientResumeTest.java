package dev.strategos.core.client.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.client.select.ControlGroups;
import dev.strategos.core.client.select.Selection;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * MATCH-04.5 in L1: the camera's pose and the control groups a player left match 3 with come back when it returns to
 * match 3, whatever it did in match 4 meanwhile; a match never left has nothing kept.
 */
class ClientResumeTest {
    @Test
    void theCameraAndTheGroupsSurviveAResume() {
        ClientResume resume = new ClientResume();
        ControlGroups groups = new ControlGroups();
        Selection selection = new Selection(0);
        selection.set(List.of(7, 9));
        groups.set(1, selection);
        selection.set(List.of(11));
        groups.set(4, selection);
        resume.save(3, new ClientResume.Pose(40.5, 12, 135, 30), groups.snapshot());

        groups.restore(List.of()); // into match 4: nothing of match 3's
        assertThat(groups.members(1)).isEmpty();
        selection.set(List.of(2));
        groups.set(1, selection);
        resume.save(4, new ClientResume.Pose(0, 0, 0, 24), groups.snapshot());

        ClientResume.Saved back = resume.of(3);
        assertThat(back).isNotNull();
        groups.restore(back.groups());
        assertThat(groups.members(1)).containsExactly(7, 9);
        assertThat(groups.members(4)).containsExactly(11);
        assertThat(groups.members(2)).isEmpty();
        assertThat(back.pose()).isEqualTo(new ClientResume.Pose(40.5, 12, 135, 30));
        assertThat(resume.of(5)).as("a match never left").isNull();

        CameraRig rig = new CameraRig(
                new dev.strategos.core.client.camera.CameraLimits(0, 0, 128, 128, 16),
                back.pose().focusX(),
                back.pose().focusZ(),
                64,
                back.pose().yaw());
        rig.zoomTo(back.pose().height());
        assertThat(rig.focusX()).isEqualTo(40.5);
        assertThat(rig.yaw()).isEqualTo(135);
        assertThat(rig.height()).isEqualTo(30);
    }
}
