package dev.strategos.core.sim.civ;

import com.google.gson.JsonObject;
import dev.strategos.core.shared.CoreApi;
import java.util.function.Function;

/**
 * One variant of a seam in the {@link RuleCatalogue}: its id, the seam interface it makes, and the decoding of its
 * parameters. A variant's behaviour never changes under its id: a changed behaviour is a new id ({@code direct@2}),
 * and the old one stays for old journals.
 *
 * @param <S> the seam interface
 */
@CoreApi
public final class RuleFactory<S> {
    /** The parameters did not decode: the message says what was expected, for a lint line. */
    public static final class BadParams extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        public BadParams(String message) {
            super(message);
        }
    }

    private final String id;
    private final Class<S> seam;
    private final Function<JsonObject, S> create;

    private RuleFactory(String id, Class<S> seam, Function<JsonObject, S> create) {
        this.id = id;
        this.seam = seam;
        this.create = create;
    }

    /** A variant whose parameters {@code create} decodes, throwing {@link BadParams} (or any IllegalArgumentException). */
    public static <S> RuleFactory<S> of(String id, Class<S> seam, Function<JsonObject, S> create) {
        if (id.isBlank()) {
            throw new IllegalArgumentException("a rule id is not blank");
        }
        return new RuleFactory<>(id, seam, create);
    }

    /** A variant with no parameters: any given are refused. */
    public static <S> RuleFactory<S> fixed(String id, Class<S> seam, S instance) {
        return of(id, seam, params -> {
            if (!params.isEmpty()) {
                throw new BadParams("rule '" + id + "' takes no parameters, got " + params);
            }
            return instance;
        });
    }

    public String id() {
        return id;
    }

    /** The seam interface this variant implements. */
    public Class<S> seam() {
        return seam;
    }

    /** The variant for {@code params}; throws {@link BadParams} saying what is wrong with them, whatever the decoder threw. */
    public S create(JsonObject params) {
        try {
            return create.apply(params);
        } catch (BadParams e) {
            throw e;
        } catch (RuntimeException e) { // a decoder's own failure on a wrong shape is still the data's fault
            throw new BadParams("rule '" + id + "': " + e);
        }
    }
}
