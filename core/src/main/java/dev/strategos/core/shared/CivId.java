package dev.strategos.core.shared;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.regex.Pattern;

/** A civilisation's id, {@code namespace:path}, with Minecraft's resource-location characters. */
public record CivId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    public static final Codec<CivId> CODEC = Codec.STRING.comapFlatMap(CivId::read, CivId::value);

    public CivId {
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("civilisation id '" + value + "' is not namespace:path");
        }
    }

    private static DataResult<CivId> read(String value) {
        return FORMAT.matcher(value).matches()
                ? DataResult.success(new CivId(value))
                : DataResult.error(() -> "civilisation id '" + value + "' is not namespace:path");
    }

    @Override
    public String toString() {
        return value;
    }
}
