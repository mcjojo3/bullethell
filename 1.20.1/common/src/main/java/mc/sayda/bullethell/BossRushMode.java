package mc.sayda.bullethell;

/** Dedicated boss-rush toggle. Disabled by default and not user-configurable yet. */
public final class BossRushMode {

    private static final boolean ENABLED = false;

    private BossRushMode() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }
}
