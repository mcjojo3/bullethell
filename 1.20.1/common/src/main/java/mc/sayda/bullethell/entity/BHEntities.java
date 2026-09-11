package mc.sayda.bullethell.entity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.boss.NpcDefinition;
import mc.sayda.bullethell.data.BHClasspathScan;
import mc.sayda.bullethell.data.BHJsonFiles;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NPC entity types, one per {@code data/bullethell/npcs/<id>.json} found in the mod jar.
 *
 * There is no per-NPC code: adding the json is all that is required. The scan reads the
 * jar rather than the datapack stack because entity registries are frozen before
 * datapacks load, and registering a type the client's jar lacks would desync.
 * Everything about an NPC's <em>behaviour</em> is still datapack-overridable via
 * {@link mc.sayda.bullethell.boss.NpcLoader}.
 */
public final class BHEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Bullethell.MODID, Registries.ENTITY_TYPE);

    private static final Gson GSON = new GsonBuilder().create();

    /** npc id → entity type, in scan order. */
    public static final Map<String, RegistrySupplier<EntityType<BHNpc>>> NPCS;

    /** npc id → the jar-time definition used for registration (hitbox, egg colours). */
    public static final Map<String, NpcDefinition> INIT_DEFS;

    static {
        Map<String, RegistrySupplier<EntityType<BHNpc>>> npcs = new LinkedHashMap<>();
        Map<String, NpcDefinition> defs = new LinkedHashMap<>();

        for (String id : BHClasspathScan.ids("npcs")) {
            NpcDefinition def = readInitDef(id);
            defs.put(id, def);
            npcs.put(id, ENTITY_TYPES.register(id,
                    () -> EntityType.Builder.<BHNpc>of(
                            (type, level) -> new BHNpc(type, level, id),
                            MobCategory.MISC)
                            .sized(def.width, def.height)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build(id)));
        }

        NPCS = Collections.unmodifiableMap(npcs);
        INIT_DEFS = Collections.unmodifiableMap(defs);
        Bullethell.LOGGER.info("[BulletHell] Registering {} NPC entity types: {}", npcs.size(), npcs.keySet());
    }

    private BHEntities() {
    }

    /** Jar-time definition, never null. */
    public static NpcDefinition initDef(String id) {
        NpcDefinition def = INIT_DEFS.get(id);
        return def != null ? def : fallback(id);
    }

    private static NpcDefinition readInitDef(String id) {
        try {
            JsonElement el = BHJsonFiles.readFromClasspath("npcs", id);
            if (el != null && el.isJsonObject()) {
                NpcDefinition def = GSON.fromJson(el, NpcDefinition.class);
                if (def != null) {
                    if (def.id == null || def.id.isBlank()) def.id = id;
                    if (def.displayName == null || def.displayName.isBlank()) def.displayName = id;
                    return def;
                }
            }
        } catch (Exception e) {
            Bullethell.LOGGER.error("[BulletHell] Bad npc json {} at init: {}", id, e.getMessage());
        }
        return fallback(id);
    }

    private static NpcDefinition fallback(String id) {
        NpcDefinition def = new NpcDefinition();
        def.id = id;
        def.displayName = id;
        return def;
    }

    public static void register() {
        ENTITY_TYPES.register();
    }
}
