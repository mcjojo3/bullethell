package mc.sayda.bullethell.network;

import mc.sayda.bullethell.data.BHData;
import mc.sayda.bullethell.data.BHDataManager;
import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S → C | raw content json for every {@link BHData} folder.
 *
 * Sent on datapack reload and on request, so clients see exactly the entries the
 * server resolved - including datapack additions and overrides the client's own jar
 * knows nothing about. Raw json is shipped rather than parsed fields so both sides
 * run the identical parser and cannot drift.
 */
public class DataSyncPacket {

    /** folder → (id → raw json). */
    public final Map<String, Map<String, String>> folders;
    /** Merged registry-style single files, by file name. */
    public final Map<String, String> mergedFiles;

    public DataSyncPacket(Map<String, Map<String, String>> folders, Map<String, String> mergedFiles) {
        this.folders = folders;
        this.mergedFiles = mergedFiles;
    }

    public static DataSyncPacket fromCurrent() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (BHDataManager<?> m : BHData.ALL) {
            out.put(m.folder(), m.rawForSync());
        }
        Map<String, String> merged = new LinkedHashMap<>();
        merged.put(BHData.BULLET_TYPES.fileName(), BHData.BULLET_TYPES.rawForSync());
        return new DataSyncPacket(out, merged);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(folders.size());
        folders.forEach((folder, entries) -> {
            buf.writeUtf(folder);
            buf.writeVarInt(entries.size());
            entries.forEach((id, json) -> {
                buf.writeUtf(id);
                buf.writeUtf(json, 1048576);
            });
        });
        buf.writeVarInt(mergedFiles.size());
        mergedFiles.forEach((name, json) -> {
            buf.writeUtf(name);
            buf.writeUtf(json, 1048576);
        });
    }

    public static DataSyncPacket decode(FriendlyByteBuf buf) {
        int folderCount = buf.readVarInt();
        Map<String, Map<String, String>> folders = new LinkedHashMap<>();
        for (int i = 0; i < folderCount; i++) {
            String folder = buf.readUtf();
            int n = buf.readVarInt();
            Map<String, String> entries = new LinkedHashMap<>();
            for (int j = 0; j < n; j++) {
                String entryId = buf.readUtf();
                entries.put(entryId, buf.readUtf(1048576));
            }
            folders.put(folder, entries);
        }
        int mergedCount = buf.readVarInt();
        Map<String, String> merged = new LinkedHashMap<>();
        for (int i = 0; i < mergedCount; i++) {
            String name = buf.readUtf();
            merged.put(name, buf.readUtf(1048576));
        }
        return new DataSyncPacket(folders, merged);
    }

    /** Client side: hand each folder's json to its manager. */
    public void apply() {
        folders.forEach((folder, entries) -> {
            BHDataManager<?> m = BHData.byFolder(folder);
            if (m != null) m.acceptSync(entries);
        });
        mergedFiles.forEach((name, json) -> {
            if (BHData.BULLET_TYPES.fileName().equals(name)) BHData.BULLET_TYPES.acceptSync(json);
        });
    }
}
