package mc.sayda.bullethell.arena;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Server-authoritative 2-D player state inside the bullet hell arena. */
public class PlayerState2D {

    // Default constants (used when no character definition overrides them)
    public static final float HIT_RADIUS = 3f;
    public static final float GRAZE_RADIUS = 12f;
    /**
     * Arena units per tick at 20 tps; tuned from TH06 px/frame (Reimu 4/2 @ 60fps)
     * × ~3.25.
     */
    public static final float SPEED_NORMAL = 13f;
    public static final float SPEED_FOCUSED = 6.5f;

    // Per-instance values set from CharacterDefinition at arena start
    public float hitRadius;
    public float grazeRadius;
    public float pickupRadius;
    public float speedNormal;
    public float speedFocused;

    /** Grace window (ticks) after a hit where a bomb can cancel death. */
    public static final int DEATH_BOMB_GRACE = 3;

    public float x;
    public float y;
    public int lives;
    public int bombs;
    /** Reset value for {@link #bombs} when a life is lost. */
    public int baseStartingBombs;
    public int graze;
    public boolean focused;
    public boolean isCharging;
    /** Triggers bullet cancel once per life when hitting 128 power (TH6 parity). */
    public boolean reachedMaxPowerInThisLife = false;

    /** TH19-style rate multipliers (used with PoFV timing). */
    public float chargeRateShooting;
    public float chargeRateIdle;
    public float chargeRateCharging;

    /**
     * PoFV gray "stock" bar (0.0-3.0 levels): grazing, kills, passive build while
     * not holding X. Higher spells spend stock per wiki (Lv. N costs N−1 stock).
     */
    public double storedChargeProgress = 0.0;

    /**
     * PoFV colored bar while holding X (0.0-3.0): fills at
     * {@link #chargeSpeedFrames}
     * per level after startup; on release, cast level is derived from this, not
     * from draining all stock for L1.
     */
    public double holdChargeProgress = 0.0;

    /** Frames X held this press; resets when X released. Used for 9f startup. */
    public int chargeConsecutiveHoldTicks = 0;

    /** Ticks remaining before charging can build after a charge skill (PoFV). */
    public int chargeLockoutTicks = 0;

    /**
     * PoFV frames per level while holding X (after startup); from character JSON.
     */
    public double chargeSpeedFrames = 31.0;

    /** PoFV charge delay after a cast; from character JSON. */
    public int chargeDelayAfterSkill = 41;

    /** Milli-levels (0-3000) of {@link #storedChargeProgress} for HUD / network. */
    public int skillGauge = 0;

    /** Milli-levels (0-3000) of {@link #holdChargeProgress} while holding X. */
    public int holdChargeGauge = 0;

    /** Floor of stored stock (0-3) for HUD pips. */
    public int chargeLevel = 0;

    /** Touhou 9: first 9 frames of holding X, the charge bar does not advance. */
    public static final int POFV_CHARGE_STARTUP_FRAMES = 9;

    /** Level 4 (PoFV boss / split-screen) is not implemented. */
    public static final int CHARGE_LEVEL_MAX = 3;

    /**
     * Applied to passive stock, hold fill after startup, and stock rewards
     * ({@link #addStoredChargeProgress}) - tweak global charge pacing.
     */
    public static final double CHARGE_GLOBAL_SPEED_MULT = 1.28;

    /** Counts down after a hit; if > 0 and a bomb arrives, death is cancelled. */
    public int deathPendingTicks = 0;

    /** Consecutive graze count (resets when grazeChainCooldown expires). */
    public int grazeChain = 0;
    /** Ticks since last graze; when it reaches 0 grazeChain resets. */
    public int grazeChainCooldown = 0;
    /** Ticks of no graze before the chain breaks. */
    public static final int GRAZE_CHAIN_TIMEOUT = 120;

    /** Pieces toward next life extend (0 to PIECES_PER_EXTEND-1). */
    public int lifePieces = 0;
    /** Pieces toward next bomb extend (0 to PIECES_PER_EXTEND-1). */
    public int bombPieces = 0;
    /** Number of pieces required to grant a life or bomb extend. */
    public static final int PIECES_PER_EXTEND = 5;

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

    /**
     * Monotonically incrementing tick counter while Z is held; resets to 0 when Z
     * is released.
     * Used by periodic spawns (fireRateTicks > 0) to fire independently of the main
     * cooldown.
     */
    public int shotTick = 0;

    /** Whether the shoot key (Z) is currently held. Set by PlayerPos2DPacket. */
    public boolean shooting = false;

