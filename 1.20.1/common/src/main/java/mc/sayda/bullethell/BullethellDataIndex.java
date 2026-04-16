package mc.sayda.bullethell;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import mc.sayda.bullethell.boss.BossLoader;
import mc.sayda.bullethell.boss.CharacterLoader;
import mc.sayda.bullethell.boss.StageLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Discovers {@code data/bullethell/&lt;subdir&gt;/*.json} basenames (used for broad tooling).
 * {@code /bullethell start} uses {@link #suggestStartArenaTargets()} (stages + bosses only).
 */
public final class BullethellDataIndex {

    private static final String[] PREFIXES = {
            "data/bullethell/stages/",
            "data/bullethell/bosses/",
            "data/bullethell/characters/",
            "data/bullethell/npcs/",
            "data/bullethell/fairy_waves/",
    };

    private static final String[] SAMPLE_FILES = {
            "data/bullethell/stages/marisa_stage.json",
            "data/bullethell/bosses/marisa_boss.json",
            "data/bullethell/characters/reimu.json",
            "data/bullethell/npcs/marisa_npc.json",
            "data/bullethell/fairy_waves/mixed_cross_top.json",
    };

    private static List<String> cachedSorted;

    private BullethellDataIndex() {
    }

    public static synchronized List<String> allJsonIdsSorted() {
        if (cachedSorted != null)
            return cachedSorted;
        LinkedHashSet<String> set = new LinkedHashSet<>();
        ClassLoader cl = Bullethell.class.getClassLoader();
        for (int i = 0; i < PREFIXES.length; i++) {
            collectFromPrefix(cl, PREFIXES[i], SAMPLE_FILES[i], set);
        }
        Collections.addAll(set, StageLoader.REGISTERED_IDS);
        cachedSorted = new ArrayList<>(set);
        Collections.sort(cachedSorted);
        return cachedSorted;
    }

    private static void collectFromPrefix(ClassLoader cl, String prefix, String sampleFile, Set<String> out) {
        URL anchor = cl.getResource(sampleFile);
        if (anchor == null)
            return;
        try {
            if ("file".equals(anchor.getProtocol())) {
                Path p = Paths.get(anchor.toURI());
                Path dir = p.getParent();
                if (dir != null && Files.isDirectory(dir)) {
                    try (var stream = Files.list(dir)) {
                        stream.filter(x -> x.toString().endsWith(".json"))
                                .forEach(x -> out.add(nameWithoutJson(x.getFileName().toString())));
                    }
                }
            } else {
                scanJar(anchor, prefix, out);
            }
        } catch (Exception ignored) {
        }
    }

    private static void scanJar(URL anchor, String prefix, Set<String> out) throws Exception {
        java.net.JarURLConnection conn = (java.net.JarURLConnection) anchor.openConnection();
        try (JarFile jar = conn.getJarFile()) {
            Enumeration<JarEntry> en = jar.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String name = e.getName();
                if (!name.startsWith(prefix) || !name.endsWith(".json") || e.isDirectory())
                    continue;
                String rest = name.substring(prefix.length());
                if (rest.indexOf('/') >= 0)
                    continue;
                out.add(nameWithoutJson(rest));
            }
        }
    }

    private static String nameWithoutJson(String name) {
        return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    public static List<String> characterIdsSorted() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.addAll(Arrays.asList(CharacterLoader.REGISTERED_IDS));
        ClassLoader cl = Bullethell.class.getClassLoader();
        collectFromPrefix(cl, "data/bullethell/characters/", "data/bullethell/characters/reimu.json", set);
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }

    /** Basenames of {@code data/bullethell/bosses/*.json} (always includes {@link BossLoader#REGISTERED_IDS}). */
    public static List<String> bossIdsSorted() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        Collections.addAll(set, BossLoader.REGISTERED_IDS);
        ClassLoader cl = BossLoader.class.getClassLoader();
        collectFromPrefix(cl, "data/bullethell/bosses/", "data/bullethell/bosses/marisa_boss.json", set);
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }

    /**
     * Stage ids + boss ids for {@code /bullethell start &lt;target&gt;} (no characters, npcs, or fairy_waves).
     */
    public static List<String> startArenaTargetIdsSorted() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        Collections.addAll(set, StageLoader.REGISTERED_IDS);
        ClassLoader stageCl = StageLoader.class.getClassLoader();
        collectFromPrefix(stageCl, "data/bullethell/stages/", "data/bullethell/stages/marisa_stage.json", set);
        set.addAll(bossIdsSorted());
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }

    public static SuggestionProvider<CommandSourceStack> suggestTargets() {
        return (ctx, builder) -> SharedSuggestionProvider.suggest(allJsonIdsSorted(), builder);
    }

    public static SuggestionProvider<CommandSourceStack> suggestStartArenaTargets() {
        return (ctx, builder) -> SharedSuggestionProvider.suggest(startArenaTargetIdsSorted(), builder);
    }

    public static SuggestionProvider<CommandSourceStack> suggestCharacters() {
        return (ctx, builder) -> SharedSuggestionProvider.suggest(characterIdsSorted(), builder);
    }
}
