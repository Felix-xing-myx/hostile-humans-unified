# 开发任务与整合记录

由主控维护。此处状态是有日期的核查记录；开始任务前重新检查 Git 和文件，不能将历史快照当作当前状态。

## 首次接管：2026-10-06

| 项目 | 实际核查结果 |
| --- | --- |
| 主仓库 | `E:\MC\Create-Delight\HostileHumansUnified-维护\project` |
| 维护上级目录 | `E:\MC\Create-Delight\HostileHumansUnified-维护` |
| 分支 / 基线 HEAD | `main` / `5c7c4dcd06d5a3f4a502cb71e591766d5e619fe7` |
| origin | `https://github.com/Felix-xing-myx/hostile-humans-unified.git`（本地 remote 核查，未连接远端） |
| 版本 / 平台 | `build.gradle`: 3.6.0；`gradle.properties`: Minecraft 1.20.1 / Forge 47.4.16；Java toolchain 17 |
| 接管时工作树 | 仅主工作树，尚无并行开发工作树 |
| 接管时未提交内容 | 仅 `CHANGELOG.md`，暂存区为空；3.6.0 中英文段落重排，原样保留，不纳入接管提交 |
| CHANGELOG 原始 SHA-256 | `340C905E8C54142D3C0FC15961D82539DA50BD9FAC7C416055A43C4E7D039EC2` |
| 既有协作规则 | 上级 `E:\MC\Create-Delight\AGENTS.md` 要求对漏洞、风险和证据冲突主动提出依据与替代方案；接管前源码根目录无 AGENTS.md |
| 构建输入 | `D:\Java\17\bin\java.exe` 和三个指定 API JAR 均存在；只检查存在性，未运行 Java/Gradle、未校验 API 内容 |
| 发布目标 | 尚未指定，不建立默认同步范围 |

## 当前源码入口

下列关系来自此次静态源码阅读，仅用于后续分工定位，不代表已完成游戏内验收。

| 范围 | 当前入口与约束 |
| --- | --- |
| Better Combat 服务端近战 | `club.someoneice.humangunner.BetterCombatMeleeCombat` 读取可选武器配置、范围、冷却与命中框；`MeleeAttackTiming` 优先采用其冷却，否则使用阶级配置回退；`MeleeCombatRange` 共用攻击几何 |
| 延迟攻击 | `Human.doHurtTarget` 建立攻击动画与待出伤状态；`finishBetterCombatUpswing` 重新检查目标、武器与逃跑状态，并按实时攻击计划出伤；不能再引入第二套独立出伤调度 |
| Better Combat 动画 | `BetterCombatNpcAnimator` 维护 NPC 播放状态；`HumanPlayerModel` 接入动作，`HumanRenderer` 接入身体变换和自定义手持物层 |
| TaCZ 动画 | `TaczNpcAnimator` 使用 NPC 动画状态及 Player Animator 层；`PlayerAnimatorModelBridge` 连接模型变形；`TaczNpcAnimatorMixin` 已在客户端 Mixin 配置注册以阻止旧 TaCZ 人形姿势覆盖 |
| 战术 AI | `AdaptiveCombatGoal` 协调防御、撤退、恢复、侧绕和脱困，与既有武器 Goal 交接移动控制；修改时审查导航所有权和中断恢复 |
| 公共配置 | `UnifiedConfig` 为重启范围的配置快照；已有 `reloadForServer()` 及 Better Combat 配置读取；公共配置接口由主控协调 |
| 自动化验证入口 | `build.gradle` 已注册 loot、defensive、Better Combat animation timing、optional runtime、config、maintenance 等验证任务，部分属于 `check`；本次均未执行 |

## 任务登记

| ID | 任务 / 状态 | 基线 | 负责人 / 会话 | 工作树 / 分支 | 修改范围 | 接口约定 | 交付 / 验证 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TAKEOVER-20261006 | 首次接管及协作规范 / 已完成 | `5c7c4dcd06d5a3f4a502cb71e591766d5e619fe7` | 主控 / 当前接管聊天 | 主仓库 / `main` | 新建 `AGENTS.md`、`docs/WORKSTREAMS.md` | 无运行接口变化；不修改或提交既有 CHANGELOG 改动 | 交付为本记录所在的文档提交，可用 `git log -- AGENTS.md docs/WORKSTREAMS.md` 定位；静态核对及文档差异检查，无构建/同步/push/游戏测试 |

## 2026-10-06 首批修复任务

共同基线：`31aa9329b02f789e2bedfdd5b1d2f57503b46072`。主仓库未提交的 CHANGELOG 中英文重排不带入开发工作树，主控保留。未授权构建、同步、推送或启动游戏。

| ID | 任务 / 状态 | 负责人 | 工作树 / 分支 | 修改范围与接口约定 |
| --- | --- | --- | --- | --- |
| TACZ-01 | 持枪动画重追踪/传送冻结修复；无 Player Animator 动画方案 / 已整合，待构建与游戏验收 | tacz_animation 子 Agent，GPT-6.1 Sol / medium | `E:\MC\Create-Delight\HostileHumansUnified-维护\worktrees\tacz-animation` / `fix/tacz-animation-lifecycle` | 客户端 renderer 动画模块、`TaczNpcAnimatorMixin`、独立策略测试；不改 Human、HumanGunner、配置、版本或日志。保留 Better Combat 接口和服务端出伤权限 |
| LOOT-01 | 敌对信标食物保留原掉落期望的 20%，不掉附魔金苹果 / 已整合，待构建与游戏验收 | beacon_drops 子 Agent，GPT-6 Luna / medium | `E:\MC\Create-Delight\HostileHumansUnified-维护\worktrees\beacon-drops` / `fix/beacon-food-drops` | 信标来源、HumanEquipmentDrops 最终掉落集合和独立策略；非食物不变；用户明确正式雇佣后完整返还库存 |

主控负责公共构建/测试任务接入、更新日志、版本决策、交付审核与本地整合。子 Agent 未再分派其他 Agent；仅使用上述两个模型及独立目录。

### 交付与审核结果

