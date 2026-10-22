package dev.strategos.devtools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * S10 (UNIT-09.3, spec §7.7): one unit walks a row of patches on the flat world's grass, each two blocks long and three
 * wide with grass between: bottom slabs, stairs walked along their row, three layers of snow, carpet, a dirt path,
 * farmland and a pool a block deep. The camera stands fixed to the south. From the order to the arrival, a PNG every 2
 * ticks, and for each the body's client-side Y against where vanilla physics would rest it ({@code frames} in
 * result.json): "settled" frames are those whose body box lies over one patch or one stretch of grass.
 */
final class S10StepsScenario implements Scenario {
    private static final int SIZE = 48;
    /** The row's west end, from the anchor. */
    private static final int WEST = -15;
    /** Each patch's first block from the row's west end. */
    private static final int[] PATCHES = {2, 6, 10, 14, 18, 22, 26};

    private static final String[] NAMES = {"slab", "stairs", "snow", "carpet", "path", "farmland", "pool"};
    private static final int LENGTH = 31;
    private static final int LOAD_MIN = 60;
    private static final int SETTLE = 20;
    private static final int MAX_FRAMES = 90;

    private enum Stage {
        LOADING,
        SETTLING,
        WALKING,
        DONE
    }

    private volatile @Nullable MatchRuntime match;
    private @Nullable IntegratedServer server;
    private BlockPos anchor = BlockPos.ZERO;
    private Stage stage = Stage.LOADING;
    private int loading;
    private int settled;
    private int walked;
    private int still;
    private double lastX = Double.NaN;
    private final JsonArray frames = new JsonArray();
    private double settledWorst;
    private double edgeWorst;
    private int settledFrames;

