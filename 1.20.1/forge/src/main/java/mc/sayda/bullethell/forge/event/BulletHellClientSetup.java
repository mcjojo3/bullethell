package mc.sayda.bullethell.forge.event;

import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.forge.render.BulletHellRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * MOD-bus client setup - runs during mod initialisation on the client only.
 * Registers the arena HUD overlay.
 */
@Mod.EventBusSubscriber(modid = Bullethell.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class BulletHellClientSetup {

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        // Drawn above all standard vanilla overlays (hotbar, crosshair, etc.)
        event.registerAboveAll("bullethell_arena", BulletHellRenderer.INSTANCE);
    }
}
