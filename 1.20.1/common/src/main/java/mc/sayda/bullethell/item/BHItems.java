package mc.sayda.bullethell.item;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.boss.NpcDefinition;
import mc.sayda.bullethell.entity.BHEntities;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spawn eggs for {@link mc.sayda.bullethell.entity.BHNpc} entities.
 *
 * One egg per registered NPC, built in the same scan order as {@link BHEntities}.
 * Colours come from {@code eggPrimaryColor} / {@code eggSecondaryColor} in the NPC json.
 */
public final class BHItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Bullethell.MODID, Registries.ITEM);

    /** npc id → spawn egg. */
    public static final Map<String, RegistrySupplier<SpawnEggItem>> NPC_SPAWN_EGGS;

    static {
        Map<String, RegistrySupplier<SpawnEggItem>> eggs = new LinkedHashMap<>();
        BHEntities.NPCS.forEach((id, entity) -> {
            NpcDefinition def = BHEntities.initDef(id);
            eggs.put(id, ITEMS.register(id + "_spawn_egg",
                    () -> BHSpawnEggFactory.create(entity,
                            def.resolveEggPrimary(), def.resolveEggSecondary(), eggProps())));
        });
        NPC_SPAWN_EGGS = Collections.unmodifiableMap(eggs);
    }

    private BHItems() {
    }

    private static Item.Properties eggProps() {
        return new Item.Properties().arch$tab(BHCreativeTabs.NPC_SPAWN_EGGS);
    }

    /** The first registered NPC egg, used as the creative tab icon. Empty if there are none. */
    public static ItemStack tabIcon() {
        for (RegistrySupplier<SpawnEggItem> egg : NPC_SPAWN_EGGS.values()) {
            return new ItemStack(egg.get());
        }
        return ItemStack.EMPTY;
    }

    /**
     * Spawn egg item for a registered {@link mc.sayda.bullethell.entity.BHNpc}
     * type; empty if unknown.
     */
    public static ItemStack spawnEggStackFor(EntityType<?> type) {
        for (Map.Entry<String, RegistrySupplier<EntityType<mc.sayda.bullethell.entity.BHNpc>>> e
                : BHEntities.NPCS.entrySet()) {
            if (e.getValue().get() == type) {
                RegistrySupplier<SpawnEggItem> egg = NPC_SPAWN_EGGS.get(e.getKey());
                if (egg != null) return new ItemStack(egg.get());
            }
        }
        return ItemStack.EMPTY;
    }

    public static void register() {
        BHMusicDiscs.registerInto(ITEMS);
        ITEMS.register();
    }
}
