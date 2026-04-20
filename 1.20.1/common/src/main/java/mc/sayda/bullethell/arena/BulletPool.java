package mc.sayda.bullethell.arena;

import java.util.Arrays;
import java.util.function.IntConsumer;

/**
 * Fixed-size struct array for bullet simulation.
 * No entity overhead, no heap allocations per bullet.
 * Layout per slot: [x, y, vx, vy, type, lifetime, visScale, hitScale, angVel,
 * freezeRemaining, pendingVx, pendingVy]. While {@code freezeRemaining} &gt; 0 the bullet does not move,
 * angular velocity is not applied, and lifetime does not decay; velocity is copied from pending when freeze hits 0.
 *
 * Capacity is configurable - use ENEMY_CAPACITY for boss bullets
 * and PLAYER_CAPACITY for player shots (raised for dense spread patterns
 * on fairy-heavy stages so spawn() rarely fails when many shots are alive).
 */
public class BulletPool {

    /** Room for dense boss patterns; {@link #spawn} recycles the oldest slot if full so volleys never silently drop. */
    public static final int   ENEMY_CAPACITY  = 4096;
    public static final int   PLAYER_CAPACITY = 128; // player shots
    /** Legacy alias kept for existing references. */
    public static final int   CAPACITY        = ENEMY_CAPACITY;

    public static final float ARENA_W = 480f;
    public static final float ARENA_H = 640f;

    /** Floats per bullet slot (enemy + player pools share layout). */
    public static final int STRIDE = 13;

    /** Stored in {@link #F_PLAYER_HOMING}: bullet receives boss/enemy homing steer in player pools only. */
    public static final int HOMING_OFF = 0;
    public static final int HOMING_ON = 1;
    /** Resolve from {@link mc.sayda.bullethell.pattern.BulletType#defaultPlayerHomingSteer()}. */
    public static final int HOMING_USE_TYPE_DEFAULT = 2;

    // Slot field offsets
    public static final int F_X       = 0;
    public static final int F_Y       = 1;
    public static final int F_VX      = 2;
    public static final int F_VY      = 3;
    public static final int F_TYPE    = 4;
    public static final int F_LIFE    = 5;
    /** Visual radius multiplier vs {@link mc.sayda.bullethell.pattern.BulletType#radius}. */
    public static final int F_VIS_SCALE = 6;
    /** Hit radius multiplier vs type base radius (can be &lt; vis for forgiving large orbs). */
    public static final int F_HIT_SCALE = 7;
    /**
     * Angular velocity in radians per tick applied to the velocity vector before integration
     * (TH-style curved shots). 0 = straight motion.
     */
    public static final int F_ANG_VEL = 8;
    /** Ticks left with no movement / no life decay; then {@link #F_PENDING_VX}/{@link #F_PENDING_VY} apply. */
    public static final int F_FREEZE_REMAINING = 9;
    public static final int F_PENDING_VX = 10;
    public static final int F_PENDING_VY = 11;
    /** 1f = {@link mc.sayda.bullethell.arena.ArenaContext} applies homing steer; 0f = off. */
    public static final int F_PLAYER_HOMING = 12;

    private final int capacity;
    private final float[]   data;
    private final boolean[] active;
    private final boolean[] dirty;
    private int activeCount = 0;
    /** Optional: reset arena-side metadata (e.g. bounce state) before overwriting a slot. */
    private IntConsumer onBeforeWriteSlot;

    /** Default constructor - uses {@link #ENEMY_CAPACITY}. */
    public BulletPool() { this(ENEMY_CAPACITY); }

    public BulletPool(int capacity) {
        this.capacity = capacity;
        this.data     = new float[capacity * STRIDE];
        this.active   = new boolean[capacity];
        this.dirty    = new boolean[capacity];
    }

    public int getCapacity() { return capacity; }

    /**
     * Called with the slot index immediately before new bullet data is written (every successful spawn).
     * Use to clear per-slot state keyed by slot index (e.g. wall-bounce counters).
     */
    public void setOnBeforeWriteSlot(IntConsumer callback) {
        this.onBeforeWriteSlot = callback;
    }

    // ---------------------------------------------------------------- tick

