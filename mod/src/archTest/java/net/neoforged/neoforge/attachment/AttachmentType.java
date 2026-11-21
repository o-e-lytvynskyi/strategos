package net.neoforged.neoforge.attachment;

/** A same-named SDK target stub; these classes are never shipped or used as an attachment implementation. */
public final class AttachmentType<T> {
    private AttachmentType() {}

    public static final class Builder<T> {
        public Builder<T> sync(AttachmentSyncHandler<T> handler) {
            return this;
        }
    }
}
