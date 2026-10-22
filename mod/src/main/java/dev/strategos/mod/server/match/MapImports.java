package dev.strategos.mod.server.match;

import java.util.ArrayList;
import java.util.List;

/**
 * Map imports that belong to no match ({@code /strategos map import}, tests), stepped once a server tick until each is
 * done. Server thread only.
 */
public final class MapImports {
    private final List<MapImporter> running = new ArrayList<>();

    public MapImporter start(MapImporter importer) {
        running.add(importer);
        return importer;
    }

    /** Steps every import; a finished one leaves the list. */
    public void tick() {
        running.removeIf(MapImporter::step);
    }

    public List<MapImporter> running() {
        return List.copyOf(running);
    }
}
