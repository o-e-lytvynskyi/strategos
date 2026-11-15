package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.TechResearched;
import dev.strategos.core.sim.WaveAnnounced;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What a chapter's match said so far: the technologies researched, the notices by kind and the last wave announced,
 * read from the match's outputs. A chapter makes one before its match steps and waits on it.
 */
final class Watch {
    private final Set<TechId> techs = ConcurrentHashMap.newKeySet();
    private final Map<NoticeKind, Integer> notices = new ConcurrentHashMap<>();
    private final AtomicLong waveLeaves = new AtomicLong(-1);

    /** Starts listening to {@code rt}'s outputs. */
    void attach(MatchRuntime rt) {
        rt.addListener(out -> out.events().forEach(e -> {
            if (e instanceof TechResearched t) {
                techs.add(t.tech());
            } else if (e instanceof Notice n) {
                notices.merge(n.kind(), 1, Integer::sum);
            } else if (e instanceof WaveAnnounced w) {
                waveLeaves.set(w.leaves());
            }
        }));
    }

    boolean researched(TechId tech) {
        return techs.contains(tech);
    }

    int notices(NoticeKind kind) {
        return notices.getOrDefault(kind, 0);
    }

    /** The tick the last announced wave leaves, or -1 before one was announced. */
    long waveLeaves() {
        return waveLeaves.get();
    }
}
