package dev.strategos.gametest.spike;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Rng;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.entity.UnitSpawnData;
import dev.strategos.mod.registry.ModEntityTypes;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Spike S1, the server cost per body (spec 8.4): the configurations in their order, and the populations that make,
 * drive and remove each configuration's bodies. Measured work (combat events, the build storm) runs in
 * {@code ServerTickEvent.Pre}, inside the tick; bookkeeping (arrivals, orders) runs in {@code Post}, outside it.
 */
final class S1BodyCost {
    static final long SEED = 42;

    private S1BodyCost() {}

    enum Body {
        NONE,
        K,
        P,
        L,
        K_MISC_STRAY,
        K_CREATURE
    }

    enum Motion {
        IDLE,
        MOVING,
        FIGHTING
    }

    /** {@code recorded} false: a warm-up configuration, run so the JIT has compiled every body's path first. */
    record Config(String name, Body body, int count, Motion motion, boolean push, boolean storm, boolean recorded) {
        Config(String name, Body body, int count, Motion motion, boolean push, boolean storm) {
            this(name, body, count, motion, push, storm, true);
        }

        String state() {
            return motion.name().toLowerCase(java.util.Locale.ROOT) + (push ? "+push" : "") + (storm ? "+storm" : "");
        }
    }

    static List<Config> plan() {
        List<Config> plan = new ArrayList<>();
        for (Body body : List.of(Body.K, Body.P, Body.L)) {
            String name = "warmup_" + body.name().toLowerCase(java.util.Locale.ROOT) + "_500";
            plan.add(new Config(name, body, 500, Motion.FIGHTING, false, false, false));
        }
        plan.add(new Config("baseline_first", Body.NONE, 0, Motion.IDLE, false, false));
        for (Body body : List.of(Body.K, Body.P, Body.L)) {
            for (int count : new int[] {200, 500, 1000}) {
                for (Motion motion : Motion.values()) {
                    String name = body.name().toLowerCase(java.util.Locale.ROOT) + "_" + count + "_"
                            + motion.name().toLowerCase(java.util.Locale.ROOT);
                    plan.add(new Config(name, body, count, motion, false, false));
                }
            }
        }
        plan.add(new Config("melee_blob_l_300", Body.L, 300, Motion.FIGHTING, true, false));
        for (Body body : List.of(Body.K, Body.P, Body.L)) {
            String name = "build_storm_" + body.name().toLowerCase(java.util.Locale.ROOT) + "_500";
            plan.add(new Config(name, body, 500, Motion.MOVING, false, true));
        }
        plan.add(new Config("category_k_misc_500", Body.K_MISC_STRAY, 500, Motion.IDLE, false, false));
        plan.add(new Config("category_k_creature_500", Body.K_CREATURE, 500, Motion.IDLE, false, false));
        plan.add(new Config("baseline_last", Body.NONE, 0, Motion.IDLE, false, false));
        return plan;
    }

    static Population population(Config config, SpikeArena arena) {
        return switch (config.body()) {
            case NONE -> new Population() {};
            case K -> new Kinematic(arena, config);
            case P -> new Pathfinders(arena, config);
            case L -> new Physics(arena, config);
            case K_MISC_STRAY -> new Strays(arena, config, ModEntityTypes.UNIT_HUMANOID.get());
            case K_CREATURE -> new Strays(arena, config, SpikeRegistry.require(SpikeRegistry.unitCreature));
        };
    }

    /** A random point of the 16 x 16 blob, from the core's seeded Rng. */
    static Vec2d blobPoint(long tick, int index) {
        double x = SpikeArena.BLOB + 16 * Rng.unit(SEED, 1, tick, index, 0);
        double z = SpikeArena.BLOB + 16 * Rng.unit(SEED, 1, tick, index, 1);
        return new Vec2d(x, z);
    }

    /** The vanilla-visible cost of combat, every 20 ticks per body, staggered by index. */
    static void combatEvents(ServerLevel level, @Nullable LivingEntity body, int index, long tick) {
        if (body != null && (index + tick) % 20 == 0) {
            body.swing(InteractionHand.MAIN_HAND);
            level.broadcastDamageEvent(body, level.damageSources().generic());
        }
    }

    /** One configuration's bodies. */
    interface Population {
        default void spawn() {}

        default boolean ready() {
            return true;
        }

        /** Measured: runs inside the tick. */
        default void preTick(long tick) {}

        /** Bookkeeping: runs after the tick's time is taken. */
        default void postTick(long tick) {}

        default long stepNanos() {
            return -1;
        }

        default long adapterNanos() {
            return -1;
        }

        default void tearDown() {}
    }

