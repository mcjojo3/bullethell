package mc.sayda.bullethell.entity;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mc.sayda.bullethell.Bullethell;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Registers all BulletHell NPC entity types via Architectury DeferredRegister.
 * Add new NPC entries here; spawn in-world via {@code /summon bullethell:<id>}.
 */
public final class BHEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Bullethell.MODID, Registries.ENTITY_TYPE);

    /**
     * Marisa Kirisame - the first challenge NPC.
     * Hitbox: 0.6 × 1.95 (standard player width/height).
     * Spawned via: {@code /summon bullethell:marisa_npc}
     */
    public static final RegistrySupplier<EntityType<BHNpc>> MARISA_NPC =
            ENTITY_TYPES.register("marisa_npc",
                    () -> EntityType.Builder.<BHNpc>of(
                                    (type, level) -> new BHNpc(type, level, "marisa_npc"),
                                    MobCategory.MISC)
                            .sized(0.6f, 1.95f)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build("marisa_npc"));
    public static final RegistrySupplier<EntityType<BHNpc>> REMILIA_NPC =
            ENTITY_TYPES.register("remilia_npc",
                    () -> EntityType.Builder.<BHNpc>of(
                                    (type, level) -> new BHNpc(type, level, "remilia_npc"),
                                    MobCategory.MISC)
                            .sized(0.6f, 1.95f)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build("remilia_npc"));
    public static final RegistrySupplier<EntityType<BHNpc>> SAKUYA_NPC =
            ENTITY_TYPES.register("sakuya_npc",
                    () -> EntityType.Builder.<BHNpc>of(
                                    (type, level) -> new BHNpc(type, level, "sakuya_npc"),
                                    MobCategory.MISC)
                            .sized(0.6f, 1.95f)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build("sakuya_npc"));
    public static final RegistrySupplier<EntityType<BHNpc>> CIRNO_NPC =
            ENTITY_TYPES.register("cirno_npc",
                    () -> EntityType.Builder.<BHNpc>of(
                                    (type, level) -> new BHNpc(type, level, "cirno_npc"),
                                    MobCategory.MISC)
                            .sized(0.6f, 1.95f)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build("cirno_npc"));   
    public static final RegistrySupplier<EntityType<BHNpc>> SANAE_NPC =
            ENTITY_TYPES.register("sanae_npc",
                    () -> EntityType.Builder.<BHNpc>of(
                                    (type, level) -> new BHNpc(type, level, "sanae_npc"),
                                    MobCategory.MISC)
                            .sized(0.6f, 1.95f)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build("sanae_npc"));
    public static final RegistrySupplier<EntityType<BHNpc>> FLANDRE_NPC =
            ENTITY_TYPES.register("flandre_npc",
                    () -> EntityType.Builder.<BHNpc>of(
                                    (type, level) -> new BHNpc(type, level, "flandre_npc"),
                                    MobCategory.MISC)
                            .sized(0.6f, 1.95f)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build("flandre_npc"));
    public static void register() {
        ENTITY_TYPES.register();
    }
}