    @Override
    public String name() {
        return "s10_steps";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        this.server = server;
        anchor = ScenarioWorld.anchor(server);
        ServerLevel level = ScenarioWorld.player(server).serverLevel();
        buildRow(level);
        double cx = anchor.getX() + 0.5;
        double cy = anchor.getY() + 1.5;
        double cz = anchor.getZ() + 14.5;
        ScenarioWorld.camera(server, cx, cy, cz, 180, 8);
        ctx.holdCamera(cx, cy, cz, 180, 8);
        MatchRuntime m = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, anchor, SIZE, SIZE),
                        MatchOptions.generated(anchor.getY())
                                .withPersistent(false)
                                .withSeed(ScenarioWorld.SEED));
        m.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(
                        TemplateSet.DEBUG_WALKER,
                        new FactionSlot(0),
                        new Vec2d(localX(m, WEST - 2 + 0.5), localZ(m, 0.5)),
                        1,
                        0));
        match = m;
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        UnitEntity body = body(mc);
        switch (stage) {
            case LOADING -> {
                if (++loading >= LOAD_MIN && mc.levelRenderer.hasRenderedAllSections() && body != null) {
                    stage = Stage.SETTLING;
                }
            }
            case SETTLING -> {
                if (++settled >= SETTLE) {
                    MatchRuntime m = match;
                    IntegratedServer s = server;
                    if (m != null && s != null) {
                        s.execute(() -> order(m));
                    }
                    stage = Stage.WALKING;
                }
            }
            case WALKING -> {
                walked++;
                if (body != null && walked % 2 == 0) {
                    record(ctx, body);
                }
                if (body != null) {
                    still = body.getX() == lastX ? still + 1 : 0;
                    lastX = body.getX();
                }
                boolean arrived = body != null && still >= 10 && body.getX() - rowWest() > LENGTH - 1;
                if (arrived || frames.size() >= MAX_FRAMES) {
                    ctx.result.add("frames", frames);
                    ctx.result.addProperty("settledFrames", settledFrames);
                    ctx.result.addProperty("settledWorstError", settledWorst);
                    ctx.result.addProperty("edgeWorstError", edgeWorst);
                    ctx.result.addProperty("arrived", arrived);
                    ctx.result.addProperty("ok", arrived);
                    stage = Stage.DONE;
                    return true;
                }
            }
            case DONE -> {
                return true;
            }
        }
        return false;
    }

    /** A frame, and where the body stands in it against where vanilla physics would rest it. */
    private void record(ScenarioContext ctx, UnitEntity body) {
        String name = String.format(Locale.ROOT, "s10-%03d", walked);
        ctx.capture(name);
        double x = body.getX() - rowWest();
        double y = body.getY() - anchor.getY();
        double rest = restY(body) - anchor.getY();
        double error = Math.abs(y - rest);
        boolean flat = patchAt(x - 0.3) == patchAt(x + 0.3 - 1e-9);
        if (flat) {
            settledFrames++;
            settledWorst = Math.max(settledWorst, error);
        } else {
            edgeWorst = Math.max(edgeWorst, error);
        }
        JsonObject frame = new JsonObject();
        frame.addProperty("name", name);
        frame.addProperty("x", round(x));
        frame.addProperty("y", round(y));
        frame.addProperty("rest", round(rest));
        frame.addProperty("over", flat ? patchName(x) : "edge");
        frames.add(frame);
    }

    private static double restY(Entity body) {
        AABB lifted = body.getBoundingBox().move(0, 1.5, 0);
        Vec3 fall = Entity.collideBoundingBox(null, new Vec3(0, -3, 0), lifted, body.level(), List.of());
        return body.getY() + 1.5 + fall.y;
    }

    private void order(MatchRuntime m) {
        MatchCore core = m.core();
        if (core == null || core.queries().unitHandles().length != 1) {
            return;
        }
        int handle = core.queries().unitHandles()[0];
        m.submit(
                PlayerSlot.ADMIN,
                Move.replace(UnitSet.of(handle), new Vec2d(localX(m, WEST + LENGTH + 0.5), localZ(m, 0.5))));
    }

    private static @Nullable UnitEntity body(Minecraft mc) {
        if (mc.level == null) {
            return null;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity unit) {
                return unit;
            }
        }
        return null;
    }

    /** The patch whose blocks hold row-local x, or -1 over grass. */
    private static int patchAt(double x) {
        int column = (int) Math.floor(x);
        for (int k = 0; k < PATCHES.length; k++) {
            if (column >= PATCHES[k] && column < PATCHES[k] + 2) {
                return k;
            }
        }
        return -1;
    }

    private static String patchName(double x) {
        int k = patchAt(x);
        return k < 0 ? "grass" : NAMES[k];
    }

    private double rowWest() {
        return anchor.getX() + WEST;
    }

    /** Match-local X of a point {@code dx} blocks east of the anchor. */
    private double localX(MatchRuntime m, double dx) {
        return anchor.getX() + dx - m.space().origin().getX();
    }

    private double localZ(MatchRuntime m, double dz) {
        return anchor.getZ() + dz - m.space().origin().getZ();
    }

    private static double round(double v) {
        return Math.round(v * 10_000) / 10_000.0;
    }

    /** The patches on the grass, west to east, three wide about the anchor's row. */
    private void buildRow(ServerLevel level) {
        int y = anchor.getY();
        BlockState stairs = Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        BlockState snow = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3);
        BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE);
        for (int k = 0; k < PATCHES.length; k++) {
            for (int dx = PATCHES[k]; dx < PATCHES[k] + 2; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = anchor.getX() + WEST + dx;
                    int z = anchor.getZ() + dz;
                    BlockPos on = new BlockPos(x, y, z);
                    BlockPos ground = new BlockPos(x, y - 1, z);
                    switch (NAMES[k]) {
                        case "slab" -> level.setBlockAndUpdate(on, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
                        case "stairs" -> level.setBlockAndUpdate(on, stairs);
                        case "snow" -> level.setBlockAndUpdate(on, snow);
                        case "carpet" -> level.setBlockAndUpdate(on, Blocks.WHITE_CARPET.defaultBlockState());
                        case "path" -> level.setBlockAndUpdate(ground, Blocks.DIRT_PATH.defaultBlockState());
                        case "farmland" -> level.setBlockAndUpdate(ground, farmland);
                        default -> level.setBlockAndUpdate(ground, Blocks.WATER.defaultBlockState());
                    }
                }
            }
        }
    }
}
