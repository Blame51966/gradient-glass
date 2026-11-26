# Gradient Glass 1.0.0

> 本项目由 OpenAI Codex（AI）生成。项目不定期维护，完整源码采用 MIT 许可证开放，欢迎自行修改和构建。

适用于 Minecraft Java 1.21.1 NeoForge 的客户端模组。从发行版下载 `gradient-glass-1.0.0.jar`，放入实例的 `mods` 文件夹。升级时先移走旧的 Gradient Glass JAR。打开旧存档即可看到变化，服务端无需安装，也无需连接纹理模组或资源包。[下载 1.0.0](https://github.com/Blame51966/gradient-glass/releases/tag/v1.0.0)

## 玻璃连接与混色

- 原版 18 种玻璃方块和 17 种玻璃板都支持连接外观。
- 参与互溶的材质相接时，颜色和透明度会从一边平滑过渡到另一边。同材质玻璃也会连成一片。
- 普通玻璃与普通玻璃板的玻璃面材质 Alpha 为 16/255。遮光玻璃的游戏内挡光行为保留。
- 外轮廓和洞口保留边框。L 形窗内侧折角的两面连在一起时，折缝处不画边框。

## 边框设置

启动游戏后，可在实例的 `config/gradientglass-client.toml` 中修改以下选项。

| 选项 | 默认值 | 含义 |
| --- | --- | --- |
| `border_width` | `0.0625` | 以方块为单位，默认 1/16 格。设为 `0.0` 隐藏边框。 |
| `blend_stained` | `true` | 16 种彩色玻璃及玻璃板加入互溶。 |
| `blend_clear` | `false` | 普通玻璃和玻璃板加入互溶。 |
| `blend_tinted` | `false` | 遮光玻璃加入互溶。 |

两种不同材质各自开启互溶后才会融合。升级时会沿用已有的 `border_width` 值。如需采用新的默认宽度，可以把该值改为 `0.0625`。

玻璃沿用半透明地形通道。最终的透明混色、反射和折射取决于光影包。实测环境为 NeoForge 21.1.265、Sodium 0.8.13、Iris 1.8.14-beta.1，关闭光影及开启 Complementary Unbound r5.9.3 均已在游戏里检查。

## 构建

安装 JDK 21 后，在源码目录运行 `bash ./gradlew build`。Windows 上运行 `.\gradlew.bat build`。产物位于 `build/libs`。构建会读取 `tools/glass-textures.zip` 中的 PNG。修改配色后，可运行 `python tools/generate_textures.py` 重新生成该贴图包。
