# Gradient Glass 1.0.1

Gradient Glass 是 Minecraft Java 1.21.1 NeoForge 的客户端模组。它为原版 18 种玻璃方块和 17 种玻璃板绘制无缝表面。相接的玻璃可以按配置分组，让颜色和透明度沿接缝过渡。

本项目由 OpenAI Codex（AI）生成，维护不定期，完整源码以 [MIT 许可证](LICENSE)开源。

## 安装

从 [GitHub Releases](https://github.com/Blame51966/gradient-glass/releases) 下载所需版本的 JAR，放入 NeoForge 实例的 `mods` 文件夹。升级时移走旧的 Gradient Glass JAR。只需在客户端安装，已有存档可以直接使用。

模组自带连接渲染，可以与 Sodium 和 Iris 一起使用。透明混色、反射和折射的最终效果取决于光影包。由 Voxy 等远景模组接管的区域使用对应模组的渲染。

## 配置

首次启动后，编辑实例中的 `config/gradientglass-client.toml`。当前默认值如下。

```toml
border_width = 0.0625
blend_groups = ["stained_blocks", "stained_panes"]

[clear]
color = "#E0FFFF"
body_alpha = 32
border_alpha = 192

[stained]
body_alpha = 96
border_alpha = 192

[tinted]
color = "#302030"
body_alpha = 160
border_alpha = 224
```

- `border_width` 以格为单位，范围为 0 至 0.25。默认是 1/16 格，设为 0 可隐藏边框。启用边框时，外轮廓和洞口会保留边框，内折角的折缝会连成一体。
- `body_alpha` 控制面内的不透明度，`border_alpha` 控制边框的不透明度。0 为完全透明，255 为完全不透明。
- `[clear]` 用于普通玻璃和玻璃板，`[stained]` 用于 16 种染色玻璃及对应玻璃板，`[tinted]` 用于遮光玻璃。普通和遮光玻璃的 `color` 用 `#RRGGBB` 格式，染色玻璃沿用原版染料色值。遮光玻璃仍按原版规则挡光。

### 互溶分组

`blend_groups` 中每个字符串是一组，用逗号分隔组内成员。成员可以是原版方块 ID，例如 `minecraft:red_stained_glass`，也可以省略 `minecraft:`。简写 `stained_blocks` 和 `stained_panes` 分别代表全部染色玻璃方块和全部染色玻璃板。

默认两类染色玻璃各自成组。要让普通玻璃和遮光玻璃再单独组成一组，可以这样写。

```toml
blend_groups = ["stained_blocks", "stained_panes", "glass, tinted_glass"]
```

同一组内实际相接的玻璃会渐变。包含共同成员的组会合并。未分组的玻璃仍会与相同方块无缝连接，设成 `[]` 可以关闭全部跨种类互溶。

旧版配置会保留已有的 `border_width`。原先的三个互溶开关由 `blend_groups` 取代，升级后可在分组里重新指定所需的连接关系。

## 从源码构建

使用 JDK 21，在项目根目录运行以下命令。构建后的 JAR 在 `build/libs/`。玻璃材质由客户端根据配置生成。

```powershell
.\gradlew.bat build
```

```bash
bash ./gradlew build
```