- TACZ-01：开发提交 `92d13f7d24dbd85e9f320c1cbda05227abbb7ae5`，整合提交 `ed127f5`。文件为 ClientRenderer、HumanPlayerModel、TaczNpcAnimator、TaczNativeAnimation、TaczAnimationPolicy、TaczNpcAnimatorMixin 及 TaczAnimationPolicyTest。保留 `apply`、`isAimingOrFiring`、`hasActiveCustomAnimation`，新增包内 `resetPlayback()`。
- 动画审核：实际只读 TaCZ 1.1.8-hotfix JAR 的 javap 确认原生 ThirdPersonManager/IThirdPersonAnimation 仅提供持枪/瞄准（部件顺序 rightArm、leftArm、body、head）；基础换弹/射击等用同步状态驱动模型，完整枪包关键帧仍依赖 Player Animator。时钟回退、渲染间隔、位置跳变、模型变化和资源重载重建状态；EMF 优先级读取实时动作。每帧模型复位及失败关键帧回滚防止姿势残留，未改变攻击/弹药逻辑。
- LOOT-01：开发提交 `d29731adb95147a155134a8c2888a992f64020b7`、`59411447dd0d6aba46cee8ddbd665bfc07444727`，对应整合提交 `a3fef26`、`2949971`。文件为 HumanEquipmentDrops、HumanRelations、HumanSignalItem。第一版仅覆盖库存，主控发现实体 loot table 也产食物后要求修订为最终集合过滤，避免漏掉或重复削减。
- 来源持久化：新单位写入 `humangunner:hostile_beacon_origin`，仍保留旧 `forced_hostile_target` 的单位会固化来源。已经在旧版本丢失旧标记的历史实体无法凭空恢复来源。按死亡快照正式雇佣状态排除；未雇佣时对现有食物结果逐件再保留 20%，附魔金苹果归零，非食物不变。原补给 20% 的库存路径最终期望为原携带量 4%；战利品表按其自身原结果再乘 20%。
- 主控补充 HostileBeaconFoodPolicy 与概率边界/组合削减测试，并将 `hostileBeaconFoodPolicyTest`、`taczAnimationPolicyTest` 接入 `check`。README 中英说明基础动作与资源关键帧区别；版本保留 3.6.0，日志新增未发布栏。既有 CHANGELOG 中英文重排保留为用户未提交修改，未混入任务提交。
- 实际验证：当前源码调用关系审查、TaCZ API javap 核对、Git 差异/暂存范围检查。未运行 Gradle、编译、自动化测试、游戏、同步或 push。新增测试仅已接入，不能写成已通过。
- 待游戏验收：持枪跟随同维度/跨维度传送、离开追踪范围后返回、重新登录与资源重载；有/无 Player Animator，EMF 资源包，移动/瞄准/开火/换弹切换，护甲/持物和左右手；Better Combat 切换及无 TaCZ 启动；四阶信标的最终食物统计/附魔苹果、自然生成对照，以及雇佣后自存食物/附魔苹果完整返还。
- 两项无文本合并冲突；独立开发工作树保留且干净，便于后续反馈定位。冻结现象尚未游戏复现，修复覆盖源码可确认的生命周期缺口，不将其宣称为已确认的唯一根因。

## 指定实例构建与投放：2026-10-06

- 用户明确目标：`E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21`，仅此实例。
- 版本沿用 3.6.0；构建源码提交 `dcfef5fb8073e4414ab03860ccaef9b043c3bd96`，用户 CHANGELOG 旧段落重排保留在工作区。
- Java 17，仓库 gradlew.bat，`build lootScreeningTest --offline`，三个指定 compileOnly API 路径均以完整参数传入。成功日志：维护目录 `验证日志/build-20261006-animation-beacon-retry.log`。
- 首次构建在 optionalRuntimeTest 发现 TaczNativeAnimation 的 lambda 合成方法暴露 GunDisplayInstance；主控移除该 lambda，保持未安装 TaCZ 时加载检查，未通过跳过测试规避问题。修复提交为上述源码提交。
- 重试 BUILD SUCCESSFUL：27 actionable tasks，23 executed / 4 up-to-date；TaCZ 动画策略、信标食物概率、无可选依赖链接（332 核心类）、战斗、库存、配置、结构及 lootScreening 等全部通过。结构/配置坏输入检查的错误日志是预期兜底场景。此前“未执行”的记录为开发阶段状态，现更新为自动化通过、游戏验收待定。
- 替换前再次检查进程，未检测到运行游戏；目标 mods 中仅一份统一版 JAR，TaCZ 为 1.1.8-hotfix，目标 Forge 47.4.21 在模组声明范围内。
- 旧文件备份：维护目录 `部署备份/20261006-122206-animation-beacon-Forge_47.4.21/hostile-humans-unified-1.20.1-3.6.0.jar`，旧文件与备份 SHA-256 均为 `4EFDB98536D1142D374CFA6554040EBE648B55A49513BBB9EC93CFB90B1C41E0`。
- 构建来源：`build/libs/hostile-humans-unified-1.20.1-3.6.0.jar`；替换目标：指定实例 `mods/hostile-humans-unified-1.20.1-3.6.0.jar`。源/目标 SHA-256 均为 `48349D249CA65CA7223B5F14C132BF7A943D1FDE9E73EB4628370EC7CEDFAA16`，目标大小 3,639,281 字节，仅一份统一版 JAR。
- 备份目录的 `deployment.json` 保存完整清单。未改其他实例、未启动游戏、未 push；传送/重追踪与资源包组合、掉落统计及雇佣完整返还仍待用户游戏验收。

## 有限弹药开发：2026-10-06

共同开发基线 `9ca9c54c83292190fe62587d586f5dbb66ccb3a1`（已包含共享 HumanAmmo/HumanAmmoPolicy 与 GunSupport.hasLoadedAmmo 接口）；主仓库原 CHANGELOG 重排保留。此轮只授权开发，不构建/同步/push/启动游戏。

