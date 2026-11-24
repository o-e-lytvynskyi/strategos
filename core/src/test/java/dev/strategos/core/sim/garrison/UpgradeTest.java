package dev.strategos.core.sim.garrison;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.CancelTrain;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UpgradeBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.econ.Amount;
import dev.strategos.core.sim.prod.Techs;
import dev.strategos.core.sim.prod.Upgrades;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * BLD-12.2 in L1: a sentry tower upgrades to the defence tower in Town for the sheet's cost and time, and the building
 * stays itself: its health ratio, its garrison and its capture points' share; a Village faction is refused, a cancel
 * refunds, and a holder too small for the garrison lets the extra out.
 */
class UpgradeTest {
    static final TemplateId SENTRY = new TemplateId("strategos:building/sentry_tower");
    static final TemplateId TOWER = new TemplateId("strategos:building/tower");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final TechId TOWN = new TechId("strategos:phase_town");
    static final PlayerSlot P0 = new PlayerSlot(0);
    static final int WOOD = 1; // the resources' order: food, wood, stone, iron
    static final int STONE = 2;
    static final long UPGRADE_TICKS = 50 * 20;

    /** A match of three factions on flat ground, faction 0 holding a thousand wood and stone. */
    static final class Scene {
        final SimHarness h;
        final MatchState s;

