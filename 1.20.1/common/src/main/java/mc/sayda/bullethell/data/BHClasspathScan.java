package mc.sayda.bullethell.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.architectury.platform.Platform;
import mc.sayda.bullethell.Bullethell;

import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Lists content ids straight off the mod's own files, before any datapack exists.
 *
 * Needed only for content that drives a <em>registry</em> - entity types and spawn eggs
 * are populated at mod-init and frozen long before datapacks load, and registering
 * something the client's jar does not also have would desync. Everything else should go
 * through {@link BHDataManager} instead, which is datapack-aware.
 *
 * <h2>Why an index file</h2>
 * Listing the contents of a classpath <em>directory</em> is not reliable at mod-init.
 * Forge serves mod resources from a SecureJar {@code union:} filesystem whose URLs are
 * neither {@code file:} nor {@code jar:}, so a class-loader directory scan silently
 * finds nothing; and in a dev run {@code Platform.findResource} does not reach the
 * common subproject's resources either. Both failed together, which is why Forge
 * registered zero NPCs.
 *
 * Reading <em>one file by exact path</em> has none of those problems - it works on every
 * loader, in dev and from a built jar. So the list is baked at build time into
 * {@code data/bullethell/indexes/<folder>.json} by the {@code generateNpcIndex} Gradle
 * task and simply read back here. Adding an NPC is still just adding its json; the index
 * is regenerated on every build.
 *
 * The two directory scans are kept below as fallbacks for anyone running from a source
 * tree without the generated resource.
 */
public final class BHClasspathScan {

    private BHClasspathScan() {}

    /** Basenames of {@code data/bullethell/<folder>/*.json} in the mod's files, sorted. */
    public static List<String> ids(String folder) {
        Set<String> out = new LinkedHashSet<>();

        readIndex(folder, out);
        if (out.isEmpty()) scanModFile(folder, out);
        if (out.isEmpty()) scanClassLoader(folder, out);

        if (out.isEmpty()) {
            // Loud, because the symptom otherwise is "the feature just isn't there".
            Bullethell.LOGGER.error(
                    "[BulletHell] No ids for data/{}/{}/ - index data/{}/indexes/{}.json is missing "
                            + "and directory scanning found nothing. Anything registered from it "
                            + "(NPC entity types, spawn eggs) will be absent. Run "
                            + "'gradlew :1.20.1:common:generateNpcIndex'.",
                    Bullethell.MODID, folder, Bullethell.MODID, folder);
        }
        return sorted(out);
    }

    /** Primary path: the build-time index, read as a single file. */
    private static void readIndex(String folder, Set<String> out) {
        String path = "data/" + Bullethell.MODID + "/indexes/" + folder + ".json";
        try {
            JsonElement el = BHJsonFiles.readFromClasspathRaw(path);
            if (el == null || !el.isJsonObject()) return;
            JsonObject obj = el.getAsJsonObject();
            if (!obj.has("ids") || !obj.get("ids").isJsonArray()) return;
            JsonArray arr = obj.getAsJsonArray("ids");
            for (JsonElement e : arr) {
                String id = e.getAsString();
                if (id != null && !id.isBlank()) out.add(id);
            }
        } catch (Exception e) {
            Bullethell.LOGGER.warn("[BulletHell] Could not read index {}: {}", path, e.toString());
        }
    }

    /** Fallback: works on Fabric jars and exploded dev classpaths. */
    private static void scanModFile(String folder, Set<String> out) {
        try {
            Optional<Path> dir = Platform.getMod(Bullethell.MODID)
                    .findResource("data", Bullethell.MODID, folder);
            if (dir.isEmpty() || !Files.isDirectory(dir.get())) return;
            collectJsonNames(dir.get(), out);
        } catch (Exception e) {
            Bullethell.LOGGER.warn("[BulletHell] Mod-file scan failed for {}: {}", folder, e.toString());
        }
    }

    private static void collectJsonNames(Path dir, Set<String> out) throws Exception {
        try (var stream = Files.list(dir)) {
            stream.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .forEach(out::add);
        }
    }

    private static void scanClassLoader(String folder, Set<String> out) {
        String prefix = "data/" + Bullethell.MODID + "/" + folder + "/";
        try {
            Enumeration<URL> roots = BHClasspathScan.class.getClassLoader().getResources(prefix);
            while (roots.hasMoreElements()) {
                URL url = roots.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    Path dir = Paths.get(url.toURI());
                    if (Files.isDirectory(dir)) collectJsonNames(dir, out);
                } else if ("jar".equals(protocol)) {
                    scanJar(url, prefix, out);
                }
            }
        } catch (Exception e) {
            Bullethell.LOGGER.warn("[BulletHell] Class-loader scan failed for {}: {}", folder, e.toString());
        }
    }

    private static void scanJar(URL url, String prefix, Set<String> out) throws Exception {
        JarURLConnection conn = (JarURLConnection) url.openConnection();
        try (JarFile jar = conn.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                String name = e.getName();
                if (e.isDirectory() || !name.startsWith(prefix) || !name.endsWith(".json")) continue;
                String rest = name.substring(prefix.length());
                if (rest.indexOf('/') >= 0) continue;
                out.add(rest.substring(0, rest.length() - ".json".length()));
            }
        }
    }

    private static List<String> sorted(Set<String> out) {
        List<String> list = new ArrayList<>(out);
        Collections.sort(list);
        return list;
    }
}
