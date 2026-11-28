package io.github.gradientglass;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

final class GlassMesh {
    static final Direction[] DIRECTIONS = Direction.values();
    private static final int STRIDE = DefaultVertexFormat.BLOCK.getVertexSize() / Integer.BYTES;
    private static final int EMPTY = -1;
    private static final float[] HORIZONTAL_CUTS = {0f, 7f / 16, 9f / 16, 1f};
    private static final float[] VERTICAL_CUTS = {0f, 1f};
    private static final Face[] FACES = {
        new Face(Direction.DOWN, Direction.EAST, Direction.SOUTH),
        new Face(Direction.UP, Direction.EAST, Direction.NORTH),
        new Face(Direction.NORTH, Direction.WEST, Direction.UP),
        new Face(Direction.SOUTH, Direction.EAST, Direction.UP),
        new Face(Direction.WEST, Direction.SOUTH, Direction.UP),
        new Face(Direction.EAST, Direction.NORTH, Direction.UP)
    };

    private final GlassNeighborhood neighbors;
    private final GlassColors colors;
    private final GlassPalette.Paint paint;
    private final List<BakedQuad> quads = new ArrayList<>();

    GlassMesh(GlassNeighborhood neighbors, GlassPalette palette) {
        this.neighbors = neighbors;
        this.colors = new GlassColors(neighbors, palette);
        this.paint = palette.paint(colors);
    }

    List<BakedQuad> build() {
        for (Face face : FACES) {
            float[] depths = cuts(face.normal);
            for (int layer = 0; layer < depths.length - 1; layer++) {
                if (layer + 2 != depths.length || !neighbors.occludes(face.normal)) {
                    new Plane(face, layer, depths[layer + 1]).emit();
                }
            }
        }
        return quads;
    }

    private static float[] cuts(Direction axis) {
        return axis.getAxis().isVertical() ? VERTICAL_CUTS : HORIZONTAL_CUTS;
    }

    private final class Plane {
        private final Face face;
        private final int layer;
        private final float depth;
        private final float[] baseU;
        private final float[] baseV;
        private final float borderWidth = neighbors.settings().width();

        Plane(Face face, int layer, float depth) {
            this.face = face;
            this.layer = layer;
            this.depth = depth;
            baseU = cuts(face.u);
            baseV = cuts(face.v);
        }

        void emit() {
            int width = baseU.length - 1;
            int height = baseV.length - 1;
            int[] tiles = new int[width * height];
            boolean anyVisible = false;
            boolean anyBorder = false;
            for (int v = 0; v < height; v++) {
                for (int u = 0; u < width; u++) {
                    int mask = EMPTY;
                    if (visible(u, v)) {
                        anyVisible = true;
                        mask = borderWidth == 0 ? 0 : borderMask(u, v);
                        anyBorder |= mask != 0;
                    }
                    tiles[v * width + u] = mask;
                }
            }
            if (!anyVisible) {
                return;
            }

            float[] us = knots(baseU, anyBorder);
            float[] vs = knots(baseV, anyBorder);
            int columns = us.length - 1;
            int rows = vs.length - 1;
            int[] regions = new int[columns * rows];
            for (int v = 0; v < rows; v++) {
                float centerV = (vs[v] + vs[v + 1]) * 0.5f;
                int tileV = tileAt(baseV, centerV);
                int edgeV = edgeAt(baseV, tileV, centerV);
                for (int u = 0; u < columns; u++) {
                    float centerU = (us[u] + us[u + 1]) * 0.5f;
                    int tileU = tileAt(baseU, centerU);
                    int tile = tiles[tileV * width + tileU];
                    int edgeU = edgeAt(baseU, tileU, centerU);
                    regions[v * columns + u] = tile == EMPTY ? EMPTY : (tile >> (edgeV * 3 + edgeU)) & 1;
                }
            }
            emitRectangles(us, vs, regions);
        }

