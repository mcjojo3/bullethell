package mc.sayda.bullethell.fabric.client;

import mc.sayda.bullethell.client.BullethellClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class BullethellFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BullethellClient.init();
    }
}
