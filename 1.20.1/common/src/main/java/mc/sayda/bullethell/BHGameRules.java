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

    /**
     * When true, each client simulates its own copy of the fight and the server keeps
     * only shared truth (boss HP, phase, score). Cuts per-tick traffic from tens of
     * kilobytes to tens of bytes, at the cost of each client seeing a slightly
     * different bullet layout. Off by default until the sim is proven.
     */
    public static final GameRules.Key<GameRules.BooleanValue> CLIENT_SIM = GameRules.register(
            "bulletHellClientSim",
            GameRules.Category.MISC,
            GameRules.BooleanValue.create(false));

    private BHGameRules() {
    }

    /** Class-load hook; no-op on purpose. */
    public static void init() {
    }

    public static boolean isGlobalPauseEnabled(MinecraftServer server) {
        return server != null && server.getGameRules().getBoolean(GLOBAL_PAUSE);
    }

    public static boolean isClientSimEnabled(MinecraftServer server) {
        return server != null && server.getGameRules().getBoolean(CLIENT_SIM);
    }
}
