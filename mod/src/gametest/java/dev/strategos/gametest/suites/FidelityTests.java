package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.move;
import static dev.strategos.gametest.suites.Units.onlyUnit;
import static dev.strategos.gametest.suites.Units.spawn;
import static dev.strategos.gametest.suites.Units.unit;

import com.mojang.authlib.GameProfile;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.journal.Journal;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spike S10 (UNIT-09, spec §8.2): how kinematic bodies sit on the ground, and vanilla damage reaching the core.
 * The floor error is measured against where vanilla physics would rest the same body: its bounding box, dropped from
 * above, stops on the collision shapes under it.
 */
public final class FidelityTests {
    private static final Logger LOG = LoggerFactory.getLogger(FidelityTests.class);
    private static final double SIXTEENTH = 1 / 16.0;
    /** The surface strips run from x 10 to 29; inside 11..29 a body's box lies over the strip alone. */
    private static final int STRIP_FROM = 10;

    private static final int STRIP_TO = 29;
    /** The drop lane's platform ends at x 19; from x 23 the body has landed. */
    private static final int LANDED_FROM = 23;

    /** The lanes, each by its centre row; a lane spans the rows either side too, and its walls the next ones. */
    private enum Lane {
        SLAB(3),
        STAIRS(9),
        SNOW(15),
        CARPET(21),
        PATH(27),
        FARMLAND(33),
        SHALLOW_WATER(39),
        DROP(45);

        private final int z;

        Lane(int z) {
            this.z = z;
        }

        int z() {
            return z;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private FidelityTests() {}

    /**
     * UNIT-09.1: eight lanes on a stone bed, walled in five high so no path goes round a strip, one unit walking each
     * from x 2 to 44 over its strip: bottom slabs, stairs walked along their row, three layers of snow, carpet, a dirt
     * path, moist farmland, a pool one block deep, and a platform three blocks high to drop from. Wherever a body's box lies over a strip alone and the unit is not halfway
     * up a step, its Y is within 1/16 block of where vanilla physics would rest it; the drop falls at most half a block
     * a tick.
     */
    @StrategosTest(arena = "arena_flat_48_high", timeoutTicks = 900)
    public static void poseTraces(GameTestHelper h) {
        buildLanes(h);
        MatchRuntime rt = MatchFixture.create(
                h, MatchOptions.gameTest(SimHarness.DEFAULT_SEED, Math.floor(h.getBounds().minY) + 2));
        for (Lane lane : Lane.values()) {
            spawn(rt, 2.5, lane.z() + 0.5, 1, 0);
        }
        Lane[] lanes = Lane.values();
        var state = new Object() {
            final int[] handles = new int[lanes.length];
            boolean ordered;
            final double[] worst = new double[lanes.length];
            final int[] samples = new int[lanes.length];
            double lastDropY = Double.NaN;
            double steepest;
            int arrived;
        };
        h.onEachTick(() -> {
            if (core(rt).queries().unitCount() != lanes.length) {
                return;
            }
            if (!state.ordered) {
                for (int handle : core(rt).queries().unitHandles()) {
                    UnitView u = unit(rt, handle);
                    int k = (int) Math.floor(u.z()) / 6;
                    state.handles[k] = handle;
                    move(rt, handle, 44.5, lanes[k].z() + 0.5);
                }
                state.ordered = true;
                return;
            }
            state.arrived = 0;
            for (int k = 0; k < lanes.length; k++) {
                UnitView u = unit(rt, state.handles[k]);
                if (u.state() == UnitState.IDLE && u.target() == null) {
                    state.arrived++;
                }
                UnitEntity body = rt.bodies().get(state.handles[k]);
                MotionStore.Extra motion = core(rt).queries().motion(state.handles[k]);
                if (body == null || motion == null) {
                    continue;
                }
                double x = body.getX() - h.absoluteVec(Vec3.ZERO).x;
                if (lanes[k] == Lane.DROP) {
                    if (!Double.isNaN(state.lastDropY)) {
                        state.steepest = Math.max(state.steepest, state.lastDropY - body.getY());
                    }
                    state.lastDropY = body.getY();
                }
                int from = lanes[k] == Lane.DROP ? LANDED_FROM : STRIP_FROM + 1;
                if (x >= from && x <= STRIP_TO && !motion.rising()) {
                    double error = Math.abs(body.getY() - restY(body));
                    state.worst[k] = Math.max(state.worst[k], error);
                    state.samples[k]++;
                }
            }
            if (state.arrived == lanes.length) {
                StringBuilder line = new StringBuilder("pose_traces:");
                for (int k = 0; k < lanes.length; k++) {
                    line.append(String.format(
                            Locale.ROOT, " %s %.4f over %d ticks;", lanes[k].id(), state.worst[k], state.samples[k]));
                }
                line.append(String.format(Locale.ROOT, " the drop fell at most %.4f a tick", state.steepest));
                LOG.info(line.toString());
                for (int k = 0; k < lanes.length; k++) {
                    check(state.samples[k] >= 20, lanes[k].id() + ": only " + state.samples[k] + " ticks measured");
                    check(
                            state.worst[k] <= SIXTEENTH,
                            lanes[k].id() + ": the body stood " + state.worst[k] + " off the floor");
                }
                check(
                        state.steepest > 0.4 && state.steepest <= 0.5 + 1e-9,
                        "the drop fell " + state.steepest + " a tick");
                h.succeed();
            }
        });
    }

    /**
     * UNIT-09.2: a player's blow and an arrow the player shot reach a unit's body; each arrives in the core as a
     * journaled {@link ExternalDamage} input, hack and pierce from a player, and the body takes no vanilla damage.
     */
    @StrategosTest(timeoutTicks = 300)
    public static void playerMeleeAndArrowJournaled(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        spawn(rt, 16.5, 16.5, 1, 0);
        var state = new Object() {
            boolean struck;
        };
        h.onEachTick(() -> {
            if (core(rt).queries().unitCount() != 1) {
                return;
            }
            int handle = onlyUnit(rt);
            UnitEntity body = rt.bodies().get(handle);
            if (body == null) {
                return;
            }
            if (!state.struck) {
                state.struck = true;
                FakePlayer player = FakePlayerFactory.get(
                        h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(new byte[] {10}), "strategos-s10"));
                player.moveTo(body.getX() - 1.5, body.getY(), body.getZ(), -90, 0);
                player.attack(body);
                Arrow arrow = new Arrow(h.getLevel(), player, new ItemStack(Items.ARROW), null);
                arrow.setPos(body.getX() - 4, body.getY() + 0.9, body.getZ());
                arrow.shoot(1, 0, 0, 2.5f, 0);
                h.getLevel().addFreshEntity(arrow);
                return;
            }
            List<ExternalDamage> hits = journaled(core(rt).journal(), handle);
            boolean hacked = hits.stream()
                    .anyMatch(d -> d.kind() == ExternalDamage.Kind.HACK
                            && d.sourceKind() == ExternalDamage.SourceKind.PLAYER
                            && d.amount() > 0);
            boolean pierced = hits.stream()
                    .anyMatch(d -> d.kind() == ExternalDamage.Kind.PIERCE
                            && d.sourceKind() == ExternalDamage.SourceKind.PLAYER
                            && d.amount() > 0);
            if (hacked && pierced) {
                check(hits.size() == 2, "journaled " + hits);
                check(body.getHealth() == body.getMaxHealth() && body.isAlive(), "the body took vanilla damage");
                LOG.info("player_melee_and_arrow_journaled: {}", hits);
                h.succeed();
            }
        });
    }

