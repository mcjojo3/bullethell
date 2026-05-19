package mc.sayda.bullethell.arena;

import mc.sayda.bullethell.pattern.BulletType;

import java.util.Arrays;

/**
 * Kinematic pentagram "line" bullets: stacked {@code firePentagramStarOutline}-style stars, then eased
 * from the boss centre to ring positions. Supports optional <strong>dual colours</strong> per sample
 * (inner + outer ring radii) and <strong>multiple waves</strong> (new stacks at the boss while older
 * outlines keep drifting).
 */
public final class PentagramFormationRuntime {

    public static final int STAR_COUNT = 10;
    public static final float RING_RADIUS = 122f;
    public static final float STAR_RADIUS = 66f;

    private static final int MAX_POINTS = 1400;
    public static final int MAX_WAVES = 24;

    private static final int[] STAR_ORDER = { 0, 2, 4, 1, 3, 0 };
    private static final float[] STAR_UNIT_X;
    private static final float[] STAR_UNIT_Y;
    static {
        float step = (float) (Math.PI * 2.0 / 5.0);
        STAR_UNIT_X = new float[5];
        STAR_UNIT_Y = new float[5];
        for (int i = 0; i < 5; i++) {
            float a = step * i;
            STAR_UNIT_X[i] = (float) Math.cos(a);
            STAR_UNIT_Y[i] = (float) Math.sin(a);
        }
    }

    private final int[] slotsA = new int[MAX_POINTS];
    /** Second bullet per sample when dual; {@code -1} when single-colour mode. */
    private final int[] slotsB = new int[MAX_POINTS];
    private final byte[] layerOf = new byte[MAX_POINTS];
    private final byte[] waveOf = new byte[MAX_POINTS];
    private final float[] localX = new float[MAX_POINTS];
    private final float[] localY = new float[MAX_POINTS];
    /** Outward unit normal in star-local space (same as {@link mc.sayda.bullethell.pattern.PatternEngine} comb logic). */
    private final float[] outNlx = new float[MAX_POINTS];
    private final float[] outNly = new float[MAX_POINTS];
    /** Arm-relative position [0, 1]: 0 at edge start (V-tip vertex A), 1 at far end (vertex B). Used for arc speed gradient. */
    private final float[] armUOf = new float[MAX_POINTS];
    /** Edge tangent direction in star-local space (A→B normalised). Used for along-arm arc firing. */
    private final float[] armTx = new float[MAX_POINTS];
    private final float[] armTy = new float[MAX_POINTS];
    private int count;

    private int samplesPerEdge = 7;
    private int spawnLayerIndex;
    private int spawnEdge;
    private int spawnSample;

    private boolean dualMode;
    /** True after {@link #prelaunchArcStraight}: bullets are flying straight but still tracked for velocity redirect. */
    private boolean arcPrelaunched;

    /** Wave index currently receiving outline spawns. */
    private int spawnWave;
    /** Number of wave slots ever started ({@code beginStack} = wave 0, then {@link #beginNewWave}). */
    private int wavesStartedCount;
    /** Ritual tick when each wave finished stacking; {@code -1} until complete. */
    private final int[] waveStackDoneAt = new int[MAX_WAVES];

    public int getCount() {
        return count;
    }

    public boolean isDualMode() {
        return dualMode;
    }

    public void setDualMode(boolean dual) {
        this.dualMode = dual;
    }

    public boolean isArcPrelaunched() {
        return arcPrelaunched;
    }

    public int getSpawnWave() {
        return spawnWave;
    }

    public int getWavesStartedCount() {
        return wavesStartedCount;
    }

    public int getWaveStackDoneAt(int wave) {
        if (wave < 0 || wave >= MAX_WAVES)
            return -1;
        return waveStackDoneAt[wave];
    }

    public void clear(BulletPool pool) {
        if (pool != null) {
            for (int i = 0; i < count; i++) {
                deactivate(pool, slotsA[i]);
                deactivate(pool, slotsB[i]);
            }
        }
        count = 0;
        spawnLayerIndex = 0;
        spawnEdge = 0;
        spawnSample = 0;
        spawnWave = 0;
        wavesStartedCount = 0;
        arcPrelaunched = false;
        Arrays.fill(waveStackDoneAt, -1);
        Arrays.fill(slotsB, -1);
    }

