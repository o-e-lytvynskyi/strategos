package dev.strategos.core.sim.ai;

/** One part of a computer player (04 §14's managers): it reads the view and sends commands, within the budget. */
public interface AiModule {
    void think(AiView view, AiBudget budget, CommandSink sink);
}
