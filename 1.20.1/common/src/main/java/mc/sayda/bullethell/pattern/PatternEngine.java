package mc.sayda.bullethell.pattern;

import mc.sayda.bullethell.arena.BulletPool;
import mc.sayda.bullethell.arena.DifficultyConfig;
import mc.sayda.bullethell.config.BullethellConfig;

/**
 * Stateless shot factories.
 *
 * Each method receives a BulletPool and DifficultyConfig so patterns scale across difficulties.
 * {@code lifetimeTicks} &le; 0 selects pattern defaults; {@code angVelRadPerTick} rotates the
 * velocity vector each tick (0 = straight).
 *
 * Scale overloads ({@code visScale}, {@code hitScale}) multiply render radius and hit radius
 * vs {@link BulletType#radius} (see {@link BulletPool#F_VIS_SCALE} / {@link BulletPool#F_HIT_SCALE}).
 */
public final class PatternEngine {

    /** Default enemy bullet lifetime when JSON does not override (mod ticks). */
    public static final int DEFAULT_LIFE_RING = 200;
    public static final int DEFAULT_LIFE_AIMED = 220;
    public static final int DEFAULT_LIFE_RAIN = 230;

    /** Default radians between adjacent shots in {@code LASER_BEAM} (tight aimed burst). */
    public static final float DEFAULT_LASER_BEAM_SPREAD = 0.04f;

    private PatternEngine() {}

    /** Difficulty speed mult × global enemy bullet slowdown (configurable). */
    private static float enemySpeed(DifficultyConfig diff) {
        return diff.speedMult * BullethellConfig.GLOBAL_ENEMY_BULLET_SPEED_MULT.get();
    }

    private static int lifeOrDefault(int lifetimeTicks, int def) {
        return lifetimeTicks > 0 ? lifetimeTicks : def;
    }

    // ---------------------------------------------------------------- spiral

    public static void fireSpiral(BulletPool pool, float bx, float by,
                                   float angleOffset, int arms, float speed,
                                   DifficultyConfig diff, BulletType type) {
        fireSpiral(pool, bx, by, angleOffset, arms, speed, diff, type, 1f, 1f, -1, 0f);
    }

    public static void fireSpiral(BulletPool pool, float bx, float by,
                                   float angleOffset, int arms, float speed,
                                   DifficultyConfig diff, BulletType type,
                                   float visScale, float hitScale) {
        fireSpiral(pool, bx, by, angleOffset, arms, speed, diff, type, visScale, hitScale, -1, 0f);
    }

    public static void fireSpiral(BulletPool pool, float bx, float by,
                                   float angleOffset, int arms, float speed,
                                   DifficultyConfig diff, BulletType type,
                                   float visScale, float hitScale, int lifetimeTicks,
                                   float angVelRadPerTick) {
        int life = lifeOrDefault(lifetimeTicks, DEFAULT_LIFE_RING);
        float step = (float) (Math.PI * 2.0 / arms);
        for (int i = 0; i < arms; i++) {
            float angle = angleOffset + step * i;
            float vx = (float) Math.cos(angle) * speed * enemySpeed(diff);
            float vy = (float) Math.sin(angle) * speed * enemySpeed(diff);
            pool.spawn(bx, by, vx, vy, type.getId(), life, visScale, hitScale, angVelRadPerTick);
        }
    }

    public static void fireSpiral(BulletPool pool, float bx, float by,
                                   float angleOffset, int arms, float speed,
                                   DifficultyConfig diff) {
        fireSpiral(pool, bx, by, angleOffset, arms, speed, diff, BulletType.ORB);
    }

    // ---------------------------------------------------------------- aimed

    public static void fireAimed(BulletPool pool, float bx, float by,
                                  float tx, float ty,
                                  int count, float spread, float speed,
                                  DifficultyConfig diff, BulletType type) {
        fireAimed(pool, bx, by, tx, ty, count, spread, speed, diff, type, 1f, 1f, -1, 0f);
    }

