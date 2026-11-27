package dev.strategos.core.sim.civ;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.PlaceWall;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Construction;
import dev.strategos.core.sim.build.Placing;
import dev.strategos.core.sim.econ.EconomyChecks;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * CIV-05: {@code self} construction. A faction's placed foundation is committed at once, with its world writes and the
 * eviction of units standing on it, and then grows by {@code rate} of one builder's work a tick with no builder; builders
 * add nothing; walls and gates the same; the cost is paid at placement and conservation holds; a default faction beside
 * it still waits for its first builder; a match with the variant replays and restores to the live hashes.
 */
class ConstructionSelfTest {
    private static final TemplateId HOUSE = CivScene.HOUSE;
    private static final TemplateId PALISADE = new TemplateId("strategos:building/palisade");
    private static final PlayerSlot P0 = new PlayerSlot(0);
    private static final PlayerSlot P1 = new PlayerSlot(1);
    private static final long HOUSE_TICKS = 600; // Cost/time 30 s
    private static final List<CivId> LEAN_VS_DEFAULT = List.of(CivScene.LEAN, CivId.DEFAULT);
    private static final String SELF2 = "{\"construction\":{\"id\":\"self\",\"params\":{\"rate\":2}}}";

    private static SimHarness match(String rules) {
        return CivScene.match(CivScene.with(TestRules.civ(rules)), LEAN_VS_DEFAULT);
    }

    private static CommandResult place(SimHarness h, PlayerSlot who, TemplateId t, int x, int z, int... builders) {
        h.submit(who, new PlaceBuilding(t, x, z, 0, UnitSet.of(builders), QueueMode.REPLACE));
        h.step();
        CommandResult r = h.results().get(h.results().size() - 1);
        assertThat(r.outcome()).as("%s", r).isEqualTo(CommandOutcome.ACCEPTED);
        return r;
    }

    /** The first unfinished building not among {@code known}. */
    private static int newest(MatchState s, int... known) {
        for (int handle : s.buildings().handles()) {
            if (s.buildings().state(handle) != BuildingStore.COMPLETE
                    && java.util.Arrays.stream(known).noneMatch(k -> k == handle)) {
                return handle;
            }
        }
        throw new AssertionError("no new building");
    }

    private static int[] workers(SimHarness h, int faction, int n, double x, double z) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.WORKER, new FactionSlot(faction), new Vec2d(x, z), n, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    @Test
    void aSelfFoundationIsCommittedAtPlacementAndEvictsTheUnitsOnIt() {
        SimHarness h = match(SELF2);
        MatchState s = KernelAccess.state(h.core());
        assertThat(s.civs().rules(0).construction()).isNotSameAs(ConstructionRule.DEFAULT);
        assertThat(s.civs().rules(1).construction()).isSameAs(ConstructionRule.DEFAULT);
        int[] standing = workers(h, 0, 1, 20.5, 20.5);
        int i = Handle.index(standing[0]);
        h.runTicks(2);
        assertThat(s.footprintAt(20, 20)).isFalse();

        CommandResult r = place(h, P0, HOUSE, 20, 20);

        assertThat(r.outcome()).as("%s", r).isEqualTo(CommandOutcome.ACCEPTED);
        int house = newest(s);
        assertThat(s.buildings().state(house)).as("no builder came").isEqualTo(BuildingStore.CONSTRUCTING);
        assertThat(s.footprintAt(20, 20)).as("its columns block units").isTrue();
        Footprint at = s.footprintOf(house);
        double x = s.motion().x(i);
        double z = s.motion().z(i);
        assertThat(x >= at.anchorX()
                        && x < at.anchorX() + at.spanX()
                        && z >= at.anchorZ()
                        && z < at.anchorZ() + at.spanZ())
                .as("the worker was moved off the foundation")
                .isFalse();
        assertThat(h.invariantViolations()).isEmpty();
    }

    @Test
    void aSelfSiteProgressesAtTheDataRateWithNoBuildersAndFinishesOnTime() {
        SimHarness h = match(SELF2);
        MatchState s = KernelAccess.state(h.core());
        long stock = s.treasury().stock(0, 1);
        long placedAt = h.tick() + 1;
        place(h, P0, HOUSE, 20, 20);
        int house = newest(s);
        long perTick = 2 * Construction.MICRO;
        assertThat(s.buildings().work(house)).as("the placing tick's own step").isEqualTo(perTick);
        assertThat(s.treasury().stock(0, 1)).as("the cost is paid at placement").isLessThan(stock);

        long finished = -1;
        for (int t = 0; t < 1_000 && finished < 0; t++) {
            h.step();
            if (s.buildings().state(house) == BuildingStore.CONSTRUCTING) {
                assertThat(s.buildings().work(house)).isEqualTo(perTick * (h.tick() - placedAt + 1));
            }
            assertThat(EconomyChecks.broken(s))
                    .as("conservation at tick %d", h.tick())
                    .isNull();
            if (s.buildings().state(house) == BuildingStore.COMPLETE) {
                finished = h.tick();
            }
        }

        assertThat(finished - placedAt + 1).as("T ÷ rate ticks").isEqualTo(HOUSE_TICKS / 2);
        assertThat(s.buildings().progress(house)).isEqualTo(BuildingStore.FULL);
        assertThat(h.invariantViolations()).isEmpty();
    }

