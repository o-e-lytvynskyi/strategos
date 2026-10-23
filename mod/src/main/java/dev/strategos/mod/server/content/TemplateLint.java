package dev.strategos.mod.server.content;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

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
            if (t.isBuilding()) {
                Blueprint bp = set.blueprint(t.netIndex());
                if (bp != null) {
                    problems.addAll(palette(bp, file));
                }
                continue;
            }
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

    /**
     * A blueprint's palette (BLD-01.2): every state parses, and none burns, since fire would remove a building's blocks
     * behind the core's back (04 §11.9); nor may a state carry a block entity, which M3's buildings never need.
     */
    static List<TemplateProblem> palette(Blueprint bp, String file) {
        List<TemplateProblem> problems = new ArrayList<>();
        for (String text : bp.palette()) {
            BlockState state;
            try {
                state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false)
                        .blockState();
            } catch (CommandSyntaxException e) {
                problems.add(TemplateProblem.error(file, "/Blueprint/palette", "not a block state: " + text));
                continue;
            }
            if (state.isFlammable(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP)) {
                problems.add(TemplateProblem.error(
                        file, "/Blueprint/palette", bp.id() + " holds " + text + ", which burns"));
            }
            if (state.hasBlockEntity()) {
                problems.add(TemplateProblem.error(
                        file, "/Blueprint/palette", bp.id() + " holds " + text + ", a block entity"));
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
