package dev.strategos.core.sim.civ;

import dev.strategos.core.content.CivDef;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.MatchConfig;
import java.util.BitSet;
import org.jspecify.annotations.Nullable;

/**
 * Each faction's civilisation bound to a template set (CIV-01, ADR-0028): its {@link CivDef} and its roster as bitsets
 * over template indices and technology net indices, resolved once at match start, at restore and at a content swap, so
 * the roster rule is one bit test. A faction outside the match (Gaia) is bound to nothing and may own everything.
 * Derived state: never saved, never hashed (the civilisation ids are, through {@link MatchConfig#civs()}).
 */
@CoreApi
public final class FactionCivs {
    private final CivDef[] defs;
    private final @Nullable BitSet[] templates; // null = every template
    private final @Nullable BitSet[] techs; // null = every technology

    private FactionCivs(CivDef[] defs, @Nullable BitSet[] templates, @Nullable BitSet[] techs) {
        this.defs = defs;
        this.templates = templates;
        this.techs = techs;
    }

    /**
     * Why {@code set} cannot serve {@code config}'s civilisations, or null when it can: a faction's civilisation it
     * lacks, or an empty list over a set that has civilisations but not {@link CivId#DEFAULT}.
     */
    public static @Nullable String problem(MatchConfig config, TemplateSet set) {
        for (int f = 0; f < config.factions(); f++) {
            CivId id = config.civ(f);
            if (set.civ(id) == null) {
                return config.civs().isEmpty()
                        ? "the template set has civilisations but not the default " + id
                                + ", which a match with no civilisations list plays"
                        : "faction " + f + " plays civilisation " + id + ", which the template set lacks";
            }
        }
        return null;
    }

    /** Binds each faction of {@code config} to its civilisation in {@code set}; throws {@link CivBindException} when {@link #problem} is set. */
    public static FactionCivs bind(MatchConfig config, TemplateSet set) {
        String problem = problem(config, set);
        if (problem != null) {
            throw new CivBindException(problem);
        }
        int n = config.factions();
        CivDef[] defs = new CivDef[n];
        @Nullable BitSet[] templates = new BitSet[n];
        @Nullable BitSet[] techs = new BitSet[n];
        for (int f = 0; f < n; f++) {
            CivDef def = java.util.Objects.requireNonNull(set.civ(config.civ(f))); // checked by problem()
            defs[f] = def;
            CivDef.Roster roster = def.roster();
            if (roster.templates() != null) {
                BitSet bits = new BitSet();
                for (TemplateId id : roster.templates()) {
                    UnitTemplate t = set.byId(id);
                    if (t != null) {
                        bits.set(t.netIndex());
                    }
                }
                templates[f] = bits;
            }
            if (roster.techs() != null) {
                BitSet bits = new BitSet();
                for (TechId id : roster.techs()) {
                    TechDef t = set.tech(id);
                    if (t != null) {
                        bits.set(t.netIndex());
                    }
                }
                techs[f] = bits;
            }
        }
        return new FactionCivs(defs, templates, techs);
    }

    /** Faction {@code faction}'s civilisation; null for a faction outside the match. */
    public @Nullable CivDef of(int faction) {
        return faction >= 0 && faction < defs.length ? defs[faction] : null;
    }

    /** Whether {@code faction}'s roster has the template at {@code netIndex}. */
    public boolean hasTemplate(int faction, int netIndex) {
        if (faction < 0 || faction >= defs.length) {
            return true;
        }
        BitSet bits = templates[faction];
        return bits == null || bits.get(netIndex);
    }

    /** Whether {@code faction}'s roster has the technology at {@code netIndex}. */
    public boolean hasTech(int faction, int netIndex) {
        if (faction < 0 || faction >= defs.length) {
            return true;
        }
        BitSet bits = techs[faction];
        return bits == null || bits.get(netIndex);
    }
}
