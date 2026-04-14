package mc.sayda.bullethell.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and caches {@link StageDefinition} objects from JSON files on the
 * classpath.
 *
 * Resource path convention:
 *   {@code data/bullethell/stages/<id>.json}
 *
 * Files in {@code common/src/main/resources/} are bundled into the mod JAR
 * and readable on both sides.  Future: hook {@code AddReloadListenerEvent} and
 * call {@link #invalidateAll()} to support datapack overrides.
 */
public final class StageLoader {

    /**
     * All stage IDs available in this build, in display order.
     * Add new IDs here when you create their JSON file.
     */
    public static final String[] REGISTERED_IDS = { "marisa_stage" };

    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, StageDefinition> CACHE = new HashMap<>();

    private StageLoader() {}

    /** Return all registered stages in display order. */
    public static java.util.List<StageDefinition> loadAll() {
        java.util.List<StageDefinition> result = new java.util.ArrayList<>();
        for (String id : REGISTERED_IDS) result.add(load(id));
        return result;
    }

    /** Load by ID, returning a cached instance if already loaded. */
    public static StageDefinition load(String id) {
        return CACHE.computeIfAbsent(id, StageLoader::readFromClasspath);
    }

    public static void invalidate(String id)  { CACHE.remove(id); }
    public static void invalidateAll()         { CACHE.clear(); }

    // ---------------------------------------------------------------- internal

    private static StageDefinition readFromClasspath(String id) {
        String path = "data/bullethell/stages/" + id + ".json";
        InputStream is = StageLoader.class.getClassLoader().getResourceAsStream(path);

        if (is == null) {
            System.err.println("[BulletHell] Stage definition not found: " + path
                    + " - using fallback");
            return fallback(id);
        }

        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            StageDefinition def = GSON.fromJson(reader, StageDefinition.class);
            if (def == null) {
                System.err.println("[BulletHell] Null stage definition: " + id);
                return fallback(id);
            }
            if (def.waves   == null) def.waves = new java.util.ArrayList<>();
            if (def.rules   == null) def.rules = new RulesetConfig();
            if (def.bossId  == null) def.bossId = "marisa_boss";
            // Validate each wave's enemy list
            for (WaveDefinition wave : def.waves) {
                if (wave.enemies == null) wave.enemies = new java.util.ArrayList<>();
            }
            return def;
        } catch (Exception e) {
            System.err.println("[BulletHell] Failed to parse stage: " + path
                    + " - " + e.getMessage());
            return fallback(id);
        }
    }

    /** Minimal fallback: no waves, default boss. */
    private static StageDefinition fallback(String id) {
        StageDefinition def = new StageDefinition();
        def.id         = id;
        def.title      = "??? (missing: " + id + ")";
        def.bossId     = "marisa_boss";
        def.stageMusic = null;
        def.rules      = new RulesetConfig();
        return def;
    }
}
