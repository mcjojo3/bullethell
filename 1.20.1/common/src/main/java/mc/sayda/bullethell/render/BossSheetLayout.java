package mc.sayda.bullethell.render;

/**
 * Per-boss sprite sheet layout for arena / indicator / challenge portrait blits.
 *
 * <p>Default: 256×256 texture, 4×4 grid of 64×64 cells — only the top row is used
 * for the four-frame idle loop.
 *
 * <p>Sakuya: 256×255 texture, 4 columns × 3 rows of 64×85 cells — idle uses row 0 only.
 */
public final class BossSheetLayout {

    public final int cellW;
    public final int cellH;
    public final int texW;
    public final int texH;

    private BossSheetLayout(int cellW, int cellH, int texW, int texH) {
        this.cellW = cellW;
        this.cellH = cellH;
        this.texW = texW;
        this.texH = texH;
    }

    public static BossSheetLayout forBoss(String bossId) {
        if (bossId != null && "sakuya_boss".equals(bossId)) {
            return new BossSheetLayout(64, 85, 256, 255);
        }
        return new BossSheetLayout(64, 64, 256, 256);
    }

    /** U offset for frame index 0–3 on the idle row. */
    public float uForFrame(int frame) {
        return (frame & 3) * (float) cellW;
    }

    /** V offset for idle row (always 0). */
    public float idleRowV() {
        return 0f;
    }

    /**
     * Destination height when the on-screen width is {@code destW}, preserving aspect
     * ratio of one cell.
     */
    public int destHeightForWidth(int destW) {
        return Math.max(1, destW * cellH / cellW);
    }
}
