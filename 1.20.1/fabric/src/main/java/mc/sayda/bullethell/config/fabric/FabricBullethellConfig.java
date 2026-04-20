package mc.sayda.bullethell.config.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import mc.sayda.bullethell.config.BullethellConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Fabric-side config: JSON under {@code config/bullethell/}, same keys/semantics as Forge TOML
 * (mirrors creraces {@code FabricConfig} + common {@link BullethellConfig} suppliers).
 */
public final class FabricBullethellConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("bullethell");

    private FabricBullethellConfig() {
    }

    public static void load() {
        File dir = CONFIG_DIR.toFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            LOGGER.warn("[Bullethell] Could not create config directory: {}", CONFIG_DIR);
        }
        CommonJson data = loadFile("bullethell-common.json", CommonJson.class);
        apply(data);
    }

    private static <T> T loadFile(String fileName, Class<T> clazz) {
        File file = CONFIG_DIR.resolve(fileName).toFile();
        T data;
        try {
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    data = GSON.fromJson(reader, clazz);
                    if (data == null) {
                        data = clazz.getDeclaredConstructor().newInstance();
                    }
                }
            } else {
                data = clazz.getDeclaredConstructor().newInstance();
                saveFile(fileName, data);
            }
        } catch (Exception e) {
            LOGGER.error("[Bullethell] Failed to load Fabric config: {}", fileName, e);
            try {
                data = clazz.getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return data;
    }

    private static void saveFile(String fileName, Object data) {
        File file = CONFIG_DIR.resolve(fileName).toFile();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.error("[Bullethell] Failed to save Fabric config: {}", fileName, e);
        }
    }

    static void apply(CommonJson root) {
        WaveTimingJson w = root.wave_timing != null ? root.wave_timing : new WaveTimingJson();
        BullethellConfig.WAVE_TIMING_EASY = () -> (float) w.easy;
        BullethellConfig.WAVE_TIMING_NORMAL = () -> (float) w.normal;
        BullethellConfig.WAVE_TIMING_HARD = () -> (float) w.hard;
        BullethellConfig.WAVE_TIMING_LUNATIC = () -> (float) w.lunatic;

        DifficultyTuningJson dt = root.difficulty_tuning != null ? root.difficulty_tuning : new DifficultyTuningJson();
        BullethellConfig.DIFFICULTY_SPEED_TUNER_EASY = () -> (float) dt.speed_tuner_easy;
        BullethellConfig.DIFFICULTY_SPEED_TUNER_NORMAL = () -> (float) dt.speed_tuner_normal;
        BullethellConfig.DIFFICULTY_SPEED_TUNER_HARD = () -> (float) dt.speed_tuner_hard;
        BullethellConfig.DIFFICULTY_SPEED_TUNER_LUNATIC = () -> (float) dt.speed_tuner_lunatic;
        BullethellConfig.DIFFICULTY_DENSITY_TUNER_EASY = () -> (float) dt.density_tuner_easy;
        BullethellConfig.DIFFICULTY_DENSITY_TUNER_NORMAL = () -> (float) dt.density_tuner_normal;
        BullethellConfig.DIFFICULTY_DENSITY_TUNER_HARD = () -> (float) dt.density_tuner_hard;
        BullethellConfig.DIFFICULTY_DENSITY_TUNER_LUNATIC = () -> (float) dt.density_tuner_lunatic;

        FairyEnemyAiJson fe = root.fairy_enemy_ai != null ? root.fairy_enemy_ai : new FairyEnemyAiJson();
        BullethellConfig.FAIRY_MIN_ATTACK_INTERVAL_TICKS = () -> fe.min_attack_interval_ticks;
        BullethellConfig.FAIRY_AIMED_BURST_CAP = () -> fe.aimed_burst_cap;
        BullethellConfig.FAIRY_AIMED_BURST_CAP_LUNATIC = () -> fe.aimed_burst_cap_lunatic;
        BullethellConfig.FAIRY_SPREAD_BURST_CAP = () -> fe.spread_burst_cap;
        BullethellConfig.FAIRY_SPREAD_BURST_CAP_LUNATIC = () -> fe.spread_burst_cap_lunatic;
        BullethellConfig.FAIRY_STREAM_COOLDOWN_DIVISOR = () -> fe.stream_cooldown_divisor;
        BullethellConfig.FAIRY_STREAM_COOLDOWN_MIN_TICKS = () -> fe.stream_cooldown_min_ticks;
        BullethellConfig.FAIRY_BULLET_COUNT_MULT = () -> (float) fe.bullet_count_mult;
        BullethellConfig.FAIRY_ATTACK_INTERVAL_MULT = () -> (float) fe.attack_interval_mult;

        FairyRushJson fr = root.fairy_rush != null ? root.fairy_rush : new FairyRushJson();
        BullethellConfig.FAIRY_RUSH_GAP_BREATHING_EASY = () -> (float) fr.gap_breathing_easy;
        BullethellConfig.FAIRY_RUSH_GAP_BREATHING_NORMAL = () -> (float) fr.gap_breathing_normal;
        BullethellConfig.FAIRY_RUSH_GAP_BREATHING_HARD = () -> (float) fr.gap_breathing_hard;
        BullethellConfig.FAIRY_RUSH_GAP_BREATHING_LUNATIC = () -> (float) fr.gap_breathing_lunatic;
        BullethellConfig.FAIRY_RUSH_DURATION_HINT_EASY = () -> (float) fr.duration_hint_easy;
        BullethellConfig.FAIRY_RUSH_DURATION_HINT_NORMAL = () -> (float) fr.duration_hint_normal;
        BullethellConfig.FAIRY_RUSH_DURATION_HINT_HARD = () -> (float) fr.duration_hint_hard;
        BullethellConfig.FAIRY_RUSH_DURATION_HINT_LUNATIC = () -> (float) fr.duration_hint_lunatic;
        BullethellConfig.FAIRY_RUSH_INTENSITY_BIAS_EASY = () -> fr.intensity_bias_easy;
        BullethellConfig.FAIRY_RUSH_INTENSITY_BIAS_NORMAL = () -> fr.intensity_bias_normal;
        BullethellConfig.FAIRY_RUSH_INTENSITY_BIAS_HARD = () -> fr.intensity_bias_hard;
        BullethellConfig.FAIRY_RUSH_INTENSITY_BIAS_LUNATIC = () -> fr.intensity_bias_lunatic;
        BullethellConfig.FAIRY_CATALOG_INTENSITY_THRESHOLD = () -> fr.catalog_intensity_threshold;
        BullethellConfig.FAIRY_CATALOG_INTENSITY_BOOST_PER_STEP = () -> (float) fr.catalog_intensity_boost_per_step;

        BossDifficultyJson b = root.boss_difficulty != null ? root.boss_difficulty : new BossDifficultyJson();
        BullethellConfig.BOSS_PHASE_DENSITY_CAP = () -> (float) b.phase_density_cap;
        BullethellConfig.BOSS_PHASE_DENSITY_PER_PHASE = () -> (float) b.phase_density_per_phase;
        BullethellConfig.BOSS_PHASE_SPEED_CAP = () -> (float) b.phase_speed_cap;
        BullethellConfig.BOSS_PHASE_SPEED_PER_PHASE = () -> (float) b.phase_speed_per_phase;
        BullethellConfig.BOSS_LUNATIC_DENSITY_EXTRA = () -> (float) b.lunatic_density_extra;
        BullethellConfig.BOSS_LUNATIC_SPEED_EXTRA = () -> (float) b.lunatic_speed_extra;
        BullethellConfig.BOSS_RING_DENSITY_CAP = () -> (float) b.ring_density_cap;
        BullethellConfig.BOSS_RING_ARMS_MAX = () -> b.ring_arms_max;
        BullethellConfig.BOSS_LASER_BEAM_MIN_COOLDOWN = () -> b.laser_beam_min_cooldown;

        PatternDefaultsJson pd = root.pattern_defaults != null ? root.pattern_defaults : new PatternDefaultsJson();
        BullethellConfig.PATTERN_DEFAULT_LIFE_RING = () -> pd.default_life_ring;
        BullethellConfig.PATTERN_DEFAULT_LIFE_AIMED = () -> pd.default_life_aimed;
        BullethellConfig.PATTERN_DEFAULT_LIFE_RAIN = () -> pd.default_life_rain;
        BullethellConfig.PATTERN_DEFAULT_LASER_BEAM_SPREAD_RAD = () -> (float) pd.laser_beam_spread_rad;

        ItemCollectiblesJson ic = root.item_collectibles != null ? root.item_collectibles : new ItemCollectiblesJson();
        BullethellConfig.ITEM_COLLECTIBLE_LIFE_TICKS = () -> ic.collectible_life_ticks;
        BullethellConfig.ITEM_ATTRACT_SPEED = () -> (float) ic.attract_speed;

        CombatJson co = root.combat != null ? root.combat : new CombatJson();
        BullethellConfig.GLOBAL_ENEMY_BULLET_SPEED_MULT = () -> (float) co.global_enemy_bullet_speed_mult;

        VictoryXpJson vx = root.victory_xp != null ? root.victory_xp : new VictoryXpJson();
        BullethellConfig.VICTORY_XP_BASE = () -> vx.base;
        BullethellConfig.VICTORY_XP_SQRT_MULT = () -> vx.sqrt_mult;
        BullethellConfig.VICTORY_XP_MAX = () -> vx.max;
        BullethellConfig.VICTORY_XP_MULT_EASY = () -> vx.mult_easy;
        BullethellConfig.VICTORY_XP_MULT_NORMAL = () -> vx.mult_normal;
        BullethellConfig.VICTORY_XP_MULT_HARD = () -> vx.mult_hard;
        BullethellConfig.VICTORY_XP_MULT_LUNATIC = () -> vx.mult_lunatic;

        TestModeJson tm = root.test_mode != null ? root.test_mode : new TestModeJson();
        BullethellConfig.TEST_DEV_PATH = () -> tm.test_dev_path;
    }

    public static final class CommonJson {
        /** Mirrors Forge {@code [WaveTiming]} - designer-tick schedule divisor per difficulty. */
        public WaveTimingJson wave_timing = new WaveTimingJson();
        public DifficultyTuningJson difficulty_tuning = new DifficultyTuningJson();
        public FairyEnemyAiJson fairy_enemy_ai = new FairyEnemyAiJson();
        public FairyRushJson fairy_rush = new FairyRushJson();
        public BossDifficultyJson boss_difficulty = new BossDifficultyJson();
        public PatternDefaultsJson pattern_defaults = new PatternDefaultsJson();
        public ItemCollectiblesJson item_collectibles = new ItemCollectiblesJson();
        public CombatJson combat = new CombatJson();
        public VictoryXpJson victory_xp = new VictoryXpJson();
        public TestModeJson test_mode = new TestModeJson();
    }

    /** Same semantics as {@link mc.sayda.bullethell.config.BullethellConfig#waveTimingMult}. */
    public static final class WaveTimingJson {
        public double easy = BullethellConfig.DEF_WAVE_TIMING_EASY;
        public double normal = BullethellConfig.DEF_WAVE_TIMING_NORMAL;
        public double hard = BullethellConfig.DEF_WAVE_TIMING_HARD;
        public double lunatic = BullethellConfig.DEF_WAVE_TIMING_LUNATIC;
    }

    public static final class DifficultyTuningJson {
        public double speed_tuner_easy = BullethellConfig.DEF_DIFFICULTY_SPEED_TUNER_EASY;
        public double speed_tuner_normal = BullethellConfig.DEF_DIFFICULTY_SPEED_TUNER_NORMAL;
        public double speed_tuner_hard = BullethellConfig.DEF_DIFFICULTY_SPEED_TUNER_HARD;
        public double speed_tuner_lunatic = BullethellConfig.DEF_DIFFICULTY_SPEED_TUNER_LUNATIC;
        public double density_tuner_easy = BullethellConfig.DEF_DIFFICULTY_DENSITY_TUNER_EASY;
        public double density_tuner_normal = BullethellConfig.DEF_DIFFICULTY_DENSITY_TUNER_NORMAL;
        public double density_tuner_hard = BullethellConfig.DEF_DIFFICULTY_DENSITY_TUNER_HARD;
        public double density_tuner_lunatic = BullethellConfig.DEF_DIFFICULTY_DENSITY_TUNER_LUNATIC;
    }

    public static final class FairyEnemyAiJson {
        public int min_attack_interval_ticks = BullethellConfig.DEF_FAIRY_MIN_ATTACK_INTERVAL_TICKS;
        public int aimed_burst_cap = BullethellConfig.DEF_FAIRY_AIMED_BURST_CAP;
        public int aimed_burst_cap_lunatic = BullethellConfig.DEF_FAIRY_AIMED_BURST_CAP_LUNATIC;
        public int spread_burst_cap = BullethellConfig.DEF_FAIRY_SPREAD_BURST_CAP;
        public int spread_burst_cap_lunatic = BullethellConfig.DEF_FAIRY_SPREAD_BURST_CAP_LUNATIC;
        public int stream_cooldown_divisor = BullethellConfig.DEF_FAIRY_STREAM_COOLDOWN_DIVISOR;
        public int stream_cooldown_min_ticks = BullethellConfig.DEF_FAIRY_STREAM_COOLDOWN_MIN_TICKS;
        public double bullet_count_mult = BullethellConfig.DEF_FAIRY_BULLET_COUNT_MULT;
        public double attack_interval_mult = BullethellConfig.DEF_FAIRY_ATTACK_INTERVAL_MULT;
    }

    public static final class FairyRushJson {
        public double gap_breathing_easy = BullethellConfig.DEF_FAIRY_RUSH_GAP_BREATHING_EASY;
        public double gap_breathing_normal = BullethellConfig.DEF_FAIRY_RUSH_GAP_BREATHING_NORMAL;
        public double gap_breathing_hard = BullethellConfig.DEF_FAIRY_RUSH_GAP_BREATHING_HARD;
        public double gap_breathing_lunatic = BullethellConfig.DEF_FAIRY_RUSH_GAP_BREATHING_LUNATIC;
        public double duration_hint_easy = BullethellConfig.DEF_FAIRY_RUSH_DURATION_HINT_EASY;
        public double duration_hint_normal = BullethellConfig.DEF_FAIRY_RUSH_DURATION_HINT_NORMAL;
        public double duration_hint_hard = BullethellConfig.DEF_FAIRY_RUSH_DURATION_HINT_HARD;
        public double duration_hint_lunatic = BullethellConfig.DEF_FAIRY_RUSH_DURATION_HINT_LUNATIC;
        public int intensity_bias_easy = BullethellConfig.DEF_FAIRY_RUSH_INTENSITY_BIAS_EASY;
        public int intensity_bias_normal = BullethellConfig.DEF_FAIRY_RUSH_INTENSITY_BIAS_NORMAL;
        public int intensity_bias_hard = BullethellConfig.DEF_FAIRY_RUSH_INTENSITY_BIAS_HARD;
        public int intensity_bias_lunatic = BullethellConfig.DEF_FAIRY_RUSH_INTENSITY_BIAS_LUNATIC;
        public int catalog_intensity_threshold = BullethellConfig.DEF_FAIRY_CATALOG_INTENSITY_THRESHOLD;
        public double catalog_intensity_boost_per_step = BullethellConfig.DEF_FAIRY_CATALOG_INTENSITY_BOOST_PER_STEP;
    }

    public static final class BossDifficultyJson {
        public double phase_density_cap = BullethellConfig.DEF_BOSS_PHASE_DENSITY_CAP;
        public double phase_density_per_phase = BullethellConfig.DEF_BOSS_PHASE_DENSITY_PER_PHASE;
        public double phase_speed_cap = BullethellConfig.DEF_BOSS_PHASE_SPEED_CAP;
        public double phase_speed_per_phase = BullethellConfig.DEF_BOSS_PHASE_SPEED_PER_PHASE;
        public double lunatic_density_extra = BullethellConfig.DEF_BOSS_LUNATIC_DENSITY_EXTRA;
        public double lunatic_speed_extra = BullethellConfig.DEF_BOSS_LUNATIC_SPEED_EXTRA;
        public double ring_density_cap = BullethellConfig.DEF_BOSS_RING_DENSITY_CAP;
        public int ring_arms_max = BullethellConfig.DEF_BOSS_RING_ARMS_MAX;
        public int laser_beam_min_cooldown = BullethellConfig.DEF_BOSS_LASER_BEAM_MIN_COOLDOWN;
    }

    public static final class PatternDefaultsJson {
        public int default_life_ring = BullethellConfig.DEF_PATTERN_DEFAULT_LIFE_RING;
        public int default_life_aimed = BullethellConfig.DEF_PATTERN_DEFAULT_LIFE_AIMED;
        public int default_life_rain = BullethellConfig.DEF_PATTERN_DEFAULT_LIFE_RAIN;
        public double laser_beam_spread_rad = BullethellConfig.DEF_PATTERN_DEFAULT_LASER_BEAM_SPREAD_RAD;
    }

    public static final class ItemCollectiblesJson {
        public int collectible_life_ticks = BullethellConfig.DEF_ITEM_COLLECTIBLE_LIFE_TICKS;
        public double attract_speed = BullethellConfig.DEF_ITEM_ATTRACT_SPEED;
    }

    public static final class CombatJson {
        public double global_enemy_bullet_speed_mult = BullethellConfig.DEF_GLOBAL_ENEMY_BULLET_SPEED_MULT;
    }

    public static final class VictoryXpJson {
        public int base = BullethellConfig.DEF_VICTORY_XP_BASE;
        public double sqrt_mult = BullethellConfig.DEF_VICTORY_XP_SQRT_MULT;
        public int max = BullethellConfig.DEF_VICTORY_XP_MAX;
        public double mult_easy = BullethellConfig.DEF_VICTORY_XP_MULT_EASY;
        public double mult_normal = BullethellConfig.DEF_VICTORY_XP_MULT_NORMAL;
        public double mult_hard = BullethellConfig.DEF_VICTORY_XP_MULT_HARD;
        public double mult_lunatic = BullethellConfig.DEF_VICTORY_XP_MULT_LUNATIC;
    }

    public static final class TestModeJson {
        public String test_dev_path = BullethellConfig.DEF_TEST_DEV_PATH;
    }
}
