package mc.sayda.bullethell.pattern;

import mc.sayda.bullethell.arena.BulletPool;
import mc.sayda.bullethell.arena.DifficultyConfig;

/**
 * Stateless shot factories.
 *
 * All methods receive an ArenaContext-owned BulletPool and a DifficultyConfig
 * so they scale cleanly across difficulties and work in any arena (splitscreen-safe).
 *
 * Each pattern has two overloads:
 *   - Full overload with explicit {@link BulletType} - used by the JSON-driven boss system.
 *   - Convenience overload with a hardcoded default type - retained for quick ad-hoc use.
 *
 * Adding a new pattern = adding a new static method here.
 */
public final class PatternEngine {

    private PatternEngine() {}

    // ---------------------------------------------------------------- spiral

    /**
     * Fires {@code arms} bullets in a rotating spoke pattern.
     * Call each tick with an ever-increasing {@code angleOffset} for smooth rotation.
     */
    public static void fireSpiral(BulletPool pool, float bx, float by,
                                   float angleOffset, int arms, float speed,
                                   DifficultyConfig diff, BulletType type) {
        float step = (float) (Math.PI * 2.0 / arms);
        for (int i = 0; i < arms; i++) {
            float angle = angleOffset + step * i;
            float vx = (float) Math.cos(angle) * speed * diff.speedMult;
            float vy = (float) Math.sin(angle) * speed * diff.speedMult;
            pool.spawn(bx, by, vx, vy, type.getId(), 200);
        }
    }

    /** Convenience overload - uses {@link BulletType#ORB}. */
    public static void fireSpiral(BulletPool pool, float bx, float by,
                                   float angleOffset, int arms, float speed,
                                   DifficultyConfig diff) {
        fireSpiral(pool, bx, by, angleOffset, arms, speed, diff, BulletType.ORB);
    }

    // ---------------------------------------------------------------- aimed

    /**
     * Fires {@code count} bullets spread in a fan aimed toward (tx, ty).
     * {@code spread} is the angle between adjacent shots in radians.
     */
    public static void fireAimed(BulletPool pool, float bx, float by,
                                  float tx, float ty,
                                  int count, float spread, float speed,
                                  DifficultyConfig diff, BulletType type) {
        float baseAngle  = (float) Math.atan2(ty - by, tx - bx);
        float halfSpread = spread * (count - 1) / 2f;
        for (int i = 0; i < count; i++) {
            float angle = baseAngle - halfSpread + spread * i;
            float vx = (float) Math.cos(angle) * speed * diff.speedMult;
            float vy = (float) Math.sin(angle) * speed * diff.speedMult;
            pool.spawn(bx, by, vx, vy, type.getId(), 220);
        }
    }

    /** Convenience overload - uses {@link BulletType#STAR}. */
    public static void fireAimed(BulletPool pool, float bx, float by,
                                  float tx, float ty,
                                  int count, float spread, float speed,
                                  DifficultyConfig diff) {
        fireAimed(pool, bx, by, tx, ty, count, spread, speed, diff, BulletType.STAR);
    }

    // ---------------------------------------------------------------- ring

    /** Uniform ring of bullets in all directions. */
    public static void fireRing(BulletPool pool, float bx, float by,
                                 int count, float speed,
                                 DifficultyConfig diff, BulletType type) {
        float step = (float) (Math.PI * 2.0 / count);
        for (int i = 0; i < count; i++) {
            float angle = step * i;
            float vx = (float) Math.cos(angle) * speed * diff.speedMult;
            float vy = (float) Math.sin(angle) * speed * diff.speedMult;
            pool.spawn(bx, by, vx, vy, type.getId(), 200);
        }
    }

    /** Convenience overload - uses {@link BulletType#RICE}. */
    public static void fireRing(BulletPool pool, float bx, float by,
                                 int count, float speed, DifficultyConfig diff) {
        fireRing(pool, bx, by, count, speed, diff, BulletType.RICE);
    }

    // ---------------------------------------------------------------- spread (downward fan)

    /** Convenience: fan of bullets aimed straight down. */
    public static void fireSpread(BulletPool pool, float bx, float by,
                                   int count, float speed,
                                   DifficultyConfig diff, BulletType type) {
        fireAimed(pool, bx, by, bx, by + 100f, count, 0.28f, speed, diff, type);
    }

