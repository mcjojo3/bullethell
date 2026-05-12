package mc.sayda.bullethell.mixin;

import mc.sayda.bullethell.entity.BHAttributes;
import net.minecraft.server.Bootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Bootstrap.class)
public class BootstrapMixin {

    @Inject(method = "bootStrap", at = @At("RETURN"))
    private static void bullethell$bootStrap(CallbackInfo ci) {
        // Fabric: DefaultAttributes caches Player's attribute supplier after Bootstrap
        // returns, so we must commit the DeferredRegister here — before that cache is
        // built — so MixinPlayerBHAttributes can successfully call .get() on EXTRA_LIVES
        // and EXTRA_BOMBS. The initialized guard in BHAttributes.register() makes the
        // subsequent call from Bullethell.init() a no-op.
        BHAttributes.register();
    }
}
