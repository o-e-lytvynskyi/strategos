package dev.strategos.core.sim;

import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;

/** A step tells a faction something (04 §2.1): an order of one of its units failed, or a unit is stuck. */
@CoreApi
public record Notice(FactionSlot faction, NoticeKind kind, int handle, String detail) implements OutputEvent {}