    /** Where vanilla physics would rest this body: its box, lifted 1.5 blocks, dropped onto the collision shapes. */
    private static double restY(UnitEntity body) {
        AABB lifted = body.getBoundingBox().move(0, 1.5, 0);
        Vec3 fall = Entity.collideBoundingBox(null, new Vec3(0, -3, 0), lifted, body.level(), List.of());
        return body.getY() + 1.5 + fall.y;
    }

    private static List<ExternalDamage> journaled(Journal journal, int handle) {
        List<ExternalDamage> hits = new ArrayList<>();
        for (Journal.Segment segment : journal.segments()) {
            for (TickInputs tick : segment.nonEmptyTicks()) {
                for (InputEvent event : tick.events()) {
                    if (event instanceof ExternalDamage d && d.handle() == handle) {
                        hits.add(d);
                    }
                }
            }
        }
        return hits;
    }

    /** A stone bed over the arena's floor (relative y 2), each lane's walls, and its strip or platform on the bed. */
    private static void buildLanes(GameTestHelper h) {
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
        }
        BlockState stairs = Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        BlockState snow = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3);
        BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE);
        for (Lane lane : Lane.values()) {
            for (int x = 0; x < 48; x++) {
                for (int y = 3; y <= 7; y++) {
                    h.setBlock(new BlockPos(x, y, lane.z() - 2), Blocks.STONE);
                    h.setBlock(new BlockPos(x, y, lane.z() + 2), Blocks.STONE);
                }
            }
            for (int z = lane.z() - 1; z <= lane.z() + 1; z++) {
                for (int x = STRIP_FROM; x <= STRIP_TO; x++) {
                    BlockPos on = new BlockPos(x, 3, z);
                    switch (lane) {
                        case SLAB -> h.setBlock(on, Blocks.SMOOTH_STONE_SLAB);
                        case STAIRS -> h.setBlock(on, stairs);
                        case SNOW -> h.setBlock(on, snow);
                        case CARPET -> h.setBlock(on, Blocks.WHITE_CARPET);
                        case PATH -> h.setBlock(on, Blocks.DIRT_PATH);
                        case FARMLAND -> h.setBlock(on, farmland);
                        case SHALLOW_WATER -> h.setBlock(new BlockPos(x, 2, z), Blocks.WATER);
                        case DROP -> {
                            if (x < 20) {
                                for (int y = 3; y <= 5; y++) {
                                    h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                                }
                            }
                        }
                    }
                }
                if (lane == Lane.DROP) { // the way up: a block, then two, below the platform's three
                    h.setBlock(new BlockPos(8, 3, z), Blocks.STONE);
                    h.setBlock(new BlockPos(9, 3, z), Blocks.STONE);
                    h.setBlock(new BlockPos(9, 4, z), Blocks.STONE);
                }
            }
        }
    }
}
