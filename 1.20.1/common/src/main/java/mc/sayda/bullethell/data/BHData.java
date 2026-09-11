package mc.sayda.bullethell.data;

import dev.architectury.registry.ReloadListenerRegistry;
import mc.sayda.bullethell.boss.BossDefinition;
import mc.sayda.bullethell.boss.BossLoader;
import mc.sayda.bullethell.boss.CharacterDefinition;
import mc.sayda.bullethell.boss.CharacterLoader;
import mc.sayda.bullethell.boss.NpcDefinition;
import mc.sayda.bullethell.boss.NpcLoader;
import mc.sayda.bullethell.boss.StageDefinition;
import mc.sayda.bullethell.boss.StageLoader;
import net.minecraft.server.packs.PackType;

import java.util.List;

/**
 * Every datapack-backed content folder in the mod.
 *
 * Adding a folder here is all it takes for its json to become discoverable,
 * datapack-overridable and client-synced - no per-id registration anywhere.
 */
public final class BHData {

    public static final BHDataManager<CharacterDefinition> CHARACTERS =
            new BHDataManager<>("characters", CharacterLoader::parse);

    public static final BHDataManager<BossDefinition> BOSSES =
            new BHDataManager<>("bosses", BossLoader::parse);

    public static final BHDataManager<StageDefinition> STAGES =
            new BHDataManager<>("stages", StageLoader::parse);

    public static final BHDataManager<NpcDefinition> NPCS =
            new BHDataManager<>("npcs", NpcLoader::parse);

    /**
     * Registry-style single file: top-level keys merge across packs, so a datapack can
     * retune one bullet type without restating the file.
     */
    public static final BHMergedJsonManager BULLET_TYPES =
            new BHMergedJsonManager("bullet_types.json",
                    mc.sayda.bullethell.pattern.BulletTypeLoader::invalidate);

    /** Every folder manager, in sync order. */
    public static final List<BHDataManager<?>> ALL = List.of(CHARACTERS, BOSSES, STAGES, NPCS);

    private BHData() {}

    public static void register() {
        for (BHDataManager<?> m : ALL) {
            ReloadListenerRegistry.register(PackType.SERVER_DATA, m);
        }
        ReloadListenerRegistry.register(PackType.SERVER_DATA, BULLET_TYPES);
        // Registered last so it runs after every manager has ingested: one broadcast
        // per reload rather than one per folder.
        ReloadListenerRegistry.register(PackType.SERVER_DATA, new SyncBroadcaster());
    }

    /** Pushes the freshly reloaded content to everyone online. */
    private static final class SyncBroadcaster
            implements net.minecraft.server.packs.resources.ResourceManagerReloadListener {
        @Override
        public void onResourceManagerReload(
                @javax.annotation.Nonnull net.minecraft.server.packs.resources.ResourceManager resourceManager) {
            mc.sayda.bullethell.network.BHPackets.sendDataSyncToAll(
                    dev.architectury.utils.GameInstance.getServer());
        }
    }

    public static BHDataManager<?> byFolder(String folder) {
        for (BHDataManager<?> m : ALL) {
            if (m.folder().equals(folder)) return m;
        }
        return null;
    }
}
