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
                public final ForgeConfigSpec.DoubleValue diffSpeedTunerEasy;
                public final ForgeConfigSpec.DoubleValue diffSpeedTunerNormal;
                public final ForgeConfigSpec.DoubleValue diffSpeedTunerHard;
                public final ForgeConfigSpec.DoubleValue diffSpeedTunerLunatic;
                public final ForgeConfigSpec.DoubleValue diffDensityTunerEasy;
                public final ForgeConfigSpec.DoubleValue diffDensityTunerNormal;
                public final ForgeConfigSpec.DoubleValue diffDensityTunerHard;
                public final ForgeConfigSpec.DoubleValue diffDensityTunerLunatic;

                public final ForgeConfigSpec.DoubleValue bossPhaseDensityCap;
                public final ForgeConfigSpec.DoubleValue bossPhaseDensityPerPhase;
                public final ForgeConfigSpec.DoubleValue bossPhaseSpeedCap;
                public final ForgeConfigSpec.DoubleValue bossPhaseSpeedPerPhase;
                public final ForgeConfigSpec.DoubleValue bossLunaticDensityExtra;
                public final ForgeConfigSpec.DoubleValue bossLunaticSpeedExtra;
                public final ForgeConfigSpec.DoubleValue bossRingDensityCap;
                public final ForgeConfigSpec.IntValue bossRingArmsMax;
                public final ForgeConfigSpec.IntValue bossLaserBeamMinCooldown;

                public final ForgeConfigSpec.IntValue patternDefaultLifeRing;
                public final ForgeConfigSpec.IntValue patternDefaultLifeAimed;
                public final ForgeConfigSpec.IntValue patternDefaultLifeRain;
                public final ForgeConfigSpec.DoubleValue patternLaserBeamSpreadRad;

                public final ForgeConfigSpec.IntValue itemCollectibleLifeTicks;
                public final ForgeConfigSpec.DoubleValue itemAttractSpeed;

                public final ForgeConfigSpec.DoubleValue globalEnemyBulletSpeedMult;

                public final ForgeConfigSpec.IntValue victoryXpBase;
                public final ForgeConfigSpec.DoubleValue victoryXpSqrtMult;
                public final ForgeConfigSpec.IntValue victoryXpMax;
                public final ForgeConfigSpec.DoubleValue victoryXpMultEasy;
                public final ForgeConfigSpec.DoubleValue victoryXpMultNormal;
                public final ForgeConfigSpec.DoubleValue victoryXpMultHard;
                public final ForgeConfigSpec.DoubleValue victoryXpMultLunatic;

                public final ForgeConfigSpec.ConfigValue<String> testDevPath;

                Common(ForgeConfigSpec.Builder builder) {
                        builder.push("DifficultyTuning");
                        diffSpeedTunerEasy = builder
                                        .comment("Multiplier on DifficultyConfig.speedMult for Easy (bullet speed scaling).")
                                        .defineInRange("speed_tuner_easy",
                                                        BullethellConfig.DEF_DIFFICULTY_SPEED_TUNER_EASY, 0.25D, 4.0D);
                        diffSpeedTunerNormal = builder
                                        .defineInRange("speed_tuner_normal",
                                                        BullethellConfig.DEF_DIFFICULTY_SPEED_TUNER_NORMAL, 0.25D,
                                                        4.0D);
                        diffSpeedTunerHard = builder
                                        .defineInRange("speed_tuner_hard",
                                                        BullethellConfig.DEF_DIFFICULTY_SPEED_TUNER_HARD, 0.25D, 4.0D);
                        diffSpeedTunerLunatic = builder
                                        .defineInRange("speed_tuner_lunatic",
                                                        BullethellConfig.DEF_DIFFICULTY_SPEED_TUNER_LUNATIC, 0.25D,
                                                        4.0D);
                        diffDensityTunerEasy = builder
                                        .comment("Multiplier on DifficultyConfig.densityMult (bullet counts, attack cadence, boss density).")
                                        .defineInRange("density_tuner_easy",
                                                        BullethellConfig.DEF_DIFFICULTY_DENSITY_TUNER_EASY, 0.25D,
                                                        4.0D);
                        diffDensityTunerNormal = builder
                                        .defineInRange("density_tuner_normal",
                                                        BullethellConfig.DEF_DIFFICULTY_DENSITY_TUNER_NORMAL, 0.25D,
                                                        4.0D);
                        diffDensityTunerHard = builder
                                        .defineInRange("density_tuner_hard",
                                                        BullethellConfig.DEF_DIFFICULTY_DENSITY_TUNER_HARD, 0.25D,
                                                        4.0D);
                        diffDensityTunerLunatic = builder
                                        .defineInRange("density_tuner_lunatic",
                                                        BullethellConfig.DEF_DIFFICULTY_DENSITY_TUNER_LUNATIC, 0.25D,
                                                        4.0D);
                        builder.pop();

                        builder.push("BossDifficulty");
                        bossPhaseDensityCap = builder
                                        .comment("Max additive density creep from boss phase index (added to 1.0). Not the same as DifficultyConfig.densityMult.")
                                        .defineInRange("phase_density_cap", BullethellConfig.DEF_BOSS_PHASE_DENSITY_CAP,
                                                        0.0D, 2.0D);
                        bossPhaseDensityPerPhase = builder
                                        .comment("Density creep per boss phase index before cap.")
                                        .defineInRange("phase_density_per_phase",
                                                        BullethellConfig.DEF_BOSS_PHASE_DENSITY_PER_PHASE, 0.0D, 0.5D);
                        bossPhaseSpeedCap = builder
                                        .comment("Max additive speed creep from boss phase index (added to 1.0).")
                                        .defineInRange("phase_speed_cap", BullethellConfig.DEF_BOSS_PHASE_SPEED_CAP,
                                                        0.0D, 2.0D);
                        bossPhaseSpeedPerPhase = builder
                                        .comment("Speed creep per boss phase index before cap.")
                                        .defineInRange("phase_speed_per_phase",
                                                        BullethellConfig.DEF_BOSS_PHASE_SPEED_PER_PHASE, 0.0D, 0.5D);
                        bossLunaticDensityExtra = builder
                                        .comment("Extra boss density multiplier on Lunatic only (multiplies after DifficultyConfig + phase creep).")
                                        .defineInRange("lunatic_density_extra",
                                                        BullethellConfig.DEF_BOSS_LUNATIC_DENSITY_EXTRA, 1.0D, 3.0D);
                        bossLunaticSpeedExtra = builder
                                        .comment("Extra boss speed multiplier on Lunatic only.")
                                        .defineInRange("lunatic_speed_extra",
                                                        BullethellConfig.DEF_BOSS_LUNATIC_SPEED_EXTRA, 1.0D, 3.0D);
                        bossRingDensityCap = builder
                                        .comment("Upper clamp when scaling AIMED_RING ring arms by boss density.")
                                        .defineInRange("ring_density_cap", BullethellConfig.DEF_BOSS_RING_DENSITY_CAP,
                                                        1.0D, 3.0D);
                        bossRingArmsMax = builder
                                        .comment("Hard cap on scaled ring arms for AIMED_RING.")
                                        .defineInRange("ring_arms_max", BullethellConfig.DEF_BOSS_RING_ARMS_MAX, 6, 64);
                        bossLaserBeamMinCooldown = builder
                                        .comment("Minimum boss pattern cooldown (ticks) for LASER_BEAM after density scaling.")
                                        .defineInRange("laser_beam_min_cooldown",
                                                        BullethellConfig.DEF_BOSS_LASER_BEAM_MIN_COOLDOWN, 1, 40);
                        builder.pop();

                        builder.push("PatternDefaults");
                        patternDefaultLifeRing = builder
                                        .comment("Default bullet lifetime (ticks) when boss JSON omits lifetime (ring-like patterns).")
                                        .defineInRange("default_life_ring",
                                                        BullethellConfig.DEF_PATTERN_DEFAULT_LIFE_RING, 40, 800);
                        patternDefaultLifeAimed = builder
                                        .defineInRange("default_life_aimed",
                                                        BullethellConfig.DEF_PATTERN_DEFAULT_LIFE_AIMED, 40, 800);
                        patternDefaultLifeRain = builder
                                        .defineInRange("default_life_rain",
                                                        BullethellConfig.DEF_PATTERN_DEFAULT_LIFE_RAIN, 40, 800);
                        patternLaserBeamSpreadRad = builder
                                        .comment("Default radians between LASER_BEAM shots when spread not specified.")
                                        .defineInRange("laser_beam_spread_rad",
                                                        BullethellConfig.DEF_PATTERN_DEFAULT_LASER_BEAM_SPREAD_RAD,
                                                        0.005D, 0.5D);
                        builder.pop();

                        builder.push("ItemCollectibles");
                        itemCollectibleLifeTicks = builder
                                        .comment("Initial lifetime ticks for score/power drops (ItemPool).")
                                        .defineInRange("collectible_life_ticks",
                                                        BullethellConfig.DEF_ITEM_COLLECTIBLE_LIFE_TICKS, 60, 2400);
                        itemAttractSpeed = builder
                                        .comment("Arena units/tick when items move toward player during bomb attract.")
                                        .defineInRange("attract_speed", BullethellConfig.DEF_ITEM_ATTRACT_SPEED, 1.0D,
                                                        40.0D);
                        builder.pop();

                        builder.push("Combat");
                        globalEnemyBulletSpeedMult = builder
                                        .comment("Multiplier on enemy/boss bullet speed after difficulty scaling (global slow-mo for patterns).")
                                        .defineInRange("global_enemy_bullet_speed_mult",
                                                        BullethellConfig.DEF_GLOBAL_ENEMY_BULLET_SPEED_MULT, 0.25D,
                                                        2.0D);
                        builder.pop();

                        builder.push("TestMode");
                        testDevPath = builder
                                        .comment("Filesystem path to a directory containing boss JSON files for hot-reload during pattern testing. Leave blank to use only the bundled JSONs.")
                                        .define("testDevPath", BullethellConfig.DEF_TEST_DEV_PATH);
                        builder.pop();

                        builder.push("VictoryXp");
                        victoryXpBase = builder
                                        .comment("Flat XP before sqrt(score) term.")
                                        .defineInRange("base", BullethellConfig.DEF_VICTORY_XP_BASE, 0, 500);
                        victoryXpSqrtMult = builder
                                        .defineInRange("sqrt_mult", BullethellConfig.DEF_VICTORY_XP_SQRT_MULT, 0.0D,
                                                        2.0D);
                        victoryXpMax = builder
                                        .defineInRange("max", BullethellConfig.DEF_VICTORY_XP_MAX, 0, 5000);
                        victoryXpMultEasy = builder
                                        .defineInRange("mult_easy", BullethellConfig.DEF_VICTORY_XP_MULT_EASY, 0.0D,
                                                        3.0D);
                        victoryXpMultNormal = builder
                                        .defineInRange("mult_normal", BullethellConfig.DEF_VICTORY_XP_MULT_NORMAL, 0.0D,
                                                        3.0D);
                        victoryXpMultHard = builder
                                        .defineInRange("mult_hard", BullethellConfig.DEF_VICTORY_XP_MULT_HARD, 0.0D,
                                                        3.0D);
                        victoryXpMultLunatic = builder
                                        .defineInRange("mult_lunatic", BullethellConfig.DEF_VICTORY_XP_MULT_LUNATIC,
                                                        0.0D, 3.0D);
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
                Common c = COMMON;
                BullethellConfig.DIFFICULTY_SPEED_TUNER_EASY = () -> c.diffSpeedTunerEasy.get().floatValue();
                BullethellConfig.DIFFICULTY_SPEED_TUNER_NORMAL = () -> c.diffSpeedTunerNormal.get().floatValue();
                BullethellConfig.DIFFICULTY_SPEED_TUNER_HARD = () -> c.diffSpeedTunerHard.get().floatValue();
                BullethellConfig.DIFFICULTY_SPEED_TUNER_LUNATIC = () -> c.diffSpeedTunerLunatic.get().floatValue();
                BullethellConfig.DIFFICULTY_DENSITY_TUNER_EASY = () -> c.diffDensityTunerEasy.get().floatValue();
                BullethellConfig.DIFFICULTY_DENSITY_TUNER_NORMAL = () -> c.diffDensityTunerNormal.get().floatValue();
                BullethellConfig.DIFFICULTY_DENSITY_TUNER_HARD = () -> c.diffDensityTunerHard.get().floatValue();
                BullethellConfig.DIFFICULTY_DENSITY_TUNER_LUNATIC = () -> c.diffDensityTunerLunatic.get().floatValue();

                BullethellConfig.BOSS_PHASE_DENSITY_CAP = () -> c.bossPhaseDensityCap.get().floatValue();
                BullethellConfig.BOSS_PHASE_DENSITY_PER_PHASE = () -> c.bossPhaseDensityPerPhase.get().floatValue();
                BullethellConfig.BOSS_PHASE_SPEED_CAP = () -> c.bossPhaseSpeedCap.get().floatValue();
                BullethellConfig.BOSS_PHASE_SPEED_PER_PHASE = () -> c.bossPhaseSpeedPerPhase.get().floatValue();
                BullethellConfig.BOSS_LUNATIC_DENSITY_EXTRA = () -> c.bossLunaticDensityExtra.get().floatValue();
                BullethellConfig.BOSS_LUNATIC_SPEED_EXTRA = () -> c.bossLunaticSpeedExtra.get().floatValue();
                BullethellConfig.BOSS_RING_DENSITY_CAP = () -> c.bossRingDensityCap.get().floatValue();
                BullethellConfig.BOSS_RING_ARMS_MAX = c.bossRingArmsMax::get;
                BullethellConfig.BOSS_LASER_BEAM_MIN_COOLDOWN = c.bossLaserBeamMinCooldown::get;

                BullethellConfig.PATTERN_DEFAULT_LIFE_RING = c.patternDefaultLifeRing::get;
                BullethellConfig.PATTERN_DEFAULT_LIFE_AIMED = c.patternDefaultLifeAimed::get;
                BullethellConfig.PATTERN_DEFAULT_LIFE_RAIN = c.patternDefaultLifeRain::get;
                BullethellConfig.PATTERN_DEFAULT_LASER_BEAM_SPREAD_RAD = () -> c.patternLaserBeamSpreadRad.get()
                                .floatValue();

                BullethellConfig.ITEM_COLLECTIBLE_LIFE_TICKS = c.itemCollectibleLifeTicks::get;
                BullethellConfig.ITEM_ATTRACT_SPEED = () -> c.itemAttractSpeed.get().floatValue();

                BullethellConfig.GLOBAL_ENEMY_BULLET_SPEED_MULT = () -> c.globalEnemyBulletSpeedMult.get().floatValue();

                BullethellConfig.VICTORY_XP_BASE = c.victoryXpBase::get;
                BullethellConfig.VICTORY_XP_SQRT_MULT = c.victoryXpSqrtMult::get;
                BullethellConfig.VICTORY_XP_MAX = c.victoryXpMax::get;
                BullethellConfig.VICTORY_XP_MULT_EASY = c.victoryXpMultEasy::get;
                BullethellConfig.VICTORY_XP_MULT_NORMAL = c.victoryXpMultNormal::get;
                BullethellConfig.VICTORY_XP_MULT_HARD = c.victoryXpMultHard::get;
                BullethellConfig.VICTORY_XP_MULT_LUNATIC = c.victoryXpMultLunatic::get;

                BullethellConfig.TEST_DEV_PATH = c.testDevPath::get;
        }
}
