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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

    // ---------------------------------------------------------------- dev-path support (test mode)

    private static final Map<String, Long> DEV_MOD_TIMES = new HashMap<>();

    public static FairyWaveDefinition loadFromDevPath(String id) {
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath == null || devPath.isBlank()) return null;
        Path file = Paths.get(devPath, "fairy_waves", id + ".json");
        if (!Files.exists(file)) return null;
        try (java.io.Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            FairyWaveDefinition def = GSON.fromJson(r, FairyWaveDefinition.class);
            if (def == null) return null;
            if (def.enemies == null) def.enemies = new ArrayList<>();
            try { DEV_MOD_TIMES.put(id, Files.getLastModifiedTime(file).toMillis()); }
            catch (Exception ignored) {}
            return def;
        } catch (Exception e) {
            System.err.println("[BulletHell/Test] Failed to parse dev wave: " + id + " - " + e.getMessage());
            return null;
        }
    }

    public static FairyWaveDefinition loadWithDevPath(String id) {
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath != null && !devPath.isBlank()) {
            FairyWaveDefinition dev = loadFromDevPath(id);
            if (dev != null) { CACHE.put(id, dev); return dev; }
        }
        return load(id);
    }

    public static List<String> allWaveIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath != null && !devPath.isBlank()) {
            Path dir = Paths.get(devPath, "fairy_waves");
            if (Files.isDirectory(dir)) {
                try {
                    Files.list(dir)
                        .filter(p -> {
                            String n = p.getFileName().toString();
                            return n.endsWith(".json") && !n.equals("catalog.json");
                        })
                        .map(p -> p.getFileName().toString().replace(".json", ""))
                        .sorted()
                        .forEach(ids::add);
                } catch (Exception ignored) {}
            }
        }
        // Classpath fallback: collect wave IDs referenced in the catalog
        try {
            FairyWaveCatalog cat = FairyWaveCatalogLoader.load();
            cat.sets.values().forEach(entries ->
                entries.forEach(e -> { if (e.id != null && !e.id.isBlank()) ids.add(e.id); }));
        } catch (Exception ignored) {}
        return new ArrayList<>(ids);
    }

    public static boolean checkDevFileChanged(String id) {
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath == null || devPath.isBlank()) return false;
        Path file = Paths.get(devPath, "fairy_waves", id + ".json");
        try {
            long mtime = Files.getLastModifiedTime(file).toMillis();
            Long prev = DEV_MOD_TIMES.get(id);
            if (prev != null && mtime != prev) { DEV_MOD_TIMES.put(id, mtime); return true; }
            if (prev == null) DEV_MOD_TIMES.put(id, mtime);
        } catch (Exception ignored) {}
        return false;
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
