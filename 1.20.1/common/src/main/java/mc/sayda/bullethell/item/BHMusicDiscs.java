package mc.sayda.bullethell.item;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mc.sayda.bullethell.sound.BHSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registers one {@link BHMusicDiscItem} per music track listed in {@link BHSounds}. Game-specific
 * visuals come from {@link BHMusicDiscMetadata} and {@code assets/bullethell/models/item/}.
 */
public final class BHMusicDiscs {

    private static final Map<String, RegistrySupplier<Item>> BY_TRACK_ID = new HashMap<>();

    private BHMusicDiscs() {
    }

    public static void registerInto(DeferredRegister<Item> items) {
        BY_TRACK_ID.clear();
        BHMusicDiscMetadata.ensureLoaded();
        String[] ids = BHSounds.getMusicTrackIds();
        for (int i = 0; i < ids.length; i++) {
            final String trackId = ids[i];
            final int comparator = (i % 15) + 1;
            RegistrySupplier<Item> supplier = items.register("music_disc_" + trackId,
                    () -> {
                        SoundEvent sound = Objects.requireNonNull(BHSounds.get(trackId),
                                "Missing SoundEvent for track: " + trackId);
                        return new BHMusicDiscItem(trackId, comparator, sound,
                                new Item.Properties().stacksTo(1).arch$tab(BHCreativeTabs.MUSIC_DISCS),
                                7200);
                    });
            BY_TRACK_ID.put(trackId, supplier);
        }
    }

    public static Map<String, RegistrySupplier<Item>> byTrackIdView() {
        return Collections.unmodifiableMap(BY_TRACK_ID);
    }

    @Nullable
    public static RegistrySupplier<Item> supplierForTrack(String trackId) {
        return BY_TRACK_ID.get(trackId);
    }

    public static ItemStack stackForTrack(String trackId) {
        RegistrySupplier<Item> s = BY_TRACK_ID.get(trackId);
        if (s == null || !s.isPresent())
            return ItemStack.EMPTY;
        return new ItemStack(s.get());
    }

    /** First registered disc (for creative tab icon); empty if none. */
    public static ItemStack anyDiscStack() {
        String[] ids = BHSounds.getMusicTrackIds();
        if (ids.length == 0)
            return ItemStack.EMPTY;
        return stackForTrack(ids[0]);
    }
}
