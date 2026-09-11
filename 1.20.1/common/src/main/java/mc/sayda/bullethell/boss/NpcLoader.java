package mc.sayda.bullethell.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import mc.sayda.bullethell.data.BHData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Access to {@link NpcDefinition}s.
 *
 * Discovery, datapack overrides, dev-path layering and client sync are handled by
 * {@link BHData#NPCS}. Note that a datapack can retune an existing NPC but cannot
 * add a brand-new one: the entity type and spawn egg are registered at mod-init from
 * the jar, long before datapacks load.
 */
public final class NpcLoader {

    private static final Gson GSON = new GsonBuilder().create();

    /** Classpath fallback for lookups that happen before any reload or sync. */
    private static final Map<String, NpcDefinition> FALLBACK_CACHE = new ConcurrentHashMap<>();

    private NpcLoader() {}

    /** Gson binding + defaults. Called by {@link BHData#NPCS} for every discovered file. */
    public static NpcDefinition parse(String id, JsonObject root) {
        NpcDefinition def = GSON.fromJson(root, NpcDefinition.class);
        if (def == null) return null;
        if (def.id == null || def.id.isBlank()) def.id = id;
        if (def.displayName == null || def.displayName.isBlank()) def.displayName = id;
        return def;
    }

    /** Load an NPC by ID. Never null - returns a visible fallback when missing. */
    public static NpcDefinition load(String id) {
        NpcDefinition def = BHData.NPCS.get(id);
        if (def != null) return def;
        return FALLBACK_CACHE.computeIfAbsent(id, NpcLoader::loadFallback);
    }

    public static List<String> allNpcIds() {
        return BHData.NPCS.ids();
    }

    public static void invalidate(String id) { FALLBACK_CACHE.remove(id); }

    public static void invalidateAll() { FALLBACK_CACHE.clear(); }

    private static NpcDefinition loadFallback(String id) {
        NpcDefinition def = BHData.NPCS.parseFromClasspath(id);
        if (def != null) return def;
        System.err.println("[BulletHell] NPC definition not found: " + id + " - using fallback");
        NpcDefinition missing = new NpcDefinition();
        missing.id = id;
        missing.displayName = id;
        missing.challengeText = "...";
        missing.stageId = "marisa_stage";
        return missing;
    }
}