| ID | 负责人 / 模型 | 工作树 / 分支 | 独占文件范围与接口 |
| --- | --- | --- | --- |
| AMMO-GUN | finite_gun 子 Agent / GPT-6.1 Sol medium | 维护目录 `worktrees/finite-gun-ammo` / `feature/finite-gun-ammo` | TaczIntegration、GunnerGoal、独立换弹事务模块/测试；所有换弹入口共用扣费，修复无偿补满。实现 GunSupport.hasLoadedAmmo，不修改共享 HumanAmmo/GunSupport 定义 |
| AMMO-BOW | finite_bow 子 Agent / GPT-6.1 Sol medium | 维护目录 `worktrees/finite-bow-ammo` / `feature/finite-bow-ammo` | Human.java、BowAttack、CrossbowGoal、SpartanRangedCompat 及独立射击策略/测试；每次弓射击/弩齐射消耗一个 HumanAmmo 箭矢，三叉戟不变 |
| AMMO-FALLBACK | ammo_fallback 子 Agent / GPT-6.1 Sol medium | 维护目录 `worktrees/ammo-weapon-fallback` / `feature/ammo-weapon-fallback` | GunCustody、RangedWeaponCustody、HumanLootManager、必要独立武器选择模块/测试；有弹枪优先，其次有箭弓弩，再近战；补给恢复可用武器，库存守恒 |

主控负责物品注册与资源、HumanAmmo 公共实现、生成弹药、文档/版本与公共测试任务接入。公共文件不交叉编辑，子 Agent 不再分派。用户已确认霰弹枪每次换弹消耗2个，与步枪相同。

### 交付与整合（已整合）

- AMMO-BOW：开发提交 `686a83d7003ec475c66084d147f9815a91e2ab34`，整合 `f7808b2`。Human、BowAttack、CrossbowGoal、SpartanRangedCompat、HumanArrowCombat、HumanInventoryContainer、HumanArrowCombatTest。菜单文件的新增归属在工作中明确交给该 Agent；普通弓、原生弩和 Spartan 重弩同一储备，每次成功射击/齐射扣1，多重射击不逐箭扣。装填后只保存空 charged 动作，发射即时合成载荷并禁止回收；玩家投入的实际 charged 载荷不在菜单转出时清除，NPC发射仍以统一箭矢计费。主控补充 finally 清理，异常钩子也不留下临时载荷。
- AMMO-FALLBACK：开发提交 `94758b35159371d2f4132ce6e423abe1572125be`，整合 `25f06a2`。AmmoWeaponSelection、GunCustody、RangedWeaponCustody、HumanLootManager、AdaptiveCombatGoal、PlayerLikeMovementGoal、AmmoWeaponSelectionTest；后两公共战术文件在工作中明确追加为独占范围。物理拥有枪接口保留，战术改为实际可用枪；已付费换弹保留手持至完成，可用枪→有箭弓弩→现有近战/工具→空手。原位交换优先，满包掉落插入失败不清手持；租约快照不重建武器。补给价值100并沿用拾取设置，不能作为武器装备。
- AMMO-GUN：开发提交 `ac3ccbff74f9069aed1cb6dd7e729175ed09d5be`、`936aed6abfc2821f600d51809be02327ccfe83ea`，整合 `7fb02be`、`bc5de4b`。TaczIntegration、GunnerGoal、ReloadPaymentLedger、TaczHumanReloadTransactions、TaczHumanReloadMixin/TaczHumanShootMixin/TaczHumanGunScriptMixin、OptionalRuntimeTest、ReloadPaymentLedgerTest。收费位于原生取消事件/重复/冷却过滤之后、startReload成功时；逐发装填在完整周期只付一次。授权仅内存并绑定实际 ItemStack，取消/切枪/超时不能继承免费装填。INVENTORY供弹枪转为NPC有限弹仓，玩家保留原路径；补给耗尽时不能续装。
- 主控 `baba2eb` 注册箭矢64/弹匣24、物品模型与中英提示，弹匣只在安装TaCZ时显示创造栏但注册ID始终稳定；生成管线在最终成长替换后按实际持有武器配给，四阶数量遵照需求并拆成合法堆叠。初始枪内子弹保留；仅初次生成授予，旧存档/雇佣/重加载不自动补满。自定义满包通过现有价值/保护规则存入，若全部槽位受保护则溢出物理掉落；取消掉落不会覆盖现有装备。未新增合成配方或弹药数量配置。
- 主控 `e965d6d` 补充静态AI模式和枪手Goal弹药门控，避免未能收起的无弹副手堵住近战。`557511b`、`7b039f5` 修复INVENTORY枪膛内计数导致满弹重复闲置收费，以及开膛枪旧barrel标记虚构剩余一发；定时装填使用枪包空仓/战术 feed+cooldown 时间，恢复超时预算独立。
- 用户追加换弹阈值后，`7a9d2dc`、`ecf8026` 整合：闲置严格低于25%（30发剩7触发、8不触发；40发剩10不触发），战斗必须实际弹仓/膛内皆空；统一收费入口也复核阈值。既有闲置逃跑/使用物品保护保留，战斗逃跑不被新增闲置限制误拦截。已开始的有效周期继续，不因随后进入战斗而自动退费。
- 公共测试入口已接入 `check`：humanAmmoPolicyTest、humanArrowCombatTest、ammoWeaponSelectionTest、reloadPaymentLedgerTest；更新旧 CombatPressurePolicyTest 的闲置预期。新增用例覆盖阶级预算、类型成本、25%边界、最后一份库存、拒绝/重复/取消/超时、武器对象变化、齐射/装填/不可回收接线及满包交换守恒。版本仍为3.6.0，有限弹药与阈值记录在未发布栏。
- 实际验证：三个 Agent 与主控执行 Git diff/暂存检查；主控核对实际TaCZ 1.1.8-hotfix javap调用顺序、描述符与字段，验证资源JSON、3个@Pseudo注册、生成顺序、收费前阈值等25项静态检查，日志在维护目录 `验证日志/static-finite-ammo-20261006.log`。子Agent另有12项源码静态断言。未执行Gradle、编译、新增测试或游戏，本轮未部署/push；上轮已部署JAR不包含这些新改动。
- 游戏验收待项：各阶自然/信标生成数量；低于/等于25%及战斗最后一发；各类枪费用、最后一次付费周期、逐颗装填/INVENTORY枪包；补货回切、近身租约、满包与拒绝掉落；多重弩/Spartan弩、NPC临时载荷取出/死亡、三叉戟无限；换枪/菜单/存档/传送、缺TaCZ启动与玩家枪械回归。Mixin注入、编译及游戏效果不能由静态检查宣称通过。
- 三个开发工作树均保持干净并保留；主控整合无文本冲突，逐项核对逻辑交互，补充上述跨模块修正。源码最终提交为 `ecf8026`（文档提交另计），原用户CHANGELOG重排独立保留。

