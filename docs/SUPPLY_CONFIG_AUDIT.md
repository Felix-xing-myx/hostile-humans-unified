# 食物、药水与护甲自由配置审查

适用当前五文件：`config/hostile_humans_unified/global.json` 与 `roamer/tier1/tier2/tier3.json`。源码修复已于2026-10-08构建并同步至约定测试实例；修改配置后需重启服务器，仅新生成单位领取出生物品，已存档士兵不重新发放。


## 2026-10-11 当前机制校正（e18ee5b）

- 当前服务端五文件为global+四阶。`global.ai`只保留enabled总开关，`item_recovery_enabled`及撤退/恢复细项在各阶ai；出生食物/药水在各阶food/potions，箭/弹匣数量在各阶ammunition。共用consume_ammunition位于global.ammunition，不能写到某阶期待独立免费弹药。
- 本阶成长曲线与早期材质/附魔/图腾/三叉戟策略位于growth_profile；共用阶段百分比、经验与奖励位于global.growth。仓库默认JSON是内部汇总结构，不等于玩家应继续把equipment_loadouts等旧聚合组写回global。
- `RecoverySupplies.provisionInitialKit`在实体数据准备后一次性发放，KIT_GRANTED先标记再按真实背包余量插入。库存满不会等待以后补齐，卸载/重启不补发；食物和药水关闭/空池语义仍按下方说明。
- 出生弹药由`HumanAmmoSpawn`按实际拥有枪/弓弩分别发放；满包先尝试价值替换，仍无空间的弹药作为真实掉落放在附近并设80 tick拾取延迟，不与食物/药水的空间不足路径混同。不是每个人类不论装备都领箭和弹匣。通用箭矢/弹匣是人类补给凭证，不能当玩家原版箭或TaCZ真实弹药使用。
- 自动拾取、出生发放、主动使用是不同机制。雇佣士兵自动拾取默认关闭，野生人类默认开启；打开后仍受战斗/撤退/手动背包会话及命令范围限制。背包策略保留食物位置并保护最好药物/备用装备，不只按总分盲目装满弹药。
- 食物、药水、First Aid与Scorched Guns医疗效果须区分。First Aid和Scorched配置在global对应联动区域；携带第三方物品不证明AI识别其用途。手动打开背包时暂停AI物品写入，不额外补给。
- 自然装备降级仅新出生自然/自动战斗人类执行一次；成长/日期变化不把已有护甲自动换新。装备自动导入追加候选和用户池抽选是不同层；严格池需要同时审视自动导入及早期装备策略。

核对源码：`TierConfigProjection`、`SplitConfigFiles`、`GrowthConfig`、`RecoverySupplies.provisionInitialKit`、`HumanAmmoSpawn`、`SoldierPickupPolicy`、`SupplyInventoryPolicy`、`NaturalEquipmentGrowth`。本次仅更新说明，未构建、同步或进行游戏验收。
## 完整加载链

1. FMLCommonSetup 的服务器配置准备调用 `UnifiedConfig.reloadForServer()`。`SplitConfigFiles.load` 先读 global，再读四阶。旧单文件和 TOML 已不作为设置来源。
2. 文件必须是有效 JSON 且 `schema_version=2`。坏文件原样保留，日志报错并在内存使用该文件的默认值；不会把部分成功字段拼进坏文件。现有文件只整理原字段，缺项仅内存补默认，不创建继承或迁移备份。
3. `TierConfigProjection.applyTier` 将各阶 food/potions 放入 `tiers.<阶级>`，装备放入 `equipment_loadouts.<阶级>`，AI放入 `tier_ai.<阶级>`。`UnifiedConfig` 合并缺项时保留显式数组和空数组，再形成不可变分阶快照。
4. 实体数据准备好后 `HumanSpawnEquipment.completeIfReady` 生成装备、护甲、武器备用、成长修正与弹药；`HumanGunner.configureCombatAi` 再调用 `RecoverySupplies.provisionInitialKit`。
5. 出生补给依赖全局 `ai.enabled` 和本阶 `ai.item_recovery_enabled`，另受各组 enabled 控制。NBT 的补给领取标记防止卸载/重载重发。库存满则发放数量可能小于配置要求，通常一瓶药水占一个背包槽。

## 2026-10-08 发现与修复（历史记录）

