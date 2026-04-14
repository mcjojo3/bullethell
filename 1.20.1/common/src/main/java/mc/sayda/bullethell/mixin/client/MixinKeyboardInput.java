package mc.sayda.bullethell.mixin.client;

import mc.sayda.bullethell.client.ClientArenaState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Consistently suppresses Minecraft movement input while the bullet hell arena is active.
 *
 * This version uses @At("TAIL") and direct state checks to be extremely safe, ensuring
 * that standard Minecraft movement (Sneak/Sprint) is only affected when a match is verified active.
 */
@Mixin(KeyboardInput.class)
public class MixinKeyboardInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void bh_suppressDuringArena(boolean isSneaking, float movementMultiplier, CallbackInfo ci) {
        // Direct reference to avoid potential classloading issues with shared state
        if (!mc.sayda.bullethell.client.ClientArenaState.INSTANCE.active) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        Input self = (Input) (Object) this;
        
        // Zero out standard Minecraft movement impulses
        self.forwardImpulse = 0f;
        self.leftImpulse = 0f;
        self.up = false;
        self.down = false;
        self.left = false;
        self.right = false;
        self.jumping = false;
        self.shiftKeyDown = false;
    }
}
