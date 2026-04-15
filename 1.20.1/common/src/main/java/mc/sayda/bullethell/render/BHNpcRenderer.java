package mc.sayda.bullethell.render;

import mc.sayda.bullethell.entity.BHNpc;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders {@link BHNpc} entities using the standard player model.
 *
 * The skin texture follows the convention:
 *   {@code assets/bullethell/textures/entities/<npcId stripped of "_npc">.png}
 * which must be a standard 64×64 (or 64×32 legacy) Minecraft player skin.
 */
@Environment(EnvType.CLIENT)
public class BHNpcRenderer extends HumanoidMobRenderer<BHNpc, PlayerModel<BHNpc>> {

    public BHNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(BHNpc entity) {
        String id = entity.getNpcId();
        String texId = id.endsWith("_npc") ? id.substring(0, id.length() - 4) : id;
        return new ResourceLocation("bullethell", "textures/entities/" + texId + ".png");
    }
}
