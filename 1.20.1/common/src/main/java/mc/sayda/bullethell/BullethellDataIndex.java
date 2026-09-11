package mc.sayda.bullethell;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import mc.sayda.bullethell.boss.BossLoader;
import mc.sayda.bullethell.boss.CharacterLoader;
import mc.sayda.bullethell.boss.NpcLoader;
import mc.sayda.bullethell.boss.StageLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Command tab-completion over the loaded content ids.
 *
 * Ids come straight from the datapack-backed managers, so suggestions include
 * anything a datapack added and drop anything it removed - no scanning here.
 */
public final class BullethellDataIndex {

    private BullethellDataIndex() {
    }

    private static List<String> sorted(java.util.Collection<String> ids) {
        List<String> list = new ArrayList<>(ids);
        Collections.sort(list);
        return list;
    }

    public static List<String> allJsonIdsSorted() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.addAll(StageLoader.allStageIds());
        set.addAll(BossLoader.allBossIds());
        set.addAll(CharacterLoader.allCharIds());
        set.addAll(NpcLoader.allNpcIds());
        return sorted(set);
    }

    public static List<String> characterIdsSorted() {
        return sorted(CharacterLoader.allCharIds());
    }

    public static List<String> bossIdsSorted() {
        return sorted(BossLoader.allBossIds());
    }

    /** Stage ids + boss ids for {@code /bullethell start &lt;target&gt;}. */
    public static List<String> startArenaTargetIdsSorted() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.addAll(StageLoader.allStageIds());
        set.addAll(BossLoader.allBossIds());
        return sorted(set);
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
