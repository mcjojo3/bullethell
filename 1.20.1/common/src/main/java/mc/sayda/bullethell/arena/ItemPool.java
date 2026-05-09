package mc.sayda.bullethell.arena;

import java.util.Arrays;

import mc.sayda.bullethell.config.BullethellConfig;

/**
 * Fixed-size pool for collectible items dropped by the boss.
 *
 * Layout per slot: [x, y, vy, type, timer]
 * x, y - arena position
 * vy - vertical velocity (starts negative = floating up)
 * type - ItemType ordinal
 * timer - remaining lifetime ticks (item despawns at 0)
 *
 * Physics: light gravity each tick (vy += GRAVITY), capped at MAX_FALL_SPEED.
 * Items start rising, slow down, then fall.
 */
public class ItemPool {

    public static final int CAPACITY = 1024;

    public static final int STRIDE = 6;
    public static final float INITIAL_VY = -2.5f;
    private static final float GRAVITY = 0.08f;
    private static final float MAX_FALL_SPEED = 3.5f;

    /**
     * Speed at which bomb-attracted items fly toward the player (arena units/tick).
     * 
     * @see BullethellConfig#ITEM_ATTRACT_SPEED
     */
    public static float attractSpeed() {
        return BullethellConfig.ITEM_ATTRACT_SPEED.get();
    }

    public static final int F_X = 0;
    public static final int F_Y = 1;
    public static final int F_VY = 2;
    public static final int F_TYPE = 3;
    public static final int F_LIFE = 4;
    /**
     * 1.0 = item is being attracted toward the player (bomb vacuum); 0.0 = normal
     * fall.
     */
    public static final int F_ATTRACT = 5;

    // ---------------------------------------------------------------- item types

    public static final int TYPE_POWER = 0;
    public static final int TYPE_POINT = 1;
    public static final int TYPE_FULL_POWER = 2;
    public static final int TYPE_ONE_UP = 3;
    public static final int TYPE_BOMB = 4;
    public static final int TYPE_POWER_LARGE = 5;
    public static final int TYPE_LIFE_PIECE = 6;
    public static final int TYPE_BOMB_PIECE = 7;
    public static final int TYPE_POINT_GREEN = 8;

    private static final int[] ITEM_COLORS = { 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
            0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF };
    private static final int[] ITEM_SIZES = { 3, 3, 5, 5, 4, 5, 3, 3, 3 };

    public static int colorOf(int type) {
        return type < ITEM_COLORS.length ? ITEM_COLORS[type] : 0xFFFFFFFF;
    }

    public static int sizeOf(int type) {
        return type < ITEM_SIZES.length ? ITEM_SIZES[type] : 3;
    }

    // ---------------------------------------------------------------- storage

    private final float[] data = new float[CAPACITY * STRIDE];
    private final boolean[] active = new boolean[CAPACITY];
    private final float[] prevX = new float[CAPACITY];
    private final float[] prevY = new float[CAPACITY];
    private int activeCount = 0;

    // ---------------------------------------------------------------- tick

    public void tick() {
        for (int i = 0; i < CAPACITY; i++) {
            if (!active[i])
                continue;
            int b = i * STRIDE;
            if (data[b + F_ATTRACT] != 0f)
                continue; // attraction handled by ArenaContext
            // Apply gravity
            float vy = data[b + F_VY] + GRAVITY;
            if (vy > MAX_FALL_SPEED)
                vy = MAX_FALL_SPEED;
            data[b + F_VY] = vy;
            data[b + F_Y] += vy;
            data[b + F_LIFE]--;
            if (data[b + F_LIFE] <= 0 || data[b + F_Y] > BulletPool.ARENA_H + 16f) {
                deactivate(i);
            }
        }
    }

