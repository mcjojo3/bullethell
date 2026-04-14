package mc.sayda.bullethell.client;

import mc.sayda.bullethell.sound.OggMetaReader;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Client-side music controller for the bullet hell arena.
 * Cross-platform: sound lookup is delegated to a platform-provided resolver
 * registered via {@link #setSoundProvider(Function)}.
 * Forge sets {@code BHSounds::get}; Fabric sets its own equivalent.
 */
@Environment(EnvType.CLIENT)
public final class BHMusicManager {

    public static final BHMusicManager INSTANCE = new BHMusicManager();

    /** Resolve a track-ID string to a live SoundEvent. Set by each platform on init. */
    private static Function<String, SoundEvent> soundProvider = id -> null;

    @Nullable private SimpleSoundInstance currentMusic = null;
    @Nullable private String currentTrackId = null;
    private boolean wasActive = false;

    // ---- Now-playing display state (read by BulletHellRenderer) ----
    public static final int NP_TOTAL_TICKS = 140;
    public int  npTick   = NP_TOTAL_TICKS;
    public String npTitle  = "";
    public String npArtist = "";

    private BHMusicManager() {}

    /** Call once from the platform's client init to wire up sound resolution. */
    public static void setSoundProvider(Function<String, SoundEvent> provider) {
        soundProvider = provider;
    }

    // ---------------------------------------------------------------- public API

    public void tick(ClientArenaState state) {
        if (npTick < NP_TOTAL_TICKS) npTick++;

        if (!state.active) {
            if (wasActive) stopMusic();
            wasActive = false;
            return;
        }

        if (!wasActive) Minecraft.getInstance().getMusicManager().stopPlaying();
        wasActive = true;

        String wanted = state.currentMusicTrackId;
        if (wanted == null || wanted.isEmpty()) return;

        if (wanted.equals(currentTrackId)) {
            if (currentMusic != null && !Minecraft.getInstance().getSoundManager().isActive(currentMusic)) {
                currentMusic = null; currentTrackId = null;
            } else {
                return;
            }
        }

        stopMusic();
        SoundEvent evt = soundProvider.apply(wanted);
        if (evt == null) {
            System.err.println("[BulletHell] Unknown music track: \"" + wanted + "\".");
            return;
        }

        OggMetaReader.TrackMeta meta = OggMetaReader.read(wanted);
        npTitle  = meta.title().isEmpty() ? wanted : meta.title();
        npArtist = meta.artist();
        npTick   = 0;

        currentTrackId = wanted;
        currentMusic   = new SimpleSoundInstance(
                evt.getLocation(), SoundSource.MUSIC,
                1.0f, 1.0f,
                SoundInstance.createUnseededRandom(),
                true, 0,
                SoundInstance.Attenuation.NONE,
                0.0, 0.0, 0.0, true);
        Minecraft.getInstance().getSoundManager().play(currentMusic);
    }

    public void stopMusic() {
        if (currentMusic != null) {
            Minecraft.getInstance().getSoundManager().stop(currentMusic);
            currentMusic = null;
            currentTrackId = null;
        }
    }

    @Nullable
    public String getCurrentTrackId() { return currentTrackId; }
}
