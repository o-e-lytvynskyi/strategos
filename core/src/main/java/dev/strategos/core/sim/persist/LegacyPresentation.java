package dev.strategos.core.sim.persist;

import com.mojang.serialization.Dynamic;
import dev.strategos.core.protocol.FactionPresentation;

/** Schema 6 to 7: old matches retain their exact released appearance and configuration hash. */
final class LegacyPresentation implements MigrationStep {
    @Override
    public int from() {
        return 6;
    }

    @Override
    public <T> Dynamic<T> apply(Dynamic<T> input) {
        int factions = input.get("config").get("factions").asInt(-1);
        T encoded = PresentationCodec.STATE
                .encodeStart(input.getOps(), FactionPresentation.legacy(factions))
                .getOrThrow();
        var play = input.get("play").orElseEmptyMap();
        return input.set("play", play.set("presentation", new Dynamic<>(input.getOps(), encoded)));
    }
}
