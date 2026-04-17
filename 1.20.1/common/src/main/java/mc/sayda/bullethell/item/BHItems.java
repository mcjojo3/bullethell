package mc.sayda.bullethell.item;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.entity.BHEntities;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Spawn eggs for {@link mc.sayda.bullethell.entity.BHNpc} entities. Colors follow each
 * character's palette (dress / hair / accent tones as base + spots).
 */
public final class BHItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Bullethell.MODID, Registries.ITEM);

    private static Item.Properties eggProps() {
        return new Item.Properties().arch$tab(BHCreativeTabs.NPC_SPAWN_EGGS);
    }

    /** Marisa: black base, white spots */
    public static final RegistrySupplier<SpawnEggItem> MARISA_NPC_SPAWN_EGG =
            ITEMS.register("marisa_npc_spawn_egg",
                    () -> BHSpawnEggFactory.create(BHEntities.MARISA_NPC, 0x3A3332, 0xEBD3D6, eggProps()));

    /** Remilia: pink base, light blue spots */
    public static final RegistrySupplier<SpawnEggItem> REMILIA_NPC_SPAWN_EGG =
            ITEMS.register("remilia_npc_spawn_egg",
                    () -> BHSpawnEggFactory.create(BHEntities.REMILIA_NPC, 0xFFA3A3, 0x9D9BDA, eggProps()));

    /** Sakuya: aqua base, light gray spots */
    public static final RegistrySupplier<SpawnEggItem> SAKUYA_NPC_SPAWN_EGG =
            ITEMS.register("sakuya_npc_spawn_egg",
                    () -> BHSpawnEggFactory.create(BHEntities.SAKUYA_NPC, 0x877FD4, 0xD9D9F9, eggProps()));

    /** Cirno: icy cyan + white */
    public static final RegistrySupplier<SpawnEggItem> CIRNO_NPC_SPAWN_EGG =
            ITEMS.register("cirno_npc_spawn_egg",
                    () -> BHSpawnEggFactory.create(BHEntities.CIRNO_NPC, 0x58ADF4, 0xBCF7FF, eggProps()));

    /** Sanae: white base, green spots */
    public static final RegistrySupplier<SpawnEggItem> SANAE_NPC_SPAWN_EGG =
            ITEMS.register("sanae_npc_spawn_egg",
                    () -> BHSpawnEggFactory.create(BHEntities.SANAE_NPC, 0xCDCAEA, 0x3AC787, eggProps()));

    /** Flandre: deep scarlet + blonde */
    public static final RegistrySupplier<SpawnEggItem> FLANDRE_NPC_SPAWN_EGG =
            ITEMS.register("flandre_npc_spawn_egg",
                    () -> BHSpawnEggFactory.create(BHEntities.FLANDRE_NPC, 0xBE6069, 0xFDCC7F, eggProps()));

    private BHItems() {
    }

    /** Spawn egg item for a registered {@link mc.sayda.bullethell.entity.BHNpc} type; empty if unknown. */
    public static ItemStack spawnEggStackFor(EntityType<?> type) {
        if (type == BHEntities.MARISA_NPC.get()) {
            return new ItemStack(MARISA_NPC_SPAWN_EGG.get());
        }
        if (type == BHEntities.REMILIA_NPC.get()) {
            return new ItemStack(REMILIA_NPC_SPAWN_EGG.get());
        }
        if (type == BHEntities.SAKUYA_NPC.get()) {
            return new ItemStack(SAKUYA_NPC_SPAWN_EGG.get());
        }
        if (type == BHEntities.CIRNO_NPC.get()) {
            return new ItemStack(CIRNO_NPC_SPAWN_EGG.get());
        }
        if (type == BHEntities.SANAE_NPC.get()) {
            return new ItemStack(SANAE_NPC_SPAWN_EGG.get());
        }
        if (type == BHEntities.FLANDRE_NPC.get()) {
            return new ItemStack(FLANDRE_NPC_SPAWN_EGG.get());
        }
        return ItemStack.EMPTY;
    }

    public static void register() {
        ITEMS.register();
    }
}
