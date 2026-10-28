package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.mod.registry.ModPayloads;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** A newly registered outbound type must acquire a documented fog policy, not merely a transport audience. */
class FogChannelInventoryTest {
    @Test
    void everyOutboundPayloadHasExactlyOneAuditEntry() throws IOException {
        Path doc = Path.of(System.getProperty("strategos.repoRoot"), "docs/design/m8-outbound-channels.md");
        String text = Files.readString(doc, StandardCharsets.UTF_8);
        Matcher rows = Pattern.compile("^\\| `(strategos:[a-z0-9_./-]+)` \\|", Pattern.MULTILINE)
                .matcher(text);
        List<String> audited = new ArrayList<>();
        while (rows.find()) {
            audited.add(rows.group(1));
        }
        List<String> registered = new ArrayList<>();
        for (PayloadSpec spec : ModPayloads.specs()) {
            if (spec.toClient()) {
                registered.add(spec.id().toString());
            }
        }
        assertThat(audited).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(registered);
    }
}
