package mc.sayda.bullethell.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
            BossDefinition def = GSON.fromJson(reader, BossDefinition.class);
            if (def == null || def.phases == null || def.phases.isEmpty()) {
                System.err.println("[BulletHell] Boss definition has no phases: " + id
                        + " - using fallback");
                return fallback(id);
            }
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
        step.bulletType   = "ORB";
        step.arms         = 8;
        step.speed        = 2.5f;
        phase.attacks.add(step);

        def.phases.add(phase);
        return def;
    }
}
