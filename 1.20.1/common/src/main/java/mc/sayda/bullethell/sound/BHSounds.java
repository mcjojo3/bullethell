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
 * Bullet-hell music and SFX registered as {@link SoundEvent}s.
 *
 * Music IDs match {@code assets/bullethell/sounds.json} and boss JSON
 * {@code "music"} fields. Current set is TH06–TH08 (Artifex) plus SFX.
 */
public final class BHSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Bullethell.MODID,
            Registries.SOUND_EVENT);

    private static final Map<String, RegistrySupplier<SoundEvent>> BY_ID = new HashMap<>();

    // ---------------------------------------------------------------- SFX

    public static final RegistrySupplier<SoundEvent> ATTACK_CHARGE = reg("attack_charge");

    public static final RegistrySupplier<SoundEvent> MASTER_SPARK = reg("master_spark");

    /**
     * Touhou 6–8 themes (Artifex), roughly game order — see {@code sounds.json}.
     */
    private static final String[] MUSIC_TRACK_IDS = {
            "a_dream_more_scarlet_than_red",
            "a_soul_as_scarlet_as_a_ground_cherry",
            "apparitions_stalk_the_night",
            "the_young_descendant_of_tepes",
            "beloved_tomboyish_girl",
            "beloved_tomboyish_girl_2",
            "lunate_elf",
            "shanghai_scarlet_teahouse_chinese_tea",
            "voile_the_magic_library",
            "locked_girl_the_girls_sealed_room",
            "lunar_clock_luna_dial",
            "lunar_clock_luna_dial_2",
            "the_maid_and_the_pocket_watch_of_blood",
            "the_centennial_festival_for_magical_girls",
            "an_eternity_more_transient_than_scarlet",
            "shanghai_alice_of_meiji_17",
            "septette_for_the_dead_princess",
            "septette_for_the_dead_princess_2",
            "u_n_owen_was_her",
            "u_n_owen_was_her_2",
            "scarlet_tower_eastern_dream",
            "ghostly_dream_snow_or_cherry_petal",
            "eastern_ghostly_dream_ancient_temple",
            "crystallized_silver",
            "the_fantastic_tales_from_tohno",
            "diao_ye_zong_withered_leaf",
            "diao_ye_zong_withered_leaf_2",
            "doll_judgement_the_girl_who_played_with_peoples_shapes",
            "the_doll_maker_of_bucuresti",
            "the_capital_city_of_flowers_in_the_sky",
            "dream_of_a_spring_breeze",
            "ultimate_truth",
            "bloom_nobly_ink_black_cherry_blossom_border_of_life",
            "border_of_life",
            "hiroari_shoots_a_strange_bird_till_when",
            "phantom_band_phantom_ensemble",
            "a_maidens_illusionary_funeral_necro_fantasy",
            "necrofantasia",
            "spiritual_domination",
            "spiritual_domination_who_done_it",
            "paradise_deep_mountain",
            "sakura_sakura_japanize_dream",
            "eternal_night_vignette_eastern_night",
            "illusionary_night_ghostly_eyes",
            "song_of_the_night_sparrow_night_bird",
            "wriggling_autumn_moon_mooned_insect",
            "wriggling_autumn_moon_mooned_insect_2",
            "eastern_youkai_beauty",
            "plain_asia",
            "plain_asia_2",
            "retribution_for_the_eternal_night_imperishable_night",
            "maidens_capriccio_dream_battle",
            "love_coloured_master_spark",
            "love_coloured_master_spark_2",
            "cinderella_cage_kagome_kagome",
            "lunatic_eyes_invisible_full_moon_remade",
            "lunatic_eyes_invisible_full_moon_2023",
            "voyage_1969",
            "voyage_1969_2",
            "extend_ash_person_of_hourai",
            "extend_ash_person_of_hourai_2",
            "gensokyo_millennium_history_of_the_moon",
            "gensokyo_millennium_history_of_the_moon_2",
            "flight_of_the_bamboo_cutter_lunatic_princess",
            "flight_of_the_bamboo_cutter_lunatic_princess_2",
            "deaf_to_all_but_the_song",
            "evening_primrose",
            "eternal_dream_mystical_maple",
            "nostalgic_blood_of_the_east_old_world",
            "nostalgic_blood_of_the_east_old_world_2",
            "reach_for_the_moon_immortal_smoke",
            "reach_for_the_moon_immortal_smoke_2",
            "voyage_1970",
            "voyage_1970_2021",
    };

    static {
        for (String id : MUSIC_TRACK_IDS)
            reg(id);
    }

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
     * @param id track name, e.g. {@code "love_coloured_master_spark"}
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
