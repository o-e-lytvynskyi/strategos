package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * Vanilla damage that reached a unit's body (04 §6.7, UNIT-09): a player's or a mob's blow, a projectile, an explosion
 * or a kill, as the damage packet it becomes, with its amount and who dealt it. Journaled in the tick's inputs; M1 only
 * records it, and CMB-01 applies it.
 */
@CoreApi
public record ExternalDamage(int handle, Kind kind, float amount, SourceKind sourceKind) implements InputEvent {
    public ExternalDamage {
        if (!(amount >= 0) || Float.isInfinite(amount)) {
            throw new IllegalArgumentException("damage " + amount + " is not a finite amount");
        }
    }

    /** The damage type it deals: melee hacks, projectiles pierce, explosions crush, a kill is lethal. */
    @CoreApi
    public enum Kind {
        HACK(0),
        PIERCE(1),
        CRUSH(2),
        LETHAL(3);

        private final int code;

        Kind(int code) {
            this.code = code;
        }

        /** Its fixed number in the journal. */
        public int code() {
            return code;
        }

        public static Kind ofCode(int code) {
            return switch (code) {
                case 0 -> HACK;
                case 1 -> PIERCE;
                case 2 -> CRUSH;
                case 3 -> LETHAL;
                default -> throw new IllegalArgumentException("no damage kind " + code);
            };
        }
    }

    /** Who dealt it: a player, a mob, or nothing alive (the world, a dispenser, a command). */
    @CoreApi
    public enum SourceKind {
        PLAYER(0),
        MOB(1),
        NONE(2);

        private final int code;

        SourceKind(int code) {
            this.code = code;
        }

        /** Its fixed number in the journal. */
        public int code() {
            return code;
        }

        public static SourceKind ofCode(int code) {
            return switch (code) {
                case 0 -> PLAYER;
                case 1 -> MOB;
                case 2 -> NONE;
                default -> throw new IllegalArgumentException("no damage source kind " + code);
            };
        }
    }
}