    /** Convenience overload - uses {@link BulletType#STAR}. */
    public static void fireSpread(BulletPool pool, float bx, float by,
                                   int count, float speed, DifficultyConfig diff) {
        fireSpread(pool, bx, by, count, speed, diff, BulletType.STAR);
    }

    // ---------------------------------------------------------------- dense ring

    /**
     * Two interleaved rings offset by half a step - used for dense wall patterns.
     * The second ring uses the provided {@code altType}, or the same type if null.
     */
    public static void fireDenseRing(BulletPool pool, float bx, float by,
                                      int countPerRing, float speed,
                                      DifficultyConfig diff, BulletType type) {
        fireRing(pool, bx, by, countPerRing, speed, diff, type);
        float halfStep = (float) (Math.PI / countPerRing);
        float step     = (float) (Math.PI * 2.0 / countPerRing);
        // Second ring offset by half-step, slightly different type for visual contrast
        BulletType altType = (type == BulletType.BUBBLE) ? BulletType.RICE : BulletType.BUBBLE;
        for (int i = 0; i < countPerRing; i++) {
            float angle = halfStep + step * i;
            float vx = (float) Math.cos(angle) * speed * diff.speedMult;
            float vy = (float) Math.sin(angle) * speed * diff.speedMult;
            pool.spawn(bx, by, vx, vy, altType.getId(), 200);
        }
    }

    /** Convenience overload - uses {@link BulletType#BUBBLE} / {@link BulletType#RICE} interleaved. */
    public static void fireDenseRing(BulletPool pool, float bx, float by,
                                      int countPerRing, float speed, DifficultyConfig diff) {
        fireDenseRing(pool, bx, by, countPerRing, speed, diff, BulletType.BUBBLE);
    }

    // ---------------------------------------------------------------- ring with offset

    /**
     * Uniform ring starting at {@code startAngle} radians.
     * Pass a random angle each burst for TH6-style barrier fairies (RING pattern).
     */
    public static void fireRingOffset(BulletPool pool, float bx, float by,
                                       int count, float speed,
                                       DifficultyConfig diff, BulletType type,
                                       float startAngle) {
        float step = (float) (Math.PI * 2.0 / count);
        for (int i = 0; i < count; i++) {
            float angle = startAngle + step * i;
            float vx = (float) Math.cos(angle) * speed * diff.speedMult;
            float vy = (float) Math.sin(angle) * speed * diff.speedMult;
            pool.spawn(bx, by, vx, vy, type.getId(), 200);
        }
    }

    // ---------------------------------------------------------------- aimed fan + outer ring

    /**
     * Fires an aimed fan toward (tx, ty) PLUS a slower ring in all directions.
     * Used by large/anchor fairies (AIMED_RING pattern) — dual-threat TH-style.
     *
     * @param ringCount  number of ring bullets (independent of difficulty scaling)
     * @param ringSpeed  ring bullet speed (slower than aimed, typically 0.6×)
     */
    public static void fireAimedWithRing(BulletPool pool, float bx, float by,
                                          float tx, float ty,
                                          int aimCount, float aimSpread, float aimSpeed,
                                          int ringCount, float ringSpeed,
                                          DifficultyConfig diff,
                                          BulletType aimType, BulletType ringType,
                                          float ringStartAngle) {
        fireAimed(pool, bx, by, tx, ty, aimCount, aimSpread, aimSpeed, diff, aimType);
        fireRingOffset(pool, bx, by, ringCount, ringSpeed, diff, ringType, ringStartAngle);
    }

    // ---------------------------------------------------------------- laser beam

    /**
     * Fires a tight burst of {@code count} bullets aimed at (tx, ty) with a very narrow
     * spread (0.04 rad), simulating a laser beam. Call on a low cooldown (2–4 ticks) to
     * produce a continuous beam column.
     */
    public static void fireLaserBeam(BulletPool pool, float bx, float by,
                                      float tx, float ty,
                                      int count, float speed,
                                      DifficultyConfig diff, BulletType type) {
        fireAimed(pool, bx, by, tx, ty, count, 0.04f, speed, diff, type);
    }
}
