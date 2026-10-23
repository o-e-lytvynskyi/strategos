package dev.strategos.mod.server.command;

import static dev.strategos.mod.server.command.StrategosCommands.template;

import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.strategos.core.content.CanonicalJson;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.content.ServerTemplates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;

/** The {@code /strategos templates} subtree (DATA-03): reloading, the last problems, one template flattened. */
final class TemplateCommands {
    private TemplateCommands() {}

    /** {@code templates reload|errors|dump <id>} (DATA-03): the data packs' templates again, the last problems, one. */
    static LiteralArgumentBuilder<CommandSourceStack> templates() {
        return Commands.literal("templates")
                .then(Commands.literal("reload").executes(ctx -> reloadTemplates(ctx.getSource())))
                .then(Commands.literal("errors").executes(ctx -> templateErrors(ctx.getSource())))
                .then(Commands.literal("dump")
                        .then(Commands.argument("template", ResourceLocationArgument.id())
                                .executes(ctx -> dumpTemplate(ctx.getSource(), template(ctx)))));
    }

    private static int reloadTemplates(CommandSourceStack source) {
        StrategosServer server = StrategosServer.get();
        ServerTemplates.Outcome outcome = server.reloadTemplates();
        if (!outcome.accepted()) {
            source.sendFailure(Component.literal(outcome.message()));
            outcome.problems().forEach(p -> source.sendFailure(Component.literal(p.toString())));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(outcome.message()), true);
        return 1;
    }

    private static int templateErrors(CommandSourceStack source) {
        ServerTemplates templates = StrategosServer.get().templates();
        source.sendSuccess(() -> Component.literal(templates.outcome()), false);
        if (templates.problems().isEmpty()) {
            source.sendSuccess(() -> Component.literal("no problems"), false);
        }
        for (TemplateProblem p : templates.problems()) {
            source.sendSuccess(() -> Component.literal(p.toString()), false);
        }
        return templates.problems().size();
    }

    private static int dumpTemplate(CommandSourceStack source, TemplateId id) {
        JsonObject json = StrategosServer.get().templates().set().flattened(id);
        if (json == null) {
            source.sendFailure(Component.literal("no template " + id));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(id + " " + CanonicalJson.write(json)), false);
        return 1;
    }
}
