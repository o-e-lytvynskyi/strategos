package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;

import com.google.gson.GsonBuilder;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.MapManifest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.content.MapPackageLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import net.minecraft.resources.ResourceLocation;

/** Metadata-only reads retain exported-map precedence; actual package creation still validates every tile. */
final class LobbyMapHeaders {
    private LobbyMapHeaders() {}

    static void verify(StrategosServer server) {
        var id = ResourceLocation.fromNamespaceAndPath("strategos", "first_skirmish");
        var original = MapPackageLoader.manifest(server.server(), id);
        var exported = new MapManifest(
                original.id(),
                original.sizeX(),
                original.sizeZ(),
                original.minY(),
                original.height(),
                original.baseBlock(),
                original.tileSize(),
                original.palette(),
                original.startSlots(),
                original.waypoints(),
                original.version() + 1,
                original.terrainHash(),
                original.terrainJobs());
        var root = MapPackageLoader.exports(server.server()).resolve(id.getNamespace());
        var file = root.resolve(id.getPath() + ".json");
        check(
                !Files.exists(file) && !Files.exists(root.resolve(id.getPath())),
                "map-header fixture would replace an export");
        try {
            Files.createDirectories(root);
            try {
                var json = MapManifest.CODEC
                        .encodeStart(JsonOps.INSTANCE, exported)
                        .getOrThrow();
                Files.writeString(file, new GsonBuilder().create().toJson(json));
                check(
                        MapPackageLoader.manifest(server.server(), id).equals(exported),
                        "metadata read ignored world export");
                boolean missingTile = false;
                try {
                    MapPackageLoader.load(server.server(), id);
                } catch (IllegalArgumentException expected) {
                    missingTile = String.valueOf(expected.getMessage()).contains("cannot read tile");
                }
                check(missingTile, "full package load skipped missing exported tiles or fell back to resource tiles");
                Files.writeString(file, "{}");
                boolean invalidManifest = false;
                try {
                    MapPackageLoader.manifest(server.server(), id);
                } catch (IllegalArgumentException expected) {
                    invalidManifest = true;
                }
                check(invalidManifest, "malformed world manifest fell back to packaged resource");
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        check(
                MapPackageLoader.manifest(server.server(), id).equals(original),
                "header fixture did not restore resource lookup");
    }
}
