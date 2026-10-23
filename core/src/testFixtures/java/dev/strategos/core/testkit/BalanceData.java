package dev.strategos.core.testkit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.balance.BalanceSheet;
import dev.strategos.core.shared.TemplateId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The mod's content as the balance sheet reads it (DATA-04), straight from a data directory ({@code
 * mod/src/main/resources/data}): every {@code <ns>/strategos/templates/**.json} as a raw template (without NeoForge's
 * conditions), every {@code <ns>/data_maps/block/resource_yield.json} entry, every {@code
 * <ns>/strategos/balance/provenance.json} merged, and {@code
 * strategos/strategos/balance/targets.json}. {@link #main} writes {@code sheet.md} and {@code sheet.csv}
 * ({@code :core:balanceSheet}).
 */
public final class BalanceData {
    private static final String CONDITIONS = "neoforge:conditions";

    private final List<RawTemplate> raws;
    private final Map<String, Map<String, String>> provenance;
    private final List<BalanceSheet.Target> targets;
    private final List<BalanceSheet.Yield> yields;

    private BalanceData(
            List<RawTemplate> raws,
            Map<String, Map<String, String>> provenance,
            List<BalanceSheet.Target> targets,
            List<BalanceSheet.Yield> yields) {
        this.raws = raws;
        this.provenance = provenance;
        this.targets = targets;
        this.yields = yields;
    }

    public static BalanceData read(Path data) {
        List<RawTemplate> raws = new ArrayList<>();
        Map<String, Map<String, String>> provenance = new TreeMap<>();
        List<BalanceSheet.Target> targets = new ArrayList<>();
        List<BalanceSheet.Yield> yields = new ArrayList<>();
        try (Stream<Path> namespaces = Files.list(data)) {
            for (Path ns : namespaces.sorted().toList()) {
                Path templates = ns.resolve("strategos/templates");
                if (Files.isDirectory(templates)) {
                    try (Stream<Path> files = Files.walk(templates)) {
                        for (Path f : files.filter(p -> p.toString().endsWith(".json"))
                                .sorted()
                                .toList()) {
                            String rel = templates.relativize(f).toString().replace('\\', '/');
                            String id = ns.getFileName() + ":" + rel.substring(0, rel.length() - ".json".length());
                            JsonObject json = parse(f).getAsJsonObject();
                            json.remove(CONDITIONS);
                            raws.add(new RawTemplate(
                                    new TemplateId(id), data.relativize(f).toString(), json));
                        }
                    }
                }
                Path map = ns.resolve("data_maps/block/resource_yield.json");
                if (Files.isRegularFile(map)) {
                    for (Map.Entry<String, JsonElement> e : parse(map)
                            .getAsJsonObject()
                            .getAsJsonObject("values")
                            .entrySet()) {
                        JsonObject v = e.getValue().getAsJsonObject();
                        yields.add(new BalanceSheet.Yield(
                                e.getKey(),
                                v.get("kind").getAsString(),
                                v.has("resource") ? v.get("resource").getAsString() : "",
                                v.has("subtype") ? v.get("subtype").getAsString() : "",
                                v.has("amount") ? v.get("amount").getAsBigDecimal() : BigDecimal.ZERO,
                                v.has("regrow") ? v.get("regrow").getAsBigDecimal() : BigDecimal.ZERO));
                    }
                }
                Path notes = ns.resolve("strategos/balance/provenance.json");
                if (Files.isRegularFile(notes)) {
                    for (Map.Entry<String, JsonElement> t :
                            parse(notes).getAsJsonObject().entrySet()) {
                        Map<String, String> paths = provenance.computeIfAbsent(t.getKey(), k -> new TreeMap<>());
                        t.getValue()
                                .getAsJsonObject()
                                .entrySet()
                                .forEach(p -> paths.put(p.getKey(), p.getValue().getAsString()));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Path goals = data.resolve("strategos/strategos/balance/targets.json");
        if (Files.isRegularFile(goals)) {
            for (JsonElement t : parse(goals).getAsJsonObject().getAsJsonArray("targets")) {
                JsonObject o = t.getAsJsonObject();
                targets.add(new BalanceSheet.Target(
                        o.get("event").getAsString(),
                        o.get("by").getAsString(),
                        o.get("note").getAsString()));
            }
        }
        return new BalanceData(List.copyOf(raws), provenance, List.copyOf(targets), List.copyOf(yields));
    }

    public List<RawTemplate> raws() {
        return raws;
    }

    public Map<String, Map<String, String>> provenance() {
        return provenance;
    }

    public List<BalanceSheet.Target> targets() {
        return targets;
    }

    public List<BalanceSheet.Yield> yields() {
        return yields;
    }

    /** The compiled set and its sheet. */
    public BalanceSheet sheet() {
        return BalanceSheet.of(compile(), raws, provenance, targets, yields);
    }

    public TemplateCompiler.Result compile() {
        return TemplateCompiler.compile(raws);
    }

    private static JsonElement parse(Path f) {
        try {
            return JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code <data dir> <out dir>}: writes the sheet; fails on a template problem or a sheet problem. */
    public static void main(String[] args) throws IOException {
        BalanceData data = read(Path.of(args[0]));
        TemplateCompiler.Result result = data.compile();
        if (!result.problems().isEmpty()) {
            throw new IllegalStateException("template problems: " + result.problems());
        }
        BalanceSheet sheet = BalanceSheet.of(result, data.raws, data.provenance, data.targets, data.yields);
        Path out = Path.of(args[1]);
        Files.createDirectories(out);
        Files.writeString(out.resolve("sheet.md"), sheet.markdown(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("sheet.csv"), sheet.csv(), StandardCharsets.UTF_8);
        if (!sheet.problems().isEmpty()) {
            throw new IllegalStateException("balance sheet problems:\n  " + String.join("\n  ", sheet.problems()));
        }
    }
}
