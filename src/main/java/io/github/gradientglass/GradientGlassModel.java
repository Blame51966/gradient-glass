package io.github.gradientglass;

import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

final class GradientGlassModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<GlassNeighborhood> NEIGHBORS = new ModelProperty<>();
    private static final ChunkRenderTypeSet TRANSLUCENT = ChunkRenderTypeSet.of(RenderType.translucent());
    private final GlassPalette palette;

    GradientGlassModel(BakedModel original, GlassPalette palette) {
        super(original);
        this.palette = palette;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData incoming) {
        return incoming.derive().with(NEIGHBORS, new GlassNeighborhood(level, pos, state)).build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                   ModelData modelData, @Nullable RenderType renderType) {
        GlassNeighborhood neighbors = modelData.get(NEIGHBORS);
        if (renderType == null || neighbors == null) {
            return originalModel.getQuads(state, side, random, modelData, renderType);
        }
        if (side != null || renderType != RenderType.translucent()) {
            return List.of();
        }
        // Panes have internal planes and can partially cover a full block face. The mesh performs
        // that culling before emitting general quads, so vanilla side culling cannot remove those planes.
        return new GlassMesh(neighbors, palette).build();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return data.has(NEIGHBORS) ? TRANSLUCENT : originalModel.getRenderTypes(state, random, data);
    }

    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        return data.has(NEIGHBORS) ? TriState.DEFAULT : originalModel.useAmbientOcclusion(state, data, renderType);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }
}
