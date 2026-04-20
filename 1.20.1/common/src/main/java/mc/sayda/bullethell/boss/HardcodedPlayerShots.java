package mc.sayda.bullethell.boss;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/**
 * Fallback shot layout when {@link CharacterDefinition#shotOptions} is missing or empty
 * after loading {@code data/bullethell/characters/&lt;id&gt;.json}.
 */
final class HardcodedPlayerShots {

    private static final Gson GSON = new Gson();
    private static List<PlayerShotOptionJson> genericCache;

    private HardcodedPlayerShots() {}

    static List<PlayerShotOptionJson> genericCopy() {
        if (genericCache == null) {
            PlayerShotsFileDefinition def = GSON.fromJson(GENERIC_JSON, PlayerShotsFileDefinition.class);
            genericCache = def != null && def.shotOptions != null ? def.shotOptions : List.of();
        }
        return new ArrayList<>(genericCache);
    }

    /** Mirrors former {@code data/bullethell/player_shots/generic.json}. */
    private static final String GENERIC_JSON = """
            {
              "shotOptions": [
                {
                  "label": "Default",
                  "description": "Fallback green spread when no shotOptions are defined on the character.",
                  "unfocused": {
                    "powerTiers": [
                      [
                        { "bulletType": "PLAYER_SHOT", "offsetX": 0, "offsetY": 0, "vx": 0, "vy": -16, "lifetime": 55 }
                      ],
                      [
                        { "bulletType": "PLAYER_SHOT", "offsetX": 0, "offsetY": 0, "vx": 0, "vy": -16, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": -8, "offsetY": 0, "vx": -1.6, "vy": -16, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 8, "offsetY": 0, "vx": 1.6, "vy": -16, "lifetime": 55 }
                      ],
                      [
                        { "bulletType": "PLAYER_SHOT", "offsetX": 0, "offsetY": 0, "vx": 0, "vy": -16, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": -10, "offsetY": 0, "vx": -2, "vy": -16, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 10, "offsetY": 0, "vx": 2, "vy": -16, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": -22, "offsetY": 0, "vx": -4.4, "vy": -14, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 22, "offsetY": 0, "vx": 4.4, "vy": -14, "lifetime": 55 }
                      ],
                      [
                        { "bulletType": "PLAYER_SHOT", "offsetX": 0, "offsetY": 0, "vx": 0, "vy": -16, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": -10, "offsetY": 0, "vx": -2, "vy": -16, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 10, "offsetY": 0, "vx": 2, "vy": -16, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": -22, "offsetY": 0, "vx": -4.4, "vy": -14, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 22, "offsetY": 0, "vx": 4.4, "vy": -14, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 0, "offsetY": 7, "vx": 0, "vy": -15.5, "lifetime": 55 }
                      ],
                      [
                        { "bulletType": "PLAYER_SHOT", "offsetX": 0, "offsetY": 0, "vx": 0, "vy": -16, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": -10, "offsetY": 0, "vx": -2, "vy": -16, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 10, "offsetY": 0, "vx": 2, "vy": -16, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": -22, "offsetY": 0, "vx": -4.4, "vy": -14, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 22, "offsetY": 0, "vx": 4.4, "vy": -14, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 0, "offsetY": 7, "vx": 0, "vy": -15.5, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": -34, "offsetY": 0, "vx": -6.5, "vy": -12, "lifetime": 55 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 34, "offsetY": 0, "vx": 6.5, "vy": -12, "lifetime": 55 }
                      ]
                    ]
                  },
                  "focused": {
                    "powerTiers": [
                      [
                        { "bulletType": "PLAYER_SHOT", "offsetX": 0, "offsetY": 0, "vx": 0, "vy": -20, "lifetime": 45 }
                      ],
                      [
                        { "bulletType": "PLAYER_SHOT", "offsetX": 0, "offsetY": 0, "vx": 0, "vy": -20, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": -5, "offsetY": 0, "vx": 0, "vy": -18, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 5, "offsetY": 0, "vx": 0, "vy": -18, "lifetime": 45 }
                      ],
                      [
                        { "bulletType": "PLAYER_SHOT", "offsetX": 0, "offsetY": 0, "vx": 0, "vy": -20, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": -5, "offsetY": 0, "vx": 0, "vy": -20, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 5, "offsetY": 0, "vx": 0, "vy": -20, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": -13, "offsetY": 0, "vx": 0, "vy": -18, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 13, "offsetY": 0, "vx": 0, "vy": -18, "lifetime": 45 }
                      ],
                      [
                        { "bulletType": "PLAYER_SHOT", "offsetX": 0, "offsetY": 0, "vx": 0, "vy": -20, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": -5, "offsetY": 0, "vx": 0, "vy": -20, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 5, "offsetY": 0, "vx": 0, "vy": -20, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": -13, "offsetY": 0, "vx": 0, "vy": -18, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 13, "offsetY": 0, "vx": 0, "vy": -18, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 0, "offsetY": 7, "vx": 0, "vy": -19.5, "lifetime": 45 }
                      ],
                      [
                        { "bulletType": "PLAYER_SHOT", "offsetX": 0, "offsetY": 0, "vx": 0, "vy": -20, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": -5, "offsetY": 0, "vx": 0, "vy": -20, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 5, "offsetY": 0, "vx": 0, "vy": -20, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": -13, "offsetY": 0, "vx": 0, "vy": -18.5, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 13, "offsetY": 0, "vx": 0, "vy": -18.5, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 0, "offsetY": 7, "vx": 0, "vy": -19.5, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": -20, "offsetY": 0, "vx": 0, "vy": -17.5, "lifetime": 45 },
                        { "bulletType": "PLAYER_SHOT", "offsetX": 20, "offsetY": 0, "vx": 0, "vy": -17.5, "lifetime": 45 }
                      ]
                    ]
                  }
                }
              ]
            }
            """;
}
