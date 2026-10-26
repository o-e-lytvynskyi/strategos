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
        assertThat(s.hasLargeMobility()).isFalse();
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
