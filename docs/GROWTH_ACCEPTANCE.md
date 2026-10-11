# 统一成长系统验收记录

本记录对应用户批准的统一成长计划。源码实现与静态检查完成后，用户已授权构建同步。2026-10-10完整构建及Java回归通过，JAR与五份配置已同步至约定测试实例；没有启动游戏。以下“接入”不等于运行验收通过。

| 计划要求 | 当前源码证据 | 验证状态与剩余验收 |
|---|---|---|
| 全局开关统一属性、培养、装备、UI | GrowthConfig、HumanGrowth.multiplier/fullHealth、GrowthRewards、NaturalEquipmentGrowth、GrowthSnapshot/SoldierCommandScreen | 静态接入已核对；待运行开关关闭及重开、当前生命不增加、既有装备不替换 |
| 0～5级离散成长；经验逐百分点变化 | GrowthProgression.advance、HumanGrowth.award、GrowthHealth.apply | Java 阈值/余数/连升/封顶回归已编译执行并通过 |
| 生命40/60/80/100，成长后乘经验；枪械独立经验修正 | 默认配置、Tier.maxHealth、HumanGrowth.fullHealth、HumanGunner.onLivingHurt 三个非枪械分支 | 源码契约通过；待实际伤害、外部属性修正和原版模式验收 |
| 服务器重载同步生命与原版近战规则 | HumanGrowth.tick、TierAttributes.applyCombatBase | 源码契约通过；待已有士兵切换原版模式及配置重载验收 |
| 统一个人在线时间和四阶曲线 | PlayerProgressionData.timeNear、GrowthConfig.stageAt、EquipmentGrowthPolicy.Profile.progress、出生状态 NBT | 不新增时钟；待多玩家、无有效玩家、离线/睡觉/时间指令及不同生成方式验收 |
| 野生状态固定；正式雇佣培养 | HumanGrowth.state/markBirth/award、GrowthCombat.formal、RecruitmentLedger | 静态接入已核对；待野生击杀不成长、临时支援不成长、解雇重雇保留状态 |
| 实际扣血、无尾刀门槛、严格超过300奖励 | LivingEntityGrowthDamageMixin、GrowthCombat.record、GrowthProgression.reward | 核对 hurt/setHealth SRG 签名；Java 奖励边界回归已通过；待真实护盾/吸收/过量伤害/回血/枪械归属验收 |
| 死亡取消、图腾、最终一击和幂等结算 | GrowthCombat.onDeath/onLeave/onPlayerClone/endHit/settle | 静态接入已核对；待 Mixin 注入和真实死亡事件顺序验收 |
| 保存出生、等级、余数、贡献与待领取奖励 | 实体 Forge PersistentData、GrowthRewards SavedData、reward_sequence | Java SavedData 回归5项通过；待区块卸载、重启、卸载士兵领取一次、拥有者变化验收。不承诺进程异常退出时跨存档文件的原子事务 |
| 关闭期间贡献作废、已获奖励和状态保留 | GrowthRewards.epoch/configurationReloaded、GrowthCombat.record/settle、HumanGrowth.state | 静态接入已核对；待关闭/重开及两次重载之间没有 tick 的实际测试 |
| 全局配置完整、旧参数清理、不生成继承文件 | 默认 growth 区域、GrowthConfig 校验、TierConfigProjection、SplitConfigFiles | 资源 JSON 解析通过；已有自定义配置缺失字段由内存默认补齐，不自动写入游戏值，本次实例配置已备份后更新，保留无关有效值与自定义池 |
| 服务器权威面板、禁用隐藏、延迟包不串实体 | HumanCommandNetwork 协议7、会话 token/UUID/entityId、维度/距离/雇主检查、GrowthSnapshot | UI 源码契约9项通过；待独立服务器、本地配置不同、多人查看、连续切换及小窗口排版验收 |
| 事件驱动且无新增持续范围扫描 | GrowthCombat 按 hurt/死亡事件汇总；面板每10tick比较快照，变更才发包 | 静态确认无目标范围扫描；尚无运行耗时/网络流量测量，不能声称已通过性能验收 |

## 历史执行记录（2026-10-10，非本次重跑）

- Python UI 源码契约：9项通过。
- Python 服务器源码契约：11项通过。
- 全部281个资源 JSON 解析通过。
- 已移除 Java API 引用检查、git diff 检查通过。
- 只读 javap 和已有映射核对 Minecraft/Forge 目标方法签名。


## 2026-10-11 当前源码说明校正（e18ee5b，只读核对）

