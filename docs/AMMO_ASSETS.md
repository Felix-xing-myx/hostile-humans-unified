# 弹药贴图与配方

2026-10-06；主控制作，内置 imagegen 生成，System.Drawing 仅以最近邻缩放整理为32×32 RGBA PNG。参考既有信号弹及身份牌的小尺寸像素道具、深色轮廓与金属高光。生成原图留在本机 Codex 生成目录，项目使用的最终文件均在仓库内。

- 箭矢：`src/main/resources/assets/humangunner/textures/item/human_arrow.png`。
- 弹匣：`src/main/resources/assets/humangunner/textures/item/human_magazine.png`。
- 对应物品模型引用 `humangunner:item/human_arrow`、`humangunner:item/human_magazine`，无需资源包或外部文件。
- 无序配方：4箭＋1木棍＋1羽毛→32人类箭矢；3火药＋2铁锭→2通用弹匣。材料全部为原版物品；后者采用 Forge `mod_loaded` 条件，仅安装TaCZ时加载。

## 最终生成提示词

### human_arrow

Use case: stylized-concept. Asset type: Minecraft 1.20.1 inventory item PNG texture for Hostile Humans Unified. Generate ONE isolated sprite of a compact bundle of three military supply arrows, wooden shafts in muted brown, steel gray pointed arrowheads, cream feather fletchings, bound by a small desaturated olive green cloth band. Diagonal lower-left to upper-right, clear silhouette matching vanilla Minecraft inventory items and the mod's restrained dark outlines and small metallic highlights. Strict very low resolution pixel art: design on a 32 by 32 logical pixel grid, enlarge with nearest-neighbor crisp square pixels, only about 12 flat palette colors, no antialias, no blur, no painterly detail, no text, no labels, no frame, no ground shadow. Bundle fills 85 percent of square canvas with transparent margin. Actual transparent background, not a checkerboard drawing. It must remain readable when reduced to 32x32. Arrow bundle only, no ammunition magazine.

### human_magazine

Use case: stylized-concept. Asset type: Minecraft 1.20.1 inventory item PNG texture for Hostile Humans Unified. Generate ONE isolated universal gun magazine sprite, single curved rectangular rifle box magazine, angled 25 degrees clockwise, dark gunmetal gray casing, three vertical stamped grooves, desaturated olive-green cloth tape around lower third, two small copper/brass cartridge tips visibly poking out the open top. Distinct utilitarian military supply silhouette. Vanilla Minecraft inventory pixel-art aesthetic, restrained dark outlines and steel-gray small highlights. Strict very low resolution pixel art: design on a 32 by 32 logical pixel grid and enlarge with nearest-neighbor crisp square pixels, around 12 flat palette colors, no antialias, no blur, no painterly detail, no text, no labels, no logos, no frame, no cast shadow. Magazine fills 80 percent of square canvas with transparent margin. Actual transparent background, not a checkerboard drawing. Must remain readable at 32x32. One magazine only, no gun or arrows.
