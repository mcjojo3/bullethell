package mc.sayda.bullethell.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * The character cut-in that plays on a bomb or a boss spell card.
 *
 * Motion is a fast entrance that decays into a slow drift, then fades out without ever
 * stopping - the portrait is still creeping when it disappears, which reads better than
 * settling to a halt.
 *
 * <pre>
 *   0.0s - 0.5s   slide in   (eased out, covers most of the travel)
 *   0.5s - 1.0s   slow drift (the remainder, near-linear and barely moving)
 *   1.0s - 1.5s   fade out   (drift continues, alpha falls to zero)
 * </pre>
 *
 * The server's bomb effect lands at the start of the fade, so the wipe happens under a
 * fully-arrived portrait rather than during its entrance.
 */
@Environment(EnvType.CLIENT)
public final class SplashState {

    public static final SplashState INSTANCE = new SplashState();

    /** 0.5s at 20 tps. */
    public static final int SLIDE_TICKS = 10;
    /** 0.5s. */
    public static final int DRIFT_TICKS = 10;
    /** 0.5s. */
    public static final int FADE_TICKS = 10;
    public static final int TOTAL_TICKS = SLIDE_TICKS + DRIFT_TICKS + FADE_TICKS;

    /** Fraction of the entrance travel completed by the end of the fast slide. */
    private static final float SLIDE_PORTION = 0.88f;

    private ResourceLocation texture;
    private boolean fromLeft = true;
    private int ticks = -1;

    private SplashState() {}

    // ---------------------------------------------------------------- control

    /** Player cut-in: enters from the left. */
    public void playFromLeft(String characterId) {
        play(characterId, true);
    }

    /**
     * Boss cut-in: enters from the right. Boss ids carry a {@code _boss} suffix, so
     * {@code cirno_boss.png} is preferred and {@code cirno.png} accepted as a fallback.
     */
    public void playFromRight(String bossId) {
        if (bossId == null || bossId.isBlank()) return;
        ResourceLocation rl = resolve(bossId);
        if (rl == null && bossId.endsWith("_boss")) {
            rl = resolve(bossId.substring(0, bossId.length() - "_boss".length()));
        }
        begin(rl, false);
    }

    private void play(String textureId, boolean fromLeft) {
        if (textureId == null || textureId.isBlank()) return;
        begin(resolve(textureId), fromLeft);
    }

    private void begin(ResourceLocation rl, boolean fromLeft) {
        // No art yet for most characters; skip rather than flash a missing-texture box.
        if (rl == null) return;
        this.texture = rl;
        this.fromLeft = fromLeft;
        this.ticks = 0;
    }

    /** The splash texture for {@code id}, or {@code null} when the art does not exist. */
    private static ResourceLocation resolve(String id) {
        ResourceLocation rl = new ResourceLocation("bullethell", "textures/splash/" + id + ".png");
        var mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) return null;
        return mc.getResourceManager().getResource(rl).isPresent() ? rl : null;
    }

    public void reset() {
        ticks = -1;
        texture = null;
    }

    /** Advances one client tick. */
    public void tick() {
        if (ticks < 0) return;
        if (++ticks >= TOTAL_TICKS) reset();
    }

    // ---------------------------------------------------------------- query

    public boolean isActive() {
        return ticks >= 0 && texture != null;
    }

    public boolean isFromLeft() {
        return fromLeft;
    }

    public ResourceLocation texture() {
        return texture;
    }

    /**
     * How far the portrait has travelled, {@code 0} fully off-screen to {@code 1} fully
     * arrived. Fast to {@link #SLIDE_PORTION}, then a slow crawl over the remainder.
     */
    public float progress(float partialTick) {
        float t = time(partialTick);
        if (t <= SLIDE_TICKS) {
            float p = t / SLIDE_TICKS;
            // Cubic ease-out: most of the distance is covered in the first few ticks.
            float eased = 1f - (1f - p) * (1f - p) * (1f - p);
            return eased * SLIDE_PORTION;
        }
        float p = Math.min(1f, (t - SLIDE_TICKS) / (float) (DRIFT_TICKS + FADE_TICKS));
        return SLIDE_PORTION + p * (1f - SLIDE_PORTION);
    }

    /** 1 until the fade begins, then falls to 0. */
    public float alpha(float partialTick) {
        float t = time(partialTick);
        int fadeStart = SLIDE_TICKS + DRIFT_TICKS;
        if (t <= fadeStart) return 1f;
        return Math.max(0f, 1f - (t - fadeStart) / FADE_TICKS);
    }

    private float time(float partialTick) {
        if (ticks < 0) return 0f;
        return Math.min(TOTAL_TICKS, ticks + partialTick);
    }
}