    public void tick() {
        for (int i = 0; i < capacity; i++) {
            if (!active[i]) continue;
            int b = i * STRIDE;
            float freeze = data[b + F_FREEZE_REMAINING];
            if (freeze > 0f) {
                data[b + F_FREEZE_REMAINING] = freeze - 1f;
                if (data[b + F_FREEZE_REMAINING] <= 0f) {
                    data[b + F_VX] = data[b + F_PENDING_VX];
                    data[b + F_VY] = data[b + F_PENDING_VY];
                }
                dirty[i] = true;
                continue;
            }
            applyAngularVelocity(data, b);
            data[b + F_X] += data[b + F_VX];
            data[b + F_Y] += data[b + F_VY];
            data[b + F_LIFE]--;
            if (data[b + F_LIFE] <= 0 || outOfBounds(data[b + F_X], data[b + F_Y])) {
                deactivate(i);
            } else {
                dirty[i] = true;
            }
        }
    }

    /**
     * Client-side extrapolation - moves bullets without deactivating them.
     * Server delta packets handle deactivation.
     */
    public void clientTick() {
        for (int i = 0; i < capacity; i++) {
            if (!active[i]) continue;
            int b = i * STRIDE;
            float freeze = data[b + F_FREEZE_REMAINING];
            if (freeze > 0f) {
                data[b + F_FREEZE_REMAINING] = freeze - 1f;
                if (data[b + F_FREEZE_REMAINING] <= 0f) {
                    data[b + F_VX] = data[b + F_PENDING_VX];
                    data[b + F_VY] = data[b + F_PENDING_VY];
                }
                continue;
            }
            applyAngularVelocity(data, b);
            data[b + F_X] += data[b + F_VX];
            data[b + F_Y] += data[b + F_VY];
        }
    }

    private static void applyAngularVelocity(float[] data, int b) {
        float ang = data[b + F_ANG_VEL];
        if (ang * ang < 1e-16f)
            return;
        float vx = data[b + F_VX];
        float vy = data[b + F_VY];
        float c = (float) Math.cos(ang);
        float s = (float) Math.sin(ang);
        data[b + F_VX] = vx * c - vy * s;
        data[b + F_VY] = vx * s + vy * c;
    }

    // ---------------------------------------------------------------- spawn / deactivate

    public int spawn(float x, float y, float vx, float vy, int type, int life) {
        return spawn(x, y, vx, vy, type, life, 1f, 1f, 0f);
    }

    public int spawn(float x, float y, float vx, float vy, int type, int life,
                     float visScale, float hitScale) {
        return spawn(x, y, vx, vy, type, life, visScale, hitScale, 0f);
    }

    public int spawn(float x, float y, float vx, float vy, int type, int life,
                     float visScale, float hitScale, float angVelRadPerTick) {
        return spawn(x, y, vx, vy, type, life, visScale, hitScale, angVelRadPerTick, 0);
    }

    /**
     * @param freezeTicks when &gt; 0, bullet starts with zero velocity and does not age until freeze elapses,
     *                    then {@code vx}/{@code vy} are applied from the same arguments (stored as pending).
     */
    public int spawn(float x, float y, float vx, float vy, int type, int life,
                     float visScale, float hitScale, float angVelRadPerTick, int freezeTicks) {
        return spawn(x, y, vx, vy, type, life, visScale, hitScale, angVelRadPerTick, freezeTicks, HOMING_USE_TYPE_DEFAULT);
    }

    public int spawn(float x, float y, float vx, float vy, int type, int life,
                     float visScale, float hitScale, float angVelRadPerTick, int freezeTicks, int homingMode) {
        int slot = nextFreeSlot();
        if (slot == -1) return -1;
        if (onBeforeWriteSlot != null)
            onBeforeWriteSlot.accept(slot);
        int b = slot * STRIDE;
        data[b + F_X]    = x;
        data[b + F_Y]    = y;
        data[b + F_TYPE] = type;
        data[b + F_LIFE] = life;
        data[b + F_VIS_SCALE] = visScale > 0.01f ? visScale : 1f;
        data[b + F_HIT_SCALE] = hitScale > 0.01f ? hitScale : 1f;
        data[b + F_ANG_VEL] = angVelRadPerTick;
        float homingVal;
        if (homingMode == HOMING_ON)
            homingVal = 1f;
        else if (homingMode == HOMING_OFF)
            homingVal = 0f;
        else
            homingVal = mc.sayda.bullethell.pattern.BulletType.fromId(type).defaultPlayerHomingSteer() ? 1f : 0f;
        data[b + F_PLAYER_HOMING] = homingVal;
        if (freezeTicks > 0) {
            data[b + F_VX] = 0f;
            data[b + F_VY] = 0f;
            data[b + F_PENDING_VX] = vx;
            data[b + F_PENDING_VY] = vy;
            data[b + F_FREEZE_REMAINING] = freezeTicks;
        } else {
            data[b + F_VX] = vx;
            data[b + F_VY] = vy;
            data[b + F_PENDING_VX] = 0f;
            data[b + F_PENDING_VY] = 0f;
            data[b + F_FREEZE_REMAINING] = 0f;
        }
        active[slot] = true;
        dirty[slot]  = true;
        activeCount++;
        return slot;
    }

