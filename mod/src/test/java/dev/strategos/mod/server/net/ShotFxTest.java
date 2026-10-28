package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Fx;
import dev.strategos.core.protocol.FxKind;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.ShotFired;
import dev.strategos.core.sim.ShotLanded;
import dev.strategos.core.sim.combat.ShotStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CMB-04: a tick's shots become one event each with both ends, batched once per pair of pages. */
class ShotFxTest {
    private static ShotFired fired(double fromX, double toX, long launch, long land) {
        return new ShotFired(1, 2, fromX, 70, 5, toX, 70, 9, launch, land);
    }

    @Test
    void aLaunchWhoseEndsAreInTwoPagesIsOneGroup() {
        List<Fx.Event> events = ShotFx.events(List.<OutputEvent>of(fired(10, 40, 5, 9)), 64);
        assertThat(events).hasSize(1);
        Fx.Event e = events.get(0);
        assertThat(e.kind()).isEqualTo(FxKind.SHOT_LAUNCH);
        assertThat(e.y8()).isEqualTo(6 * 8); // Y from the origin's height
        assertThat(e.flightTicks()).isEqualTo(4);
        List<ShotFx.Group> groups = ShotFx.group(events);
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).cellAx()).isZero();
        assertThat(groups.get(0).cellBx()).isEqualTo(32);
    }

    @Test
    void sixtyFiveShotsOnOnePagePairMake64AndOne() {
        List<OutputEvent> shots = new ArrayList<>();
        for (int k = 0; k < 65; k++) {
            shots.add(fired(10, 40, 5, 9));
        }
        List<ShotFx.Group> groups = ShotFx.group(ShotFx.events(shots, 0));
        assertThat(groups).extracting(g -> g.events().size()).containsExactly(Fx.MAX_EVENTS, 1);
    }

    @Test
    void groupsComeInPageOrderWhateverTheEventOrder() {
        List<Fx.Event> events =
                ShotFx.events(List.of(fired(100, 110, 5, 9), fired(10, 20, 5, 9), fired(10, 40, 5, 9)), 0);
        List<ShotFx.Group> forward = ShotFx.group(events);
        assertThat(forward).extracting(ShotFx.Group::cellAx).containsExactly(0, 0, 96);
        assertThat(forward).extracting(ShotFx.Group::cellBx).containsExactly(16, 32, 96);
        assertThat(ShotFx.group(events)).isEqualTo(forward);
        assertThat(forward.stream().flatMap(g -> g.events().stream()).count()).isEqualTo(3);
    }

    @Test
    void anImpactCarriesItsReleaseOrLandsFromItself() {
        ShotLanded released = new ShotLanded(1, 2, 2, new ShotStore.Release(10, 70, 5, 5), 40, 70, 9, 9);
        ShotLanded legacy = new ShotLanded(1, 2, 0, null, 40, 70, 9, 9);
        List<Fx.Event> events = ShotFx.events(List.of(released, legacy), 64);
        assertThat(events).allMatch(e -> e.kind() == FxKind.SHOT_IMPACT);
        assertThat(events.get(0).x8()).isEqualTo(80);
        assertThat(events.get(0).toX8()).isEqualTo(320);
        assertThat(events.get(0).flightTicks()).isEqualTo(4);
        assertThat(events.get(1).x8()).isEqualTo(events.get(1).toX8());
        assertThat(events.get(1).z8()).isEqualTo(events.get(1).toZ8());
        assertThat(events.get(1).flightTicks()).isEqualTo(1);
    }

    @Test
    void theFlightIsAtLeastOneTickAndAtMostTheWireCap() {
        List<Fx.Event> events =
                ShotFx.events(List.of(fired(10, 40, 9, 9), fired(10, 40, 0, Fx.MAX_FLIGHT_TICKS + 500L)), 0);
        assertThat(events).extracting(Fx.Event::flightTicks).containsExactly(1, Fx.MAX_FLIGHT_TICKS);
    }
}
