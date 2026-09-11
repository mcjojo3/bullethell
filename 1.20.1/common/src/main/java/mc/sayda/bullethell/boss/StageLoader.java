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
 * Access to {@link StageDefinition}s.
 *
 * Discovery, datapack overrides, dev-path layering and client sync are handled by
 * {@link BHData#STAGES}; this class owns only the Gson binding and the defaults
 * applied on top of it. Adding a stage means adding
 * {@code data/bullethell/stages/<id>.json} - there is no id list to update.
 */
public final class StageLoader {

    private static final Gson GSON = new GsonBuilder().create();

    /** Classpath fallback for lookups that happen before any reload or sync. */
    private static final Map<String, StageDefinition> FALLBACK_CACHE = new ConcurrentHashMap<>();

    private StageLoader() {}

    // ---------------------------------------------------------------- parsing

    /** Gson binding + defaults. Called by {@link BHData#STAGES} for every discovered file. */
    public static StageDefinition parse(String id, JsonObject root) {
        StageDefinition def = GSON.fromJson(root, StageDefinition.class);
        if (def == null) return null;
        if (def.id == null || def.id.isBlank()) def.id = id;
        applyDefaults(def);
        return def;
    }

    private static void applyDefaults(StageDefinition def) {
        if (def.rules == null) def.rules = new RulesetConfig();
        def.rules.applyPreset();
        if (def.rewards == null) def.rewards = new StageRewards();
        if (def.rewards.onWin == null) def.rewards.onWin = new ArrayList<>();
        if (def.rewards.onLoss == null) def.rewards.onLoss = new ArrayList<>();
        if (def.bossId == null) def.bossId = "marisa_boss";
        if (def.nextStageId == null) def.nextStageId = "";
    }

    // ---------------------------------------------------------------- access

    /** Load a stage by ID. Never null - returns a visible fallback when missing. */
    public static StageDefinition load(String id) {
        StageDefinition def = BHData.STAGES.get(id);
        if (def != null) return def;
        return FALLBACK_CACHE.computeIfAbsent(id, StageLoader::loadFallback);
    }

    /** True when a real definition exists (not a fallback). */
    public static boolean resourceExists(String id) {
        if (id == null || id.isEmpty()) return false;
        return BHData.STAGES.has(id);
    }

    /** All stages in display order. */
    public static List<StageDefinition> loadAll() {
        return BHData.STAGES.all();
    }

    public static List<String> allStageIds() {
        return BHData.STAGES.ids();
    }

    public static void invalidate(String id) {
        FALLBACK_CACHE.remove(id);
    }

    public static void invalidateAll() {
        FALLBACK_CACHE.clear();
    }

    /**
     * In-memory stage that loads only the given boss - for
     * {@code /bullethell start &lt;bossId&gt;} when no stage JSON exists.
     */
    public static StageDefinition syntheticBossOnly(String bossId) {
        StageDefinition def = new StageDefinition();
        def.id = "_boss_" + bossId;
        def.title = bossId;
        def.bossId = bossId;
        def.rules = new RulesetConfig();
        def.rules.applyPreset();
        return def;
    }

    // ---------------------------------------------------------------- internal

    private static StageDefinition loadFallback(String id) {
        StageDefinition def = BHData.STAGES.parseFromClasspath(id);
        if (def != null) return def;
        System.err.println("[BulletHell] Stage definition not found: " + id + " - using fallback");
        return fallback(id);
    }

    /** Minimal fallback: default boss. */
    private static StageDefinition fallback(String id) {
        StageDefinition def = new StageDefinition();
        def.id         = id;
        def.title      = "??? (missing: " + id + ")";
        def.bossId     = "marisa_boss";
        def.stageMusic = null;
        def.rules      = new RulesetConfig();
        def.rules.applyPreset();
        return def;
    }
}