    /** K: production bodies of a production match, posed by KinematicDriver. */
    static final class Kinematic implements Population {
        private final SpikeArena arena;
        private final Config config;
        private @Nullable MatchRuntime match;
        private int[] handles = new int[0];
        private boolean[] toB = new boolean[0];

        Kinematic(SpikeArena arena, Config config) {
            this.arena = arena;
            this.config = config;
        }

        private MatchRuntime match() {
            MatchRuntime m = match;
            if (m == null) {
                throw new IllegalStateException("the kinematic population has no match yet");
            }
            return m;
        }

        private MatchCore core() {
            MatchCore core = match().core();
            if (core == null) {
                throw new IllegalStateException("the spike match faulted: " + match().faultMessage());
            }
            return core;
        }

        @Override
        public void spawn() {
            MatchOptions options = new MatchOptions(
                    SEED,
                    2,
                    false,
                    MatchSource.GAMETEST,
                    null,
                    dev.strategos.core.port.MetricsSink.none(),
                    arena.floorY);
            match = StrategosServer.get().matches().create(arena.level, arena.space, options);
            double spread = config.count() >= 500 ? 12 : 8;
            for (int left = config.count(); left > 0; left -= 300) {
                match().submit(
                                PlayerSlot.ADMIN,
                                new SpawnUnits(
                                        TemplateSet.DEBUG_WALKER,
                                        new FactionSlot(0),
                                        new Vec2d(SpikeArena.A[0], SpikeArena.A[1]),
                                        Math.min(300, left),
                                        spread));
            }
        }

        @Override
        public boolean ready() {
            if (core().queries().unitCount() != config.count()
                    || match().bodies().count() != config.count()) {
                return false;
            }
            if (handles.length == 0) {
                handles = core().queries().unitHandles();
                toB = new boolean[handles.length];
            }
            return true;
        }

        @Override
        public void preTick(long tick) {
            if (config.motion() != Motion.FIGHTING) {
                return;
            }
            for (int i = 0; i < handles.length; i++) {
                combatEvents(arena.level, match().bodies().get(handles[i]), i, tick);
            }
        }

