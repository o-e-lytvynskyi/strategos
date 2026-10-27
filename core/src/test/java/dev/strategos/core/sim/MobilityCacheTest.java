package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.build.Construction;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class MobilityCacheTest {
    private static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");
    private static final TemplateId UNIT = new TemplateId("strategos:unit/cache_test");

    @Test
    void acceptedReloadReclassifiesLiveAndNewUnitsAtASparseIndex() {
        SimHarness h = SimHarness.builder().templates(set("infantry", 7)).build();
        int first = spawn(h);
        MatchState s = KernelAccess.state(h.core());
        assertThat(s.hasLargeMobility()).isFalse();
        assertThat(s.mobility(first)).isEqualTo(MobilityClass.INFANTRY);
        s.swapTemplates(set("large", 7));
        assertThat(s.hasLargeMobility()).isTrue();
        assertThat(s.mobility(first)).isEqualTo(MobilityClass.LARGE);
        assertThat(s.mobility(spawn(h))).isEqualTo(MobilityClass.LARGE);
        s.swapTemplates(set("infantry", 7));
        assertThat(s.hasLargeMobility()).isFalse();
        assertThat(s.mobility(first)).isEqualTo(MobilityClass.INFANTRY);
    }

    @Test
    void rejectedReloadKeepsTheLiveUnitsClassification() {
        SimHarness h = SimHarness.builder().templates(set("large", 7)).build();
        int first = spawn(h);
        MatchState s = KernelAccess.state(h.core());
        s.swapTemplates(set("infantry", 8));
        assertThat(s.hasLargeMobility()).isTrue();
        assertThat(s.mobility(first)).isEqualTo(MobilityClass.LARGE);
        assertThat(s.mobility(spawn(h))).isEqualTo(MobilityClass.LARGE);
        s.swapTemplates(new TemplateSet(List.of()));
        assertThat(s.hasLargeMobility()).isTrue();
        assertThat(s.mobility(first)).isEqualTo(MobilityClass.LARGE);
    }

    @Test
    void retiredIndicesAndUnknownMobilityKeepTheirQueryErrors() {
        SimHarness h = SimHarness.builder().templates(set("infantry", 7)).build();
        int first = spawn(h);
        MatchState s = KernelAccess.state(h.core());
        s.swapTemplates(set("unknown", 7));
        assertThatThrownBy(() -> s.mobility(first))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown mobility 'unknown'");
        s.identity().set(first, 0, 0);
        assertThatThrownBy(() -> s.mobility(first)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildingTemplatesRetainTheirOriginalQueryError() {
        SimHarness h = SimHarness.builder().build();
        int first = Handle.index(h.spawn(new FactionSlot(0), new Vec2d(10.5, 10.5), 1, 0)[0]);
        MatchState s = KernelAccess.state(h.core());
        int building = java.util.Objects.requireNonNull(s.templates().byId(new TemplateId("strategos:building/house")))
                .netIndex();
        s.identity().set(first, building, 0);
        assertThatThrownBy(() -> s.mobility(first))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown mobility 'building'");
        assertThat(s.templates().all())
                .as("the building adds no large class; the horseman (UNIT-06) is the builtin set's only one")
                .filteredOn(t -> "large".equals(t.mobility()))
                .extracting(t -> t.id().value())
                .containsExactly("strategos:unit/horseman");
    }

    @Test
    void largeBoundaryRecoveryStillRunsWithoutFootprints() {
        SimHarness h = SimHarness.builder().templates(set("large", 7)).build();
        h.publishAll(VoxelFixtures.flat(32, 32, 8, 1));
        int first = spawn(h);
        MatchState s = KernelAccess.state(h.core());
        s.motion().set(first, 0.5, 1, 10.5, 0);
        Construction.evictBlocked(s);
        assertThat(s.motion().x(first)).isGreaterThanOrEqualTo(1.5);
    }

    @Test
    void aMatchWithoutALiveLargeUnitSkipsTheEvictionScanThoughTheSetHoldsOne() {
        SimHarness h = SimHarness.builder().build(); // the builtin set holds the horseman
        MatchState s = KernelAccess.state(h.core());
        assertThat(s.hasLargeMobility()).isTrue();
        assertThat(s.hasLiveLargeUnits()).isFalse();
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(10.5, 10.5), 3, 2));
        h.step();
        assertThat(s.hasLiveLargeUnits()).as("foot soldiers only").isFalse();
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(HORSEMAN, new FactionSlot(0), new Vec2d(20.5, 10.5), 2, 2));
        int horseman = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .map(UnitCreated.class::cast)
                .filter(e -> s.mobility(Handle.index(e.handle())) == MobilityClass.LARGE)
                .findFirst()
                .orElseThrow()
                .handle();
        assertThat(s.hasLiveLargeUnits()).isTrue();
        for (int handle : h.queries().unitHandles()) {
            if (s.mobility(Handle.index(handle)) == MobilityClass.LARGE) {
                s.removeUnit(handle);
            }
        }
        assertThat(horseman).isNotZero();
        assertThat(s.hasLiveLargeUnits()).as("the last horseman is gone").isFalse();
    }

    private static TemplateSet set(String mobility, int index) {
        return new TemplateSet(List.of(new UnitTemplate(
                UNIT,
                index,
                "Test",
                List.of(),
                0.1875,
                45,
                mobility,
                "strategos:unit_mount",
                "horse",
                "",
                1.4f,
                2f,
                0.7)));
    }

    private static int spawn(SimHarness h) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(UNIT, new FactionSlot(0), new Vec2d(10.5, 10.5), 1, 0));
        return Handle.index(h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .map(UnitCreated.class::cast)
                .findFirst()
                .orElseThrow()
                .handle());
    }
}
