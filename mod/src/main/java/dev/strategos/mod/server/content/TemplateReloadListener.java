package dev.strategos.mod.server.content;

import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.mod.StrategosServer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Reloads the templates with the data packs (DATA-03, spec §6.6), added in {@code AddReloadListenerEvent}
 * (AddReloadListenerEvent.java:44) by {@code Strategos#reloadListeners}: {@code prepare} reads and parses the files off the main thread, {@code apply} keeps
 * those whose conditions hold, under NeoForge's conditional ops, and hands them to the server, which compiles, lints
 * and swaps. At the server's start no server is up yet when this applies; the server loads the same files itself
 * ({@link StrategosServer}).
 */
public final class TemplateReloadListener extends SimplePreparableReloadListener<TemplateFiles.Read> {
    /** Its name in the engine-surface inventory and in the reload's profiler. */
    public static final String NAME = "strategos:templates";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected TemplateFiles.Read prepare(ResourceManager resources, ProfilerFiller profiler) {
        return TemplateFiles.read(resources);
    }

    @Override
    protected void apply(TemplateFiles.Read read, ResourceManager resources, ProfilerFiller profiler) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null) {
            return;
        }
        List<TemplateProblem> problems = new ArrayList<>(read.problems());
        List<RawTemplate> raws = TemplateFiles.raws(read, makeConditionalOps(), problems);
        server.loadTemplates(raws, problems);
    }
}
