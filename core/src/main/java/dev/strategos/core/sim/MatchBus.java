package dev.strategos.core.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * A typed synchronous per-match bus. Handlers run in registration order for the exact message class. A message
 * published while a handler runs is queued and delivered, FIFO, once the current message has reached all its
 * handlers, so nesting never grows the stack and every run dispatches in the same order.
 */
public final class MatchBus {
    private final LinkedHashMap<Class<?>, List<Consumer<BusMessage>>> handlers = new LinkedHashMap<>();
    private final ArrayDeque<BusMessage> queue = new ArrayDeque<>();
    private boolean dispatching;

    @SuppressWarnings("unchecked") // the handler only ever receives messages of its own exact class
    public <M extends BusMessage> void subscribe(Class<M> type, Consumer<? super M> handler) {
        handlers.computeIfAbsent(type, t -> new ArrayList<>()).add((Consumer<BusMessage>) handler);
    }

    public void publish(BusMessage message) {
        queue.addLast(message);
        if (dispatching) {
            return;
        }
        dispatching = true;
        try {
            BusMessage next;
            while ((next = queue.pollFirst()) != null) {
                List<Consumer<BusMessage>> list = handlers.get(next.getClass());
                if (list != null) {
                    for (int i = 0; i < list.size(); i++) {
                        list.get(i).accept(next);
                    }
                }
            }
        } finally {
            dispatching = false;
        }
    }

    /** No dispatch running and nothing queued; the final phase asserts it. */
    public boolean idle() {
        return !dispatching && queue.isEmpty();
    }
}
