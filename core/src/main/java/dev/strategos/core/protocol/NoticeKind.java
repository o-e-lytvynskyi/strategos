package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;

/** What a notice tells a faction (04 §2.1): an order that could not be carried out, or a unit that is stuck. */
@CoreApi
public enum NoticeKind {
    ORDER_FAILED,
    STUCK
}
