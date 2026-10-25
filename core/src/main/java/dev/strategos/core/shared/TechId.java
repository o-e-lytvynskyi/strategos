package dev.strategos.core.shared;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.regex.Pattern;

/** A technology's id, {@code namespace:path}, with Minecraft's resource-location characters. */
public record TechId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    public static final Codec<TechId> CODEC = Codec.STRING.comapFlatMap(TechId::read, TechId::value);

    public TechId {
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("tech id '" + value + "' is not namespace:path");
        }
    }

    private static DataResult<TechId> read(String value) {
        return FORMAT.matcher(value).matches()
                ? DataResult.success(new TechId(value))
                : DataResult.error(() -> "tech id '" + value + "' is not namespace:path");
    }

    @Override
    public String toString() {
        return value;
    }
}
