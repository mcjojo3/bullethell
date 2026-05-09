package mc.sayda.bullethell.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Single-piece holder for a BH spawn structure template.
 *
 * The template is loaded eagerly in both the generation constructor and the NBT
 * deserialization constructor (both paths receive a {@link StructureTemplateManager}),
 * so {@link #postProcess} can place it without needing to reach back to the server.
 */
public class BHSpawnStructurePiece extends StructurePiece {

    private final ResourceLocation templateId;
    private final BlockPos origin;
    private final StructureTemplate template;

    /** Used during world generation — loads the template immediately. */
    public BHSpawnStructurePiece(StructureTemplateManager manager, ResourceLocation templateId, BlockPos origin) {
        super(BHStructures.BH_SPAWN_PIECE.get(), 0, computeBox(manager, templateId, origin));
        this.templateId = templateId;
        this.origin = origin;
        this.template = manager.getOrCreate(templateId);
    }

    private static BoundingBox computeBox(StructureTemplateManager manager, ResourceLocation templateId, BlockPos origin) {
        Vec3i size = manager.getOrCreate(templateId).getSize();
        return BoundingBox.fromCorners(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1));
    }

    /** NBT deserialization constructor — called by the registered {@code StructurePieceType}. */
    public BHSpawnStructurePiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(BHStructures.BH_SPAWN_PIECE.get(), tag);
        this.templateId = new ResourceLocation(tag.getString("BHTemplate"));
        this.origin = NbtUtils.readBlockPos(tag.getCompound("BHOrigin"));
        this.template = ctx.structureTemplateManager().getOrCreate(this.templateId);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putString("BHTemplate", templateId.toString());
        tag.put("BHOrigin", NbtUtils.writeBlockPos(origin));
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false);
        template.placeInWorld(level, origin, origin, settings, random, 2);
    }

    public static void addPiece(StructureTemplateManager manager, ResourceLocation templateId,
                                BlockPos origin, StructurePiecesBuilder builder) {
        builder.addPiece(new BHSpawnStructurePiece(manager, templateId, origin));
    }
}
