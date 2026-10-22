package dev.strategos.core.testkit;

import com.google.gson.JsonParser;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.shared.TemplateId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Template sets for L1 from test resources: {@code <root>/<namespace>/<path>.json} is the template {@code
 * <namespace>:<path>}, as {@code data/<namespace>/strategos/templates/<path>.json} is in a data pack.
 */
public final class TestTemplates {
    private TestTemplates() {}

    /** Every template under the classpath directory {@code root}, in path order. */
    public static List<RawTemplate> load(String root) {
        URL url = TestTemplates.class.getClassLoader().getResource(root);
        if (url == null) {
            throw new IllegalArgumentException("no test resource directory " + root);
        }
        Path dir;
        try {
            dir = Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        List<RawTemplate> raws = new ArrayList<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file :
                    files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String relative = dir.relativize(file).toString().replace('\\', '/');
                int slash = relative.indexOf('/');
                String id = relative.substring(0, slash) + ":"
                        + relative.substring(slash + 1, relative.length() - ".json".length());
                raws.add(new RawTemplate(
                        new TemplateId(id),
                        relative,
                        JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                                .getAsJsonObject()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return raws;
    }

    public static TemplateCompiler.Result compile(String root) {
        return TemplateCompiler.compile(load(root));
    }
}
