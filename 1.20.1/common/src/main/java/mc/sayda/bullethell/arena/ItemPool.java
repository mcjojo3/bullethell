package mc.sayda.bullethell.arena;

import java.util.Arrays;

/**
 * Fixed-size pool for collectible items dropped by the boss.
 *
 * Layout per slot: [x, y, vy, type, timer]
 *   x, y     - arena position
 *   vy       - vertical velocity (starts negative = floating up)
 *   type     - ItemType ordinal
 *   timer    - remaining lifetime ticks (item despawns at 0)
 *
 * Physics: light gravity each tick (vy += GRAVITY), capped at MAX_FALL_SPEED.
 * Items start rising, slow down, then fall.
 */
public class ItemPool {

    public static final int CAPACITY = 128;

    private static final int   STRIDE        = 5;
    public  static final float INITIAL_VY    = -2.5f;
    private static final float GRAVITY       =  0.08f;
    private static final float MAX_FALL_SPEED=  3.5f;
    private static final int   DEFAULT_LIFE  =  400;  // ~20 s

    public static final int F_X    = 0;
    public static final int F_Y    = 1;
    public static final int F_VY   = 2;
    public static final int F_TYPE = 3;
    public static final int F_LIFE = 4;

    // ---------------------------------------------------------------- item types

    public static final int TYPE_POWER      = 0; // pink  - increases power level
    public static final int TYPE_POINT      = 1; // yellow - score item
    public static final int TYPE_FULL_POWER = 2; // blue  - max power instantly
    public static final int TYPE_ONE_UP     = 3; // green - extra life
    public static final int TYPE_BOMB       = 4; // orange - bomb stock +1

    private static final int[]  ITEM_COLORS  = { 0xFFFF4488, 0xFFFFE600, 0xFF44AAFF, 0xFF44FF88, 0xFFFF8800 };
    private static final int[]  ITEM_SIZES   = { 3, 3, 5, 5, 4 };

    public static int  colorOf(int type) { return type < ITEM_COLORS.length ? ITEM_COLORS[type] : 0xFFFFFFFF; }
    public static int  sizeOf (int type) { return type < ITEM_SIZES.length  ? ITEM_SIZES[type]  : 3; }

    // ---------------------------------------------------------------- storage

    private final float[]   data   = new float[CAPACITY * STRIDE];
    private final boolean[] active = new boolean[CAPACITY];
    private int activeCount = 0;

    // ---------------------------------------------------------------- tick

    public void tick() {
        for (int i = 0; i < CAPACITY; i++) {
            if (!active[i]) continue;
            int b = i * STRIDE;
            // Apply gravity
            float vy = data[b + F_VY] + GRAVITY;
            if (vy > MAX_FALL_SPEED) vy = MAX_FALL_SPEED;
            data[b + F_VY]  = vy;
            data[b + F_Y]  += vy;
            data[b + F_LIFE]--;
            if (data[b + F_LIFE] <= 0 || data[b + F_Y] > BulletPool.ARENA_H + 16f) {
                deactivate(i);
            }
        }
    }

    /** Client-side extrapolation (no deactivation - server corrections handle that). */
    public void clientTick() {
        for (int i = 0; i < CAPACITY; i++) {
            if (!active[i]) continue;
            int b = i * STRIDE;
            float vy = data[b + F_VY] + GRAVITY;
            if (vy > MAX_FALL_SPEED) vy = MAX_FALL_SPEED;
            data[b + F_VY] = vy;
            data[b + F_Y] += vy;
        }
    }

    // ---------------------------------------------------------------- spawn / deactivate

    public int spawn(float x, float y, int type) {
        int slot = nextFreeSlot();
        if (slot == -1) return -1;
        int b = slot * STRIDE;
        data[b + F_X]    = x;
        data[b + F_Y]    = y;
        data[b + F_VY]   = INITIAL_VY;
        data[b + F_TYPE] = type;
        data[b + F_LIFE] = DEFAULT_LIFE;
        active[slot] = true;
        activeCount++;
        return slot;
    }

    public void deactivate(int slot) {
        if (active[slot]) { active[slot] = false; activeCount--; }
    }

    // ---------------------------------------------------------------- getters

    public float   getX(int slot)    { return data[slot * STRIDE + F_X]; }
    public float   getY(int slot)    { return data[slot * STRIDE + F_Y]; }
    public float   getVy(int slot)   { return data[slot * STRIDE + F_VY]; }
    public int     getType(int slot) { return (int) data[slot * STRIDE + F_TYPE]; }
    public boolean isActive(int slot){ return active[slot]; }
    public int     getActiveCount()  { return activeCount; }

    // ---------------------------------------------------------------- network sync

    public float[] getSlotData(int slot) {
        float[] out = new float[STRIDE];
        System.arraycopy(data, slot * STRIDE, out, 0, STRIDE);
        return out;
    }

    public void setSlotData(int slot, float[] d, boolean isActive) {
        System.arraycopy(d, 0, data, slot * STRIDE, STRIDE);
        if (isActive && !active[slot]) { active[slot] = true;  activeCount++; }
        else if (!isActive && active[slot]) { active[slot] = false; activeCount--; }
    }

    public void clearAll() {
        for (int i = 0; i < CAPACITY; i++) if (active[i]) deactivate(i);
    }

    // ---------------------------------------------------------------- helpers

    private int nextFreeSlot() {
        for (int i = 0; i < CAPACITY; i++) if (!active[i]) return i;
        return -1;
    }
}
