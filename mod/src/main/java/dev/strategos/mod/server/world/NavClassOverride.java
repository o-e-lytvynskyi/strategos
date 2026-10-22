package dev.strategos.mod.server.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.sim.nav.BlockClass;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A {@code strategos:nav_class} data map entry (04 §8.5): a block whose collision shape misleads, other mods' blocks
 * included, gets its collision top replaced and flags set or cleared, as in
 * {@code {"top": 16, "set": ["standable"], "clear": ["leaves"]}}.
 */
public record NavClassOverride(Optional<Integer> top, List<String> set, List<String> clear) {
    public static final Codec<NavClassOverride> CODEC = RecordCodecBuilder.<NavClassOverride>create(i -> i.group(
                            Codec.intRange(0, 24).optionalFieldOf("top").forGetter(NavClassOverride::top),
                            Codec.STRING
                                    .listOf()
                                    .optionalFieldOf("set", List.of())
                                    .forGetter(NavClassOverride::set),
                            Codec.STRING
                                    .listOf()
                                    .optionalFieldOf("clear", List.of())
                                    .forGetter(NavClassOverride::clear))
                    .apply(i, NavClassOverride::new))
            .validate(NavClassOverride::validate);

    /** The derived class with this override applied: top first, then the flags set, then those cleared. */
    public short apply(short derived) {
        int top = this.top.orElse(BlockClass.top(derived));
        int flags = derived & ~BlockClass.TOP_MASK;
        for (String name : set) {
            flags |= flag(name);
        }
        for (String name : clear) {
            flags &= ~flag(name);
        }
        return BlockClass.of(top, flags);
    }

    private static DataResult<NavClassOverride> validate(NavClassOverride o) {
        for (String name : o.set) {
            if (flagOrZero(name) == 0) {
                return DataResult.error(() -> "unknown nav flag '" + name + "'");
            }
        }
        for (String name : o.clear) {
            if (flagOrZero(name) == 0) {
                return DataResult.error(() -> "unknown nav flag '" + name + "'");
            }
        }
        return DataResult.success(o);
    }

    private static int flag(String name) {
        int flag = flagOrZero(name);
        if (flag == 0) {
            throw new IllegalArgumentException("unknown nav flag '" + name + "'");
        }
        return flag;
    }

    private static int flagOrZero(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "standable" -> BlockClass.STANDABLE;
            case "passable" -> BlockClass.PASSABLE;
            case "water" -> BlockClass.WATER;
            case "lava" -> BlockClass.LAVA;
            case "climbable" -> BlockClass.CLIMBABLE;
            case "door" -> BlockClass.DOOR;
            case "hazard" -> BlockClass.HAZARD;
            case "leaves" -> BlockClass.LEAVES;
            case "slow" -> BlockClass.SLOW;
            default -> 0;
        };
    }
}
