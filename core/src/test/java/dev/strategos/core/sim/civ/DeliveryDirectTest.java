package dev.strategos.core.sim.civ;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Demolish;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.econ.Amount;
import dev.strategos.core.sim.econ.CarryStore;
import dev.strategos.core.sim.econ.EconomyChecks;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * CIV-05: {@code direct} delivery. A faction's gatherers credit each full carry, and what is left of a spent node, to
 * the treasury as {@code gathered} where they stand, with no dropsite to walk to; the conservation invariant holds every
 * tick; a default faction beside them still walks home; a match with the variant replays and restores to the live hashes.
 */
class DeliveryDirectTest {
    private static final MatchRules PLAIN =
            MatchRules.DEFAULTS.withPlay(new PlayRules(PlayRules.Victory.NONE, false, true, false));
    private static final List<CivId> LEAN_VS_DEFAULT = List.of(CivScene.LEAN, CivId.DEFAULT);
    private static final int STONE = 2;

    /** A rock of {@code size} x {@code size} columns of two blocks, each of {@code units} stone, at (x, z). */
    private static void rock(VoxelWorld world, int x, int z, int size, double units) {
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                for (int y = 1; y <= 2; y++) {
                    world.set(x + dx, y, z + dz, VoxelFixtures.SOLID);
                    world.material(
                            x + dx, y, z + dz, new Material(MaterialKind.STONE, "stone", "rock", Amount.of(units)));
                }
            }
        }
    }

    private static SimHarness match(String delivery, VoxelWorld world) {
        TemplateSet set = CivScene.with(TestRules.civ(delivery));
        return CivScene.match(set, LEAN_VS_DEFAULT, RuleCatalogue.DEFAULT, PLAIN, world);
    }

    private static int worker(SimHarness h, int faction, int x, int z) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(CivScene.WORKER, new FactionSlot(faction), new Vec2d(x, z), 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
    }

    /** A save and a load: the snapshot through its codec, encoded to JSON and decoded again. */
    private static MatchSnapshot roundTrip(MatchSnapshot saved) {
        return MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, saved)),
                        Migrations.production())
                .getOrThrow();
    }

    /** A restore of {@code h}'s snapshot, its voxel pages drawn from {@code world}. */
    private static SimHarness restored(SimHarness h, VoxelWorld world) {
        MatchSnapshot snapshot = roundTrip(h.core().snapshot());
        Map<Long, byte[]> slices = new TreeMap<>();
        for (var page : snapshot.pages()) {
            slices.put(
                    page.slice(), world.slice(new PageCoord(page.x(), page.z())).encode());
        }
        return SimHarness.restore(snapshot, KernelAccess.state(h.core()).templates(), slices::get);
    }

    private static final String DIRECT = "{\"delivery\":{\"id\":\"direct\"}}";

    @Test
    void aDirectFactionCreditsEachFullCarryWhereItStandsWithNoDropsite() {
        VoxelWorld world = VoxelFixtures.flat(64, 64, 32, 1);
        rock(world, 14, 14, 3, 5);
        rock(world, 46, 46, 3, 5);
        SimHarness h = match(DIRECT, world);
        MatchState s = KernelAccess.state(h.core());
        h.submit(new PlayerSlot(0), new Demolish(CivScene.building(h, 0, CivScene.CENTRE, 2, 2)));
        h.runTicks(40);
        assertThat(s.civs().rules(0).delivery()).isSameAs(DeliveryRule.DIRECT);
        assertThat(s.civs().rules(1).delivery()).isSameAs(DeliveryRule.DEFAULT);

        int mine = worker(h, 0, 12, 12);
        int theirs = worker(h, 1, 44, 44);
        h.submit(new PlayerSlot(0), Gather.replace(UnitSet.of(mine), 14, 2, 14));
        h.submit(new PlayerSlot(1), Gather.replace(UnitSet.of(theirs), 46, 2, 46));
        int i = Handle.index(mine);
        int j = Handle.index(theirs);
        long capacity = s.gatherRates().capacity(s.stats(), i, STONE);
        long last = 0;
        int credits = 0;
        double nearest = Double.MAX_VALUE;
        double farthest = 0;
        for (int t = 0; t < 2_400; t++) {
            h.step();
            double d = Math.hypot(s.motion().x(i) - 15, s.motion().z(i) - 15);
            if (t > 400) { // once it has reached the rock
                farthest = Math.max(farthest, d);
            }
            long now = s.treasury().gathered(0, STONE);
            if (now != last) {
                assertThat(now - last).as("a full carry at tick %d", t).isEqualTo(capacity);
                credits++;
                last = now;
            }
            assertThat(EconomyChecks.broken(s)).as("conservation at tick %d", t).isNull();
            nearest =
                    Math.min(nearest, Math.hypot(s.motion().x(j) - 2, s.motion().z(j) - 2));
        }

        assertThat(credits).as("several credits").isGreaterThanOrEqualTo(3);
        assertThat(farthest).as("the worker never walked to a dropsite").isLessThan(4);
        assertThat(s.treasury().gathered(1, STONE))
                .as("the default faction delivers")
                .isPositive();
        assertThat(h.events(Notice.class))
                .as("no 'no dropsite' idling")
                .noneMatch(n -> n.detail().equals("no dropsite"));
        assertThat(s.carry().amount(i)).isLessThan(capacity);
        assertThat(h.invariantViolations()).isEmpty();
    }

    @Test
    void theDefaultFactionBesideItStillWalksHome() {
        VoxelWorld world = VoxelFixtures.flat(64, 64, 32, 1);
        rock(world, 14, 14, 3, 5);
        rock(world, 46, 46, 3, 5);
        SimHarness h = match(DIRECT, world);
        MatchState s = KernelAccess.state(h.core());
        int theirs = worker(h, 1, 44, 44);
        h.submit(new PlayerSlot(1), Gather.replace(UnitSet.of(theirs), 46, 2, 46));
        int j = Handle.index(theirs);
        double nearest = Double.MAX_VALUE;
        for (int t = 0; t < 1_500; t++) {
            h.step();
            nearest = Math.min(
                    nearest, Math.hypot(s.motion().x(j) - 50, s.motion().z(j) - 50));
        }
        assertThat(s.treasury().gathered(1, STONE)).isPositive();
        assertThat(nearest).as("it reached the centre's reach").isLessThan(8);
    }

    @Test
    void aSpentNodeCreditsWhatIsLeftAndTheWorkerStops() {
        VoxelWorld world = VoxelFixtures.flat(64, 64, 32, 1);
        rock(world, 14, 14, 1, 3); // two blocks of 3: 6 units, under a carry
        SimHarness h = match(DIRECT, world);
        MatchState s = KernelAccess.state(h.core());
        h.submit(new PlayerSlot(0), new Demolish(CivScene.building(h, 0, CivScene.CENTRE, 2, 2)));
        h.runTicks(40);
        int mine = worker(h, 0, 12, 12);
        h.submit(new PlayerSlot(0), Gather.replace(UnitSet.of(mine), 14, 2, 14));
        int i = Handle.index(mine);
        assertThat(s.gatherRates().capacity(s.stats(), i, STONE)).isGreaterThan(Amount.of(6));

        for (int t = 0; t < 2_400; t++) {
            h.step();
            assertThat(EconomyChecks.broken(s)).as("conservation at tick %d", t).isNull();
        }

        assertThat(s.treasury().gathered(0, STONE)).as("all the rock, once").isEqualTo(Amount.of(6));
        assertThat(s.carry().resource(i)).as("the carry is empty").isEqualTo(CarryStore.NONE);
        assertThat(h.events(Notice.class))
                .as("idle because the node is gone, not for want of a dropsite")
                .noneMatch(n -> n.detail().equals("no dropsite"))
                .anyMatch(n -> n.detail().equals("no node"));
        assertThat(h.invariantViolations()).isEmpty();
    }

    @Test
    void aMatchWithTheVariantReplaysAndRestoresToTheLiveHashes() {
        VoxelWorld world = VoxelFixtures.flat(64, 64, 32, 1);
        rock(world, 14, 14, 3, 5);
        rock(world, 46, 46, 3, 5);
        SimHarness h = match(DIRECT, world);
        MatchState s = KernelAccess.state(h.core());
        int mine = worker(h, 0, 12, 12);
        int theirs = worker(h, 1, 44, 44);
        h.submit(new PlayerSlot(0), Gather.replace(UnitSet.of(mine), 14, 2, 14));
        h.submit(new PlayerSlot(1), Gather.replace(UnitSet.of(theirs), 46, 2, 46));
        h.runTicks(900);

        SimHarness back = restored(h, world);
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        for (int t = 0; t < 900; t++) {
            h.step();
            back.step();
            assertThat(back.stateHash()).as("tick %d", h.tick()).isEqualTo(h.stateHash());
        }
        assertThat(s.treasury().gathered(0, STONE)).isPositive();
        var replay = h.replay();
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalHash()).isEqualTo(h.stateHash());
    }
}
