package dev.strategos.core.shared;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** A civilisation's id, {@code namespace:path}, with Minecraft's resource-location characters. */
public record CivId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    /** The most UTF-8 bytes an id has: what {@code match_join} writes a civilisation's id in. */
    public static final int MAX_BYTES = 48;

    public static final Codec<CivId> CODEC = Codec.STRING.comapFlatMap(CivId::read, CivId::value);

    /** The civilisation every faction plays unless a match says otherwise: today's content and behaviour. */
    public static final CivId DEFAULT = new CivId("strategos:default");

    public CivId {
        String problem = problem(value);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
    }

    private static @Nullable String problem(String value) {
        if (!FORMAT.matcher(value).matches()) {
            return "civilisation id '" + value + "' is not namespace:path";
        }
        return value.length() > MAX_BYTES
                ? "civilisation id '" + value + "' is " + value.length() + " bytes; the most is " + MAX_BYTES
                : null;
    }

    private static DataResult<CivId> read(String value) {
        String problem = problem(value);
        return problem == null ? DataResult.success(new CivId(value)) : DataResult.error(() -> problem);
    }

    @Override
    public String toString() {
        return value;
    }
}
