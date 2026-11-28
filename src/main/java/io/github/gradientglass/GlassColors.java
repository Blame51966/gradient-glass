package io.github.gradientglass;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

final class GlassColors {
    private final Color solid;
    private final Color[] anchors;
    private final int tones;

    GlassColors(GlassNeighborhood neighbors, GlassPalette palette) {
        int tone = neighbors.center().tone();
        solid = palette.color(tone);
        if (!neighbors.hasOtherTone()) {
            anchors = null;
            tones = 1 << tone;
            return;
        }

        int contributors = 0;
        anchors = new Color[27];
        for (int y = 0; y < 3; y++) {
            for (int z = 0; z < 3; z++) {
                for (int x = 0; x < 3; x++) {
                    int cells = connectedCells(neighbors, x, y, z);
                    float red = 0;
                    float green = 0;
                    float blue = 0;
                    float bodyAlpha = 0;
                    float borderAlpha = 0;
                    for (int remaining = cells; remaining != 0; remaining &= remaining - 1) {
                        var cell = neighbors.cell(Integer.numberOfTrailingZeros(remaining));
                        contributors |= 1 << cell.tone();
                        var color = palette.color(cell.tone());
                        red += color.red();
                        green += color.green();
                        blue += color.blue();
                        bodyAlpha += color.bodyAlpha();
                        borderAlpha += color.borderAlpha();
                    }
                    int count = Integer.bitCount(cells);
                    anchors[y * 9 + z * 3 + x] = new Color(red / count, green / count, blue / count,
                            bodyAlpha / count, borderAlpha / count);
                }
            }
        }
        tones = contributors;
    }

    private static int connectedCells(GlassNeighborhood neighbors, int ax, int ay, int az) {
        // At a block center only that block contributes. On a boundary, use the touching component
        // among the two, four or eight cells sharing the sample.
        int candidates = 0;
        for (int y = minimum(ay); y <= maximum(ay); y++) {
            for (int z = minimum(az); z <= maximum(az); z++) {
                for (int x = minimum(ax); x <= maximum(ax); x++) {
                    int index = GlassNeighborhood.index(x, y, z);
                    if (neighbors.cell(index) != null) {
                        candidates |= 1 << index;
                    }
                }
            }
        }

        int visited = 1 << GlassNeighborhood.index(0, 0, 0);
        int pending = visited;
        while (pending != 0) {
            int index = Integer.numberOfTrailingZeros(pending);
            pending &= pending - 1;
            int x = index % 3 - 1;
            int z = index / 3 % 3 - 1;
            int y = index / 9 - 1;
            GlassCell cell = neighbors.cell(index);
            for (Direction direction : GlassMesh.DIRECTIONS) {
                int nx = x + direction.getStepX();
                int ny = y + direction.getStepY();
                int nz = z + direction.getStepZ();
                if (nx < -1 || nx > 1 || ny < -1 || ny > 1 || nz < -1 || nz > 1) {
                    continue;
                }
                int adjacent = GlassNeighborhood.index(nx, ny, nz);
                int bit = 1 << adjacent;
                if ((candidates & ~visited & bit) != 0 && cell.touches(neighbors.cell(adjacent), direction)) {
                    visited |= bit;
                    pending |= bit;
                }
            }
        }
        return visited;
    }

    private static int minimum(int coordinate) {
        return coordinate == 0 ? -1 : 0;
    }

    private static int maximum(int coordinate) {
        return coordinate == 2 ? 1 : 0;
    }

    int tones() {
        return tones;
    }

    boolean mixed() {
        return Integer.bitCount(tones) > 1;
    }

    Color sample(float x, float y, float z) {
        if (!mixed()) {
            return solid;
        }
        int ix = x < 0.5f ? 0 : 1;
        int iy = y < 0.5f ? 0 : 1;
        int iz = z < 0.5f ? 0 : 1;
        float tx = x * 2 - ix;
        float ty = y * 2 - iy;
        float tz = z * 2 - iz;
        int i = iy * 9 + iz * 3 + ix;
        return Color.lerp(ty,
                Color.lerp(tz, Color.lerp(tx, anchors[i], anchors[i + 1]),
                               Color.lerp(tx, anchors[i + 3], anchors[i + 4])),
                Color.lerp(tz, Color.lerp(tx, anchors[i + 9], anchors[i + 10]),
                               Color.lerp(tx, anchors[i + 12], anchors[i + 13])));
    }

    record Color(float red, float green, float blue, float bodyAlpha, float borderAlpha) {
        static Color fromRgb(int rgb, int bodyAlpha, int borderAlpha) {
            return new Color(rgb >>> 16 & 255, rgb >>> 8 & 255, rgb & 255, bodyAlpha, borderAlpha);
        }

        static Color lerp(float t, Color from, Color to) {
            return new Color(Mth.lerp(t, from.red, to.red), Mth.lerp(t, from.green, to.green),
                    Mth.lerp(t, from.blue, to.blue), Mth.lerp(t, from.bodyAlpha, to.bodyAlpha),
                    Mth.lerp(t, from.borderAlpha, to.borderAlpha));
        }

        int bakedColor() {
            return 0xFF000000 | rgbInAbgrOrder();
        }

        int pixel(boolean border) {
            return Math.round(border ? borderAlpha : bodyAlpha) << 24 | rgbInAbgrOrder();
        }

        private int rgbInAbgrOrder() {
            return Math.round(blue) << 16 | Math.round(green) << 8 | Math.round(red);
        }
    }
}
