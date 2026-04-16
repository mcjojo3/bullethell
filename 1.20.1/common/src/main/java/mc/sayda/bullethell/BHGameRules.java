package mc.sayda.bullethell;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

/** BulletHell gamerules. */
public final class BHGameRules {

    /** If true, any paused player freezes their whole arena for all participants. */
    public static final GameRules.Key<GameRules.BooleanValue> GLOBAL_PAUSE = GameRules.register(
            "globalPause",
            GameRules.Category.PLAYER,
            GameRules.BooleanValue.create(true));

    private BHGameRules() {
    }

    /** Class-load hook; no-op on purpose. */
    public static void init() {
    }

    public static boolean isGlobalPauseEnabled(MinecraftServer server) {
        return server != null && server.getGameRules().getBoolean(GLOBAL_PAUSE);
    }
}
