package dev.strategos.core.sim.command;

import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Rng;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.Streams;
import dev.strategos.core.sim.econ.Livestock;

/**
 * SpawnUnits, admin only: creates units around a centre. Unit i stands at angle {@code 2π·u(2i)} and radius
 * {@code spread·√u(2i+1)} (uniform over the disc), from {@code Rng} stream SPAWN keyed by issuer and sequence, kept
 * 0.5 inside the bounds, on the floor, facing +Z, idle.
 */
final class SpawnUnitsRule implements CommandRule<SpawnUnits> {
    static final int MAX_COUNT = 300;
    static final double MAX_SPREAD = 64;

    @Override
    public CommandKind<SpawnUnits> kind() {
        return CommandKinds.SPAWN_UNITS;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, SpawnUnits spawn, MatchState state) {
        int requested = Math.max(0, spawn.count());
        if (!envelope.issuer().isAdmin()) {
            return Tally.rejectAll(envelope, kind(), requested, RejectReason.NOT_PERMITTED);
        }
        UnitTemplate template = state.templates().byId(spawn.template());
        if (template == null) {
            return Tally.rejectAll(envelope, kind(), requested, RejectReason.UNKNOWN_TEMPLATE);
        }
        if (template.isBuilding()) {
            return Tally.rejectAll(envelope, kind(), requested, RejectReason.UNSUPPORTED); // buildings are placed
        }
        // The faction must be seated in this match, or be Gaia for an animal (ECO-16), besides the spec's count and
        // spread ranges.
        boolean fauna = Livestock.isFauna(state.templates(), template.netIndex());
        if (spawn.count() < 1
                || spawn.count() > MAX_COUNT
                || !(spawn.spread() >= 0 && spawn.spread() <= MAX_SPREAD)
                || spawn.faction().value() > state.gaia()
                || (spawn.faction().value() == state.gaia()) != fauna) {
            return Tally.rejectAll(envelope, kind(), requested, RejectReason.INVALID_ARGUMENT);
        }
        MatchBounds bounds = state.config().bounds();
        if (!bounds.contains(spawn.center().x(), spawn.center().z())) {
            return Tally.rejectAll(envelope, kind(), requested, RejectReason.OUT_OF_BOUNDS);
        }
        if (state.entities().aliveCount() + spawn.count()
                > state.config().rules().unitCap()) {
            return Tally.rejectAll(envelope, kind(), requested, RejectReason.UNIT_CAP);
        }

        long seed = state.config().seed();
        long tick = state.tick();
        long key = ((long) envelope.issuer().value() << 32) ^ envelope.clientSeq();
        double floorY = bounds.flatFloorY();
        for (int i = 0; i < spawn.count(); i++) {
            double angle = 2 * Math.PI * Rng.unit(seed, Streams.SPAWN, tick, key, 2L * i);
            double r = spawn.spread() * DetMath.sqrt(Rng.unit(seed, Streams.SPAWN, tick, key, 2L * i + 1));
            Vec2d p = bounds.clamp(
                    new Vec2d(
                            spawn.center().x() + r * DetMath.cos(angle),
                            spawn.center().z() + r * DetMath.sin(angle)),
                    0.5);
            int handle = state.spawnUnit(template.netIndex(), spawn.faction().value(), p.x(), floorY, p.z(), 0f);
            if (fauna) {
                Livestock.spawn(state, handle);
            }
        }
        return Tally.acceptAll(envelope, kind(), spawn.count());
    }
}
