package dev.strategos.core.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TemplateId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A ready-made setup (MATCH-01, {@code data/<ns>/strategos/scenarios/*.json}): the map it is made for, its victory
 * rule, and per slot who plays it ({@code human} or {@code ai:<profile>}), what its faction starts with and the
 * entities it starts with, placed from the map's start slot: a building's footprint centred {@code dx}, {@code dz}
 * from it, {@code count} units round a point there. {@code ScenarioSetup} turns it into the admin commands a match
 * starts with, journaled as every command is.
 */
@CoreApi
public record Scenario(String map, String victory, List<Slot> slots) {
    /** One slot: its player, its starting resources in whole units, its entities. */
    @CoreApi
    public record Slot(String player, Map<String, Long> resources, List<Entity> entities) {
        public Slot {
            resources = Collections.unmodifiableSortedMap(new TreeMap<>(resources));
            entities = List.copyOf(entities);
        }

        static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.STRING.optionalFieldOf("player", "human").forGetter(Slot::player),
                        Codec.unboundedMap(Codec.STRING, Codec.LONG)
                                .optionalFieldOf("resources", Collections.emptySortedMap())
                                .forGetter(Slot::resources),
                        Entity.CODEC.listOf().fieldOf("entities").forGetter(Slot::entities))
                .apply(i, Slot::new));
    }

    /** Entities of one template: a building (count 1) or {@code count} units, {@code spread} apart. */
    @CoreApi
    public record Entity(TemplateId template, int dx, int dz, int count, double spread, int rotation) {
        static final Codec<Entity> CODEC = RecordCodecBuilder.create(i -> i.group(
                        TemplateId.CODEC.fieldOf("template").forGetter(Entity::template),
                        Codec.INT.optionalFieldOf("dx", 0).forGetter(Entity::dx),
                        Codec.INT.optionalFieldOf("dz", 0).forGetter(Entity::dz),
                        Codec.intRange(1, 200).optionalFieldOf("count", 1).forGetter(Entity::count),
                        Codec.DOUBLE.optionalFieldOf("spread", 2.0).forGetter(Entity::spread),
                        Codec.intRange(0, 3).optionalFieldOf("rotation", 0).forGetter(Entity::rotation))
                .apply(i, Entity::new));
    }

    public Scenario {
        slots = List.copyOf(slots);
    }

    public static final Codec<Scenario> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.fieldOf("map").forGetter(Scenario::map),
                    Codec.STRING.optionalFieldOf("victory", "civic_centre").forGetter(Scenario::victory),
                    Slot.CODEC.listOf().fieldOf("slots").forGetter(Scenario::slots))
            .apply(i, Scenario::new));
}
