# 统一成长系统验收记录

本记录对应用户批准的统一成长计划。源码实现与静态检查已经完成；未获得构建指令，尚未编译、启动游戏或修改测试实例。以下“接入”不等于运行验收通过。

| 计划要求 | 当前源码证据 | 验证状态与剩余验收 |
|---|---|---|
| 全局开关统一属性、培养、装备、UI | GrowthConfig、HumanGrowth.multiplier/fullHealth、GrowthRewards、NaturalEquipmentGrowth、GrowthSnapshot/SoldierCommandScreen | 静态接入已核对；待运行开关关闭及重开、当前生命不增加、既有装备不替换 |
| 0～5级离散成长；经验逐百分点变化 | GrowthProgression.advance、HumanGrowth.award、GrowthHealth.apply | 已补 Java 阈值/余数/连升/封顶回归，尚未编译执行 |
| 生命40/60/80/100，成长后乘经验，枪伤不变 | 默认配置、Tier.maxHealth、HumanGrowth.fullHealth、HumanGunner.onLivingHurt 三个非枪械分支 | 源码契约通过；待实际伤害、外部属性修正和原版模式验收 |
| 服务器重载同步生命与原版近战规则 | HumanGrowth.tick、TierAttributes.applyCombatBase | 源码契约通过；待已有士兵切换原版模式及配置重载验收 |
| 统一个人在线时间和四阶曲线 | PlayerProgressionData.timeNear、GrowthConfig.stageAt、EquipmentGrowthPolicy.Profile.progress、出生状态 NBT | 不新增时钟；待多玩家、无有效玩家、离线/睡觉/时间指令及不同生成方式验收 |
| 野生状态固定；正式雇佣培养 | HumanGrowth.state/markBirth/award、GrowthCombat.formal、RecruitmentLedger | 静态接入已核对；待野生击杀不成长、临时支援不成长、解雇重雇保留状态 |
| 实际扣血、无尾刀门槛、严格超过300奖励 | LivingEntityGrowthDamageMixin、GrowthCombat.record、GrowthProgression.reward | 核对 hurt/setHealth SRG 签名；Java 奖励边界回归未执行；待真实护盾/吸收/过量伤害/回血/枪械归属验收 |
| 死亡取消、图腾、最终一击和幂等结算 | GrowthCombat.onDeath/onLeave/onPlayerClone/endHit/settle | 静态接入已核对；待 Mixin 注入和真实死亡事件顺序验收 |
| 保存出生、等级、余数、贡献与待领取奖励 | 实体 Forge PersistentData、GrowthRewards SavedData、reward_sequence | 已补 Java SavedData 回归，未运行；待区块卸载、重启、卸载士兵领取一次、拥有者变化验收。不承诺进程异常退出时跨存档文件的原子事务 |
| 关闭期间贡献作废、已获奖励和状态保留 | GrowthRewards.epoch/configurationReloaded、GrowthCombat.record/settle、HumanGrowth.state | 静态接入已核对；待关闭/重开及两次重载之间没有 tick 的实际测试 |
| 全局配置完整、旧参数清理、不生成继承文件 | 默认 growth 区域、GrowthConfig 校验、TierConfigProjection、SplitConfigFiles | 资源 JSON 解析通过；已有自定义配置缺失字段由内存默认补齐，不自动写入游戏值，实例配置仍待明确授权更新 |
| 服务器权威面板、禁用隐藏、延迟包不串实体 | HumanCommandNetwork 协议7、会话 token/UUID/entityId、维度/距离/雇主检查、GrowthSnapshot | UI 源码契约9项通过；待独立服务器、本地配置不同、多人查看、连续切换及小窗口排版验收 |
| 事件驱动且无新增持续范围扫描 | GrowthCombat 按 hurt/死亡事件汇总；面板每10tick比较快照，变更才发包 | 静态确认无目标范围扫描；尚无运行耗时/网络流量测量，不能声称已通过性能验收 |

## 本轮已执行

- Python UI 源码契约：9项通过。
- Python 服务器源码契约：11项通过。
- 全部281个资源 JSON 解析通过。
- 已移除 Java API 引用检查、git diff 检查通过。
- 只读 javap 和已有映射核对 Minecraft/Forge 目标方法签名。

## 后续验证顺序

1. 获得明确构建指令后，使用 Java17、仓库 gradlew.bat 和三个显式 API 路径执行适当构建与 Java 回归；先修复编译或测试失败。
2. 只有明确同步授权后才检查实例进程、备份和投放，核对 SHA-256；已有实例配置也需单独核对新增字段及玩家自定义值。
3. 由用户启动测试实例反馈游戏验收。优先测试总开关、出生与培养、奖励边界/死亡取消、存档恢复、服务器权威 UI，再测试多人战斗性能。

完整目标尚未运行验证，不能仅凭这些源码检查标记为全部验收完成。
