package dev.strategos.mod.archfixture;

import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;

/** NET-03 permits server-only attachments but forbids the SDK's client-sync exit. */
public final class AttachmentFixtures {
    private AttachmentFixtures() {}

    public static final class LocalOnly {
        public Object keepLocal(AttachmentType.Builder<Object> builder) {
            return builder;
        }
    }

    public static final class ClientSynced {
        public Object enableSync(AttachmentType.Builder<Object> builder, AttachmentSyncHandler<Object> handler) {
            return builder.sync(handler);
        }
    }
}
