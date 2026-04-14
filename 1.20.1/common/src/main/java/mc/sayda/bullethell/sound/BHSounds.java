package mc.sayda.bullethell.sound;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mc.sayda.bullethell.Bullethell;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * All bullet-hell music tracks registered as Forge SoundEvents.
 *
 * Each key matches exactly the entry in {@code assets/bullethell/sounds.json}.
 * Use {@link #get(String)} to resolve a track ID string (from boss JSON) to a
 * live {@link SoundEvent} at runtime.
 */
public final class BHSounds {

        public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Bullethell.MODID,
                        Registries.SOUND_EVENT);

        /**
         * Reverse lookup: track ID string → RegistryObject, populated at class-load
         * time.
         */
        private static final Map<String, RegistrySupplier<SoundEvent>> BY_ID = new HashMap<>();

        // ---------------------------------------------------------------- SFX (2)

        public static final RegistrySupplier<SoundEvent> ATTACK_CHARGE = reg("attack_charge");

        public static final RegistrySupplier<SoundEvent> MASTER_SPARK = reg("master_spark");

        // ---------------------------------------------------------------- tracks (20)

        public static final RegistrySupplier<SoundEvent> AT_THE_END_OF_SPRING = reg("at_the_end_of_spring");

        public static final RegistrySupplier<SoundEvent> BAMBOO_FOREST_OF_THE_FULL_MOON = reg(
                        "bamboo_forest_of_the_full_moon");

        public static final RegistrySupplier<SoundEvent> CRAZY_BACKUP_DANCERS = reg("crazy_backup_dancers");

        public static final RegistrySupplier<SoundEvent> ELECTRIC_HERITAGE = reg("electric_heritage");

        public static final RegistrySupplier<SoundEvent> FARAWAY_VOYAGE_OF_380000_KILOMETERS = reg(
                        "faraway_voyage_of_380000_kilometers");

        public static final RegistrySupplier<SoundEvent> HEARTFELT_FANCY = reg("heartfelt_fancy");

        public static final RegistrySupplier<SoundEvent> HEIAN_ALIEN = reg("heian_alien");

        public static final RegistrySupplier<SoundEvent> INTO_BACKDOOR = reg("into_backdoor");

        public static final RegistrySupplier<SoundEvent> INVISIBLE_FULL_MOON = reg("invisible_full_moon");

        public static final RegistrySupplier<SoundEvent> LOVE_COLORED_MASTER_SPARK = reg("love_colored_master_spark");

        public static final RegistrySupplier<SoundEvent> NOSTALGIC_BLOOD_OF_THE_EAST = reg(
                        "nostalgic_blood_of_the_east");

        public static final RegistrySupplier<SoundEvent> RETRIBUTION_FOR_THE_ETERNAL_NIGHT = reg(
                        "retribution_for_the_eternal_night");

        public static final RegistrySupplier<SoundEvent> RURAL_MAKAI_CITY_ESOTERIA = reg("rural_makai_city_esoteria");

        public static final RegistrySupplier<SoundEvent> SWIM_IN_A_CHERRY_BLOSSOM_COLORED_SEA = reg(
                        "swim_in_a_cherry_blossom_colored_sea");

        public static final RegistrySupplier<SoundEvent> THE_LONG_AWAITED_OMAGATOKI = reg("the_long_awaited_omagatoki");

        public static final RegistrySupplier<SoundEvent> THE_PRINCESS_THAT_SLAYS_DRAGON_KINGS = reg(
                        "the_princess_that_slays_dragon_kings");

        public static final RegistrySupplier<SoundEvent> THE_SHINING_NEEDLE_CASTLE = reg(
                        "the_shining_needle_castle_sinking_in_the_air");

        public static final RegistrySupplier<SoundEvent> THE_SHINING_NEEDLE_CASTLE_REREMIX = reg(
                        "the_shining_needle_castle_sinking_in_the_air_reremix");

        public static final RegistrySupplier<SoundEvent> THE_YORIMASHI = reg(
                        "the_yorimashi_sits_between_dream_and_reality");

        public static final RegistrySupplier<SoundEvent> WALKING_THE_STREETS_OF_A_FORMER_HELL = reg(
                        "walking_the_streets_of_a_former_hell");

        // ---------------------------------------------------------------- helpers

        private BHSounds() {
        }

        private static RegistrySupplier<SoundEvent> reg(String id) {
                RegistrySupplier<SoundEvent> obj = SOUND_EVENTS.register(id,
                                () -> SoundEvent.createVariableRangeEvent(
                                                new ResourceLocation(Bullethell.MODID, id)));
                BY_ID.put(id, obj);
                return obj;
        }

        /**
         * Resolve a track ID string (as written in boss JSON) to a live SoundEvent.
         *
         * @param id track name, e.g. {@code "love_colored_master_spark"}
         * @return the SoundEvent, or {@code null} if the ID is unknown or the
         *         registry hasn't been populated yet
         */
        public static SoundEvent get(String id) {
                if (id == null || id.isEmpty())
                        return null;
                RegistrySupplier<SoundEvent> obj = BY_ID.get(id);
                return (obj != null && obj.isPresent()) ? obj.get() : null;
        }

        public static void register() {
                SOUND_EVENTS.register();
        }
}
