package dev.strategos.core.protocol;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.AbilityId;
import dev.strategos.core.shared.Hasher;

/**
 * Have own {@code units} use the data-defined {@code ability} on {@code target} (UNIT-10, 04 §7.6): each unit that has
 * it walks into the ability's range and fires it once it is off cooldown and its faction can pay. Abilities never add
 * command types: this one carries the id.
 */
public record UseAbility(UnitSet units, AbilityId ability, Target target, QueueMode mode) implements Command {
    static final MapCodec<UseAbility> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    UnitSet.CODEC.fieldOf("units").forGetter(UseAbility::units),
                    AbilityId.CODEC.fieldOf("ability").forGetter(UseAbility::ability),
                    Target.CODEC.fieldOf("target").forGetter(UseAbility::target),
                    QueueMode.CODEC.fieldOf("mode").forGetter(UseAbility::mode))
            .apply(i, UseAbility::new));

    /** Units, the ability's id, the target, the queue mode; wire id 35. */
    static final WireCodec<UseAbility> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, UseAbility value) {
            WireCodecs.UNIT_SET.write(out, value.units());
            WireCodecs.ABILITY_ID.write(out, value.ability());
            Target.WIRE.write(out, value.target());
            WireCodecs.QUEUE_MODE.write(out, value.mode());
        }

        @Override
        public UseAbility read(WireReader in) {
            return new UseAbility(
                    WireCodecs.UNIT_SET.read(in),
                    WireCodecs.ABILITY_ID.read(in),
                    Target.WIRE.read(in),
                    WireCodecs.QUEUE_MODE.read(in));
        }

        @Override
        public int maxSize() {
            return WireCodecs.UNIT_SET.maxSize()
                    + WireCodecs.ABILITY_ID.maxSize()
                    + Target.WIRE.maxSize()
                    + WireCodecs.QUEUE_MODE.maxSize();
        }
    };

    /** Replaces the units' orders. */
    public static UseAbility replace(UnitSet units, AbilityId ability, Target target) {
        return new UseAbility(units, ability, target, QueueMode.REPLACE);
    }

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 35); // the command type: ... 33 UpgradeBuilding, 34 PlaceWall, 35 UseAbility
        units.hashInto(h);
        h.putString(ability.value());
        target.hashInto(h);
        h.putString(mode.id());
    }
}