        private void emitRectangles(float[] us, float[] vs, int[] regions) {
            int columns = us.length - 1;
            int rows = vs.length - 1;
            for (int v = 0; v < rows; v++) {
                for (int u = 0; u < columns; u++) {
                    int fill = regions[v * columns + u];
                    if (fill == EMPTY) {
                        continue;
                    }
                    int endU = u + 1;
                    while (endU < columns && !gradientBreak(us[endU])
                            && regions[v * columns + endU] == fill) {
                        endU++;
                    }
                    int endV = v + 1;
                    while (endV < rows && !gradientBreak(vs[endV])
                            && rowMatches(regions, endV * columns, u, endU, fill)) {
                        endV++;
                    }
                    for (int row = v; row < endV; row++) {
                        Arrays.fill(regions, row * columns + u, row * columns + endU, EMPTY);
                    }
                    addQuad(face, depth, us[u], vs[v], us[endU], vs[endV], fill != 0);
                }
            }
        }

        private boolean gradientBreak(float position) {
            return position == 0.5f && colors.mixed();
        }

        private boolean rowMatches(int[] regions, int row, int start, int end, int fill) {
            for (int col = start; col < end; col++) {
                if (regions[row + col] != fill) {
                    return false;
                }
            }
            return true;
        }

        private boolean visible(int u, int v) {
            return covered(u, v, layer) && !covered(u, v, layer + 1);
        }

        private boolean covered(int u, int v, int depthCell) {
            return neighbors.covers(face.gridX(u, v, depthCell), face.gridY(u, v, depthCell),
                                    face.gridZ(u, v, depthCell));
        }

        private int borderMask(int u, int v) {
            boolean left = covered(u - 1, v, layer);
            boolean right = covered(u + 1, v, layer);
            boolean bottom = covered(u, v - 1, layer);
            boolean top = covered(u, v + 1, layer);
            int mask = 0;
            if (!left) mask |= 1 << 3;
            if (!right) mask |= 1 << 5;
            if (!bottom) mask |= 1 << 1;
            if (!top) mask |= 1 << 7;
            if (!left || !bottom || planarCorner(u, v, -1, -1)) mask |= 1;
            if (!right || !bottom || planarCorner(u, v, 1, -1)) mask |= 1 << 2;
            if (!left || !top || planarCorner(u, v, -1, 1)) mask |= 1 << 6;
            if (!right || !top || planarCorner(u, v, 1, 1)) mask |= 1 << 8;
            return mask;
        }

        private boolean planarCorner(int u, int v, int du, int dv) {
            // A diagonal surface hidden by projecting glass still belongs to this sheet.
            // The frame fills this corner only if the diagonal cell itself is missing.
            return visible(u + du, v) && visible(u, v + dv) && !covered(u + du, v + dv, layer);
        }

        private float[] knots(float[] base, boolean framed) {
            float[] points = Arrays.copyOf(base, base.length * 3);
            int count = base.length;
            if (colors.mixed()) {
                points[count++] = 0.5f;
            }
            if (framed) {
                for (int i = 0; i + 1 < base.length; i++) {
                    float width = inset(base, i);
                    points[count++] = base[i] + width;
                    points[count++] = base[i + 1] - width;
                }
            }
            Arrays.sort(points, 0, count);
            int unique = 1;
            for (int i = 1; i < count; i++) {
                if (points[i] > points[unique - 1]) {
                    points[unique++] = points[i];
                }
            }
            return Arrays.copyOf(points, unique);
        }

        private int tileAt(float[] cuts, float value) {
            if (cuts.length == 2 || value < cuts[1]) return 0;
            return value < cuts[2] ? 1 : 2;
        }

        private int edgeAt(float[] cuts, int tile, float position) {
            float inset = inset(cuts, tile);
            if (position < cuts[tile] + inset) return 0;
            return position > cuts[tile + 1] - inset ? 2 : 1;
        }

        private float inset(float[] cuts, int tile) {
            return Math.min(borderWidth, (cuts[tile + 1] - cuts[tile]) * 0.5f);
        }
    }

