package mc.sayda.bullethell.forge.network;

import mc.sayda.bullethell.Bullethell;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Central network channel - register once during mod init. */
public final class BHNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Bullethell.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int nextId = 0;

    private BHNetwork() {}

    public static void register() {
        // ---- S → C ----
        CHANNEL.registerMessage(nextId++, BulletDeltaPacket.class,
                BulletDeltaPacket::encode, BulletDeltaPacket::decode, BulletDeltaPacket::handle);

        CHANNEL.registerMessage(nextId++, BulletFullSyncPacket.class,
                BulletFullSyncPacket::encode, BulletFullSyncPacket::decode, BulletFullSyncPacket::handle);

        CHANNEL.registerMessage(nextId++, ArenaStatePacket.class,
                ArenaStatePacket::encode, ArenaStatePacket::decode, ArenaStatePacket::handle);

        CHANNEL.registerMessage(nextId++, GameEventPacket.class,
                GameEventPacket::encode, GameEventPacket::decode, GameEventPacket::handle);

        CHANNEL.registerMessage(nextId++, PlayerBulletSyncPacket.class,
                PlayerBulletSyncPacket::encode, PlayerBulletSyncPacket::decode, PlayerBulletSyncPacket::handle);

        CHANNEL.registerMessage(nextId++, ItemSyncPacket.class,
                ItemSyncPacket::encode, ItemSyncPacket::decode, ItemSyncPacket::handle);

        CHANNEL.registerMessage(nextId++, EnemySyncPacket.class,
                EnemySyncPacket::encode, EnemySyncPacket::decode, EnemySyncPacket::handle);

        // ---- S → C (character select) ----
        CHANNEL.registerMessage(nextId++, OpenCharacterSelectPacket.class,
                OpenCharacterSelectPacket::encode, OpenCharacterSelectPacket::decode, OpenCharacterSelectPacket::handle);

        // ---- S → C (co-op) ----
        CHANNEL.registerMessage(nextId++, CoopPlayersSyncPacket.class,
                CoopPlayersSyncPacket::encode, CoopPlayersSyncPacket::decode, CoopPlayersSyncPacket::handle);

        CHANNEL.registerMessage(nextId++, LaserSyncPacket.class,
                LaserSyncPacket::encode, LaserSyncPacket::decode, LaserSyncPacket::handle);

        CHANNEL.registerMessage(nextId++, OpenJoinSelectPacket.class,
                OpenJoinSelectPacket::encode, OpenJoinSelectPacket::decode, OpenJoinSelectPacket::handle);

        // ---- C → S ----
        CHANNEL.registerMessage(nextId++, PlayerPos2DPacket.class,
                PlayerPos2DPacket::encode, PlayerPos2DPacket::decode, PlayerPos2DPacket::handle);

        CHANNEL.registerMessage(nextId++, BombPacket.class,
                BombPacket::encode, BombPacket::decode, BombPacket::handle);

        CHANNEL.registerMessage(nextId++, CharacterSelectPacket.class,
                CharacterSelectPacket::encode, CharacterSelectPacket::decode, CharacterSelectPacket::handle);

        CHANNEL.registerMessage(nextId++, JoinMatchPacket.class,
                JoinMatchPacket::encode, JoinMatchPacket::decode, JoinMatchPacket::handle);
    }
}
