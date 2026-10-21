package dev.strategos.core.shared;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** 64-bit values (seeds, hashes) as 16 lowercase hex digits, exact in JSON and NBT alike. */
public final class Hex64 {
    public static final Codec<Long> CODEC = Codec.STRING.comapFlatMap(Hex64::read, Hex64::format);

    private static final int DIGITS = 16;

    private Hex64() {}

    public static String format(long v) {
        String hex = Long.toHexString(v);
        return "0".repeat(DIGITS - hex.length()) + hex;
    }

    /** Exactly 16 lowercase hex digits. */
    public static long parse(String s) {
        if (!isHex64(s)) {
            throw new IllegalArgumentException("'" + s + "' is not 16 lowercase hex digits");
        }
        return Long.parseUnsignedLong(s, 16);
    }

    private static DataResult<Long> read(String s) {
        return isHex64(s)
                ? DataResult.success(Long.parseUnsignedLong(s, 16))
                : DataResult.error(() -> "'" + s + "' is not 16 lowercase hex digits");
    }

    private static boolean isHex64(String s) {
        if (s.length() != DIGITS) {
            return false;
        }
        for (int i = 0; i < DIGITS; i++) {
            char c = s.charAt(i);
            if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')) {
                return false;
            }
        }
        return true;
    }
}
