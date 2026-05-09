package mc.sayda.bullethell.arena;

/**
 * Defines the stats for each enemy archetype.
 *
 * Fairy categories follow classic Touhou conventions:
 * BLUE_FAIRY - common wave filler, low HP, 2-shot aimed bursts
 * YELLOW_FAIRY - slightly tougher than blue, 2-shot wide burst
 * RED_FAIRY - aggressive, 3-shot aimed bursts, drops power items
 * GREEN_FAIRY - tanky support, 4-shot spread, drops power items
 * LARGE_FAIRY - mid-wave anchor (any colour), 5-shot burst; uses shared large
 * sprite sheet
 * POLICE_FAIRY / WARRIOR_FAIRY - small-fairy variants with dedicated 160×40
 * sheets
 * MEDIUM_FAIRY - between small and large; uses {@code medium_fairy.png} sheet
 *
 * All stats are pre-difficulty-scaling; the ArenaContext multiplies bullet
 * speed by {@link DifficultyConfig#speedMult} at spawn time.
 *
 * {@code textureIdx} maps to small-fairy color sheets in BulletHellRenderer
 * (0=blue, 1=red, 2=yellow, 3=green, 4=police, 5=warrior). Ignored for
 * {@link #medium} and {@link #large} sprite tiers (dedicated sheets).
 */
public enum EnemyType {

    // hp bullets spread bSpd atkIvl score dropType texIdx med? large? hRadius
    // defaultPattern
    // BLUE - aimed fan; base 1 so Lunatic stays ~2 shots (TH6-style thin curtains,
    // not walls)
    BLUE_FAIRY(1, 1, 0.20f, 3.0f, 52, 100, ItemPool.TYPE_POINT, 0, false, false, 8.0f, EnemyPattern.AIMED),
    // RED - 3-shot tight aimed fan: faster + more bullets than blue
    RED_FAIRY(1, 3, 0.22f, 3.5f, 30, 200, ItemPool.TYPE_POWER, 1, false, false, 8.0f, EnemyPattern.AIMED),
    // YELLOW- downward fan, NOT aimed: keep burst count modest at high difficulty
    YELLOW_FAIRY(1, 2, 0.28f, 3.2f, 56, 150, ItemPool.TYPE_POINT, 2, false, false, 8.0f, EnemyPattern.SPREAD),
    // GREEN - ring burst with random start angle each burst (TH6 barrier style)
    GREEN_FAIRY(1, 6, 0.0f, 2.8f, 38, 250, ItemPool.TYPE_POWER, 3, false, false, 8.0f, EnemyPattern.RING),
    // Large fairies: aimed burst + outer ring - dual threat (shared large 280×70
    // sheet)
    LARGE_FAIRY(20, 4, 0.25f, 2.5f, 50, 500, ItemPool.TYPE_POWER, 0, false, true, 16.0f, EnemyPattern.AIMED_RING),
    LARGE_RED(25, 5, 0.28f, 3.0f, 40, 700, ItemPool.TYPE_POWER, 1, false, true, 16.0f, EnemyPattern.AIMED_RING),
    // LARGE_YELLOW - rapid single-shot stream: rate-based harassment
    LARGE_YELLOW(18, 1, 0.0f, 4.5f, 45, 600, ItemPool.TYPE_POWER, 2, false, true, 16.0f, EnemyPattern.STREAM),
    // LARGE_GREEN - dense ring: 8-bullet wall (16 on Lunatic)
    LARGE_GREEN(30, 8, 0.0f, 2.8f, 45, 900, ItemPool.TYPE_POWER, 3, false, true, 16.0f, EnemyPattern.RING),
    // Small variants - 160×40 sheets (40×40 × 4 frames)
    POLICE_FAIRY(1, 1, 0.20f, 3.0f, 52, 100, ItemPool.TYPE_POINT, 4, false, false, 8.0f, EnemyPattern.AIMED),
    WARRIOR_FAIRY(1, 3, 0.22f, 3.5f, 30, 200, ItemPool.TYPE_POWER, 5, false, false, 8.0f, EnemyPattern.AIMED),
    // Medium tier - 200×50 sheet (50×50 × 4 frames); textureIdx unused
    MEDIUM_FAIRY(14, 4, 0.25f, 2.8f, 44, 550, ItemPool.TYPE_POWER, 0, true, false, 12.0f, EnemyPattern.AIMED_RING);

    /** Base HP before any scaling. */
    public final int hp;
    /** Number of aimed bullets fired per burst. */
    public final int bulletCount;
    /** Fan spread between adjacent aimed bullets (radians). */
    public final float bulletSpread;
    /** Bullet speed before difficulty scaling. */
    public final float bulletSpeed;
    /** Ticks between attack bursts. */
    public final int atkInterval;
    /** Score awarded on kill. */
    public final int scoreValue;
    /** Item type dropped on kill (see {@link ItemPool} TYPE_ constants). */
    public final int dropType;
    /**
     * Index into small-fairy color sheets (0-5); ignored when {@link #medium} or
     * {@link #large}.
     */
    public final int textureIdx;
    /** If true, renders from {@code medium_fairy.png} (50×50 × 4). */
    public final boolean medium;
    /**
     * If true the renderer uses {@code large_fairy.png} (70×70 × 4); hitbox stays
     * large.
     */
    public final boolean large;
    /** Collision radius for player bullets. */
    public final float hitRadius;
    /** Default attack pattern for this enemy type. */
    public final EnemyPattern defaultPattern;

    EnemyType(int hp, int bulletCount, float bulletSpread,
            float bulletSpeed, int atkInterval, int scoreValue, int dropType,
            int textureIdx, boolean medium, boolean large, float hitRadius, EnemyPattern defaultPattern) {
        this.hp = hp;
        this.bulletCount = bulletCount;
        this.bulletSpread = bulletSpread;
        this.bulletSpeed = bulletSpeed;
        this.atkInterval = atkInterval;
        this.scoreValue = scoreValue;
        this.dropType = dropType;
        this.textureIdx = textureIdx;
        this.medium = medium;
        this.large = large;
        this.hitRadius = hitRadius;
        this.defaultPattern = defaultPattern;
    }

    private static final EnemyType[] VALUES = values();

    public static EnemyType fromId(int id) {
        return (id >= 0 && id < VALUES.length) ? VALUES[id] : BLUE_FAIRY;
    }

    public int getId() {
        return ordinal();
    }
}
