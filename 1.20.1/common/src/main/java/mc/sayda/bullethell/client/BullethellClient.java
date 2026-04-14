package mc.sayda.bullethell.client;

import mc.sayda.bullethell.event.BHClientEvents;
import mc.sayda.bullethell.network.BHClientPackets;
import mc.sayda.bullethell.sound.BHSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class BullethellClient {
    public static void init() {
        BHMusicManager.setSoundProvider(BHSounds::get);
        BHKeyMappings.register();
        BHClientPackets.register();
        BHClientEvents.register();
    }
}
