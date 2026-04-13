package mc.sayda.bullethell.forge;

import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.forge.network.BHNetwork;
import mc.sayda.bullethell.forge.sound.BHSounds;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Bullethell.MODID)
public class BullethellForge {
    public BullethellForge() {
        Bullethell.init();
        BHNetwork.register();
        BHSounds.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
