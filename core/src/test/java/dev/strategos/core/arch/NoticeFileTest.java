package dev.strategos.core.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * PATH-06.4 and ADR-0005: the adapted RVO2 code keeps its licence. {@code NOTICE} carries RVO2's copyright and licence
 * and names the adapted file, a copy of the Apache License 2.0 sits beside it, and the adapted file's header keeps the
 * copyright and lists the changes.
 */
class NoticeFileTest {
    private static final String COPYRIGHT = "Copyright 2008 University of North Carolina at Chapel Hill";
    private static final String ORCA = "core/src/main/java/dev/strategos/core/sim/move/Orca.java";

    @Test
    void noticeCarriesRvo2() throws IOException {
        String notice = read("NOTICE");

        assertThat(notice)
                .contains("RVO2 Library 2.0.3")
                .contains(COPYRIGHT)
                .contains("Licensed under the Apache License, Version 2.0")
                .contains("licenses/Apache-2.0.txt")
                .contains(ORCA);
        assertThat(read("licenses/Apache-2.0.txt"))
                .contains("Apache License")
                .contains("Version 2.0, January 2004")
                .contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION");
    }

    @Test
    void theAdaptedFileKeepsItsHeader() throws IOException {
        String orca = read(ORCA);

        assertThat(orca.substring(0, orca.indexOf("package ")))
                .contains("Adapted from RVO2 Library 2.0.3")
                .contains(COPYRIGHT)
                .contains("Licensed under the Apache License, Version 2.0")
                .contains("Changes from the original");
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(System.getProperty("strategos.repoRoot"), path), StandardCharsets.UTF_8);
    }
}
