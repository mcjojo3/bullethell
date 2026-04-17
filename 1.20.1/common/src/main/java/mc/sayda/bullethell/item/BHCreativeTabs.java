package mc.sayda.bullethell.item;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mc.sayda.bullethell.Bullethell;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Creative tab for BulletHell NPC spawn eggs (vanilla spawn-egg tab is populated via
 * registry data; mod eggs use a dedicated Architectury tab).
 */
public final class BHCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Bullethell.MODID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> NPC_SPAWN_EGGS =
            TABS.register("npc_spawn_eggs",
                    () -> CreativeTabRegistry.create(
                            Component.translatable("category.bullethell.npc_spawn_eggs"),
                            () -> new ItemStack(BHItems.MARISA_NPC_SPAWN_EGG.get())));
                       

    private BHCreativeTabs() {
    }

    public static void register() {
        TABS.register();
    }
}
