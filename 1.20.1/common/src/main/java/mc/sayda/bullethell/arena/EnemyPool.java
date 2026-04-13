package mc.sayda.bullethell.arena;

/**
 * Fixed-size pool for stage enemies (fairies, mid-bosses, etc.).
 *
 * Each slot is a packed float array - no heap allocation per enemy.
 * Slot layout (stride = 9):
 *
 *   [0] x          - arena X position
 *   [1] y          - arena Y position
 *   [2] vx         - X velocity (arena units / tick)
 *   [3] vy         - Y velocity (arena units / tick)
 *   [4] hp         - remaining hit points
 *   [5] type       - EnemyType ordinal
 *   [6] atkCooldown - ticks until next attack burst
 *   [7] angVel     - angular velocity of velocity vector (radians / tick); 0 = straight
 *   [8] arcLeft    - ticks of angular velocity remaining; 0 = no more rotation
 *
 * Positive angVel rotates the velocity vector clockwise on screen (RIGHT→DOWN→LEFT→UP).
 * Negative angVel rotates counter-clockwise (RIGHT→UP→LEFT→DOWN).
 * A quarter-circle arc uses arcLeft ≈ 25 and |angVel| ≈ 0.063 (25 × 0.063 ≈ π/2).
 */
public class EnemyPool {

    public static final int CAPACITY = 64;
    public static final int STRIDE   = 9;

    public static final int F_X        = 0;
    public static final int F_Y        = 1;
    public static final int F_VX       = 2;
    public static final int F_VY       = 3;
    public static final int F_HP       = 4;
    public static final int F_TYPE     = 5;
    public static final int F_ATK_CD   = 6;
    public static final int F_ANG_VEL  = 7;
    public static final int F_ARC_LEFT = 8;

    private final float[]   data   = new float[CAPACITY * STRIDE];
    private final boolean[] active = new boolean[CAPACITY];
    private int activeCount = 0;

    // ---------------------------------------------------------------- tick

    /**
     * Advance all active enemies by one step.
     * Arc rotation is applied before the position update so the new velocity
     * is used immediately.
     */
    public void tick() {
        for (int i = 0; i < CAPACITY; i++) {
            if (!active[i]) continue;
            int b = i * STRIDE;

            // Angular arc - rotate velocity vector while arcLeft > 0
            if (data[b + F_ARC_LEFT] > 0) {
                float a   = data[b + F_ANG_VEL];
                float vx  = data[b + F_VX];
                float vy  = data[b + F_VY];
                float cos = (float) Math.cos(a);
                float sin = (float) Math.sin(a);
                data[b + F_VX] = vx * cos - vy * sin;
                data[b + F_VY] = vx * sin + vy * cos;
                data[b + F_ARC_LEFT]--;
            }

            data[b + F_X] += data[b + F_VX];
            data[b + F_Y] += data[b + F_VY];
            if (data[b + F_ATK_CD] > 0) data[b + F_ATK_CD]--;
        }
    }

    /** Client-side position extrapolation (arc + movement, no deactivation). */
    public void clientTick() {
        for (int i = 0; i < CAPACITY; i++) {
            if (!active[i]) continue;
            int b = i * STRIDE;

            if (data[b + F_ARC_LEFT] > 0) {
                float a   = data[b + F_ANG_VEL];
                float vx  = data[b + F_VX];
                float vy  = data[b + F_VY];
                float cos = (float) Math.cos(a);
                float sin = (float) Math.sin(a);
                data[b + F_VX] = vx * cos - vy * sin;
                data[b + F_VY] = vx * sin + vy * cos;
                data[b + F_ARC_LEFT]--;
            }

            data[b + F_X] += data[b + F_VX];
            data[b + F_Y] += data[b + F_VY];
        }
    }

    // ---------------------------------------------------------------- spawn / deactivate

    /**
     * @param angVel   angular velocity in rad/tick (0 = straight line)
     * @param arcTicks ticks to apply angVel (0 = no arc)
     */
    public int spawn(float x, float y, float vx, float vy,
                     float angVel, int arcTicks, EnemyType type) {
        int slot = nextFreeSlot();
        if (slot == -1) return -1;
        int b = slot * STRIDE;
        data[b + F_X]        = x;
        data[b + F_Y]        = y;
        data[b + F_VX]       = vx;
        data[b + F_VY]       = vy;
        data[b + F_HP]       = type.hp;
        data[b + F_TYPE]     = type.getId();
        data[b + F_ATK_CD]   = type.atkInterval / 2; // stagger first attack
        data[b + F_ANG_VEL]  = angVel;
        data[b + F_ARC_LEFT] = arcTicks;
        active[slot] = true;
        activeCount++;
        return slot;
    }

    public void deactivate(int slot) {
        if (active[slot]) { active[slot] = false; activeCount--; }
    }

    // ---------------------------------------------------------------- setters

    /** Directly set the attack cooldown for a slot (used after firing). */
    public void setAtkCooldown(int slot, int value) {
        data[slot * STRIDE + F_ATK_CD] = value;
    }

    // ---------------------------------------------------------------- damage

    /** Returns true if the enemy died. */
    public boolean damage(int slot, int amount) {
        int b = slot * STRIDE;
        data[b + F_HP] = Math.max(0, data[b + F_HP] - amount);
        return data[b + F_HP] <= 0;
    }

    // ---------------------------------------------------------------- getters

    public float   getX(int slot)        { return data[slot * STRIDE + F_X]; }
    public float   getY(int slot)        { return data[slot * STRIDE + F_Y]; }
    public float   getVx(int slot)       { return data[slot * STRIDE + F_VX]; }
    public float   getVy(int slot)       { return data[slot * STRIDE + F_VY]; }
    public float   getHp(int slot)       { return data[slot * STRIDE + F_HP]; }
    public int     getType(int slot)     { return (int) data[slot * STRIDE + F_TYPE]; }
    public int     getAtkCd(int slot)    { return (int) data[slot * STRIDE + F_ATK_CD]; }
    public boolean isActive(int slot)    { return active[slot]; }
    public int     getActiveCount()      { return activeCount; }

    // ---------------------------------------------------------------- network sync

    public float[] getSlotData(int slot) {
        float[] out = new float[STRIDE];
        System.arraycopy(data, slot * STRIDE, out, 0, STRIDE);
        return out;
    }

    public void setSlotData(int slot, float[] d, boolean isActive) {
        System.arraycopy(d, 0, data, slot * STRIDE, STRIDE);
        if (isActive  && !active[slot]) { active[slot] = true;  activeCount++; }
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
