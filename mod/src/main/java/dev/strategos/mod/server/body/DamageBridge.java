package dev.strategos.mod.server.body;

import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.jspecify.annotations.Nullable;

/**
 * Vanilla damage on a unit's body as the core's {@link ExternalDamage} input (04 §6.7): explosions crush, a kill or a
 * fall out of the world is lethal, projectiles pierce, and blows and everything else hack. Who dealt it is the source's
 * causing entity: a player, a mob, or none.
 */
public final class DamageBridge {
    private DamageBridge() {}

    public static ExternalDamage of(int handle, DamageSource source, float amount) {
        return convert(handle, source, amount, source.getEntity(), Damage.UNATTRIBUTED);
    }

    /** A locked match accepts a player only through its authenticated primary seat; world mobs stay unaffiliated. */
    public static @Nullable ExternalDamage ofForMatch(
            MatchRegistry matches, MatchRuntime target, int handle, DamageSource source, float amount) {
        Entity by = source.getEntity();
        int faction = Damage.UNATTRIBUTED;
        var core = target.core();
        if (core != null && core.queries().diplomacy().teamsLocked()) {
            if (by instanceof ServerPlayer player) {
                var seat = matches.seatOf(player.getUUID());
                if (seat == null || seat.match() != target || seat.slot().isAdmin()) return null;
                faction = core.queries()
                        .diplomacy()
                        .factionOf(seat.slot())
                        .map(dev.strategos.core.shared.FactionSlot::value)
                        .orElse(Damage.UNATTRIBUTED);
                if (faction == Damage.UNATTRIBUTED) return null;
            } else if (by instanceof UnitEntity unit) {
                var data = unit.spawnData();
                if (data == null
                        || data.matchId() != target.id().value()
                        || data.faction() < 0
                        || data.faction() > core.queries().config().factions()) return null;
                faction = data.faction();
            }
        }
        return convert(handle, source, amount, by, faction);
    }

    private static ExternalDamage convert(
            int handle, DamageSource source, float amount, @Nullable Entity by, int faction) {
        ExternalDamage.Kind kind;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            kind = ExternalDamage.Kind.CRUSH;
        } else if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            kind = ExternalDamage.Kind.LETHAL;
        } else if (source.getDirectEntity() instanceof Projectile) {
            kind = ExternalDamage.Kind.PIERCE;
        } else {
            kind = ExternalDamage.Kind.HACK;
        }
        ExternalDamage.SourceKind sourceKind = by instanceof Player
                ? ExternalDamage.SourceKind.PLAYER
                : by != null ? ExternalDamage.SourceKind.MOB : ExternalDamage.SourceKind.NONE;
        float finite = Float.isNaN(amount) || amount < 0 ? 0 : Math.min(amount, Float.MAX_VALUE);
        return new ExternalDamage(handle, kind, finite, sourceKind, faction);
    }
}