## 弹药贴图与原版材料配方：2026-10-06（已完成）

- 基线 `35249e5a5569f5578b3f4367262626dff0ef5b32`，负责人主控；主仓库 `main`，没有并行编辑会话，根目录既有 CHANGELOG 用户重排保留。范围限两项新弹药资源、配方及说明，不更改AI/库存/弹药收费，不构建/部署/push。
- 内置 imagegen 分别生成箭矢束与通用弹匣，参考模组军用品配色、轮廓、金属高光；最近邻缩放为32×32 RGBA，项目内两个PNG经视觉审查，物品模型改为引用本模组贴图。完整提示词与资源位置见 `docs/AMMO_ASSETS.md`。生成原图留在本机，不提交工具缓存。
- 原版工作台无序配方：4个 `minecraft:arrow`＋1个 `minecraft:stick`＋1个 `minecraft:feather`→32个 `humangunner:human_arrow`；3个 `minecraft:gunpowder`＋2个 `minecraft:iron_ingot`→2个 `humangunner:human_magazine`。用户未指定额外材料/铁数量，主控选木棍/羽毛作为扩充箭杆/箭羽，每弹匣1铁锭。后者 `forge:mod_loaded` 条件为TaCZ，符合可选联动范围。
- 实际验证：JSON解析、重复材料计数/产量、工作台格数、可选条件、模型路径及PNG尺寸/alpha检查通过；箭图735透明/289可见像素，弹匣643透明/381可见像素，放大预览检查通过；Git diff --check通过。日志在维护目录 `验证日志/static-ammo-assets-20261006.log`，预览 `验证日志/ammo-textures-preview-20261006.png`。没有运行Gradle/编译/游戏；合成界面与资源加载仍需构建及游戏验收。版本仍为3.6.0，更新日志仅新增未发布条目。

## 阶级配置重组与弹药配置：2026-10-06（已本地整合）

- 基线 `0b5c67f10a994c52e47e368556f8b3caa950c2a8`；原CHANGELOG用户改动保留。此轮仅开发，无构建/投放/push。
- CONFIG-LAYOUT：GPT-6.1 Sol medium子Agent，维护目录 `worktrees/tier-config-layout`，分支 `feature/tier-config-layout`，独占SplitConfigFiles及新投影/旧布局读取模块、独立迁移测试；五文件布局/迁移/备份，禁止修改UnifiedConfig、默认JSON和公共文档。全局/阶级模式接口由主控约定。
- AMMO-CONFIG：GPT-6.1 Sol medium子Agent，维护目录 `worktrees/ammo-config-switch`，分支 `feature/ammo-config-switch`，独占HumanAmmo、HumanAmmoSpawn及必要TaCZ事务/选择修订与独立测试；以主控提供UnifiedConfig接口读取配置，关闭消耗时无弹药也可射击/换弹，开启仍执行库存扣费。
- 主控独占UnifiedConfig、默认JSON、CombatAiConfig与按需AI调用接入、TierRuntimeConfigTest、公共Gradle/文档/版本，负责审核整合。总开关留全局，既有分阶参数全部归并，用户确认AI行为参数也按阶独立。现有UnifiedConfigTest由CONFIG-LAYOUT负责人调整。

- 已审核并整合 AMMO-CONFIG 原提交 `5f6de430aa89dac3f26855bd9e1cc47036121dee` → `ef5cf58`；CONFIG-LAYOUT 原提交 `05b12e4ea9a47ad83398b64aa5fc475f08ff10d4` → `21f1eae`，补充 `a73f4eb2f93657123d056dce462e810fbbafefaa` → `a7f345f`。均为本地提交。
- 主控接入提交 `2e6690c`：将所有AI读取绑定实体阶级，加入出生弹药范围缓存/零库存免费弹药总开关，迁移并停止注册旧Forge TOML；原生结构设置进global.native_gameplay，旧逃跑/台词概率进每阶ai。独立逻辑共用原有射击与换弹入口，不另建战斗/动画流程。
- 静态核查：五文件权威来源、旧文件只读/先备份、损坏招聘价格失败关闭、空池/权重保护、所有AI调用分阶、可选TaCZ边界、默认数值兼容与Git diff --check。新增TierRuntimeConfigTest、HumanAmmoConfigSwitchTest、TierConfigFilesTest，已接Gradle但按用户规则未执行编译或测试运行。
- 待游戏验收：旧配置升级后五文件生成与备份；四阶AI差异和出生弹药范围；开关关闭时零库存弓弩射击/枪械换弹、开启时扣费与武器回退。未构建、未实例同步、未推送；版本保留3.6.0，变更登记Unreleased。

## 分派与交付模板

每项功能任务新增独立记录，至少包含：

- ID、用户需求、预期行为、范围边界和状态。
- 基线完整 SHA、负责人、会话/Agent 标识、工作树绝对路径和分支。
- 允许修改文件/模块、共享文件负责人及禁止修改范围。
- 公共接口、配置/存档/网络字段约定；与 AI、移动、武器切换、动画的关系。
- 前置任务、接入顺序，以及是否允许在当前授权下执行构建。
- 交付提交 SHA、实际修改文件、静态检查/自动验证命令及结果、未解决问题。
- 主控审核结论、整合提交 SHA、冲突处理依据及整合后的验证。
- 待用户游戏测试的具体场景；如涉及发布，链接发布记录与目标清单。

状态建议：待分派 → 开发中 → 待审核 → 待整合 → 已整合；遇到阻塞记明具体原因。没有运行验证时记录“未执行”，不引用历史成功作为本次结果。

## 可用分工方式

