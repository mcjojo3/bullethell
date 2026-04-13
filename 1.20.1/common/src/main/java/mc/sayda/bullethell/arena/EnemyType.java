package mc.sayda.bullethell.arena;

/**
 * Defines the stats for each enemy archetype.
 *
 * Fairy categories follow classic Touhou conventions:
 *   BLUE_FAIRY   - common wave filler, low HP, 2-shot aimed bursts
 *   YELLOW_FAIRY - slightly tougher than blue, 2-shot wide burst
 *   RED_FAIRY    - aggressive, 3-shot aimed bursts, drops power items
 *   GREEN_FAIRY  - tanky support, 4-shot spread, drops power items
 *   LARGE_FAIRY  - mid-wave anchor (any colour), 5-shot burst; textureIdx picks sprite
 *
 * All stats are pre-difficulty-scaling; the ArenaContext multiplies bullet
 * speed by {@link DifficultyConfig#speedMult} at spawn time.
 *
 * {@code textureIdx} maps to the ENEMY_TEXTURES array in BulletHellRenderer
 * (0=blue, 1=red, 2=yellow, 3=green).  {@code large} makes the renderer scale
 * the sprite up instead of requiring a separate texture.
 */
public enum EnemyType {

    //                  hp   bullets spread  bSpd atkIvl score  dropType         texIdx large
    BLUE_FAIRY   (   8,   2, 0.15f, 3.0f,   40,   100, ItemPool.TYPE_POINT,  0, false),
    RED_FAIRY    (  16,   3, 0.20f, 3.5f,   30,   200, ItemPool.TYPE_POWER,  1, false),
    YELLOW_FAIRY (  12,   2, 0.25f, 3.2f,   45,   150, ItemPool.TYPE_POINT,  2, false),
    GREEN_FAIRY  (  20,   4, 0.22f, 3.0f,   35,   250, ItemPool.TYPE_POWER,  3, false),
    // Large (midboss-tier) fairies — HP raised so they survive ~1–2 s at max power
    LARGE_FAIRY  ( 200,   5, 0.28f, 2.5f,   50,   500, ItemPool.TYPE_POWER,  0, true),
    LARGE_RED    ( 250,   6, 0.30f, 3.0f,   40,   700, ItemPool.TYPE_POWER,  1, true),
    LARGE_YELLOW ( 175,   5, 0.30f, 3.2f,   45,   600, ItemPool.TYPE_POWER,  2, true),
    LARGE_GREEN  ( 300,   7, 0.25f, 2.8f,   38,   900, ItemPool.TYPE_POWER,  3, true);

    /** Base HP before any scaling. */
    public final int   hp;
    /** Number of aimed bullets fired per burst. */
    public final int   bulletCount;
    /** Fan spread between adjacent aimed bullets (radians). */
    public final float bulletSpread;
    /** Bullet speed before difficulty scaling. */
    public final float bulletSpeed;
    /** Ticks between attack bursts. */
    public final int   atkInterval;
    /** Score awarded on kill. */
    public final int   scoreValue;
    /** Item type dropped on kill (see {@link ItemPool} TYPE_ constants). */
    public final int   dropType;
    /** Index into the 4-entry texture array (0=blue,1=red,2=yellow,3=green). */
    public final int   textureIdx;
    /** If true the renderer scales the sprite up ~1.75× for a "large fairy" look. */
    public final boolean large;

    EnemyType(int hp, int bulletCount, float bulletSpread,
              float bulletSpeed, int atkInterval, int scoreValue, int dropType,
              int textureIdx, boolean large) {
        this.hp           = hp;
        this.bulletCount  = bulletCount;
        this.bulletSpread = bulletSpread;
        this.bulletSpeed  = bulletSpeed;
        this.atkInterval  = atkInterval;
        this.scoreValue   = scoreValue;
        this.dropType     = dropType;
        this.textureIdx   = textureIdx;
        this.large        = large;
    }

    private static final EnemyType[] VALUES = values();

    public static EnemyType fromId(int id) {
        return (id >= 0 && id < VALUES.length) ? VALUES[id] : BLUE_FAIRY;
    }

    public int getId() { return ordinal(); }
}
