package mc.sayda.bullethell.arena;


/**
 * Manages the {@code RING_SPAWNER} pattern:
 *
 * <ol>
 *   <li>N rings fire from the boss in a downward cone.</li>
 *   <li>While each ring flies, a child bullet (zero velocity) is spawned at
 *       its current position every {@code childSpawnIntervalTicks}.</li>
 *   <li>Once all rings leave the playfield, a sweep direction (randomly left
 *       or right) is chosen.  Each ring layer fans out by {@code childFanDeg}
 *       degrees from the center - center ring goes straight, outer rings angle
 *       progressively above or below it, forming a half-circle spread.</li>
 *   <li>After activation, children accelerate by {@code childAcceleration}
 *       units/tick each tick until they leave the playfield.</li>
 * </ol>
 *
 * Previous-wave children are left flying freely when {@link #init} is called
 * again - only in-flight rings are cleaned up.
 */
public final class RingSpawnerRuntime {

    public static final int MAX_RINGS    = 32;
    public static final int MAX_CHILDREN = 512;

    // Ring tracking
    private final int[]     ringSlots      = new int[MAX_RINGS];
    private final int[]     ringSpawnTimer = new int[MAX_RINGS];
    private final boolean[] ringActive     = new boolean[MAX_RINGS];

    // Child tracking - direction stored at activation for acceleration
    private final int[]   childSlots   = new int[MAX_CHILDREN];
    private final int[]   childRingIdx = new int[MAX_CHILDREN];
    private final float[] childDx      = new float[MAX_CHILDREN];
    private final float[] childDy      = new float[MAX_CHILDREN];

    // Config
    private int   ringCount;
    private int   childCount;
    private int   centerRingIndex;
    private int   spawnIntervalTicks;
    private float sweepSpeed;
    private float sweepAcceleration;
    private float sweepSpreadRad;
    private int   childTypeId;
    private float childVis;
    private float childHit;

    private boolean active      = false;
    private boolean sweepActive = false;
    private int     sweepTick   = 0;

    public boolean isActive() { return active; }

    // ---------------------------------------------------------------- init

    public void init(int count, float coneAngleDeg, float coneHalfAngleDeg,
                     int ringTypeId, float ringVis, float ringHit, float ringSpeed,
                     int childTypeIdIn, float childVisIn, float childHitIn,
                     int spawnInterval, float initSweepSpeed, float initSweepAccel,
                     float sweepSpreadRadIn, BulletPool pool, float bossX, float bossY) {
        if (pool != null) {
            for (int r = 0; r < ringCount; r++) {
                if (ringActive[r] && ringSlots[r] >= 0) pool.deactivate(ringSlots[r]);
            }
        }
        resetState();
        if (pool == null) return;

        int n = Math.max(1, Math.min(count, MAX_RINGS));
        float centerRad = (float) Math.toRadians(coneAngleDeg);
        float halfRad   = (float) Math.toRadians(Math.abs(coneHalfAngleDeg));
        float step      = n > 1 ? (2f * halfRad) / (n - 1) : 0f;
        float startAng  = centerRad - halfRad;

        childTypeId        = childTypeIdIn;
        childVis           = childVisIn;
        childHit           = childHitIn;
        spawnIntervalTicks = Math.max(1, spawnInterval);
        sweepSpeed         = initSweepSpeed;
        sweepAcceleration  = initSweepAccel;
        sweepSpreadRad     = sweepSpreadRadIn;
        centerRingIndex    = (n - 1) / 2;

        for (int i = 0; i < n; i++) {
            float ang = startAng + i * step;
            float vx  = (float) Math.cos(ang) * ringSpeed;
            float vy  = (float) Math.sin(ang) * ringSpeed;
            int slot  = pool.spawn(bossX, bossY, vx, vy, ringTypeId,
                    BulletPool.LIFE_KILL_WALL_ONLY, ringVis, ringHit, 0f);
            ringSlots[i]      = slot;
            ringSpawnTimer[i] = spawnIntervalTicks;
            ringActive[i]     = (slot >= 0);
        }
        ringCount = n;
        active    = true;
    }

    // ---------------------------------------------------------------- tick

    public void tick(BulletPool pool) {
        if (!active || pool == null) return;

        if (sweepActive) {
            sweepTick++;
            float speed = sweepSpeed + sweepAcceleration * sweepTick;
            boolean anyAlive = false;
            for (int i = 0; i < childCount; i++) {
                int slot = childSlots[i];
                if (slot < 0 || !pool.isActive(slot)) continue;
                anyAlive = true;
                pool.setVx(slot, childDx[i] * speed);
                pool.setVy(slot, childDy[i] * speed);
            }
            if (!anyAlive) {
                sweepActive = false;
                active      = false;
            }
            return;
        }

        boolean anyRingActive = false;
        for (int r = 0; r < ringCount; r++) {
            if (!ringActive[r]) continue;
            int slot = ringSlots[r];
            if (!pool.isActive(slot)) {
                ringActive[r] = false;
                continue;
            }
            anyRingActive = true;
            if (--ringSpawnTimer[r] <= 0) {
                ringSpawnTimer[r] = spawnIntervalTicks;
                spawnChild(pool, slot, r);
            }
        }

        if (!anyRingActive) {
            sweepChildren(pool);
        }
    }

    // ---------------------------------------------------------------- helpers

    private void spawnChild(BulletPool pool, int ringSlot, int ringIdx) {
        if (childCount >= MAX_CHILDREN) return;
        int slot = pool.spawn(pool.getX(ringSlot), pool.getY(ringSlot),
                0f, 0f, childTypeId, BulletPool.LIFE_KILL_WALL_ONLY, childVis, childHit, 0f);
        if (slot >= 0) {
            childSlots[childCount]   = slot;
            childRingIdx[childCount] = ringIdx;
            childCount++;
        }
    }

    /**
     * Fans children into a half-circle: center ring goes straight left/right,
     * each layer fans out by sweepSpreadRad. Stores per-child direction for
     * the acceleration phase.
     */
    private void sweepChildren(BulletPool pool) {
        float baseAngle = (float) ((Math.random() < 0.5) ? 0 : Math.PI);

        for (int i = 0; i < childCount; i++) {
            int slot = childSlots[i];
            if (slot < 0 || !pool.isActive(slot)) continue;
            int   layerSigned = childRingIdx[i] - centerRingIndex;
            float angle       = baseAngle + layerSigned * sweepSpreadRad;
            float dx          = (float) Math.cos(angle);
            float dy          = (float) Math.sin(angle);
            childDx[i] = dx;
            childDy[i] = dy;
            pool.setVx(slot, dx * sweepSpeed);
            pool.setVy(slot, dy * sweepSpeed);
        }
        sweepActive = true;
        sweepTick   = 0;
    }

    // ---------------------------------------------------------------- clear

    public void clear(BulletPool pool) {
        if (pool != null) {
            for (int r = 0; r < ringCount; r++) {
                if (ringActive[r] && ringSlots[r] >= 0) pool.deactivate(ringSlots[r]);
            }
        }
        resetState();
    }

    private void resetState() {
        for (int r = 0; r < MAX_RINGS;    r++) { ringActive[r] = false; ringSlots[r]  = -1; }
        for (int i = 0; i < MAX_CHILDREN; i++) { childSlots[i] = -1;   childRingIdx[i] = 0; childDx[i] = 0f; childDy[i] = 0f; }
        ringCount   = 0;
        childCount  = 0;
        active      = false;
        sweepActive = false;
        sweepTick   = 0;
    }
}
