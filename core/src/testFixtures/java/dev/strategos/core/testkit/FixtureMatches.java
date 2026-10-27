package dev.strategos.core.testkit;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitView;
import java.util.ArrayList;
import java.util.List;

/**
 * The matches the released-schema save fixtures are made of (QA-17.5), the same for the core's JSON and the mod's NBT:
 * a fixture of a schema pins every field that schema's save may hold, so later builds can be checked against it.
 */
public final class FixtureMatches {
    /** The tick schema 2's fixture is saved at. */
    public static final long SCHEMA_2_TICK = 400;

    public static final long SCHEMA_3_TICK = 9;

    private FixtureMatches() {}

    /**
     * Schema 2's fixture (M1 T27, with M2's fields so far): a 64 × 32 match on flat terrain, six spearmen a side twelve
     * blocks apart attack-moving into each other and a walker with a move and one queued behind it, stepped to tick
     * {@value #SCHEMA_2_TICK}. Its save holds terrain pages, a queue's rest, attacks with their targets and damage
     * taken.
     */
    public static SimHarness schema2() {
        SimHarness h = SimHarness.builder().size(64, 32).build();
        h.publishAll(VoxelFixtures.flat(64, 32, 16, 1));
        FactionSlot west = new FactionSlot(0);
        FactionSlot east = new FactionSlot(1);
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, west, new Vec2d(20, 16), 6, 2));
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, east, new Vec2d(32, 16), 6, 2));
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, west, new Vec2d(6, 6), 1, 0));
        h.step();
        List<Integer> spearmen = new ArrayList<>();
        int walker = 0;
        for (int unit : h.queries().unitHandlesOf(west)) {
            UnitView view = h.queries().unit(unit);
            if (view != null && view.template().equals(TemplateSet.INFANTRY)) {
                spearmen.add(unit);
            } else {
                walker = unit;
            }
        }
        h.submit(
                new PlayerSlot(0),
                AttackMove.replace(
                        UnitSet.of(spearmen.stream().mapToInt(Integer::intValue).toArray()), new Vec2d(32, 16)));
        h.submit(new PlayerSlot(1), AttackMove.replace(UnitSet.of(h.queries().unitHandlesOf(east)), new Vec2d(20, 16)));
        h.submit(new PlayerSlot(0), Move.replace(UnitSet.of(walker), new Vec2d(6, 26)));
        h.submit(new PlayerSlot(0), new Move(UnitSet.of(walker), new Vec2d(14, 26), QueueMode.QUEUE, 0));
        while (h.tick() < SCHEMA_2_TICK) {
            h.step();
        }
        return h;
    }
    /** Schema 3 pins building identities, queued products and training progress alongside stable bindings. */
    public static SimHarness schema3() {
        SimHarness h = SimHarness.builder().size(64, 64).yBand(0, 31, 1).build();
        h.publishAll(VoxelFixtures.flat(64, 64, 32, 1));
        var centre = new dev.strategos.core.shared.TemplateId("strategos:building/civic_centre");
        h.submit(PlayerSlot.ADMIN, new dev.strategos.core.protocol.SpawnBuilding(centre, new FactionSlot(0), 2, 2, 0));
        h.submit(
                PlayerSlot.ADMIN, new dev.strategos.core.protocol.SpawnBuilding(centre, new FactionSlot(1), 48, 48, 0));
        h.submit(PlayerSlot.ADMIN, new dev.strategos.core.protocol.Grant(new FactionSlot(0), "food", 1_000));
        h.step();
        int building = h.queries().buildings().getFirst().handle();
        h.submit(new PlayerSlot(0), new dev.strategos.core.protocol.Train(building, TemplateSet.WORKER));
        h.submit(new PlayerSlot(0), new dev.strategos.core.protocol.Train(building, TemplateSet.WORKER));
        while (h.tick() < SCHEMA_3_TICK) {
            h.step();
        }
        return h;
    }
}
