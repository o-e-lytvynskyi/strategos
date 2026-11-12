package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.Fx;
import dev.strategos.core.protocol.FxKind;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.ShotFired;
import dev.strategos.core.sim.ShotLanded;
import dev.strategos.core.sim.combat.ShotStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A tick's shot events and their batches (CMB-04): each shot's launch and impact become an {@code fx} event with both
 * ends, and the events are grouped by the pages (16 × 16 blocks) of their two ends, so a player who sees either end
 * is sent each event once. Pure, for tests without a server.
 */
final class ShotFx {
    private ShotFx() {}

    /** The events sent to the players who see the cells {@code (ax, az)} or {@code (bx, bz)}, at most 64. */
    record Group(int cellAx, int cellAz, int cellBx, int cellBz, List<Fx.Event> events) {}

    private record Key(int ax, int az, int bx, int bz) implements Comparable<Key> {
        private static final Comparator<Key> ORDER = Comparator.comparingInt(Key::ax)
                .thenComparingInt(Key::az)
                .thenComparingInt(Key::bx)
                .thenComparingInt(Key::bz);

        @Override
        public int compareTo(Key other) {
            return ORDER.compare(this, other);
        }
    }

    /**
     * The tick's launches and impacts in output order, Y relative to the match's origin ({@code originY}); X and Z are
     * match-local already. An impact without a release (a shot saved before releases existed) lands from itself.
     */
    static List<Fx.Event> events(List<OutputEvent> outputs, int originY) {
        List<Fx.Event> events = new ArrayList<>();
        for (OutputEvent output : outputs) {
            if (output instanceof ShotFired f) {
                events.add(Fx.Event.shot(
                        FxKind.SHOT_LAUNCH,
                        f.fromX(),
                        f.fromY() - originY,
                        f.fromZ(),
                        f.toX(),
                        f.toY() - originY,
                        f.toZ(),
                        flight(f.landTick() - f.launchTick())));
            } else if (output instanceof ShotLanded l) {
                ShotStore.Release from = l.from();
                events.add(
                        from == null
                                ? Fx.Event.shot(
                                        FxKind.SHOT_IMPACT,
                                        l.toX(),
                                        l.toY() - originY,
                                        l.toZ(),
                                        l.toX(),
                                        l.toY() - originY,
                                        l.toZ(),
                                        1)
                                : Fx.Event.shot(
                                        FxKind.SHOT_IMPACT,
                                        from.x(),
                                        from.y() - originY,
                                        from.z(),
                                        l.toX(),
                                        l.toY() - originY,
                                        l.toZ(),
                                        flight(l.landTick() - from.tick())));
            }
        }
        return events;
    }

    /** {@code events} grouped by the pages of their two ends, in page order, at most {@value Fx#MAX_EVENTS} to a group. */
    static List<Group> group(List<Fx.Event> events) {
        Map<Key, List<Fx.Event>> pages = new TreeMap<>();
        for (Fx.Event e : events) {
            Key key = new Key(e.x8() >> 7, e.z8() >> 7, e.toX8() >> 7, e.toZ8() >> 7); // eighths: 128 to a page
            pages.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        List<Group> groups = new ArrayList<>();
        pages.forEach((key, list) -> {
            for (int from = 0; from < list.size(); from += Fx.MAX_EVENTS) {
                groups.add(new Group(
                        key.ax() * 16,
                        key.az() * 16,
                        key.bx() * 16,
                        key.bz() * 16,
                        List.copyOf(list.subList(from, Math.min(list.size(), from + Fx.MAX_EVENTS)))));
            }
        });
        return groups;
    }

    private static int flight(long ticks) {
        return (int) Math.max(1, Math.min(Fx.MAX_FLIGHT_TICKS, ticks));
    }
}
