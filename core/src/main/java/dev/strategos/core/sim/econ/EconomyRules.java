package dev.strategos.core.sim.econ;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A match's economy rules (ECO-01), copied in when it is created: its resources in their order, at most {@link #MAX},
 * and the most population a faction may reach (ECO-06; D-01's 150 by default, 50 to 200 in setup). The default is
 * D-10's four, food (fruit, grain), wood (tree), stone (rock) and iron (ore), starting at 0; a match decoded from M1's
 * or M2's rules gets it.
 */
@CoreApi
public record EconomyRules(List<ResourceDef> resources, int populationMax) {
    public static final int MAX = 16;

    /** D-01: a faction's most population, by default. */
    public static final int POPULATION_MAX = 150;

    public static final EconomyRules DEFAULTS = new EconomyRules(List.of(
            new ResourceDef("food", List.of("fruit", "grain"), 0),
            new ResourceDef("wood", List.of("tree"), 0),
            new ResourceDef("stone", List.of("rock"), 0),
            new ResourceDef("iron", List.of("ore"), 0)));

    public static final Codec<EconomyRules> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ResourceDef.CODEC
                            .listOf()
                            .validate(EconomyRules::checked)
                            .fieldOf("resources")
                            .forGetter(EconomyRules::resources),
                    Codec.intRange(1, 10_000)
                            .optionalFieldOf("populationMax", POPULATION_MAX)
                            .forGetter(EconomyRules::populationMax))
            .apply(i, EconomyRules::new));

    /** Rules with D-01's population maximum. */
    public EconomyRules(List<ResourceDef> resources) {
        this(resources, POPULATION_MAX);
    }

    public EconomyRules {
        resources = List.copyOf(resources);
        String problem = problem(resources);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
    }

    /** The index of resource {@code id}, or -1. */
    public int indexOf(String id) {
        for (int r = 0; r < resources.size(); r++) {
            if (resources.get(r).id().equals(id)) {
                return r;
            }
        }
        return -1;
    }

    public int size() {
        return resources.size();
    }

    /** These rules with every faction starting at {@code units} of each resource, in order. */
    public EconomyRules withStart(long... units) {
        List<ResourceDef> out = new ArrayList<>();
        for (int r = 0; r < resources.size(); r++) {
            out.add(resources.get(r).withStart(r < units.length ? units[r] : 0));
        }
        return new EconomyRules(out, populationMax);
    }

    private static DataResult<List<ResourceDef>> checked(List<ResourceDef> resources) {
        String problem = problem(resources);
        return problem == null ? DataResult.success(resources) : DataResult.error(() -> problem);
    }

    private static @Nullable String problem(List<ResourceDef> resources) {
        if (resources.size() > MAX) {
            return resources.size() + " resources, more than " + MAX;
        }
        for (int a = 0; a < resources.size(); a++) {
            for (int b = 0; b < a; b++) {
                if (resources.get(a).id().equals(resources.get(b).id())) {
                    return "resource '" + resources.get(a).id() + "' listed twice";
                }
            }
        }
        return null;
    }
}