    private static void deactivate(BulletPool pool, int s) {
        if (s >= 0 && pool != null)
            pool.deactivate(s);
    }

    /**
     * After edge combs: remove outline bullets but keep stack state "finished" so
     * {@link #spawnNextBatch} does not redraw pentagrams in the same spell.
     */
    public void deactivateFormationKeepStackDone(BulletPool pool) {
        if (pool != null) {
            for (int i = 0; i < count; i++) {
                deactivate(pool, slotsA[i]);
                deactivate(pool, slotsB[i]);
            }
        }
        count = 0;
        spawnLayerIndex = STAR_COUNT;
        spawnEdge = 0;
        spawnSample = 0;
    }

    /** First wave: call once when the ritual stack phase begins (after {@link #clear(BulletPool)}). */
    public void beginStack(int samplesPerEdgeIn) {
        this.samplesPerEdge = Math.max(2, Math.min(10, samplesPerEdgeIn));
        spawnLayerIndex = 0;
        spawnEdge = 0;
        spawnSample = 0;
        spawnWave = 0;
        wavesStartedCount = 1;
        arcPrelaunched = false;
        waveStackDoneAt[0] = -1;
    }

    /**
     * Start another stacked pentagram while previous outline bullets keep updating.
     * No-op if wave cap reached.
     */
    public void beginNewWave() {
        if (spawnWave + 1 >= MAX_WAVES)
            return;
        spawnWave++;
        if (spawnWave >= wavesStartedCount)
            wavesStartedCount = spawnWave + 1;
        spawnLayerIndex = 0;
        spawnEdge = 0;
        spawnSample = 0;
        waveStackDoneAt[spawnWave] = -1;
    }

    public boolean isStackComplete() {
        return spawnLayerIndex >= STAR_COUNT;
    }

    /** Whether the wave currently receiving spawns has finished its outline stack. */
    public boolean isCurrentSpawnWaveStackComplete() {
        if (spawnWave < 0 || spawnWave >= MAX_WAVES)
            return true;
        return waveStackDoneAt[spawnWave] >= 0;
    }

    public void markCurrentSpawnWaveComplete(int ritualTick) {
        if (spawnWave >= 0 && spawnWave < MAX_WAVES && waveStackDoneAt[spawnWave] < 0)
            waveStackDoneAt[spawnWave] = ritualTick;
    }

    /**
     * Spawn up to {@code maxBullets} outline samples for the current spawn wave.
     * When {@link #dualMode}, spawns {@code typeA} and {@code typeB} at the same local position.
     */
    public boolean spawnNextBatch(BulletPool pool, int maxBullets, float starRadius,
        BulletType typeA, BulletType typeB, float vis, float hit, int lifeTicks, float angVel) {
    int max = Math.max(1, maxBullets);
    int samples = samplesPerEdge;

    int spawned = 0;
    while (spawned < max && spawnLayerIndex < STAR_COUNT) {

        float ax = STAR_UNIT_X[STAR_ORDER[spawnEdge]] * starRadius;
        float ay = STAR_UNIT_Y[STAR_ORDER[spawnEdge]] * starRadius;
        float bx = STAR_UNIT_X[STAR_ORDER[spawnEdge + 1]] * starRadius;
        float by = STAR_UNIT_Y[STAR_ORDER[spawnEdge + 1]] * starRadius;

        float u = (spawnSample + 0.5f) / samples;
        float lx = ax + (bx - ax) * u;
        float ly = ay + (by - ay) * u;

        float dx = bx - ax;
        float dy = by - ay;
        float elen = (float) Math.sqrt(dx * dx + dy * dy);

        float nlx, nly, etx, ety;
        if (elen < 1e-4f) {
            nlx = 1f;
            nly = 0f;
            etx = 1f;
            ety = 0f;
        } else {
            float tx = dx / elen;
            float ty = dy / elen;

            float nx = -ty;
            float ny = tx;

            float mx = (ax + bx) * 0.5f;
            float my = (ay + by) * 0.5f;

            float dot = nx * mx + ny * my;
            if (dot < 0f) {
                nx = -nx;
                ny = -ny;
            }

            nlx = nx;
            nly = ny;
            etx = tx;
            ety = ty;
        }

        if (count >= MAX_POINTS)
            return isStackComplete();

        int slotA = pool.spawn(0f, 0f, 0f, 0f, typeA.getId(), lifeTicks, vis, hit, angVel);
        if (slotA < 0)
            return isStackComplete();

        int slotB = -1;
        if (dualMode && typeB != null) {
            slotB = pool.spawn(0f, 0f, 0f, 0f, typeB.getId(), lifeTicks, vis, hit, angVel);
            if (slotB < 0) {
                pool.deactivate(slotA);
                return isStackComplete();
            }
        }

        slotsA[count] = slotA;
        slotsB[count] = slotB;
        layerOf[count] = (byte) spawnLayerIndex;
        waveOf[count] = (byte) Math.min(spawnWave, 255);

        localX[count] = lx;
        localY[count] = ly;
        outNlx[count] = nlx;
        outNly[count] = nly;
        armUOf[count] = u;
        armTx[count] = etx;
        armTy[count] = ety;

        count++;
        spawned++;

        spawnSample++;
        if (spawnSample >= samples) {
            spawnSample = 0;
            spawnEdge++;

            if (spawnEdge >= 5) {
                spawnEdge = 0;
                spawnLayerIndex++;
            }
        }
    }
    return isStackComplete();
 }

