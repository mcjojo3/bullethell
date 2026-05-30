package mc.sayda.bullethell.boss;

import java.util.List;

/**
 * One selectable shot type (A/B/…) with unfocused/focused patterns per power
 * tier.
 * <p>
 * <b>Schema:</b> {@link #unfocused} / {@link #focused} each have
 * {@link Mode#powerTiers} - index 0 = P0,
 * clamped by {@link mc.sayda.bullethell.arena.PlayerState2D#powerLevel()}. Each
 * tier is a list of
 * {@link Spawn}s fired together (spread = many entries). Per-spawn:
 * {@link Spawn#bulletType} (enum name),
 * offsets, {@code vx}/{@code vy}
 * (pre-{@link mc.sayda.bullethell.arena.PlayerShotPatterns} speed scale),
 * {@code lifetime}, {@code visScale}/{@code hitScale}, {@code angularVelocity}
 * (rad/tick curve),
 * {@code freezeTicks}, {@code homing} ({@code null} = type default from
 * {@link mc.sayda.bullethell.pattern.BulletType#defaultPlayerHomingSteer()}).
 */
public class PlayerShotOptionJson {

    public String label = "";
    public String description = "";
    /** Optional note for datapack authors (e.g. game reference). */
    public String note = "";

    public Mode unfocused;
    public Mode focused;

    public static class Mode {
        /**
         * Index 0 = P0, …; {@link mc.sayda.bullethell.arena.PlayerState2D#powerLevel()}
         * clamps to last tier.
         */
        public List<List<Spawn>> powerTiers;
    }

    public static class Spawn {
        /** {@link mc.sayda.bullethell.pattern.BulletType} name, e.g. {@code RED_OFUDA}. */
        public String bulletType = "PLAYER_SHOT";
        public float offsetX = 0f;
        public float offsetY = 0f;
        public float vx = 0f;
        public float vy = -16f;
        public int lifetime = 55;
        public float visScale = 1f;
        public float hitScale = 2.75f;
        public float angularVelocity = 0f;
        public int freezeTicks = 0;
        /**
         * null = use
         * {@link mc.sayda.bullethell.pattern.BulletType#defaultPlayerHomingSteer()} for
         * this type;
         * true/false = force on/off (e.g. needles off, ofudas on).
         */
        public Boolean homing;

        /**
         * Independent fire period in arena ticks (at 20 tps). 0 = fires with the main
         * shot cooldown. Positive = fires every N ticks via
         * {@link PlayerState2D#shotTick},
         * independently of the primary stream. Use for secondary orb/option shots
         * (e.g. TH7 homing ofudas fire every 5 or 10 ticks while forward shots fire
         * every 2).
         */
        public int fireRateTicks = 0;
    }
}
