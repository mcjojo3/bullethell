package mc.sayda.bullethell.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Optional;

/**
 * Places a fixed NBT structure template with its floor (template local Y=2) at the
 * terrain surface, so the bottom two blocks sit underground.
 *
 * Registered as "bullethell:bh_spawn"; referenced by the structure JSON via
 * "type": "bullethell:bh_spawn".
 */
public class BHSpawnStructure extends Structure {

    public static final Codec<BHSpawnStructure> CODEC = RecordCodecBuilder.<BHSpawnStructure>mapCodec(instance ->
            instance.group(
                    Structure.settingsCodec(instance),
                    ResourceLocation.CODEC.fieldOf("template").forGetter(s -> s.templateId)
            ).apply(instance, BHSpawnStructure::new)
    ).codec();

    private final ResourceLocation templateId;

    public BHSpawnStructure(StructureSettings settings, ResourceLocation templateId) {
        super(settings);
        this.templateId = templateId;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos chunkPos = ctx.chunkPos();
        int cx = chunkPos.getMiddleBlockX();
        int cz = chunkPos.getMiddleBlockZ();

        // firstFreeHeight = first air block above terrain (surface block Y + 1).
        // We want template local Y=2 (the floor) at the surface block, so:
        //   originY = (firstFreeHeight - 1) - 2 = firstFreeHeight - 3
        int firstFree = ctx.chunkGenerator().getFirstFreeHeight(
                cx, cz, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
        int originY = firstFree - 3;

        // Centre the footprint on the chunk middle using the template's actual XZ size.
        StructureTemplateManager mgr = ctx.structureTemplateManager();
        Vec3i size = mgr.getOrCreate(templateId).getSize();
        BlockPos origin = new BlockPos(cx - size.getX() / 2, originY, cz - size.getZ() / 2);

        return Optional.of(new GenerationStub(origin, builder ->
                BHSpawnStructurePiece.addPiece(mgr, templateId, origin, builder)));
    }

    @Override
    public StructureType<?> type() {
        return BHStructures.BH_SPAWN.get();
    }
}
