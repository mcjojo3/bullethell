package mc.sayda.bullethell.arena;

import mc.sayda.bullethell.boss.WormCircleDef;
import mc.sayda.bullethell.pattern.BulletType;

/**
 * Manages a {@code WORM_CIRCLE} formation: knives fly outward from the boss
 * centre, orbit the boss for {@code orbitTicks}, then fire in the direction
 * knife 0 is facing (tangential to the orbit).  The remaining knives follow
 * sequentially, each firing from their own orbit position in the same
 * direction — creating a parallel stream (worm) per ring instance.
 *
 * <p>Travel animation: the first third of {@code orbitTicks} is used to slide
 * knives outward from the boss centre to the orbit radius; the remainder is
 * pure rotation.
 */
public final class WormCircleRuntime {

    public static final int MAX_RINGS  = 64;
    public static final int MAX_KNIVES = 20;

    private final int[][]   slots            = new int[MAX_RINGS][MAX_KNIVES];
    private final float[]   baseAngle        = new float[MAX_RINGS];
    private final float[]   spinSpeed        = new float[MAX_RINGS];
    private final float[]   orbitRadius      = new float[MAX_RINGS];
    private final float[][] localAngle       = new float[MAX_RINGS][MAX_KNIVES];
    private final int[]     wormLength       = new int[MAX_RINGS];
    private final float[]   fireDirX         = new float[MAX_RINGS];
    private final float[]   fireDirY         = new float[MAX_RINGS];
    private final int[][]   knifeReleaseTick = new int[MAX_RINGS][MAX_KNIVES];

    private int   ringCount            = 0;
    private int   spinTicksRemaining   = 0;
    private int   totalSpinTicks       = 0;
    private int   travelTicks          = 0; // outward-travel portion of spin phase
    private float fireSpeed            = 5f;
    private boolean active             = false;
    private boolean releasing          = false;
    private int   releaseTickCounter   = 0;

    public boolean isActive() { return active; }

    // ---------------------------------------------------------------- init

    public void init(WormCircleDef[] defs, int spinTicks, float fireSpeedIn,
                     BulletPool pool, float bossX, float bossY) {
        clear(pool);
        if (defs == null || defs.length == 0) return;
        totalSpinTicks     = Math.max(1, spinTicks);
        spinTicksRemaining = totalSpinTicks;
        travelTicks        = Math.max(1, totalSpinTicks / 3);
        fireSpeed          = fireSpeedIn;

        int r = 0;
        for (int d = 0; d < defs.length && r < MAX_RINGS; d++) {
            WormCircleDef def = defs[d];
            int inst       = Math.max(1, def.instances);
            float instStep = inst > 1 ? 360f / inst : 0f;

            String typeName = (def.bulletType == null || def.bulletType.isEmpty())
                    ? "KUNAI" : def.bulletType;
            BulletType bt = BulletType.fromName(typeName);
            float vis    = def.bulletScale  > 0.01f ? def.bulletScale  : 1f;
            float hit    = def.hitboxScale  > 0.01f ? def.hitboxScale  : 1f;
            int typeId   = bt.getId();
            int n        = Math.max(1, Math.min(def.wormLength, MAX_KNIVES));
            float spanRad  = (float) Math.toRadians(def.spanDeg);
            float stepRad  = n > 1 ? spanRad / n : 0f;
            float spd      = (float) Math.toRadians(def.spinSpeedDeg);
            float rad      = Math.max(1f, def.orbitRadius);
            float absSpeed = Math.abs(spd);

            for (int k = 0; k < inst && r < MAX_RINGS; k++, r++) {
                float start  = (float) Math.toRadians(def.startAngleDeg + k * instStep);
                wormLength[r]  = n;
                baseAngle[r]   = start;
                spinSpeed[r]   = spd;
                orbitRadius[r] = rad;

                for (int i = 0; i < n; i++) {
                    // CW (spd < 0): trailing knives ahead in CCW direction.
                    // CCW (spd > 0): trailing knives behind in CCW direction.
                    // Both reach fire angle after |localAngle| / absSpeed ticks.
                    localAngle[r][i] = spd >= 0 ? -stepRad * i : stepRad * i;
                    // Spawn at boss centre — will be animated outward during travel phase.
                    slots[r][i] = pool.spawn(bossX, bossY, 0f, 0f, typeId,
                            BulletPool.LIFE_KILL_WALL_ONLY, vis, hit, 0f);
                }

                knifeReleaseTick[r][0] = 0;
                for (int i = 1; i < n; i++) {
                    knifeReleaseTick[r][i] = absSpeed < 1e-6f
                            ? 0
                            : Math.max(1, Math.round(Math.abs(localAngle[r][i]) / absSpeed));
                }
            }
        }
        ringCount = r;
        active = true;
    }

    // ---------------------------------------------------------------- tick

