package mc.sayda.bullethell.item.fabric;

import dev.architectury.registry.registries.RegistrySupplier;
import mc.sayda.bullethell.entity.BHNpc;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public final class BHSpawnEggFactoryImpl {

    public static SpawnEggItem create(RegistrySupplier<EntityType<BHNpc>> entity, int backgroundColor, int highlightColor,
            Item.Properties properties) {
        return new SpawnEggItem(entity.get(), backgroundColor, highlightColor, properties);
    }

    private BHSpawnEggFactoryImpl() {
    }
}
