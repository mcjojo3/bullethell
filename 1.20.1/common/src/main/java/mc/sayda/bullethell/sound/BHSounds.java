package mc.sayda.bullethell.sound;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mc.sayda.bullethell.Bullethell;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Bullet-hell music and SFX registered as {@link SoundEvent}s.
 *
 * Music IDs match {@code assets/bullethell/sounds.json} and boss JSON
 * {@code "music"} fields. Current set is TH06-TH10 (Artifex) plus SFX.
 */
public final class BHSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Bullethell.MODID,
            Registries.SOUND_EVENT);

    private static final Map<String, RegistrySupplier<SoundEvent>> BY_ID = new HashMap<>();

    // ---------------------------------------------------------------- SFX

    public static final RegistrySupplier<SoundEvent> ATTACK_CHARGE = reg("attack_charge");

    public static final RegistrySupplier<SoundEvent> MASTER_SPARK = reg("master_spark");

    public static final RegistrySupplier<SoundEvent> BACK = reg("back");

    public static final RegistrySupplier<SoundEvent> DEATH = reg("death");

    public static final RegistrySupplier<SoundEvent> KILL = reg("kill");

    public static final RegistrySupplier<SoundEvent> ONE_UP = reg("one_up");

    /** Point items and small P power pieces (not bomb / full power / 1-up). */
    public static final RegistrySupplier<SoundEvent> PICK_UP = reg("pick_up");

    public static final RegistrySupplier<SoundEvent> POWER_UP = reg("power_up");

    public static final RegistrySupplier<SoundEvent> POWER_UP_ALT = reg("power_up_alt");

    public static final RegistrySupplier<SoundEvent> SELECT = reg("select");

    public static final RegistrySupplier<SoundEvent> SHOOT = reg("shoot");

    /**
     * Touhou themes (Artifex), roughly game order - see {@code sounds.json}.
     */
    /** Touhou music track ids (boss JSON / jukebox); order is display order. */
    private static final String[] MUSIC_TRACK_IDS = {

            "a_dream_more_scarlet_than_red",
            "a_soul_as_scarlet_as_a_ground_cherry",
            "an_eternity_more_transient_than_scarlet",
            "apparitions_stalk_the_night",
            "beloved_tomboyish_girl",
            "locked_girl_the_girls_sealed_room",
            "lunar_clock_luna_dial",
            "lunate_elf",
            "scarlet_tower_eastern_dream",
            "septette_for_the_dead_princess",
            "shanghai_alice_of_meiji_17",
            "shanghai_scarlet_teahouse_chinese_tea",
            "the_centennial_festival_for_magical_girls",
            "the_maid_and_the_pocket_watch_of_blood",
            "the_young_descendant_of_tepes",
            "u_n_owen_was_her",
            "voile_the_magic_library",

            "a_maidens_illusionary_funeral_necro_fantasy",
            "bloom_nobly_ink_black_cherry_blossom_border_of_life",
            "border_of_life",
            "crystallized_silver",
            "diao_ye_zong_withered_leaf",
            "doll_judgement_the_girl_who_played_with_peoples_shapes",
            "dream_of_a_spring_breeze",
            "eastern_ghostly_dream_ancient_temple",
            "ghostly_dream_snow_or_cherry_petal",
            "hiroari_shoots_a_strange_bird_till_when",
            "necrofantasia",
            "paradise_deep_mountain",
            "phantom_band_phantom_ensemble",
            "sakura_sakura_japanize_dream",
            "spiritual_domination",
            "spiritual_domination_who_done_it",
            "the_capital_city_of_flowers_in_the_sky",
            "the_doll_maker_of_bucuresti",
            "the_fantastic_tales_from_tohno",
            "ultimate_truth",

            "cinderella_cage_kagome_kagome",
            "deaf_to_all_but_the_song",
            "eastern_youkai_beauty",
            "eternal_dream_mystical_maple",
            "eternal_night_vignette_eastern_night",
            "evening_primrose",
            "extend_ash_person_of_hourai",
            "flight_of_the_bamboo_cutter_lunatic_princess",
            "gensokyo_millennium_history_of_the_moon",
            "illusionary_night_ghostly_eyes",
            "love_coloured_master_spark",
            "lunatic_eyes_invisible_full_moon",
            "maidens_capriccio_dream_battle",
            "nostalgic_blood_of_the_east_old_world",
            "plain_asia",
            "reach_for_the_moon_immortal_smoke",
            "retribution_for_the_eternal_night_imperishable_night",
            "song_of_the_night_sparrow_night_bird",
            "voyage_1969",
            "voyage_1970",
            "wriggling_autumn_moon_mooned_insect",

            "adventure_of_the_lovestruck_tomboy",
            "flowering_night",
            "oriental_dark_flight",
            "spring_lane_colorful_path",
            "white_flag_of_usa_shrine",

            "a_god_that_misses_people_romantic_fall",
            "akutagawa_ryuunosukes_kappa_candid_friend",
            "because_princess_inada_is_scolding_me",
            "cemetery_of_onbashira_grave_of_being",
            "dark_side_of_fate",
            "faith_is_for_the_transient_people",
            "fall_of_fall_autumnal_waterfall",
            "native_faith",
            "players_score",
            "sealed_gods",
            "shrine_at_the_foot_of_the_mountain",
            "the_gensokyo_the_gods_loved",
            "the_gods_give_us_blessed_rain_sylphid_dream",
            "the_primal_scene_of_japan_the_girl_saw",
            "the_road_of_the_misfortune_god_dark_road",
            "the_venerable_ancient_battlefield_suwa_foughten_field",
            "the_youkai_mountain_mysterious_mountain",
            "tomorrow_will_be_special_yesterday_was_not",

            "a_flower_studded_sake_dish_on_mt_ooe",
            "awakening_of_the_earth_spirits",
            "corpse_voyage_be_of_good_cheer",
            "energy_daybreak_future_dream",
            "green_eyed_jealousy",
            "hartmanns_youkai_girl",
            "heartfelt_fancy",
            "hellfire_mantle",
            "last_remote",
            "lullaby_of_deserted_hell",
            "satori_maiden_3rd_eye",
            "solar_sect_of_mystic_wisdom_nuclear_fusion",
            "the_bridge_people_no_longer_cross",
            "the_dark_blowhole",
            "the_earth_spirits_homecoming",
            "the_sealed_away_youkai_lost_place",
            "walking_the_streets_of_a_former_hell",

    };

    static {
        for (String id : MUSIC_TRACK_IDS)
            reg(id);
    }

    private BHSounds() {
    }

    /** All registered music track ids (same order as the internal music list). */
    public static String[] getMusicTrackIds() {
        return MUSIC_TRACK_IDS.clone();
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

    /**
     * Resolve a sound for one-shot UI/gameplay playback from boss JSON
     * {@code activationSound}:
     * first {@link #get(String)} (mod-registered ids), then
     * {@link BuiltInRegistries#SOUND_EVENT}
     * for {@code namespace:path} or default namespace {@link Bullethell#MODID}.
     */
    public static SoundEvent resolveForActivationSfx(String raw) {
        if (raw == null)
            return null;
        String s = raw.trim();
        if (s.isEmpty())
            return null;
        SoundEvent fromMod = get(s);
        if (fromMod != null)
            return fromMod;
        ResourceLocation loc = ResourceLocation.tryParse(s);
        if (loc == null && !s.contains(":"))
            loc = new ResourceLocation(Bullethell.MODID, s);
        if (loc == null)
            return null;
        return BuiltInRegistries.SOUND_EVENT.get(loc);
    }

    public static void register() {
        SOUND_EVENTS.register();
    }
}
