package mc.sayda.bullethell.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.config.BullethellConfig;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Discovers {@code data/<namespace>/<folder>/*.json} through the datapack stack, so
 * any pack - the mod jar, a world datapack, or an addon - can add or override entries.
 *
 * Ids are the path below {@code folder} without the {@code .json} suffix, so
 * {@code data/bullethell/characters/reimu.json} yields id {@code "reimu"}.
 * Later packs win, which is what gives datapacks their override.
 *
 * A developer filesystem path ({@link BullethellConfig#TEST_DEV_PATH}) is layered on
 * top when set, using this mod's existing {@code <devPath>/<folder>/<id>.json} layout.
 * That layer <em>replaces</em> the folder wholesale rather than merging, so deleting a
 * dev file is not masked by the stale packaged copy.
 */
public final class BHJsonFiles {

    private BHJsonFiles() {}

    /** All json under {@code folder}, keyed by id, in discovery order. */
    public static Map<String, JsonElement> collect(ResourceManager resourceManager, String folder) {
        Map<String, JsonElement> out = new LinkedHashMap<>();

        if (resourceManager != null) {
            String prefix = folder + "/";
            resourceManager.listResources(folder, rl -> rl.getPath().endsWith(".json"))
                    .forEach((rl, resource) -> {
                        String path = rl.getPath();
                        int start = path.indexOf(prefix);
                        if (start < 0) return;
                        String id = path.substring(start + prefix.length(), path.length() - ".json".length());
                        try (InputStream is = resource.open();
                             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                            out.put(id, JsonParser.parseReader(reader));
                        } catch (Exception e) {
                            Bullethell.LOGGER.error("[BulletHell] Failed to parse {} from pack {}: {}",
                                    rl, resource.sourcePackId(), e.getMessage());
                        }
                    });
        }

        applyDevPath(folder, out);
        return out;
    }

    /** Read one json straight off the mod jar, bypassing the datapack stack. */
    public static JsonElement readFromClasspath(String folder, String id) {
        return readFromClasspathRaw("data/" + Bullethell.MODID + "/" + folder + "/" + id + ".json");
    }

    /** Read a json resource by full classpath path. */
    public static JsonElement readFromClasspathRaw(String path) {
        InputStream is = BHJsonFiles.class.getClassLoader().getResourceAsStream(path);
        if (is == null) return null;
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (Exception e) {
            Bullethell.LOGGER.error("[BulletHell] Failed to parse classpath {}: {}", path, e.getMessage());
            return null;
        }
    }

    private static void applyDevPath(String folder, Map<String, JsonElement> out) {
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath == null || devPath.isBlank()) return;

        File dir = new File(devPath, folder);
        if (!dir.isDirectory()) return;

        // Wholesale replace so a deleted dev file does not fall back to the packaged copy.
        Map<String, JsonElement> dev = new LinkedHashMap<>();
        scanDev(dir, "", dev);
        if (dev.isEmpty()) return;

        out.clear();
        out.putAll(dev);
        Bullethell.LOGGER.info("[BulletHell] Dev path overrides {} ({} entries from {})",
                folder, dev.size(), dir.getAbsolutePath());
    }

    private static void scanDev(File dir, String prefix, Map<String, JsonElement> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanDev(f, prefix + f.getName() + "/", out);
            } else if (f.getName().endsWith(".json")) {
                try (FileReader reader = new FileReader(f, StandardCharsets.UTF_8)) {
                    String id = prefix + f.getName().substring(0, f.getName().length() - ".json".length());
                    out.put(id, JsonParser.parseReader(reader));
                } catch (Exception e) {
                    Bullethell.LOGGER.error("[BulletHell] Failed to parse dev file {}: {}",
                            f.getAbsolutePath(), e.getMessage());
                }
            }
        }
    }
}
