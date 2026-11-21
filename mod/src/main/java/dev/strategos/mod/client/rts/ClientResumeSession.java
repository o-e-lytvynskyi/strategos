package dev.strategos.mod.client.rts;

import dev.strategos.core.client.camera.CameraLimits;
import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.client.model.ClientResume;
import dev.strategos.mod.server.net.GroupRestoreFrame;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Session-only camera/groups cache, available only after an authenticated match confirmation. */
final class ClientResumeSession {
    /** Bootstrap every published/interpolated pose at the saved height before a jump or player tick reads it. */
    static CameraRig camera(ClientResume.Pose saved, CameraLimits limits, double ground) {
        var rig = new CameraRig(limits, saved.focusX(), saved.focusZ(), ground, saved.yaw());
        rig.zoomTo(saved.height());
        rig.jumpTo(rig.focusX(), rig.focusZ(), ground);
        return rig;
    }

    record Scope(String realm, UUID account) {}

    private @Nullable Scope scope;
    private int confirmedMatch = -1;
    private long epoch;
    private @Nullable Set<Integer> confirmedHandles;
    private ClientResume cache = new ClientResume();

    void joined(@Nullable Scope next, int matchId, long epoch) {
        if (next == null || !next.equals(scope)) cache = new ClientResume();
        scope = next;
        confirmedMatch = matchId;
        this.epoch = epoch;
        confirmedHandles = null;
    }

    boolean accept(GroupRestoreFrame frame) {
        if (confirmedHandles != null || frame.epoch() != epoch || frame.matchId() != confirmedMatch) return false;
        Set<Integer> valid = Set.copyOf(frame.handles());
        confirmedHandles = valid;
        ClientResume.Saved saved = cache.of(confirmedMatch);
        if (saved != null)
            cache.save(
                    confirmedMatch,
                    saved.pose(),
                    saved.groups().stream()
                            .map(group -> group.stream().filter(valid::contains).toList())
                            .toList());
        return true;
    }

    boolean ready() {
        return confirmedHandles != null;
    }

    void disconnected() {
        confirmedMatch = -1;
        confirmedHandles = null;
    }

    void remember(int matchId, ClientResume.Pose pose, List<List<Integer>> groups) {
        if (ready() && matchId == confirmedMatch && confirmedMatch > 0) cache.save(matchId, pose, groups);
    }

    ClientResume.@Nullable Saved of(int matchId) {
        return ready() && matchId == confirmedMatch && confirmedMatch > 0 ? cache.of(matchId) : null;
    }
}
