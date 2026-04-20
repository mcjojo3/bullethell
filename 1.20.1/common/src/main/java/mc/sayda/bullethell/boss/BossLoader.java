package mc.sayda.bullethell.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
 * Loads and caches {@link BossDefinition} objects from JSON files on the classpath.
 *
 * Resource path convention:
 *   {@code data/bullethell/bosses/<id>.json}
 *
 * Files placed in {@code common/src/main/resources/} are bundled into the mod JAR
 * and are readable at runtime by any classloader.  Datapacks can shadow these with
 * their own copies once a full {@code ReloadableServerResources} hook is wired up.
 *
 * If a file is missing or malformed, a single-phase fallback definition is returned
 * so the arena still starts without crashing.
 */
public final class BossLoader {

    /**
     * Boss JSON ids shipped with the mod (used for tab-complete and when classpath
     * directory listing is unavailable).
     */
    public static final String[] REGISTERED_IDS = {
            "cirno_boss", "flandre_boss", "marisa_boss", "remilia_boss", "sakuya_boss", "sanae_boss"
    };

    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, BossDefinition> CACHE = new HashMap<>();

    private BossLoader() {}

    /** True if {@code data/bullethell/bosses/&lt;id&gt;.json} exists on the classpath (not a fallback). */
    public static boolean resourceExists(String id) {
        if (id == null || id.isEmpty())
            return false;
        String path = "data/bullethell/bosses/" + id + ".json";
        InputStream is = BossLoader.class.getClassLoader().getResourceAsStream(path);
        if (is != null) {
            try {
                is.close();
            } catch (Exception ignored) {
            }
            return true;
        }
        return false;
    }

    /**
     * Load a boss definition by ID, returning a cached instance if already loaded.
     *
     * @param id  boss file name without extension, e.g. {@code "marisa_boss"}
     */
    public static BossDefinition load(String id) {
        return CACHE.computeIfAbsent(id, BossLoader::readFromClasspath);
    }

    /** Force-reload a definition (useful after a /reload in development). */
    public static void invalidate(String id) {
        CACHE.remove(id);
    }

    /** Clear the entire cache (e.g. on datapack reload). */
    public static void invalidateAll() {
        CACHE.clear();
    }

    // ---------------------------------------------------------------- internal

    private static BossDefinition readFromClasspath(String id) {
        String path = "data/bullethell/bosses/" + id + ".json";
        InputStream is = BossLoader.class.getClassLoader().getResourceAsStream(path);

        if (is == null) {
            System.err.println("[BulletHell] Boss definition not found on classpath: " + path
                    + " - using fallback");
            return fallback(id);
        }

        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            TierJson.migrateLegacyByDifficultyKeysOnBoss(root);
            TierJson.promoteUnionTierFieldsOnBoss(root);
            BossDefinition def = GSON.fromJson(root, BossDefinition.class);
            if (def == null || def.phases == null || def.phases.isEmpty()) {
                System.err.println("[BulletHell] Boss definition has no phases: " + id
                        + " - using fallback");
                return fallback(id);
            }
            DifficultyTierArray.normalizeBossDefinition(def);
            // Ensure every phase has at least one attack step
            for (PhaseDefinition phase : def.phases) {
                if (phase.attacks == null || phase.attacks.isEmpty()) {
                    PatternStep ring = new PatternStep();
                    ring.pattern = "RING";
                    ring.arms    = 8;
                    ring.speed   = 2.0f;
                    phase.attacks = new java.util.ArrayList<>();
                    phase.attacks.add(ring);
                }
                if (phase.spellDurationTicks == null || phase.spellDurationTicks.length < 4) {
                    phase.spellDurationTicks = new int[]{600, 450, 300, 150};
                }
            }
            return def;
        } catch (Exception e) {
            System.err.println("[BulletHell] Failed to parse boss definition: " + path
                    + " - " + e.getMessage());
            return fallback(id);
        }
    }

    // ---------------------------------------------------------------- dev-path support (test mode)

    /** Last known file-modification times for dev-path files; used for auto-reload polling. */
    private static final Map<String, Long> DEV_MOD_TIMES = new HashMap<>();

    /**
     * Load boss JSON from {@link BullethellConfig#TEST_DEV_PATH} if set and file exists.
     * Result is cached so repeated calls are cheap. Returns {@code null} if dev path is
     * not configured, file is absent, or parse fails.
     */
    public static BossDefinition loadFromDevPath(String id) {
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath == null || devPath.isBlank()) return null;
        Path file = Paths.get(devPath, "bosses", id + ".json");
        if (!Files.exists(file)) return null;
        try (java.io.Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            TierJson.migrateLegacyByDifficultyKeysOnBoss(root);
            TierJson.promoteUnionTierFieldsOnBoss(root);
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
            // Track mod time for auto-reload
            try { DEV_MOD_TIMES.put(id, Files.getLastModifiedTime(file).toMillis()); }
            catch (Exception ignored) {}
            return def;
        } catch (Exception e) {
            System.err.println("[BulletHell/Test] Failed to parse dev boss: " + id + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Load boss by ID, checking dev path first (if configured), then classpath cache.
     * Used by the test-mode server and by {@link mc.sayda.bullethell.client.TestModeHud}.
     */
    public static BossDefinition loadWithDevPath(String id) {
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath != null && !devPath.isBlank()) {
            BossDefinition dev = loadFromDevPath(id);
            if (dev != null) {
                CACHE.put(id, dev);
                return dev;
            }
        }
        return load(id);
    }

    /**
     * Returns combined list of boss IDs: dev-path JSONs first (sorted), then classpath
     * {@link #REGISTERED_IDS}. Duplicate IDs are collapsed (dev-path wins).
     */
    public static List<String> allBossIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath != null && !devPath.isBlank()) {
            Path dir = Paths.get(devPath, "bosses");
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

    /**
     * Returns {@code true} if the dev-path file for {@code id} has been modified since
     * the last time it was loaded. Resets the stored timestamp on each check so consecutive
     * calls only fire once per change. Safe to call every few server ticks.
     */
    public static boolean checkDevFileChanged(String id) {
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath == null || devPath.isBlank()) return false;
        Path file = Paths.get(devPath, "bosses", id + ".json");
        try {
            long mtime = Files.getLastModifiedTime(file).toMillis();
            Long prev = DEV_MOD_TIMES.get(id);
            if (prev != null && mtime != prev) {
                DEV_MOD_TIMES.put(id, mtime);
                return true;
            }
            if (prev == null) DEV_MOD_TIMES.put(id, mtime);
        } catch (Exception ignored) {}
        return false;
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
        phase.moveSpeed    = 140f;

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
