package mc.sayda.bullethell.forge;

import dev.architectury.platform.forge.EventBuses;
import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.client.BullethellClient;
import mc.sayda.bullethell.entity.BHEntities;
import mc.sayda.bullethell.entity.BHNpc;
import mc.sayda.bullethell.render.BHNpcRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Bullethell.MODID)
public class BullethellForge {
    public BullethellForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        EventBuses.registerModEventBus(Bullethell.MODID, modBus);
        Bullethell.init();

        modBus.addListener(BullethellForge::onEntityAttributes);

        if (FMLEnvironment.dist.isClient()) {
            BullethellClient.init();
            modBus.addListener(BullethellForge::onRegisterRenderers);
        }
    }

    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(BHEntities.MARISA_NPC.get(), BHNpc.createAttributes().build());
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BHEntities.MARISA_NPC.get(), BHNpcRenderer::new);
    }
}
