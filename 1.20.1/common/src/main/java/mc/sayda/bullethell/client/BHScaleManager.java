package mc.sayda.bullethell.client;

import net.minecraft.client.Minecraft;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Handles temporary GUI scale overrides for Bullethell screens and matches.
 * Effectively manages the saving, calculating, and reverting of the mc.options.guiScale.
 */
@Environment(EnvType.CLIENT)
public class BHScaleManager {

    private static int originalGuiScale = -1;
    private static boolean scaleOverridden = false;

    /**
     * Calculates and applies an ideal GUI scale for Bullethell content.
     * Capped at 4 to prevent excessively large UI on high-res monitors.
     */
    public static void applyIdealScale() {
        Minecraft mc = Minecraft.getInstance();
        
        // Save original scale if not already saved
        if (!scaleOverridden) {
            originalGuiScale = mc.options.guiScale().get();
        }

        int ideal = calculateIdealScale(mc);

        // Only apply if it's different from the current setting
        if (mc.options.guiScale().get() != ideal) {
            mc.options.guiScale().set(ideal);
            scaleOverridden = true;
            
            // CRITICAL: Minecraft needs a display resize to reflow the UI with the new scale
            mc.resizeDisplay();
        }
    }

    /**
     * Restores the player's original GUI scale settings.
     */
    public static void restoreOriginalScale() {
        if (!scaleOverridden) return;

        Minecraft mc = Minecraft.getInstance();
        if (originalGuiScale != -1) {
            mc.options.guiScale().set(originalGuiScale);
            mc.resizeDisplay();
        }
        
        scaleOverridden = false;
        originalGuiScale = -1;
    }

    private static int calculateIdealScale(Minecraft mc) {
        int h = mc.getWindow().getHeight();
        
        // Target a scale that keeps logical height around 300-450 units.
        if (h < 700) return 1;
        if (h < 1000) return 2;
        if (h < 1400) return 3; // 1080p sweet spot
        return 4; // 1440p and higher, capped at 4 per user feedback
    }

    public static boolean isOverridden() {
        return scaleOverridden;
    }
}
