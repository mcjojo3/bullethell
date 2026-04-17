package mc.sayda.bullethell.entity;

import mc.sayda.bullethell.arena.BulletHellManager;
import mc.sayda.bullethell.BossProgression;
import mc.sayda.bullethell.boss.NpcDefinition;
import mc.sayda.bullethell.boss.NpcLoader;
import mc.sayda.bullethell.boss.StageLoader;
import mc.sayda.bullethell.entity.ai.NpcSeekShadeGoal;
import mc.sayda.bullethell.entity.ai.NpcShadeAwareWaterAvoidingRandomStrollGoal;
import mc.sayda.bullethell.item.BHItems;
import mc.sayda.bullethell.network.BHPackets;
import mc.sayda.bullethell.network.OpenChallengePacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A non-hostile NPC that challenges nearby players to a bullet-hell duel.
 *
 * AI goals (in priority order):
 *   0 – Float (stay above water)
 *   1 – Seek shade when bright daytime (if {@link NpcDefinition#seeksShade})
 *   2 – Look at the nearest player within 8 blocks
 *   3 – Random look-around when idle
 *   4 – Slow wander to stay lively
 *
 * Right-clicking sends an {@link OpenChallengePacket} to the player which
 * opens the {@link mc.sayda.bullethell.client.screen.ChallengeScreen}.
 * The NPC never despawns from distance. Default stats match a normal mob ({@code 20} health,
 * {@code 0.2} movement speed, no knockback resistance so melee hits apply knockback). They are killable
 * unless {@link NpcDefinition#invulnerable} is set; on death they drop their spawn egg.
 * Optional {@link NpcDefinition#knockbackOnHit} enables knockback while invulnerable.
 * {@link NpcDefinition#seeksShade} makes Scarlet-style NPCs path toward shade, avoid wandering
 * back into bright sun, and avoids vanilla sun-burn ignition (not lava or campfires).
 */
public class BHNpc extends PathfinderMob {

    private static final EntityDataAccessor<String> NPC_ID =
            SynchedEntityData.defineId(BHNpc.class, EntityDataSerializers.STRING);

    /** The default npcId used when this entity is spawned without NBT data. */
    private final String defaultNpcId;

    public BHNpc(EntityType<? extends BHNpc> type, Level level, String defaultNpcId) {
        super(type, level);
        // super() calls defineSynchedData() before this.defaultNpcId is assigned,
        // so we must set the synched value explicitly here.
        this.defaultNpcId = defaultNpcId;
        this.entityData.set(NPC_ID, defaultNpcId);
        this.setCustomNameVisible(true);
        NpcDefinition def = NpcLoader.load(defaultNpcId);
        if (!def.displayName.isEmpty()) {
            this.setCustomName(Component.literal(def.displayName));
        }
        applyNpcAttributesFromDefinition();
    }

    // ---------------------------------------------------------------- data sync

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        // Use empty string as the initial value - the real ID is applied in the
        // constructor (and restored from NBT by readAdditionalSaveData).
        this.entityData.define(NPC_ID, "");
    }

    public void setNpcId(String id) {
        this.entityData.set(NPC_ID, id);
        refreshNameFromDefinition();
        applyNpcAttributesFromDefinition();
    }

    public String getNpcId() {
        return this.entityData.get(NPC_ID);
    }

    private void refreshNameFromDefinition() {
        NpcDefinition def = NpcLoader.load(getNpcId());
        if (!def.displayName.isEmpty()) {
            this.setCustomName(Component.literal(def.displayName));
        }
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new NpcSeekShadeGoal(this, 0.55));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(4, new NpcShadeAwareWaterAvoidingRandomStrollGoal(this, 0.5));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0);
    }

    private void applyNpcAttributesFromDefinition() {
        NpcDefinition def = NpcLoader.load(getNpcId());
        var kb = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            float v = def.knockbackResistance >= 0f ? def.knockbackResistance : 0.0f;
            kb.setBaseValue(v);
        }
    }

    /**
     * Scarlet NPCs ({@link NpcDefinition#seeksShade}) should not ignite from direct sunlight;
     * lava, campfires, and other fire sources behave normally.
     */
    @Override
    protected boolean isSunBurnTick() {
        if (NpcLoader.load(getNpcId()).seeksShade) {
            return false;
        }
        return super.isSunBurnTick();
    }

    private void applyKnockbackFromAttacker(Entity attacker, double strength) {
        double dx = this.getX() - attacker.getX();
        double dz = this.getZ() - attacker.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) {
            dx = (this.random.nextDouble() - 0.5) * 2.0;
            dz = (this.random.nextDouble() - 0.5) * 2.0;
            len = Math.sqrt(dx * dx + dz * dz);
        }
        this.knockback(strength, dx / len, dz / len);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        NpcDefinition def = NpcLoader.load(getNpcId());
        if (def.invulnerable) {
            if (!this.level().isClientSide && def.knockbackOnHit > 0.0 && source.getDirectEntity() != null) {
                applyKnockbackFromAttacker(source.getDirectEntity(), def.knockbackOnHit);
            }
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (!this.level().isClientSide) {
            ItemStack egg = BHItems.spawnEggStackFor(this.getType());
            if (!egg.isEmpty()) {
                this.spawnAtLocation(egg);
            }
        }
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (BulletHellManager.INSTANCE.getArenaForPlayer(serverPlayer.getUUID()) != null) {
                // Already in an arena - ignore
                return InteractionResult.CONSUME;
            }
            NpcDefinition def = NpcLoader.load(getNpcId());
            String bossId = StageLoader.load(def.stageId).bossId;
            int maxOrd = BossProgression.maxAllowedDifficultyOrdinal(serverPlayer, bossId);
            String req = BossProgression.requirementSummary(bossId);
            BHPackets.sendOpenChallenge(serverPlayer,
                    new OpenChallengePacket(getNpcId(), def.stageId, def.displayName, def.challengeText, maxOrd, req));
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // ---------------------------------------------------------------- despawn / targeting

    @Override
    public boolean removeWhenFarAway(double distSq) {
        return false; // Never despawn
    }

    /** Prevents hostile mob AI (zombies, skeletons, etc.) from selecting this NPC as a target. */
    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    // ---------------------------------------------------------------- NBT

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("NpcId", getNpcId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("NpcId")) {
            setNpcId(tag.getString("NpcId"));
        }
    }
}
