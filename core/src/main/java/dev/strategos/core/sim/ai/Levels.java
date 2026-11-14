package dev.strategos.core.sim.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;
import java.util.List;

/**
 * The difficulty levels of the skirmish script (AI-05, D-19, M6 §2.20): {@code ai/skirmish.json} is a strategy script
 * with a {@code levels} object, each level's knobs by its lower-case name. {@link #resolve} turns the script and a level
 * into the plain script {@code SetAi} carries, so a journal replays it: the level's pace goes into the script's
 * {@code level} field (think period, reaction, commands per think, the open handicap), the wave size multiplies each
 * attack's {@code min}, and the tactics (counters, focus, retreat: the {@code battle} block) are dropped when the level
 * has them off. No level has a bonus: {@link StrategyScript.Handicap}'s codec admits none.
 */
@CoreApi
public final class Levels {
    /** The profile of the skirmish script: {@code ai:skirmish}. */
    public static final String PROFILE = "skirmish";

    /** The levels' names, in order of strength. */
    public static List<String> names() {
        return List.of("easy", "normal", "hard");
    }

    /** One level's knobs as the script file gives them. */
    public record Knobs(
            int think, int reaction, int commands, double wave, boolean tactics, StrategyScript.Handicap handicap) {
        static final Codec<Knobs> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.intRange(1, 1_200).fieldOf("think").forGetter(Knobs::think),
                        Codec.intRange(0, 1_200).fieldOf("reaction").forGetter(Knobs::reaction),
                        Codec.intRange(1, 1_000).fieldOf("commands").forGetter(Knobs::commands),
                        Codec.doubleRange(0.1, 1.4).fieldOf("wave").forGetter(Knobs::wave),
                        Codec.BOOL.fieldOf("tactics").forGetter(Knobs::tactics),
                        StrategyScript.Handicap.CODEC
                                .optionalFieldOf("handicap", StrategyScript.Handicap.NONE)
                                .forGetter(Knobs::handicap))
                .apply(i, Knobs::new));
    }

    private Levels() {}

    /** Level {@code level}'s knobs in {@code skirmishJson}; a missing level or a malformed one throws. */
    public static Knobs knobs(String skirmishJson, String level) {
        JsonObject root = JsonParser.parseString(skirmishJson).getAsJsonObject();
        JsonObject levels = root.has("levels") ? root.getAsJsonObject("levels") : new JsonObject();
        if (!levels.has(level)) {
            throw new IllegalArgumentException("the skirmish script has no level '" + level + "'");
        }
        return Knobs.CODEC
                .parse(JsonOps.INSTANCE, levels.get(level))
                .getOrThrow(m -> new IllegalArgumentException("skirmish level " + level + ": " + m));
    }

    /** The script {@code SetAi} carries for {@code level} (lower case): compact JSON, without the level table. */
    public static String resolve(String skirmishJson, String level) {
        return resolve(skirmishJson, level, "");
    }

    /** As {@link #resolve(String, String)}, with the opening {@code opening} pinned ("" draws by weight, as in play). */
    public static String resolve(String skirmishJson, String level, String opening) {
        Knobs k = knobs(skirmishJson, level);
        JsonObject script = JsonParser.parseString(skirmishJson).getAsJsonObject();
        script.remove("levels");
        JsonObject pace = new JsonObject();
        pace.addProperty("name", level);
        pace.addProperty("think", k.think());
        pace.addProperty("reaction", k.reaction());
        pace.addProperty("commands", k.commands());
        if (k.handicap().any()) {
            JsonObject h = new JsonObject();
            h.addProperty("gather", k.handicap().gather());
            h.addProperty("train", k.handicap().train());
            pace.add("handicap", h);
        }
        script.add("level", pace);
        if (!opening.isEmpty()) {
            script.addProperty("opening", opening);
        }
        if (!k.tactics()) {
            script.remove("battle");
        }
        scale(script.get("attack"), k.wave());
        JsonElement openings = script.get("openings");
        if (openings instanceof JsonArray list) {
            for (JsonElement o : list) {
                scale(o.getAsJsonObject().get("attack"), k.wave());
            }
        }
        String json = script.toString();
        StrategyScript.parse(json); // a script that does not parse fails here, not at SetAi
        return json;
    }

    /** Multiplies an attack's {@code min} by {@code wave}, at least 1. */
    private static void scale(@org.jspecify.annotations.Nullable JsonElement attack, double wave) {
        if (attack instanceof JsonObject a && a.has("min")) {
            a.addProperty("min", Math.max(1, (int) Math.round(a.get("min").getAsInt() * wave)));
        }
    }
}
