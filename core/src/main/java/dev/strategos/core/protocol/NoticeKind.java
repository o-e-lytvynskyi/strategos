package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;

/** What a notice tells a faction (04 §2.1): an order that could not be carried out, a unit that is stuck or one lost. */
@CoreApi
public enum NoticeKind {
    ORDER_FAILED,
    STUCK,
    /** One of its units died (CMB-01). */
    UNIT_LOST,
    /** A worker found nothing more to gather within reach, or nowhere to take its load, and stands idle (ECO-05). */
    IDLE_WORKER
}
