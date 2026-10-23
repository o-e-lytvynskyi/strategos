package dev.strategos.mod.server.content;

import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

/**
 * What a compiled set may only learn on the server (spec §6.6): each body's entity type registered, its model one M1
 * draws, and its skin one M1 ships (fixed lists until UNIT-09's assets grow). The core compiler has already checked
 * the keys and types; M1's templates name no items, blocks or productions yet.
 */
public final class TemplateLint {
    private TemplateLint() {}

    /** The models M1's renderer draws. */
    static List<String> models() {
        return List.of("humanoid");
    }

    /** The skins M1 ships, and none. */
    static List<String> skins() {
        return List.of("", "strategos:units/debug_walker", "strategos:units/infantry", "strategos:units/worker");
    }

    /** The problems of {@code set} against the server's registries; {@code files} gives each template's file. */
    public static List<TemplateProblem> check(TemplateSet set, RegistryAccess registries, FileOf files) {
        List<TemplateProblem> problems = new ArrayList<>();
        var entities = registries.registryOrThrow(Registries.ENTITY_TYPE);
        for (UnitTemplate t : set.all()) {
            String file = files.of(t);
            ResourceLocation type = ResourceLocation.tryParse(t.bodyType());
            if (type == null || !entities.containsKey(type)) {
                problems.add(
                        TemplateProblem.error(file, "/Body/entityType", "unknown entity type '" + t.bodyType() + "'"));
            }
            if (!models().contains(t.model())) {
                problems.add(TemplateProblem.error(
                        file, "/Body/model", "unknown model '" + t.model() + "'; M1 draws " + models()));
            }
            if (!skins().contains(t.skin())) {
                problems.add(TemplateProblem.error(file, "/Body/skin", "unknown skin '" + t.skin() + "'"));
            }
        }
        return problems;
    }

    /** The file a template came from, for its problems. */
    @FunctionalInterface
    public interface FileOf {
        String of(UnitTemplate template);
    }
}
