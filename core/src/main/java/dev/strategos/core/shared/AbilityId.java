package dev.strategos.core.shared;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.regex.Pattern;

/** An ability's id, the key under {@code Abilities/} in a template (UNIT-10): lower-case letters, digits, {@code _ . -}. */
public record AbilityId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_.-]*");
    public static final Codec<AbilityId> CODEC = Codec.STRING.comapFlatMap(AbilityId::read, AbilityId::value);

    public AbilityId {
        if (!valid(value)) {
            throw new IllegalArgumentException("ability id '" + value + "' is not lower-case letters, digits, _ . -");
        }
    }

    /** Whether {@code value} is an ability id. */
    public static boolean valid(String value) {
        return FORMAT.matcher(value).matches();
    }

    private static DataResult<AbilityId> read(String value) {
        return valid(value)
                ? DataResult.success(new AbilityId(value))
                : DataResult.error(() -> "ability id '" + value + "' is not lower-case letters, digits, _ . -");
    }

    @Override
    public String toString() {
        return value;
    }
}
