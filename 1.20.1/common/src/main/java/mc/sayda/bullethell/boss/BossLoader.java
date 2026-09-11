package mc.sayda.bullethell.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import mc.sayda.bullethell.data.BHData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Access to {@link BossDefinition}s.
 *
 * Discovery, datapack overrides, dev-path layering and client sync are handled by
 * {@link BHData#BOSSES}; this class owns only the Gson binding and the normalization
 * applied on top of it. Adding a boss means adding
 * {@code data/bullethell/bosses/<id>.json} - there is no id list to update.
 */
public final class BossLoader {

    private static final Gson GSON = new GsonBuilder().create();

    /** Classpath fallback for lookups that happen before any reload or sync. */
    private static final Map<String, BossDefinition> FALLBACK_CACHE = new ConcurrentHashMap<>();

    private BossLoader() {}

    // ---------------------------------------------------------------- parsing

    /** Gson binding + normalization. Called by {@link BHData#BOSSES} for every discovered file. */
    public static BossDefinition parse(String id, JsonObject root) {
        BossDefinition def = parseAndNormalize(root);
        if (def == null) {
            System.err.println("[BulletHell] Boss definition has no phases: " + id);
            return null;
        }
        if (def.id == null || def.id.isBlank()) def.id = id;
        return def;
    }

    // ---------------------------------------------------------------- access

    /** Load a boss by ID. Never null - returns a visible single-phase fallback when missing. */
    public static BossDefinition load(String id) {
        BossDefinition def = BHData.BOSSES.get(id);
        if (def != null) return def;
        return FALLBACK_CACHE.computeIfAbsent(id, BossLoader::loadFallback);
    }

    /** True when a real definition exists (not a fallback). */
    public static boolean resourceExists(String id) {
        if (id == null || id.isEmpty()) return false;
        return BHData.BOSSES.has(id);
    }

    public static List<String> allBossIds() {
        return BHData.BOSSES.ids();
    }

    public static void invalidate(String id) {
        FALLBACK_CACHE.remove(id);
    }

    public static void invalidateAll() {
        FALLBACK_CACHE.clear();
    }

    // ---------------------------------------------------------------- internal

    private static BossDefinition loadFallback(String id) {
        BossDefinition def = BHData.BOSSES.parseFromClasspath(id);
        if (def != null) return def;
        System.err.println("[BulletHell] Boss definition not found: " + id + " - using fallback");
        return fallback(id);
    }

    // ---------------------------------------------------------------- shared parse + normalize

    private static BossDefinition parseAndNormalize(JsonObject root) {
        TierJson.promoteUnionTierFieldsOnBoss(root);
        normalizeMusicArrays(root);
        BossDefinition def = GSON.fromJson(root, BossDefinition.class);
        if (def == null || def.phases == null || def.phases.isEmpty()) return null;
        DifficultyTierArray.normalizeBossDefinition(def);
        for (PhaseDefinition phase : def.phases) {
            if (phase.attacks == null || phase.attacks.isEmpty()) {
                PatternStep ring = new PatternStep();
                ring.pattern = "RING"; ring.arms = 8; ring.speed = 2.0f;
                phase.attacks = new ArrayList<>();
                phase.attacks.add(ring);
            }
            if (phase.spellDurationTicks == null || phase.spellDurationTicks.length < 4)
                phase.spellDurationTicks = new int[]{600, 450, 300, 150};
        }
        return def;
    }

    // ---------------------------------------------------------------- pre-processing

    /**
     * Converts {@code "music": ["a", "b"]} in each phase to {@code "musicPool": ["a", "b"]}
     * so Gson can deserialize it into {@link PhaseDefinition#musicPool} (List<String>)
     * rather than failing on the array type.  Single-string {@code "music"} entries are
     * left untouched.
     */
    private static void normalizeMusicArrays(JsonObject root) {
        if (!root.has("phases")) return;
        for (com.google.gson.JsonElement el : root.getAsJsonArray("phases")) {
            if (!el.isJsonObject()) continue;
            com.google.gson.JsonObject phase = el.getAsJsonObject();
            if (phase.has("music") && phase.get("music").isJsonArray()) {
                phase.add("musicPool", phase.remove("music"));
            }
        }
    }

    // ---------------------------------------------------------------- fallback

    /** Minimal single-phase fallback so the arena can still run. */
    private static BossDefinition fallback(String id) {
        BossDefinition def   = new BossDefinition();
        def.id   = id;
        def.name = "????? (missing: " + id + ")";

        PhaseDefinition phase = new PhaseDefinition();
        phase.hp           = 500;
        phase.isSpellCard  = false;
        phase.spellName    = "???";
        phase.spellDurationTicks = new int[]{0, 0, 0, 0};
        phase.spellBonus   = 0L;
        phase.movement     = "SINE_WAVE";
        phase.moveRange    = 140f;

        PatternStep step  = new PatternStep();
        step.pattern      = "RING";
        step.cooldown     = 20;
        step.bulletType   = "DOT";
        step.arms         = 8;
        step.speed        = 2.5f;
        phase.attacks.add(step);

        def.phases.add(phase);
        return def;
    }
}
