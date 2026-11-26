package io.github.gradientglass;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class GlassNeighborhood {
    private final GlassCell[] cells = new GlassCell[27];
    private final boolean[] opaqueNeighbors = new boolean[6];
    private final GlassCell center;
    private final GradientGlass.Settings settings;
    private boolean otherTone;

    GlassNeighborhood(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        var states = GlassCell.states();
        center = states.get(state);
        settings = GradientGlass.settings();
        var scratch = new BlockPos.MutableBlockPos();
        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                for (int x = -1; x <= 1; x++) {
                    scratch.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    GlassCell cell = states.get(level.getBlockState(scratch));
                    if (cell != null && settings.connects(cell.tone(), center.tone())) {
                        cells[index(x, y, z)] = cell;
                        otherTone |= cell.tone() != center.tone();
                    }
                }
            }
        }
        cells[index(0, 0, 0)] = center;

        for (Direction direction : GlassMesh.DIRECTIONS) {
            scratch.setWithOffset(pos, direction);
            BlockState adjacent = level.getBlockState(scratch);
            opaqueNeighbors[direction.ordinal()] = !states.containsKey(adjacent) && adjacent.canOcclude()
                    && Block.isShapeFullBlock(adjacent.getFaceOcclusionShape(level, scratch, direction.getOpposite()));
        }
    }

    GlassCell center() {
        return center;
    }

    GradientGlass.Settings settings() {
        return settings;
    }

    boolean hasOtherTone() {
        return otherTone;
    }

    GlassCell cell(int index) {
        return cells[index];
    }

    boolean occludes(Direction normal) {
        return opaqueNeighbors[normal.ordinal()];
    }

    boolean covers(int x, int y, int z) {
        GlassCell cell = cells[index(Math.floorDiv(x, 3), y, Math.floorDiv(z, 3))];
        return cell != null && cell.covers(Math.floorMod(x, 3), Math.floorMod(z, 3));
    }

    static int index(int x, int y, int z) {
        return (y + 1) * 9 + (z + 1) * 3 + x + 1;
    }
}
