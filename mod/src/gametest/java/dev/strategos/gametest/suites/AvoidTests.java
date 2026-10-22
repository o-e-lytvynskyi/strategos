package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.move;
import static dev.strategos.gametest.suites.Units.spawn;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.Agent;
import dev.strategos.core.sim.AgentUpdate;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.journal.Journal;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Local avoidance on a real server (PATH-06, spec §9.2 T21): two groups head-on through a gap, a unit walking round a
 * first-person player, and the match's own member, whose avatar is an observer, passed through (D-08).
 */
public final class AvoidTests {
    private static final int COLUMNS = 10;
    private static final int ROWS = 5;
    private static final int UNITS = 2 * COLUMNS * ROWS;
    private static final double SPACING = 1.5;
    private static final double SHIFT = 41.5;
    private static final double CLOSE = 0.4;
    private static final int CLOSE_TICKS = 5;

    /** A unit's radius (the debug walker's) and a player's, as the agent tracker gives it. */
    private static final double CLEARANCE = 0.35 + 0.3;

    private AvoidTests() {}

    /**
     * PATH-06.1–2, the head_on_gap golden's ground in the world: on a 64 x 32 floor laid by the test, a wall two
     * blocks thick and three high across x 31..32 but for a gap at z 13..18. Fifty walkers of faction 0 in ten columns
     * and five rows 1.5 apart from (4.5, 12.5) and fifty of faction 1 mirrored east are ordered 41.5 blocks across the
     * wall, keeping their formations. Both groups arrive, every unit idle within a block of its target, and no two
     * units stand closer than 0.4 block for more than 5 ticks in a row.
     */
    @StrategosTest(arena = "arena_air_96", timeoutTicks = 2_400)
    public static void headOnGroupsThroughGap(GameTestHelper h) {
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 32; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE); // relative y 1 is the floor layer
                boolean gap = z >= 13 && z <= 18;
                if ((x == 31 || x == 32) && !gap) {
                    for (int y = 2; y <= 4; y++) {
                        h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    }
                }
            }
        }
        MatchRuntime rt = MatchFixture.create(h, 0, 0, 64, 32, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        for (int c = 0; c < COLUMNS; c++) {
            for (int r = 0; r < ROWS; r++) {
                double x = 4.5 + SPACING * c;
                double z = 12.5 + SPACING * r;
                rt.submit(
                        PlayerSlot.ADMIN,
                        new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(x, z), 1, 0));
                rt.submit(
                        PlayerSlot.ADMIN,
                        new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(64 - x, z), 1, 0));
            }
        }
        var state = new Object() {
            int[] units = new int[0];
            double[] targetX = new double[0];
            double[] targetZ = new double[0];
            int[] streak = new int[UNITS * UNITS];
            int longest;
        };
        h.onEachTick(() -> {
            if (state.units.length == 0) {
                int[] all = handles(rt);
                if (all.length < UNITS) {
                    return;
                }
                state.units = all;
                state.targetX = new double[UNITS];
                state.targetZ = new double[UNITS];
                for (int i = 0; i < UNITS; i++) {
                    UnitView u = unit(rt, all[i]);
                    state.targetX[i] = u.x() < 32 ? u.x() + SHIFT : u.x() - SHIFT;
                    state.targetZ[i] = u.z();
                    move(rt, all[i], state.targetX[i], state.targetZ[i]);
                }
                return;
            }
            double[] xs = new double[UNITS];
            double[] zs = new double[UNITS];
            boolean done = true;
            for (int i = 0; i < UNITS; i++) {
                UnitView u = unit(rt, state.units[i]);
                xs[i] = u.x();
                zs[i] = u.z();
                done &= u.state() == UnitState.IDLE
                        && Math.hypot(u.x() - state.targetX[i], u.z() - state.targetZ[i]) <= 1.0;
            }
            for (int i = 0; i < UNITS; i++) {
                for (int j = i + 1; j < UNITS; j++) {
                    int k = i * UNITS + j;
                    state.streak[k] = Math.hypot(xs[i] - xs[j], zs[i] - zs[j]) < CLOSE ? state.streak[k] + 1 : 0;
                    state.longest = Math.max(state.longest, state.streak[k]);
                }
            }
            check(
                    state.longest <= CLOSE_TICKS,
                    "two units stayed closer than " + CLOSE + " for " + state.longest + " ticks");
            if (done) {
                h.succeed();
            }
        });
    }

    /**
     * PATH-06.3, S10's avoidance check (ADR-0011): a first-person mock player, in no match, stands on the straight line
     * of a walker's route. The agent tracker makes it the tick's agent, journaled with its entity id; the walker goes
     * round it, never nearer than their radii, and arrives.
     */
    @StrategosTest(timeoutTicks = 400)
    public static void walkAroundMockPlayer(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 4.5, 16.5, 1, 0);
        Vec3 at = h.absoluteVec(new Vec3(16.5, 2, 16.5));
        MockViewer player = MockViewer.join(h.getLevel(), "strategos-in-way", at.x, at.y, at.z, 2);
        var state = new Object() {
            boolean ordered;
            double closest = Double.POSITIVE_INFINITY;
        };
        h.onEachTick(() -> {
            player.poll();
            int[] units = handles(rt);
            if (units.length != 1) {
                return;
            }
            if (!state.ordered) {
                state.ordered = true;
                move(rt, units[0], 28.5, 16.5);
                return;
            }
            UnitView u = unit(rt, units[0]);
            double px = rt.space().localX(player.player().getX());
            double pz = rt.space().localZ(player.player().getZ());
            state.closest = Math.min(state.closest, Math.hypot(u.x() - px, u.z() - pz));
            if (u.state() == UnitState.IDLE && u.x() == 28.5 && u.z() == 16.5) {
                check(state.closest >= CLEARANCE - 0.01, "the walker came within " + state.closest + " of the player");
                check(journaledAgent(rt, player.player().getId()), "no agent update named the player");
                player.close();
                h.succeed();
            }
        });
    }

    /**
     * PATH-06 and D-08: a member of the match standing on a walker's route is its observer, which units pass through:
     * no agent update is journaled, and the walker keeps its straight line through the member's point.
     */
    @StrategosTest(timeoutTicks = 400)
    public static void memberAvatarPassedThrough(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 4.5, 16.5, 1, 0);
        Vec3 at = h.absoluteVec(new Vec3(16.5, 2, 16.5));
        MockViewer member = MockViewer.join(h.getLevel(), "strategos-member", at.x, at.y, at.z, 2);
        StrategosServer.get().matches().join(rt, member.player().getUUID(), new PlayerSlot(0));
        var state = new Object() {
            boolean ordered;
            double closest = Double.POSITIVE_INFINITY;
        };
        h.onEachTick(() -> {
            member.poll();
            int[] units = handles(rt);
            if (units.length != 1) {
                return;
            }
            if (!state.ordered) {
                state.ordered = true;
                move(rt, units[0], 28.5, 16.5);
                return;
            }
            UnitView u = unit(rt, units[0]);
            check(u.z() == 16.5, "the walker left its line for the member, to z " + u.z());
            double px = rt.space().localX(member.player().getX());
            state.closest = Math.min(state.closest, Math.abs(u.x() - px));
            if (u.state() == UnitState.IDLE && u.x() == 28.5) {
                check(state.closest < 0.25, "the walker never passed the member's point");
                check(!journaledAgent(rt, member.player().getId()), "the member was journaled as an agent");
                member.close();
                h.succeed();
            }
        });
    }

    private static boolean journaledAgent(MatchRuntime rt, int entityId) {
        for (Journal.Segment segment : core(rt).journal().segments()) {
            for (TickInputs tick : segment.nonEmptyTicks()) {
                for (InputEvent event : tick.events()) {
                    if (event instanceof AgentUpdate update) {
                        for (Agent agent : update.agents()) {
                            if (agent.id() == entityId) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}