1. 单任务独立工作树：开发者交付本地提交，主控审核并整合。
2. 多模块并行：每项任务独立工作树；先检查文件重叠，公共接入由一个负责人完成。
3. 耦合任务顺序实施：涉及同一 AI、配置或渲染状态时先确定接口，按依赖顺序整合；不以无文本冲突判定兼容。
4. 只读调查：可先分派证据核查，明确不得修改；实现阶段再登记编辑工作树。

## 发布清单模板

由主控在用户明确要求构建/同步/推送后按当前指定范围登记：版本、源码提交和未提交状态、构建命令及结果、每个目标绝对路径、运行检查、旧 JAR 名称与备份位置、新 JAR 名称、源/目标 SHA-256、同步结果、推送结果、游戏内验收状态。未指定的历史部署目录不自动加入清单。

## 构建及测试实例同步：2026-10-06 15:09（北京时间）

- 用户授权“构建并同步到那个测试实例”；目标沿用明确指定的 `E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21`，未同步其他实例、未启动游戏、未推送GitHub。
- 源码提交 `95d0da5706a75dd737b9ec92e3bece784dc4966c`，版本3.6.0。构建修复Gson JsonObject.isEmpty兼容性及隔离工作目录中的源码检查路径，修改单独提交；用户CHANGELOG修改仍保留未提交。
- Java17 / gradlew.bat / 三个显式只读API路径；`build lootScreeningTest --offline` 最终成功，34项任务。配置迁移171检查、UnifiedConfig944检查、弹药开关58检查、箭矢31检查、换弹账本36检查、掉落13386检查以及其他已注册check任务通过。故障注入测试中的坏JSON/TOML和备份错误日志为预期路径。
- 打包资源检查通过：箭矢/弹匣模型、透明PNG及配方、新默认弹药配置与迁移类均在JAR内。替换前检查未发现运行中的目标Minecraft，备份旧JAR及本模组配置后投放；目标仅一份本模组JAR。
- 源/目标SHA-256一致：`3E023411ABCA15E9CDC00EA4DFDE3887CD5AAE288798A09BB50694B91C3ECA50`。旧JAR：`48349D249CA65CA7223B5F14C132BF7A943D1FDE9E73EB4628370EC7CEDFAA16`。
- 备份及部署清单：`E:\MC\Create-Delight\HostileHumansUnified-维护\部署备份\20261006-150935-tier-config-ammo-Forge_47.4.21\deployment.json`；最终构建日志：维护目录 `验证日志/build-tier-config-20261006-retry3.log`。
- 实例配置未提前改写；五文件布局于用户下一次启动迁移。待游戏内验收动画重追踪、信标掉落、有限弹药/武器回退、换弹阈值、配方贴图以及分阶配置效果；自动验证不代表游戏行为已验收。

## 弹药贴图、命名与拾取价值：2026-10-06（已开发）

- 基线 `5c38171311d90425b294d1c1191821bb6954eae8`；主控单独实施，不另开Agent或工作树。范围：HumanLootManager、LootScreeningTest、两种语言、弹匣PNG和维护文档；原CHANGELOG未提交部分保留。
- 内置imagegen编辑旧弹匣，简化大面积明暗、底部绿带和轮廓，System.Drawing最近邻整理32×32RGBA。最终提示词见docs/AMMO_ASSETS.md，模型路径不变。
- 箭矢中文“通用箭矢”、英文“Universal Arrow”，注册ID humangunner:human_arrow保留；两个弹药物品同走HumanAmmo.isAmmo，评分100→160。瞬间治疗药水115→55（包括同类喷溅/滞留形式）；其它药水、食物公式、堆叠和受保护装备不改。
- 提取foodValue供生产与回归共用；新增面包<治疗<牛排/金胡萝卜、满背包双向替换回归断言。静态JSON/PNG/评分/差异检查通过，未编译或执行自动测试；未构建、同步或推送。待游戏验收物品远距/背包贴图、显示名及满背包拾取取舍。


## 弹匣压槽及出生药水配置：2026-10-06（本地开发完成）

- 基线 `07dfdf19ba562924d0041f542d0a691806b1ddd0`；主控独立实施，未开新Agent。范围：SpawnPotionConfig、UnifiedConfig、RecoverySupplies、默认JSON、独立SpawnPotionConfigTest/Gradle入口、弹匣PNG与文档；原CHANGELOG用户修改保留。
- 按阶级缓存potions snapshot，读取恢复/战术数量范围、带瓶形和药水注册ID的权重池及bonus概率；仅原KIT_GRANTED的一次性补给入口调用。库存满停止、原堆叠上限不变、未注册药水跳过、空池不制造fallback；关闭本阶potions不关闭食物，既有item_recovery_enabled仍管整个补给包。可选药水注册在实际生成时解析，不在启动静态初始化强绑模组API。
- 默认上下限与种类取原代码，新增独立均匀数量/权重抽取替代与食物预设耦合的药水次数及固定药水轮换；原食物/金苹果预设保持。将药水硬编码发放从RecoverySupplies移除，不新增恢复/攻击/动画系统。
- 内置imagegen编辑弹匣，加入两条压槽，最近邻整理32×32透明PNG；完整提示词见docs/AMMO_ASSETS.md。静态JSON、默认值变化隔离、食物预设、一次性调用、PNG尺寸与差异检查通过。
- 新增分阶投影/默认值/空池/关闭/上下限/注册ID配置回归测试，已接check但没有运行编译或自动化测试。本轮未构建、未同步、未推送；待游戏验收各阶出生瓶数、模组药水ID、背包满与空池、关闭开关及贴图效果。

## 构建及测试实例同步：2026-10-06 15:31（北京时间）

