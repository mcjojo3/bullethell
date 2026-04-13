package mc.sayda.bullethell.forge.client;

import mc.sayda.bullethell.forge.sound.BHSounds;
import mc.sayda.bullethell.forge.sound.OggMetaReader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;

/**
 * Client-side music controller for the bullet hell arena.
 *
 * Responsibilities:
 *  - Play the correct track (from boss JSON) when the arena starts or the
 *    phase changes.
 *  - Gracefully stop playback when the arena ends.
 *  - Prevent vanilla ambient music from overlapping by stopping it at arena
 *    start (the MixinMusicManager keeps it suppressed for the session).
 *
 * Usage:
 *   Call {@link #tick(ClientArenaState)} once per client tick from
 *   {@code BulletHellClientEvents.onClientTick}.  It is safe to call before
 *   the sound engine is ready - it checks {@link Minecraft#getSoundManager()}.
 */
public final class BHMusicManager {

    public static final BHMusicManager INSTANCE = new BHMusicManager();

    @Nullable
    private SimpleSoundInstance currentMusic   = null;
    @Nullable
    private String              currentTrackId = null;

    private boolean wasActive = false;

    // ---- Now-playing display state (read by BulletHellRenderer) ----
    /** Total ticks the now-playing banner is shown (fade-in + hold + fade-out). */
    public static final int NP_TOTAL_TICKS = 140; // 7 s at 20 tps
    /** Current tick for the now-playing animation; ≥ NP_TOTAL_TICKS means not shown. */
    public int    npTick   = NP_TOTAL_TICKS;
    public String npTitle  = "";
    public String npArtist = "";

    private BHMusicManager() {}

    // ---------------------------------------------------------------- public API

    /**
     * Called once per client tick. Compares the wanted track (from arena state)
     * against what is currently playing and switches if they differ.
     */
    public void tick(ClientArenaState state) {
        // Always advance the now-playing timer so it fades out even if arena ends mid-display
        if (npTick < NP_TOTAL_TICKS) npTick++;

        if (!state.active) {
            if (wasActive) {
                stopMusic();
            }
            wasActive = false;
            return;
        }

        // Arena just became active - silence vanilla background music first
        if (!wasActive) {
            Minecraft.getInstance().getMusicManager().stopPlaying();
        }
        wasActive = true;

        String wanted = state.currentMusicTrackId;
        if (wanted == null || wanted.isEmpty()) return;

        // Already playing the right track
        if (wanted.equals(currentTrackId)) {
            // Restart if it stopped on its own (e.g. reached end before looping kicked in)
            if (currentMusic != null
                    && !Minecraft.getInstance().getSoundManager().isActive(currentMusic)) {
                currentMusic   = null;
                currentTrackId = null;
            } else {
                return;
            }
        }

        // Switch tracks
        stopMusic();
        SoundEvent evt = BHSounds.get(wanted);
        if (evt == null) {
            // Unknown track ID - log once and move on
            System.err.println("[BulletHell] Unknown music track: \"" + wanted
                    + "\". Check sounds.json and BHSounds.java.");
            return;
        }

        // Trigger now-playing banner — read OGG Vorbis comment metadata
        OggMetaReader.TrackMeta meta = OggMetaReader.read(wanted);
        npTitle  = meta.title().isEmpty()  ? wanted : meta.title();
        npArtist = meta.artist();
        npTick   = 0;

        currentTrackId = wanted;
        currentMusic   = new SimpleSoundInstance(
                evt.getLocation(),
                SoundSource.MUSIC,
                1.0f,           // volume
                1.0f,           // pitch
                SoundInstance.createUnseededRandom(),
                true,           // looping
                0,              // delay ticks
                SoundInstance.Attenuation.NONE,
                0.0, 0.0, 0.0,  // position - irrelevant for NONE attenuation
                true            // relative to listener (2-D, non-positional)
        );
        Minecraft.getInstance().getSoundManager().play(currentMusic);
    }

    /** Stop current arena music immediately (e.g. on arena end or screen close). */
    public void stopMusic() {
        if (currentMusic != null) {
            Minecraft.getInstance().getSoundManager().stop(currentMusic);
            currentMusic   = null;
            currentTrackId = null;
        }
    }

    /** The ID of the currently playing track, or {@code null} if nothing is playing. */
    @Nullable
    public String getCurrentTrackId() {
        return currentTrackId;
    }
}
