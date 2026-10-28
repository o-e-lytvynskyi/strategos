package dev.strategos.devtools.demo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The demo's chapters in the order they are shown, and which of them a run shows. A selection is {@code all}, {@code
 * new} (the chapters whose id and version the seen file lacks) or a comma list of ids and groups; a skip list of ids
 * and groups takes chapters out of it. The seen file is the viewer's, outside the repository: a line {@code <id>
 * <version>} for each chapter shown to its end.
 */
public final class DemoCatalog {
    /** Every chapter, M1's first. */
    public static final List<DemoChapter> CHAPTERS = List.of(
            new CrowdChapter(),
            new MeleeChapter(),
            new GatherChapter(),
            new LivestockChapter(),
            new DepositChapter(),
            new FieldChapter(),
            new BuildersChapter(),
            new SlopeChapter(),
            new RepairChapter(),
            new ProtectionChapter(),
            new RallyChapter(),
            new BotChapter(),
            new ArchersChapter(),
            new HighGroundChapter(),
            new StancesChapter(),
            new HorsemenChapter(),
            new FocusSpreadChapter(),
            new GarrisonChapter(),
            new AttackLinesChapter());

    private DemoCatalog() {}

    /** The chapters {@code select} names less those {@code skip} names, in the catalog's order; refuses unknown names. */
    public static List<DemoChapter> pick(List<DemoChapter> chapters, String select, String skip, Set<String> seen) {
        Set<String> skipped = names(chapters, skip);
        List<DemoChapter> out = new ArrayList<>();
        String s = select.trim().toLowerCase(Locale.ROOT);
        Set<String> chosen = s.equals("all") || s.equals("new") ? Set.of() : names(chapters, s);
        for (DemoChapter c : chapters) {
            boolean in = switch (s) {
                case "all" -> true;
                case "new" -> !seen.contains(key(c));
                default -> matches(c, chosen);
            };
            if (in && !matches(c, skipped)) {
                out.add(c);
            }
        }
        return out;
    }

    /** {@code <id> <version>}: a chapter's line in the seen file. */
    public static String key(DemoChapter c) {
        return c.id() + " " + c.version();
    }

    private static boolean matches(DemoChapter c, Set<String> names) {
        return names.contains(c.id()) || c.groups().stream().anyMatch(names::contains);
    }

    /** The comma list's names, each an id or a group of {@code chapters}. */
    private static Set<String> names(List<DemoChapter> chapters, String list) {
        Set<String> known = new HashSet<>();
        for (DemoChapter c : chapters) {
            known.add(c.id());
            known.addAll(c.groups());
        }
        Set<String> out = new LinkedHashSet<>();
        for (String n : list.toLowerCase(Locale.ROOT).split(",", -1)) {
            String name = n.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (!known.contains(name)) {
                throw new IllegalArgumentException("no demo chapter or group " + name + "; known: " + known);
            }
            out.add(name);
        }
        return out;
    }

    /** The seen file's lines; none when it does not exist. */
    public static Set<String> seen(Path file) {
        try {
            return Files.exists(file)
                    ? new HashSet<>(Files.readAllLines(file, StandardCharsets.UTF_8))
                    : new HashSet<>();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    /** Adds the chapter's line to the seen file. */
    public static void markSeen(Path file, DemoChapter c) {
        Set<String> lines = new LinkedHashSet<>(seen(file));
        lines.add(key(c));
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    /** The catalog as lines: id, version, groups, title. */
    public static List<String> describe(List<DemoChapter> chapters) {
        return chapters.stream()
                .map(c -> String.format(
                        Locale.ROOT,
                        "%-12s v%d  %-28s %s",
                        c.id(),
                        c.version(),
                        String.join(",", c.groups()),
                        c.title()))
                .toList();
    }

    /** Where the seen file lives: {@code -Pdemo.seen}, else {@code ~/.strategos/demo-seen}. */
    public static Path seenFile() {
        String p = System.getProperty("strategos.demo.seen", "");
        return p.isBlank() ? Path.of(System.getProperty("user.home"), ".strategos", "demo-seen") : Path.of(p);
    }
}
