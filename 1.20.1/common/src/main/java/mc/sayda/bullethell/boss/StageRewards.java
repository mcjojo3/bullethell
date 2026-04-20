package mc.sayda.bullethell.boss;

import java.util.ArrayList;
import java.util.List;

/**
 * Commands to run when a player wins or loses this stage.
 * Defined in the {@code "rewards"} block of a stage JSON file.
 *
 * <p>Example:
 * <pre>{@code
 * "rewards": {
 *   "onWin":  ["give {player} minecraft:diamond 1",
 *              "say {player} cleared the stage!"],
 *   "onLoss": ["say Better luck next time, {player}!"]
 * }
 * }</pre>
 *
 * <p>Supported placeholders (case-sensitive):
 * <ul>
 *   <li>{@code {player}}     – player's in-game name</li>
 *   <li>{@code {score}}      – this player's personal score (in co-op, not the team total)</li>
 *   <li>{@code {difficulty}} – difficulty name (EASY, NORMAL, HARD, LUNATIC)</li>
 * </ul>
 *
 * Commands run with operator-level (level 2) permission, executed as the server.
 */
public class StageRewards {

    /** Commands run for every participant when the arena is won. */
    public List<String> onWin = new ArrayList<>();

    /** Commands run for every participant when the arena is lost. */
    public List<String> onLoss = new ArrayList<>();
}
