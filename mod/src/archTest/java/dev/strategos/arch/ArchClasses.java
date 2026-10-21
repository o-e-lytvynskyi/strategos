package dev.strategos.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** The classes the mod rules check: the shipped classes of mod and core, whose directories the build passes. */
final class ArchClasses {
    private ArchClasses() {}

    static JavaClasses load() {
        String paths = System.getProperty("strategos.arch.classes");
        if (paths == null || paths.isBlank()) {
            throw new IllegalStateException(
                    "strategos.arch.classes is not set: run the rules through ./gradlew :mod:archTest");
        }
        List<Path> dirs = Arrays.stream(paths.split(File.pathSeparator))
                .filter(p -> !p.isBlank())
                .map(Path::of)
                .filter(Files::isDirectory)
                .toList();
        if (dirs.isEmpty()) {
            throw new IllegalStateException("no class directory in strategos.arch.classes: " + paths);
        }
        return new ClassFileImporter().importPaths(dirs);
    }

    /** A rule's own violating fixtures, which it must flag. */
    static JavaClasses fixtures(Class<?>... fixtures) {
        return new ClassFileImporter().importClasses(fixtures);
    }
}
