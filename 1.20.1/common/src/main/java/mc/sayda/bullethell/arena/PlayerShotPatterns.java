package mc.sayda.bullethell.arena;

import mc.sayda.bullethell.boss.CharacterDefinition;
import mc.sayda.bullethell.boss.PlayerShotOptionJson;
import mc.sayda.bullethell.pattern.BulletType;

import java.util.List;
import java.util.Locale;

/**
 * Playable shot patterns come from {@link CharacterDefinition#shotOptions} in
 * {@code data/bullethell/characters/&lt;id&gt;.json}; if {@code shotOptions} is missing or empty,
 * {@link CharacterLoader} substitutes a built-in generic spread (see {@code HardcodedPlayerShots} in source).
 * <p>
 * Each shot option lists {@link PlayerShotOptionJson.Mode#powerTiers} (P0…); each tier is a list of
 * bullet spawns: type, offset, velocity, lifetime, scales, {@code angularVelocity} for curved motion,
 * {@code freezeTicks}, and optional {@code homing} (null = use {@link BulletType#defaultPlayerHomingSteer()}).
 * Spread patterns are simply many spawns in one tier; there is no separate Java “pattern language”.
 * <p>
 * Vertical velocities are arena units/tick before {@link #SHOT_SPEED_SCALE} (~TH06 tuning on 20 tps).
 */
public final class PlayerShotPatterns {

    private PlayerShotPatterns() {}

    /** Scales arena-units/tick velocities to approximate TH06 player shot speed on 20 tps arena. */
    private static final float SHOT_SPEED_SCALE = 1.92f;

    public static void fire(PlayerState2D ps, BulletPool pb, CharacterDefinition def, int shotTypeIndex) {
        fireFromData(ps, pb, def, shotTypeIndex);
    }

    private static void fireFromData(PlayerState2D ps, BulletPool pb, CharacterDefinition def, int shotTypeIndex) {
        List<PlayerShotOptionJson> opts = def.shotOptions;
        if (opts == null || opts.isEmpty())
            return;
        int idx = Math.max(0, Math.min(shotTypeIndex, opts.size() - 1));
        PlayerShotOptionJson opt = opts.get(idx);
        if (opt == null)
            return;
        PlayerShotOptionJson.Mode mode = ps.focused ? opt.focused : opt.unfocused;
        if (mode == null || mode.powerTiers == null || mode.powerTiers.isEmpty())
            return;
        int lv = ps.powerLevel();
        int tierIdx = Math.max(0, Math.min(lv, mode.powerTiers.size() - 1));
        List<PlayerShotOptionJson.Spawn> tier = mode.powerTiers.get(tierIdx);
        if (tier == null)
            return;
        float px = ps.x;
        float py = ps.y - 4f;
        for (PlayerShotOptionJson.Spawn s : tier) {
            if (s == null)
                continue;
            BulletType bt = parseBulletType(s.bulletType);
            int homing = s.homing == null ? BulletPool.HOMING_USE_TYPE_DEFAULT
                    : (s.homing ? BulletPool.HOMING_ON : BulletPool.HOMING_OFF);
            pb.spawn(
                    px + s.offsetX,
                    py + s.offsetY,
                    s.vx * SHOT_SPEED_SCALE,
                    s.vy * SHOT_SPEED_SCALE,
                    bt.getId(),
                    s.lifetime,
                    s.visScale,
                    s.hitScale,
                    s.angularVelocity,
                    s.freezeTicks,
                    homing);
        }
    }

    private static BulletType parseBulletType(String raw) {
        if (raw == null || raw.isBlank())
            return BulletType.fromName("PLAYER_SHOT");
        return BulletType.fromName(raw.trim());
    }
}
