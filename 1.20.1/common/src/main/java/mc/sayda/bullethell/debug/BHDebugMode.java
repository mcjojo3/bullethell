package mc.sayda.bullethell.debug;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player god-mode for test-mode sessions: max lives/bombs, hit invulnerability, free bombs.
 * This should be enabled only while a player is actively in a test-mode arena.
 */
public final class BHDebugMode {

    private static final Set<UUID> GOD_MODE = ConcurrentHashMap.newKeySet();

    private BHDebugMode() {
    }

    public static boolean isGodMode(UUID uuid) {
        return uuid != null && GOD_MODE.contains(uuid);
    }

    /** @return true if debug is now ON */
    public static boolean toggleGodMode(UUID uuid) {
        if (uuid == null)
            return false;
        if (!GOD_MODE.add(uuid))
            GOD_MODE.remove(uuid);
        return GOD_MODE.contains(uuid);
    }

    public static void setGodMode(UUID uuid, boolean enabled) {
        if (uuid == null)
            return;
        if (enabled)
            GOD_MODE.add(uuid);
        else
            GOD_MODE.remove(uuid);
    }

    public static void clear(UUID uuid) {
        if (uuid != null)
            GOD_MODE.remove(uuid);
    }
}