    @Test
    void buildersAddNothingToASelfSite() {
        SimHarness h = match(SELF2);
        MatchState s = KernelAccess.state(h.core());
        int template = Objects.requireNonNull(s.templates().byId(HOUSE)).netIndex();
        Blueprint b = Objects.requireNonNull(s.templates().blueprint(template));
        int[] slots = Placing.slots(s, Footprint.of(b, 20, 20, 0));
        int cell = slots[0];
        int[] crew = workers(h, 0, 4, (cell >>> 16) + 0.5, (cell & 0xFFFF) + 0.5);
        place(h, P0, HOUSE, 20, 20, crew);
        int crewed = newest(s);
        long crewedAt = h.tick();
        place(h, P0, HOUSE, 8, 20);
        int alone = newest(s, crewed);
        long aloneAt = h.tick();
        h.runTicks(120);

        assertThat(s.buildings().work(crewed))
                .as("four builders worked it, and it grew by the rate alone")
                .isEqualTo(2 * Construction.MICRO * (h.tick() - crewedAt + 1));
        assertThat(s.buildings().work(alone)).isEqualTo(2 * Construction.MICRO * (h.tick() - aloneAt + 1));
        assertThat(h.invariantViolations()).isEmpty();
    }

    @Test
    void aSelfWallAndItsGateAreCommittedAndGrowWithNoBuilders() {
        SimHarness h = match(SELF2);
        MatchState s = KernelAccess.state(h.core());
        h.submit(P0, new PlaceWall(PALISADE, 20, 8, 27, 8, List.of(1), UnitSet.of()));
        h.step();
        CommandResult r = h.results().get(h.results().size() - 1);
        assertThat(r.outcome()).as("%s", r).isEqualTo(CommandOutcome.ACCEPTED);
        List<Integer> segments = new ArrayList<>();
        for (int handle : s.buildings().handles()) {
            if (s.buildings().faction(handle) == 0
                    && s.templates()
                            .byIndex(s.buildings().template(handle))
                            .id()
                            .value()
                            .contains("palisade")) {
                segments.add(handle);
            }
        }
        assertThat(segments).hasSizeGreaterThan(1);
        for (int handle : segments) {
            assertThat(s.buildings().state(handle)).isEqualTo(BuildingStore.CONSTRUCTING);
        }
        assertThat(s.play().gates().empty()).as("a foundation is no gate").isTrue();

        assertThat(h.runUntil(() -> s.play().gates().count() == 1, 3_000))
                .as("the gate segment finishes with no builder")
                .isTrue();
        for (int handle : segments) {
            assertThat(s.buildings().work(handle)).isPositive();
        }
        assertThat(EconomyChecks.broken(s)).isNull();
        assertThat(h.invariantViolations()).isEmpty();
    }

    @Test
    void aDefaultFactionBesideItIsUnchanged() {
        SimHarness h = match(SELF2);
        MatchState s = KernelAccess.state(h.core());
        place(h, P1, HOUSE, 42, 42);
        int house = newest(s);
        assertThat(s.buildings().faction(house)).isEqualTo(1);
        h.runTicks(100);
        assertThat(s.buildings().state(house)).as("waits for its first builder").isEqualTo(BuildingStore.FOUNDATION);
        assertThat(s.buildings().work(house)).isZero();
        assertThat(s.footprintAt(42, 42)).isFalse();
    }

    @Test
    void aMatchWithTheVariantReplaysAndRestoresToTheLiveHashes() {
        VoxelWorld world = VoxelFixtures.flat(64, 64, 32, 1);
        SimHarness h = CivScene.match(
                CivScene.with(TestRules.civ(SELF2)),
                LEAN_VS_DEFAULT,
                RuleCatalogue.DEFAULT,
                dev.strategos.core.sim.MatchRules.DEFAULTS.withPlay(new dev.strategos.core.sim.PlayRules(
                        dev.strategos.core.sim.PlayRules.Victory.NONE, false, true, false)),
                world);
        place(h, P0, HOUSE, 20, 20);
        place(h, P1, HOUSE, 42, 42, workers(h, 1, 2, 46.5, 46.5));
        h.runTicks(150);

        SimHarness back = restored(h, world);
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        for (int t = 0; t < 600; t++) {
            h.step();
            back.step();
            assertThat(back.stateHash()).as("tick %d", h.tick()).isEqualTo(h.stateHash());
        }
        var replay = h.replay();
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalHash()).isEqualTo(h.stateHash());
    }

    private static MatchSnapshot roundTrip(MatchSnapshot saved) {
        return MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, saved)),
                        Migrations.production())
                .getOrThrow();
    }

    private static SimHarness restored(SimHarness h, VoxelWorld world) {
        MatchSnapshot snapshot = roundTrip(h.core().snapshot());
        Map<Long, byte[]> slices = new TreeMap<>();
        for (var page : snapshot.pages()) {
            slices.put(
                    page.slice(), world.slice(new PageCoord(page.x(), page.z())).encode());
        }
        return SimHarness.restore(snapshot, KernelAccess.state(h.core()).templates(), slices::get);
    }
}
