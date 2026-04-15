package mc.sayda.bullethell.mixin;

import mc.sayda.bullethell.entity.BHAttributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayerBHAttributes {

    @Inject(method = "createAttributes", at = @At("RETURN"))
    private static void bullethell$addBHAttributes(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
        cir.getReturnValue()
                .add(BHAttributes.EXTRA_LIVES.get(), 0.0)
                .add(BHAttributes.EXTRA_BOMBS.get(), 0.0);
    }
}
