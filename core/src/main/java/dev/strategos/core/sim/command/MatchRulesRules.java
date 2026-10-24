package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Resign;
import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.FactionDefeated;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.econ.EconomyRules;
import org.jspecify.annotations.Nullable;

/**
 * The match's own commands (MATCH-01): a faction resigns, and an admin changes a rule. Resigning defeats the faction at
 * once, its end checked in FINAL as a fallen centre's is; a match whose rules name no victory, or one already over,
 * refuses it. A rule change takes effect from the next step, the command journaled as every command is.
 */
final class MatchRulesRules {
    private MatchRulesRules() {}

    static final class ResignRule implements CommandRule<Resign> {
        @Override
        public CommandKind<Resign> kind() {
            return CommandKinds.RESIGN;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, Resign resign, MatchState state) {
            int faction = resign.faction().value();
            if (!envelope.issuer().isAdmin() && envelope.issuer().value() != faction) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.NOT_PERMITTED);
            }
            if (faction >= state.config().factions()) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
            }
            if (state.config().rules().play().victory() == PlayRules.Victory.NONE
                    || state.outcome().ended()
                    || state.outcome().defeated(faction)) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.UNSUPPORTED);
            }
            state.outcome().defeat(faction);
            state.outputs().add(new FactionDefeated(new FactionSlot(faction), FactionDefeated.RESIGNED));
            return Tally.acceptAll(envelope, kind(), 0);
        }
    }

    static final class RuleChangeRule implements CommandRule<RuleChange> {
        @Override
        public CommandKind<RuleChange> kind() {
            return CommandKinds.RULE_CHANGE;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, RuleChange change, MatchState state) {
            if (!envelope.issuer().isAdmin()) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.NOT_PERMITTED);
            }
            MatchRules next = changed(state.config().rules(), change.key(), change.value());
            if (next == null) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
            }
            state.setRules(next);
            return Tally.acceptAll(envelope, kind(), 0);
        }
    }

    /** {@code rules} with {@code key} set to {@code value}, or null for an unknown key or a value out of range. */
    static @Nullable MatchRules changed(MatchRules rules, String key, String value) {
        PlayRules play = rules.play();
        return switch (key) {
            case "populationMax" -> {
                int max;
                try {
                    max = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    yield null;
                }
                yield max < 1 || max > 1_000
                        ? null
                        : rules.withEconomy(new EconomyRules(rules.economy().resources(), max));
            }
            case "victory" -> {
                try {
                    yield rules.withPlay(new PlayRules(PlayRules.Victory.of(value), play.revealMap(), play.aiVision()));
                } catch (IllegalArgumentException e) {
                    yield null;
                }
            }
            case "revealMap", "aiVision" -> {
                if (!value.equals("true") && !value.equals("false")) {
                    yield null;
                }
                boolean on = value.equals("true");
                yield rules.withPlay(
                        key.equals("revealMap")
                                ? new PlayRules(play.victory(), on, play.aiVision())
                                : new PlayRules(play.victory(), play.revealMap(), on));
            }
            default -> null;
        };
    }
}