- 用户授权“构建同步”，目标沿用 `E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21`；只同步这一实例，未启动游戏、未推送。
- 源码 `37451d6397c56410f3609e38c33cf242bd3a04fa`，版本3.6.0。Java17、仓库gradlew.bat、显式三个API参数，`build lootScreeningTest --offline` 一次成功，35任务；新SpawnPotionConfigTest通过，UnifiedConfig1016检查及拾取/食物药水价值13391检查通过。其他check全通过，故障注入ERROR为预期坏配置测试。
- 包内弹匣PNG与源码一致，中文箭矢名称为通用箭矢，四阶默认potions与SpawnPotionConfig类在包内。运行状态检查无目标Minecraft，旧JAR和模组配置备份完成；目标仅一份本模组JAR。
- 源/目标SHA-256：`C79C24033B54B2F8C953F0A7C7B322105C37782DD74A09CB854C37E398A6889B`；旧JAR：`3E023411ABCA15E9CDC00EA4DFDE3887CD5AAE288798A09BB50694B91C3ECA50`。
- 备份清单：维护目录 `部署备份/20261006-153120-potions-magazine-Forge_47.4.21/deployment.json`；构建日志 `验证日志/build-potion-config-20261006.log`。实例既有配置未改写，用户CHANGELOG未提交部分保留。
- 待游戏验收：两条压槽的远近显示、通用箭矢名称、弹药与瞬间治疗拾取取舍、各阶出生药水的数量/候选池/空池和开关。已有五文件未填写potions时使用默认值，自定义按CONFIGURATION.md加入对应阶文件；自动验证不代替实际游戏验收。

## 雇佣跟随、离线驻守及死亡报告：2026-10-06（本地开发完成）

- 明确基线 `8ba0dbec684c3aa4ca8d5c2d2cab380922cc9784`，主控负责 main 的 SoldierFollowNavigation/SoldierOrder、OwnerOfflinePolicy、HumanData、UnifiedConfig、HumanGunner 事件接入、默认配置、语言、构建测试入口和文档。仅修改模组源码，不修改实例；保留用户原CHANGELOG改动。
- 独立开发者 `/root/hired_persistence_audit`，GPT-6.1 Sol medium，工作树 `E:\MC\Create-Delight\HostileHumansUnified-维护\worktrees\offline-death-notices`，分支 `feature/offline-death-notices`；最初只读审计，随后仅授权 OfflineSoldierDeathReports.java 与对应测试。共享接口全部由主控接入，不允许自行合并/构建/部署。
- 开发者交付 `b6b79ae65a2d1f2dee6fa0bade6e228a52fc7d2b`、`82cd1fa2b947c48fe5fa6af52797ebb238610369`，本地已逐项审核整合为 `16a4f7a`、`2a6b931`。接口：onDeath(LOWEST)、onEntityLeave(所有移除原因)、tick(清理复活候选)、onPlayerLogin、onServerStopped。SavedData 用主世界持久化，32详情上限、超出计数；候选保存实体NBT，只有KILLED入箱，卸载/换维度保留候选，DISCARDED不报告。
- 确认问题：HumanData原活体保存依赖在线同维度getOwner，主人UUID可能漏存；改以实体getOwnerUUID为准，在线时更新名字、离线时保留已知名字。名册/召回受影响不等于实体实际死亡。实体卸载和跨维度原本不删除库存；离线实体正常承伤，不能仅凭缺席断言消失原因。为实际KILLED/DISCARDED增加日志以辅助后续定位。
- 跟随：原每10tick重新提交路径、无起止迟滞且无追赶疾跑。新机制4格外开始、2.5格内停止、6格外疾跑、12格外追赶，默认导航1.35/1.6；延续有效路径，只在目标移动2格或路径结束时重算，最多每5tick一次。原48格安全传送、寻岸/撤退/拾取/战斗导航各自优先关系保持。
- 下线：用户明确答复区域驻守，已加载跟随士兵在logout事件转换，后加载士兵分散20tick检查全服务器在线名单，避免把跨维度当离线。只FOLLOW->GUARD，不改其它指令或攻击模式；GUARD锚点为转换位置，不自动恢复跟随。
- 验证：新增跟随边界/配置独立性/离线转换资格测试，以及26项死亡持久化/上限/玩家隔离/候选卸载与复活测试，已接入check。当前只做静态核查，未编译、未执行自动测试、未构建同步或推送。待游戏检查跟随连续性、不同地形与战斗切换、下线仅跟随转驻守、跨维度在线不误判、离线实际死亡/重启通知和其它模组救命取消。

- 最终静态核查：默认与语言JSON全部可解析，四阶倍率一致、提示占位符匹配、事件入口唯一、离线检测使用全服身份、用户CHANGELOG旧段SHA-256保持不变；git diff --check通过。开发者独立只读复审发现岩浆逃生未置isFleeing的导航冲突，已补充SoldierOrder的岩浆让路；注销转换保留撤退/寻岸/岩浆导航，不取消逃生冲刺。没有执行编译或自动化回归，新增测试待用户构建授权后运行。

- 主控最终整合提交 `91b7d392f6308a08ab541884ca6ed1d0311013c4`；工作树仅用户原CHANGELOG修改未提交，开发者工作树干净。源码已整合到main，未推送，测试实例仍是上一轮部署产物。


## 默认配置说明与排版：2026-10-06（本地开发完成）

- 基线 `3168083157faa965f7388ba95865f38106365905`；主控单独实施，未另开Agent或编辑工作树。范围：TierConfigProjection、SplitConfigFiles、默认JSON、TierConfigFilesTest及文档/本任务CHANGELOG；保留用户原CHANGELOG修改。
- 分阶投影同步分发装备池/成长说明，伤害层级说明置于伤害区域，枪械权重说明置于阶级TaCZ。全局移除已分阶且只含原装说明的空区域（包括解锁日）；未知扩展、自定义说明保留。AI全局仅总开关及专属说明，四阶独立说明和原有读取路径保留。
- 四阶使用相同分区顺序，固定字段双语说明紧贴数值；生成/迁移缺失文件时，补齐后再次整理位置。生命、移速、伤害、近战、AI、装备、成长、出生弹药、药水、枪械及雇佣注明用途/单位/范围，权重池条目不塞入伪权重字段。澄清空枪械类型权重实际回退内置规则。已有五文件仍不自动重写，不修改任何游戏实例。
- 静态JSON解析及去说明后的整棵默认值比较通过：所有游戏配置键、数值、物品ID、数组顺序和权重不变；220个核心标量位置有双语说明，迁移到阶级的枪械/雇佣字段均有说明。新增投影生成文件的分区/邻接/无孤立说明/未知用户说明保留及运行设置一致性回归断言，尚未运行。
- 未构建、未执行编译或自动化测试、未同步和推送。用户下次授权构建后运行TierConfigFilesTest；游戏内仍需检查首次生成五份文件、既有自定义文件不改写以及配置读取效果。

