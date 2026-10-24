package dev.strategos.core.sim.build;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.world.WritePriority;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * BLD-03.1 in L1: n builders at work throughout raise a house in exactly ceil(T ÷ n^0.7) ticks, T its 30 s, for 1, 2,
 * 5 and 10 of them; the writer is asked for its blueprint's blocks once each, in build order; and a match saved
 * mid-build resumes to the same hashes.
 */
class ConstructionTest {
    static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final PlayerSlot P0 = new PlayerSlot(0);

    /** A flat 64², a civic centre at (2, 2), 1,000 wood for faction 0. */
    static SimHarness match() {
        return match(VoxelFixtures.flat(64, 64, 32, 1));
    }

    /** {@code world}, 64², with a civic centre at (2, 2) and 1,000 wood for faction 0. */
    static SimHarness match(dev.strategos.core.testkit.VoxelWorld world) {
        SimHarness h = SimHarness.builder().size(64, 64).yBand(0, 31, 1).build();
        h.publishAll(world);
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 2, 2, 0));
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 1_000));
        h.step();
        return h;
    }

    /** {@code n} workers, each on one of the house's builder slots at (30, 30). */
    static int[] buildersOnSlots(SimHarness h, int n) {
        MatchState s = KernelAccess.state(h.core());
        int template = Objects.requireNonNull(s.templates().byId(HOUSE)).netIndex();
        Blueprint b = Objects.requireNonNull(s.templates().blueprint(template));
        int[] slots = Placing.slots(s, Footprint.of(b, 30, 30, 0));
        for (int k = 0; k < n; k++) {
            int cell = slots[k * slots.length / n];
            h.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(
                            TemplateSet.WORKER,
                            new FactionSlot(0),
                            new Vec2d((cell >>> 16) + 0.5, (cell & 0xFFFF) + 0.5),
                            1,
                            0));
        }
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    @Test
    void buildTimeForOneTwoFiveAndTenBuilders() {
        for (int n : new int[] {1, 2, 5, 10}) {
            SimHarness h = match();
            MatchState s = KernelAccess.state(h.core());
            int[] builders = buildersOnSlots(h, n);
            assertThat(builders).hasSize(n);
            h.submit(P0, new PlaceBuilding(HOUSE, 30, 30, 0, UnitSet.of(builders), QueueMode.REPLACE));
            h.step();
            CommandResult placed = h.results().get(h.results().size() - 1);
            assertThat(placed.outcome()).as("n=%d: %s", n, placed).isEqualTo(CommandOutcome.ACCEPTED);
            int house = s.buildings().handles()[1];
            long committed = -1;
            long finished = -1;
            for (int t = 0; t < 2_000 && finished < 0; t++) {
                h.step();
                if (committed < 0 && s.buildings().state(house) != BuildingStore.FOUNDATION) {
                    committed = h.tick();
                }
                if (s.buildings().state(house) == BuildingStore.COMPLETE) {
                    finished = h.tick();
                }
            }
            long expected = (long) Math.ceil(600 / DetMath.pow(n, Construction.EXPONENT));
            // the builders work in the tick that commits the site, so it counts
            assertThat(finished - committed + 1)
                    .as("n=%d: ceil(600 ÷ n^0.7) ticks of work from the commit", n)
                    .isEqualTo(expected);
            long work = Construction.work(n, n);
            assertThat(expected)
                    .as("n=%d: the whole millionths agree with the formula", n)
                    .isEqualTo((600 * Construction.MICRO + work - 1) / work);
            assertThat(s.buildings().progress(house)).isEqualTo(BuildingStore.FULL);
        }
    }

    @Test
    void aBuiltHouseHousesAndABuiltFieldRipens() {
        SimHarness h = match();
        MatchState s = KernelAccess.state(h.core());
        assertThat(s.population().cap(0)).as("the centre's 20").isEqualTo(20);
        int[] builders = buildersOnSlots(h, 3);
        h.submit(P0, new PlaceBuilding(HOUSE, 30, 30, 0, UnitSet.of(builders), QueueMode.REPLACE));
        h.runUntil(
                () -> s.buildings().handles().length > 1
                        && s.buildings().state(s.buildings().handles()[1]) == BuildingStore.COMPLETE,
                2_000);
        assertThat(s.population().cap(0))
                .as("built, not spawned, the house houses five")
                .isEqualTo(25);
        h.submit(
                P0,
                new PlaceBuilding(
                        new TemplateId("strategos:building/field"),
                        40,
                        10,
                        0,
                        UnitSet.of(builders),
                        QueueMode.REPLACE));
        h.runUntil(
                () -> s.buildings().handles().length > 2
                        && s.buildings().state(s.buildings().handles()[2]) == BuildingStore.COMPLETE,
                2_000);
        int field = s.buildings().handles()[2];
        assertThat(s.buildings().field(field))
                .as("built, the field is a crop that ripens")
                .isPositive();
        assertThat(s.nodes().ripening(s.buildings().field(field))).isPositive();
    }

    @Test
    void theWriterIsAskedForEveryBlockOnceInBuildOrder() {
        SimHarness h = match();
        MatchState s = KernelAccess.state(h.core());
        int[] builders = buildersOnSlots(h, 3);
        h.submit(P0, new PlaceBuilding(HOUSE, 30, 30, 0, UnitSet.of(builders), QueueMode.REPLACE));
        int seen = h.events(OutputEvent.class).size();
        h.runTicks(600);
        int house = s.buildings().handles()[1];
        assertThat(s.buildings().state(house)).isEqualTo(BuildingStore.COMPLETE);
        Blueprint b = s.blueprintOf(house);
        Footprint at = s.footprintOf(house);
        int baseY = s.buildings().baseY(house);
        List<String> expected = new ArrayList<>();
        for (int k = 0; k < b.count(); k++) {
            expected.add(at.worldX(b.x(k), b.z(k)) + "," + (baseY + b.y(k)) + "," + at.worldZ(b.x(k), b.z(k)) + " "
                    + b.palette().get(b.state(k)));
        }
        List<String> asked = h
                .events(OutputEvent.class)
                .subList(seen, h.events(OutputEvent.class).size())
                .stream()
                .filter(WorldEffect.class::isInstance)
                .map(WorldEffect.class::cast)
                .filter(w -> w.priority() == WritePriority.CONSTRUCTION && w.owner() == house)
                .filter(w -> !w.state().equals(Construction.FILL))
                .map(w -> w.x() + "," + w.y() + "," + w.z() + " " + w.state())
                .toList();
        assertThat(asked).containsExactlyElementsOf(expected);
        assertThat(s.buildingMaxHp(house)).isEqualTo(400);
        for (int u : builders) {
            assertThat(s.orders().size(dev.strategos.core.shared.Handle.index(u)))
                    .as("the builders' work is done")
                    .isZero();
        }
    }

    @Test
    void hitPointsRiseWithTheWork() {
        SimHarness h = match();
        MatchState s = KernelAccess.state(h.core());
        int[] builders = buildersOnSlots(h, 1);
        h.submit(P0, new PlaceBuilding(HOUSE, 30, 30, 0, UnitSet.of(builders), QueueMode.REPLACE));
        h.step();
        int house = s.buildings().handles()[1];
        assertThat(s.buildingMaxHp(house)).as("a foundation: a thousandth").isEqualTo(0.4);
        h.runTicks(301);
        assertThat(s.buildings().progress(house)).isBetween(495L, 505L);
        assertThat(s.buildingMaxHp(house)).isCloseTo(200, org.assertj.core.data.Offset.offset(2.0));
    }

    @Test
    void aMatchSavedMidBuildResumesExactly() {
        SimHarness h = match();
        int[] builders = buildersOnSlots(h, 4);
        h.submit(P0, new PlaceBuilding(HOUSE, 30, 30, 0, UnitSet.of(builders), QueueMode.REPLACE));
        h.runTicks(150);
        MatchSnapshot snapshot = h.core().snapshot();
        SimHarness restored = SimHarness.restore(
                snapshot, TemplateSet.builtin(), h.core().journal().segments().get(0)::slice);
        assertThat(restored.stateHash()).isEqualTo(h.stateHash());
        for (int k = 0; k < 300; k++) {
            h.step();
            restored.step();
            assertThat(restored.stateHash()).as("tick %d", h.tick()).isEqualTo(h.stateHash());
        }
        MatchState s = KernelAccess.state(restored.core());
        assertThat(s.buildings().state(s.buildings().handles()[1])).isEqualTo(BuildingStore.COMPLETE);
    }

    /** ECO-01.2 for the first paying command: a house its faction cannot pay for is refused with the wood it lacks. */
    @Test
    void anUnpayablePlacementNamesTheMissingWood() {
        SimHarness h = SimHarness.builder().size(64, 64).yBand(0, 31, 1).build();
        h.publishAll(VoxelFixtures.flat(64, 64, 32, 1));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 2, 2, 0));
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 50));
        h.step();
        h.submit(P0, new PlaceBuilding(HOUSE, 30, 30, 0, UnitSet.of(), QueueMode.REPLACE));
        h.step();
        CommandResult refused = h.results().get(h.results().size() - 1);
        assertThat(refused.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(refused.firstReason()).isEqualTo(dev.strategos.core.protocol.RejectReason.NOT_ENOUGH_RESOURCES);
        assertThat(refused.missing()).containsExactly(0L, 30_000L, 0L, 0L);
        MatchState s = KernelAccess.state(h.core());
        assertThat(s.buildings().handles()).as("no foundation").hasSize(1);
        assertThat(s.treasury().stock(0, 1)).as("nothing paid").isEqualTo(50_000);
    }
}