    public static void fireAimed(BulletPool pool, float bx, float by,
                                  float tx, float ty,
                                  int count, float spread, float speed,
                                  DifficultyConfig diff, BulletType type,
                                  float visScale, float hitScale) {
        fireAimed(pool, bx, by, tx, ty, count, spread, speed, diff, type, visScale, hitScale, -1, 0f);
    }

    public static void fireAimed(BulletPool pool, float bx, float by,
                                  float tx, float ty,
                                  int count, float spread, float speed,
                                  DifficultyConfig diff, BulletType type,
                                  float visScale, float hitScale, int lifetimeTicks,
                                  float angVelRadPerTick) {
        int life = lifeOrDefault(lifetimeTicks, DEFAULT_LIFE_AIMED);
        float baseAngle  = (float) Math.atan2(ty - by, tx - bx);
        float halfSpread = spread * (count - 1) / 2f;
        for (int i = 0; i < count; i++) {
            float angle = baseAngle - halfSpread + spread * i;
            float vx = (float) Math.cos(angle) * speed * enemySpeed(diff);
            float vy = (float) Math.sin(angle) * speed * enemySpeed(diff);
            pool.spawn(bx, by, vx, vy, type.getId(), life, visScale, hitScale, angVelRadPerTick);
        }
    }

    public static void fireAimed(BulletPool pool, float bx, float by,
                                  float tx, float ty,
                                  int count, float spread, float speed,
                                  DifficultyConfig diff) {
        fireAimed(pool, bx, by, tx, ty, count, spread, speed, diff, BulletType.STAR);
    }

    // ---------------------------------------------------------------- ring

    public static void fireRing(BulletPool pool, float bx, float by,
                                 int count, float speed,
                                 DifficultyConfig diff, BulletType type) {
        fireRing(pool, bx, by, count, speed, diff, type, 1f, 1f, -1, 0f, 0f);
    }

    public static void fireRing(BulletPool pool, float bx, float by,
                                 int count, float speed,
                                 DifficultyConfig diff, BulletType type,
                                 float visScale, float hitScale) {
        fireRing(pool, bx, by, count, speed, diff, type, visScale, hitScale, -1, 0f, 0f);
    }

    /**
     * @param ringStartRad first bullet angle; 0 = first bullet to +X, stepping CCW in screen space
     */
    public static void fireRing(BulletPool pool, float bx, float by,
                                 int count, float speed,
                                 DifficultyConfig diff, BulletType type,
                                 float visScale, float hitScale, int lifetimeTicks,
                                 float angVelRadPerTick, float ringStartRad) {
        int life = lifeOrDefault(lifetimeTicks, DEFAULT_LIFE_RING);
        float step = (float) (Math.PI * 2.0 / count);
        for (int i = 0; i < count; i++) {
            float angle = ringStartRad + step * i;
            float vx = (float) Math.cos(angle) * speed * enemySpeed(diff);
            float vy = (float) Math.sin(angle) * speed * enemySpeed(diff);
            pool.spawn(bx, by, vx, vy, type.getId(), life, visScale, hitScale, angVelRadPerTick);
        }
    }

    public static void fireRing(BulletPool pool, float bx, float by,
                                 int count, float speed, DifficultyConfig diff) {
        fireRing(pool, bx, by, count, speed, diff, BulletType.RICE);
    }

    // ---------------------------------------------------------------- spread (downward fan)

    public static void fireSpread(BulletPool pool, float bx, float by,
                                   int count, float speed,
                                   DifficultyConfig diff, BulletType type) {
        fireSpread(pool, bx, by, count, speed, diff, type, 1f, 1f, -1, 0f);
    }

    public static void fireSpread(BulletPool pool, float bx, float by,
                                   int count, float speed,
                                   DifficultyConfig diff, BulletType type,
                                   float visScale, float hitScale) {
        fireSpread(pool, bx, by, count, speed, diff, type, visScale, hitScale, -1, 0f);
    }

    public static void fireSpread(BulletPool pool, float bx, float by,
                                   int count, float speed,
                                   DifficultyConfig diff, BulletType type,
                                   float visScale, float hitScale, int lifetimeTicks,
                                   float angVelRadPerTick) {
        fireAimed(pool, bx, by, bx, by + 100f, count, 0.28f, speed, diff, type, visScale, hitScale,
                lifetimeTicks, angVelRadPerTick);
    }

