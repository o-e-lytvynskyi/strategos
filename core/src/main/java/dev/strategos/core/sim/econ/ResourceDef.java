package dev.strategos.core.sim.econ;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;
import java.util.List;

/**
 * A resource of a match (ECO-01, D-10): its id, as template paths name it ({@code Cost/resources/wood}); its subtypes,
 * which gathering rates key ({@code ResourceGatherer/rates/food/fruit}); and how much of it each faction starts with,
 * in whole units.
 */
@CoreApi
public record ResourceDef(String id, List<String> subtypes, long start) {
    public static final Codec<ResourceDef> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.fieldOf("id").forGetter(ResourceDef::id),
                    Codec.STRING.listOf().fieldOf("subtypes").forGetter(ResourceDef::subtypes),
                    Codec.LONG.optionalFieldOf("start", 0L).forGetter(ResourceDef::start))
            .apply(i, ResourceDef::new));

    public ResourceDef {
        if (!id.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("a resource id is lower case, digits and _: '" + id + "'");
        }
        for (String s : subtypes) {
            if (!s.matches("[a-z0-9_]+")) {
                throw new IllegalArgumentException("a subtype is lower case, digits and _: '" + s + "'");
            }
        }
        if (start < 0) {
            throw new IllegalArgumentException("a negative start of " + id + ": " + start);
        }
        subtypes = List.copyOf(subtypes);
    }

    public ResourceDef withStart(long units) {
        return new ResourceDef(id, subtypes, units);
    }
}
