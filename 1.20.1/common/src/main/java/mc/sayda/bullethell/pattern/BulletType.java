package mc.sayda.bullethell.pattern;

/** Bullet visual type. id == ordinal - safe to store in BulletPool float array. */

// TODO: Is this class needed? Replaced with bullet_types.json?
public enum BulletType {

    /** Generic large orb ({@code orb.png}); neutral / light tint - legacy tinted orb. */
    ORB        (0xFFFFFFFF, 4f, 1f),
    /** Blue star / curtain shots; hitbox smaller than drawn star footprint. */
    STAR       (0xFF44AAFF, 4f, 0.76f),
    /** Yellow fairy rice stream; forgiving hitbox vs thin sprite. */
    RICE       (0xFFFFFF44, 3f, 0.58f),
    LASER_HEAD (0xFFFF44FF, 6f, 1f),  // magenta
    BUBBLE     (0xFF44FFAA, 5f, 1f),  // teal
    PLAYER_SHOT(0xFF88FF44, 3f, 1f),  // green - player-fired bullets
    /** Marisa / player 4-point stars ({@code star_4.png}); tight hitbox vs wide sprite. */
    GOLD       (0xFFFFDD00, 4f, 0.42f),
    SPARK      (0xFFFFFF88, 8f, 1f),  // bright white-yellow - Master Spark / Final Spark
    HOMING_ORB (0xFFFF88FF, 6f, 1f),  // magenta/purple - legacy homing orb type
    /** Sakuya knives; slightly forgiving vs blade sprite. */
    KUNAI      (0xFF88CCFF, 4f, 0.82f),
    /** Thin stake sprite; keep hitbox much smaller than drawn needle. */
    NEEDLE     (0xFFFFDDDD, 2.5f, 0.48f),
    /** Deep scarlet mist / large droplets (legacy tinted {@code orb.png}). */
    SCARLET    (0xFFCC1020, 4.5f, 1f),
    /**
     * Large orb; ARGB alpha in {@link #color} is honored by the renderer (tint +
     * texture). Tune collision with JSON {@code hitboxScale} / pool hit scale.
     */
    SCARLET_LARGE (0xEECC1020, 7f, 1f),
    /** Small pill / mentos trail followers (Scarlet Meister tail pressure). */
    SCARLET_MENTOS(0xFFE03040, 2.5f, 1f),
    /**
     * Cirno / ice shards; {@code icicle.png} in renderer. Collision profile matches {@link #KUNAI}
     * (thin sprite, forgiving {@code hitboxCollisionMul}).
     */
    ICE        (0xFF66DDFF, 4f, 0.82f),
    /**
     * Sakuya-style throwing knives; {@code knife.png}. Same collision and tuning as
     * {@link #KUNAI}; tinted red in the renderer (TH-style) while {@link #KUNAI} stays cool-toned.
     */
    KNIFE      (0xFFFF5058, 4f, 0.82f),
    /**
     * Reisen-style bullet; white ellipse with magenta border ({@code bullet.png}).
     * Used for Reisen Udongein Inaba's signature bullet barrages.
     */
    BULLET      (0xFFFFFFFF, 4f, 1.0f),
    /**
     * Reimu homing amulets ({@code ofuda.png}). Tint is not applied in the renderer.
     */
    AMULET     (0xFFFFDDFF, 4f, 1f),
    /**
     * Crimson large orb ({@code orb.png}); classic red preset for Scarlet-themed patterns
     * when {@link #ORB} is too neutral.
     */
    CRIMSON_ORB(0xFFFF4444, 4f, 1f),
    /**
     * Small dot ({@code dot.png}); untinted - default dense danmaku when JSON uses {@code DOT}.
     * Draw radius matches legacy {@link #ORB}; {@link #hitboxCollisionMul} shrinks collision only.
     */
    DOT        (0xFFFFFFFF, 3f, 0.68f),
    /**
     * Blue orb ({@code orb_blue.png}); untinted. Draw size matches legacy {@link #ORB}; collision
     * smaller via {@link #hitboxCollisionMul}. Player homing may use higher pool {@code visScale}
     * to match old {@link #HOMING_ORB} on-screen size.
     */
    BLUE_ORB   (0xFFFFFFFF, 5f, 0.68f),
    /** Red orb ({@code orb_red.png}); untinted. Draw matches {@link #SCARLET}; tighter hitbox. */
    RED_ORB    (0xFFFFFFFF, 5f, 0.68f),
    /** Large red orb ({@code orb_red.png}); untinted. Draw matches {@link #SCARLET_LARGE}; tighter hitbox. */
    RED_ORB_LARGE (0xFFFFFFFF, 7f, 0.60f),
    /**
     * Short laser segment ({@code blue_laser.png}). Collision and sprite use separate bases in
     * {@code bullet_types.json}: {@code lineCollisionHalfLength} × pool visScale,
     * {@code lineCollisionHalfWidth} × pool hitScale (hit), and optional
     * {@code lineVisualHalfLength} / {@code lineVisualHalfWidth} for draw (0 = same as collision).
     */
    BLUE_LASER   (0xFFAA88FF, 3.25f, 1f),
    /** Horizontal short laser - identical to {@link #BLUE_LASER} but {@code baseAngleDeg=90} so segments appear horizontal when falling straight down. */
    RIVER_LASER (0xFFAA88FF, 3.25f, 1f),
    /** Blue kunai-style dagger ({@code kunai.png}); tinted blue. */
    BLUE_DAGGER   (0xFF4499FF, 3.5f, 0.80f),
    /** Red kunai-style dagger ({@code kunai.png}); tinted red. */
    RED_DAGGER    (0xFFFF3344, 3.5f, 0.80f),
    /** MoF-style blue rice ({@code rice_blue.png}); JSON overrides in {@code bullet_types.json}. */
    BLUE_RICE     (0xFFFFFFFF, 3f, 1f),
    /** MoF-style green rice ({@code rice_green.png}); JSON overrides in {@code bullet_types.json}. */
    GREEN_RICE    (0xFFFFFFFF, 3f, 1f),
    /** Extra-large orb for spell rings; JSON overrides radius / texture in {@code bullet_types.json}. */
    LARGE_ORB     (0xFFFFFFFF, 12f, 1f);

