package mc.sayda.bullethell.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mc.sayda.bullethell.config.BullethellConfig;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Loads and caches {@link CharacterDefinition} objects from JSON files
 * at {@code data/bullethell/characters/<id>.json}.
 *
 * The registered character list is built from {@link #REGISTERED_IDS} so we
 * don't need directory scanning (not supported in JAR resources).
 * Add new character IDs here when you create their JSON + texture.
 */
public final class CharacterLoader {

    /**
     * All character IDs available in this build.
     * Order determines the display order on the select screen.
     */
    public static final String[] REGISTERED_IDS = { "reimu", "marisa", "sakuya", "sanae", "cirno" };

    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, CharacterDefinition> CACHE = new HashMap<>();

    private CharacterLoader() {
    }

    /** Load a single character by ID (cached). */
    public static CharacterDefinition load(String id) {
        return CACHE.computeIfAbsent(id, CharacterLoader::readFromClasspath);
    }

    /** Return all registered characters in display order. */
    public static List<CharacterDefinition> loadAll() {
        List<CharacterDefinition> result = new ArrayList<>();
        for (String id : REGISTERED_IDS)
            result.add(load(id));
        return result;
    }

    public static void invalidate(String id) {
        CACHE.remove(id);
    }

    public static void invalidateAll() {
        CACHE.clear();
    }

    // ---------------------------------------------------------------- dev-path support (test mode)

    public static CharacterDefinition loadFromDevPath(String id) {
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath == null || devPath.isBlank()) return null;
        Path file = Paths.get(devPath, "characters", id + ".json");
        if (!Files.exists(file)) return null;
        try (java.io.Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            CharacterDefinition def = GSON.fromJson(r, CharacterDefinition.class);
            if (def == null) return null;
            if (def.id == null) def.id = id;
            resolveShotOptions(def);
            return def;
        } catch (Exception e) {
            System.err.println("[BulletHell/Test] Failed to parse dev character: " + id + " - " + e.getMessage());
            return null;
        }
    }

    public static CharacterDefinition loadWithDevPath(String id) {
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath != null && !devPath.isBlank()) {
            CharacterDefinition dev = loadFromDevPath(id);
            if (dev != null) { CACHE.put(id, dev); return dev; }
        }
        return load(id);
    }

    public static List<String> allCharIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath != null && !devPath.isBlank()) {
            Path dir = Paths.get(devPath, "characters");
            if (Files.isDirectory(dir)) {
                try {
                    Files.list(dir)
                        .filter(p -> p.getFileName().toString().endsWith(".json"))
                        .map(p -> p.getFileName().toString().replace(".json", ""))
                        .sorted()
                        .forEach(ids::add);
                } catch (Exception ignored) {}
            }
        }
        Arrays.stream(REGISTERED_IDS).forEach(ids::add);
        return new ArrayList<>(ids);
    }

    // ---------------------------------------------------------------- internal

    private static CharacterDefinition readFromClasspath(String id) {
        String path = "data/bullethell/characters/" + id + ".json";
        InputStream is = CharacterLoader.class.getClassLoader().getResourceAsStream(path);

        if (is == null) {
            System.err.println("[BulletHell] Character definition not found: " + path + " - using fallback");
            return fallback(id);
        }

        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            CharacterDefinition def = GSON.fromJson(reader, CharacterDefinition.class);
            if (def == null) {
                System.err.println("[BulletHell] Null character definition: " + id);
                return fallback(id);
            }
            if (def.id == null)
                def.id = id;
            resolveShotOptions(def);
            return def;
        } catch (Exception e) {
            System.err.println("[BulletHell] Failed to parse character: " + path + " - " + e.getMessage());
            return fallback(id);
        }
    }

    /**
     * If {@link CharacterDefinition#shotOptions} is null or empty after parsing the character JSON,
     * fills it from {@link HardcodedPlayerShots} (green spread tiers).
     */
    private static void resolveShotOptions(CharacterDefinition def) {
        if (def.shotOptions != null && !def.shotOptions.isEmpty())
            return;
        def.shotOptions = HardcodedPlayerShots.genericCopy();
    }

    private static CharacterDefinition fallback(String id) {
        CharacterDefinition def = new CharacterDefinition();
        def.id = id;
        def.name = "??? (" + id + ")";
        def.description = "Missing character data";
        resolveShotOptions(def);
        return def;
    }
}