    /** Events queued for this specific player (e.g. hit flash). */
    public final Queue<GameEvent> personalEvents = new ConcurrentLinkedQueue<>();

    /**
     * Power level 0-128.
     * Increased by TYPE_POWER (+1), TYPE_POWER_LARGE (+8), TYPE_FULL_POWER (→ max).
     * Determines the player's shot pattern; see {@link #powerLevel()}.
     */
    public int power = 0;

    public static final int MAX_POWER = 128;

    /**
     * Volley interval when unfocused (ticks at arena rate). Low values ≈ TH-style
     * mash/hold
     * stream; override per character via {@code shotCooldownNormal} in character
     * JSON.
     */
    public static final int SHOT_COOLDOWN_NORMAL = 2;
    /** Volley interval when focused; slightly slower than unfocused. */
    public static final int SHOT_COOLDOWN_FOCUSED = 3;

    /**
     * Returns discrete power tier (0-8), matching TH7 PCB power thresholds.
     * Characters with fewer than 9 JSON tiers are clamped to their last entry.
     * 0 → 0-7 1 → 8-15 2 → 16-31 3 → 32-47 4 → 48-63
     * 5 → 64-79 6 → 80-95 7 → 96-127 8 → 128 (MAX)
     */
    public int powerLevel() {
        if (power >= 128)
            return 8;
        if (power >= 96)
            return 7;
        if (power >= 80)
            return 6;
        if (power >= 64)
            return 5;
        if (power >= 48)
            return 4;
        if (power >= 32)
            return 3;
        if (power >= 16)
            return 2;
        if (power >= 8)
            return 1;
        return 0;
    }

    /** Default pickup radius matches the previous hardcoded arena value. */
    public static final float PICKUP_RADIUS = 20f;

    /** Default constructor - uses built-in constants (Reimu baseline). */
    public PlayerState2D() {
        this(HIT_RADIUS, GRAZE_RADIUS, PICKUP_RADIUS, SPEED_NORMAL, SPEED_FOCUSED,
                1.0f, 3.0f, 5.0f, 31.0, 41, 3, 3);
    }

    /**
     * Character-aware constructor called from ArenaContext with CharacterDefinition
     * values.
     */
    public PlayerState2D(float hitRadius, float grazeRadius, float pickupRadius,
            float speedNormal, float speedFocused,
            float chargeRateShooting, float chargeRateIdle, float chargeRateCharging,
            double chargeSpeedFrames, int chargeDelayAfterSkill,
            int startingLives, int startingBombs) {
        this.hitRadius = hitRadius;
        this.grazeRadius = grazeRadius;
        this.pickupRadius = pickupRadius;
        this.speedNormal = speedNormal;
        this.speedFocused = speedFocused;
        this.chargeRateShooting = chargeRateShooting;
        this.chargeRateIdle = chargeRateIdle;
        this.chargeRateCharging = chargeRateCharging;
        this.chargeSpeedFrames = Math.max(1.0, chargeSpeedFrames);
        this.chargeDelayAfterSkill = Math.max(0, chargeDelayAfterSkill);
        setSpawnPosition();
        lives = startingLives;
        bombs = startingBombs;
        baseStartingBombs = startingBombs;
        graze = 0;
        focused = false;
        reachedMaxPowerInThisLife = false;
        syncChargePacketFields();
    }

    /**
     * Sync packet/HUD ints from {@link #storedChargeProgress} and
     * {@link #holdChargeProgress}.
     */
    public void syncChargePacketFields() {
        double s = Math.min(CHARGE_LEVEL_MAX, Math.max(0.0, storedChargeProgress));
        storedChargeProgress = s;
        double h = Math.min(CHARGE_LEVEL_MAX, Math.max(0.0, holdChargeProgress));
        // Colored hold cannot exceed gray stock (PoFV wheel).
        h = Math.min(h, s);
        holdChargeProgress = h;
        skillGauge = (int) Math.round(s * 1000.0);
        holdChargeGauge = (int) Math.round(h * 1000.0);
        chargeLevel = Math.min(CHARGE_LEVEL_MAX, (int) Math.floor(s + 1e-9));
    }

    /** Add to stored (gray) stock - kills, graze, passive; respects lockout. */
    public void addStoredChargeProgress(double delta) {
        if (chargeLockoutTicks > 0)
            return;
        double d = delta * CHARGE_GLOBAL_SPEED_MULT;
        storedChargeProgress = Math.min(CHARGE_LEVEL_MAX, Math.max(0.0, storedChargeProgress + d));
        syncChargePacketFields();
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