    public void deactivate(int slot) {
        if (active[slot]) {
            active[slot] = false;
            activeCount--;
            dirty[slot] = true;
        }
    }

    public void clearAll() {
        for (int i = 0; i < capacity; i++) if (active[i]) deactivate(i);
    }

    // ---------------------------------------------------------------- getters

    public float   getX(int slot)    { return data[slot * STRIDE + F_X]; }
    public float   getY(int slot)    { return data[slot * STRIDE + F_Y]; }
    public float   getVx(int slot)   { return data[slot * STRIDE + F_VX]; }
    public float   getVy(int slot)   { return data[slot * STRIDE + F_VY]; }
    public int     getType(int slot) { return (int) data[slot * STRIDE + F_TYPE]; }
    public float   getVisScale(int slot) { return data[slot * STRIDE + F_VIS_SCALE]; }
    public float   getHitScale(int slot) { return data[slot * STRIDE + F_HIT_SCALE]; }
    public float   getAngVel(int slot) { return data[slot * STRIDE + F_ANG_VEL]; }
    /** Non-zero when this player bullet should be steered toward boss / nearest enemy. */
    public float   getPlayerHoming(int slot) { return data[slot * STRIDE + F_PLAYER_HOMING]; }
    public boolean isActive(int slot){ return active[slot]; }
    public int     getActiveCount()  { return activeCount; }

    // ---------------------------------------------------------------- delta sync

    public boolean isDirty(int slot) { return dirty[slot]; }
    public void    clearDirty()      { Arrays.fill(dirty, false); }

    public float[] getSlotData(int slot) {
        float[] out = new float[STRIDE];
        System.arraycopy(data, slot * STRIDE, out, 0, STRIDE);
        return out;
    }

    public void setSlotData(int slot, float[] slotData, boolean isActive) {
        System.arraycopy(slotData, 0, data, slot * STRIDE, STRIDE);
        if (isActive  && !active[slot]) { active[slot] = true;  activeCount++; }
        else if (!isActive && active[slot]) { active[slot] = false; activeCount--; }
    }

    // ---------------------------------------------------------------- helpers
    
    public void setVx(int slot, float vx) { if (active[slot]) { data[slot * STRIDE + F_VX] = vx; dirty[slot] = true; } }
    public void setVy(int slot, float vy) { if (active[slot]) { data[slot * STRIDE + F_VY] = vy; dirty[slot] = true; } }

    public void setAngVel(int slot, float angVelRadPerTick) {
        if (active[slot]) {
            data[slot * STRIDE + F_ANG_VEL] = angVelRadPerTick;
            dirty[slot] = true;
        }
    }

    /** Swap render/hit type (e.g. outline ORB → comb NEEDLE on ritual release). */
    public void setBulletType(int slot, int typeId) {
        if (active[slot]) {
            data[slot * STRIDE + F_TYPE] = typeId;
            dirty[slot] = true;
        }
    }

    /** Replace remaining lifetime (e.g. when releasing kinematic bullets into normal flight). */
    public void setRemainingLife(int slot, int lifeTicks) {
        if (active[slot] && lifeTicks > 0) {
            data[slot * STRIDE + F_LIFE] = lifeTicks;
            dirty[slot] = true;
        }
    }

    /** Kinematic / formation bullets: set world position without changing velocity. */
    public void setPosition(int slot, float x, float y) {
        if (!active[slot])
            return;
        int b = slot * STRIDE;
        data[b + F_X] = x;
        data[b + F_Y] = y;
        dirty[slot] = true;
    }

    private int nextFreeSlot() {
        for (int i = 0; i < capacity; i++) if (!active[i]) return i;
        // Pool full: recycle lowest-index active bullet so spawn() never fails silently.
        for (int i = 0; i < capacity; i++) {
            if (active[i]) {
                deactivate(i);
                return i;
            }
        }
        return -1;
    }

    private static boolean outOfBounds(float x, float y) {
        return x < -32f || x > ARENA_W + 32f || y < -32f || y > ARENA_H + 32f;
    }
}
