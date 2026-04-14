package mc.sayda.bullethell.arena;

import java.util.Arrays;

/**
 * Fixed-size struct array for bullet simulation.
 * No entity overhead, no heap allocations per bullet.
 * Layout per slot: [x, y, vx, vy, type, lifetime]
 *
 * Capacity is configurable - use ENEMY_CAPACITY (500) for boss bullets
 * and PLAYER_CAPACITY (64) for player shots.
 */
public class BulletPool {

    public static final int   ENEMY_CAPACITY  = 500; // boss bullets
    public static final int   PLAYER_CAPACITY =  64; // player shots
    /** Legacy alias kept for existing references. */
    public static final int   CAPACITY        = ENEMY_CAPACITY;

    public static final float ARENA_W = 480f;
    public static final float ARENA_H = 640f;

    private static final int STRIDE = 6;

    // Slot field offsets
    public static final int F_X    = 0;
    public static final int F_Y    = 1;
    public static final int F_VX   = 2;
    public static final int F_VY   = 3;
    public static final int F_TYPE = 4;
    public static final int F_LIFE = 5;

    private final int capacity;
    private final float[]   data;
    private final boolean[] active;
    private final boolean[] dirty;
    private int activeCount = 0;

    /** Default constructor - uses ENEMY_CAPACITY (500). */
    public BulletPool() { this(ENEMY_CAPACITY); }

    public BulletPool(int capacity) {
        this.capacity = capacity;
        this.data     = new float[capacity * STRIDE];
        this.active   = new boolean[capacity];
        this.dirty    = new boolean[capacity];
    }

    public int getCapacity() { return capacity; }

    // ---------------------------------------------------------------- tick

    public void tick() {
        for (int i = 0; i < capacity; i++) {
            if (!active[i]) continue;
            int b = i * STRIDE;
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
            data[b + F_X] += data[b + F_VX];
            data[b + F_Y] += data[b + F_VY];
        }
    }

    // ---------------------------------------------------------------- spawn / deactivate

    public int spawn(float x, float y, float vx, float vy, int type, int life) {
        int slot = nextFreeSlot();
        if (slot == -1) return -1;
        int b = slot * STRIDE;
        data[b + F_X]    = x;
        data[b + F_Y]    = y;
        data[b + F_VX]   = vx;
        data[b + F_VY]   = vy;
        data[b + F_TYPE] = type;
        data[b + F_LIFE] = life;
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

    private int nextFreeSlot() {
        for (int i = 0; i < capacity; i++) if (!active[i]) return i;
        return -1;
    }

    private static boolean outOfBounds(float x, float y) {
        return x < -32f || x > ARENA_W + 32f || y < -32f || y > ARENA_H + 32f;
    }
}
