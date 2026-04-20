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

    private PatternEngine() {}

    /** Difficulty speed mult × tuners × global enemy bullet slowdown ({@link BullethellConfig#enemyBulletSpeedFactor}). */
    public static float enemySpeedScale(DifficultyConfig diff) {
        return BullethellConfig.enemyBulletSpeedFactor(diff);
    }

    private static float enemySpeed(DifficultyConfig diff) {
        return enemySpeedScale(diff);
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
        int life = lifeOrDefault(lifetimeTicks, BullethellConfig.PATTERN_DEFAULT_LIFE_RING.get());
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
        fireSpiral(pool, bx, by, angleOffset, arms, speed, diff, BulletType.DOT);
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
        int life = lifeOrDefault(lifetimeTicks, BullethellConfig.PATTERN_DEFAULT_LIFE_AIMED.get());
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
        int life = lifeOrDefault(lifetimeTicks, BullethellConfig.PATTERN_DEFAULT_LIFE_RING.get());
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
        int life = lifeOrDefault(lifetimeTicks, BullethellConfig.PATTERN_DEFAULT_LIFE_RING.get());
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
        int aimLife = lifeOrDefault(aimLifetimeTicks, BullethellConfig.PATTERN_DEFAULT_LIFE_AIMED.get());
        int ringLife = lifeOrDefault(ringLifetimeTicks, BullethellConfig.PATTERN_DEFAULT_LIFE_RING.get());
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
                lifetimeTicks, angVelRadPerTick, BullethellConfig.PATTERN_DEFAULT_LASER_BEAM_SPREAD_RAD.get());
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
        float s = spreadRad >= 0f ? spreadRad : BullethellConfig.PATTERN_DEFAULT_LASER_BEAM_SPREAD_RAD.get();
        fireAimed(pool, bx, by, tx, ty, count, s, speed, diff, type, visScale, hitScale,
                lifetimeTicks, angVelRadPerTick);
    }

    /**
     * Two concentric regular N-gons offset by half a step (MoF / Sanae "star ritual" lattice).
     * Outer ring uses {@code outerType}; inner uses {@code innerType}.
     */
    public static void firePentagramDouble(BulletPool pool, float bx, float by,
            int points, float speed, DifficultyConfig diff,
            BulletType outerType, BulletType innerType,
            float visScale, float hitScale, int lifetimeTicks,
            float angVelRadPerTick, float ringStartRad) {
        int n = points >= 3 ? points : 5;
        int life = lifeOrDefault(lifetimeTicks, BullethellConfig.PATTERN_DEFAULT_LIFE_RING.get());
        float step = (float) (Math.PI * 2.0 / n);
        float halfStep = step * 0.5f;
        float sp = speed * enemySpeed(diff);
        for (int i = 0; i < n; i++) {
            float angle = ringStartRad + step * i;
            float vx = (float) Math.cos(angle) * sp;
            float vy = (float) Math.sin(angle) * sp;
            pool.spawn(bx, by, vx, vy, outerType.getId(), life, visScale, hitScale, angVelRadPerTick);
        }
        for (int i = 0; i < n; i++) {
            float angle = ringStartRad + halfStep + step * i;
            float vx = (float) Math.cos(angle) * sp;
            float vy = (float) Math.sin(angle) * sp;
            pool.spawn(bx, by, vx, vy, innerType.getId(), life, visScale, hitScale, angVelRadPerTick);
        }
    }

    /**
     * True five-point star (pentagram): vertices on a circle, edges connect every second vertex.
     * Spawns stationary bullets along each edge (outline).
     */
    public static void firePentagramStarOutline(BulletPool pool, float cx, float cy,
            float radius, float rotationRad, int samplesPerEdge,
            BulletType type,
            float visScale, float hitScale, int lifetimeTicks, float angVelRadPerTick) {
        int samples = Math.max(1, samplesPerEdge);
        int life = lifeOrDefault(lifetimeTicks, BullethellConfig.PATTERN_DEFAULT_LIFE_RING.get());
        float[] px = new float[5];
        float[] py = new float[5];
        float step = (float) (Math.PI * 2.0 / 5.0);
        for (int i = 0; i < 5; i++) {
            float a = rotationRad + step * i;
            px[i] = cx + (float) Math.cos(a) * radius;
            py[i] = cy + (float) Math.sin(a) * radius;
        }
        int[] starOrder = { 0, 2, 4, 1, 3, 0 };
        for (int e = 0; e < 5; e++) {
            float ax = px[starOrder[e]];
            float ay = py[starOrder[e]];
            float bx = px[starOrder[e + 1]];
            float by = py[starOrder[e + 1]];
            for (int s = 0; s < samples; s++) {
                float u = (s + 0.5f) / samples;
                float x = ax + (bx - ax) * u;
                float y = ay + (by - ay) * u;
                pool.spawn(x, y, 0f, 0f, type.getId(), life, visScale, hitScale, angVelRadPerTick);
            }
        }
    }

    /**
     * Pentagram edges emit parallel rows perpendicular to each edge (TH "comb" / lattice).
     * Outward normal is chosen so streams move away from the local star center through each edge band.
     */
    public static void firePentagramStarEdgeStreams(BulletPool pool, float cx, float cy,
            float radius, float rotationRad, int samplesPerEdge, float speed,
            DifficultyConfig diff, BulletType type,
            float visScale, float hitScale, int lifetimeTicks, float angVelRadPerTick) {
        firePentagramStarEdgeStreams(pool, cx, cy, radius, rotationRad, samplesPerEdge, 1, 2.5f,
                speed, diff, type, visScale, hitScale, lifetimeTicks, angVelRadPerTick);
    }

    /**
     * Same as {@link #firePentagramStarEdgeStreams(BulletPool, float, float, float, float, int, float, DifficultyConfig, BulletType, float, float, int, float)}
     * but offsets multiple parallel lines along each edge (tangent direction) for a thicker comb band.
     *
     * @param parallelRows 1 = single line per edge sample; 3–5 matches TH-style wide combs
     * @param rowSpacing    world units between adjacent parallel rows along the edge tangent
     */
    public static void firePentagramStarEdgeStreams(BulletPool pool, float cx, float cy,
            float radius, float rotationRad, int samplesPerEdge,
            int parallelRows, float rowSpacing,
            float speed,
            DifficultyConfig diff, BulletType type,
            float visScale, float hitScale, int lifetimeTicks, float angVelRadPerTick) {
        int samples = Math.max(1, samplesPerEdge);
        int rows = Math.max(1, parallelRows);
        int life = lifeOrDefault(lifetimeTicks, BullethellConfig.PATTERN_DEFAULT_LIFE_RING.get());
        float sp = speed * enemySpeed(diff);
        float[] px = new float[5];
        float[] py = new float[5];
        float step = (float) (Math.PI * 2.0 / 5.0);
        for (int i = 0; i < 5; i++) {
            float a = rotationRad + step * i;
            px[i] = cx + (float) Math.cos(a) * radius;
            py[i] = cy + (float) Math.sin(a) * radius;
        }
        int[] starOrder = { 0, 2, 4, 1, 3, 0 };
        for (int e = 0; e < 5; e++) {
            float ax = px[starOrder[e]];
            float ay = py[starOrder[e]];
            float bx = px[starOrder[e + 1]];
            float by = py[starOrder[e + 1]];
            float dx = bx - ax;
            float dy = by - ay;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-4f)
                continue;
            float tx = dx / len;
            float ty = dy / len;
            float nx = -ty;
            float ny = tx;
            float mx = (ax + bx) * 0.5f;
            float my = (ay + by) * 0.5f;
            float dot = nx * (mx - cx) + ny * (my - cy);
            if (dot < 0f) {
                nx = -nx;
                ny = -ny;
            }
            for (int s = 0; s < samples; s++) {
                float u = (s + 0.5f) / samples;
                float bx0 = ax + dx * u;
                float by0 = ay + dy * u;
                float vx = nx * sp;
                float vy = ny * sp;
                for (int r = 0; r < rows; r++) {
                    float off = (-(rows - 1) * 0.5f + r) * rowSpacing;
                    float x = bx0 + tx * off;
                    float y = by0 + ty * off;
                    pool.spawn(x, y, vx, vy, type.getId(), life, visScale, hitScale, angVelRadPerTick);
                }
            }
        }
    }

    /**
     * Row of orbs in one line perpendicular to {@code flightRad}, with small per-bullet
     * {@code angVel} so the row opens into a slight C while flying along that direction.
     *
     * @param flightRad radians; 0 = +X, {@code PI*0.5} = +Y (down in screen space)
     * @param rowCount how many bullets in the row (minimum {@code 1})
     * @param rowSpacingScale multiply default spacing ({@code 1f} = legacy loose row; ~{@code 0.62f} = tight "comet")
     */
    public static void fireOrbCRowInDirection(BulletPool pool,
            float bx, float by, float flightRad,
            float speed, DifficultyConfig diff, BulletType type,
            float visScale, float hitScale, int lifetimeTicks,
            java.util.Random rng, float curvScale, int rowCount, float rowSpacingScale, float rowSpeedSlope) {
        int life = lifeOrDefault(lifetimeTicks, BullethellConfig.PATTERN_DEFAULT_LIFE_RING.get());
        float sp = speed * enemySpeed(diff);
        float base = flightRad;
        float ca = (float) Math.cos(base);
        float sa = (float) Math.sin(base);
        float perp = base + (float) (Math.PI * 0.5);
        float pc = (float) Math.cos(perp);
        float ps = (float) Math.sin(perp);
        float mul = Math.max(0.35f, rowSpacingScale);
        float rowSpacing = Math.max(1.15f, 2.4f * Math.max(0.85f, visScale) * mul);
        int count = Math.max(1, rowCount);
        float mid = (count - 1) * 0.5f;
        for (int i = 0; i < count; i++) {
            float k = i - mid;
            float ox = bx + pc * k * rowSpacing;
            float oy = by + ps * k * rowSpacing;
            float curv = curvScale * k * 0.00055f;
            if (rng != null)
                curv += (rng.nextFloat() - 0.5f) * 0.0002f * curvScale;
            float spMul = 1f + k * rowSpeedSlope;
            float shotSp = Math.max(0.05f, sp * spMul);
            pool.spawn(ox, oy, ca * shotSp, sa * shotSp, type.getId(), life, visScale, hitScale, curv);
        }
    }

    /**
     * Same as {@link #fireOrbCRowInDirection} with default row spacing (looser row).
     */
    public static void fireOrbCRowInDirection(BulletPool pool,
            float bx, float by, float flightRad,
            float speed, DifficultyConfig diff, BulletType type,
            float visScale, float hitScale, int lifetimeTicks,
            java.util.Random rng, float curvScale, int rowCount) {
        fireOrbCRowInDirection(pool, bx, by, flightRad, speed, diff, type, visScale, hitScale,
                lifetimeTicks, rng, curvScale, rowCount, 1f, 0f);
    }

    /**
     * Row of orbs (same {@code visScale} / {@code hitScale} as the pentagram outline), in one line
     * perpendicular to aim toward ({@code tx}, {@code ty}), with a small per-bullet {@code angVel}
     * so the row opens into a very slight C while flying at the target.
     *
     * @param rowCount how many bullets in the row (minimum {@code 1})
     */
    public static void fireOrbCRowToward(BulletPool pool,
            float bx, float by, float tx, float ty,
            float speed, DifficultyConfig diff, BulletType type,
            float visScale, float hitScale, int lifetimeTicks,
            java.util.Random rng, float curvScale, int rowCount) {
        float base = (float) Math.atan2(ty - by, tx - bx);
        fireOrbCRowInDirection(pool, bx, by, base, speed, diff, type, visScale, hitScale,
                lifetimeTicks, rng, curvScale, rowCount, 1f, 0f);
    }

    /**
     * Stacked "walls" from a single spawn: fan toward ({@code tx}, {@code ty}), multiple parallel
     * stacks perpendicular to each ray; each ray uses a different {@code angVel} so streams drift
     * into a shallow C while still roughly homing the fan at the target.
     */
    public static void fireCurvingWallFanFromPoint(BulletPool pool,
            float bx, float by, float tx, float ty,
            int rays, int stackDepth, float fanHalfWidthRad, float stackPerpOffset,
            float speed, DifficultyConfig diff, BulletType type,
            float visScale, float hitScale, int lifetimeTicks,
            java.util.Random rng, float angleJitterRad, float curvScale) {
        int life = lifeOrDefault(lifetimeTicks, BullethellConfig.PATTERN_DEFAULT_LIFE_RING.get());
        float sp = speed * enemySpeed(diff);
        float base = (float) Math.atan2(ty - by, tx - bx);
        int rMax = Math.max(1, rays);
        int dMax = Math.max(1, stackDepth);
        float midS = (dMax - 1) * 0.5f;
        float midR = (rMax - 1) * 0.5f;
        for (int r = 0; r < rMax; r++) {
            float u = rMax <= 1 ? 0f : (r / (float) (rMax - 1)) * 2f - 1f;
            float rayJ = (rng != null && angleJitterRad > 1e-6f)
                    ? (rng.nextFloat() - 0.5f) * 2f * angleJitterRad
                    : 0f;
            float ang = base + u * fanHalfWidthRad + rayJ;
            float ca = (float) Math.cos(ang);
            float sa = (float) Math.sin(ang);
            float perp = ang + (float) (Math.PI * 0.5);
            float pc = (float) Math.cos(perp);
            float ps = (float) Math.sin(perp);
            for (int s = 0; s < dMax; s++) {
                float sr = s - midS;
                float off = dMax <= 1 ? 0f : sr * stackPerpOffset;
                float ox = bx + pc * off;
                float oy = by + ps * off;
                float rr = r - midR;
                float curv = curvScale * (0.03f * u + sr * 0.024f + rr * 0.014f);
                if (rng != null)
                    curv += (rng.nextFloat() - 0.5f) * 0.032f * curvScale;
                pool.spawn(ox, oy, ca * sp, sa * sp, type.getId(), life, visScale, hitScale, curv);
            }
        }
    }
}