        @Override
        public void postTick(long tick) {
            switch (config.motion()) {
                case IDLE -> {}
                case MOVING -> {
                    List<Integer> toBNow = new ArrayList<>();
                    List<Integer> toANow = new ArrayList<>();
                    for (int i = 0; i < handles.length; i++) {
                        UnitView unit = core().queries().unit(handles[i]);
                        if (unit != null && unit.state() == UnitState.IDLE) {
                            toB[i] = !toB[i];
                            (toB[i] ? toBNow : toANow).add(handles[i]);
                        }
                    }
                    order(toBNow, SpikeArena.B);
                    order(toANow, SpikeArena.A);
                }
                case FIGHTING -> {
                    if (tick % 40 == 0) {
                        for (int i = 0; i < handles.length; i++) {
                            match().submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(handles[i]), blobPoint(tick, i)));
                        }
                    }
                }
            }
        }

        /** One Move per 300 units, a unit set's limit. */
        private void order(List<Integer> units, double[] target) {
            for (int from = 0; from < units.size(); from += 300) {
                int[] set = units.subList(from, Math.min(units.size(), from + 300)).stream()
                        .mapToInt(Integer::intValue)
                        .toArray();
                match().submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(set), new Vec2d(target[0], target[1])));
            }
        }

        @Override
        public long stepNanos() {
            return core().timers().lastStepNanos();
        }

        @Override
        public long adapterNanos() {
            return core().timers().lastAdapterNanos();
        }

        @Override
        public void tearDown() {
            if (match != null) {
                StrategosServer.get().matches().dispose(match.id());
            }
        }
    }

    /** P: stripped PathfinderMobs, walking with their navigation or fighting with vanilla melee. */
    static final class Pathfinders implements Population {
        private final SpikeArena arena;
        private final Config config;
        private final List<SpikeMob> mobs = new ArrayList<>();
        private boolean[] toB = new boolean[0];

        Pathfinders(SpikeArena arena, Config config) {
            this.arena = arena;
            this.config = config;
        }

        @Override
        public void spawn() {
            EntityType<SpikeMob> type = SpikeRegistry.require(SpikeRegistry.mob);
            List<Vec3> points = config.motion() == Motion.FIGHTING
                    ? teams(arena, config.count())
                    : arena.grid(config.count(), SpikeArena.A[0], SpikeArena.A[1]);
            for (Vec3 at : points) {
                SpikeMob mob = type.create(arena.level);
                if (mob == null) {
                    throw new IllegalStateException("cannot create a spike mob");
                }
                mob.moveTo(at.x, at.y, at.z, 0, 0);
                arena.level.addFreshEntity(mob);
                mobs.add(mob);
            }
            toB = new boolean[mobs.size()];
            if (config.motion() == Motion.FIGHTING) {
                int half = mobs.size() / 2;
                for (int i = 0; i < half; i++) {
                    mobs.get(i).fight(mobs.get(i + half));
                    mobs.get(i + half).fight(mobs.get(i));
                }
            }
        }

        @Override
        public void postTick(long tick) {
            if (config.motion() != Motion.MOVING) {
                return;
            }
            for (int i = 0; i < mobs.size(); i++) {
                SpikeMob mob = mobs.get(i);
                if (mob.getNavigation().isDone()) {
                    toB[i] = !toB[i];
                    Vec3 to = toB[i]
                            ? arena.world(SpikeArena.B[0], SpikeArena.B[1])
                            : arena.world(SpikeArena.A[0], SpikeArena.A[1]);
                    mob.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
                }
            }
        }

        @Override
        public void tearDown() {
            mobs.forEach(Entity::discard);
        }
    }

    /** L: physics LivingEntities with the full vanilla tick. */
    static final class Physics implements Population {
        private final SpikeArena arena;
        private final Config config;
        private final List<SpikePhysicsBody> bodies = new ArrayList<>();

        Physics(SpikeArena arena, Config config) {
            this.arena = arena;
            this.config = config;
        }

        @Override
        public void spawn() {
            EntityType<SpikePhysicsBody> type = SpikeRegistry.require(SpikeRegistry.physics);
            List<Vec3> points = config.motion() == Motion.FIGHTING
                    ? teams(arena, config.count())
                    : arena.grid(config.count(), SpikeArena.A[0], SpikeArena.A[1]);
            Vec3 a = arena.world(SpikeArena.A[0], SpikeArena.A[1]);
            Vec3 b = arena.world(SpikeArena.B[0], SpikeArena.B[1]);
            for (Vec3 at : points) {
                SpikePhysicsBody body = type.create(arena.level);
                if (body == null) {
                    throw new IllegalStateException("cannot create a spike physics body");
                }
                body.moveTo(at.x, at.y, at.z, 0, 0);
                body.setPush(config.push());
                if (config.motion() == Motion.MOVING) {
                    body.walk(b, a);
                }
                arena.level.addFreshEntity(body);
                bodies.add(body);
            }
        }

        @Override
        public void preTick(long tick) {
            if (config.motion() != Motion.FIGHTING) {
                return;
            }
            for (int i = 0; i < bodies.size(); i++) {
                combatEvents(arena.level, bodies.get(i), i, tick);
            }
        }

        @Override
        public void postTick(long tick) {
            if (config.motion() == Motion.FIGHTING && tick % 40 == 0) {
                for (int i = 0; i < bodies.size(); i++) {
                    Vec2d p = blobPoint(tick, i);
                    bodies.get(i).walk(arena.world(p.x(), p.z()), null);
                }
            }
        }

        @Override
        public void tearDown() {
            bodies.forEach(Entity::discard);
        }
    }

    /** Unit bodies no match drives, bound to match id 0 so the stray-body guard admits them: the category check. */
    static final class Strays implements Population {
        private final SpikeArena arena;
        private final Config config;
        private final EntityType<UnitEntity> type;
        private final List<UnitEntity> bodies = new ArrayList<>();

        Strays(SpikeArena arena, Config config, EntityType<UnitEntity> type) {
            this.arena = arena;
            this.config = config;
            this.type = type;
        }

        @Override
        public void spawn() {
            List<Vec3> points = arena.grid(config.count(), SpikeArena.A[0], SpikeArena.A[1]);
            for (int i = 0; i < points.size(); i++) {
                UnitEntity body = type.create(arena.level);
                if (body == null) {
                    throw new IllegalStateException("cannot create a stray unit body");
                }
                body.bind(new UnitSpawnData(0, i, 0, 0, UnitSpawnData.KINEMATIC));
                body.moveTo(points.get(i).x, points.get(i).y, points.get(i).z, 0, 0);
                arena.level.addFreshEntity(body);
                bodies.add(body);
            }
        }

        @Override
        public void tearDown() {
            bodies.forEach(Entity::discard);
        }
    }

    /** Two teams facing each other across the blob, the first half west of it and the second east. */
    private static List<Vec3> teams(SpikeArena arena, int count) {
        List<Vec3> points = new ArrayList<>(arena.grid(count / 2, 24.5, 48.5));
        points.addAll(arena.grid(count - count / 2, 72.5, 48.5));
        return points;
    }
}
