package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.PlayerAuthority;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.replicate.Visibility;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroupRestoreFrameTest {
    @Test
    void controlGrantsDoNotRevealHiddenAlliesOrVisibleEnemyIdentities() {
        var h = SimHarness.builder().factions(3).build();
        int own = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 1, 0)[0];
        int ally = h.spawn(new FactionSlot(1), new Vec2d(16, 8), 1, 0)[0];
        h.spawn(new FactionSlot(2), new Vec2d(24, 8), 1, 0);
        var authority = new PlayerAuthority(1, new PlayerSlot(0), h.tick(), 0, 3, 3);
        var hidden = GroupRestoreFrame.capture(1, authority, h.core().queries(), (viewer, unit) -> false);
        assertThat(hidden.handles()).containsExactly(own);
        var visible = GroupRestoreFrame.capture(1, authority, h.core().queries(), Visibility.ALL);
        assertThat(visible.handles()).containsExactly(own, ally);
        var observer = new PlayerAuthority(1, PlayerSlot.ADMIN, h.tick(), -1, 0, 0);
        assertThat(GroupRestoreFrame.capture(1, observer, h.core().queries(), Visibility.ALL)
                        .handles())
                .isEmpty();
    }

    @Test
    void fullBoundRoundTripsIncludingNegativeGenerationHandles() {
        var handles = new ArrayList<Integer>();
        for (int i = 0; i < GroupRestoreFrame.MAX_HANDLES; i++) handles.add(Handle.of(i, Handle.MAX_GENERATION));
        var frame = new GroupRestoreFrame(Long.MAX_VALUE, Integer.MAX_VALUE, handles);
        byte[] bytes = WireCodec.encode(GroupRestoreFrame.WIRE, frame);
        assertThat(bytes.length).isEqualTo(GroupRestoreFrame.WIRE.maxSize());
        assertThat(WireCodec.decode(GroupRestoreFrame.WIRE, bytes)).isEqualTo(frame);
        handles.clear();
        assertThat(frame.handles()).hasSize(GroupRestoreFrame.MAX_HANDLES);
    }

    @Test
    void malformedIdentityDuplicateAndOversizedHandleListsAreRefused() {
        int handle = Handle.of(0, 1);
        assertThatThrownBy(() -> new GroupRestoreFrame(0, 1, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GroupRestoreFrame(1, 0, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GroupRestoreFrame(1, 1, List.of(0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GroupRestoreFrame(1, 1, List.of(handle, handle)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GroupRestoreFrame(
                        1, 1, java.util.Collections.nCopies(GroupRestoreFrame.MAX_HANDLES + 1, handle)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyTruncatedFrameAndOneExtraByteAreRefused() {
        byte[] bytes = WireCodec.encode(GroupRestoreFrame.WIRE, new GroupRestoreFrame(1, 1, List.of(Handle.of(0, 1))));
        for (int i = 0; i < bytes.length; i++) {
            byte[] truncated = java.util.Arrays.copyOf(bytes, i);
            assertThatThrownBy(() -> WireCodec.decode(GroupRestoreFrame.WIRE, truncated))
                    .isInstanceOf(RuntimeException.class);
        }
        byte[] extra = java.util.Arrays.copyOf(bytes, bytes.length + 1);
        assertThatThrownBy(() -> WireCodec.decode(GroupRestoreFrame.WIRE, extra))
                .isInstanceOf(RuntimeException.class);
    }
}
