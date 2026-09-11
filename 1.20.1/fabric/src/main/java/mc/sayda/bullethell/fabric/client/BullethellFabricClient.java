package mc.sayda.bullethell.fabric.client;

import mc.sayda.bullethell.client.BullethellClient;
import mc.sayda.bullethell.entity.BHEntities;
import mc.sayda.bullethell.render.BHNpcRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

@Environment(EnvType.CLIENT)
public class BullethellFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BullethellClient.init();
        BHEntities.NPCS.values().forEach(npc -> EntityRendererRegistry.register(npc.get(), BHNpcRenderer::new));
    }
}
