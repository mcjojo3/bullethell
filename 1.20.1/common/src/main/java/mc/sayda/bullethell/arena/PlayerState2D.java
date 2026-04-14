package mc.sayda.bullethell.arena;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Server-authoritative 2-D player state inside the bullet hell arena. */
public class PlayerState2D {

    // Default constants (used when no character definition overrides them)
    public static final float HIT_RADIUS = 3f;
    public static final float GRAZE_RADIUS = 12f;
    public static final float SPEED_NORMAL = 10f;
    public static final float SPEED_FOCUSED = 4.5f;

    // Per-instance values set from CharacterDefinition at arena start
    public float hitRadius;
    public float grazeRadius;
    public float pickupRadius;
    public float speedNormal;
    public float speedFocused;

    /** Grace window (ticks) after a hit where a bomb can cancel death. */
    public static final int DEATH_BOMB_GRACE = 8;

    public float x;
    public float y;
    public int lives;
    public int bombs;
    public int graze;
    public boolean focused;
    public boolean isCharging; // New field for TH19 charging
    
    // TH19 Charge Rates
    public float chargeRateShooting;
    public float chargeRateIdle;
    public float chargeRateCharging;

    // TH19 Charge System
    public int skillGauge    = 0;
    public int chargeLevel   = 0; // 0..4
    public static final int MAX_GAUGE = 2000;

    /** Counts down after a hit; if > 0 and a bomb arrives, death is cancelled. */
    public int deathPendingTicks = 0;

    /**
     * Post-death invulnerability ticks. While > 0 no bullets or lasers can hit
     * this player. Set to {@link #INVULN_TICKS} when a life is lost; decrements
     * each arena tick.
     */
    public int invulnTicks = 0;

    /**
     * Duration of the post-death invulnerability window in ticks (default 60 = 3
     * s).
     */
    public static final int INVULN_TICKS = 60;

    /** Ticks until the player can fire again. */
    public int shotCooldown = 0;

    /** Whether the shoot key (Z) is currently held. Set by PlayerPos2DPacket. */
    public boolean shooting = false;

    /** Events queued for this specific player (e.g. hit flash). */
    public final Queue<GameEvent> personalEvents = new ConcurrentLinkedQueue<>();

    /**
     * Power level 0–128.
     * Increased by TYPE_POWER items (+4) and TYPE_FULL_POWER (+128 → max).
     * Determines the player's shot pattern; see {@link #powerLevel()}.
     */
    public int power = 0;

    public static final int MAX_POWER = 128;

    public static final int SHOT_COOLDOWN_NORMAL = 3;
    public static final int SHOT_COOLDOWN_FOCUSED = 5;

    /**
     * Returns discrete power tier (0–4):
     * 0 → 0–7 (1 bullet)
     * 1 → 8–31 (3-way)
     * 2 → 32–95 (5-way)
     * 3 → 96–127 (5-way + 2 side)
     * 4 → 128 (max: 5-way + 4 side / focused with extra rings)
     */
    public int powerLevel() {
        if (power >= MAX_POWER)
            return 4;
        if (power >= 96)
            return 3;
        if (power >= 32)
            return 2;
        if (power >= 8)
            return 1;
        return 0;
    }

    /** Default pickup radius matches the previous hardcoded arena value. */
    public static final float PICKUP_RADIUS = 20f;

    /** Default constructor - uses built-in constants (Reimu baseline). */
    public PlayerState2D() {
        this(HIT_RADIUS, GRAZE_RADIUS, PICKUP_RADIUS, SPEED_NORMAL, SPEED_FOCUSED, 1.0f, 3.0f, 5.0f, 3, 3);
    }

    /**
     * Character-aware constructor called from ArenaContext with CharacterDefinition
     * values.
     */
    public PlayerState2D(float hitRadius, float grazeRadius, float pickupRadius,
            float speedNormal, float speedFocused,
            float chargeRateShooting, float chargeRateIdle, float chargeRateCharging,
            int startingLives, int startingBombs) {
        this.hitRadius = hitRadius;
        this.grazeRadius = grazeRadius;
        this.pickupRadius = pickupRadius;
        this.speedNormal = speedNormal;
        this.speedFocused = speedFocused;
        this.chargeRateShooting = chargeRateShooting;
        this.chargeRateIdle = chargeRateIdle;
        this.chargeRateCharging = chargeRateCharging;
        setSpawnPosition();
        lives = startingLives;
        bombs = startingBombs;
        graze = 0;
        focused = false;
    }

    /**
     * Move by (dx, dy) direction vector (values in {-1, 0, 1}).
     * Clamps to arena bounds.
     */
    public void move(float dx, float dy) {
        float speed = (focused || isCharging) ? speedFocused : speedNormal;
        if (dx != 0 && dy != 0) {
            // Normalize diagonal movement
            speed *= 0.7071f;
        }
        x = Math.max(8f, Math.min(BulletPool.ARENA_W - 8f, x + dx * speed));
        y = Math.max(8f, Math.min(BulletPool.ARENA_H - 8f, y + dy * speed));
    }

    /**
     * Place the player at the default spawn point (bottom-centre). Used at arena
     * start only.
     */
    public void setSpawnPosition() {
        x = BulletPool.ARENA_W / 2f;
        y = BulletPool.ARENA_H * 0.85f;
    }
}
