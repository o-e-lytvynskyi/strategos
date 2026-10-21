package dev.strategos.core.shared;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.regex.Pattern;

/** A unit template's id, {@code namespace:path}, with Minecraft's resource-location characters. */
public record TemplateId(String value) {
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    public static final Codec<TemplateId> CODEC = Codec.STRING.comapFlatMap(TemplateId::read, TemplateId::value);

    public TemplateId {
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("template id '" + value + "' is not namespace:path");
        }
    }

    private static DataResult<TemplateId> read(String value) {
        return FORMAT.matcher(value).matches()
                ? DataResult.success(new TemplateId(value))
                : DataResult.error(() -> "template id '" + value + "' is not namespace:path");
    }

    @Override
    public String toString() {
        return value;
    }
}
