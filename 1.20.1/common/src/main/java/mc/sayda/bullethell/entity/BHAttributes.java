package mc.sayda.bullethell.entity;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mc.sayda.bullethell.Bullethell;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import javax.annotation.Nullable;

/**
 * Vanilla-style {@link Attribute}s for bullet-hell bonuses, modifiable in-game
 * with {@code /attribute ... bullethell:extra_lives base set 1} (and
 * {@code bullethell:extra_bombs}).
 */
public final class BHAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Bullethell.MODID, Registries.ATTRIBUTE);

    /**
     * Extra lives added after resolving stage rules vs character
     * {@code startingLives} (same order as {@link mc.sayda.bullethell.arena.ArenaContext}).
     */
    public static final RegistrySupplier<Attribute> EXTRA_LIVES = ATTRIBUTES.register("extra_lives",
            () -> new RangedAttribute(
                    "attribute.name.bullethell.extra_lives",
                    0.0,
                    0.0,
                    64.0)
                    .setSyncable(true));

    /** Extra bombs (capped with base so total bombs never exceed 9 in arena). */
    public static final RegistrySupplier<Attribute> EXTRA_BOMBS = ATTRIBUTES.register("extra_bombs",
            () -> new RangedAttribute(
                    "attribute.name.bullethell.extra_bombs",
                    0.0,
                    0.0,
                    9.0)
                    .setSyncable(true));

    private BHAttributes() {
    }

    public static void register() {
        ATTRIBUTES.register();
    }

    /** Floored non-negative bonus from {@link #EXTRA_LIVES}. */
    public static int extraLivesBonus(@Nullable net.minecraft.world.entity.LivingEntity entity) {
        if (entity == null || !entity.getAttributes().hasAttribute(EXTRA_LIVES.get()))
            return 0;
        return Mth.floor(Mth.clamp(entity.getAttributeValue(EXTRA_LIVES.get()), 0.0, 64.0));
    }

    /** Floored non-negative bonus from {@link #EXTRA_BOMBS}. */
    public static int extraBombsBonus(@Nullable net.minecraft.world.entity.LivingEntity entity) {
        if (entity == null || !entity.getAttributes().hasAttribute(EXTRA_BOMBS.get()))
            return 0;
        return Mth.floor(Mth.clamp(entity.getAttributeValue(EXTRA_BOMBS.get()), 0.0, 9.0));
    }
}
