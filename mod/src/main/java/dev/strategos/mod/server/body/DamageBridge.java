package dev.strategos.mod.server.body;

import dev.strategos.core.sim.ExternalDamage;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Vanilla damage on a unit's body as the core's {@link ExternalDamage} input (04 §6.7): explosions crush, a kill or a
 * fall out of the world is lethal, projectiles pierce, and blows and everything else hack. Who dealt it is the source's
 * causing entity: a player, a mob, or none.
 */
public final class DamageBridge {
    private DamageBridge() {}

    public static ExternalDamage of(int handle, DamageSource source, float amount) {
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
        Entity by = source.getEntity();
        ExternalDamage.SourceKind sourceKind = by instanceof Player
                ? ExternalDamage.SourceKind.PLAYER
                : by != null ? ExternalDamage.SourceKind.MOB : ExternalDamage.SourceKind.NONE;
        float finite = Float.isNaN(amount) || amount < 0 ? 0 : Math.min(amount, Float.MAX_VALUE);
        return new ExternalDamage(handle, kind, finite, sourceKind);
    }
}
