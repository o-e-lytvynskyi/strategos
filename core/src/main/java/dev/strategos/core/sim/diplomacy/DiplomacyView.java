package dev.strategos.core.sim.diplomacy;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import java.util.Optional;

/**
 * An immutable authority view. A control grant authorizes orders, not treasury/research/queue visibility or shared
 * vision. Adapters must derive the issuer from an authenticated binding; ADMIN is a trusted server-only issuer.
 */
@CoreApi
public interface DiplomacyView {
    int factions();

    Optional<FactionSlot> factionOf(PlayerSlot player);

    int teamOf(FactionSlot faction);

    boolean teamsLocked();

    /** False if either faction is outside this match, including two equal absent factions. */
    boolean allied(FactionSlot first, FactionSlot second);

    /** Primary ownership or an explicit grant from an ally; absent participants never gain numeric authority. */
    boolean controls(PlayerSlot player, FactionSlot faction);
}
