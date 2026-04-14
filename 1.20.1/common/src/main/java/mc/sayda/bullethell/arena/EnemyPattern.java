package mc.sayda.bullethell.arena;

/**
 * Attack pattern types for fairy enemies.
 *
 * Touhou difficulty scaling notes (research):
 *   Bullet count and speed scale via DifficultyConfig.densityMult / speedMult.
 *   These values calibrate cleanly: BLUE_FAIRY(bulletCount=2) gives 1 bullet on
 *   Easy, 2 on Normal, 3 on Hard, 4 on Lunatic via round(2 * densityMult).
 *   Fire interval also scales: Normal=40 ticks (2 s), Lunatic=20 ticks (1 s).
 *
 * Pattern notes (per Touhou game conventions TH6-TH14):
 *   AIMED      – Fan spread aimed at the player. Standard wave filler.
 *   RING       – Uniform ring in a random start orientation each burst.
 *                Green fairies, TH6-style barrier enemies.
 *   AIMED_RING – Aimed burst + slower surrounding ring. Large/anchor fairies.
 *   SPREAD     – Fixed downward fan, independent of player position.
 *                Yellow fairies: environmental hazard feel (TH8 curtain style).
 *   STREAM     – Single aimed bullet at 1/3 the normal interval.
 *                Rapid-fire harassment; danger comes from rate, not spread.
 */
public enum EnemyPattern {
    AIMED,
    RING,
    AIMED_RING,
    SPREAD,
    STREAM;

    public int getId() { return ordinal(); }

    public static EnemyPattern fromId(int id) {
        EnemyPattern[] v = values();
        return (id >= 0 && id < v.length) ? v[id] : AIMED;
    }

    public static EnemyPattern fromName(String name) {
        if (name == null || name.isEmpty()) return AIMED;
        try { return valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return AIMED; }
    }
}
