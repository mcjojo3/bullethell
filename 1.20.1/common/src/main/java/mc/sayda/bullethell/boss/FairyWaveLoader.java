package mc.sayda.bullethell.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and caches {@link FairyWaveDefinition} objects from JSON files on
 * the classpath.
 *
 * Resource path convention:
 *   {@code data/bullethell/fairy_waves/<id>.json}
 *
 * Call {@link #invalidateAll()} on datapack reload to clear the cache.
 */
public final class FairyWaveLoader {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, FairyWaveDefinition> CACHE = new HashMap<>();

    private FairyWaveLoader() {}

    /**
     * Load a fairy wave template by ID, returning a cached instance if already
     * loaded.  Returns an empty {@link FairyWaveDefinition} (no enemies) if the
     * file is not found, logging a warning to stderr.
     */
    public static FairyWaveDefinition load(String id) {
        return CACHE.computeIfAbsent(id, FairyWaveLoader::readFromClasspath);
    }

    public static void invalidate(String id) { CACHE.remove(id); }

    public static void invalidateAll() {
        CACHE.clear();
        FairyWaveCatalogLoader.invalidate();
    }

    // ---------------------------------------------------------------- internal

    private static FairyWaveDefinition readFromClasspath(String id) {
        String path = "data/bullethell/fairy_waves/" + id + ".json";
        InputStream is = FairyWaveLoader.class.getClassLoader().getResourceAsStream(path);

        if (is == null) {
            System.err.println("[BulletHell] Fairy wave template not found: " + path);
            return fallback(id);
        }

        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            FairyWaveDefinition def = GSON.fromJson(reader, FairyWaveDefinition.class);
            if (def == null) {
                System.err.println("[BulletHell] Null fairy wave definition: " + id);
                return fallback(id);
            }
            if (def.enemies == null) def.enemies = new ArrayList<>();
            return def;
        } catch (Exception e) {
            System.err.println("[BulletHell] Failed to parse fairy wave: " + path
                    + " - " + e.getMessage());
            return fallback(id);
        }
    }

    private static FairyWaveDefinition fallback(String id) {
        FairyWaveDefinition def = new FairyWaveDefinition();
        def.id = id;
        def.description = "missing";
        return def;
    }
}
