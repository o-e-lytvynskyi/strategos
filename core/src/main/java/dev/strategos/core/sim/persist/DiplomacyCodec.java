package dev.strategos.core.sim.persist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.diplomacy.DiplomacyState;

/** Bounded authority fields, canonicalized and fully validated before a snapshot can enter the match. */
public final class DiplomacyCodec {
    private static final int SEATS = FactionSlot.MAX + 1;
    private static final Codec<DiplomacyState.Binding> BINDING = RecordCodecBuilder.create(i -> i.group(
                    PlayerSlot.CODEC.fieldOf("player").forGetter(DiplomacyState.Binding::player),
                    FactionSlot.CODEC.fieldOf("faction").forGetter(DiplomacyState.Binding::faction))
            .apply(i, DiplomacyState.Binding::new));
    private static final Codec<DiplomacyState.Grant> GRANT = RecordCodecBuilder.create(i -> i.group(
                    FactionSlot.CODEC.fieldOf("faction").forGetter(DiplomacyState.Grant::faction),
                    PlayerSlot.CODEC.fieldOf("recipient").forGetter(DiplomacyState.Grant::recipient))
            .apply(i, DiplomacyState.Grant::new));
    private static final Codec<DiplomacyState.Snapshot> FIELDS = RecordCodecBuilder.create(i -> i.group(
                    Codec.intRange(0, FactionSlot.MAX)
                            .listOf(1, SEATS)
                            .fieldOf("teams")
                            .forGetter(DiplomacyState.Snapshot::teams),
                    BINDING.listOf(0, SEATS).fieldOf("bindings").forGetter(DiplomacyState.Snapshot::bindings),
                    GRANT.listOf(0, SEATS * SEATS).fieldOf("grants").forGetter(DiplomacyState.Snapshot::grants),
                    Codec.BOOL.fieldOf("locked").forGetter(DiplomacyState.Snapshot::teamsLocked))
            .apply(i, DiplomacyState.Snapshot::new));

    public static final Codec<DiplomacyState> STATE =
            FIELDS.comapFlatMap(DiplomacyCodec::validate, DiplomacyState::snapshot);

    private DiplomacyCodec() {}

    private static DataResult<DiplomacyState> validate(DiplomacyState.Snapshot saved) {
        try {
            return DataResult.success(DiplomacyState.restore(saved));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "invalid diplomacy: " + e.getMessage());
        }
    }
}
