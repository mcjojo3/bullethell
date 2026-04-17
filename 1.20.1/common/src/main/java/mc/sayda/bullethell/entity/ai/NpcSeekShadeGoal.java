package mc.sayda.bullethell.entity.ai;

import mc.sayda.bullethell.boss.NpcLoader;
import mc.sayda.bullethell.entity.BHNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * During daytime, path toward nearby blocks with lower sky light (e.g. under trees or eaves).
 * Only active when the NPC definition has {@code seeksShade: true}.
 */
public class NpcSeekShadeGoal extends Goal {

    private static final int SAMPLE_ATTEMPTS = 48;
    private static final int HORIZONTAL_RANGE = 16;

    /** Sky light at or above this value counts as "bright" / direct sun for shade seeking. */
    public static final int MIN_BRIGHT_SKY = 10;

    private final BHNpc npc;
    private final double speed;
    private int cooldownTicks;

    public NpcSeekShadeGoal(BHNpc npc, double speed) {
        this.npc = npc;
        this.speed = speed;
    }

    @Override
    public boolean canUse() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return false;
        }
        if (!NpcLoader.load(npc.getNpcId()).seeksShade) {
            return false;
        }
        Level level = npc.level();
        if (!level.isDay()) {
            return false;
        }
        BlockPos pos = npc.blockPosition();
        return level.getBrightness(LightLayer.SKY, pos) >= MIN_BRIGHT_SKY;
    }

    @Override
    public void start() {
        BlockPos target = findShadierBlock(npc.level(), npc.blockPosition(), npc.getRandom());
        if (target != null) {
            npc.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
            cooldownTicks = 30 + npc.getRandom().nextInt(40);
        } else {
            cooldownTicks = 15 + npc.getRandom().nextInt(20);
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (!npc.level().isDay()) {
            return false;
        }
        return npc.getNavigation().isInProgress();
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
    }

    private static BlockPos findShadierBlock(Level level, BlockPos origin, RandomSource random) {
        int originSky = level.getBrightness(LightLayer.SKY, origin);
        BlockPos best = null;
        int bestSky = originSky;

        for (int i = 0; i < SAMPLE_ATTEMPTS; i++) {
            int dx = random.nextInt(HORIZONTAL_RANGE * 2 + 1) - HORIZONTAL_RANGE;
            int dz = random.nextInt(HORIZONTAL_RANGE * 2 + 1) - HORIZONTAL_RANGE;
            BlockPos.MutableBlockPos test = new BlockPos.MutableBlockPos();
            test.set(origin.getX() + dx, origin.getY(), origin.getZ() + dz);
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, test);
            if (!level.isLoaded(ground)) {
                continue;
            }
            int sky = level.getBrightness(LightLayer.SKY, ground);
            if (sky < bestSky) {
                bestSky = sky;
                best = ground.immutable();
            }
        }

        return best;
    }
}
