package mc.sayda.bullethell.item.forge;

import dev.architectury.registry.registries.RegistrySupplier;
import mc.sayda.bullethell.entity.BHNpc;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.common.ForgeSpawnEggItem;

public final class BHSpawnEggFactoryImpl {

    public static SpawnEggItem create(RegistrySupplier<EntityType<BHNpc>> entity, int backgroundColor, int highlightColor,
            Item.Properties properties) {
        return new ForgeSpawnEggItem(entity, backgroundColor, highlightColor, properties);
    }

    private BHSpawnEggFactoryImpl() {
    }
}
