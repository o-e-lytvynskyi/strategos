package dev.strategos.core.shared.archfixture;

import com.google.common.base.Preconditions;

/** B1's violating fixture: Guava, which DFU puts on the compile classpath and the game does not promise. */
public final class ForeignLibraryFixture {
    private ForeignLibraryFixture() {}

    static int checked(int value) {
        Preconditions.checkArgument(value >= 0, "negative");
        return value;
    }
}
