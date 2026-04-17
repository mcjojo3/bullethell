package mc.sayda.bullethell.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

import java.util.function.Supplier;

/**
 * Client-side UI / gameplay SFX (short cues, not streaming music).
 */
@Environment(EnvType.CLIENT)
public final class BHSfx {

    private BHSfx() {
    }

    public static void play(SoundEvent sound) {
        if (sound == null)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null)
            return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f));
    }

    public static void play(Supplier<SoundEvent> supplier) {
        if (supplier == null)
            return;
        SoundEvent e = supplier.get();
        play(e);
    }

    public static void playSelect() {
        play(mc.sayda.bullethell.sound.BHSounds.SELECT);
    }

    public static void playBack() {
        play(mc.sayda.bullethell.sound.BHSounds.BACK);
    }
}
