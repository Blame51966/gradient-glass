package io.github.gradientglass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@Mod(value = GradientGlass.ID, dist = Dist.CLIENT)
public final class GradientGlass {
    static final String ID = "gradientglass";
    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue WIDTH;
    private static final ModConfigSpec.BooleanValue BLEND_STAINED;
    private static final ModConfigSpec.BooleanValue BLEND_CLEAR;
    private static final ModConfigSpec.BooleanValue BLEND_TINTED;

    private static volatile Settings settings = new Settings(1f / 16, true, false, false);

    static {
        var builder = new ModConfigSpec.Builder();
        WIDTH = builder.comment("边框厚度（格）。0.0625 = 1/16，设为 0 隐藏边框。")
                .defineInRange("border_width", 1.0 / 16, 0.0, 0.25);
        BLEND_STAINED = builder.comment("十六种彩色玻璃及玻璃板是否加入互溶？")
                .define("blend_stained", true);
        BLEND_CLEAR = builder.comment("普通玻璃及玻璃板是否加入互溶？")
                .define("blend_clear", false);
        BLEND_TINTED = builder.comment("遮光玻璃是否加入互溶？")
                .define("blend_tinted", false);
        SPEC = builder.build();
    }

    public GradientGlass(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
        bus.addListener(this::replaceGlassModels);
        bus.addListener(this::loadConfig);
        bus.addListener(this::reloadConfig);
    }

    static Settings settings() {
        return settings;
    }

    private void replaceGlassModels(ModelEvent.ModifyBakingResult event) {
        var palette = new GlassPalette(event.getTextureGetter());
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
            readConfig();
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                if (minecraft.level != null) {
                    minecraft.levelRenderer.allChanged();
                }
            });
        }
    }

    private void readConfig() {
        settings = new Settings(WIDTH.get().floatValue(), BLEND_STAINED.get(), BLEND_CLEAR.get(), BLEND_TINTED.get());
    }

    record Settings(float width, boolean blendStained, boolean blendClear, boolean blendTinted) {
        boolean connects(int first, int second) {
            return first == second || (blends(first) && blends(second));
        }

        private boolean blends(int tone) {
            return switch (tone) {
                case GlassCell.CLEAR -> blendClear;
                case GlassCell.TINTED -> blendTinted;
                default -> blendStained;
            };
        }
    }
}
