package mc.sayda.bullethell.arena;

/**
 * Starting bonuses a player brings into an arena, already resolved to plain numbers.
 *
 * The arena deliberately does not read Minecraft entity attributes itself. Resolving
 * them at the boundary keeps {@link ArenaContext} free of Minecraft types, which is what
 * lets the same class run as a client-side simulation - and it also means attribute
 * reads happen on the main thread rather than the arena thread.
 */
public record PlayerBonuses(int extraLives, int extraBombs) {

    public static final PlayerBonuses NONE = new PlayerBonuses(0, 0);

    public PlayerBonuses {
        extraLives = Math.max(0, extraLives);
        extraBombs = Math.max(0, extraBombs);
    }
}
