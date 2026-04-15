package mc.sayda.bullethell;

import java.util.Locale;
import java.util.Optional;

/**
 * Bullet hell input layout. {@link #TH19} matches Touhou 19-style defaults in
 * {@code BHKeyMappings}; {@link #TH9} matches PoFV-style (hold Z to charge, tap Z to shoot).
 */
public enum BHControlScheme {
    TH19,
    TH9;

    /**
     * Strict parse for commands: only known tokens (th19 / th9 and a few aliases).
     */
    public static Optional<BHControlScheme> tryParse(String raw) {
        if (raw == null)
            return Optional.empty();
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "th19", "19", "um" -> Optional.of(TH19);
            case "th9", "pofv", "9" -> Optional.of(TH9);
            default -> Optional.empty();
        };
    }

    /** Lenient fallback (unknown → TH19). Prefer {@link #tryParse} when validating input. */
    public static BHControlScheme fromString(String raw) {
        return tryParse(raw).orElse(TH19);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