    public static void fireSpread(BulletPool pool, float bx, float by,
                                   int count, float speed, DifficultyConfig diff) {
        fireSpread(pool, bx, by, count, speed, diff, BulletType.STAR);
    }

    // ---------------------------------------------------------------- dense ring

    public static void fireDenseRing(BulletPool pool, float bx, float by,
                                      int countPerRing, float speed,
                                      DifficultyConfig diff, BulletType type) {
        fireDenseRing(pool, bx, by, countPerRing, speed, diff, type, 1f, 1f, -1, 0f, 0f);
    }

    public static void fireDenseRing(BulletPool pool, float bx, float by,
                                      int countPerRing, float speed,
                                      DifficultyConfig diff, BulletType type,
                                      float visScale, float hitScale) {
        fireDenseRing(pool, bx, by, countPerRing, speed, diff, type, visScale, hitScale, -1, 0f, 0f);
    }

    public static void fireDenseRing(BulletPool pool, float bx, float by,
                                      int countPerRing, float speed,
                                      DifficultyConfig diff, BulletType type,
                                      float visScale, float hitScale, int lifetimeTicks,
                                      float angVelRadPerTick, float ringStartRad) {
        fireRing(pool, bx, by, countPerRing, speed, diff, type, visScale, hitScale,
                lifetimeTicks, angVelRadPerTick, ringStartRad);
        float halfStep = (float) (Math.PI / countPerRing);
        float step     = (float) (Math.PI * 2.0 / countPerRing);
        BulletType altType = (type == BulletType.BUBBLE) ? BulletType.RICE : BulletType.BUBBLE;
        int life = lifeOrDefault(lifetimeTicks, DEFAULT_LIFE_RING);
        for (int i = 0; i < countPerRing; i++) {
            float angle = ringStartRad + halfStep + step * i;
            float vx = (float) Math.cos(angle) * speed * enemySpeed(diff);
            float vy = (float) Math.sin(angle) * speed * enemySpeed(diff);
            pool.spawn(bx, by, vx, vy, altType.getId(), life, visScale, hitScale, angVelRadPerTick);
        }
    }

    public static void fireDenseRing(BulletPool pool, float bx, float by,
                                      int countPerRing, float speed, DifficultyConfig diff) {
        fireDenseRing(pool, bx, by, countPerRing, speed, diff, BulletType.BUBBLE);
    }

    // ---------------------------------------------------------------- ring with offset

    public static void fireRingOffset(BulletPool pool, float bx, float by,
                                       int count, float speed,
                                       DifficultyConfig diff, BulletType type,
                                       float startAngle) {
        fireRingOffset(pool, bx, by, count, speed, diff, type, startAngle, 1f, 1f, -1, 0f);
    }

    public static void fireRingOffset(BulletPool pool, float bx, float by,
                                       int count, float speed,
                                       DifficultyConfig diff, BulletType type,
                                       float startAngle,
                                       float visScale, float hitScale) {
        fireRingOffset(pool, bx, by, count, speed, diff, type, startAngle, visScale, hitScale, -1, 0f);
    }

    public static void fireRingOffset(BulletPool pool, float bx, float by,
                                       int count, float speed,
                                       DifficultyConfig diff, BulletType type,
                                       float startAngle,
                                       float visScale, float hitScale, int lifetimeTicks,
                                       float angVelRadPerTick) {
        fireRing(pool, bx, by, count, speed, diff, type, visScale, hitScale,
                lifetimeTicks, angVelRadPerTick, startAngle);
    }

    // ---------------------------------------------------------------- aimed fan + outer ring

