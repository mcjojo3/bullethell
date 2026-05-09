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
 * Spawn eggs for {@link mc.sayda.bullethell.entity.BHNpc} entities. Colors
 * follow each
 * character's palette (dress / hair / accent tones as base + spots).
 */
public final class BHItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Bullethell.MODID, Registries.ITEM);

    private static Item.Properties eggProps() {
        return new Item.Properties().arch$tab(BHCreativeTabs.NPC_SPAWN_EGGS);
    }

    /** Marisa: black base, white spots */
    public static final RegistrySupplier<SpawnEggItem> MARISA_NPC_SPAWN_EGG = ITEMS.register("marisa_npc_spawn_egg",
            () -> BHSpawnEggFactory.create(BHEntities.MARISA_NPC, 0x3A3332, 0xEBD3D6, eggProps()));

    /** Remilia: pink base, light blue spots */
    public static final RegistrySupplier<SpawnEggItem> REMILIA_NPC_SPAWN_EGG = ITEMS.register("remilia_npc_spawn_egg",
            () -> BHSpawnEggFactory.create(BHEntities.REMILIA_NPC, 0xFFA3A3, 0x9D9BDA, eggProps()));

    /** Sakuya: aqua base, light gray spots */
    public static final RegistrySupplier<SpawnEggItem> SAKUYA_NPC_SPAWN_EGG = ITEMS.register("sakuya_npc_spawn_egg",
            () -> BHSpawnEggFactory.create(BHEntities.SAKUYA_NPC, 0x877FD4, 0xD9D9F9, eggProps()));

    /** Cirno: icy cyan + white */
    public static final RegistrySupplier<SpawnEggItem> CIRNO_NPC_SPAWN_EGG = ITEMS.register("cirno_npc_spawn_egg",
            () -> BHSpawnEggFactory.create(BHEntities.CIRNO_NPC, 0x58ADF4, 0xBCF7FF, eggProps()));

    /** Sanae: white base, green spots */
    public static final RegistrySupplier<SpawnEggItem> SANAE_NPC_SPAWN_EGG = ITEMS.register("sanae_npc_spawn_egg",
            () -> BHSpawnEggFactory.create(BHEntities.SANAE_NPC, 0xCDCAEA, 0x3AC787, eggProps()));

    /** Flandre: deep scarlet + blonde */
    public static final RegistrySupplier<SpawnEggItem> FLANDRE_NPC_SPAWN_EGG = ITEMS.register("flandre_npc_spawn_egg",
            () -> BHSpawnEggFactory.create(BHEntities.FLANDRE_NPC, 0xBE6069, 0xFDCC7F, eggProps()));
    /** Satori: lavender base, pink spots */
    public static final RegistrySupplier<SpawnEggItem> SATORI_NPC_SPAWN_EGG = ITEMS.register("satori_npc_spawn_egg",
            () -> BHSpawnEggFactory.create(BHEntities.SATORI_NPC, 0xD5BCF2, 0xBC555C, eggProps()));

    /** Yuuka: red base, light green spots */
    public static final RegistrySupplier<SpawnEggItem> YUUKA_NPC_SPAWN_EGG = ITEMS.register("yuuka_npc_spawn_egg",
            () -> BHSpawnEggFactory.create(BHEntities.YUUKA_NPC, 0xDE3132, 0xBEEC7F, eggProps()));

    /** Kanako: blue base, yellow spots */
    public static final RegistrySupplier<SpawnEggItem> KANAKO_NPC_SPAWN_EGG = ITEMS.register("kanako_npc_spawn_egg",
            () -> BHSpawnEggFactory.create(BHEntities.KANAKO_NPC, 0xCB1F22, 0x082C90, eggProps()));

    /** Reisen: purple base, red spots */
    // public static final RegistrySupplier<SpawnEggItem> REISEN_NPC_SPAWN_EGG =
    // ITEMS.register("reisen_npc_spawn_egg",
    // () -> BHSpawnEggFactory.create(BHEntities.REISEN_NPC, 0xD5BCF2, 0xBC555C,
    // eggProps()));

    private BHItems() {
    }

    /**
     * Spawn egg item for a registered {@link mc.sayda.bullethell.entity.BHNpc}
     * type; empty if unknown.
     */
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
        if (type == BHEntities.SATORI_NPC.get()) {
            return new ItemStack(SATORI_NPC_SPAWN_EGG.get());
        }
        if (type == BHEntities.YUUKA_NPC.get()) {
            return new ItemStack(YUUKA_NPC_SPAWN_EGG.get());
        }
        if (type == BHEntities.KANAKO_NPC.get()) {
            return new ItemStack(KANAKO_NPC_SPAWN_EGG.get());
        }
        return ItemStack.EMPTY;
    }

    public static void register() {
        BHMusicDiscs.registerInto(ITEMS);
        ITEMS.register();
    }
}
