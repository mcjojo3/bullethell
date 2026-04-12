package mc.sayda.bullethell.fabric;

import mc.sayda.bullethell.Bullethell;
import net.fabricmc.api.ModInitializer;

public class BullethellFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Bullethell.init();
    }
}
