package mc.sayda.bullethell.pattern;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.sayda.bullethell.config.BullethellConfig;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and caches {@link BulletTypeData} from {@code data/bullethell/bullet_types.json}.
 *
 * Dev-path hot-reload: place {@code bullet_types.json} in the root of
 * {@code BullethellConfig.TEST_DEV_PATH} and call {@link #invalidate()} to pick up changes
 * without restarting the game. The dev path file takes priority over the classpath resource.
 */
public final class BulletTypeLoader {

    private static final String CLASSPATH_PATH = "data/bullethell/bullet_types.json";
    private static Map<BulletType, BulletTypeData> cache = null;

    private BulletTypeLoader() {}

    /** Returns the live data for {@code type}, loading from JSON if necessary. Never null. */
    public static BulletTypeData get(BulletType type) {
        if (cache == null) load();
        BulletTypeData d = cache.get(type);
        return d != null ? d : hardcodedFallback(type);
    }

    /** Clears the cache so the next {@link #get} call re-reads the JSON file. */
    public static void invalidate() {
        cache = null;
    }

    // ---------------------------------------------------------------- internal

    private static void load() {
        cache = new HashMap<>();

        // Dev path takes priority
        String devPath = BullethellConfig.TEST_DEV_PATH != null ? BullethellConfig.TEST_DEV_PATH.get() : null;
        if (devPath != null && !devPath.isBlank()) {
            Path p = Paths.get(devPath, "bullet_types.json");
            if (Files.exists(p)) {
                try (var reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    parseInto(cache, JsonParser.parseReader(reader).getAsJsonObject());
                    return;
                } catch (Exception ignored) {}
            }
        }

        // Classpath fallback
        InputStream is = BulletTypeLoader.class.getClassLoader().getResourceAsStream(CLASSPATH_PATH);
        if (is != null) {
            try (is) {
                parseInto(cache, JsonParser.parseReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception ignored) {}
        }
    }

    private static void parseInto(Map<BulletType, BulletTypeData> map, JsonObject root) {
        // First, handle all keys in the JSON to allow for new dynamic types
        for (String key : root.keySet()) {
            try {
                BulletType type = BulletType.fromName(key);
                if (type.name.equals("ORB") && !key.equalsIgnoreCase("ORB")) {
                    // It's a new type: register it with default values first, 
                    // parseEntry will overwrite with JSON values.
                    type = BulletType.register(key, 0xFFFFFFFF, 4.0f, 1.0f);
                }
                map.put(type, parseEntry(root.getAsJsonObject(key), type));
            } catch (Exception ignored) {}
        }
    }

    private static BulletTypeData parseEntry(JsonObject o, BulletType t) {
        int color = t.color;
        if (o.has("color")) {
            String cs = o.get("color").getAsString();
            long v = Long.parseLong(cs.startsWith("#") ? cs.substring(1) : cs, 16); // #RRGGBBAA
            color = (int)(((v & 0xFF) << 24) | (v >>> 8)); // → AARRGGBB
        }
        float radius = o.has("radius") ? o.get("radius").getAsFloat() : t.radius;
        String texture = o.has("texture") ? o.get("texture").getAsString() : null;
        float texScale = o.has("textureScale") ? o.get("textureScale").getAsFloat() : 2.80f;
        int srcSize = o.has("sourceSize") ? o.get("sourceSize").getAsInt() : 16;
        Float baseAngle = o.has("baseAngleDeg") ? o.get("baseAngleDeg").getAsFloat() : null;
        boolean tint = !o.has("applyTint") || o.get("applyTint").getAsBoolean();
        boolean lineHit = o.has("lineHit") && o.get("lineHit").getAsBoolean();

        float hitboxMul = o.has("hitboxMul") ? o.get("hitboxMul").getAsFloat() : t.hitboxCollisionMul;
        if (Float.isNaN(hitboxMul) || Float.isInfinite(hitboxMul))
            hitboxMul = t.hitboxCollisionMul;
        else if (!lineHit && hitboxMul <= 0f)
            hitboxMul = t.hitboxCollisionMul;
        else if (!lineHit) { /* round bullets: keep mul as-is */ }
        else {
            /* lineHit: hitboxMul kept for getHitboxMul() / legacy; collision width uses lineCollisionHalfWidth */
            if (hitboxMul < 0f)
                hitboxMul = 0f;
            hitboxMul = Math.min(hitboxMul, 4f);
        }

        float lcLen = 0f;
        float lcWid = 0f;
        float lvLen = 0f;
        float lvWid = 0f;

        if (lineHit) {
            if (o.has("lineCollisionHalfLength"))
                lcLen = o.get("lineCollisionHalfLength").getAsFloat();
            else if (o.has("shortLaserHalfLengthBase"))
                lcLen = o.get("shortLaserHalfLengthBase").getAsFloat();
            else
                lcLen = 50f;

            if (o.has("lineCollisionHalfWidth"))
                lcWid = o.get("lineCollisionHalfWidth").getAsFloat();
            else if (o.has("lineHitPerpHalfThickness")) {
                float perp = o.get("lineHitPerpHalfThickness").getAsFloat();
                lcWid = perp * hitboxMul;
            }             else
                lcWid = radius * 0.15f;

            if (Float.isNaN(lcLen) || Float.isInfinite(lcLen))
                lcLen = 50f;
            if (Float.isNaN(lcWid) || Float.isInfinite(lcWid))
                lcWid = 0.2f;
            lcLen = Math.max(0f, lcLen);
            lcWid = Math.max(0f, lcWid);

            if (o.has("lineVisualHalfLength"))
                lvLen = o.get("lineVisualHalfLength").getAsFloat();
            else if (o.has("shortLaserVisualLengthMul"))
                lvLen = lcLen * o.get("shortLaserVisualLengthMul").getAsFloat();
            else
                lvLen = 0f;

            if (o.has("lineVisualHalfWidth"))
                lvWid = o.get("lineVisualHalfWidth").getAsFloat();
            else if (o.has("shortLaserVisualPerpMul"))
                lvWid = lcWid * o.get("shortLaserVisualPerpMul").getAsFloat();
            else
                lvWid = 0f;

            if (Float.isNaN(lvLen) || Float.isInfinite(lvLen))
            lvLen = 0f;
        if (Float.isNaN(lvWid) || Float.isInfinite(lvWid))
            lvWid = 0f;
        }

        boolean homing = o.has("homing") && o.get("homing").getAsBoolean();
        boolean sakuyaBlade = o.has("sakuyaBlade") && o.get("sakuyaBlade").getAsBoolean();

        return new BulletTypeData(color, radius, hitboxMul, texture, texScale, srcSize, baseAngle, tint, lineHit,
                lcLen, lcWid, lvLen, lvWid, homing, sakuyaBlade);
    }

    private static BulletTypeData hardcodedFallback(BulletType type) {
        return new BulletTypeData(
                type.color, type.radius, type.hitboxCollisionMul,
                null, 2.80f, 16, null, true, false,
                0f, 0f, 0f, 0f, false, false);
    }
}
