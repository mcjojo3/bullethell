package mc.sayda.bullethell.worldgen;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mc.sayda.bullethell.Bullethell;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class BHStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Bullethell.MODID, Registries.STRUCTURE_TYPE);

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Bullethell.MODID, Registries.STRUCTURE_PIECE);

    public static final RegistrySupplier<StructureType<BHSpawnStructure>> BH_SPAWN =
            STRUCTURE_TYPES.register("bh_spawn", () -> () -> BHSpawnStructure.CODEC);

    public static final RegistrySupplier<StructurePieceType> BH_SPAWN_PIECE =
            STRUCTURE_PIECE_TYPES.register("bh_spawn_piece",
                    () -> (ctx, tag) -> new BHSpawnStructurePiece(ctx, tag));

    public static void register() {
        STRUCTURE_TYPES.register();
        STRUCTURE_PIECE_TYPES.register();
    }
}
