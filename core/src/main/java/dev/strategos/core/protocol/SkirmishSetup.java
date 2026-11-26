package dev.strategos.core.protocol;

import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.MatchConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

/**
 * A skirmish as the setup screen and {@code /strategos skirmish} choose it (MATCH-02, spec §2.11): the map package, the
 * computer's level (D-19), the starting resources (the map's, half or double), the population cap (D-01) and the
 * visibility (MATCH-07), and each slot's civilisation (CIV-01): empty is the default civilisation for every slot, one
 * id is that civilisation for every slot, else one per slot; and whether the match has day and night (MATCH-16, off
 * by default). {@link #DEFAULTS} is two clicks: Skirmish, then Start.
 */
@CoreApi
public record SkirmishSetup(
        String map,
        Level level,
        StartResources resources,
        int populationCap,
        Visibility visibility,
        List<CivId> civs,
        boolean dayNight) {
    public static final String FIRST_SKIRMISH = "strategos:first_skirmish";
    public static final int MIN_POPULATION = 50;
    public static final int MAX_POPULATION = 200;
    public static final int DEFAULT_POPULATION = 150;
    private static final int MAX_MAP_BYTES = 128;

    public static final SkirmishSetup DEFAULTS =
            new SkirmishSetup(FIRST_SKIRMISH, Level.NORMAL, StartResources.MAP, DEFAULT_POPULATION, Visibility.FOG);

    /** The computer's level (D-19); its knobs are AI-05's (§2.20). */
    @CoreApi
    public enum Level {
        EASY(0),
        NORMAL(1),
        HARD(2);

        private final int code;

        Level(int code) {
            this.code = code;
        }

        /** Its stable wire code. */
        public int code() {
            return code;
        }
    }

    /** The starting resources: the map scenario's, half of them, or twice as many. */
    @CoreApi
    public enum StartResources {
        MAP(0, 1),
        LOW(1, 0.5),
        HIGH(2, 2);

        private final int code;
        private final double factor;

        StartResources(int code, double factor) {
            this.code = code;
            this.factor = factor;
        }

        /** Its stable wire code. */
        public int code() {
            return code;
        }

        public double factor() {
            return factor;
        }
    }

    /** MATCH-07's options (§2.8). */
    @CoreApi
    public enum Visibility {
        FOG(0),
        EXPLORED(1),
        REVEALED(2);

        private final int code;

        Visibility(int code) {
            this.code = code;
        }

        /** Its stable wire code. */
        public int code() {
            return code;
        }
    }

    /** A setup whose slots all play the default civilisation. */
    public SkirmishSetup(String map, Level level, StartResources resources, int populationCap, Visibility visibility) {
        this(map, level, resources, populationCap, visibility, List.of());
    }

    /** A setup without day and night. */
    public SkirmishSetup(
            String map,
            Level level,
            StartResources resources,
            int populationCap,
            Visibility visibility,
            List<CivId> civs) {
        this(map, level, resources, populationCap, visibility, civs, false);
    }

    public SkirmishSetup {
        civs = List.copyOf(civs);
        if (civs.size() > MatchConfig.MAX_FACTIONS) {
            throw new IllegalArgumentException("a skirmish of " + civs.size() + " civilisations");
        }
        if (map.isEmpty() || map.length() > MAX_MAP_BYTES) {
            throw new IllegalArgumentException("a skirmish map id of " + map.length() + " characters");
        }
        if (populationCap < MIN_POPULATION || populationCap > MAX_POPULATION) {
            throw new IllegalArgumentException(
                    "population cap " + populationCap + " outside " + MIN_POPULATION + "–" + MAX_POPULATION);
        }
    }

    /** Slot {@code slot}'s civilisation: the default when none is listed, the one entry when there is one. */
    public CivId civ(int slot) {
        if (civs.isEmpty()) {
            return CivId.DEFAULT;
        }
        return civs.size() == 1 ? civs.get(0) : civs.get(Math.min(slot, civs.size() - 1));
    }

    /** A slot's starting resources: the scenario's, scaled by the choice and rounded down. */
    public Map<String, Long> startResources(Map<String, Long> scenario) {
        Map<String, Long> out = new TreeMap<>();
        scenario.forEach((id, amount) -> out.put(id, (long) Math.floor(amount * resources.factor())));
        return out;
    }

    public static final WireCodec<SkirmishSetup> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, SkirmishSetup s) {
            out.writeString(s.map(), MAX_MAP_BYTES)
                    .writeByte(s.level().code())
                    .writeByte(s.resources().code())
                    .writeVarInt(s.populationCap())
                    .writeByte(s.visibility().code());
            boolean custom = s.civs().stream().anyMatch(c -> !c.equals(CivId.DEFAULT));
            if (custom || s.dayNight()) { // appended: a setup without them is as it was
                out.writeVarInt(custom ? s.civs().size() : 0);
                if (custom) {
                    s.civs().forEach(c -> out.writeString(c.value(), CivId.MAX_BYTES));
                }
                if (s.dayNight()) { // after the civilisations, whose count may be 0 only before this byte
                    out.writeByte(1);
                }
            }
        }

        @Override
        public SkirmishSetup read(WireReader in) {
            String map = in.readString(MAX_MAP_BYTES);
            int level = in.readUnsignedByte();
            int resources = in.readUnsignedByte();
            int cap = in.readVarInt();
            int visibility = in.readUnsignedByte();
            try {
                List<CivId> civs = readCivs(in);
                boolean dayNight = in.remaining() > 0 && flag(in.readUnsignedByte());
                return new SkirmishSetup(
                        map,
                        pick(Level.values(), level, Level::code, "level"),
                        pick(StartResources.values(), resources, StartResources::code, "resources"),
                        cap,
                        pick(Visibility.values(), visibility, Visibility::code, "visibility"),
                        civs,
                        dayNight);
            } catch (IllegalArgumentException e) { // client input: a wire fault, not a bug
                throw new WireException(String.valueOf(e.getMessage()));
            }
        }

        @Override
        public int maxSize() {
            return 3 + MAX_MAP_BYTES + 1 + 1 + 5 + 1 + 1 + MatchConfig.MAX_FACTIONS * (1 + CivId.MAX_BYTES) + 1;
        }
    };

    /**
     * The appended civilisations: absent for none; a present list is empty only before the day-and-night byte (the
     * writer leaves an empty one out otherwise).
     */
    private static List<CivId> readCivs(WireReader in) {
        if (in.remaining() == 0) {
            return List.of();
        }
        int n = in.readVarInt(MatchConfig.MAX_FACTIONS);
        if (n == 0) {
            if (in.remaining() == 0) {
                throw new IllegalArgumentException("an empty civilisation list is never written");
            }
            return List.of();
        }
        List<CivId> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new CivId(in.readString(CivId.MAX_BYTES)));
        }
        return out;
    }

    /** The day-and-night byte: written only when set, so it is 1 or a fault. */
    private static boolean flag(int b) {
        if (b != 1) {
            throw new IllegalArgumentException("a day-and-night byte of " + b);
        }
        return true;
    }

    private static <E> E pick(E[] values, int code, ToIntFunction<E> codeOf, String what) {
        for (E e : values) {
            if (codeOf.applyAsInt(e) == code) {
                return e;
            }
        }
        throw new IllegalArgumentException("unknown " + what + " " + code);
    }
}