    private void addQuad(Face face, float depth, float u0, float v0, float u1, float v1, boolean border) {
        TextureAtlasSprite sprite = border ? paint.sprites().border() : paint.sprites().body();
        int[] vertices = new int[4 * STRIDE];
        float a = writeVertex(vertices, 0, face, depth, u0, v0, border);
        float b = writeVertex(vertices, 1, face, depth, u1, v0, border);
        float c = writeVertex(vertices, 2, face, depth, u1, v1, border);
        float d = writeVertex(vertices, 3, face, depth, u0, v1, border);
        if (paint.usesRamp()) {
            boolean horizontal = Math.abs(a - b) + Math.abs(c - d) >= Math.abs(a - d) + Math.abs(b - c);
            writeUv(vertices, 0, sprite, a, transverse(horizontal, u0, v0));
            writeUv(vertices, 1, sprite, b, transverse(horizontal, u1, v0));
            writeUv(vertices, 2, sprite, c, transverse(horizontal, u1, v1));
            writeUv(vertices, 3, sprite, d, transverse(horizontal, u0, v1));
        } else {
            writeUv(vertices, 0, sprite, 0.25f, 0.25f);
            writeUv(vertices, 1, sprite, 0.75f, 0.25f);
            writeUv(vertices, 2, sprite, 0.75f, 0.75f);
            writeUv(vertices, 3, sprite, 0.25f, 0.75f);
        }
        quads.add(new BakedQuad(vertices, -1, face.normal, sprite, false, true));
    }

    private float writeVertex(int[] vertices, int index, Face face, float depth, float u, float v, boolean border) {
        int start = index * STRIDE;
        float x = face.x(u, v, depth);
        float y = face.y(u, v, depth);
        float z = face.z(u, v, depth);
        vertices[start] = Float.floatToRawIntBits(x);
        vertices[start + 1] = Float.floatToRawIntBits(y);
        vertices[start + 2] = Float.floatToRawIntBits(z);
        vertices[start + 7] = face.packedNormal;
        if (paint.usesRamp()) {
            var color = colors.sample(x, y, z);
            vertices[start + 3] = paint.vertexColor() ? color.bakedColor() : 0xFFFFFFFF;
            return paint.textureU(color, border);
        }
        vertices[start + 3] = 0xFFFFFFFF;
        return 0;
    }

    private static void writeUv(int[] vertices, int index, TextureAtlasSprite sprite, float u, float v) {
        int start = index * STRIDE;
        vertices[start + 4] = Float.floatToRawIntBits(sprite.getU(u));
        vertices[start + 5] = Float.floatToRawIntBits(sprite.getV(v));
    }

    private static float transverse(boolean horizontal, float u, float v) {
        return 0.25f + 0.5f * (horizontal ? v : u);
    }

    private static final class Face {
        private final Direction normal;
        private final Direction u;
        private final Direction v;
        private final int xOrigin;
        private final int yOrigin;
        private final int zOrigin;
        private final int packedNormal;

        Face(Direction normal, Direction u, Direction v) {
            this.normal = normal;
            this.u = u;
            this.v = v;
            xOrigin = normal.getStepX() + u.getStepX() + v.getStepX() < 0 ? 1 : 0;
            yOrigin = normal.getStepY() + u.getStepY() + v.getStepY() < 0 ? 1 : 0;
            zOrigin = normal.getStepZ() + u.getStepZ() + v.getStepZ() < 0 ? 1 : 0;
            packedNormal = (127 * normal.getStepX() & 255)
                    | (127 * normal.getStepY() & 255) << 8 | (127 * normal.getStepZ() & 255) << 16;
        }

        float x(float a, float b, float d) {
            return xOrigin + u.getStepX() * a + v.getStepX() * b + normal.getStepX() * d;
        }

        float y(float a, float b, float d) {
            return yOrigin + u.getStepY() * a + v.getStepY() * b + normal.getStepY() * d;
        }

        float z(float a, float b, float d) {
            return zOrigin + u.getStepZ() * a + v.getStepZ() * b + normal.getStepZ() * d;
        }

        int gridX(int a, int b, int d) {
            return xOrigin * 2 + u.getStepX() * a + v.getStepX() * b + normal.getStepX() * d;
        }

        int gridY(int a, int b, int d) {
            return u.getStepY() * a + v.getStepY() * b + normal.getStepY() * d;
        }

        int gridZ(int a, int b, int d) {
            return zOrigin * 2 + u.getStepZ() * a + v.getStepZ() * b + normal.getStepZ() * d;
        }
    }
}
