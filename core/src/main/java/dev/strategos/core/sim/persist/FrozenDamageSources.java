package dev.strategos.core.sim.persist;

import com.mojang.serialization.Dynamic;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.combat.ShotStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Schema 4 to 5: preserve unlocked shots; infer a locked source only from a complete saved live identity. */
final class FrozenDamageSources implements MigrationStep {
    @Override
    public int from() {
        return 4;
    }

    @Override
    public <T> Dynamic<T> apply(Dynamic<T> input) {
        if (input.get("play").get("shots").get("rows").result().isEmpty()) return input;
        if (input.get("play").get("diplomacy").result().isEmpty()) {
            throw new IllegalArgumentException("schema 4 snapshot needs diplomacy");
        }
        MatchSnapshot old = MatchSnapshotCodec.CODEC.parse(input).getOrThrow();
        if (old.play().shots().rows().isEmpty()) return input;
        Map<Integer, Integer> owners = new TreeMap<>();
        for (var unit : old.units()) {
            if (owners.putIfAbsent(unit.handle(), unit.faction().value()) != null) {
                throw new IllegalArgumentException("duplicate handle in legacy damage source");
            }
        }
        for (int index = 0; index < old.buildings().size(); index++) {
            BuildingStore.Saved building = old.buildings().get(index);
            if (building.alive()
                    && owners.putIfAbsent(
                                    Handle.of(BuildingStore.BASE + index, building.generation()), building.faction())
                            != null) {
                throw new IllegalArgumentException("duplicate handle in legacy damage source");
            }
        }
        List<ShotStore.Shot> rows = new ArrayList<>();
        for (ShotStore.Shot shot : old.play().shots().rows()) {
            int source = Damage.UNATTRIBUTED;
            if (old.diplomacy().teamsLocked() && shot.shooter() != Handle.NONE) {
                source = owners.getOrDefault(shot.shooter(), Damage.UNATTRIBUTED);
                if (source == Damage.UNATTRIBUTED) {
                    throw new IllegalArgumentException("cannot recover legacy locked-shot source faction");
                }
            }
            rows.add(new ShotStore.Shot(
                    shot.id(),
                    shot.shooter(),
                    shot.target(),
                    shot.landTick(),
                    shot.landingX(),
                    shot.landingY(),
                    shot.landingZ(),
                    shot.damage().withSourceFaction(source),
                    shot.release()));
        }
        T encoded = MatchSnapshotCodec.SHOTS
                .encodeStart(
                        input.getOps(),
                        new ShotStore.Snapshot(old.play().shots().nextId(), rows))
                .getOrThrow();
        return input.set(
                "play", input.get("play").orElseEmptyMap().set("shots", new Dynamic<>(input.getOps(), encoded)));
    }
}
