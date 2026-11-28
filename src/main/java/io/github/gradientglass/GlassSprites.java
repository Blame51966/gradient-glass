package io.github.gradientglass;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.Mth;

final class GlassSprites implements SpriteSource {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(GradientGlass.ID, "glass");
    static final SpriteSourceType TYPE = new SpriteSourceType(MapCodec.unit(new GlassSprites()));
    private static final int BLEND_WIDTH = 128;
    private static final int BLEND_START = 16;
    private static final int BLEND_SPAN = 95;
    private static final int ALPHA_WIDTH = 512;
    private static final int ALPHA_START = 128;

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }

    @Override
    public void run(ResourceManager manager, Output output) {
        var materials = GradientGlass.settings().materials();
        var colors = materials.colors();
        for (int first = 0; first < colors.length; first++) {
            addPair(output, "color_" + first, colors[first], colors[first]);
            for (int second = first + 1; second < colors.length; second++) {
                if (!colors[first].equals(colors[second])) {
                    addPair(output, "blend_" + first + "_" + second, colors[first], colors[second]);
                }
            }
        }
        var id = spriteId("neutral");
        output.add(id, loader -> new AlphaRamp(id, materials));
    }

    static ResourceLocation spriteId(String name) {
        return ResourceLocation.fromNamespaceAndPath(GradientGlass.ID, "block/glass/" + name);
    }

    static float blendU(float fraction) {
        return (BLEND_START + 0.5f + BLEND_SPAN * fraction) / BLEND_WIDTH;
    }

    static float alphaU(float alpha) {
        return (ALPHA_START + 0.5f + alpha) / ALPHA_WIDTH;
    }

    private static void addPair(Output output, String name, GlassColors.Color start, GlassColors.Color end) {
        add(output, spriteId(name), start, end, false);
        add(output, spriteId(name + "_border"), start, end, true);
    }

    private static void add(Output output, ResourceLocation id, GlassColors.Color start, GlassColors.Color end,
                            boolean border) {
        output.add(id, loader -> {
            boolean solid = start.equals(end);
            int width = solid ? 16 : BLEND_WIDTH;
            int height = solid ? 16 : 32;
            var image = new NativeImage(width, height, false);
            for (int x = 0; x < width; x++) {
                float fraction = Mth.clamp((float) (x - BLEND_START) / BLEND_SPAN, 0, 1);
                int pixel = (solid ? start : GlassColors.Color.lerp(fraction, start, end)).pixel(border);
                image.fillRect(x, 0, 1, height, pixel);
            }
            return new SpriteContents(id, new FrameSize(width, height), image, ResourceMetadata.EMPTY);
        });
    }

    private static NativeImage alphaRamp() {
        var image = new NativeImage(ALPHA_WIDTH, 32, false);
        for (int x = 0; x < ALPHA_WIDTH; x++) {
            int alpha = Mth.clamp(x - ALPHA_START, 0, 255);
            image.fillRect(x, 0, 1, 32, alpha << 24 | 0xFFFFFF);
        }
        return image;
    }

    static final class AlphaRamp extends SpriteContents {
        // Keep the model palette and sprites on the same config snapshot during an async reload.
        private final GlassPalette.Materials materials;

        AlphaRamp(ResourceLocation id, GlassPalette.Materials materials) {
            super(id, new FrameSize(ALPHA_WIDTH, 32), alphaRamp(), ResourceMetadata.EMPTY);
            this.materials = materials;
        }

        GlassPalette.Materials materials() {
            return materials;
        }
    }
}