        Scene(TemplateSet templates) {
            h = SimHarness.builder()
                    .templates(templates)
                    .size(96, 64)
                    .yBand(0, 31, 1)
                    .factions(3)
                    .build();
            h.publishAll(VoxelFixtures.flat(96, 64, 32, 1));
            h.step();
            s = KernelAccess.state(h.core());
            h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 1_000));
            h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "stone", 1_000));
            h.step();
        }

        int building(TemplateId template, int faction, int x, int z) {
            h.submit(PlayerSlot.ADMIN, new SpawnBuilding(template, new FactionSlot(faction), x, z, 0));
            h.step();
            int[] handles = s.buildings().handles();
            return handles[handles.length - 1];
        }

        /** {@code count} infantry of faction 0 walked into building {@code building}; their handles. */
        int[] garrison(int building, int count) {
            int[] door = Garrisons.door(s, building);
            h.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(
                            TemplateSet.INFANTRY,
                            new FactionSlot(0),
                            new Vec2d(door[0] + 0.5, door[1] + 4.5),
                            count,
                            1.5));
            int[] made = h.step().events().stream()
                    .filter(UnitCreated.class::isInstance)
                    .mapToInt(e -> ((UnitCreated) e).handle())
                    .toArray();
            h.submit(P0, new Garrison(UnitSet.of(made), building));
            h.step();
            assertThat(h.runUntil(() -> s.garrison().count(building) == count, 400))
                    .isTrue();
            return made;
        }

        void town() {
            s.progress().complete(0, TOWN);
            Techs.reapply(s, null);
        }

        CommandResult upgrade(PlayerSlot who, int building) {
            h.submit(who, new UpgradeBuilding(building));
            h.step();
            return h.results().get(h.results().size() - 1);
        }

        String templateOf(int building) {
            return s.templates().byIndex(s.buildings().template(building)).id().value();
        }

        double ratio(int building) {
            double max = s.buildingMaxHp(building);
            return (max - s.buildings().damage(building)) / max;
        }
    }

    @Test
    void aVillageFactionIsRefusedAndPaysNothing() {
        Scene t = new Scene(TemplateSet.builtin());
        int tower = t.building(SENTRY, 0, 40, 30);
        long wood = t.s.treasury().stock(0, WOOD);
        CommandResult village = t.upgrade(P0, tower);
        assertThat(village.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(village.firstReason()).isEqualTo(RejectReason.REQUIREMENTS);
        assertThat(t.s.treasury().stock(0, WOOD)).isEqualTo(wood);
        assertThat(t.s.buildings().queues().queueSize(tower)).isZero();
    }

    @Test
    void inTownItIsAcceptedPaidAndTheDefenceTowerAfterFiftySeconds() {
        Scene t = new Scene(TemplateSet.builtin());
        t.town();
        int tower = t.building(SENTRY, 0, 40, 30);
        t.s.buildings().setDamage(tower, t.s.buildingMaxHp(tower) * 0.25); // three quarters left
        int[] garrisoned = t.garrison(tower, 3);
        long wood = t.s.treasury().stock(0, WOOD);
        long stone = t.s.treasury().stock(0, STONE);
        assertThat(t.upgrade(P0, tower).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(t.s.treasury().stock(0, WOOD)).isEqualTo(wood - Amount.of(50));
        assertThat(t.s.treasury().stock(0, STONE)).isEqualTo(stone - Amount.of(100));
        assertThat(t.s.buildings().queues().queueSize(tower)).isEqualTo(1);
        assertThat(t.upgrade(P0, tower).firstReason()).as("one at a time").isEqualTo(RejectReason.LIMIT);
        t.h.runUntil(() -> false, (int) UPGRADE_TICKS - 20);
        assertThat(t.templateOf(tower)).as("not yet").isEqualTo(SENTRY.value());
        assertThat(t.h.runUntil(() -> t.templateOf(tower).equals(TOWER.value()), 60))
                .isTrue();
        assertThat(t.s.buildings().queues().queueSize(tower)).isZero();
        assertThat(t.ratio(tower))
                .as("the health ratio is kept")
                .isCloseTo(0.75, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(t.s.buildingMaxHp(tower))
                .as("the defence tower's, with Town's")
                .isGreaterThan(1000);
        assertThat(t.s.garrison().count(tower)).as("the three stay").isEqualTo(3);
        for (int unit : garrisoned) {
            assertThat(t.s.garrison().inside(Handle.index(unit))).isTrue();
        }
        assertThat(Garrisons.capacity(t.s, tower)).isEqualTo(5);
    }

    @Test
    void aCancelRefundsTheWholeUpgrade() {
        Scene t = new Scene(TemplateSet.builtin());
        t.town();
        int tower = t.building(SENTRY, 0, 40, 30);
        long wood = t.s.treasury().stock(0, WOOD);
        long stone = t.s.treasury().stock(0, STONE);
        t.upgrade(P0, tower);
        t.h.step();
        t.h.submit(P0, new CancelTrain(tower, 0));
        t.h.step();
        assertThat(t.s.buildings().queues().queueSize(tower)).isZero();
        assertThat(t.s.treasury().stock(0, WOOD)).isEqualTo(wood);
        assertThat(t.s.treasury().stock(0, STONE)).isEqualTo(stone);
        assertThat(t.templateOf(tower)).isEqualTo(SENTRY.value());
    }

    @Test
    void whatIsNotAnUpgradeableOwnFinishedBuildingIsRefused() {
        Scene t = new Scene(TemplateSet.builtin());
        t.town();
        int house = t.building(HOUSE, 0, 20, 20);
        assertThat(t.upgrade(P0, house).firstReason()).isEqualTo(RejectReason.UNSUPPORTED);
        int tower = t.building(SENTRY, 0, 40, 30);
        assertThat(t.upgrade(new PlayerSlot(1), tower).firstReason()).isEqualTo(RejectReason.NOT_OWNER);
        int poor = t.building(SENTRY, 1, 60, 30);
        t.s.progress().complete(1, TOWN);
        Techs.reapply(t.s, null);
        assertThat(t.upgrade(new PlayerSlot(1), poor).outcome())
                .as("the second faction has no stock")
                .isEqualTo(CommandOutcome.REJECTED);
        assertThat(t.s.buildings().queues().queueSize(poor)).isZero();
    }

    @Test
    void theCapturePointsKeepTheirShareOfTheNewTotal() {
        Scene t = new Scene(TemplateSet.builtin());
        t.town();
        int tower = t.building(SENTRY, 0, 40, 30);
        // owner 0 holds half of the sentry tower's 400 points, faction 1 the other half
        t.s.capture().restore(List.of((long) tower, 0L, 200_000L, 200_000L, 0L));
        // the upgrade's end itself, not a wait the owner's regeneration would win
        Upgrades.apply(
                t.s, tower, Objects.requireNonNull(t.s.templates().byId(TOWER)).netIndex());
        assertThat(t.templateOf(tower)).isEqualTo(TOWER.value());
        long[] points = Objects.requireNonNull(t.s.capture().points(tower));
        assertThat(points[0] + points[1]).as("the defence tower's 800 points").isEqualTo(800_000L);
        assertThat(points[0]).isEqualTo(points[1]);
    }

    /** The built-in set with the defence tower's holder cut to {@code capacity}, so an upgrade leaves some outside. */
    static TemplateSet smallHolder(int capacity) {
        TemplateSet b = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : b.all()) {
            JsonObject json = Objects.requireNonNull(b.flattened(t.id())).deepCopy();
            if (t.id().equals(TOWER)) {
                json.getAsJsonObject("GarrisonHolder").addProperty("capacity", capacity);
            }
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), json));
        }
        return TemplateCompiler.fromFlattened(
                        flats,
                        b.techs().stream().map(TemplateCompiler.TechFlat::of).toList())
                .set();
    }

    @Test
    void occupantsOverTheNewCapacityComeOutTheRestStay() {
        Scene small = new Scene(smallHolder(2));
        small.town();
        int tower = small.building(SENTRY, 0, 40, 30);
        int[] garrisoned = small.garrison(tower, 3);
        small.upgrade(P0, tower);
        assertThat(small.h.runUntil(() -> small.templateOf(tower).equals(TOWER.value()), (int) UPGRADE_TICKS + 40))
                .isTrue();
        assertThat(small.s.garrison().count(tower)).isEqualTo(2);
        int out = 0;
        for (int unit : garrisoned) {
            if (!small.s.garrison().inside(Handle.index(unit))) {
                out++;
            }
        }
        assertThat(out).isEqualTo(1);
    }
}
