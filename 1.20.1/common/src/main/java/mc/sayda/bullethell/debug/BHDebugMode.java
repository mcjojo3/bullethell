package mc.sayda.bullethell.debug;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Operator-only bullet-hell debug helpers (see {@code /bullethell debug}).
 * Per-player so co-op partners are unaffected unless they also toggle debug.
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

    public static void clear(UUID uuid) {
        if (uuid != null)
            GOD_MODE.remove(uuid);
    }
}