    /**
     * World positions: each sample's star centre lies on a ring around the boss; inner/outer radius
     * per wave from {@code ringInnerPx[w]} / {@code ringOuterPx[w]} (single-colour uses inner only).
     * No-op when {@link #arcPrelaunched}: bullets are flying free and must not be snapped back.
     */
    public void syncPositions(BulletPool pool, float bossX, float bossY, float spin,
            float[] ringInnerPx, float[] ringOuterPx, int wavesCap) {
        if (arcPrelaunched) return;
        // Pre-compute cos/sin for each of the STAR_COUNT layers (40 trig calls total)
        // rather than recomputing per bullet (up to 1400 × 4 = 5600 calls per tick).
        float layerStep = (float) (Math.PI * 2.0 / STAR_COUNT);
        float[] caL     = new float[STAR_COUNT];
        float[] saL     = new float[STAR_COUNT];
        float[] cosAaL  = new float[STAR_COUNT];
        float[] sinAaL  = new float[STAR_COUNT];
        for (int l = 0; l < STAR_COUNT; l++) {
            float ang  = spin + l * layerStep;
            caL[l]    = (float) Math.cos(ang);
            saL[l]    = (float) Math.sin(ang);
            float aa   = l * layerStep + spin * 1.15f;
            cosAaL[l] = (float) Math.cos(aa);
            sinAaL[l] = (float) Math.sin(aa);
        }

        for (int i = 0; i < count; i++) {
            int sA = slotsA[i];
            if (!pool.isActive(sA))
                continue;
            int layer = layerOf[i] & 0xFF;
            int w = waveOf[i] & 0xFF;
            if (w >= wavesCap)
                continue;
            float rInner = w < ringInnerPx.length ? ringInnerPx[w] : 0f;
            float rOuter = (ringOuterPx != null && w < ringOuterPx.length) ? ringOuterPx[w] : rInner;

            float lx = localX[i];
            float ly = localY[i];
            float ca = caL[layer];
            float sa = saL[layer];
            float rx = lx * ca - ly * sa;
            float ry = lx * sa + ly * ca;

            float scxIn = bossX + cosAaL[layer] * rInner;
            float scyIn = bossY + sinAaL[layer] * rInner;
            pool.setPosition(sA, scxIn + rx, scyIn + ry);

            int sB = slotsB[i];
            if (sB >= 0 && pool.isActive(sB)) {
                float scxOut = bossX + cosAaL[layer] * rOuter;
                float scyOut = bossY + sinAaL[layer] * rOuter;
                pool.setPosition(sB, scxOut + rx, scyOut + ry);
            }
        }
    }