- **结算边界是每个真正死亡的目标，不是一次进入/退出战斗。** `GrowthCombat`在目标上按士兵UUID保存有效生命损失贡献；`settle`确认死亡后逐名结算。目标活着、卸载、逃离、死亡被取消均不因“脱战”支付奖励。无需最后一击；多名士兵各算各自贡献。
- 默认每名士兵对每个死亡目标：`min(4, floor(有效伤害/10)*0.25) + (有效伤害严格大于300 ? 4 : 0)`。300正好只得4，超过才得8；同一奖励同时进入阶段和经验进度。仅正式归属且记录有效的士兵可领取，临时援军不培养。
- 生命和非枪械近战/箭矢/投戟输出乘阶段×经验；枪械阶段不参与伤害，经验用独立附加散布和枪伤曲线。当前伤害分支已抽到`HumanDamageScaling`，旧表的`HumanGunner.onLivingHurt`是调用链入口引用。
- 当前五文件归属：`global.json`持有共用`growth`、`vanilla_mode`、全局生成/弹药/AI开关；四阶文件各持有`growth_profile`、`attributes`、`damage_multipliers`、`spawning.first_spawn_day`、`food`、`potions`、`ammunition`、`ai`细项、`equipment_loadout`和本阶枪械参数。已无独立`equipment_growth.enabled`配置；关闭统一成长用`global.growth.enabled=false`。
- 游戏修改配置按完整重启流程操作。文中`configurationReloaded`描述源码中服务端配置重载回调，不据此承诺一个未提供的玩家热重载指令。
- 下方构建、同步、测试数量和哈希是原日期历史证据，2026-10-11文档核对未再次构建、投放或启动游戏；原有游戏验收边界继续有效。
## 后续验证顺序

1. 已完成：Java17、仓库 gradlew.bat及三个显式API路径执行 build lootScreeningTest --offline，通过全部check；修正两处未跟随接口/配置调整的旧回归断言。
2. 已完成：确认无Java游戏进程，备份原JAR和五份配置后投放并核对SHA-256；全局成长参数集中、分阶完整生命40/60/80/100，静态比较其余有效值不变。
3. 由用户启动测试实例反馈游戏验收。优先测试总开关、出生与培养、奖励边界/死亡取消、存档恢复、服务器权威 UI，再测试多人战斗性能。

完整目标尚未运行验证，不能仅凭这些源码检查标记为全部验收完成。

## 2026-10-10 构建及投放证据

- 完整构建：BUILD SUCCESSFUL in 45s，50任务46执行4最新。日志位于维护目录 验证日志/build-3.7.0-growth-20261010-final.log；前两次失败日志保留，失败产物未投放。
- GrowthProgressionTest 23项、GrowthPersistenceTest 5项、GrowthConfigTest通过；UnifiedConfigTest 128项、TierConfigFilesTest 2068项、LootScreeningTest 13391项及其余check通过。坏配置/非法值测试的预期错误日志不代表正常配置加载失败。
- 源码基线 f50c84c。测试实例 E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21；唯一JAR hostile-humans-unified-1.20.1-3.7.0.jar，3829613字节，源/目标SHA-256：496A34D8FFA8477FD5468003674D3787A4F2E5A7CE0B952F9B08615198B468CD。
- 备份：部署备份/20261010-011704-3.7.0-growth-Forge_47.4.21；deployment.json记录旧件/备份/新件哈希及配置验证。未修改存档、同步其他实例、启动游戏或push；不将构建成功视为实际Mixin注入、游戏行为及运行性能已验证。

## 枪械经验与分档培养（历史开发阶段记录；构建结果见下文）

- 全局 `growth.experience.below_base_cost` 默认3；`cost_by_band` 默认 `[3,5,8,12,16]`，依次对应当前经验100–104、105–109、110–114、115–119、120以上时购买下一百分点的成本。低于100全部使用below_base_cost；高于默认125的自定义上限继续使用最后一档。旧cost及其注释在配置整理时删除，不生成迁移文件。
- 默认100%升至125%共需220成长值；90%升至100%另需30。阶段培养继续每级40，同一奖励分别进入两条进度。跨档逐点计算，不按奖励前的档位整批扣费；满经验时清空培养余数。面板下一百分点门槛由服务器发送，到顶为0；无需改变网络结构。
- 全局 `growth.firearms.spread_reduction_per_band_percent` 默认5，`minimum_spread_percent` 默认75；按100%经验为基准每5个百分点一档，90/100/110/125%经验的附加散布分别为110/100/90/75%。例如104%仍为100%，105%变为95%。仅改变人类额外瞄准误差，不修改TaCZ枪包固有散布；不能保证枪械最终散布缩至75%。
- `growth.firearms.maximum_damage_bonus_percent` 默认10：100%经验以下不减枪伤，100%至配置培养上限之间线性增加；默认每个百分点加0.4%，125%时枪伤乘1.10。若自定义上限不高于100%，无枪伤加成。分阶枪伤倍率仍先应用，阶段等级不影响枪伤。
- 野生人类使用固定出生经验，因此出生110%经验时已有90%附加散布和4%枪伤加成。正式雇佣后才能培养。成长关闭或原版模式开启时两项枪械修正均为1，不丢弃保存状态。
- 历史计划中的构建项已由下文“本次构建同步结果”覆盖：Java分档边界、跨档余数、封顶、可配置枪械参数、总开关/原版模式回归。仍待游戏内验收：普通开火及撤退开火共用散布、实际枪伤、经验升级后立即变化、服务器与客户端配置不同时的面板门槛。

