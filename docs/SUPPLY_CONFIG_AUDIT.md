# 食物、药水与护甲自由配置审查

适用当前五文件：`config/hostile_humans_unified/global.json` 与 `roamer/tier1/tier2/tier3.json`。源码修复尚未构建部署；修改配置后需重启服务器，仅新生成单位领取出生物品，已存档士兵不重新发放。

## 完整加载链

1. FMLCommonSetup 的服务器配置准备调用 `UnifiedConfig.reloadForServer()`。`SplitConfigFiles.load` 先读 global，再读四阶。旧单文件和 TOML 已不作为设置来源。
2. 文件必须是有效 JSON 且 `schema_version=2`。坏文件原样保留，日志报错并在内存使用该文件的默认值；不会把部分成功字段拼进坏文件。现有文件只整理原字段，缺项仅内存补默认，不创建继承或迁移备份。
3. `TierConfigProjection.applyTier` 将各阶 food/potions 放入 `tiers.<阶级>`，装备放入 `equipment_loadouts.<阶级>`，AI放入 `tier_ai.<阶级>`。`UnifiedConfig` 合并缺项时保留显式数组和空数组，再形成不可变分阶快照。
4. 实体数据准备好后 `HumanSpawnEquipment.completeIfReady` 生成装备、护甲、武器备用、成长修正与弹药；`HumanGunner.configureCombatAi` 再调用 `RecoverySupplies.provisionInitialKit`。
5. 出生补给依赖全局 `ai.enabled` 和本阶 `ai.item_recovery_enabled`，另受各组 enabled 控制。NBT 的补给领取标记防止卸载/重载重发。库存满则发放数量可能小于配置要求，通常一瓶药水占一个背包槽。

## 发现与修复

- **普通食物原先没有自由配置入口**：食物列表、分阶数量和金苹果机会写死在 `RecoverySupplies`；改备用武器 `equipment_loadout.inventory` 不会改变食物，因为该池明确只抽近战备用。新增各阶 `food`，通过上述相同加载链控制真实出生发放，删除食物的硬编码与空池牛排回退。
- 默认食物19种、原三个分阶数量方案、金苹果数量、附魔金苹果机会保留。自定义可包括只堆叠一份的食物。候选无有效食物时不制造默认物品；未安装模组的ID跳过。
- **药水读取没有发现默认池覆盖自定义池的旁路**：`SpawnPotionConfig` 在生成时解析注册瓶形物品与药水，支持自定义注册的药水ID。空恢复/战术池、零数量和 disabled 都保留。新增无法解析或瓶形物品类型不支持的提示，防止静默跳过被误认为配置未读。
- **护甲读取没有发现直接丢弃自定义ID**：候选解析为整套并按权重抽取，某个已填写部位未注册/不属于对应 ArmorItem 部位会跳过整套。新增具体ID与部位提示。空数组不回退护甲。
- **护甲有明确的后续替换入口**：全局装备自动导入会追加候选；自然生成/自动战斗的 `NaturalEquipmentGrowth.applyOnce` 可把已选护甲降为早期护甲。这些独立配置机制仍有效。要严格限定套装，关闭 `equipment_integration.auto_import_enabled` 和 `equipment_growth.enabled`，并删除池内不需要的已存在候选。
- **携带与使用不同**：药水 item 通常是 `minecraft:potion`；potion 是药水注册ID，不是 `minecraft:instant_health` 等效果ID。AI治疗、再生、力量、速度和投掷减益仍按既有策略使用，不保证自动使用任何第三方自定义效果。药水配置仅定义出生携带内容。

## 分阶食物示例

将该组放在对应阶文件的顶层，不放 global。名称里的 `example` 需替换成已安装模组真实ID。

```json
"food": {
  "enabled": true,
  "variety_min": 1,
  "variety_max": 1,
  "enchanted_golden_apple_chance": 0,
  "presets": [
    {"weight": 1, "count_min": 32, "count_max": 32, "golden_apple_min": 0, "golden_apple_max": 0}
  ],
  "pool": [{"item": "example:custom_meal", "weight": 1}]
}
```

食物总数范围0至1920、普通金苹果0至64、种类数1至30。`food.enabled=false` 关闭食物和两种金苹果；空 food.pool 只禁普通食物，普通金苹果数量与附魔金苹果概率独立控制。空 presets 禁食物与普通金苹果，不改变附魔概率。已有阶文件没有food时采用原配额，手动添加该组即可覆盖；新生成阶文件含完整说明。

## 验证边界

新增 `SupplyConfigurationTest` 用真实临时五文件覆盖：从磁盘加载到分阶快照，自定义食物/数量、新药水ID/瓶数、自定义护甲整套、空池和关闭、不影响其它阶级、默认配额保留。注册到 check，但本轮未编译执行。静态核查覆盖投射与实际发放接入，默认JSON除新food组外逐项不变，Java语法解析和差异检查通过。

待构建后的自动回归及游戏验收：重启后新生成各阶实体检查背包/护甲；自定义食物单一池、药水零配额和新注册药水；关闭成长/导入时护甲单套；错ID/错部位日志；重载旧士兵不补发、背包满不破坏已有库存。