    /**
     * Dual-outline dissolution: give each existing outline bullet outward velocity along its edge normal
     * (no new comb spawns). Clears formation bookkeeping only; released slots keep flying in the pool.
     */
    public void launchDetachedOutward(BulletPool pool, float spin, float speed, int lifeTicks,
            float innerAngVel, float outerAngVel, int innerSplitCount, float innerSplitSpreadRad,
            float innerSplitSpeedMul, float outerArcSlope) {
        if (pool == null || count <= 0)
            return;
        int splitCount = Math.max(1, Math.min(5, innerSplitCount));
        float splitSpread = Math.max(0f, innerSplitSpreadRad);
        float splitSpeedMul = Math.max(0.05f, innerSplitSpeedMul);
        float layerStep = (float) (Math.PI * 2.0 / STAR_COUNT);
        for (int i = 0; i < count; i++) {
            int layer = layerOf[i] & 0xFF;
            float ang = spin + layer * layerStep;
            float ca = (float) Math.cos(ang);
            float sa = (float) Math.sin(ang);
            float nlx = outNlx[i];
            float nly = outNly[i];
            float nwx = nlx * ca - nly * sa;
            float nwy = nlx * sa + nly * ca;
            int sA = slotsA[i];
            if (sA >= 0 && pool.isActive(sA)) {
                float ax = pool.getX(sA);
                float ay = pool.getY(sA);
                int aType = pool.getType(sA);
                float aVis = pool.getVisScale(sA);
                float aHit = pool.getHitScale(sA);
                pool.setVx(sA, nwx * speed);
                pool.setVy(sA, nwy * speed);
                pool.setAngVel(sA, innerAngVel);
                pool.setRemainingLife(sA, lifeTicks);
                if (splitCount > 1 && splitSpread > 1e-5f) {
                    float center = (splitCount - 1) * 0.5f;
                    for (int si = 0; si < splitCount; si++) {
                        float offset = (si - center) * splitSpread;
                        if (Math.abs(offset) < 1e-6f)
                            continue;
                        float ca2 = (float) Math.cos(offset);
                        float sa2 = (float) Math.sin(offset);
                        float svx = (nwx * ca2 - nwy * sa2) * speed * splitSpeedMul;
                        float svy = (nwx * sa2 + nwy * ca2) * speed * splitSpeedMul;
                        pool.spawn(ax, ay, svx, svy, aType, lifeTicks, aVis, aHit, innerAngVel);
                    }
                }
            }
            if (dualMode) {
                int sB = slotsB[i];
                if (sB >= 0 && pool.isActive(sB)) {
                    float vxB, vyB;
                    if (outerArcSlope > 1e-6f) {
                        float tlx = armTx[i];
                        float tly = armTy[i];
                        float rotRad = (float) Math.toRadians(outerArcSlope * (1f - armUOf[i]));
                        float cr = (float) Math.cos(rotRad);
                        float sr = (float) Math.sin(rotRad);
                        float vlx = speed * (tlx * cr + tly * sr);
                        float vly = speed * (-tlx * sr + tly * cr);
                        vxB = vlx * ca - vly * sa;
                        vyB = vlx * sa + vly * ca;
                    } else {
                        vxB = nwx * speed;
                        vyB = nwy * speed;
                    }
                    pool.setVx(sB, vxB);
                    pool.setVy(sB, vyB);
                    pool.setAngVel(sB, outerAngVel);
                    pool.setRemainingLife(sB, lifeTicks);
                }
            }
        }
        detachReleasedClearState();
    }

    /**
     * Begins the pre-arc phase: bullets fire at full {@code baseSpeed} along the arm tangent
     * (zero sideways deviation). Stops kinematic sync. {@link #tickArcPrelaunch} gradually rotates
     * the direction each tick; call {@link #launchArcSingleColor} once the delay expires.
     */
    public void prelaunchArcStraight(BulletPool pool, float spin, float baseSpeed, int lifeTicks) {
        if (pool == null || count <= 0) return;
        float layerStep = (float) (Math.PI * 2.0 / STAR_COUNT);
        for (int i = 0; i < count; i++) {
            int sA = slotsA[i];
            if (sA < 0 || !pool.isActive(sA)) continue;
            int layer = layerOf[i] & 0xFF;
            float ang = spin + layer * layerStep;
            float ca = (float) Math.cos(ang);
            float sa = (float) Math.sin(ang);
            float tlx = armTx[i];
            float tly = armTy[i];
            pool.setVx(sA, (tlx * ca - tly * sa) * baseSpeed);
            pool.setVy(sA, (tlx * sa + tly * ca) * baseSpeed);
            pool.setAngVel(sA, 0f);
            pool.setRemainingLife(sA, lifeTicks);
        }
        arcPrelaunched = true;
    }

