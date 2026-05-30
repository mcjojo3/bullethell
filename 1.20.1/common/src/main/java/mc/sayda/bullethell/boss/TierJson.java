package mc.sayda.bullethell.boss;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Optional nested object {@code byDifficulty} on {@link PhaseDefinition} / {@link PatternStep}:
 * keys are the same as scalar JSON field names (e.g. {@code "speed"}, {@code "arms"}); each value is a
 * {@code [EASY, NORMAL, HARD, LUNATIC]} array. Shorter arrays pad with the last entry; empty arrays are
 * dropped at load time. When a key is absent, the normal scalar field is used.
 *
 * <p><strong>Unified tier syntax:</strong> on each scalar field you may instead put a {@code [E,M,H,L]} array
 * directly on that key (e.g. {@code "rayStackSpacing": [10, 10, 15, 15]}). Boss load promotes it to the
 * internal {@code *ByDifficulty} field and sets the scalar to the first entry (fallback when tiers are absent).
 * Legacy separate {@code *ByDifficulty} keys in JSON still work.</p>
 */
public final class TierJson {

    private TierJson() {}

    public static boolean hasTierArray(JsonObject o, String key) {
        if (o == null || key == null || key.isEmpty() || !o.has(key))
            return false;
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() && e.getAsJsonArray().size() > 0;
    }

    /**
     * Pads each array value to {@link DifficultyTierArray#COUNT}; removes keys whose array is empty.
     */
    public static void normalize(JsonObject o) {
        if (o == null)
            return;
        java.util.Set<String> keys = new java.util.HashSet<>(o.keySet());
        for (String k : keys) {
            JsonElement v = o.get(k);
            if (v == null || !v.isJsonArray())
                continue;
            JsonArray a = v.getAsJsonArray();
            if (a.size() == 0) {
                o.remove(k);
                continue;
            }
            JsonArray n = new JsonArray();
            int lastIdx = a.size() - 1;
            for (int i = 0; i < DifficultyTierArray.COUNT; i++) {
                JsonElement slot = i < a.size() ? a.get(i) : a.get(lastIdx);
                n.add(slot.deepCopy());
            }
            o.add(k, n);
        }
    }

    public static int pickInt(JsonObject o, String key, int difficultyOrdinal, int fallback) {
        JsonArray a = arrayOrNull(o, key);
        if (a == null)
            return fallback;
        int i = ordinalIndex(difficultyOrdinal, a.size());
        return elementAsInt(a.get(i), fallback);
    }

    public static long pickLong(JsonObject o, String key, int difficultyOrdinal, long fallback) {
        JsonArray a = arrayOrNull(o, key);
        if (a == null)
            return fallback;
        int i = ordinalIndex(difficultyOrdinal, a.size());
        return elementAsLong(a.get(i), fallback);
    }

    public static float pickFloat(JsonObject o, String key, int difficultyOrdinal, float fallback) {
        JsonArray a = arrayOrNull(o, key);
        if (a == null)
            return fallback;
        int i = ordinalIndex(difficultyOrdinal, a.size());
        return elementAsFloat(a.get(i), fallback);
    }

    public static boolean pickBoolean(JsonObject o, String key, int difficultyOrdinal, boolean fallback) {
        JsonArray a = arrayOrNull(o, key);
        if (a == null)
            return fallback;
        int i = ordinalIndex(difficultyOrdinal, a.size());
        return elementAsBoolean(a.get(i), fallback);
    }

    public static String pickString(JsonObject o, String key, int difficultyOrdinal, String fallback) {
        JsonArray a = arrayOrNull(o, key);
        if (a == null)
            return fallback == null ? "" : fallback;
        int i = ordinalIndex(difficultyOrdinal, a.size());
        return elementAsString(a.get(i), fallback);
    }

