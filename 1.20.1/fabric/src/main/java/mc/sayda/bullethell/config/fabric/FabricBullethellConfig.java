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
    }

    public static final class CommonJson {
        public WaveTimingJson wave_timing = new WaveTimingJson();
        public BossDifficultyJson boss_difficulty = new BossDifficultyJson();
    }

    public static final class WaveTimingJson {
        public double easy = BullethellConfig.DEF_WAVE_TIMING_EASY;
        public double normal = BullethellConfig.DEF_WAVE_TIMING_NORMAL;
        public double hard = BullethellConfig.DEF_WAVE_TIMING_HARD;
        public double lunatic = BullethellConfig.DEF_WAVE_TIMING_LUNATIC;
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
}