    /**
     * Per-tick direction ramp during the pre-arc phase. Speed stays constant at {@code baseSpeed};
     * the CW rotation from tangent grows as {@code curveDeg × (1-u) × (elapsed/total)²}
     * (quadratic ease-in: starts straight, sideways deviation accelerates). Call every tick
     * between prelaunch and the final arc redirect.
     */
    public void tickArcPrelaunch(BulletPool pool, float spin, float baseSpeed, float curveDeg,
            int delayTicks, int ticksElapsed) {
        if (!arcPrelaunched || pool == null || count <= 0) return;
        float frac = delayTicks > 0 ? Math.min(1f, (float) ticksElapsed / delayTicks) : 1f;
        float easedFrac = frac * frac;
        float layerStep = (float) (Math.PI * 2.0 / STAR_COUNT);
        for (int i = 0; i < count; i++) {
            int sA = slotsA[i];
            if (sA < 0 || !pool.isActive(sA)) continue;
            int layer = layerOf[i] & 0xFF;
            float ang = spin + layer * layerStep;
            float ca = (float) Math.cos(ang);
            float sa = (float) Math.sin(ang);
            float tlx = armTx[i];
            float tly = armTy[i];
            float rotRad = (float) Math.toRadians(curveDeg * (1f - armUOf[i]) * easedFrac);
            float cr = (float) Math.cos(rotRad);
            float sr = (float) Math.sin(rotRad);
            float vlx = baseSpeed * (tlx * cr + tly * sr);
            float vly = baseSpeed * (-tlx * sr + tly * cr);
            pool.setVx(sA, vlx * ca - vly * sa);
            pool.setVy(sA, vlx * sa + vly * ca);
        }
    }

    /**
     * Single-colour arc dissolution: each slotsA bullet fires at constant {@code baseSpeed},
     * with its direction rotated CW from the arm tangent by {@code curveDeg × (1-u)} degrees.
     * u=1 (FIRST / far end) = hinge, fires exactly along the tangent.
     * u=0 (V-tip end) = fires {@code curveDeg} degrees CW from tangent.
     * All bullets share the same speed so none overtake each other.
     * Released bullets keep flying; formation bookkeeping is cleared.
     */
    public void launchArcSingleColor(BulletPool pool, float spin, float baseSpeed, float curveDeg,
            int lifeTicks, float angVel) {
        if (pool == null || count <= 0)
            return;
        float layerStep = (float) (Math.PI * 2.0 / STAR_COUNT);
        for (int i = 0; i < count; i++) {
            int sA = slotsA[i];
            if (sA < 0 || !pool.isActive(sA))
                continue;
            int layer = layerOf[i] & 0xFF;
            float ang = spin + layer * layerStep;
            float ca = (float) Math.cos(ang);
            float sa = (float) Math.sin(ang);
            float tlx = armTx[i];
            float tly = armTy[i];
            // Rotate arm tangent CW by curveDeg*(1-u) degrees
            float rotRad = (float) Math.toRadians(curveDeg * (1f - armUOf[i]));
            float cr = (float) Math.cos(rotRad);
            float sr = (float) Math.sin(rotRad);
            float vlx = baseSpeed * (tlx * cr + tly * sr);
            float vly = baseSpeed * (-tlx * sr + tly * cr);
            float nwx = vlx * ca - vly * sa;
            float nwy = vlx * sa + vly * ca;
            pool.setVx(sA, nwx);
            pool.setVy(sA, nwy);
            pool.setAngVel(sA, angVel);
            pool.setRemainingLife(sA, lifeTicks);
        }
        detachReleasedClearState();
    }

    /** Drop formation indices without deactivating bullets (after {@link #launchDetachedOutward}). */
    private void detachReleasedClearState() {
        for (int i = 0; i < count; i++) {
            slotsA[i] = -1;
            slotsB[i] = -1;
        }
        count = 0;
        spawnLayerIndex = STAR_COUNT;
        spawnEdge = 0;
        spawnSample = 0;
        arcPrelaunched = false;
    }
}
