package mc.sayda.bullethell.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and caches {@link NpcDefinition} objects from JSON files on the
 * classpath.
 *
 * Resource path convention:
 *   {@code data/bullethell/npcs/<id>.json}
 */
public final class NpcLoader {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, NpcDefinition> CACHE = new HashMap<>();

    private NpcLoader() {}

    /** Load by ID, returning a cached instance if already loaded. */
    public static NpcDefinition load(String id) {
        return CACHE.computeIfAbsent(id, NpcLoader::readFromClasspath);
    }

    public static void invalidate(String id) { CACHE.remove(id); }
    public static void invalidateAll()        { CACHE.clear(); }

    private static NpcDefinition readFromClasspath(String id) {
        String path = "data/bullethell/npcs/" + id + ".json";
        InputStream is = NpcLoader.class.getClassLoader().getResourceAsStream(path);

        if (is == null) {
            System.err.println("[BulletHell] NPC definition not found: " + path);
            return fallback(id);
        }

        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            NpcDefinition def = GSON.fromJson(reader, NpcDefinition.class);
            if (def == null) {
                System.err.println("[BulletHell] Null NPC definition: " + id);
                return fallback(id);
            }
            return def;
        } catch (Exception e) {
            System.err.println("[BulletHell] Failed to parse NPC: " + path + " - " + e.getMessage());
            return fallback(id);
        }
    }

    private static NpcDefinition fallback(String id) {
        NpcDefinition def = new NpcDefinition();
        def.id = id;
        def.displayName = id;
        def.challengeText = "...";
        def.stageId = "stage_1";
        return def;
    }
}