    public boolean tick(BulletPool pool, float bossX, float bossY) {
        if (!active) return false;

        if (!releasing) {
            spinTicksRemaining--;
            if (spinTicksRemaining <= 0) {
                // Record knife 0's tangential direction as the worm fire direction.
                for (int r = 0; r < ringCount; r++) {
                    float ang  = baseAngle[r]; // knife 0 always at localAngle == 0
                    float sign = spinSpeed[r] >= 0 ? 1f : -1f;
                    fireDirX[r] = sign * -(float) Math.sin(ang);
                    fireDirY[r] = sign *  (float) Math.cos(ang);
                }
                releasing          = true;
                releaseTickCounter = 0;
                for (int r = 0; r < ringCount; r++) {
                    for (int i = 0; i < wormLength[r]; i++) {
                        if (knifeReleaseTick[r][i] == 0)
                            releaseKnife(pool, r, i, bossX, bossY);
                    }
                }
            } else {
                int elapsed = totalSpinTicks - spinTicksRemaining;
                for (int r = 0; r < ringCount; r++) {
                    if (elapsed < travelTicks) {
                        // Travel phase: slide outward from boss centre to orbit radius.
                        float progress = (float) elapsed / travelTicks;
                        updateKnifePositions(pool, r, bossX, bossY, orbitRadius[r] * progress);
                    } else {
                        // Spin phase: kinematic orbit at full radius.
                        baseAngle[r] += spinSpeed[r];
                        updateKnifePositions(pool, r, bossX, bossY, orbitRadius[r]);
                    }
                }
            }
            return false;
        }

        // Release phase: keep spinning remaining knives, fire each on schedule.
        releaseTickCounter++;
        boolean anyLeft = false;
        for (int r = 0; r < ringCount; r++) {
            baseAngle[r] += spinSpeed[r];
            int n = wormLength[r];
            for (int i = 0; i < n; i++) {
                int slot = slots[r][i];
                if (slot < 0 || !pool.isActive(slot)) continue;
                if (releaseTickCounter >= knifeReleaseTick[r][i]) {
                    releaseKnife(pool, r, i, bossX, bossY);
                } else {
                    updateKnifePosition(pool, slot, r, i, bossX, bossY, orbitRadius[r]);
                    anyLeft = true;
                }
            }
        }

        if (!anyLeft) {
            active = false;
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- helpers

    private void updateKnifePositions(BulletPool pool, int r,
                                      float bossX, float bossY, float radius) {
        int n = wormLength[r];
        for (int i = 0; i < n; i++) {
            int slot = slots[r][i];
            if (slot < 0 || !pool.isActive(slot)) continue;
            updateKnifePosition(pool, slot, r, i, bossX, bossY, radius);
        }
    }

    private void updateKnifePosition(BulletPool pool, int slot, int r, int i,
                                     float bossX, float bossY, float radius) {
        float ang = baseAngle[r] + localAngle[r][i];
        pool.setPosition(slot,
                bossX + (float) Math.cos(ang) * radius,
                bossY + (float) Math.sin(ang) * radius);
        // Set tangential velocity so the renderer rotates the sprite correctly.
        float spd  = spinSpeed[r];
        float sign = spd >= 0 ? 1f : -1f;
        float tvx  = sign * -(float) Math.sin(ang) * Math.abs(spd) * orbitRadius[r];
        float tvy  = sign *  (float) Math.cos(ang) * Math.abs(spd) * orbitRadius[r];
        pool.setVx(slot, tvx);
        pool.setVy(slot, tvy);
    }

    // ---------------------------------------------------------------- release

    /**
     * Fires knife {@code i} from its current orbit position in knife 0's
     * tangential direction (all knives in a ring share the same fire vector).
     */
    private void releaseKnife(BulletPool pool, int r, int i,
                               float bossX, float bossY) {
        int slot = slots[r][i];
        if (slot < 0 || !pool.isActive(slot)) return;
        // Position: current orbit location of this knife.
        float ang = baseAngle[r] + localAngle[r][i];
        float kx  = bossX + (float) Math.cos(ang) * orbitRadius[r];
        float ky  = bossY + (float) Math.sin(ang) * orbitRadius[r];
        pool.setPosition(slot, kx, ky);
        // Direction: knife 0's tangent, shared by the whole worm.
        pool.setVx(slot, fireDirX[r] * fireSpeed);
        pool.setVy(slot, fireDirY[r] * fireSpeed);
        pool.setRemainingLife(slot, BulletPool.LIFE_KILL_WALL_ONLY);
        slots[r][i] = -1;
    }

    // ---------------------------------------------------------------- clear

    public void clear(BulletPool pool) {
        if (pool != null) {
            for (int r = 0; r < ringCount; r++) {
                for (int i = 0; i < wormLength[r]; i++) {
                    if (slots[r][i] >= 0) pool.deactivate(slots[r][i]);
                    slots[r][i] = -1;
                }
            }
        }
        for (int r = 0; r < MAX_RINGS; r++) wormLength[r] = 0;
        ringCount          = 0;
        spinTicksRemaining = 0;
        totalSpinTicks     = 0;
        travelTicks        = 0;
        releasing          = false;
        releaseTickCounter = 0;
        active             = false;
    }
}