- 本地整合提交 `9d3ea2cb71089f6d1b7cc5ef5821efab27679431`；默认JSON和差异静态核查通过，原用户CHANGELOG旧段哈希不变，游戏实例未改动。


## 持枪近战拦截及群体目标响应：2026-10-06（本地开发完成）

- 基线 `adbcfae5fd4096f991debd34e08dcb43c15f63f5`；主控 `/root` 单独在主仓库 main 实施，未增加 Agent 或并行编辑工作树。Human、AdaptiveCombatGoal、build.gradle 和所有接入文件由主控唯一负责；保留用户原 CHANGELOG 修改。
- 文件范围：GunSupport/TaczGunItemIdentity、GunDrawContinuity、GunnerGoal/TaczIntegration、Human、MeleeAttackGoal/MeleeCombatRange、BetterCombatMeleeCombat、AdaptiveCombatGoal、RangedHybridMeleeGoal/TridentHybridGoal、两个 NearestAttackableTargetGoal 与 GuardCombatTargetGoal；新增 GunCombatPolicyTest，更新 OptionalRuntimeTest、构建测试入口及文档。
- 接口：GunSupport.isGunItem 识别实际枪械且不受 NPC 兼容开关影响，可选 API 仅存在 TaCZ 已安装时构造的适配器。Human.humanGunner$canMeleeAttack 统一保护持枪伤害与动画入口，空枪不能被归入近战模式；取消切成枪后的 Better Combat 待出伤。近距离枪手仍通过现有 GunCustody 实际换主手武器后才能反击。
- 确認的响应原因：玩家目标限流 Goal 未检查死亡/移除/换维度，驻守记忆能重新指定死亡攻击者；随机目标重搜进一步增加等待。补齐有效性检查，驻守结束仅清除死亡目标。枪手失去死亡目标后使用 6 tick 短窗口跳过已有选择器随机门槛，并保留玩家警戒、人数上限、攻击模式、范围及原有目标谓词，不创建第二套选敌系统。
- 本机只读核对 TaCZ 1.1.8-hotfix API JAR 字节码：draw 调用 initialData 重置射击、换弹、拉栓与瞄准状态，GunDrawEvent 未取消时才发布 currentGunItem supplier。原 GunnerGoal 每次 start 无条件 draw；改为记录实体当前实际 ItemStack 引用及 gun ID，同枪跨目标/Goal 重启保持状态，真正换枪或绑定丢失才 draw。取消事件不标记成功，原卡顿/NOT_DRAW 恢复保留强制 draw；弱键映射不持有实体引用。默认 supplier 动态读取主手，不能仅以 supplier.get()==主手判断是否换过枪。
- 验证：git diff --check 与源码调用链静态核查；新增同枪多次目标切换、真换枪、原地改 gun ID、丢失绑定、外部已绑定和掏枪事件取消的状态回归及近战/目标入口字节码接入检查，接入 check，尚未编译或运行。本轮没有构建、实例同步、游戏启动或 GitHub 推送。
- 游戏内验收：有/无 Better Combat 时持枪射击不同时挥砍；空枪不拳击，实际切剑后近战恢复；群体怪物/玩家切换及区域驻守死目标及时清除；击杀时正在换弹不被重新掏枪打断；真实换枪正常掏枪，射速、拉栓、供弹扣费、视线和攻击模式仍有效。响应时间仍受原生枪械动作、服务器 tick 和视线限制，静态核查不证明游戏效果。

- 主控本地整合提交 `78a8b880eb00c223a817f632e18c14775ba1a455`；暂存差异及 draw 唯一入口静态检查通过。工作树仅保留用户原 CHANGELOG 修改，版本仍为3.6.0，游戏实例仍是之前部署产物。


## 构建及测试实例同步：2026-10-06 17:01（北京时间）

- 用户授权“构建并同步到测试实例”，仅目标 `E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21`。版本3.6.0，源码 `cc461be143ed37c7215dea9e0571ab96aadf0976`，包含跟随/下线驻守/离线死亡提示、配置说明排版、持枪近战拦截与目标响应等此前未部署修改。
- Java17、仓库 gradlew.bat 与三个完整只读 API 参数执行 `build lootScreeningTest --offline`。首轮编译及新测试通过，但 UnifiedConfigTest 仍要求默认 JSON 最末键为 tacz；新说明元数据位于其后。仅调整测试为最后一个非说明配置区域须为 tacz，保留断言意图；提交 cc461be。重跑完整构建成功（38任务，34执行/4已有结果）。结构及坏配置 ERROR 是故障注入预期输出。
- 自动化：GunCombatPolicyTest57、SoldierFollowPolicyTest31、OfflineSoldierDeathReportsTest26、TierConfigFilesTest241、UnifiedConfigTest1893、LootScreeningTest13391通过；可选依赖隔离358核心类与其余 check 均通过。JAR ZIP完整性、版本清单、最新类和默认 JSON 与源码一致性核验通过。
- 替换前两次确认无 Minecraft/无法识别的 Java 进程；备份旧 JAR 和10份相关配置文件并逐一核对哈希。仅替换本模组 JAR，实例内仅一份；既有配置未改写，未启动游戏或推送 GitHub。
- 新源/目标 SHA-256 `CBFBF4D82E5D86F347AAA3FC07D7936EED426447416F86296265C70062AAE657`；旧 JAR `C79C24033B54B2F8C953F0A7C7B322105C37782DD74A09CB854C37E398A6889B`。备份清单：维护目录 `部署备份/20261006-170124-gun-combat-follow-config-Forge_47.4.21/deployment.json`；构建日志 `验证日志/build-gun-combat-20261006-retry.log`。
- 待用户游戏内验收：有/无 Better Combat 持枪不挥砍、空枪实际换武器后近战、群体连续选敌与换弹、跟随速度/停顿、下线仅跟随转区域驻守、离线真实死亡在上线后提示。新版说明与排版只用于新生成/缺失的配置文件，现有配置保留。自动化成功不代表真实游戏行为已验证。


