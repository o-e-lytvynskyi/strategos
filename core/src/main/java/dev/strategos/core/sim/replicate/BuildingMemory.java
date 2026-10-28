package dev.strategos.core.sim.replicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.build.BuildingStore;
import java.util.ArrayList;
import java.util.List;

/**
 * One viewer's immutable observed buildings. The event watermark prevents stale events resurrecting cleared sites.
 * AFTER-M6: wire match/viewer-scoped saves and hashing against the merged snapshot schema.
 * AFTER-M7: authorize recipients and positively observe remembered footprints before removing a mirage.
 */
@CoreApi
public record BuildingMemory(long updatedAt, List<Seen> buildings) {
    public static final int MAX_BUILDINGS = 4096;
    private static final Codec<Long> TICK = Codec.LONG.validate(
            tick -> tick >= 0 ? DataResult.success(tick) : DataResult.error(() -> "negative observation tick"));
    public static final Codec<BuildingMemory> CODEC = RecordCodecBuilder.<Saved>create(i -> i.group(
                            TICK.fieldOf("updatedAt").forGetter(Saved::updatedAt),
                            Seen.CODEC
                                    .listOf(0, MAX_BUILDINGS)
                                    .fieldOf("buildings")
                                    .forGetter(Saved::buildings))
                    .apply(i, Saved::new))
            .comapFlatMap(BuildingMemory::read, memory -> new Saved(memory.updatedAt(), memory.buildings()));

    public BuildingMemory {
        if (updatedAt < 0 || buildings.size() > MAX_BUILDINGS) {
            throw new IllegalArgumentException("invalid building memory watermark or size");
        }
        List<Seen> sorted = new ArrayList<>(buildings);
        sorted.sort((a, b) -> Integer.compareUnsigned(a.handle(), b.handle()));
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).tick() > updatedAt) {
                throw new IllegalArgumentException("building observation after snapshot watermark");
            }
            if (i > 0 && sorted.get(i - 1).handle() == sorted.get(i).handle()) {
                throw new IllegalArgumentException("duplicate remembered building handle");
            }
        }
        buildings = List.copyOf(sorted);
    }

    public static BuildingMemory empty() {
        return new BuildingMemory(0, List.of());
    }

    /** Replace only with a fresh positive observation, never a read of the hidden world. */
    public BuildingMemory observe(Seen observed) {
        requireFresh(observed.tick());
        List<Seen> out = new ArrayList<>();
        for (Seen old : buildings) {
            if (old.handle() != observed.handle()) {
                out.add(old);
            }
        }
        out.add(observed);
        return new BuildingMemory(observed.tick(), out);
    }

    /** Positive empty-site observation; omission from the current live building list is not evidence. */
    public BuildingMemory scoutedEmpty(int handle, long tick) {
        if (!Seen.validHandle(handle)) {
            throw new IllegalArgumentException("invalid empty-site building handle");
        }
        requireFresh(tick);
        List<Seen> out = new ArrayList<>();
        for (Seen old : buildings) {
            if (old.handle() != handle) {
                out.add(old);
            }
        }
        return new BuildingMemory(tick, out);
    }

    private void requireFresh(long tick) {
        if (tick < updatedAt) {
            throw new IllegalArgumentException("observation predates memory watermark");
        }
    }

    private static DataResult<BuildingMemory> read(Saved saved) {
        try {
            return DataResult.success(new BuildingMemory(saved.updatedAt(), saved.buildings()));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "invalid building memory: " + e.getMessage());
        }
    }

    private record Saved(long updatedAt, List<Seen> buildings) {}

    /** Last observed stable identity, owner, placement and health in 255ths; no live queue or world references. */
    public record Seen(
            int handle, TemplateId template, int owner, int x, int y, int z, int rotation, int health, long tick) {
        private static final Codec<Integer> BUILDING_HANDLE = Codec.INT.validate(handle ->
                validHandle(handle) ? DataResult.success(handle) : DataResult.error(() -> "invalid building handle"));
        private static final Codec<Long> OBSERVED_TICK = Codec.LONG.validate(
                value -> value >= 0 ? DataResult.success(value) : DataResult.error(() -> "negative observation tick"));
        public static final Codec<Seen> CODEC = RecordCodecBuilder.create(i -> i.group(
                        BUILDING_HANDLE.fieldOf("handle").forGetter(Seen::handle),
                        TemplateId.CODEC.fieldOf("template").forGetter(Seen::template),
                        Codec.intRange(0, FactionSlot.MAX).fieldOf("owner").forGetter(Seen::owner),
                        Codec.INT.fieldOf("x").forGetter(Seen::x),
                        Codec.INT.fieldOf("y").forGetter(Seen::y),
                        Codec.INT.fieldOf("z").forGetter(Seen::z),
                        Codec.intRange(0, 3).fieldOf("rotation").forGetter(Seen::rotation),
                        Codec.intRange(0, 255).fieldOf("health").forGetter(Seen::health),
                        OBSERVED_TICK.fieldOf("tick").forGetter(Seen::tick))
                .apply(i, Seen::new));

        private static boolean validHandle(int handle) {
            return Handle.generation(handle) > 0 && Handle.index(handle) >= BuildingStore.BASE;
        }

        public Seen {
            if (!validHandle(handle)
                    || owner < 0
                    || owner > FactionSlot.MAX
                    || rotation < 0
                    || rotation > 3
                    || health < 0
                    || health > 255
                    || tick < 0) {
                throw new IllegalArgumentException("invalid observed building");
            }
        }
    }
}
