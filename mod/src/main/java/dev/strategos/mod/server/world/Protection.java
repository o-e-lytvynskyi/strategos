package dev.strategos.mod.server.world;

import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import it.unimi.dsi.fastutil.ints.Int2DoubleRBTreeMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import org.jspecify.annotations.Nullable;

/**
 * Match blocks change only through the game's rules (BLD-07, 04 §11.9): a building's blocks, which the block writer
 * entered in the {@link ProtectedBlockIndex}, are guarded against every engine source by its event, each handler
 * inside the match's {@code FaultBarrier}: a player's break is cancelled; a block placed by any entity inside a
 * footprint's box is cancelled; an explosion keeps its protected positions out of its list and crushes their building
 * instead, by distance; pistons and mobs that break or take blocks do nothing anywhere in a match's space; a fluid turns
 * no protected block. Crop growth and natural spawns are M3's ({@link MatchGrowth}, {@link MatchSpawns}). The
 * backstop: any other change of a protected block, which the block-change router sees without the writer's flag, is
 * undone at the end of the tick, and logged once per match.
 */
public final class Protection {
    /** An explosion's crush on a building, per protected block it would have broken, at its centre; less with distance. */
    static final double CRUSH = 4;

    private final List<Restore> restores = new ArrayList<>();
    private final it.unimi.dsi.fastutil.ints.IntRBTreeSet logged = new it.unimi.dsi.fastutil.ints.IntRBTreeSet();
    private long restored;

    private record Restore(ServerLevel level, BlockPos pos, BlockState state, MatchRuntime rt) {}

    /** How many changes the backstop has undone, for tests. */
    public long restored() {
        return restored;
    }

    /** The block-change router saw {@code pos} change without the writer's flag: a protected one comes back at tick end. */
    void changed(
            StrategosServer s,
            ServerLevel level,
            BlockPos pos,
            BlockState previous,
            BlockState state,
            MatchRuntime rt) {
        if (previous.equals(state) || s.writer().protectedBlocks(level).owner(pos) == 0) {
            return;
        }
        restores.add(new Restore(level, pos.immutable(), previous, rt));
        if (logged.add(rt.id().value())) {
            rt.log()
                    .warn(
                            rt.tick(),
                            "a protected block at {} changed from {} to {} behind the writer; restored",
                            pos,
                            previous,
                            state);
        }
    }

    /** At the end of the server tick: every protected block changed behind the writer's back is put back. */
    public void restoreAll(StrategosServer s) {
        if (restores.isEmpty()) {
            return;
        }
        List<Restore> now = new ArrayList<>(restores);
        restores.clear();
        for (Restore r : now) {
            s.faults().run(r.rt(), "protection backstop", () -> {
                if (s.writer().protectedBlocks(r.level()).owner(r.pos()) != 0) {
                    s.writer().restore(r.level(), r.pos(), r.state());
                    restored++;
                }
            });
        }
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        guard(event.getLevel(), event.getPos(), "break", (s, level, rt) -> {
            if (s.writer().protectedBlocks(level).owner(event.getPos()) != 0) {
                event.setCanceled(true);
            }
        });
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        guard(event.getLevel(), event.getPos(), "place", (s, level, rt) -> {
            if (s.writer().protectedBlocks(level).owner(event.getPos()) != 0 || inFootprint(rt, event.getPos())) {
                event.setCanceled(true);
            }
        });
    }

    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        StrategosServer s = StrategosServer.getOrNull();
        if (s == null || event.getAffectedBlocks().isEmpty()) {
            return;
        }
        ProtectedBlockIndex index = s.writer().protectedBlocks(level);
        Vec3 centre = event.getExplosion().center();
        double reach = Math.max(1, 2.0 * event.getExplosion().radius());
        Map<MatchRuntime, Int2DoubleRBTreeMap> crush = new java.util.LinkedHashMap<>();
        event.getAffectedBlocks().removeIf(pos -> {
            int owner = index.owner(pos);
            if (owner == 0) {
                return false;
            }
            MatchRuntime rt = s.matches().at(level, pos);
            if (rt != null) {
                double d = Math.sqrt(pos.getCenter().distanceToSqr(centre));
                crush.computeIfAbsent(rt, k -> new Int2DoubleRBTreeMap())
                        .addTo(owner, CRUSH * Math.max(0, 1 - d / reach));
            }
            return true;
        });
        Player player = event.getExplosion().getIndirectSourceEntity() instanceof Player p ? p : null;
        for (Map.Entry<MatchRuntime, Int2DoubleRBTreeMap> e : crush.entrySet()) {
            MatchRuntime rt = e.getKey();
            s.faults()
                    .run(
                            rt,
                            "protection explosion",
                            () -> e.getValue().forEach((owner, amount) -> {
                                if (amount > 0) {
                                    rt.damage(new ExternalDamage(
                                            owner,
                                            ExternalDamage.Kind.CRUSH,
                                            (float) (double) amount,
                                            player != null
                                                    ? ExternalDamage.SourceKind.PLAYER
                                                    : ExternalDamage.SourceKind.NONE));
                                }
                            }));
        }
    }

    public static void onPiston(PistonEvent.Pre event) {
        guard(event.getLevel(), event.getPos(), "piston", (s, level, rt) -> event.setCanceled(true));
    }

    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            guard(level, event.getEntity().blockPosition(), "mob griefing", (s, l, rt) -> event.setCanGrief(false));
        }
    }

    public static void onLivingDestroy(LivingDestroyBlockEvent event) {
        guard(event.getEntity().level(), event.getPos(), "mob breaking", (s, level, rt) -> event.setCanceled(true));
    }

    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        guard(event.getLevel(), event.getPos(), "fluid", (s, level, rt) -> {
            if (s.writer().protectedBlocks(level).owner(event.getPos()) != 0) {
                event.setNewState(event.getOriginalState()); // the hook returns the new state, cancelled or not
                event.setCanceled(true);
            }
        });
    }

    /** Whether {@code pos} lies in the box of one of match {@code rt}'s buildings: its footprint, from its floor up. */
    static boolean inFootprint(MatchRuntime rt, BlockPos pos) {
        var core = rt.core();
        if (core == null) {
            return false;
        }
        int x = pos.getX() - rt.space().origin().getX();
        int z = pos.getZ() - rt.space().origin().getZ();
        for (BuildingView b : core.queries().buildings()) {
            var at = b.footprint();
            var bp = core.queries()
                    .templates()
                    .blueprint(java.util.Objects.requireNonNull(
                                    core.queries().templates().byId(b.template()))
                            .netIndex());
            int height = bp == null ? 1 : bp.sizeY();
            if (x >= at.anchorX()
                    && x < at.anchorX() + at.spanX()
                    && z >= at.anchorZ()
                    && z < at.anchorZ() + at.spanZ()
                    && pos.getY() >= b.baseY()
                    && pos.getY() < b.baseY() + height) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface Guard {
        void run(StrategosServer s, ServerLevel level, MatchRuntime rt);
    }

    /** {@code work} for the match whose space holds {@code pos}, inside its fault barrier; nothing outside every match. */
    private static void guard(@Nullable LevelAccessor where, BlockPos pos, String what, Guard work) {
        if (!(where instanceof ServerLevel level)) {
            return;
        }
        StrategosServer s = StrategosServer.getOrNull();
        MatchRuntime rt = s == null ? null : s.matches().at(level, pos);
        if (s == null || rt == null) {
            return;
        }
        s.faults().run(rt, "protection " + what, () -> work.run(s, level, rt));
    }
}
