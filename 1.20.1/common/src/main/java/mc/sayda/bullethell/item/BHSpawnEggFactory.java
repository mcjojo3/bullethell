package mc.sayda.bullethell.item;

import dev.architectury.injectables.annotations.ExpectPlatform;
import dev.architectury.registry.registries.RegistrySupplier;
import mc.sayda.bullethell.entity.BHNpc;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Platform-specific creation: Forge uses {@code ForgeSpawnEggItem} (lazy supplier) because item registration
 * can run before entity registration; Fabric resolves the {@link EntityType} when the item is built.
 */
public final class BHSpawnEggFactory {

    private BHSpawnEggFactory() {
    }

    @ExpectPlatform
    public static SpawnEggItem create(RegistrySupplier<EntityType<BHNpc>> entity, int backgroundColor, int highlightColor,
            Item.Properties properties) {
        throw new AssertionError();
    }
}
