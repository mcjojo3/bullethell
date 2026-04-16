package mc.sayda.bullethell.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete definition of a boss fight, loaded from a JSON datafile.
 *
 * Place JSON files at:
 *   {@code data/bullethell/bosses/<id>.json}
 * inside the mod JAR (i.e., in {@code common/src/main/resources/}).
 *
 * The fight proceeds through {@link #phases} in order.  Phase 0 starts
 * immediately; each subsequent phase begins when the previous phase's HP
 * reaches zero.  The encounter ends when the last phase is cleared.
 */
public class BossDefinition {

    /** Unique ID - must match the JSON file name (without extension). */
    public String id = "unknown";

    /** Human-readable display name shown in the HUD and dialog boxes. */
    public String name = "Unknown Boss";

    /**
     * Optional intro dialogue shown after all waves clear and before the fight
     * begins.  Lines are auto-advanced by their {@code delayTicks} value.
     * Leave empty to skip straight to phase 0.
     */
    public List<DialogLine> introDialog = new ArrayList<>();

    /**
     * Character-specific intro dialogues.  If a match is found for the player's
     * character ID (e.g., "reimu"), it takes priority over {@link #introDialog}.
     */
    public Map<String, List<DialogLine>> characterDialogs = new HashMap<>();

    /** Optional one-line boss quote shown when the player wins this fight. */
    public String victoryDialog = "";

    /** Optional one-line boss quote shown when the player loses this fight. */
    public String defeatDialog = "";

    /**
     * Character-specific win quote overrides (keyed by player character id).
     * Used only when the player clears the boss.
     */
    public Map<String, String> victoryDialogByCharacter = new HashMap<>();

    /**
     * Character-specific loss quote overrides (keyed by player character id).
     * Used only when the player fails the boss.
     */
    public Map<String, String> defeatDialogByCharacter = new HashMap<>();

    /**
     * Ordered list of phases.  Index 0 is the opening phase.
     * Each phase transitions automatically when its own HP hits zero.
     */
    public List<PhaseDefinition> phases = new ArrayList<>();
}
