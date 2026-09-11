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
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Bullethell.MODID)
public class BullethellForge {
    public BullethellForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,
                mc.sayda.bullethell.config.forge.ForgeBullethellConfig.COMMON_SPEC,
                "bullethell/bullethell-common.toml");
        EventBuses.registerModEventBus(Bullethell.MODID, modBus);
        mc.sayda.bullethell.config.forge.ForgeBullethellConfig.apply();
        Bullethell.init();

        modBus.addListener(BullethellForge::onEntityAttributes);

        if (FMLEnvironment.dist.isClient()) {
            BullethellClient.init();
            modBus.addListener(BullethellForge::onRegisterRenderers);
        }
    }

    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        BHEntities.NPCS.values().forEach(npc -> event.put(npc.get(), BHNpc.createAttributes().build()));
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        BHEntities.NPCS.values().forEach(npc -> event.registerEntityRenderer(npc.get(), BHNpcRenderer::new));
    }
}
