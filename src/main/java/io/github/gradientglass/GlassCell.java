package io.github.gradientglass;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;

record GlassCell(int tone, boolean pane, int shape) {
    static final int CLEAR = 16;
    static final int TINTED = 17;
    static final int TONE_COUNT = 18;
    private static final int FULL_SHAPE = 0b111_111_111;

    static Map<BlockState, GlassCell> states() {
        return States.ALL;
    }

    int kind() {
        return kind(tone, pane);
    }

    static int kind(int tone, boolean pane) {
        return pane ? tone + TONE_COUNT : tone;
    }

    boolean covers(int x, int z) {
        return (shape & (1 << (x + z * 3))) != 0;
    }

    boolean touches(GlassCell adjacent, Direction direction) {
        return switch (direction) {
            case DOWN, UP -> (shape & adjacent.shape) != 0;
            case NORTH -> (shape & adjacent.shape >>> 6 & 0b111) != 0;
            case SOUTH -> (shape >>> 6 & adjacent.shape & 0b111) != 0;
            case WEST -> (shape & adjacent.shape >>> 2 & 0b001_001_001) != 0;
            case EAST -> (shape >>> 2 & adjacent.shape & 0b001_001_001) != 0;
        };
    }

    private static Map<BlockState, GlassCell> createStates() {
        Map<BlockState, GlassCell> states = new IdentityHashMap<>();
        put(states, Blocks.GLASS, CLEAR, false);
        put(states, Blocks.GLASS_PANE, CLEAR, true);
        put(states, Blocks.TINTED_GLASS, TINTED, false);
        for (DyeColor dye : DyeColor.values()) {
            String name = dye.getName();
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(name + "_stained_glass"));
            Block pane = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(name + "_stained_glass_pane"));
            put(states, block, dye.getId(), false);
            put(states, pane, dye.getId(), true);
        }
        return Map.copyOf(states);
    }

    private static void put(Map<BlockState, GlassCell> states, Block block, int tone, boolean pane) {
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            int shape = FULL_SHAPE;
            if (pane) {
                shape = 1 << 4;
                if (state.getValue(CrossCollisionBlock.NORTH)) shape |= 1 << 1;
                if (state.getValue(CrossCollisionBlock.WEST)) shape |= 1 << 3;
                if (state.getValue(CrossCollisionBlock.EAST)) shape |= 1 << 5;
                if (state.getValue(CrossCollisionBlock.SOUTH)) shape |= 1 << 7;
            }
            states.put(state, new GlassCell(tone, pane, shape));
        }
    }

    private static final class States {
        private static final Map<BlockState, GlassCell> ALL = createStates();
    }
}
