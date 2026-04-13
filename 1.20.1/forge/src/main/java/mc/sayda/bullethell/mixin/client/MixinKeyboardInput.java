package mc.sayda.bullethell.mixin.client;

import mc.sayda.bullethell.forge.client.ClientArenaState;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses all Minecraft movement input while the bullet hell arena is
 * active.
 * Injected after KeyboardInput.tick() sets its fields so we cleanly zero them
 * out.
 *
 * This prevents the player from walking, jumping, or sneaking in the Minecraft
 * world
 * while they are playing the arena. WASD / arrow keys are handled separately by
 * BulletHellClientEvents, which sends PlayerPos2DPacket to the server.
 */
@Mixin(KeyboardInput.class)
public class MixinKeyboardInput {

    @Inject(method = "tick", at = @At("RETURN"))
    private void bh_suppressDuringArena(boolean pSneaking, float pMovementMultiplier,
            CallbackInfo ci) {
        if (!ClientArenaState.INSTANCE.active)
            return;

        Input self = (Input) (Object) this;
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
