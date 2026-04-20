package mc.sayda.bullethell.item;

import mc.sayda.bullethell.sound.OggMetaReader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * Vanilla-style music disc: translated name {@code Music Disc}, gray tooltip
 * {@code ARTIST - TITLE} from {@link OggMetaReader} (Vorbis comments).
 */
public final class BHMusicDiscItem extends RecordItem {

    private final String trackId;

    public BHMusicDiscItem(String trackId, int comparatorOutput, SoundEvent soundEvent, Properties properties,
            int lengthSeconds) {
        super(comparatorOutput, soundEvent, properties, lengthSeconds);
        this.trackId = trackId;
    }

    public String trackId() {
        return trackId;
    }

    @Override
    public String getDescriptionId() {
        return "item.bullethell.music_disc";
    }

    /**
     * Subtitle line (vanilla-style gray line under the name): {@code author - title} when both exist.
     */
    @Override
    public MutableComponent getDisplayName() {
        return Component.literal(recordSubtitleLine());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(getDisplayName().withStyle(ChatFormatting.GRAY));
    }

    private String recordSubtitleLine() {
        OggMetaReader.TrackMeta meta = OggMetaReader.read(trackId);
        String artist = meta.artist().trim();
        String title = meta.title().trim();
        if (!artist.isEmpty() && !title.isEmpty())
            return artist + " - " + title;
        if (!title.isEmpty())
            return title;
        if (!artist.isEmpty())
            return artist;
        return fallbackTitle(trackId);
    }

    public static String fallbackTitle(String trackId) {
        String[] parts = trackId.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0)
                sb.append(' ');
            String p = parts[i];
            if (p.isEmpty())
                continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1)
                sb.append(p.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}
