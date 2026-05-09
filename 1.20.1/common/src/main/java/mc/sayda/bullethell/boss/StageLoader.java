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
    public static final String[] REGISTERED_IDS = { "cirno_stage", "sakuya_stage", "remilia_stage", "marisa_stage", "sanae_stage", "flandre_stage", "kanako_stage" };

    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, StageDefinition> CACHE = new HashMap<>();

    private StageLoader() {}

    /** True if {@code data/bullethell/stages/&lt;id&gt;.json} exists on the classpath. */
    public static boolean resourceExists(String id) {
        if (id == null || id.isEmpty())
            return false;
        String path = "data/bullethell/stages/" + id + ".json";
        InputStream is = StageLoader.class.getClassLoader().getResourceAsStream(path);
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
     * In-memory stage with no waves that loads only the given boss - for
     * {@code /bullethell start &lt;bossId&gt;} when no stage JSON exists.
     */
    public static StageDefinition syntheticBossOnly(String bossId) {
        StageDefinition def = new StageDefinition();
        def.id = "_boss_" + bossId;
        def.title = bossId;
        def.bossId = bossId;
        def.waves = new java.util.ArrayList<>();
        def.rules = new RulesetConfig();
        def.rules.applyPreset();
        return def;
    }

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

    // ---------------------------------------------------------------- dev-path support (test mode)

    private static final Map<String, Long> DEV_MOD_TIMES = new HashMap<>();

    public static StageDefinition loadFromDevPath(String id) {
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath == null || devPath.isBlank()) return null;
        Path file = Paths.get(devPath, "stages", id + ".json");
        if (!Files.exists(file)) return null;
        try (java.io.Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            StageDefinition def = GSON.fromJson(r, StageDefinition.class);
            if (def == null) return null;
            if (def.waves   == null) def.waves = new ArrayList<>();
            if (def.rules   == null) def.rules = new RulesetConfig();
            def.rules.applyPreset();
            if (def.rewards == null) def.rewards = new StageRewards();
            if (def.rewards.onWin  == null) def.rewards.onWin  = new ArrayList<>();
            if (def.rewards.onLoss == null) def.rewards.onLoss = new ArrayList<>();
            if (def.bossId     == null) def.bossId     = "marisa_boss";
            if (def.nextStageId == null) def.nextStageId = "";
            for (WaveDefinition wave : def.waves)
                if (wave.enemies == null) wave.enemies = new ArrayList<>();
            try { DEV_MOD_TIMES.put(id, Files.getLastModifiedTime(file).toMillis()); }
            catch (Exception ignored) {}
            return def;
        } catch (Exception e) {
            System.err.println("[BulletHell/Test] Failed to parse dev stage: " + id + " - " + e.getMessage());
            return null;
        }
    }

    public static StageDefinition loadWithDevPath(String id) {
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath != null && !devPath.isBlank()) {
            StageDefinition dev = loadFromDevPath(id);
            if (dev != null) { CACHE.put(id, dev); return dev; }
        }
        return load(id);
    }

    public static List<String> allStageIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath != null && !devPath.isBlank()) {
            Path dir = Paths.get(devPath, "stages");
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

    public static boolean checkDevFileChanged(String id) {
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath == null || devPath.isBlank()) return false;
        Path file = Paths.get(devPath, "stages", id + ".json");
        try {
            long mtime = Files.getLastModifiedTime(file).toMillis();
            Long prev = DEV_MOD_TIMES.get(id);
            if (prev != null && mtime != prev) { DEV_MOD_TIMES.put(id, mtime); return true; }
            if (prev == null) DEV_MOD_TIMES.put(id, mtime);
        } catch (Exception ignored) {}
        return false;
    }

    /** Synthetic single-wave stage used for wave-preview test mode. */
    public static StageDefinition syntheticWaveOnly(String waveId, List<WaveEnemy> enemies) {
        StageDefinition def  = new StageDefinition();
        def.id               = "_wave_" + waveId;
        def.title            = "Wave: " + waveId;
        def.bossId           = "";
        def.nextStageId      = "";
        def.rules            = new RulesetConfig();
        def.rules.applyPreset();
        def.rewards          = new StageRewards();
        def.rewards.onWin    = new ArrayList<>();
        def.rewards.onLoss   = new ArrayList<>();
        WaveDefinition wave  = new WaveDefinition();
        wave.enemies         = enemies != null ? enemies : new ArrayList<>();
        def.waves            = new ArrayList<>();
        def.waves.add(wave);
        return def;
    }

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
            def.rules.applyPreset();
            if (def.rewards == null) def.rewards = new StageRewards();
            if (def.rewards.onWin  == null) def.rewards.onWin  = new java.util.ArrayList<>();
            if (def.rewards.onLoss == null) def.rewards.onLoss = new java.util.ArrayList<>();
            if (def.bossId  == null) def.bossId = "marisa_boss";
            if (def.nextStageId == null) def.nextStageId = "";
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
        def.stageMusic = null;
        def.rules      = new RulesetConfig();
        def.rules.applyPreset();
        return def;
    }
}
