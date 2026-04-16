package mc.sayda.bullethell.config.forge;

import mc.sayda.bullethell.Bullethell;
import mc.sayda.bullethell.config.BullethellConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.apache.commons.lang3.tuple.Pair;

@Mod.EventBusSubscriber(modid = Bullethell.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeBullethellConfig {

    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    public static final class Common {
        public final ForgeConfigSpec.DoubleValue waveTimingEasy;
        public final ForgeConfigSpec.DoubleValue waveTimingNormal;
        public final ForgeConfigSpec.DoubleValue waveTimingHard;
        public final ForgeConfigSpec.DoubleValue waveTimingLunatic;

        public final ForgeConfigSpec.DoubleValue bossPhaseDensityCap;
        public final ForgeConfigSpec.DoubleValue bossPhaseDensityPerPhase;
        public final ForgeConfigSpec.DoubleValue bossPhaseSpeedCap;
        public final ForgeConfigSpec.DoubleValue bossPhaseSpeedPerPhase;
        public final ForgeConfigSpec.DoubleValue bossLunaticDensityExtra;
        public final ForgeConfigSpec.DoubleValue bossLunaticSpeedExtra;
        public final ForgeConfigSpec.DoubleValue bossRingDensityCap;
        public final ForgeConfigSpec.IntValue bossRingArmsMax;
        public final ForgeConfigSpec.IntValue bossLaserBeamMinCooldown;

        Common(ForgeConfigSpec.Builder builder) {
            builder.push("WaveTiming");
            waveTimingEasy = builder
                    .comment("Fairy-wave spawn timing multiplier for Easy (higher = waves closer together).")
                    .defineInRange("easy", BullethellConfig.DEF_WAVE_TIMING_EASY, 0.05D, 10.0D);
            waveTimingNormal = builder
                    .comment("Multiplier for Normal difficulty.")
                    .defineInRange("normal", BullethellConfig.DEF_WAVE_TIMING_NORMAL, 0.05D, 10.0D);
            waveTimingHard = builder
                    .comment("Multiplier for Hard difficulty.")
                    .defineInRange("hard", BullethellConfig.DEF_WAVE_TIMING_HARD, 0.05D, 10.0D);
            waveTimingLunatic = builder
                    .comment("Multiplier for Lunatic difficulty.")
                    .defineInRange("lunatic", BullethellConfig.DEF_WAVE_TIMING_LUNATIC, 0.05D, 10.0D);
            builder.pop();

            builder.push("BossDifficulty");
            bossPhaseDensityCap = builder
                    .comment("Max additive density creep from boss phase index (added to 1.0). Not the same as DifficultyConfig.densityMult.")
                    .defineInRange("phase_density_cap", BullethellConfig.DEF_BOSS_PHASE_DENSITY_CAP, 0.0D, 2.0D);
            bossPhaseDensityPerPhase = builder
                    .comment("Density creep per boss phase index before cap.")
                    .defineInRange("phase_density_per_phase", BullethellConfig.DEF_BOSS_PHASE_DENSITY_PER_PHASE, 0.0D, 0.5D);
            bossPhaseSpeedCap = builder
                    .comment("Max additive speed creep from boss phase index (added to 1.0).")
                    .defineInRange("phase_speed_cap", BullethellConfig.DEF_BOSS_PHASE_SPEED_CAP, 0.0D, 2.0D);
            bossPhaseSpeedPerPhase = builder
                    .comment("Speed creep per boss phase index before cap.")
                    .defineInRange("phase_speed_per_phase", BullethellConfig.DEF_BOSS_PHASE_SPEED_PER_PHASE, 0.0D, 0.5D);
            bossLunaticDensityExtra = builder
                    .comment("Extra boss density multiplier on Lunatic only (multiplies after DifficultyConfig + phase creep).")
                    .defineInRange("lunatic_density_extra", BullethellConfig.DEF_BOSS_LUNATIC_DENSITY_EXTRA, 1.0D, 3.0D);
            bossLunaticSpeedExtra = builder
                    .comment("Extra boss speed multiplier on Lunatic only.")
                    .defineInRange("lunatic_speed_extra", BullethellConfig.DEF_BOSS_LUNATIC_SPEED_EXTRA, 1.0D, 3.0D);
            bossRingDensityCap = builder
                    .comment("Upper clamp when scaling AIMED_RING ring arms by boss density.")
                    .defineInRange("ring_density_cap", BullethellConfig.DEF_BOSS_RING_DENSITY_CAP, 1.0D, 3.0D);
            bossRingArmsMax = builder
                    .comment("Hard cap on scaled ring arms for AIMED_RING.")
                    .defineInRange("ring_arms_max", BullethellConfig.DEF_BOSS_RING_ARMS_MAX, 6, 64);
            bossLaserBeamMinCooldown = builder
                    .comment("Minimum boss pattern cooldown (ticks) for LASER_BEAM after density scaling.")
                    .defineInRange("laser_beam_min_cooldown", BullethellConfig.DEF_BOSS_LASER_BEAM_MIN_COOLDOWN, 1, 40);
            builder.pop();
        }
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == COMMON_SPEC) {
            apply();
        }
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == COMMON_SPEC) {
            apply();
        }
    }

    public static void apply() {
        BullethellConfig.WAVE_TIMING_EASY = () -> COMMON.waveTimingEasy.get().floatValue();
        BullethellConfig.WAVE_TIMING_NORMAL = () -> COMMON.waveTimingNormal.get().floatValue();
        BullethellConfig.WAVE_TIMING_HARD = () -> COMMON.waveTimingHard.get().floatValue();
        BullethellConfig.WAVE_TIMING_LUNATIC = () -> COMMON.waveTimingLunatic.get().floatValue();

        BullethellConfig.BOSS_PHASE_DENSITY_CAP = () -> COMMON.bossPhaseDensityCap.get().floatValue();
        BullethellConfig.BOSS_PHASE_DENSITY_PER_PHASE = () -> COMMON.bossPhaseDensityPerPhase.get().floatValue();
        BullethellConfig.BOSS_PHASE_SPEED_CAP = () -> COMMON.bossPhaseSpeedCap.get().floatValue();
        BullethellConfig.BOSS_PHASE_SPEED_PER_PHASE = () -> COMMON.bossPhaseSpeedPerPhase.get().floatValue();
        BullethellConfig.BOSS_LUNATIC_DENSITY_EXTRA = () -> COMMON.bossLunaticDensityExtra.get().floatValue();
        BullethellConfig.BOSS_LUNATIC_SPEED_EXTRA = () -> COMMON.bossLunaticSpeedExtra.get().floatValue();
        BullethellConfig.BOSS_RING_DENSITY_CAP = () -> COMMON.bossRingDensityCap.get().floatValue();
        BullethellConfig.BOSS_RING_ARMS_MAX = () -> COMMON.bossRingArmsMax.get();
        BullethellConfig.BOSS_LASER_BEAM_MIN_COOLDOWN = () -> COMMON.bossLaserBeamMinCooldown.get();
    }
}
