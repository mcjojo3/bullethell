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
 * Access to {@link CharacterDefinition}s.
 *
 * Discovery, datapack overrides, dev-path layering and client sync are all handled by
 * {@link BHData#CHARACTERS}; this class owns only the Gson binding and the defaults
 * applied on top of it. Adding a character means adding
 * {@code data/bullethell/characters/<id>.json} - there is no id list to update.
 */
public final class CharacterLoader {

    private static final Gson GSON = new GsonBuilder().create();

    /** Classpath fallback for lookups that happen before any reload or sync. */
    private static final Map<String, CharacterDefinition> FALLBACK_CACHE = new ConcurrentHashMap<>();

    private CharacterLoader() {
    }

    // ---------------------------------------------------------------- parsing

    /** Gson binding + defaults. Called by {@link BHData#CHARACTERS} for every discovered file. */
    public static CharacterDefinition parse(String id, JsonObject root) {
        CharacterDefinition def = GSON.fromJson(root, CharacterDefinition.class);
        if (def == null) return null;
        if (def.id == null || def.id.isBlank()) def.id = id;
        resolveShotOptions(def);
        return def;
    }

    // ---------------------------------------------------------------- access

    /** Load a single character by ID. Never null - returns a visible fallback when missing. */
    public static CharacterDefinition load(String id) {
        CharacterDefinition def = BHData.CHARACTERS.get(id);
        if (def != null) return def;
        return FALLBACK_CACHE.computeIfAbsent(id, CharacterLoader::loadFallback);
    }

    /** All characters in display order. */
    public static List<CharacterDefinition> loadAll() {
        List<CharacterDefinition> all = BHData.CHARACTERS.all();
        return all.isEmpty() ? new ArrayList<>() : all;
    }

    public static List<String> allCharIds() {
        return BHData.CHARACTERS.ids();
    }

    public static void invalidate(String id) {
        FALLBACK_CACHE.remove(id);
    }

    public static void invalidateAll() {
        FALLBACK_CACHE.clear();
    }

    // ---------------------------------------------------------------- internal

    private static CharacterDefinition loadFallback(String id) {
        CharacterDefinition def = BHData.CHARACTERS.parseFromClasspath(id);
        if (def != null) return def;

        System.err.println("[BulletHell] Character definition not found: " + id + " - using fallback");
        CharacterDefinition missing = new CharacterDefinition();
        missing.id = id;
        missing.name = "??? (" + id + ")";
        missing.description1 = "Missing character data";
        resolveShotOptions(missing);
        return missing;
    }

    /**
     * If {@link CharacterDefinition#shotOptions} is null or empty after parsing,
     * fills it from {@link HardcodedPlayerShots} (green spread tiers).
     */
    private static void resolveShotOptions(CharacterDefinition def) {
        if (def.shotOptions != null && !def.shotOptions.isEmpty())
            return;
        def.shotOptions = HardcodedPlayerShots.genericCopy();
    }
}
