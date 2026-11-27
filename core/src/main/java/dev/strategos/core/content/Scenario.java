package dev.strategos.core.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CivId;
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
 * starts with, journaled as every command is. A slot is written for one civilisation ({@code civ}); the entries that
 * are that civilisation's own carry {@code "civ": true}, so a slot that plays another civilisation gets that one's
 * start kit in their place (CIV-01, ADR-0028).
 */
@CoreApi
public record Scenario(String map, String victory, List<Slot> slots) {
    /**
     * One slot: its player, its starting resources in whole units, its entities, the civilisation it was written for,
     * and whether it takes its civilisation's start kit ahead of its entities when none of them is flagged.
     */
    @CoreApi
    public record Slot(String player, Map<String, Long> resources, List<Entity> entities, CivId civ, boolean kit) {
        /** A slot written for the default civilisation, without a kit. */
        public Slot(String player, Map<String, Long> resources, List<Entity> entities) {
            this(player, resources, entities, CivId.DEFAULT, false);
        }

        public Slot {
            resources = Collections.unmodifiableSortedMap(new TreeMap<>(resources));
            entities = List.copyOf(entities);
        }

        /** Whether some entity is one of the civilisation's own. */
        public boolean hasCivEntities() {
            return entities.stream().anyMatch(Entity::civ);
        }

        /** This slot with another player and resources, as the skirmish setup makes it. */
        public Slot with(String player, Map<String, Long> resources) {
            return new Slot(player, resources, entities, civ, kit);
        }

        static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.STRING.optionalFieldOf("player", "human").forGetter(Slot::player),
                        Codec.unboundedMap(Codec.STRING, Codec.LONG)
                                .optionalFieldOf("resources", Collections.emptySortedMap())
                                .forGetter(Slot::resources),
                        Entity.CODEC.listOf().fieldOf("entities").forGetter(Slot::entities),
                        CivId.CODEC.optionalFieldOf("civ", CivId.DEFAULT).forGetter(Slot::civ),
                        Codec.BOOL.optionalFieldOf("kit", false).forGetter(Slot::kit))
                .apply(i, Slot::new));
    }

    /**
     * Entities of one template: a building (count 1) or {@code count} units, {@code spread} apart; {@code civ} marks
     * one of the slot's civilisation's own entries.
     */
    @CoreApi
    public record Entity(TemplateId template, int dx, int dz, int count, double spread, int rotation, boolean civ) {
        static final Codec<Entity> CODEC = RecordCodecBuilder.create(i -> i.group(
                        TemplateId.CODEC.fieldOf("template").forGetter(Entity::template),
                        Codec.INT.optionalFieldOf("dx", 0).forGetter(Entity::dx),
                        Codec.INT.optionalFieldOf("dz", 0).forGetter(Entity::dz),
                        Codec.intRange(1, 200).optionalFieldOf("count", 1).forGetter(Entity::count),
                        Codec.DOUBLE.optionalFieldOf("spread", 2.0).forGetter(Entity::spread),
                        Codec.intRange(0, 3).optionalFieldOf("rotation", 0).forGetter(Entity::rotation),
                        Codec.BOOL.optionalFieldOf("civ", false).forGetter(Entity::civ))
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
