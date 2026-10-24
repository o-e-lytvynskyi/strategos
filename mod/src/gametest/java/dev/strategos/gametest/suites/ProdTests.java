package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.protocol.SetRally;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchRecord;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.persist.MatchData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;

/** Training in a real level (ECO-06, PROD-01, PROD-03): housing, the door, rally points. */
public final class ProdTests {
    static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    static final PlayerSlot P0 = new PlayerSlot(0);

    private ProdTests() {}

    static BuildingView centre(MatchRuntime rt) {
        return core(rt).queries().buildings().get(0);
    }

    /** Faction 0's civic centre at (4, 4) and 1,000 food. */
    static void town(MatchRuntime rt) {
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 4, 4, 0));
        rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "food", 1_000));
    }

    /** ECO-06.4: with its twenty taken, a centre's worker waits, and the faction hears "housing needed" once. */
    @StrategosTest(batch = "production", timeoutTicks = 400)
    public static void aQueueWaitsWithHousingNeeded(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        town(rt);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(24, 24), 20, 3));
        List<Notice> notices = new ArrayList<>();
        rt.addListener(out -> out.events().stream()
                .filter(Notice.class::isInstance)
                .map(Notice.class::cast)
                .filter(n -> n.kind() == NoticeKind.HOUSING_NEEDED)
                .forEach(notices::add));
        h.startSequence()
                .thenWaitUntil(() -> check(
                        handles(rt).length == 20
                                && !core(rt).queries().buildings().isEmpty(),
                        "not ready"))
                .thenExecute(() -> rt.submit(P0, new Train(centre(rt).handle(), TemplateSet.WORKER)))
                .thenExecuteAfter(250, () -> {
                    check(notices.size() == 1, notices.size() + " housing notices");
                    check(
                            notices.get(0).handle() == centre(rt).handle(),
                            "the notice names " + notices.get(0).handle());
                    check(centre(rt).queue().size() == 1 && centre(rt).trained() == 0, "the entry " + centre(rt));
                    check(handles(rt).length == 20, "a worker was trained past the cap");
                    check(core(rt).queries().populationCap(new FactionSlot(0)) == 20, "the cap is not the centre's 20");
                })
                .thenSucceed();
    }

    /** PROD-01.4: a trained worker's body stands outside the centre by its door, on ground, not in a wall. */
    @StrategosTest(batch = "production", timeoutTicks = 400)
    public static void aUnitSpawnsAtTheDoorOnASurface(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        town(rt);
        h.startSequence()
                .thenWaitUntil(() -> check(!core(rt).queries().buildings().isEmpty(), "no centre yet"))
                .thenExecute(() -> rt.submit(P0, new Train(centre(rt).handle(), TemplateSet.WORKER)))
                .thenWaitUntil(() -> check(handles(rt).length == 1, "not trained yet"))
                .thenWaitUntil(() -> {
                    UnitEntity body = Units.body(rt, handles(rt)[0]);
                    BlockPos feet = body.blockPosition();
                    check(
                            h.getLevel()
                                    .getBlockState(feet)
                                    .getCollisionShape(h.getLevel(), feet)
                                    .isEmpty(),
                            "in a block at " + feet);
                    check(
                            h.getLevel()
                                    .getBlockState(feet.below())
                                    .isFaceSturdy(h.getLevel(), feet.below(), Direction.UP),
                            "not on ground at " + feet);
                    var at = centre(rt).footprint();
                    var u = unit(rt, handles(rt)[0]);
                    int x = (int) Math.floor(u.x());
                    int z = (int) Math.floor(u.z());
                    check(
                            x < at.anchorX()
                                    || x >= at.anchorX() + at.spanX()
                                    || z < at.anchorZ()
                                    || z >= at.anchorZ() + at.spanZ(),
                            "inside the centre at " + u.x() + ", " + u.z());
                })
                .thenSucceed();
    }

    /** PROD-03.2: a centre's rally point survives a save and a load, and the next worker walks to it. */
    @StrategosTest(batch = "production", timeoutTicks = 900)
    public static void aRallyPointSurvivesSave(GameTestHelper h) {
        MatchRegistry registry = StrategosServer.get().matches();
        MatchRuntime rt = MatchFixture.create(
                h, MatchFixture.options(SimHarness.DEFAULT_SEED, h).withPersistent(true));
        town(rt);
        int y = h.absolutePos(new BlockPos(0, 1, 0)).getY();
        var state = new Object() {
            MatchRuntime back = rt;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(!core(rt).queries().buildings().isEmpty(), "no centre yet"))
                .thenExecute(() ->
                        rt.submit(P0, new SetRally(UnitSet.of(centre(rt).handle()), 26, y, 26)))
                .thenWaitUntil(() -> check(centre(rt).rallyKind() == BuildingStore.RALLY_POINT, "no rally point yet"))
                .thenExecute(() -> {
                    MatchCore live = core(rt);
                    long hash = live.stateHash();
                    MatchData data = rt.data();
                    MatchRecord record = registry.record(rt.id());
                    check(data != null && record != null, "no blob or record");
                    CompoundTag tag = data.save(new CompoundTag(), h.getLevel().registryAccess());
                    registry.unload(rt.id());
                    state.back = registry.restore(h.getLevel(), record, MatchData.load(rt.id(), tag));
                    check(core(state.back).stateHash() == hash, "restored with another hash");
                    BuildingView c = centre(state.back);
                    check(
                            c.rallyKind() == BuildingStore.RALLY_POINT && c.rallyX() == 26.5 && c.rallyZ() == 26.5,
                            "the rally " + c);
                    registry.resume(state.back.id());
                    state.back.submit(P0, new Train(c.handle(), TemplateSet.WORKER));
                })
                .thenWaitUntil(() -> {
                    check(handles(state.back).length == 1, "not trained yet");
                    var u = unit(state.back, handles(state.back)[0]);
                    check(
                            Math.hypot(u.x() - 26.5, u.z() - 26.5) < 1.5,
                            "not at the rally point: " + u.x() + ", " + u.z());
                })
                .thenSucceed();
    }

    /** PROD-02.2: a batch of five workers steps out on one tick, and all five walk to the centre's rally point. */
    @StrategosTest(batch = "production", timeoutTicks = 1_600)
    public static void aBatchStepsOutTogetherAndRallies(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        town(rt);
        int y = h.absolutePos(new BlockPos(0, 1, 0)).getY();
        var state = new Object() {
            long firstSeen = -1;
        };
        h.onEachTick(() -> {
            if (state.firstSeen < 0 && handles(rt).length > 0) {
                state.firstSeen = h.getTick();
                check(handles(rt).length == 5, handles(rt).length + " of the batch stepped out first");
            }
        });
        h.startSequence()
                .thenWaitUntil(() -> check(!core(rt).queries().buildings().isEmpty(), "no centre yet"))
                .thenExecute(() -> {
                    rt.submit(P0, new SetRally(UnitSet.of(centre(rt).handle()), 24, y, 24));
                    rt.submit(
                            P0,
                            new dev.strategos.core.protocol.TrainBatch(
                                    centre(rt).handle(), TemplateSet.WORKER, 5));
                })
                .thenWaitUntil(() -> {
                    check(handles(rt).length == 5, "not trained yet");
                    for (int handle : handles(rt)) {
                        var u = unit(rt, handle);
                        check(
                                Math.hypot(u.x() - 24.5, u.z() - 24.5) < 3,
                                "not at the rally point: " + u.x() + ", " + u.z());
                    }
                })
                .thenSucceed();
    }
}
