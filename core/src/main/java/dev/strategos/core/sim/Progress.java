package dev.strategos.core.sim;

import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.TechId;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * What each faction has researched (TECH-01, spec §2.1, row 20): the technologies by their ids, not their net indices,
 * so a save survives a reload that shifts the indices. Saved and hashed once any faction has one; a match that never
 * researches saves and hashes as before.
 */
@CoreApi
public final class Progress {
    private final List<TreeSet<String>> researched = new ArrayList<>();
    private TemplateSet set;

    Progress(TemplateSet set, int factions) {
        this.set = set;
        for (int f = 0; f < factions; f++) {
            researched.add(new TreeSet<>());
        }
    }

    /**
     * The source id tech {@code netIndex}'s modifiers carry: {@code -1 - netIndex}. Source ids are told apart by sign:
     * entity handles are positive (at least 1 << 20) and technologies negative, so a tech's effects never meet a
     * unit's or a building's own; {@link #techOf} maps one back.
     */
    public static int sourceId(int netIndex) {
        return -1 - netIndex;
    }

    /** The tech whose modifiers carry {@code sourceId}, or null for any other source. */
    public @Nullable TechId techOf(int sourceId) {
        if (sourceId >= 0) {
            return null;
        }
        TechDef def = set.techByIndex(-1 - sourceId);
        return def == null ? null : def.id();
    }

    void swap(TemplateSet next) {
        this.set = next;
    }

    public boolean researched(int faction, TechId tech) {
        return faction >= 0
                && faction < researched.size()
                && researched.get(faction).contains(tech.value());
    }

    /** Faction {@code faction} has {@code tech} from now on. */
    public void complete(int faction, TechId tech) {
        researched.get(faction).add(tech.value());
    }

    /** Faction {@code faction}'s researched techs, in id order. */
    public List<TechId> of(int faction) {
        return researched.get(faction).stream().map(TechId::new).toList();
    }

    public boolean empty() {
        for (TreeSet<String> ids : researched) { // a loop, not a stream: the state hash calls this every tick
            if (!ids.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** The saved form: per faction its tech ids in order; none at all while nothing was researched. */
    public List<List<String>> save() {
        if (empty()) {
            return List.of();
        }
        return researched.stream().<List<String>>map(List::copyOf).toList();
    }

    public void restore(List<List<String>> saved) {
        for (int f = 0; f < researched.size(); f++) {
            researched.get(f).clear();
            if (f < saved.size()) {
                researched.get(f).addAll(saved.get(f));
            }
        }
    }

    /** Folds the researched techs into {@code h}, faction by faction; {@code h} itself while there are none. */
    public long mix(long h) {
        if (empty()) {
            return h;
        }
        for (int f = 0; f < researched.size(); f++) {
            if (researched.get(f).isEmpty()) {
                continue;
            }
            h = Hasher.mix(h, f);
            h = Hasher.mix(h, researched.get(f).size());
            for (String id : researched.get(f)) {
                h = Hasher.mix(h, id.hashCode());
            }
        }
        return h;
    }
}