- **普通食物原先没有自由配置入口**：食物列表、分阶数量和金苹果机会写死在 `RecoverySupplies`；改备用武器 `equipment_loadout.inventory` 不会改变食物，因为该池明确只抽近战备用。新增各阶 `food`，通过上述相同加载链控制真实出生发放，删除食物的硬编码与空池牛排回退。
- 2026-10-08修复保留当时默认食物与配额；当前四阶各自的有效默认池、配额和概率以defaults/hostile_humans_unified.json及TierConfigProjection投影为准，不能把历史“三个分阶方案”理解为只有三阶可配。自定义可包括只堆叠一份的食物。候选无有效食物时不制造默认物品；未安装模组的ID跳过。
- **药水读取没有发现默认池覆盖自定义池的旁路**：`SpawnPotionConfig` 在生成时解析注册瓶形物品与药水，支持自定义注册的药水ID。空恢复/战术池、零数量和 disabled 都保留。新增无法解析或瓶形物品类型不支持的提示，防止静默跳过被误认为配置未读。
- **护甲读取没有发现直接丢弃自定义ID**：候选解析为整套并按权重抽取，某个已填写部位未注册/不属于对应 ArmorItem 部位会跳过整套。新增具体ID与部位提示。空数组不回退护甲。
- **护甲有明确的后续替换入口**：全局装备自动导入会追加候选；自然生成/自动战斗的 `NaturalEquipmentGrowth.applyOnce` 可把已选护甲降为早期护甲。这些独立配置机制仍有效。要严格限定套装，关闭global.json中的 `equipment_integration.auto_import_enabled`，并关闭 `growth.enabled` 以停用统一自然装备降级，然后删除本阶equipment_loadout池内不需要的已存在候选。当前已无独立 `equipment_growth.enabled`；growth开关同时影响属性与培养，如需保留培养，应保留growth并在本阶growth_profile中调整早期装备策略。
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

## 验证边界（原日期历史执行，2026-10-11未重跑）

新增 `SupplyConfigurationTest` 用真实临时五文件覆盖：从磁盘加载到分阶快照，自定义食物/数量、新药水ID/瓶数、自定义护甲整套、空池和关闭、不影响其它阶级、默认配额保留。已注册到 check，并于2026-10-08完整构建中执行，23项检查通过；配置、恢复行为等其它回归也通过。静态核查覆盖投射与实际发放接入，默认JSON除新food组外逐项不变，Java语法解析和差异检查通过。

待游戏验收：重启后新生成各阶实体检查背包/护甲；自定义食物单一池、药水零配额和新注册药水；关闭成长/导入时护甲单套；错ID/错部位日志；重载旧士兵不补发、背包满不破坏已有库存。

## 2026-10-11 焦燃投掷物全开放与共享战术池（源码待构建）

默认使用名单开放当前焦燃10种徒手投掷物：grenade、stun_grenade、nail_bomb、gas_grenade、choke_bomb、molotov_cocktail、hellfire_bomb、swarm_bomb、beacon_grenade、shotball。GrenadeItem原生工厂已覆盖其StunGrenadeItem子类，新增Shotball的原生公开实体构造接入；Shotball按原生0.04重力计算，其余沿用0.03，拖曳0.99。信号枪发射的袭击信号弹及榴弹枪弹药不是徒手投掷物，不纳入该库存投掷入口。仍消耗真实库存，由原生实体负责爆炸/特效；不增设自动无限投掷或第二套攻击目标。

所有阶级potions.tactical.pool新增item-only条目：scguns:grenade权重3、scguns:stun_grenade权重1、scguns:nail_bomb权重3。药水六种权重5/1/10/2/5/8不变，合计38。药水条目仍填写item+potion+weight；投掷物填写item+weight，省略potion。新生人类在同一池中逐件抽取，数量合计：流浪者1～2、一阶2～4、二阶4～7、三阶6～8。并非另外附送这些数量的手榴弹；没有保证每个单位都有手榴弹。不改变恢复药水池与恢复药水数量。

非药水仅允许已接入、全局允许且功能开启的焦燃投掷物；恢复池仍拒绝item-only条目。可选物品在出生时解析，未安装焦燃或投掷被禁用时剔除候选后重新按剩余权重抽取，不减少请求的战术总数。背包满时沿用插入失败停止规则。初始补给仍受分阶potions.enabled与ai.item_recovery_enabled控制，不给已生成单位补发。投掷物作为出生库存进入现有死亡掉落流程，不另加死亡战利品表，避免重复制造掉落。

TacticalSupplyDefaults只更新与此前完整默认列表精确相同的战术池/投掷使用名单；玩家非默认名单、自定义权重、空池与数量设置保留。当前配置原位更新，不生成继承或更新文件。全局使用名单与分阶出生池分开：默认允许使用全部10种，默认出生仅有三种；玩家可自行调整两者。

战斗使用保留冷却、许可、视线、友军安全与弧线检查。修复候选遍历：一种道具安全检查失败时继续寻找其他候选，防止毒气弹的较大安全半径阻止可用手榴弹。服务器拒绝生成实体时仍不扣物品、不重复重试。群体战斗/火焰/毒气/蜂群的实际伤害由焦燃负责，投出后队友进入区域仍可能危险。

验证：126项Python契约/默认配置/API检查通过，确认10个物品均在实际JAR注册表、Shotball公开构造存在、三种出生权重和总数不变。已补Java配置测试覆盖混合池解析、恢复池拒绝、默认更新与自定义/空池保留，但本轮未编译运行Java测试。未构建、未同步、未启动游戏。待验收自然新生库存、未安装焦燃、不同投掷物效果/弹道、友军安全、耗材与死亡库存掉落。

来源：SpawnPotionConfig、ScgunsThrowables、TacticalSupplyDefaults、SplitConfigFiles、RecoverySupplies、默认配置；基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加当前未提交工作区。