    /**
     * Client-side extrapolation (no deactivation - server corrections handle that).
     * Pass the player's current arena position so attracting items can be
     * extrapolated.
     */
    public void clientTick(float playerX, float playerY, boolean frozen) {
        for (int i = 0; i < CAPACITY; i++) {
            if (!active[i])
                continue;
            int b = i * STRIDE;
            if (data[b + F_ATTRACT] != 0f) {
                // Fly toward player (always happens even in time stop)
                float dx = playerX - data[b + F_X];
                float dy = playerY - data[b + F_Y];
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > 0.5f) {
                    float nx = dx / dist * attractSpeed();
                    float ny = dy / dist * attractSpeed();
                    data[b + F_X] += nx;
                    data[b + F_Y] += ny;
                }
            } else if (!frozen) {
                float vy = data[b + F_VY] + GRAVITY;
                if (vy > MAX_FALL_SPEED)
                    vy = MAX_FALL_SPEED;
                data[b + F_VY] = vy;
                data[b + F_Y] += vy;
            }
        }
    }

    // ---------------------------------------------------------------- spawn /
    // deactivate

    public int spawn(float x, float y, int type) {
        int slot = nextFreeSlot();
        if (slot == -1)
            return -1;
        int b = slot * STRIDE;
        data[b + F_X] = x;
        data[b + F_Y] = y;
        data[b + F_VY] = INITIAL_VY;
        data[b + F_TYPE] = type;
        data[b + F_LIFE] = BullethellConfig.ITEM_COLLECTIBLE_LIFE_TICKS.get();
        data[b + F_ATTRACT] = 0f;
        prevX[slot] = x;
        prevY[slot] = y;
        active[slot] = true;
        activeCount++;
        return slot;
    }

    public void deactivate(int slot) {
        if (active[slot]) {
            active[slot] = false;
            activeCount--;
        }
    }

    // ---------------------------------------------------------------- getters

    public float getX(int slot) {
        return data[slot * STRIDE + F_X];
    }

    public float getY(int slot) {
        return data[slot * STRIDE + F_Y];
    }

    public float getVy(int slot) {
        return data[slot * STRIDE + F_VY];
    }

    public int getType(int slot) {
        return (int) data[slot * STRIDE + F_TYPE];
    }

    public boolean isActive(int slot) {
        return active[slot];
    }

    public int getActiveCount() {
        return activeCount;
    }

    public boolean isAttracting(int slot) {
        return data[slot * STRIDE + F_ATTRACT] != 0f;
    }

    public float getPrevX(int slot) { return prevX[slot]; }
    public float getPrevY(int slot) { return prevY[slot]; }

    public void savePrevPositions() {
        for (int i = 0; i < CAPACITY; i++) {
            if (active[i]) {
                prevX[i] = data[i * STRIDE + F_X];
                prevY[i] = data[i * STRIDE + F_Y];
            }
        }
    }

    public void setX(int slot, float x) {
        data[slot * STRIDE + F_X] = x;
    }

    public void setY(int slot, float y) {
        data[slot * STRIDE + F_Y] = y;
    }

    public void setAttracting(int slot, boolean val) {
        data[slot * STRIDE + F_ATTRACT] = val ? 1f : 0f;
    }

    // ---------------------------------------------------------------- network sync

    public float[] getSlotData(int slot) {
        float[] out = new float[STRIDE];
        System.arraycopy(data, slot * STRIDE, out, 0, STRIDE);
        return out;
    }

    public void setSlotData(int slot, float[] d, boolean isActive) {
        if (isActive && active[slot]) {
            prevX[slot] = data[slot * STRIDE + F_X];
            prevY[slot] = data[slot * STRIDE + F_Y];
        }
        System.arraycopy(d, 0, data, slot * STRIDE, STRIDE);
        if (isActive && !active[slot]) {
            prevX[slot] = d[F_X];
            prevY[slot] = d[F_Y];
            active[slot] = true;
            activeCount++;
        } else if (!isActive && active[slot]) {
            active[slot] = false;
            activeCount--;
        }
    }

    public void clearAll() {
        for (int i = 0; i < CAPACITY; i++)
            if (active[i])
                deactivate(i);
    }

    // ---------------------------------------------------------------- helpers

    private int nextFreeSlot() {
        for (int i = 0; i < CAPACITY; i++)
            if (!active[i])
                return i;
        return -1;
    }
}
