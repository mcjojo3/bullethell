package mc.sayda.bullethell.arena;

import java.util.Arrays;

/**
 * Fixed-capacity pool of laser beams.
 *
 * Each laser passes through two phases:
 *   Warning  – a thin indicator line shows where the beam will fire (safe for the player)
 *   Firing   – the full-width beam is active and deals damage on contact
 *
 * Data is stored in a stride-7 float array for cache efficiency.
 * Slot layout: [0]=x  [1]=y  [2]=angle  [3]=halfWidth  [4]=warnTicksLeft  [5]=activeTicksLeft  [6]=typeId
 */
public class LaserPool {

    public static final int CAPACITY = 32;
    public static final int STRIDE   = 7;

    // Field offsets within each stride block
    private static final int F_X     = 0;
    private static final int F_Y     = 1;
    private static final int F_ANGLE = 2;
    private static final int F_HW    = 3; // halfWidth in arena units
    private static final int F_WARN  = 4; // warning ticks remaining
    private static final int F_ACT   = 5; // active (firing) ticks remaining
    private static final int F_TYPE  = 6; // BulletType id

    /** Raw float data — exposed for efficient packet copy. */
    public final float[]   data   = new float[CAPACITY * STRIDE];
    /** Active flags — exposed for efficient packet copy. */
    public final boolean[] active = new boolean[CAPACITY];
    /** Bidirectional flag — true for NDL-style beams that extend in both directions. */
    public final boolean[] bidir  = new boolean[CAPACITY];

    // ---------------------------------------------------------------- spawn / tick

    /**
     * Spawn a laser. If all slots are occupied the oldest firing laser is evicted.
     * @param bidirectional true for NDL-style beams that extend in both directions through the origin;
     *                      false for directional beams (Master Spark) that only extend forward.
     */
    public void spawn(float x, float y, float angle, float halfWidth,
                      int warnTicks, int activeTicks, int typeId, boolean bidirectional) {
        int slot = findFreeSlot();
        if (slot < 0) return;
        int b = slot * STRIDE;
        data[b + F_X]     = x;
        data[b + F_Y]     = y;
        data[b + F_ANGLE] = angle;
        data[b + F_HW]    = halfWidth;
        data[b + F_WARN]  = warnTicks;
        data[b + F_ACT]   = activeTicks;
        data[b + F_TYPE]  = typeId;
        active[slot] = true;
        bidir[slot]  = bidirectional;
    }

    /** Advance all lasers by one tick; deactivate when firing ticks run out. */
    public void tick() {
        for (int i = 0; i < CAPACITY; i++) {
            if (!active[i]) continue;
            int b = i * STRIDE;
            if (data[b + F_WARN] > 0) {
                data[b + F_WARN]--;
            } else {
                data[b + F_ACT]--;
                if (data[b + F_ACT] <= 0) active[i] = false;
            }
        }
    }

    // ---------------------------------------------------------------- queries

    public boolean isActive(int i)  { return active[i]; }
    public boolean isWarning(int i) { return active[i] && data[i * STRIDE + F_WARN] > 0; }
    public boolean isFiring(int i)  { return active[i] && data[i * STRIDE + F_WARN] <= 0; }
    public boolean isBidir(int i)   { return bidir[i]; }

    public float getX(int i)         { return data[i * STRIDE + F_X]; }
    public float getY(int i)         { return data[i * STRIDE + F_Y]; }
    public float getAngle(int i)     { return data[i * STRIDE + F_ANGLE]; }
    public float getHalfWidth(int i) { return data[i * STRIDE + F_HW]; }
    public int   getWarnLeft(int i)  { return (int) data[i * STRIDE + F_WARN]; }
    public int   getActLeft(int i)   { return (int) data[i * STRIDE + F_ACT]; }
    public int   getTypeId(int i)    { return (int) data[i * STRIDE + F_TYPE]; }

    // ---------------------------------------------------------------- reset

    public void clearAll() { Arrays.fill(active, false); Arrays.fill(bidir, false); }

    // ---------------------------------------------------------------- private

    private int findFreeSlot() {
        for (int i = 0; i < CAPACITY; i++) if (!active[i]) return i;
        // Evict the laser with the least firing ticks remaining
        int worst = 0;
        float min = Float.MAX_VALUE;
        for (int i = 0; i < CAPACITY; i++) {
            float v = data[i * STRIDE + F_ACT];
            if (v < min) { min = v; worst = i; }
        }
        active[worst] = false;
        return worst;
    }
}
