package mc.sayda.bullethell;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class Bullethell {
    public static final String MODID = "bullethell";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static void init() {
        LOGGER.info("Bullethell (Common) Initializing...");
    }
}