    private static JsonArray arrayOrNull(JsonObject o, String key) {
        if (o == null || !o.has(key))
            return null;
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray())
            return null;
        JsonArray a = e.getAsJsonArray();
        return a.size() == 0 ? null : a;
    }

    private static int ordinalIndex(int difficultyOrdinal, int len) {
        int i = difficultyOrdinal;
        if (i < 0)
            i = 0;
        if (i > len - 1)
            i = len - 1;
        return i;
    }

    private static int elementAsInt(JsonElement e, int fallback) {
        if (e == null || e.isJsonNull())
            return fallback;
        try {
            if (e.isJsonPrimitive()) {
                JsonPrimitive p = e.getAsJsonPrimitive();
                if (p.isNumber())
                    return p.getAsInt();
                if (p.isBoolean())
                    return p.getAsBoolean() ? 1 : 0;
                if (p.isString()) {
                    String s = p.getAsString().trim();
                    if (!s.isEmpty())
                        return (int) Math.round(Double.parseDouble(s));
                }
            }
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    private static long elementAsLong(JsonElement e, long fallback) {
        if (e == null || e.isJsonNull())
            return fallback;
        try {
            if (e.isJsonPrimitive()) {
                JsonPrimitive p = e.getAsJsonPrimitive();
                if (p.isNumber())
                    return p.getAsLong();
                if (p.isBoolean())
                    return p.getAsBoolean() ? 1L : 0L;
                if (p.isString()) {
                    String s = p.getAsString().trim();
                    if (!s.isEmpty())
                        return (long) Math.round(Double.parseDouble(s));
                }
            }
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    private static float elementAsFloat(JsonElement e, float fallback) {
        if (e == null || e.isJsonNull())
            return fallback;
        try {
            if (e.isJsonPrimitive()) {
                JsonPrimitive p = e.getAsJsonPrimitive();
                if (p.isNumber())
                    return p.getAsFloat();
                if (p.isBoolean())
                    return p.getAsBoolean() ? 1f : 0f;
                if (p.isString()) {
                    String s = p.getAsString().trim();
                    if (!s.isEmpty())
                        return Float.parseFloat(s);
                }
            }
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    private static boolean elementAsBoolean(JsonElement e, boolean fallback) {
        if (e == null || e.isJsonNull())
            return fallback;
        try {
            if (e.isJsonPrimitive()) {
                JsonPrimitive p = e.getAsJsonPrimitive();
                if (p.isBoolean())
                    return p.getAsBoolean();
                if (p.isNumber())
                    return p.getAsInt() != 0;
                if (p.isString()) {
                    String s = p.getAsString().trim();
                    if ("true".equalsIgnoreCase(s) || "1".equals(s))
                        return true;
                    if ("false".equalsIgnoreCase(s) || "0".equals(s))
                        return false;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    private static String elementAsString(JsonElement e, String fallback) {
        if (e == null || e.isJsonNull())
            return fallback == null ? "" : fallback;
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isString())
                return p.getAsString();
            if (p.isBoolean())
                return p.getAsBoolean() ? "true" : "false";
            if (p.isNumber())
                return p.getAsString();
        }
        return fallback == null ? "" : fallback;
    }

    // ---------------------------------------------------------------- boss load - promote scalar OR [E,N,H,L] arrays

    /**
     * For each phase: promote explicitly-listed scalar fields that hold a {@code [E,N,H,L]} array into their
     * {@code *ByDifficulty} backing field on {@link PhaseDefinition}.
     * For each pattern step: any field holding an array is promoted into the step's {@code byDifficulty}
     * sub-object - fully generic, no field whitelist needed.
     */
    public static void promoteUnionTierFieldsOnBoss(JsonObject root) {
        if (root == null || !root.has("phases") || !root.get("phases").isJsonArray())
            return;
        for (JsonElement pe : root.getAsJsonArray("phases")) {
            if (!pe.isJsonObject())
                continue;
            JsonObject ph = pe.getAsJsonObject();
            promotePhaseScalarOrArrays(ph);
            promotePatternStepList(ph.get("attacks"));
            if (ph.has("emitters") && ph.get("emitters").isJsonArray()) {
                for (JsonElement ee : ph.getAsJsonArray("emitters")) {
                    if (!ee.isJsonObject())
                        continue;
                    promotePatternStepList(ee.getAsJsonObject().get("attacks"));
                }
            }
        }
    }

    private static void promotePatternStepList(JsonElement attacksEl) {
        if (attacksEl == null || !attacksEl.isJsonArray())
            return;
        for (JsonElement se : attacksEl.getAsJsonArray()) {
            if (se.isJsonObject())
                promotePatternStepScalarOrArrays(se.getAsJsonObject());
        }
    }

    private static void promotePhaseScalarOrArrays(JsonObject ph) {
        if (ph == null)
            return;
        JsonObject bd = null;
        for (String key : new java.util.ArrayList<>(ph.keySet())) {
            if ("byDifficulty".equals(key)
                    || "attacks".equals(key) || "emitters".equals(key) || "spellDurationTicks".equals(key))
                continue;
            JsonElement el = ph.get(key);
            if (el == null || !el.isJsonArray())
                continue;
            JsonArray a = el.getAsJsonArray();
            if (a.size() == 0)
                continue;
            if (bd == null)
                bd = getOrCreateByDifficulty(ph);
            bd.add(key, padJsonArrayToFour(a));
            ph.remove(key);
            ph.add(key, a.get(0));
        }
        if (bd != null && bd.size() > 0)
            ph.add("byDifficulty", bd);
    }

    /** Pattern steps: any field holding a JSON array is generically promoted into {@code byDifficulty}. */
    private static void promotePatternStepScalarOrArrays(JsonObject o) {
        if (o == null)
            return;
        JsonObject bd = null;
        for (String key : new java.util.ArrayList<>(o.keySet())) {
            if ("byDifficulty".equals(key) || "wormCircles".equals(key)
                    || "bounceExcludeSides".equals(key))
                continue;
            JsonElement el = o.get(key);
            if (el == null || !el.isJsonArray())
                continue;
            JsonArray a = el.getAsJsonArray();
            if (a.size() == 0)
                continue;
            if (bd == null)
                bd = getOrCreateByDifficulty(o);
            bd.add(key, padJsonArrayToFour(a));
            o.remove(key);
            o.add(key, a.get(0));
        }
        if (bd != null && bd.size() > 0)
            o.add("byDifficulty", bd);
    }

    private static JsonObject getOrCreateByDifficulty(JsonObject o) {
        if (o.has("byDifficulty") && o.get("byDifficulty").isJsonObject())
            return o.getAsJsonObject("byDifficulty");
        return new JsonObject();
    }

    private static JsonArray padJsonArrayToFour(JsonArray a) {
        JsonArray n = new JsonArray();
        int lastIdx = a.size() - 1;
        for (int i = 0; i < DifficultyTierArray.COUNT; i++) {
            JsonElement slot = i < a.size() ? a.get(i) : a.get(lastIdx);
            n.add(slot.deepCopy());
        }
        return n;
    }
}
