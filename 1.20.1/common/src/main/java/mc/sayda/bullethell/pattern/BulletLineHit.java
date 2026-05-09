package mc.sayda.bullethell.pattern;

/**
 * Geometry for line-segment enemy bullets (e.g. {@link BulletType#BLUE_LASER}).
 */
public final class BulletLineHit {

    private BulletLineHit() {
    }

    /** Squared distance from P to the finite segment A-B. */
    public static float distSqPointToSegment(float px, float py,
            float ax, float ay, float bx, float by) {
        float abx = bx - ax, aby = by - ay;
        float apx = px - ax, apy = py - ay;
        float abLenSq = abx * abx + aby * aby;
        if (abLenSq < 1e-10f) {
            float dx = px - ax, dy = py - ay;
            return dx * dx + dy * dy;
        }
        float t = (apx * abx + apy * aby) / abLenSq;
        t = Math.max(0f, Math.min(1f, t));
        float qx = ax + t * abx, qy = ay + t * aby;
        float dx = px - qx, dy = py - qy;
        return dx * dx + dy * dy;
    }

    /**
     * Squared distance from P to a short-laser segment centered at
     * {@code (cx, cy)}, extending
     * {@code halfLength} <em>along</em> {@code (vx, vy)} (beam shoots straight
     * outward; cross-section
     * is handled separately via thickness + player radii in
     * {@link mc.sayda.bullethell.arena.ArenaContext}).
     */
    public static float distSqToShortLaser(float px, float py,
            float cx, float cy, float vx, float vy, float halfLength) {
        float len = (float) Math.hypot(vx, vy);
        float ux, uy;
        if (len < 1e-5f) {
            ux = 0f;
            uy = 1f;
        } else {
            ux = vx / len;
            uy = vy / len;
        }
        float x1 = cx - ux * halfLength;
        float y1 = cy - uy * halfLength;
        float x2 = cx + ux * halfLength;
        float y2 = cy + uy * halfLength;
        return distSqPointToSegment(px, py, x1, y1, x2, y2);
    }
}
