package mc.sayda.bullethell.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.config.BullethellConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * One registry-style json file ({@code data/<ns>/<file>.json}) whose top-level keys are
 * merged across the datapack stack.
 *
 * Unlike a content folder, overriding here is per-key: a datapack can retune a single
 * bullet type without restating the whole file. Later packs win.
 */
public final class BHMergedJsonManager extends SimplePreparableReloadListener<JsonObject> {

    private final String fileName;
    private final ResourceLocation location;
    private final Runnable onChanged;

    private volatile JsonObject merged = new JsonObject();

    public BHMergedJsonManager(String fileName, Runnable onChanged) {
        this.fileName = fileName;
        this.location = new ResourceLocation(Bullethell.MODID, fileName);
        this.onChanged = onChanged;
    }

    public String fileName() {
        return fileName;
    }

    /** Merged view, or the classpath copy when no reload has run yet. */
    public JsonObject get() {
        if (merged.size() == 0) {
            JsonObject cp = readClasspath();
            if (cp != null) return cp;
        }
        return merged;
    }

    public String rawForSync() {
        return get().toString();
    }

    public void acceptSync(String json) {
        try {
            JsonElement el = JsonParser.parseString(json);
            if (el.isJsonObject()) {
                merged = el.getAsJsonObject();
                if (onChanged != null) onChanged.run();
            }
        } catch (Exception e) {
            Bullethell.LOGGER.error("[BulletHell] Bad synced {}: {}", fileName, e.getMessage());
        }
    }

    @Nonnull
    @Override
    protected JsonObject prepare(@Nonnull ResourceManager resourceManager, @Nonnull ProfilerFiller profiler) {
        JsonObject out = new JsonObject();
        try {
            // Lowest priority first, so later packs overwrite individual keys.
            for (var resource : resourceManager.getResourceStack(location)) {
                try (InputStream is = resource.open();
                     InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    JsonElement el = JsonParser.parseReader(reader);
                    if (!el.isJsonObject()) continue;
                    el.getAsJsonObject().entrySet().forEach(e -> out.add(e.getKey(), e.getValue()));
                } catch (Exception e) {
                    Bullethell.LOGGER.error("[BulletHell] Failed to parse {} from pack {}: {}",
                            fileName, resource.sourcePackId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            Bullethell.LOGGER.error("[BulletHell] Failed to list {}: {}", fileName, e.getMessage());
        }

        applyDevPath(out);
        return out;
    }

    @Override
    protected void apply(@Nonnull JsonObject data, @Nonnull ResourceManager resourceManager,
            @Nonnull ProfilerFiller profiler) {
        merged = data;
        if (onChanged != null) onChanged.run();
        Bullethell.LOGGER.info("[BulletHell] Loaded {} ({} entries)", fileName, data.size());
    }

    private void applyDevPath(JsonObject out) {
        String devPath = BullethellConfig.TEST_DEV_PATH.get();
        if (devPath == null || devPath.isBlank()) return;
        File f = new File(devPath, fileName);
        if (!f.isFile()) return;
        try (FileReader reader = new FileReader(f, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(reader);
            if (!el.isJsonObject()) return;
            el.getAsJsonObject().entrySet().forEach(e -> out.add(e.getKey(), e.getValue()));
            Bullethell.LOGGER.info("[BulletHell] Dev path overrides {}", fileName);
        } catch (Exception e) {
            Bullethell.LOGGER.error("[BulletHell] Failed to parse dev {}: {}", fileName, e.getMessage());
        }
    }

    private JsonObject readClasspath() {
        JsonElement el = BHJsonFiles.readFromClasspathRaw("data/" + Bullethell.MODID + "/" + fileName);
        return (el != null && el.isJsonObject()) ? el.getAsJsonObject() : null;
    }
}
