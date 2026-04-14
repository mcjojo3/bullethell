package mc.sayda.bullethell.forge;

import dev.architectury.platform.forge.EventBuses;
import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.client.BullethellClient;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Bullethell.MODID)
public class BullethellForge {
    public BullethellForge() {
        EventBuses.registerModEventBus(Bullethell.MODID, FMLJavaModLoadingContext.get().getModEventBus());
        Bullethell.init();
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            BullethellClient.init();
        }
    }
}