## 生存物资预留、职业弹药偏好及近战防卫持握：2026-10-06（本地开发完成）

- 基线 `3661fb3c5a33ab4bbea0eac31de7df0b1032d328`，主控 `/root` 单独在主仓库 main 实施；未增加 Agent 或编辑工作树，保留用户原 CHANGELOG 修改。文件范围：新增 SupplyInventoryPolicy/CloseDefenseHold/SupplyAndCloseDefenseTest；HumanLootManager、GunCustody、RangedWeaponCustody、AmmoWeaponSelection、Human、build.gradle 与文档。共享接入全部由主控负责。
- 用户确认：至少三个独立食物槽位（数量和品质较充足的堆优先，不足时预留空位），另保护最后一份可用恢复药品与必要备用装备。按食物总量×单件价值选三堆，最后一瓶可饮用治疗/再生药水、最佳近战备用及每个护甲位置一件最佳备用不被其它类别替换；同类更好物品可升级，绑定装备及原有枪/弓/盾/身份牌保护保留。旧背包缺三堆食物且全是普通弹药时，允许食物优先置换，手动库存不受限制，不凭空制造食物。
- 搜索和实际拾取共用同一快照政策；可合并到已有弹药堆而不占用预留空位。收回临时副手物品及生成必要近战备用的逐出入口也跳过生存预留，避免旁路丢掉最后物资；装备升级先检查旧装备是否能存放，避免无空间时追逐不能收纳的装备。
- 弹药评分：拥有枪械的单位弹匣180/箭矢140；无枪而有弓弩的单位箭矢180/弹匣140；纯近战均160。职业以实际持有及背包武器判断，不因枪械耗尽或临时持近战改变偏好；取枪优先于备用弓，沿用库存修订号缓存。
- 临时防卫仍用原 GunCustody/RangedWeaponCustody 两位置交换，不建第二套武器控制器。换近战后40 tick最短持握，原版攻击至少8 tick并等待实际 swinging 结束，Better Combat按两个完整攻击周期加2 tick保留收招余量；每次反击更新截止时间。距离/击杀/撤退/Goal切换的自动恢复都在统一restore入口等待，补给晋升和主手拾取/背包优化也等待。原有近身目标滞回与攻击冷却不变，目标留在近处可多次反击。破损空手或真正换武器解除旧绑定；主人手动装备管理立即取消保护。
- 验证：源码逐一核对主动搜索、近旁能力、原版pickup、提交、药品副手、必要备用和自动恢复入口，git diff --check通过。新增重复满地弹药/三个食物槽/数量升级/药品护甲保护/旧满弹药恢复/职业偏好，以及两秒边界、长动画、多次反击、原版减速挥击和恢复入口接入测试，已挂check但尚未编译或运行。本轮未构建、未同步、未启动游戏、未推送。
- 待游戏验收：30槽满背包连续拾取弹药后保留三堆食物与药品/备用，三种职业面对混合弹药选择，食物不足预留空位与补食，治疗/护盾使用后库存恢复，Better Combat有/无、慢武器/挖掘疲劳、击杀后/目标离开/群体近身反击，以及手动开包与武器损坏。测试实例仍是上次部署JAR，版本号未变。

- 主控本地整合提交 `0b9490413b913125d2b053d6fddd5e61ef4d9e21`；暂存及源码接入静态核查通过，新增回归尚未执行。用户原 CHANGELOG 内容哈希未变，实例未改动。

## 药水消耗、再生重饮与叠加回血：2026-10-06（本地开发完成）

- 基线 `7a63303eee2496ebbc28e8ec6b50bfc51f1b3484`；主控 `/root` 单独在主仓库 main 实施，无新增 Agent 或工作树。共享接入由主控负责。范围：Human、HumanGunner、RecoverySupplies、新增 PotionRecoveryPolicy/PotionRecoveryTest、build.gradle、默认配置说明、CONFIGURATION、WORKSTREAMS 及本任务 CHANGELOG；保留用户原 CHANGELOG。
- 只读检查本地 Forge 47.4.16 映射类字节码：PotionItem 为非玩家施加效果，却仅为非创造玩家扣瓶；LivingEntity 完成饮用先检查实际手中物品与使用引用一致。Human 在真正完成原生饮用后为仍未扣除的同种同标签药水补扣一次，返回玻璃瓶；已扣除或自定义返回结果不二次扣除。原有副手交换/延迟归还、治疗II额外治疗保留，中断不扣药水。
- 恢复可用性、评分、战术药水候选及最终开始饮用均排除已有有效再生时的含再生饮用药水，覆盖长效、剩余1 tick及无限效果；效果结束才能重新喝。单独瞬间治疗仍可急救，不改变喷溅投掷扣除。
- 用户最新要求再生II由1.5倍改为2倍。额外回血使用独立弱引用计时器，按各阶 gradual_heal_interval_ticks，I每次1点、II及更高等级每次2点；与食物额度及原版再生并行，不消费/重置食物池。buff失效清除计时，卸载不补发，主人编辑背包期间仍生效。默认字段数值不变，仅说明注明用途。
- 静态核查：扣除在原生完成之后，持续效果判定和四处候选/开始入口统一，计时与食物NBT分离，默认JSON解析及去说明后的默认值比较、git diff --check通过。新增消费/已扣除/返回容器/持续时间边界/倍率/独立节奏/卸载间隔及接入回归，挂入check；未编译、未执行自动化测试，未构建、同步、启动游戏或推送。
- 待游戏内验收：喝长效/强效再生后库存减一并正确归还盾与玻璃瓶，饮用中断无消耗，已有再生不连续重饮、结束后正常重饮；再生I/II每间隔额外1/2点且与食物叠加，满血后受伤/效果结束/卸载重载无爆发补发，原版治疗与喷溅药水不重复扣除。

- 主控本地整合提交 `f1e59648f99f21f1bbab76b8a6df63246d5a9c76`；暂存检查通过，默认游戏配置值不变，新增回归尚未编译执行。用户原 CHANGELOG 内容保留，测试实例未改动。
