package mc.sayda.bullethell.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code data/bullethell/fairy_waves/catalog.json} (shared pool metadata).
 */
public final class FairyWaveCatalogLoader {

    private static final Gson GSON = new GsonBuilder().create();
    private static FairyWaveCatalog cached = null;

    private FairyWaveCatalogLoader() {}

    /**
     * Gson-friendly DTO: root object with a {@code sets} map. List elements must
     * deserialize as {@link FairyWaveCatalogEntry} (TypeToken ensures generics).
     */
    private static final class CatalogFileDto {
        Map<String, List<FairyWaveCatalogEntry>> sets;
    }

    public static FairyWaveCatalog load() {
        if (cached != null) {
            return cached.copy();
        }
        String path = "data/bullethell/fairy_waves/catalog.json";
        InputStream is = FairyWaveCatalogLoader.class.getClassLoader().getResourceAsStream(path);
        if (is == null) {
            System.err.println("[BulletHell] Fairy wave catalog not found: " + path);
            cached = new FairyWaveCatalog();
            return cached.copy();
        }
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            Type dtoType = new TypeToken<CatalogFileDto>() {}.getType();
            CatalogFileDto dto = GSON.fromJson(reader, dtoType);
            FairyWaveCatalog cat = new FairyWaveCatalog();
            if (dto != null && dto.sets != null) {
                cat.sets.putAll(dto.sets);
            }
            if (cat.sets.isEmpty()) {
                System.err.println("[BulletHell] Fairy wave catalog has no sets: " + path);
            }
            cached = cat;
            return cached.copy();
        } catch (Exception e) {
            System.err.println("[BulletHell] Failed to parse fairy wave catalog: " + path
                    + " - " + e.getMessage());
            cached = new FairyWaveCatalog();
            return cached.copy();
        }
    }

    public static void invalidate() {
        cached = null;
    }
}
