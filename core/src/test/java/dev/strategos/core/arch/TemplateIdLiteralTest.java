package dev.strategos.core.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * CIV-05 (M10 spec 1.6 row 16): the core's rules name no template. ArchUnit does not see string literals, so this scans
 * the sources of {@code core.sim} and {@code core.client} for a {@code "strategos:building/…"}, {@code unit/…} or {@code
 * tech/…} literal; the built-in set, the placeholder blueprints and the save migrations are content and history.
 */
class TemplateIdLiteralTest {
    private static final Pattern LITERAL = Pattern.compile("\"strategos:(building|unit|tech)/");
    private static final Path MAIN = Path.of("src/main/java/dev/strategos/core");
    private static final List<String> ALLOWED = List.of("BuiltinTemplates.java", "PlaceholderBlueprints.java");

    @Test
    void noRuleNamesATemplate() throws IOException {
        List<String> found = new java.util.ArrayList<>();
        for (String dir : List.of("sim", "client")) {
            Path root = MAIN.resolve(dir);
            assertThat(root).as("the scan root exists").isDirectory();
            try (Stream<Path> files = Files.walk(root)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String name = p.getFileName().toString();
                    if (ALLOWED.contains(name) || p.toString().contains("/persist/Migrations")) {
                        continue;
                    }
                    List<String> lines = Files.readAllLines(p);
                    for (int i = 0; i < lines.size(); i++) {
                        if (LITERAL.matcher(lines.get(i)).find()) {
                            found.add(MAIN.relativize(p) + ":" + (i + 1));
                        }
                    }
                }
            }
        }
        assertThat(found)
                .as("template ids belong to a civilisation's roles, not to a rule (ADR-0028)")
                .isEmpty();
    }
}