### 本次构建同步结果

Java分档培养61项、GrowthConfigTest及完整build/check/lootScreeningTest通过。3.7.0新JAR已备份后同步Forge_47.4.21测试实例，global.json补齐新参数并清理旧cost，分阶自定义配置未改；SHA-256核对一致。详细部署记录见WORKSTREAMS及部署备份中的deployment.json。没有启动游戏，枪械散布/实际伤害与服务器面板仍待游戏内测试。

## 配置归属整理（历史开发阶段记录；已构建同步见下文）

共用growth继续全局；各阶曲线和早期装备细项现位于各阶growth_profile，不再放在global.growth.profiles。当前用户值安全搬移后才清理来源，不生成继承文件。新增经验档宽band_width_percent默认5，培养成本及枪械散布共用。文件增加分区标题和字段组留白、装备细项后置；默认有效平衡数值保持。新增Java文件搬移/格式稳定/优先级/坏文件与档宽回归待构建，45项Python源码契约已通过；此前部署仍是旧配置布局，该段记录时没有改实例；后续同步见下文。

### 配置整理构建与同步完成

本轮完整build/check/lootScreeningTest通过，Java配置搬移10项、分档培养61项、GrowthConfigTest与TierConfigFilesTest 2083项通过。使用正式加载器整理配置副本，静态比较除profile搬移及新增档宽5外全部有效值一致，随后备份并更新测试实例JAR和五配置，哈希核对通过。修复稀疏自定义块标题导致元数据往返变化的问题，失败产物未部署。实际游戏内配置效果与受困救援仍待用户测试。


## 2026-10-11 非枪械伤害阶段独立（源码待构建）

生命stage_percent维持50/60/70/80/90/100；新增damage_stage_percent为75/80/85/90/95/100，作用于近战、弓弩箭和投戟，之后再乘经验。两种枪械仍只调用gunDamageMultiplier，经验100%以下倍率为1，阶段不参与。已有全局配置缺失新数组时补入新项及说明，保留自定义数组和生命值。面板原百分比明确标为生命系数，无网络结构变化。新增独立配置、非法数组回退和两种枪械排除契约检查；134项静态/API检查通过，Java测试补充但未执行，未构建同步；伤害边界待游戏验收。


2026-10-11当前源码默认覆盖前述历史数值：完整生命40/40/60/80，近战和弓弩1.2/1.4/1.6/1.8，投戟1.0/1.1/1.2/1.3；各阶独立出生曲线不变，流浪者与一阶同完整生命，不再要求所有日期严格跨阶生命递增。待构建与游戏验收，未覆盖实例配置。


## 2026-10-11 19:44 3.7.0测试构建与同步

已用Java17执行离线build lootScreeningTest，BUILD SUCCESSFUL（43秒，53任务）；134项Python静态/实际API检查及6份默认附录核对通过。构建中修复了遭遇解锁日、装备满成长日的旧测试预期，以及高压参数/投掷初速度的双语说明格式。成功日志：E:\MC\Create-Delight\HostileHumansUnified-维护\验证日志\build-3.7.0-pressure-growth-20261011-194301.log。先前失败日志保留，失败产物未部署。

当前工作区3.7.0已同步到E:/MC/game/.minecraft/versions/1.20.1-Forge_47.4.21；投放前确认无Minecraft游戏进程。唯一目标JAR 4020836字节，源/目标SHA-256：9CAB70A9F9A4BB87722E528F07E3F58D30016E494AA11E6DB24462D1F8793B24。

备份旧JAR及global/roamer/tier1/tier2/tier3五份配置至：E:\MC\Create-Delight\HostileHumansUnified-维护\部署备份\20261011-194424-3.7.0-pressure-growth-Forge_47.4.21。deployment.json记录逐项变更及配置哈希。本次同步明确指定的新默认：完整生命40/40/60/80、近战/弓弩1.2/1.4/1.6/1.8、投戟1.0/1.1/1.2/1.3、四阶时间成长50日、流浪者最早第2日、焦燃全局枪伤0.7；补入非枪械阶段75/80/85/90/95/100及高压防御配置。其他阶级设置逐项核对保持不变，既有的新曲线/高压自定义项不强制覆盖。

包含此前待构建的声音/换弹时长、投掷弹道与战术池、背盾贴近、弓弩防御后台准备、高压中断恢复及枪手换近战举盾等源码修改。源码基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加当前未提交工作区，无新增提交或推送。未启动游戏；格挡衔接、声音、投掷落点、渲染、后台换弹和实际伤害/成长仍待用户游戏验收。以上测试投放状态覆盖本页此前对应修改的“待构建”状态，不表示已通过游戏验收。
