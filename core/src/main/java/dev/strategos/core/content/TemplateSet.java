package dev.strategos.core.content;

import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An immutable set of unit templates in net-index order. M0 has one code-defined template; M1's template compiler
 * produces the same type from data.
 */
public final class TemplateSet {
    public static final TemplateId DEBUG_WALKER = new TemplateId("strategos:unit/debug_walker");

    /** 0.25 blocks per tick is exact in binary, so walking distances stay exact. */
    private static final TemplateSet BUILTIN =
            new TemplateSet(List.of(new UnitTemplate(DEBUG_WALKER, 0, "strategos:unit_humanoid", 0.25, 0.6f, 1.8f)));

    private final List<UnitTemplate> templates;

    /** Each template's {@code netIndex} must be its position; ids must be distinct; speeds and sizes positive. */
    public TemplateSet(List<UnitTemplate> templates) {
        for (int i = 0; i < templates.size(); i++) {
            UnitTemplate t = templates.get(i);
            if (t.netIndex() != i) {
                throw new IllegalArgumentException(t.id() + " has net index " + t.netIndex() + " at position " + i);
            }
            if (!(t.walkSpeed() > 0 && t.width() > 0 && t.height() > 0)) {
                throw new IllegalArgumentException(t.id() + " needs a positive speed and size");
            }
            for (int j = 0; j < i; j++) {
                if (templates.get(j).id().equals(t.id())) {
                    throw new IllegalArgumentException(t.id() + " listed twice");
                }
            }
        }
        this.templates = List.copyOf(templates);
    }

    /** M0's set: {@link #DEBUG_WALKER}, net index 0, a humanoid body, 0.25 blocks per tick, 0.6 x 1.8. */
    public static TemplateSet builtin() {
        return BUILTIN;
    }

    public @Nullable UnitTemplate byId(TemplateId id) {
        for (UnitTemplate t : templates) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    public UnitTemplate byIndex(int netIndex) {
        if (netIndex < 0 || netIndex >= templates.size()) {
            throw new IllegalArgumentException("no template with net index " + netIndex);
        }
        return templates.get(netIndex);
    }

    /** In net-index order. */
    public List<UnitTemplate> all() {
        return templates;
    }

    /** Every field of every template, in order: part of a match's identity in journals and saves. */
    public long hash() {
        Hasher h = new Hasher(0).putInt(templates.size());
        for (UnitTemplate t : templates) {
            h.putString(t.id().value()).putInt(t.netIndex()).putString(t.bodyType());
            h.putDouble(t.walkSpeed()).putFloat(t.width()).putFloat(t.height());
        }
        return h.finish();
    }
}
