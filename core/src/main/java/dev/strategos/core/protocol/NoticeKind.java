package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;

/**
 * What a notice tells a faction (04 §2.1): an order that could not be carried out, a unit that is stuck or one lost,
 * and what the notification feed reports (UI-21).
 */
@CoreApi
public enum NoticeKind {
    ORDER_FAILED,
    STUCK,
    /** One of its units died (CMB-01). */
    UNIT_LOST,
    /** A worker found nothing more to gather within reach, or nowhere to take its load, and stands idle (ECO-05). */
    IDLE_WORKER,
    /** A building's training waits: its faction's population is at its cap (ECO-06). */
    HOUSING_NEEDED,
    /** A batch stepped out of its building; the detail names the unit template (UI-21). */
    TRAINED,
    /** A building's construction finished; the detail names its template (UI-21). */
    BUILT,
    /** A technology other than a phase was researched; the detail names it (UI-21). */
    RESEARCHED
}
