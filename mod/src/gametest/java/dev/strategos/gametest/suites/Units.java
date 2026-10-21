package dev.strategos.gametest.suites;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.entity.UnitSpawnData;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityTypeTest;

/**
 * Shared steps of the match and body GameTests. Every check throws {@link GameTestAssertException}: inside a
 * sequence's {@code thenWaitUntil} that means "not yet", inside {@code thenExecute} it fails the test.
 */
final class Units {
    static final FactionSlot FACTION = new FactionSlot(0);

    private Units() {}

    /** A match over the whole arena with the test kit's default seed. */
    static MatchRuntime match(GameTestHelper h) {
        return MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
    }

    /** An ADMIN spawn of debug walkers of faction 0 around match-local (x, z); it applies at the next step. */
    static void spawn(MatchRuntime rt, double x, double z, int count, double spread) {
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION, new Vec2d(x, z), count, spread));
    }

    /** An ADMIN Move of one unit to match-local (x, z); it applies at the next step. */
    static void move(MatchRuntime rt, int handle, double x, double z) {
        rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(handle), new Vec2d(x, z)));
    }

    static MatchCore core(MatchRuntime rt) {
        MatchCore core = rt.core();
        check(core != null, "match " + rt.id() + " has no core");
        return core;
    }

    /** The match's live unit handles, ascending. */
    static int[] handles(MatchRuntime rt) {
        return core(rt).queries().unitHandles();
    }

    /** The match's only unit, once it exists. */
    static int onlyUnit(MatchRuntime rt) {
        int[] handles = handles(rt);
        check(handles.length == 1, "match " + rt.id() + " has " + handles.length + " units, not 1");
        return handles[0];
    }

    static UnitView unit(MatchRuntime rt, int handle) {
        UnitView unit = core(rt).queries().unit(handle);
        check(unit != null, "unit " + Handle.toString(handle) + " is gone");
        return unit;
    }

    /** The registry's body of the unit; "not yet" without one. */
    static UnitEntity body(MatchRuntime rt, int handle) {
        UnitEntity body = rt.bodies().get(handle);
        check(body != null, "unit " + Handle.toString(handle) + " has no body");
        return body;
    }

    /** Every unit body in the level that shows a unit of this match. */
    static List<UnitEntity> bodiesOf(ServerLevel level, MatchRuntime rt) {
        List<UnitEntity> found = new ArrayList<>();
        for (UnitEntity body : level.getEntities(EntityTypeTest.forClass(UnitEntity.class), e -> true)) {
            UnitSpawnData data = body.spawnData();
            if (data != null && data.matchId() == rt.id().value()) {
                found.add(body);
            }
        }
        return found;
    }

    /** The bodies in the level that show this one unit. */
    static List<UnitEntity> bodiesOf(ServerLevel level, MatchRuntime rt, int handle) {
        List<UnitEntity> found = new ArrayList<>();
        for (UnitEntity body : bodiesOf(level, rt)) {
            UnitSpawnData data = body.spawnData();
            if (data != null && data.handle() == handle) {
                found.add(body);
            }
        }
        return found;
    }

    /** The body stands where the core puts its unit, in world coordinates, within {@code tolerance} on each axis. */
    static void assertOnCorePose(MatchRuntime rt, UnitEntity body, int handle, double tolerance) {
        UnitView unit = unit(rt, handle);
        double x = rt.space().worldX(unit.x());
        double z = rt.space().worldZ(unit.z());
        check(
                Math.abs(body.getX() - x) <= tolerance
                        && Math.abs(body.getY() - unit.y()) <= tolerance
                        && Math.abs(body.getZ() - z) <= tolerance,
                "the body of unit " + Handle.toString(handle) + " stands at (" + body.getX() + ", " + body.getY() + ", "
                        + body.getZ() + "), the core puts it at (" + x + ", " + unit.y() + ", " + z + ")");
    }

    static void check(boolean ok, String failure) {
        if (!ok) {
            throw new GameTestAssertException(failure);
        }
    }
}
