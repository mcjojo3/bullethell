package mc.sayda.bullethell.sound;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads TITLE and ARTIST from Ogg Vorbis comment headers without any
 * third-party library.
 */
public final class OggMetaReader {

    /** Immutable metadata pair extracted from a Vorbis comment header. */
    public record TrackMeta(String title, String artist) {
        public static final TrackMeta EMPTY = new TrackMeta("", "");
    }

    private static final Map<String, TrackMeta> CACHE = new ConcurrentHashMap<>();

    private OggMetaReader() {}

    /**
     * Return metadata for the given track ID (cached after first load).
     * The OGG file is expected at {@code assets/bullethell/sounds/music/<id>.ogg}.
     */
    public static TrackMeta read(String trackId) {
        return CACHE.computeIfAbsent(trackId, OggMetaReader::doRead);
    }

    private static TrackMeta doRead(String trackId) {
        String path = "assets/bullethell/sounds/music/" + trackId + ".ogg";
        InputStream raw = OggMetaReader.class.getClassLoader().getResourceAsStream(path);
        if (raw == null) return TrackMeta.EMPTY;
        try (BufferedInputStream bis = new BufferedInputStream(raw, 65_536)) {
            return parse(bis);
        } catch (Exception e) {
            return TrackMeta.EMPTY;
        }
    }

    private static TrackMeta parse(InputStream is) throws IOException {
        OggDemuxer demux = new OggDemuxer(is);
        byte[] p1 = demux.nextPacket();
        if (p1 == null) return TrackMeta.EMPTY;
        byte[] p2 = demux.nextPacket();
        if (p2 == null || p2.length < 7) return TrackMeta.EMPTY;
        if (p2[0] != 0x03 || p2[1] != 'v' || p2[2] != 'o'
                || p2[3] != 'r' || p2[4] != 'b' || p2[5] != 'i' || p2[6] != 's')
            return TrackMeta.EMPTY;
        return parseComments(p2, 7);
    }

    private static TrackMeta parseComments(byte[] data, int off) {
        if (off + 4 > data.length) return TrackMeta.EMPTY;
        int vendorLen = le32(data, off); off += 4; off += vendorLen;
        if (off + 4 > data.length) return TrackMeta.EMPTY;
        int count = le32(data, off); off += 4;
        String title = "", artist = "";
        for (int i = 0; i < count && off + 4 <= data.length; i++) {
            int len = le32(data, off); off += 4;
            if (len < 0 || off + len > data.length) break;
            String kv = new String(data, off, len, StandardCharsets.UTF_8); off += len;
            int eq = kv.indexOf('=');
            if (eq > 0) {
                String key = kv.substring(0, eq).toUpperCase(Locale.ROOT);
                String val = kv.substring(eq + 1).trim();
                if (key.equals("TITLE"))  title  = val;
                if (key.equals("ARTIST")) artist = val;
            }
        }
        return new TrackMeta(title, artist);
    }

    private static int le32(byte[] d, int o) {
        return (d[o] & 0xFF) | ((d[o+1] & 0xFF) << 8) | ((d[o+2] & 0xFF) << 16) | ((d[o+3] & 0xFF) << 24);
    }

    private static final class OggDemuxer {
        private final InputStream is;
        private final List<byte[]> segs = new ArrayList<>();
        private int segIdx = 0;

        OggDemuxer(InputStream is) { this.is = is; }

        byte[] nextPacket() throws IOException {
            ByteArrayOutputStream pkt = new ByteArrayOutputStream(4096);
            while (true) {
                if (segIdx >= segs.size()) {
                    if (!loadPage()) return pkt.size() > 0 ? pkt.toByteArray() : null;
                }
                byte[] seg = segs.get(segIdx++);
                pkt.write(seg);
                if (seg.length < 255) return pkt.toByteArray();
            }
        }

        private boolean loadPage() throws IOException {
            byte[] cap = is.readNBytes(4);
            if (cap.length < 4 || cap[0] != 'O' || cap[1] != 'g' || cap[2] != 'g' || cap[3] != 'S')
                return false;
            is.skipNBytes(22);
            int nSegs = is.read();
            if (nSegs < 0) return false;
            byte[] table = is.readNBytes(nSegs);
            segs.clear(); segIdx = 0;
            for (byte b : table) segs.add(is.readNBytes(b & 0xFF));
            return true;
        }
    }
}
