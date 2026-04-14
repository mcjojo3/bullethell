package mc.sayda.bullethell.mixin.client;

import mc.sayda.bullethell.client.ClientArenaState;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla ambient / situational music while the bullet hell arena
 * is active so it cannot start a new track and overlap with the arena music.
 *
 * The arena's BHMusicManager calls {@code MusicManager.stopPlaying()} once
 * when the arena first becomes active; this mixin ensures vanilla music stays
 * silent for the entire duration of the fight.
 */
@Mixin(MusicManager.class)
public class MixinMusicManager {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void bh_suppressDuringArena(CallbackInfo ci) {
        if (ClientArenaState.INSTANCE.active) {
            ci.cancel();
        }
    }
}
