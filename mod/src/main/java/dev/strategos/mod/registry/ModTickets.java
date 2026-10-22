package dev.strategos.mod.registry;

import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.world.ArenaTickets;
import java.util.List;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/**
 * The chunk ticket controllers (04 §3.4, MAP-07): {@code strategos:match} force-loads arena matches, and its
 * validation callback drops the tickets of matches that are gone when a level loads (TicketController.java:27).
 */
public final class ModTickets {
    public static final TicketController MATCH = new TicketController(Ids.id("match"), ArenaTickets::validate);

    private ModTickets() {}

    /** Every controller this mod registers; the inventory test reads it. */
    public static List<TicketController> all() {
        return List.of(MATCH);
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterTicketControllersEvent.class, event -> all().forEach(event::register));
    }
}
