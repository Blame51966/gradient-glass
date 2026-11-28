package io.github.gradientglass;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@Mod(value = GradientGlass.ID, dist = Dist.CLIENT)
public final class GradientGlass {
    static final String ID = "gradientglass";
    private static final double DEFAULT_WIDTH = 1.0 / 16;
    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue WIDTH;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BLEND_GROUPS;
    private static final MaterialConfig CLEAR;
    private static final MaterialConfig STAINED;
    private static final MaterialConfig TINTED;

    private static volatile Settings settings = new Settings((float) DEFAULT_WIDTH,
            new GlassBlendGroups(GlassBlendGroups.DEFAULT), GlassPalette.Materials.DEFAULT);

    static {
        var builder = new ModConfigSpec.Builder();
        WIDTH = builder.comment("边框厚度（格）。0.0625 = 1/16，设为 0 隐藏边框。")
                .defineInRange("border_width", DEFAULT_WIDTH, 0.0, 0.25);
        BLEND_GROUPS = builder.comment(
                "每个字符串是一组，组内以逗号分隔原版玻璃 ID，minecraft: 可省略。",
                "stained_blocks 代表全部 16 种染色玻璃，stained_panes 代表全部 16 种染色玻璃板。",
                "例：[\"stained_blocks, glass, tinted_glass\", \"stained_panes, glass_pane\"]",
                "有共同成员的组会合并。空列表 [] 关闭所有跨材质互溶。")
                .defineListAllowEmpty("blend_groups", GlassBlendGroups.DEFAULT, () -> "glass", GlassBlendGroups::isValid);
        CLEAR = MaterialConfig.define(builder, "clear", "#E0FFFF", GlassPalette.Materials.DEFAULT.clear());
        STAINED = MaterialConfig.define(builder, "stained", null, GlassPalette.Materials.DEFAULT.stained());
        TINTED = MaterialConfig.define(builder, "tinted", "#302030", GlassPalette.Materials.DEFAULT.tinted());
        SPEC = builder.build();
    }

    public GradientGlass(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
        bus.addListener(this::registerSpriteSource);
        bus.addListener(this::replaceGlassModels);
        bus.addListener(this::loadConfig);
        bus.addListener(this::reloadConfig);
    }

    static Settings settings() {
        return settings;
    }

    private void registerSpriteSource(RegisterSpriteSourceTypesEvent event) {
        event.register(GlassSprites.ID, GlassSprites.TYPE);
    }

    private void replaceGlassModels(ModelEvent.ModifyBakingResult event) {
        var palette = GlassPalette.load(event.getTextureGetter());
        if (palette == null) {
            return;
        }
        GlassCell.states().forEach((state, cell) -> {
            var modelId = BlockModelShaper.stateToModelLocation(state);
            event.getModels().computeIfPresent(modelId, (id, original) -> new GradientGlassModel(original, palette));
        });
    }

    private void loadConfig(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            readConfig();
        }
    }

    private void reloadConfig(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            var previous = settings;
            readConfig();
            boolean materialsChanged = !previous.materials().equals(settings.materials());
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                if (materialsChanged) {
                    minecraft.delayTextureReload();
                } else if (minecraft.level != null) {
                    minecraft.levelRenderer.allChanged();
                }
            });
        }
    }

    private void readConfig() {
        settings = new Settings(WIDTH.get().floatValue(), new GlassBlendGroups(BLEND_GROUPS.get()),
                new GlassPalette.Materials(CLEAR.read(), STAINED.read(), TINTED.read()));
    }

    record Settings(float width, GlassBlendGroups groups, GlassPalette.Materials materials) {}

    private record MaterialConfig(ModConfigSpec.ConfigValue<String> color,
                                  ModConfigSpec.IntValue bodyAlpha, ModConfigSpec.IntValue borderAlpha) {
        static MaterialConfig define(ModConfigSpec.Builder builder, String name, String rgb, GlassColors.Color defaults) {
            builder.push(name);
            var color = rgb == null ? null : builder.comment("基础颜色，格式为 #RRGGBB。")
                    .define("color", rgb, value -> value instanceof String text && text.matches("#[0-9a-fA-F]{6}"));
            var body = builder.comment("面内 Alpha。0 完全透明，255 完全不透明。")
                    .defineInRange("body_alpha", (int) defaults.bodyAlpha(), 0, 255);
            var border = builder.comment("边框 Alpha。0 完全透明，255 完全不透明。")
                    .defineInRange("border_alpha", (int) defaults.borderAlpha(), 0, 255);
            builder.pop();
            return new MaterialConfig(color, body, border);
        }

        GlassColors.Color read() {
            int rgb = color == null ? 0 : Integer.parseInt(color.get().substring(1), 16);
            return GlassColors.Color.fromRgb(rgb, bodyAlpha.get(), borderAlpha.get());
        }
    }
}
