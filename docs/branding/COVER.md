# 原生像素封面 / Authentic pixel cover

- Asset: `src/main/resources/logo.png` (1536 × 1024 PNG RGBA, transparent).
- Exact title: HOSTILE / HUMANS / UNIFIED.
- Method: deterministic pixel composition. No image generation or redrawing of item textures. Nearest-neighbour integer scaling only.
- Verification: opaque pixels of every inserted sprite exactly match the source, after integer scaling; transparent background, dimensions and existing mods.toml reference checked.
- Previous cover backup: `E:\MC\Create-Delight\HostileHumansUnified-维护\部署备份\20261010-214425-cover-before-authentic-assets\logo.png`.
- SHA-256: `68FB59751DB32879E3E7397198FD6C431816C918DD2B330D7ED48A44BC702F79`.

## 视觉原则 / Pixel identity

名称是第一视觉层，物品围绕文字保留清晰留白。字形遵循固定像素网格，以有限的阶梯色带塑造浅立体感，避免模糊阴影和随机材质。

装饰来自可追溯的真实游戏资源。精确保持像素边缘与原始色彩，细节来自物品自身，不用额外生成的枪、盾或身份牌替代。

标题以契约的暖纸色和弹匣的深轮廓建立可读性，副标题呼应三阶身份牌的紫色。字面始终完整，缩小时仍以模组名称为主。

构图的精细度来自基线、间距和像素一致性；武装、补给和雇佣的主题由真实物品表达。保留透明背景，便于模组列表与发布页面使用。

## Source textures

- `assets/hostile_humans_unified/textures/item/tier_three_identity_badge.png`; SHA-256 `68002ad687d01fd0baab419206f8953526ff6355a1b6a2d1aa95ae5b6f19ebbe`; output position [704, 136], nearest-neighbour scale 4×.
- `assets/hostile_humans_unified/textures/item/tier_three_contract.png`; SHA-256 `432c6d7c5e39e8f5d88a210007b17047dbf7267a88c23383803bad0fb97f91d0`; output position [40, 372], nearest-neighbour scale 8×.
- `assets/hostile_humans_unified/textures/item/human_arrow.png`; SHA-256 `b460269558724def43a703025108859440ac8fb02dfdd514e25771136f77e32c`; output position [52, 668], nearest-neighbour scale 8×.
- `assets/hostile_humans_unified/textures/item/human_magazine.png`; SHA-256 `6d4d45b990381ce47d86ac0a62d027266a7b6de31fb628df1b37f041c29749d4`; output position [1236, 624], nearest-neighbour scale 8×.

## Maintenance

Source item files are unchanged. The composition helper and preview are in the maintenance parent directory `封面设计`; gameplay resources contain only the final replacement logo. This source-only change does not rebuild, deploy or push a release.
