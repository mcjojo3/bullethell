package mc.sayda.bullethell.entity;

import mc.sayda.bullethell.arena.BulletHellManager;
import mc.sayda.bullethell.boss.NpcDefinition;
import mc.sayda.bullethell.boss.NpcLoader;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A non-hostile NPC that challenges nearby players to a bullet-hell duel.
 *
 * AI goals (in priority order):
 *   0 – Float (stay above water)
 *   1 – Look at the nearest player within 8 blocks
 *   2 – Random look-around when idle
 *   3 – Slow wander to stay lively
 *
 * Right-clicking sends an {@link OpenChallengePacket} to the player which
 * opens the {@link mc.sayda.bullethell.client.screen.ChallengeScreen}.
 * The NPC never despawns and cannot be hurt.
 */
public class BHNpc extends PathfinderMob {

    private static final EntityDataAccessor<String> NPC_ID =
            SynchedEntityData.defineId(BHNpc.class, EntityDataSerializers.STRING);

    /** The default npcId used when this entity is spawned without NBT data. */
    private final String defaultNpcId;

    public BHNpc(EntityType<? extends BHNpc> type, Level level, String defaultNpcId) {
        super(type, level);
        this.defaultNpcId = defaultNpcId;
        this.setCustomNameVisible(true);
        this.setInvulnerable(true);
        // Set display name immediately from the NPC definition
        NpcDefinition def = NpcLoader.load(defaultNpcId);
        if (!def.displayName.isEmpty()) {
            this.setCustomName(net.minecraft.network.chat.Component.literal(def.displayName));
        }
    }

    // ---------------------------------------------------------------- data sync

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(NPC_ID, defaultNpcId);
    }

    public void setNpcId(String id) {
        this.entityData.set(NPC_ID, id);
        refreshNameFromDefinition();
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
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.5));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (BulletHellManager.INSTANCE.getArenaForPlayer(serverPlayer.getUUID()) != null) {
                // Already in an arena — ignore
                return InteractionResult.CONSUME;
            }
            NpcDefinition def = NpcLoader.load(getNpcId());
            BHPackets.sendOpenChallenge(serverPlayer,
                    new OpenChallengePacket(getNpcId(), def.stageId, def.displayName, def.challengeText));
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // ---------------------------------------------------------------- despawn / hurt

    @Override
    public boolean removeWhenFarAway(double distSq) {
        return false; // Never despawn
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false; // Invulnerable to all damage
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
