package mc.sayda.bullethell.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * One character sprite's lean / idle animation.
 *
 * Every player on screen runs their own copy. The sheet's lean rows are driven by which
 * way that player is moving, so a single shared state would make every sprite mirror
 * whoever happens to be the local player.
 *
 * Sheet rows: 0 idle (8 frames), 1 leaning left, 2 leaning right.
 */
@Environment(EnvType.CLIENT)
public final class CharacterAnim {

    public int row = 0;
    public int leanFrame = 0;
    public int idleFrame = 0;
    private int idleTick = 0;

    /** @param dx this tick's horizontal movement: negative left, positive right, 0 idle */
    public void update(float dx) {
        if (dx < 0f) {
            lean(1);
        } else if (dx > 0f) {
            lean(2);
        } else if (leanFrame > 0) {
            // Ease back out of the lean before idling again.
            leanFrame--;
            idleTick = 0;
        } else {
            row = 0;
            if (++idleTick >= 3) {
                idleTick = 0;
                if (++idleFrame >= 8) idleFrame = 0;
            }
        }
    }

    private void lean(int leanRow) {
        if (row != leanRow) {
            leanFrame = 0;
            idleTick = 0;
        }
        row = leanRow;
        if (++idleTick >= 2) {
            idleTick = 0;
            leanFrame = (leanFrame == 0) ? 1 : (leanFrame < 7 ? leanFrame + 1 : 1);
        }
    }

    public void reset() {
        row = 0;
        leanFrame = 0;
        idleFrame = 0;
        idleTick = 0;
    }

    /** Which frame column of the current row to draw. */
    public int column() {
        return row == 0 ? idleFrame : leanFrame;
    }
}
