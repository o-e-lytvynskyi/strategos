package dev.strategos.core.sim.prod;

import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TechEffect;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.QueueEntry;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.Progress;
import dev.strategos.core.sim.TechResearched;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.DamageStages;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.sim.stats.ModifierOp;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Research (TECH-01, spec §2.1): who may research a tech, whether it is pending, and what completing it does to the
 * faction's {@link Progress} and stats. A tech's modifiers carry {@link Progress#sourceId} of its net index.
 */
@CoreApi
public final class Techs {
    /** The token path of what a building researches. */
    public static final String RESEARCHER = "Researcher/techs";

    private Techs() {}

    /** Whether building {@code handle} researches {@code tech}: its effective {@code Researcher/techs} names it. */
    public static boolean researches(MatchState s, int handle, TechDef tech) {
        BuildingStore b = s.buildings();
        int path = s.stats().tokenPath(RESEARCHER);
        List<String> techs = path < 0 ? null : s.stats().blockTokens(b.template(handle), b.faction(handle), path);
        return techs != null && techs.contains(tech.id().value());
    }

    /**
     * {@code faction}'s phase: the highest ordinal among the phase techs it has researched, 0 (Village) for none (spec
     * row 19: Village is never researched, so a match without research keeps its hash).
     */
    public static int phase(MatchState s, int faction) {
        int phase = 0;
        for (TechId id : s.progress().of(faction)) {
            TechDef t = s.templates().tech(id);
            if (t != null && t.isPhase()) {
                phase = Math.max(phase, t.ordinal());
            }
        }
        return phase;
    }

    /** Whether any building of {@code faction} holds {@code tech} in its queue. */
    public static boolean pending(MatchState s, int faction, TechDef tech) {
        BuildingStore b = s.buildings();
        int entry = QueueEntry.techEntry(tech.netIndex());
        for (int handle : b.handles()) {
            if (b.faction(handle) != faction) {
                continue;
            }
            for (int e : b.queue(handle)) {
                if (e == entry) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The technology queue entry {@code entry} names in the current set, or null for a retired index. */
    public static @Nullable TechDef of(MatchState s, int entry) {
        return s.templates().techByIndex(QueueEntry.techIndex(entry));
    }

    /**
     * {@code faction} finishes researching {@code tech}: it is researched, its stats' tech sources are derived afresh
     * (as a restore derives them, so live and restored stats agree), and the event is posted.
     */
    static void complete(MatchState s, int faction, TechDef tech) {
        s.progress().complete(faction, tech.id());
        rebuild(s, faction, null);
        for (int handle : s.buildings().handles()) { // a change of most hit points can move a building's stage
            if (s.buildings().faction(handle) == faction && s.buildings().state(handle) == BuildingStore.COMPLETE) {
                DamageStages.update(s, handle);
            }
        }
        s.outputs().add(new TechResearched(new FactionSlot(faction), tech.id()));
    }

    /**
     * Derives {@code faction}'s tech sources from its researched techs: drops every tech source (those of {@code
     * old}'s set too, after a swap), then adds the effects of each researched tech no researched tech supersedes. A
     * researched tech the set lacks is skipped.
     */
    private static void rebuild(MatchState s, int faction, @Nullable TemplateSet old) {
        TemplateSet set = s.templates();
        FactionSlot slot = new FactionSlot(faction);
        for (TechDef t : set.techs()) {
            s.stats().remove(slot, Progress.sourceId(t.netIndex()));
        }
        if (old != null) {
            for (TechDef t : old.techs()) {
                s.stats().remove(slot, Progress.sourceId(t.netIndex()));
            }
        }
        List<TechId> superseded = new ArrayList<>();
        for (TechId id : s.progress().of(faction)) {
            TechDef t = set.tech(id);
            if (t != null && t.supersedes() != null) {
                superseded.add(t.supersedes());
            }
        }
        for (TechId id : s.progress().of(faction)) {
            TechDef t = set.tech(id);
            if (t != null && !superseded.contains(id)) {
                apply(s, faction, t);
            }
        }
    }

    /** Hands {@code tech}'s effects to {@code faction}'s stats. */
    private static void apply(MatchState s, int faction, TechDef tech) {
        int source = Progress.sourceId(tech.netIndex());
        for (TechEffect e : tech.effects()) {
            s.stats()
                    .add(
                            new FactionSlot(faction),
                            new Modifier(
                                    e.path(),
                                    ModifierOp.valueOf(e.op().name()),
                                    e.value(),
                                    e.tokens() == null ? "" : e.tokens(),
                                    e.filter(),
                                    e.stage(),
                                    e.priority(),
                                    source));
        }
    }

    /**
     * Gives every faction's stats the effects of the techs it has researched, in {@code s}' set: after a restore, whose
     * stats start empty, and after a swap, which drops {@code old}'s tech sources first.
     */
    public static void reapply(MatchState s, @Nullable TemplateSet old) {
        for (int f = 0; f < s.config().factions(); f++) {
            rebuild(s, f, old);
        }
    }

    /**
     * After a swap from {@code old}: queue entries whose tech the new set lacks, or holds under another id at that
     * index, leave their queues. Nothing is refunded: the cost of a tech that is gone is not known.
     */
    public static void dropRetired(MatchState s, TemplateSet old) {
        BuildingStore b = s.buildings();
        for (int handle : b.handles()) {
            int[] queue = b.queue(handle);
            for (int k = queue.length - 1; k >= 0; k--) {
                if (!QueueEntry.isTech(queue[k])) {
                    continue;
                }
                int index = QueueEntry.techIndex(queue[k]);
                TechDef was = old.techByIndex(index);
                TechDef now = s.templates().techByIndex(index);
                if (was == null || now == null || !was.id().equals(now.id())) {
                    b.dequeue(handle, k);
                }
            }
        }
    }
}
