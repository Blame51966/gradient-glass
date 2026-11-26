package io.github.gradientglass;

import java.util.function.Function;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;

final class GlassPalette {
    private static final GlassColors.Color[] COLORS = new GlassColors.Color[18];

    static {
        for (DyeColor dye : DyeColor.values()) {
            COLORS[dye.getId()] = color(dye.getTextureDiffuseColor(), 94, 190);
        }
        COLORS[GlassCell.CLEAR] = color(0xE8F3F4, 16, 172);
        COLORS[GlassCell.TINTED] = color(0x473950, 155, 212);
    }

    private final Sprites[] solids = new Sprites[COLORS.length];
    private final Paint[][] pairs = new Paint[COLORS.length][COLORS.length];
    private final Paint neutral;

    GlassPalette(Function<Material, TextureAtlasSprite> getter) {
        for (int tone = 0; tone < solids.length; tone++) {
            solids[tone] = load(getter, "color_" + tone);
        }
        for (int first = 0; first < COLORS.length; first++) {
            for (int second = first + 1; second < COLORS.length; second++) {
                pairs[first][second] = Paint.pair(load(getter, "blend_" + first + "_" + second),
                        color(first), color(second));
            }
        }
        var alphaRamp = getter.apply(material("neutral"));
        neutral = new Paint(new Sprites(alphaRamp, alphaRamp), null, null, 0, true);
    }

    static GlassColors.Color color(int tone) {
        return COLORS[tone];
    }

    Paint paint(GlassColors colors) {
        int mask = colors.tones();
        int first = Integer.numberOfTrailingZeros(mask);
        return switch (Integer.bitCount(mask)) {
            case 1 -> new Paint(solids[first], null, null, 0, false);
            case 2 -> pairs[first][Integer.numberOfTrailingZeros(mask & (mask - 1))];
            default -> neutral;
        };
    }

    private static GlassColors.Color color(int rgb, int bodyAlpha, int borderAlpha) {
        return new GlassColors.Color(rgb >>> 16 & 255, rgb >>> 8 & 255, rgb & 255, bodyAlpha, borderAlpha);
    }

    private static Sprites load(Function<Material, TextureAtlasSprite> getter, String name) {
        return new Sprites(getter.apply(material(name)), getter.apply(material(name + "_border")));
    }

    private static Material material(String name) {
        return new Material(InventoryMenu.BLOCK_ATLAS,
                ResourceLocation.fromNamespaceAndPath(GradientGlass.ID, "block/glass/" + name));
    }

    record Sprites(TextureAtlasSprite body, TextureAtlasSprite border) {}

    record Paint(Sprites sprites, GlassColors.Color start, Delta delta, float scale, boolean vertexColor) {
        static Paint pair(Sprites sprites, GlassColors.Color start, GlassColors.Color end) {
            var delta = new Delta(end.red() - start.red(), end.green() - start.green(), end.blue() - start.blue());
            return new Paint(sprites, start, delta, 1f / delta.dot(delta.red, delta.green, delta.blue), false);
        }

        boolean usesRamp() {
            return delta != null || vertexColor;
        }

        float textureU(GlassColors.Color color, boolean border) {
            if (vertexColor) {
                // Multi-tone junctions use vertex RGB and a texture ramp for alpha.
                float alpha = border ? color.borderAlpha() : color.bodyAlpha();
                return (128.5f + alpha) / 512f;
            }
            float fraction = Mth.clamp(delta.dot(color.red() - start.red(), color.green() - start.green(),
                    color.blue() - start.blue()) * scale, 0f, 1f);
            return (16.5f + 95f * fraction) / 128f;
        }
    }

    private record Delta(float red, float green, float blue) {
        float dot(float r, float g, float b) {
            return red * r + green * g + blue * b;
        }
    }
}
