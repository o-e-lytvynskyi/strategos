package dev.strategos.gametest.suites;

import com.google.gson.GsonBuilder;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.MapManifest;
import dev.strategos.core.content.StartSlot;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.content.MapPackageLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import net.minecraft.resources.ResourceLocation;

/** A four-start engine fixture using the actual shipped M6 terrain and importer, with four independent workers. */
final class LobbyFourMap {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("strategos", "lobby_four_fixture");

    private LobbyFourMap() {}

    static void install(StrategosServer server) {
        var original = MapPackageLoader.load(
                        server.server(), ResourceLocation.fromNamespaceAndPath("strategos", "first_skirmish"))
                .manifest();
        var starts = new ArrayList<StartSlot>();
        for (StartSlot s : original.startSlots()) {
            // Both positions remain inside each original centre's cleared footprint, eight blocks apart.
            starts.add(new StartSlot(s.x(), s.y(), s.z() - 4, s.yaw()));
            starts.add(new StartSlot(s.x(), s.y(), s.z() + 4, s.yaw()));
        }
        var manifest = new MapManifest(
                ID.toString(),
                original.sizeX(),
                original.sizeZ(),
                original.minY(),
                original.height(),
                original.baseBlock(),
                original.tileSize(),
                original.palette(),
                starts,
                original.waypoints(),
                original.version(),
                original.terrainHash(),
                original.terrainJobs());
        var root = MapPackageLoader.exports(server.server()).resolve(ID.getNamespace());
        var tiles = root.resolve(ID.getPath());
        try {
            Files.createDirectories(tiles);
            for (int z = 0; z < original.tilesZ(); z++) {
                for (int x = 0; x < original.tilesX(); x++) {
                    String file = MapManifest.tileName(x, z) + ".nbt";
                    var id = ResourceLocation.fromNamespaceAndPath(
                            "strategos", "structure/strategos/maps/first_skirmish/" + file);
                    try (var in = server.server()
                            .getResourceManager()
                            .getResource(id)
                            .orElseThrow()
                            .open()) {
                        Files.copy(in, tiles.resolve(file), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            var json = MapManifest.CODEC.encodeStart(JsonOps.INSTANCE, manifest).getOrThrow();
            Files.writeString(
                    root.resolve(ID.getPath() + ".json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(json));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
