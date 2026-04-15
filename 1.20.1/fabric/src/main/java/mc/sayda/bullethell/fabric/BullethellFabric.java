package mc.sayda.bullethell.fabric;

import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.entity.BHEntities;
import mc.sayda.bullethell.entity.BHNpc;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public class BullethellFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Bullethell.init();
        FabricDefaultAttributeRegistry.register(BHEntities.MARISA_NPC.get(), BHNpc.createAttributes());
    }
}
