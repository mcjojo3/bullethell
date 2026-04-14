package mc.sayda.bullethell.arena;

/** Server-to-client game events that trigger screen FX and audio cues. */
public enum GameEvent {
    HIT,            // player hit (death-bomb window opened)
    DEATH,          // player death confirmed
    GRAZE_CHAIN,    // graze chain milestone
    GRAZE,          // continuous graze (visual/audio)
    PHASE_CHANGE,   // boss entered a new phase
    BOMB_USED,      // bomb activated
    ITEM_PICKUP,    // item collected
    SPELL_CAPTURED, // spellcard cleared without dying/bombing
    SPELL_FAILED,   // spellcard timer expired or player died during spell
    SKILL_USED;     // character special ability activated (non-bomb)

    private static final GameEvent[] VALUES = values();
    public static GameEvent fromId(int id) {
        return (id >= 0 && id < VALUES.length) ? VALUES[id] : HIT;
    }
}
