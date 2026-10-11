# 用户指定封面 / User-selected cover

- 源图：`C:/Users/Felix/Desktop/Hostile Humans Unified 封面1.png`，1448×1086 RGB PNG，1,563,671字节。桌面原图未修改。
- 模组资源：`src/main/resources/logo.png`，768×576、256色索引PNG，207,356字节（约203KiB）。
- 压缩：保持4:3比例，以Lanczos缩小后量化为256色，不抖动；PNG优化及最高压缩等级。原图保留，压缩版体积减少约86.7%。缩放和调色板量化会有轻微细节损失，未改动排布、文字或图中元素。
- 入口：沿用`META-INF/mods.toml`的`logoFile="logo.png"`。
- SHA-256：`B4B1808CBB1169E681A388349A03812F9515A7AF426FA71833CEC78696ED3B5E`。
- 前一封面备份：`E:\MC\Create-Delight\HostileHumansUnified-维护\部署备份\20261011-122741-cover-before-user-image\logo.png`。
- 核查：PNG解码/尺寸/文件大小、原图哈希未变化、人工检查标题及物品清晰度。封面修改当时只更新源码资源；后续2026-10-11测试构建已包含压缩封面并同步Forge 47.4.21测试实例，见CHANGELOG与WORKSTREAMS中的投放记录。游戏内显示验收仍待反馈，后续最新背盾/双枪修正未构建。
