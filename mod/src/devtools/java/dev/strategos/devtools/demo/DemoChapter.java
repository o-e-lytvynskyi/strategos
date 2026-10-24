package dev.strategos.devtools.demo;

import java.util.List;

/**
 * One mechanic shown in the demo: a short scene on a plot of its own with its own match, scripted from setup to the
 * last line. A chapter is told by its id on the command line ({@code -Pdemo=crowd,melee}) and by its groups ({@code
 * -Pdemo=m4}); bumping its version when the scene changes shows it again to {@code -Pdemo=new}. A new chapter is a
 * class here and a line in {@link DemoCatalog#CHAPTERS}.
 */
public interface DemoChapter {
    /** Lower case and dashes: the name on the command line. */
    String id();

    /** The title on the screen, English. */
    String title();

    /** Bumped when the scene changes, so a viewer who saw it sees it again. */
    default int version() {
        return 1;
    }

    /** The milestone that brought it ({@code m1} ..), then the topics it belongs to. */
    List<String> groups();

    /** Its script: the plot's blocks, the match, the orders, the camera and the lines. */
    void script(DemoScript script);
}
