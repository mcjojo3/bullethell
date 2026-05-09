package mc.sayda.bullethell.arena;

import mc.sayda.bullethell.boss.WormCircleDef;
import mc.sayda.bullethell.pattern.BulletType;

/**
 * Manages a {@code WORM_CIRCLE} formation: one or more knife rings that orbit
 * the boss for {@code spinTicks} then fire radially outward, creating a flower
 * of straight needle lines.
 *
 * <p>Each tick, knives are repositioned kinematically (velocity kept at zero).
 * On release, each knife gets an outward velocity proportional to {@code fireSpeed}
 * and is detached from the formation — it then flies as a normal bullet.
 */
public final class WormCircleRuntime {

    public static final int MAX_RINGS  = 8;
    public static final int MAX_KNIVES = 20;

    private final int[][]   slots          = new int[MAX_RINGS][MAX_KNIVES];
    private final float[]   baseAngle      = new float[MAX_RINGS]; // current base angle, radians
    private final float[]   spinSpeed      = new float[MAX_RINGS]; // rad/tick
    private final float[]   orbitRadius    = new float[MAX_RINGS];
    private final float[][] localAngle     = new float[MAX_RINGS][MAX_KNIVES];
    private final int[]     knifeCount     = new int[MAX_RINGS];
    private int   ringCount            = 0;
    private int   spinTicksRemaining   = 0;
    private float fireSpeed            = 5f;
    private boolean active             = false;

    public boolean isActive() { return active; }

    // ---------------------------------------------------------------- init

    public void init(WormCircleDef[] defs, int spinTicks, float fireSpeedIn,
                     BulletPool pool, float bossX, float bossY) {
        clear(pool);
        if (defs == null || defs.length == 0) return;
        ringCount             = Math.min(defs.length, MAX_RINGS);
        spinTicksRemaining    = Math.max(1, spinTicks);
        fireSpeed             = fireSpeedIn;

        for (int r = 0; r < ringCount; r++) {
            WormCircleDef def = defs[r];
            int n = Math.max(1, Math.min(def.knifeCount, MAX_KNIVES));
            knifeCount[r]  = n;
            baseAngle[r]   = (float) Math.toRadians(def.startAngleDeg);
            spinSpeed[r]   = (float) Math.toRadians(def.spinSpeedDeg);
            orbitRadius[r] = Math.max(1f, def.orbitRadius);

            float spanRad  = (float) Math.toRadians(def.spanDeg);
            float stepRad  = n > 1 ? spanRad / n : 0f;

            String typeName = (def.bulletType == null || def.bulletType.isEmpty())
                    ? "KUNAI" : def.bulletType;
            BulletType bt  = BulletType.fromName(typeName);
            float vis = def.bulletScale > 0.01f ? def.bulletScale : 1f;
            float hit = def.hitboxScale > 0.01f ? def.hitboxScale : 1f;
            int typeId = bt.getId();

            for (int i = 0; i < n; i++) {
                localAngle[r][i] = stepRad * i;
                float ang = baseAngle[r] + localAngle[r][i];
                float kx  = bossX + (float) Math.cos(ang) * orbitRadius[r];
                float ky  = bossY + (float) Math.sin(ang) * orbitRadius[r];
                int slot  = pool.spawn(kx, ky, 0f, 0f, typeId,
                        BulletPool.LIFE_KILL_WALL_ONLY, vis, hit, 0f);
                slots[r][i] = slot;
            }
        }
        active = true;
    }

    // ---------------------------------------------------------------- tick

    /**
     * Call once per boss-AI tick while the formation is active.
     * Updates kinematic positions each tick and releases knives radially when the
     * spin duration expires.
     *
     * @return {@code true} on the exact tick knives are released (formation just fired)
     */
    public boolean tick(BulletPool pool, float bossX, float bossY) {
        if (!active) return false;

        spinTicksRemaining--;
        if (spinTicksRemaining <= 0) {
            release(pool, bossX, bossY);
            active = false;
            return true;
        }

        for (int r = 0; r < ringCount; r++) {
            baseAngle[r] += spinSpeed[r];
            float rad = orbitRadius[r];
            int n = knifeCount[r];
            for (int i = 0; i < n; i++) {
                int slot = slots[r][i];
                if (slot < 0 || !pool.isActive(slot)) continue;
                float ang = baseAngle[r] + localAngle[r][i];
                pool.setPosition(slot,
                        bossX + (float) Math.cos(ang) * rad,
                        bossY + (float) Math.sin(ang) * rad);
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- release

    private void release(BulletPool pool, float bossX, float bossY) {
        for (int r = 0; r < ringCount; r++) {
            int n = knifeCount[r];
            for (int i = 0; i < n; i++) {
                int slot = slots[r][i];
                if (slot < 0 || !pool.isActive(slot)) continue;
                float kx = pool.getX(slot);
                float ky = pool.getY(slot);
                float dx = kx - bossX;
                float dy = ky - bossY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float nx, ny;
                if (dist < 1e-4f) {
                    // Knife exactly at boss centre — use orbit angle as fallback
                    float ang = baseAngle[r] + localAngle[r][i];
                    nx = (float) Math.cos(ang);
                    ny = (float) Math.sin(ang);
                } else {
                    nx = dx / dist;
                    ny = dy / dist;
                }
                pool.setVx(slot, nx * fireSpeed);
                pool.setVy(slot, ny * fireSpeed);
                pool.setRemainingLife(slot, BulletPool.LIFE_KILL_WALL_ONLY);
                slots[r][i] = -1; // detach — bullet flies free
            }
        }
    }

    // ---------------------------------------------------------------- clear

    public void clear(BulletPool pool) {
        if (pool != null) {
            for (int r = 0; r < ringCount; r++) {
                for (int i = 0; i < knifeCount[r]; i++) {
                    if (slots[r][i] >= 0) pool.deactivate(slots[r][i]);
                    slots[r][i] = -1;
                }
            }
        }
        for (int r = 0; r < MAX_RINGS; r++) knifeCount[r] = 0;
        ringCount          = 0;
        spinTicksRemaining = 0;
        active             = false;
    }
}
