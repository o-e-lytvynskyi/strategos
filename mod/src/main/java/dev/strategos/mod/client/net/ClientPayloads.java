package dev.strategos.mod.client.net;

import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.net.payload.MatchStatePayload;
import dev.strategos.mod.server.net.payload.PayloadArrived;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-to-client payloads on the client (spec §2.7): the match this player joined and its state go to the client's
 * holder; feedback is logged until the command card (M2) shows it.
 */
public final class ClientPayloads {
    private static final Logger LOG = LoggerFactory.getLogger(ClientPayloads.class);

    private ClientPayloads() {}

    public static void onPayload(PayloadArrived event) {
        switch (event.payload()) {
            case MatchJoinPayload p -> StrategosClient.match(p.join());
            case MatchStatePayload p -> StrategosClient.state(p.state());
            case FeedbackPayload p -> LOG.info("feedback: {}", p.message());
            default -> LOG.debug("unhandled payload {}", event.payload().type().id());
        }
    }
}
