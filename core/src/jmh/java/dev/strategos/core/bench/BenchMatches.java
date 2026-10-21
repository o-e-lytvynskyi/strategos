package dev.strategos.core.bench;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchConfig;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.TickInputs;
import java.util.ArrayList;
import java.util.List;

/** Matches for the benchmarks, built straight on MatchCore: the test kit's per-step invariant checks would be timed. */
final class BenchMatches {
    static final long SEED = 42;
    static final Vec2d WEST = new Vec2d(8.5, 8.5);
    static final Vec2d EAST = new Vec2d(56.5, 56.5);

    private BenchMatches() {}

    /** A 64 x 64 match with {@code perFaction} walkers in each of two factions, every one ordered on its own. */
    static MatchCore walking(int perFaction) {
        MatchConfig config =
                new MatchConfig(new MatchId(1), SEED, new MatchBounds(64, 64, 0, 32, 1.0), 2, MatchRules.DEFAULTS);
        MatchCore core = MatchCore.create(config, TemplateSet.builtin(), CorePorts.of(System::nanoTime));
        step(
                core,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), WEST, perFaction, 6),
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), EAST, perFaction, 6));
        List<Command> moves = new ArrayList<>();
        for (int f = 0; f < 2; f++) {
            Vec2d target = f == 0 ? EAST : WEST;
            for (int handle : core.queries().unitHandlesOf(new FactionSlot(f))) {
                moves.add(Move.replace(UnitSet.of(handle), target));
            }
        }
        step(core, moves.toArray(Command[]::new));
        while (core.queries().tick() < 10) {
            step(core); // the inbox applies at most 256 commands a tick
        }
        return core;
    }

    /** The whole of each faction ordered to the other waypoint: issued every 120 ticks, so the units never arrive. */
    static TickInputs reorder(MatchCore core, long tick, boolean toEast) {
        List<InputEvent> events = new ArrayList<>(2);
        for (int f = 0; f < 2; f++) {
            Vec2d target = (f == 0) == toEast ? EAST : WEST;
            Move move = Move.replace(UnitSet.of(core.queries().unitHandlesOf(new FactionSlot(f))), target);
            events.add(new CommandEnvelope(PlayerSlot.ADMIN, (int) tick * 2 + f, tick - 1, move));
        }
        return new TickInputs(tick, events);
    }

    static void step(MatchCore core, Command... commands) {
        long tick = core.queries().tick() + 1;
        List<InputEvent> events = new ArrayList<>(commands.length);
        for (int i = 0; i < commands.length; i++) {
            events.add(new CommandEnvelope(PlayerSlot.ADMIN, (int) tick * 1000 + i, tick - 1, commands[i]));
        }
        core.step(new TickInputs(tick, events));
    }
}
