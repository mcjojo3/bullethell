package mc.sayda.bullethell.item;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.sound.BHSounds;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads {@code data/bullethell/music_discs.json}, which maps each music track id
 * (same strings as {@link mc.sayda.bullethell.sound.BHSounds} and boss JSON) to a
 * short game key used for disc textures (e.g. {@code th7} → {@code textures/item/music_disc_th7.png}).
 */
public final class BHMusicDiscMetadata {

    private static final String RESOURCE = "data/bullethell/music_discs.json";
    private static final Gson GSON = new Gson();

    private static Map<String, String> trackToGame = Map.of();
    private static boolean loaded;

    private BHMusicDiscMetadata() {
    }

    public static void ensureLoaded() {
        if (loaded)
            return;
        loaded = true;
        Logger log = Bullethell.LOGGER;
        Map<String, String> map = new HashMap<>();
        try (InputStream in = BHMusicDiscMetadata.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("Missing {}; music discs will use default game key.", RESOURCE);
                trackToGame = Map.of();
                return;
            }
            JsonObject root = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) {
                trackToGame = Map.of();
                return;
            }
            JsonElement tg = root.get("trackGames");
            if (tg != null && tg.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : tg.getAsJsonObject().entrySet()) {
                    if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString()) {
                        map.put(e.getKey(), e.getValue().getAsString());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to load {}", RESOURCE, e);
            trackToGame = Map.of();
            return;
        }
        trackToGame = Collections.unmodifiableMap(map);
        for (String id : BHSounds.getMusicTrackIds()) {
            if (!trackToGame.containsKey(id)) {
                log.warn("music_discs.json missing trackGames entry for {}; add a game key and matching item model parent.", id);
            }
        }
    }

    /**
     * Game key for textures ({@code bullethell:item/music_disc_<game>}, e.g. {@code th7}) and parent models
     * ({@code bullethell:item/music_disc/parent_<game>}).
     */
    public static String gameKeyForTrack(String trackId) {
        ensureLoaded();
        return trackToGame.getOrDefault(trackId, "th_unknown");
    }

    public static Map<String, String> trackGamesView() {
        ensureLoaded();
        return trackToGame;
    }
}