    public final int   color;
    public final float radius;
    /**
     * Multiplies {@code BulletType#radius * BulletPool hitScale} for collision and
     * debug overlay only (not render size).
     */
    public final float hitboxCollisionMul;

    BulletType(int color, float radius, float hitboxCollisionMul) {
        this.color  = color;
        this.radius = radius;
        this.hitboxCollisionMul = hitboxCollisionMul;
    }

    private static final BulletType[] VALUES = values();

    public static BulletType fromId(int id) {
        return (id >= 0 && id < VALUES.length) ? VALUES[id] : ORB;
    }

    public int getId() { return ordinal(); }

    /**
     * Player bullet pools: when {@link mc.sayda.bullethell.arena.BulletPool} spawn uses
     * {@code HOMING_USE_TYPE_DEFAULT}, homing steering is enabled for these types (Reimu amulet, legacy orbs).
     */
    public boolean defaultPlayerHomingSteer() {
        return this == AMULET || this == HOMING_ORB || this == BLUE_ORB;
    }

    /** Effective collision/draw radius, hot-reloadable via {@code bullet_types.json}. */
    public float getRadius() { return BulletTypeLoader.get(this).radius; }

    /** Effective hitbox multiplier, hot-reloadable via {@code bullet_types.json}. */
    public float getHitboxMul() { return BulletTypeLoader.get(this).hitboxMul; }

    public boolean isShortLaserLineHit() {
        return BulletTypeLoader.get(this).lineHit;
    }

    /** Line-hit: collision segment half-length × pool {@code visScale} (pattern bullet size). */
    public float lineHitCollisionHalfLength(float visScale) {
        BulletTypeData d = BulletTypeLoader.get(this);
        if (!d.lineHit)
            return 0f;
        return d.lineCollisionHalfLength * visScale;
    }

    /** Line-hit: collision strip half-width × pool {@code hitScale} (pattern hitbox size). */
    public float lineHitCollisionHalfWidth(float hitScale) {
        BulletTypeData d = BulletTypeLoader.get(this);
        if (!d.lineHit)
            return 0f;
        return d.lineCollisionHalfWidth * hitScale;
    }

    /**
     * Line-hit: drawn beam half-length in arena units (before GUI scale). Uses
     * {@link BulletTypeData#lineVisualHalfLength} or collision length if visual is 0; ×
     * {@code visScale} × {@code textureScale}.
     */
    public float lineHitDrawHalfLength(float visScale) {
        BulletTypeData d = BulletTypeLoader.get(this);
        if (!d.lineHit)
            return 0f;
        float base = d.lineVisualHalfLength > 0f ? d.lineVisualHalfLength : d.lineCollisionHalfLength;
        return base * visScale * d.textureScale;
    }

    /**
     * Line-hit: drawn beam half-width in arena units. Uses {@link BulletTypeData#lineVisualHalfWidth}
     * or collision width if visual is 0; × {@code hitScale} × {@code textureScale}.
     */
    public float lineHitDrawHalfWidth(float hitScale) {
        BulletTypeData d = BulletTypeLoader.get(this);
        if (!d.lineHit)
            return 0f;
        float base = d.lineVisualHalfWidth > 0f ? d.lineVisualHalfWidth : d.lineCollisionHalfWidth;
        return base * hitScale * d.textureScale;
    }
}
