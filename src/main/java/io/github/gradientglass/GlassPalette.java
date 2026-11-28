package io.github.gradientglass;

import java.util.function.Function;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;

final class GlassPalette {
    private final GlassColors.Color[] colors;
    private final Paint[] solids = new Paint[GlassCell.TONE_COUNT];
    private final Paint[][] pairs = new Paint[GlassCell.TONE_COUNT][GlassCell.TONE_COUNT];
    private final Paint neutral;

    static GlassPalette load(Function<Material, TextureAtlasSprite> getter) {
        var alphaRamp = getter.apply(material("neutral"));
        return alphaRamp.contents() instanceof GlassSprites.AlphaRamp ramp
                ? new GlassPalette(getter, alphaRamp, ramp.materials()) : null;
    }

    private GlassPalette(Function<Material, TextureAtlasSprite> getter, TextureAtlasSprite alphaRamp,
                         Materials materials) {
        colors = materials.colors();
        for (int tone = 0; tone < solids.length; tone++) {
            solids[tone] = new Paint(load(getter, "color_" + tone), null, null, 0, false);
        }
        for (int first = 0; first < colors.length; first++) {
            for (int second = first + 1; second < colors.length; second++) {
                pairs[first][second] = colors[first].equals(colors[second]) ? solids[first]
                        : Paint.pair(load(getter, "blend_" + first + "_" + second), colors[first], colors[second]);
            }
        }
        neutral = new Paint(new Sprites(alphaRamp, alphaRamp), null, null, 0, true);
    }

    GlassColors.Color color(int tone) {
        return colors[tone];
    }

    Paint paint(GlassColors colors) {
        int mask = colors.tones();
        int first = Integer.numberOfTrailingZeros(mask);
        return switch (Integer.bitCount(mask)) {
            case 1 -> solids[first];
            case 2 -> pairs[first][Integer.numberOfTrailingZeros(mask & (mask - 1))];
            default -> neutral;
        };
    }

    private static Sprites load(Function<Material, TextureAtlasSprite> getter, String name) {
        return new Sprites(getter.apply(material(name)), getter.apply(material(name + "_border")));
    }

    private static Material material(String name) {
        return new Material(InventoryMenu.BLOCK_ATLAS, GlassSprites.spriteId(name));
    }

    record Materials(GlassColors.Color clear, GlassColors.Color stained, GlassColors.Color tinted) {
        static final Materials DEFAULT = new Materials(GlassColors.Color.fromRgb(0xE0FFFF, 32, 192),
                GlassColors.Color.fromRgb(0, 96, 192), GlassColors.Color.fromRgb(0x302030, 160, 224));

        GlassColors.Color[] colors() {
            var colors = new GlassColors.Color[GlassCell.TONE_COUNT];
            for (DyeColor dye : DyeColor.values()) {
                colors[dye.getId()] = GlassColors.Color.fromRgb(dye.getTextureDiffuseColor(),
                        (int) stained.bodyAlpha(), (int) stained.borderAlpha());
            }
            colors[GlassCell.CLEAR] = clear;
            colors[GlassCell.TINTED] = tinted;
            return colors;
        }
    }

    record Sprites(TextureAtlasSprite body, TextureAtlasSprite border) {}

    record Paint(Sprites sprites, GlassColors.Color start, Delta delta, float scale, boolean vertexColor) {
        static Paint pair(Sprites sprites, GlassColors.Color start, GlassColors.Color end) {
            var delta = Delta.between(start, end);
            return new Paint(sprites, start, delta, 1f / delta.dot(delta), false);
        }

        boolean usesRamp() {
            return delta != null || vertexColor;
        }

        float textureU(GlassColors.Color color, boolean border) {
            if (vertexColor) {
                // Multi-tone junctions use vertex RGB and a texture ramp for alpha.
                float alpha = border ? color.borderAlpha() : color.bodyAlpha();
                return GlassSprites.alphaU(alpha);
            }
            float fraction = Mth.clamp(delta.dot(Delta.between(start, color)) * scale, 0f, 1f);
            return GlassSprites.blendU(fraction);
        }
    }

    private record Delta(float red, float green, float blue, float bodyAlpha, float borderAlpha) {
        static Delta between(GlassColors.Color start, GlassColors.Color end) {
            return new Delta(end.red() - start.red(), end.green() - start.green(), end.blue() - start.blue(),
                    end.bodyAlpha() - start.bodyAlpha(), end.borderAlpha() - start.borderAlpha());
        }

        float dot(Delta other) {
            return red * other.red + green * other.green + blue * other.blue
                    + bodyAlpha * other.bodyAlpha + borderAlpha * other.borderAlpha;
        }
    }
}
