package dev.strategos.core.client.hud;

import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.MatchPresentation;
import dev.strategos.core.protocol.SetControl;
import dev.strategos.core.shared.PlayerSlot;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Named request actions only; no outgoing grants or foreign private state are guessed. */
public record ControlSharing(List<Row> rows) {
    public record Row(PlayerSlot player, String name, int faction) {}

    public ControlSharing {
        rows = List.copyOf(rows);
    }

    public static ControlSharing of(@Nullable MatchJoin joined, @Nullable MatchPresentation view, boolean active) {
        if (!active
                || joined == null
                || joined.slot().isAdmin()
                || view == null
                || joined.matchId() != view.matchId()
                || joined.factions() != view.factions().styles().size()) return new ControlSharing(List.of());
        boolean present = view.participants().stream()
                .anyMatch(p ->
                        p.player().equals(joined.slot()) && p.connected() && p.faction() == joined.primaryFaction());
        if (!present) return new ControlSharing(List.of());
        return new ControlSharing(view.participants().stream()
                .filter(p -> p.connected()
                        && !p.player().equals(joined.slot())
                        && p.faction() != joined.primaryFaction()
                        && joined.alliedFaction(p.faction()))
                .map(p -> new Row(p.player(), p.name(), p.faction()))
                .toList());
    }

    public SetControl intent(PlayerSlot recipient, boolean granted) {
        if (rows.stream().noneMatch(row -> row.player().equals(recipient)))
            throw new IllegalArgumentException("no eligible named ally");
        return new SetControl(recipient, granted);
    }
}
