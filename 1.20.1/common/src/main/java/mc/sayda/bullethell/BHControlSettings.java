package mc.sayda.bullethell;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side scheme plus a server-side mirror for {@code /bullethell controls} (no scheme).
 */
public final class BHControlSettings {

    private static volatile BHControlScheme clientScheme = BHControlScheme.TH19;

    private static final ConcurrentHashMap<UUID, String> SERVER_MIRROR = new ConcurrentHashMap<>();

    private BHControlSettings() {
    }

    public static BHControlScheme get() {
        return clientScheme;
    }

    /** Called from S2C packet on the game client. */
    public static void applyFromNetwork(BHControlScheme scheme) {
        clientScheme = scheme != null ? scheme : BHControlScheme.TH19;
    }

    public static void serverSetPreference(UUID player, BHControlScheme scheme) {
        if (player != null && scheme != null)
            SERVER_MIRROR.put(player, scheme.id());
    }

    public static String serverGetPreferenceId(UUID player) {
        if (player == null)
            return BHControlScheme.TH19.id();
        return SERVER_MIRROR.getOrDefault(player, BHControlScheme.TH19.id());
    }

    public static BHControlScheme serverGetPreference(UUID player) {
        return BHControlScheme.fromString(serverGetPreferenceId(player));
    }

    public static String describe(BHControlScheme s) {
        if (s == BHControlScheme.TH9)
            return "th9 (PoFV): tap Z to shoot; hold Z to charge; release Z (or X/C) to cast; bomb on V; X and C both act as extra cast keys.";
        return "th19 (UM): hold Z to shoot; hold X to charge, release X to cast; bomb on C.";
    }

    public static String recommendText() {
        return "Recommended (defaults):\n"
                + "  th19 — Touhou 19-style: hold Z shoot, hold X charge / release X cast, C bomb, Shift focus.\n"
                + "  th9  — Touhou 9 / PoFV-style: tap Z shoot, hold Z charge / release Z cast; V bomb; X and C duplicate cast keys.\n"
                + "Use: /bullethell controls <th19|th9>";
    }

    public static String formatList() {
        return String.join(", ",
                java.util.Arrays.stream(BHControlScheme.values())
                        .map(v -> v.id().toUpperCase(Locale.ROOT))
                        .toList());
    }
}
