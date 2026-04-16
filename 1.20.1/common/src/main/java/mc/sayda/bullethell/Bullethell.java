package mc.sayda.bullethell;

import com.mojang.logging.LogUtils;
import mc.sayda.bullethell.entity.BHAttributes;
import mc.sayda.bullethell.entity.BHEntities;
import mc.sayda.bullethell.event.BHCommonEvents;
import mc.sayda.bullethell.network.BHPackets;
import mc.sayda.bullethell.sound.BHSounds;
import org.slf4j.Logger;

public class Bullethell {
    public static final String MODID = "bullethell";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static void init() {
        LOGGER.info("Bullethell (Common) Initializing...");
        BHGameRules.init();
        BHPackets.register();
        BHCommonEvents.register();
        BHSounds.register();
        BHAttributes.register();
        BHEntities.register();
    }
}