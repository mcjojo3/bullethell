package mc.sayda.bullethell.entity.ai;

import mc.sayda.bullethell.boss.NpcLoader;
import mc.sayda.bullethell.entity.BHNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Like {@link WaterAvoidingRandomStrollGoal}, but when {@link mc.sayda.bullethell.boss.NpcDefinition#seeksShade}
 * is set and the {@link BHNpc} is already shaded (under {@link NpcSeekShadeGoal#MIN_BRIGHT_SKY}), picked
 * destinations are never brighter than the current position — so they do not wander back into sunlight.
 */
public class NpcShadeAwareWaterAvoidingRandomStrollGoal extends WaterAvoidingRandomStrollGoal {

    private static final int BRIGHT_SKY_LINE = NpcSeekShadeGoal.MIN_BRIGHT_SKY;
    private static final int SHADE_STROLL_TRIES = 16;

    private final BHNpc npc;

    public NpcShadeAwareWaterAvoidingRandomStrollGoal(BHNpc npc, double speed) {
        super(npc, speed);
        this.npc = npc;
    }

    @Override
    protected Vec3 getPosition() {
        if (!NpcLoader.load(npc.getNpcId()).seeksShade || !npc.level().isDay()) {
            return super.getPosition();
        }
        int currentSky = npc.level().getBrightness(LightLayer.SKY, npc.blockPosition());
        if (currentSky >= BRIGHT_SKY_LINE) {
            return super.getPosition();
        }
        for (int i = 0; i < SHADE_STROLL_TRIES; i++) {
            Vec3 pos = super.getPosition();
            if (pos == null) {
                return null;
            }
            int targetSky = npc.level().getBrightness(LightLayer.SKY, BlockPos.containing(pos));
            if (targetSky <= currentSky) {
                return pos;
            }
        }
        return null;
    }
}
