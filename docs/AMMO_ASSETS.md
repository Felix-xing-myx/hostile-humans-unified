# 弹药贴图与配方

2026-10-06；主控制作，内置 imagegen 生成，System.Drawing 仅以最近邻缩放整理为32×32 RGBA PNG。参考既有信号弹及身份牌的小尺寸像素道具、深色轮廓与金属高光。生成原图留在本机 Codex 生成目录，项目使用的最终文件均在仓库内。

- 箭矢：`src/main/resources/assets/humangunner/textures/item/human_arrow.png`。
- 弹匣：`src/main/resources/assets/humangunner/textures/item/human_magazine.png`。
- 对应物品模型引用 `humangunner:item/human_arrow`、`humangunner:item/human_magazine`，无需资源包或外部文件。
- 无序配方：4箭＋1木棍＋1羽毛→32通用箭矢；3火药＋2铁锭→2通用弹匣。材料全部为原版物品；后者采用 Forge `mod_loaded` 条件，仅安装TaCZ时加载。

## 最终生成提示词

### human_arrow

Use case: stylized-concept. Asset type: Minecraft 1.20.1 inventory item PNG texture for Hostile Humans Unified. Generate ONE isolated sprite of a compact bundle of three military supply arrows, wooden shafts in muted brown, steel gray pointed arrowheads, cream feather fletchings, bound by a small desaturated olive green cloth band. Diagonal lower-left to upper-right, clear silhouette matching vanilla Minecraft inventory items and the mod's restrained dark outlines and small metallic highlights. Strict very low resolution pixel art: design on a 32 by 32 logical pixel grid, enlarge with nearest-neighbor crisp square pixels, only about 12 flat palette colors, no antialias, no blur, no painterly detail, no text, no labels, no frame, no ground shadow. Bundle fills 85 percent of square canvas with transparent margin. Actual transparent background, not a checkerboard drawing. It must remain readable when reduced to 32x32. Arrow bundle only, no ammunition magazine.

### human_magazine

Use case: stylized-concept. Asset type: Minecraft 1.20.1 inventory item PNG texture for Hostile Humans Unified. Generate ONE isolated universal gun magazine sprite, single curved rectangular rifle box magazine, angled 25 degrees clockwise, dark gunmetal gray casing, three vertical stamped grooves, desaturated olive-green cloth tape around lower third, two small copper/brass cartridge tips visibly poking out the open top. Distinct utilitarian military supply silhouette. Vanilla Minecraft inventory pixel-art aesthetic, restrained dark outlines and steel-gray small highlights. Strict very low resolution pixel art: design on a 32 by 32 logical pixel grid and enlarge with nearest-neighbor crisp square pixels, around 12 flat palette colors, no antialias, no blur, no painterly detail, no text, no labels, no logos, no frame, no cast shadow. Magazine fills 80 percent of square canvas with transparent margin. Actual transparent background, not a checkerboard drawing. Must remain readable at 32x32. One magazine only, no gun or arrows.


## 弹匣简化：2026-10-06

用户反馈远处贴图杂乱，使用内置 imagegen 编辑旧图，保留深色金属/橄榄绿风格，以大面积灰色主体、单条侧缘高光和底部色带替代细碎沟槽。最终仍为同路径32×32透明PNG，由System.Drawing最近邻缩放；物品ID和模型路径保持原样。箭矢显示名改为通用箭矢 / Universal Arrow。

本次提示词：Edit target: the supplied Minecraft magazine inventory sprite. Use case: precise-object-edit. Replace its cluttered tiny grooves and scattered highlights with a MUCH SIMPLER bold silhouette readable at 16x16 and 32x32. One single slightly curved rectangular rifle magazine, dark gunmetal broad flat face, thick almost-black outside outline, one broad steel-gray highlight along a single edge, a single desaturated olive green band near the base, at most one brass cartridge tip on the top. Angle clockwise about 20 degrees as before. Keep the utilitarian Hostile Humans Unified military supply style. STRICT coarse pixel art designed on a 32x32 logical grid enlarged nearest-neighbor, 5 to 7 flat colors, no thin interior lines, no grooves, no mottled pixels, no realistic reflections or detail, no dithering, no text or logos, no shadow, transparent background with preserved alpha. Fill 85% of square canvas. Exactly one magazine and nothing else. Prioritize large continuous color regions and clear contrast at inventory scale.
