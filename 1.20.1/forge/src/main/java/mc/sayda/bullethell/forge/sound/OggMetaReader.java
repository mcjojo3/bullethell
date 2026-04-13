package mc.sayda.bullethell.forge.sound;

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
 *
 * Vorbis comment format (RFC 5215 / Vorbis I spec §5.2):
 *   packet type   : 1 byte  (0x03)
 *   magic         : 6 bytes ("vorbis")
 *   vendor length : 4 bytes LE uint32
 *   vendor string : N bytes UTF-8
 *   comment count : 4 bytes LE uint32
 *   comments[]    : (length LE + "KEY=VALUE" UTF-8) × count
 *
 * OGG framing: packets are assembled from segments across pages.
 * A segment shorter than 255 bytes marks the end of a packet.
 */
public final class OggMetaReader {

    /** Immutable metadata pair extracted from a Vorbis comment header. */
    public record TrackMeta(String title, String artist) {
        public static final TrackMeta EMPTY = new TrackMeta("", "");
        public boolean hasInfo() { return !title.isEmpty() || !artist.isEmpty(); }
    }

    private static final Map<String, TrackMeta> CACHE = new ConcurrentHashMap<>();

    private OggMetaReader() {}

    /**
     * Return metadata for the given track ID (cached after first load).
     * The OGG file is expected at {@code assets/bullethell/sounds/<id>.ogg}
     * on the classpath.
     */
    public static TrackMeta read(String trackId) {
        return CACHE.computeIfAbsent(trackId, OggMetaReader::doRead);
    }

    // ---------------------------------------------------------------- loading

    private static TrackMeta doRead(String trackId) {
        String path = "assets/bullethell/sounds/" + trackId + ".ogg";
        InputStream raw = OggMetaReader.class.getClassLoader().getResourceAsStream(path);
        if (raw == null) return TrackMeta.EMPTY;
        // Buffering is important — we read many small chunks from the page headers.
        try (BufferedInputStream bis = new BufferedInputStream(raw, 65_536)) {
            return parse(bis);
        } catch (Exception e) {
            return TrackMeta.EMPTY;
        }
    }

    private static TrackMeta parse(InputStream is) throws IOException {
        OggDemuxer demux = new OggDemuxer(is);
        byte[] p1 = demux.nextPacket(); // identification header — skip
        if (p1 == null) return TrackMeta.EMPTY;
        byte[] p2 = demux.nextPacket(); // comment header
        if (p2 == null || p2.length < 7) return TrackMeta.EMPTY;
        // Verify Vorbis comment magic: 0x03 "vorbis"
        if (p2[0] != 0x03 || p2[1] != 'v' || p2[2] != 'o'
                || p2[3] != 'r' || p2[4] != 'b' || p2[5] != 'i' || p2[6] != 's') {
            return TrackMeta.EMPTY;
        }
        return parseComments(p2, 7);
    }

    // ---------------------------------------------------------------- comment parsing

    private static TrackMeta parseComments(byte[] data, int off) {
        if (off + 4 > data.length) return TrackMeta.EMPTY;
        int vendorLen = le32(data, off); off += 4;
        off += vendorLen; // skip vendor string
        if (off + 4 > data.length) return TrackMeta.EMPTY;
        int count = le32(data, off); off += 4;

        String title = "", artist = "";
        for (int i = 0; i < count && off + 4 <= data.length; i++) {
            int len = le32(data, off); off += 4;
            if (len < 0 || off + len > data.length) break;
            String kv  = new String(data, off, len, StandardCharsets.UTF_8);
            off += len;
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
        return (d[o] & 0xFF)
             | ((d[o + 1] & 0xFF) <<  8)
             | ((d[o + 2] & 0xFF) << 16)
             | ((d[o + 3] & 0xFF) << 24);
    }

    // ---------------------------------------------------------------- minimal OGG demuxer

    /**
     * Assembles Vorbis packets from OGG pages.
     *
     * OGG page header layout (after the 4-byte "OggS" capture pattern):
     *   1  version
     *   1  header_type (bits: 0=continued, 1=bos, 2=eos)
     *   8  granule_position (LE int64)
     *   4  bitstream_serial (LE int32)
     *   4  page_sequence_no (LE int32)
     *   4  crc_checksum     (LE int32)
     *   1  page_segments
     *   N  segment_table    (each byte = segment length; 255 = continuation)
     *   …  segment data
     *
     * A packet ends when its last segment is < 255 bytes (including 0).
     */
    private static final class OggDemuxer {
        private final InputStream is;
        private final List<byte[]> segs = new ArrayList<>();
        private int segIdx = 0;

        OggDemuxer(InputStream is) { this.is = is; }

        byte[] nextPacket() throws IOException {
            ByteArrayOutputStream pkt = new ByteArrayOutputStream(4096);
            while (true) {
                // Refill page when current one is exhausted
                if (segIdx >= segs.size()) {
                    if (!loadPage()) {
                        return pkt.size() > 0 ? pkt.toByteArray() : null;
                    }
                }
                byte[] seg = segs.get(segIdx++);
                pkt.write(seg);
                // Segment < 255 bytes → packet complete
                if (seg.length < 255) return pkt.toByteArray();
            }
        }

        private boolean loadPage() throws IOException {
            // Expect capture pattern "OggS"
            byte[] cap = is.readNBytes(4);
            if (cap.length < 4 || cap[0] != 'O' || cap[1] != 'g'
                    || cap[2] != 'g' || cap[3] != 'S') return false;
            // Skip: version(1) + header_type(1) + granule(8) + serial(4) + seqno(4) + crc(4) = 22
            is.skipNBytes(22);
            int nSegs = is.read();
            if (nSegs < 0) return false;
            byte[] table = is.readNBytes(nSegs);

            segs.clear();
            segIdx = 0;
            for (byte b : table) {
                int len = b & 0xFF;
                segs.add(is.readNBytes(len));
            }
            return true;
        }
    }
}
