/**
 * The damage model (CMB-01, 04 §6.7): damage packets of hack, pierce and crush, each resisted by 0.9 to the target's
 * level against its type and multiplied by the attacker's bonuses against the target's classes; vanilla blows arriving
 * as {@code ExternalDamage}; hit points kept as the damage a unit has taken; and death, which takes a unit out of the
 * match at the end of the phase that killed it. Nothing here touches vanilla health.
 */
@NullMarked
package dev.strategos.core.sim.combat;

import org.jspecify.annotations.NullMarked;
