package mc.sayda.bullethell.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mc.sayda.bullethell.Bullethell;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * One datapack-backed content folder ({@code data/<ns>/<folder>/*.json}).
 *
 * Discovery and reloading live here; <em>parsing stays with the owning loader</em> so
 * each keeps its own Gson binding and normalization (tier promotion, music arrays,
 * shot-option defaults, ...). The manager only decides which raw json exists.
 *
 * Both sides use the same instance. The server fills it from {@link #apply}; the client
 * fills it from a sync packet via {@link #acceptSync}, parsing through the identical
 * path so the two can never diverge in interpretation.
 */
public final class BHDataManager<T> extends SimplePreparableReloadListener<Map<String, JsonElement>> {

    /** Parses one entry. Returning {@code null} drops it. */
    @FunctionalInterface
    public interface Parser<T> {
        T parse(String id, JsonObject root);
    }

    private final String folder;
    private final Parser<T> parser;

    private volatile Map<String, T> parsed = Collections.emptyMap();
    private volatile Map<String, String> raw = Collections.emptyMap();

    public BHDataManager(String folder, Parser<T> parser) {
        this.folder = folder;
        this.parser = parser;
    }

    public String folder() {
        return folder;
    }

    // ---------------------------------------------------------------- access

    /** Parsed entry, or {@code null} when absent. */
    public T get(String id) {
        return parsed.get(id);
    }

    public boolean has(String id) {
        return parsed.containsKey(id);
    }

    /** Ids in discovery order. */
    public List<String> ids() {
        return new ArrayList<>(parsed.keySet());
    }

    public List<T> all() {
        return new ArrayList<>(parsed.values());
    }

    public boolean isEmpty() {
        return parsed.isEmpty();
    }

    /** Raw json by id, for the sync packet. */
    public Map<String, String> rawForSync() {
        return raw;
    }

    // ---------------------------------------------------------------- reload

    @Nonnull
    @Override
    protected Map<String, JsonElement> prepare(@Nonnull ResourceManager resourceManager,
            @Nonnull ProfilerFiller profiler) {
        return BHJsonFiles.collect(resourceManager, folder);
    }

    @Override
    protected void apply(@Nonnull Map<String, JsonElement> data, @Nonnull ResourceManager resourceManager,
            @Nonnull ProfilerFiller profiler) {
        ingest(data);
        Bullethell.LOGGER.info("[BulletHell] Loaded {} {} entries", parsed.size(), folder);
    }

    /** Client side: rebuild from raw json strings pushed by the server. */
    public void acceptSync(Map<String, String> rawJson) {
        Map<String, JsonElement> data = new LinkedHashMap<>();
        rawJson.forEach((id, text) -> {
            try {
                data.put(id, com.google.gson.JsonParser.parseString(text));
            } catch (Exception e) {
                Bullethell.LOGGER.error("[BulletHell] Bad synced {} entry {}: {}", folder, id, e.getMessage());
            }
        });
        ingest(data);
    }

    private void ingest(Map<String, JsonElement> data) {
        Map<String, T> nextParsed = new LinkedHashMap<>();
        Map<String, String> nextRaw = new LinkedHashMap<>();
        data.forEach((id, element) -> {
            if (element == null || !element.isJsonObject()) return;
            try {
                T value = parser.parse(id, element.getAsJsonObject());
                if (value != null) {
                    nextParsed.put(id, value);
                    nextRaw.put(id, element.toString());
                }
            } catch (Exception e) {
                Bullethell.LOGGER.error("[BulletHell] Failed to load {}/{}: {}", folder, id, e.getMessage());
            }
        });
        parsed = Collections.unmodifiableMap(nextParsed);
        raw = Collections.unmodifiableMap(nextRaw);
    }

    /**
     * Parse a single id straight off the mod jar, ignoring the datapack stack.
     * Used as a fallback when a lookup happens before any reload or sync has run.
     */
    public T parseFromClasspath(String id) {
        JsonElement el = BHJsonFiles.readFromClasspath(folder, id);
        if (el == null || !el.isJsonObject()) return null;
        try {
            return parser.parse(id, el.getAsJsonObject());
        } catch (Exception e) {
            Bullethell.LOGGER.error("[BulletHell] Failed to parse classpath {}/{}: {}", folder, id, e.getMessage());
            return null;
        }
    }
}
