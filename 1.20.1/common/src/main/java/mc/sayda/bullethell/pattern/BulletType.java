package mc.sayda.bullethell.pattern;

/**
 * Bullet visual type registry.
 * Loaded and populated from data/bullethell/bullet_types.json via BulletTypeLoader.
 */
public final class BulletType {

    private static final java.util.Map<String, BulletType> REGISTRY = new java.util.LinkedHashMap<>();
    private static final java.util.List<BulletType> BY_ID = new java.util.ArrayList<>();

    public final String name;
    public final int    color;
    public final float  radius;
    public final float  hitboxCollisionMul;
    private final int   id;

    private BulletType(String name, int color, float radius, float hitboxCollisionMul, int id) {
        this.name = name;
        this.color = color;
        this.radius = radius;
        this.hitboxCollisionMul = hitboxCollisionMul;
        this.id = id;
    }

    public static BulletType register(String name, int color, float radius, float hitboxMul) {
        if (REGISTRY.containsKey(name)) return REGISTRY.get(name);
        int nextId = BY_ID.size();
        BulletType type = new BulletType(name, color, radius, hitboxMul, nextId);
        REGISTRY.put(name, type);
        BY_ID.add(type);
        return type;
    }

    public static BulletType[] values() {
        return BY_ID.toArray(new BulletType[0]);
    }

    public static BulletType fromId(int id) {
        return (id >= 0 && id < BY_ID.size()) ? BY_ID.get(id) : fromName("ORB");
    }

    public static BulletType fromName(String name) {
        BulletType t = REGISTRY.get(name.toUpperCase());
        if (t != null) return t;
        // Fallback for essential types if not yet loaded
        if ("ORB".equalsIgnoreCase(name)) return register("ORB", 0xFFFFFFFF, 4.0f, 1.0f);
        if ("DOT".equalsIgnoreCase(name)) return register("DOT", 0xFFFFFFFF, 3.0f, 0.68f);
        return fromName("ORB");
    }

    public int getId() { return id; }

    /**
     * Player bullet pools: when {@link mc.sayda.bullethell.arena.BulletPool} spawn uses
     * {@code HOMING_USE_TYPE_DEFAULT}, homing steering is enabled for these types (e.g. Reimu amulets).
     */
    public boolean defaultPlayerHomingSteer() {
        return BulletTypeLoader.get(this).homing;
    }

    /**
     * Sakuya time stop: bullets flagged as blades freeze in place.
     */
    public boolean isSakuyaBlade() {
        return BulletTypeLoader.get(this).sakuyaBlade;
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

    @Override
    public String toString() { return name; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BulletType)) return false;
        return id == ((BulletType) obj).id;
    }

    @Override
    public int hashCode() { return id; }
}