    public static void fireAimedWithRing(BulletPool pool, float bx, float by,
                                          float tx, float ty,
                                          int aimCount, float aimSpread, float aimSpeed,
                                          int ringCount, float ringSpeed,
                                          DifficultyConfig diff,
                                          BulletType aimType, BulletType ringType,
                                          float ringStartAngle) {
        fireAimedWithRing(pool, bx, by, tx, ty, aimCount, aimSpread, aimSpeed,
                ringCount, ringSpeed, diff, aimType, ringType, ringStartAngle, 1f, 1f, -1, -1, 0f);
    }

    public static void fireAimedWithRing(BulletPool pool, float bx, float by,
                                          float tx, float ty,
                                          int aimCount, float aimSpread, float aimSpeed,
                                          int ringCount, float ringSpeed,
                                          DifficultyConfig diff,
                                          BulletType aimType, BulletType ringType,
                                          float ringStartAngle,
                                          float visScale, float hitScale) {
        fireAimedWithRing(pool, bx, by, tx, ty, aimCount, aimSpread, aimSpeed,
                ringCount, ringSpeed, diff, aimType, ringType, ringStartAngle, visScale, hitScale,
                -1, -1, 0f);
    }

    public static void fireAimedWithRing(BulletPool pool, float bx, float by,
                                          float tx, float ty,
                                          int aimCount, float aimSpread, float aimSpeed,
                                          int ringCount, float ringSpeed,
                                          DifficultyConfig diff,
                                          BulletType aimType, BulletType ringType,
                                          float ringStartAngle,
                                          float visScale, float hitScale,
                                          int aimLifetimeTicks, int ringLifetimeTicks,
                                          float angVelRadPerTick) {
        int aimLife = lifeOrDefault(aimLifetimeTicks, DEFAULT_LIFE_AIMED);
        int ringLife = lifeOrDefault(ringLifetimeTicks, DEFAULT_LIFE_RING);
        fireAimed(pool, bx, by, tx, ty, aimCount, aimSpread, aimSpeed, diff, aimType, visScale, hitScale,
                aimLife, angVelRadPerTick);
        fireRing(pool, bx, by, ringCount, ringSpeed, diff, ringType, visScale, hitScale,
                ringLife, angVelRadPerTick, ringStartAngle);
    }

    // ---------------------------------------------------------------- laser beam

    public static void fireLaserBeam(BulletPool pool, float bx, float by,
                                      float tx, float ty,
                                      int count, float speed,
                                      DifficultyConfig diff, BulletType type) {
        fireLaserBeam(pool, bx, by, tx, ty, count, speed, diff, type, 1f, 1f, -1, 0f);
    }

    public static void fireLaserBeam(BulletPool pool, float bx, float by,
                                      float tx, float ty,
                                      int count, float speed,
                                      DifficultyConfig diff, BulletType type,
                                      float visScale, float hitScale) {
        fireLaserBeam(pool, bx, by, tx, ty, count, speed, diff, type, visScale, hitScale, -1, 0f);
    }

    public static void fireLaserBeam(BulletPool pool, float bx, float by,
                                      float tx, float ty,
                                      int count, float speed,
                                      DifficultyConfig diff, BulletType type,
                                      float visScale, float hitScale, int lifetimeTicks,
                                      float angVelRadPerTick) {
        fireLaserBeam(pool, bx, by, tx, ty, count, speed, diff, type, visScale, hitScale,
                lifetimeTicks, angVelRadPerTick, DEFAULT_LASER_BEAM_SPREAD);
    }

    /**
     * {@code LASER_BEAM}: rapid tight fan toward the aim point (not LaserPool geometry).
     *
     * @param spreadRad radians between adjacent bullets; TH needle stakes often ~0.03.
     */
    public static void fireLaserBeam(BulletPool pool, float bx, float by,
                                      float tx, float ty,
                                      int count, float speed,
                                      DifficultyConfig diff, BulletType type,
                                      float visScale, float hitScale, int lifetimeTicks,
                                      float angVelRadPerTick, float spreadRad) {
        float s = spreadRad >= 0f ? spreadRad : DEFAULT_LASER_BEAM_SPREAD;
        fireAimed(pool, bx, by, tx, ty, count, s, speed, diff, type, visScale, hitScale,
                lifetimeTicks, angVelRadPerTick);
    }
}
