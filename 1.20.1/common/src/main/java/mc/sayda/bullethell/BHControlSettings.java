package mc.sayda.bullethell;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;

/**
 * Client-side scheme plus a server-side mirror for {@code /bullethell controls} (no scheme).
 */
public final class BHControlSettings {

    private static volatile BHControlScheme clientScheme = BHControlScheme.TH19;
    private static final String TAG_SCHEME_TH19 = "bullethell.controls.th19";
    private static final String TAG_SCHEME_TH9 = "bullethell.controls.th9";

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

    private static String tagFor(BHControlScheme scheme) {
        return scheme == BHControlScheme.TH9 ? TAG_SCHEME_TH9 : TAG_SCHEME_TH19;
    }

    /** Persisted on player entity tags; survives logout/restart. */
    public static void serverSetPreference(ServerPlayer player, BHControlScheme scheme) {
        if (player == null || scheme == null)
            return;
        SERVER_MIRROR.put(player.getUUID(), scheme.id());
        player.removeTag(TAG_SCHEME_TH19);
        player.removeTag(TAG_SCHEME_TH9);
        player.addTag(tagFor(scheme));
    }

    public static void serverSetPreference(UUID player, BHControlScheme scheme) {
        if (player != null && scheme != null)
            SERVER_MIRROR.put(player, scheme.id());
    }

    public static String serverGetPreferenceId(ServerPlayer player) {
        if (player == null)
            return BHControlScheme.TH19.id();
        BHControlScheme scheme = serverGetPreference(player);
        return scheme.id();
    }

    public static String serverGetPreferenceId(UUID player) {
        if (player == null)
            return BHControlScheme.TH19.id();
        return SERVER_MIRROR.getOrDefault(player, BHControlScheme.TH19.id());
    }

    public static BHControlScheme serverGetPreference(ServerPlayer player) {
        if (player == null)
            return BHControlScheme.TH19;
        BHControlScheme resolved;
        if (player.getTags().contains(TAG_SCHEME_TH9)) {
            resolved = BHControlScheme.TH9;
        } else if (player.getTags().contains(TAG_SCHEME_TH19)) {
            resolved = BHControlScheme.TH19;
        } else {
            resolved = BHControlScheme.fromString(serverGetPreferenceId(player.getUUID()));
        }
        SERVER_MIRROR.put(player.getUUID(), resolved.id());
        return resolved;
    }

    public static BHControlScheme serverGetPreference(UUID player) {
        return BHControlScheme.fromString(serverGetPreferenceId(player));
    }

    public static String describe(BHControlScheme s) {
        if (s == BHControlScheme.TH9)
            return "th9 (PoFV): tap Z to shoot; hold Z to charge; release Z to cast; bomb on C or X (same as th19 C bomb).";
        return "th19 (UM): hold Z to shoot; hold X to charge, release X to cast; bomb on C.";
    }

    public static String recommendText() {
        return "Recommended (defaults):\n"
                + "  th19 - Touhou 19-style: hold Z shoot, hold X charge / release X cast, C bomb, Shift focus.\n"
                + "  th9  - Touhou 9 / PoFV-style: tap Z shoot, hold Z charge / release Z cast; C or X bomb (same behavior).\n"
                + "Use: /bullethell controls <th19|th9>";
    }

    public static String formatList() {
        return String.join(", ",
                java.util.Arrays.stream(BHControlScheme.values())
                        .map(v -> v.id().toUpperCase(Locale.ROOT))
                        .toList());
    }
}
