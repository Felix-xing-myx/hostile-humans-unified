# 开发任务与整合记录

## 2026-10-10 地形受阻、绕路与悬崖战术（源码接入，未构建）

- 用户批准上一轮完整改进方案，并要求纳入现有AI正式判断。基线b518cd488ad34eb4074e5408a2753cd062136b1a，main初始干净。主控/root独立编辑当前project工作树，负责地形判断反馈、导航/追击、弓弩枪械与防御接入、测试和说明；公共HumanNavigation/HumanTerrainSafety/PursuitNavigation/AdaptiveCombatGoal等均由主控接入。不改变防摔预算、低血量撤退、攻击许可、弹药、水下游泳或传送策略；不构建/同步/启动游戏/push。
- 只读审查/root/terrain_review使用GPT-6.1 Sol medium，只读当前main并反馈调用冲突与命令约束，不编辑文件、不构建、无需编辑工作树。不得代替实际游戏验收。
- 已接入：HumanTerrainSafety只读Assessment与执行否决反馈，HumanNavigation最多12条定向短期通行记录；CombatTerrainNavigation/CombatTerrainPolicy统一受阻、8/16/24格分批绕路、多高度候选、可射击保持远程、受击掩护、实际位移进展及16个末端记忆。PursuitNavigation使用实际路径末端，记录导航实际安装对象，避免等价Path复用造成归属丢失；弓弩/枪械/三叉戟、防御/战术/脱困及MovementContinuity共享决定，不注册新MOVE Goal。原武器Goal仍负责攻击、动画与弹药。命令节点受限，水中/撤退/上岸不接管。详见docs/COMBAT_TERRAIN.md。
- 只读复核修正了移动目标清空绕路记忆、头顶平台遮蔽候选、FIRE未停止普通追击、修复路径读取旧对象、近战重启覆盖路线及清侧移时零STRAFE覆盖问题。补齐路径拐角有限前视，保留危险移动制动及真实击退。静态审查没有发现其余确定覆盖点，不能替代实际地形验收。
- 验证：Python源码契约检查35项通过（新增9项）；git diff --check通过。只读javap核对本机Forge47.4.16的Path实际末端/下一节点、Entity.distanceToSqr(Vec3)、Level建造高度/已加载区块及导航速度/当前路径接口。新增Java CombatTerrainPolicyTest决策边界和编译后调用链，更新RangedTerrainSafetyTest适配Assessment，并登记check；按用户约定未编译或执行Java测试。未构建/实例同步/启动游戏/push。待有构建要求后编译验证，再由用户测试坡道绕路、隔崖对射/掩护、桥下多层、转角窄路、移动目标、命令切换、水边/撤退及多士兵性能。
- 本地源码提交：4b8093b68b32c502d2a24dd7dcdbe648ce307888（main，25个文件）；未推送。源码与静态接入已交付，编译及游戏行为尚未验收，不标记整体验收完成。

## 2026-10-10 人类弹射物受伤冷却范围调整（源码完成，未构建）

- 用户确认范围：本模组人类射出的弹射物命中非玩家目标，以及本模组人类受到弹射物，绕过受伤冷却；原版模式禁用本调整。主控/root在main独立处理，基线3ec1984b68088a0f8f6fc7b20712011517f8d91d，初始工作区干净；未派发编辑Agent，不修改其他工作树。范围独立ProjectileCooldownPolicy/LivingEntityProjectileCooldownMixin、Mixin清单、回归测试和构建测试登记、配置说明/日志；公共接入与审核均由主控负责。
- 精确重定向LivingEntity.hurt中BYPASSES_COOLDOWN标签查询，Slice由该标签字段至首个lastHurt字段，require/allow均1；不改原DamageSource，不清空invulnerableTime，不新增伤害/生命写入，不改变Forge取消、护甲/盾牌、友军碰撞及成长实际扣血观察入口。玩家目标、无关生物交战、近战、爆炸与非弹射物状态伤害不新增绕过规则；归属优先DamageSource显式攻击者，缺失时读取Projectile.owner。
- 原版1.20.1/Forge47.4.16实际缓存字节码核对：受伤计时>10时，仅一次BYPASSES_COOLDOWN查询；其后lastHurt差额分支。现有TaCZ1.1.8-hotfix自身在直接命中前清理受伤计时，保持上游行为，不把本次调整误称为撤销TaCZ原有行为。分类复用IS_PROJECTILE和可选GunSupport.isBullet，不直接引用可选依赖。
- Python源码契约共26项通过（新增4项，原成长/UI 22项），git diff检查通过；核对缓存Forge字节码及SRG映射与精确注入边界。新增Java范围矩阵与真实Forge字节码Slice唯一性回归，登记至check，尚未编译执行。源码本地整合，不构建/同步/改实例/启动游戏/push。游戏内待验收双弓手同tick命中、箭/三叉戟连续伤害、玩家/友军/PVP过滤、盾牌/护甲、成长贡献和原版模式，以及其他模组同时修改hurt时的Mixin启动兼容。

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

## 原版模式、贴身保护及许可攻击：2026-10-06（本地开发完成）

- 基线 `aa22f6b97618b25b7bfdb28f3c1d72c68c054931`；主控 `/root` 在主仓库 main 实施共享接入，现有其他工作树保留。只读审核 `/root/protect_review` 使用 GPT 6.1 Sol / medium，不编辑、不构建。未创建新编辑工作树，用户原 CHANGELOG 保留。范围：UnifiedConfig/TierAttributes/RandomizedHumanHealth/HumanGunner/TouhouMaidCompat/Human；SoldierOrder/Goal/CombatMode/HumanRelations/双命令界面及网络、TierConfigProjection；新增 VanillaModePolicy、BodyguardPolicy/Protection/TargetGoal/MovementGoal 与 BodyguardAndVanillaModeTest；默认JSON、双语资源、既有配置/命令测试、构建注册及文档。
- 用户确认：全部人类（包括雇佣）基础生命20，允许外部生命修饰；贴身约2–4格，追击12格，主动追击时主人附近更近威胁优先拦截，未受伤也返回；被动允许提前挡护，自身遇袭可还击。追加许可攻击模式，介于主动/被动，不自主选敌，仅自卫/主人受攻击/主人主动攻击取得具体目标许可。
- 原版模式顶层 vanilla_mode 默认false，仅全局文件持有。基础攻击1与原版玩家相同，绕过本模组全局/阶级近战、跳跃、三叉戟刺击及女仆近战倍率；武器、附魔、状态效果和Better Combat武器原生动作倍率保留，远程/护甲/AI不变。生命仅改base，保存进入前基础值和已有阶级随机值，按实际最大生命含外部修饰的比例换算；不补满、不复活，关闭恢复原值及原随机范围逻辑。
- BODYGUARD 追加在移动枚举末尾，AUTHORIZED 追加在攻击枚举末尾；界面显示许可在主动/被动之间且渲染/点击映射一致。移动目标按主人距离、攻击意图和微小保持偏好排序，每5 tick复核，同主人共享只保存UUID的附近缓存，近处威胁抢占远追目标。主动可先攻击，许可/被动仍由既有授权表管攻击；被动贴身保护等待最终有效主人伤害授权。许可沿用8目标/200tick窗口，对已许可目标造成有效伤害仅续期，不能给陌生目标自发授权。
- 新目标Goal占TARGET（-11），新站位Goal占MOVE/LOOK（-5），攻击仍交原近战/Better Combat/枪械/弓弩/三叉戟Goal。远程目标可在48格检测范围内且必须攻击主人/士兵或已有许可，实际射程和弹药规则仍生效；只在身边站定反击，统一现有驻守移动门阻止远追。盾牌仅用实际手持物和原生移动举盾/朝向，短举盾有间隔，不建伤害转移器。生存AI(-9)、上岸(-7)、岩浆逃生(-12)优先；目标控制也在逃跑/返回阶段让步。手动物品管理、恢复副手托管不被站位写入。
- 切换威胁不调用forgetSoldierTarget，避免给旧威胁60tick黑名单、清除自卫来源或中断药品；BODYGUARD实际换目标时取消旧Better Combat延迟命中。超追击圈仍用原有遗忘/安全归队。无主人时沿最后位置有限范围待命，保存命令；拾取无威胁且主人4格内才允许。原FOLLOW下线改GUARD及其余旧模式保留。
- 只读审核发现远程站位三维坐标与nearFloor投影可能永远不一致、旧测试只允许4移动模式，均修复：保护范围与高度容差内让出攻击，不要求精确不可达点；既有枚举测试更新5并检查旧序号。单兵面板227高、名单248高，新增按钮不重叠，协议6/5拒绝不能识别新枚举的旧客户端。版本号不变，需客户端与服务器使用同一更新JAR。
- 静态检查：默认/语言JSON解析、去说明后默认仅新增vanilla_mode=false、旧命令序号及渲染/点击排列、路径/攻击调用链、git diff --check通过。新增关闭默认/启用/错误类型/全局投影、生命进出比例/外部修饰/死者、许可三来源/非主动、12格及远程边界、近威胁优先/小滞回、台阶让出、接入检查；更新既有枚举/模式/默认测试并挂check。全部编译及自动测试尚未执行。本轮未构建、同步、启动游戏或推送。
- 待游戏验收：新旧世界全局开关进出、四阶与雇佣单位生命/武器伤害、外部生命装备与药水；两界面命令持久化/客户端服务器一致；主动远追途中近处敌人出现、许可主人先手/受击/自卫且不自选敌、被动提前挡护且无伤不出手；近战/弓弩/枪械、弹药耗尽和慢动画、台阶/遮挡/多个护卫、盾牌击破/冷却、主人移动/跨维度/下线重连、撤退/上岸/岩浆/拾取/手动背包。

- 最终补核：生命基值来源另存标记，模式内发生阶级变化且此前未生成随机生命时，关闭先恢复当前阶级原生基值再正常抽选；外部基值仍保留。许可/被动面对更近但尚未获攻击许可的威胁时，先暂停远处获准目标的出伤并挡护，不绕过许可。源码分隔符静态检查、界面行范围及两处显示/点击映射一致性检查通过，均不等同编译或游戏行为验证。

- 挡护落点随威胁距离缩短（最低0.8格），避免敌人已贴身时仍固定走到2.5格而越过攻击者；平时2.5格保护距离不变。新增近威胁落点回归尚未运行。

- 主控本地整合提交 `da579fce0348569933ffd2f372a3822b8a2393d0`；静态核查及精确暂存通过，用户原 CHANGELOG 片段哈希未变。新增与更新的自动回归均未编译/运行；测试实例未投放，客户端/服务器后续须统一新协议版本。

## 构建及测试实例同步：2026-10-06 19:54（北京时间）

- 用户授权“构建并同步”，唯一目标 `E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21`；版本3.6.0，源码提交 `32f10e33cf9fed9ca09c339b1a2de62f04177171`。包含生存物资预留/职业弹药偏好/近战防卫持握、药水消耗与再生叠加回血、原版模式/贴身保护/许可攻击等全部已整合修改。
- Java17、仓库 gradlew.bat、三个完整只读API参数执行 `build lootScreeningTest --offline`，首轮成功：41任务，39执行、2已有结果。BodyguardAndVanillaModeTest51、SupplyAndCloseDefenseTest41、PotionRecoveryTest23、SoldierCombatModeTest53、SoldierFollowPolicyTest34、UnifiedConfigTest1897、LootScreeningTest13391及其余check通过；可选依赖隔离373核心类通过。坏配置/缺失结构包ERROR为故障注入预期输出。
- JAR全部ZIP条目可读取，新保护/原版模式/物资/药水类存在，默认配置及双语资源与源码一致；MANIFEST Implementation-Version为3.6.0，mods.toml使用file.jarVersion引用该版本。
- 备份前与替换前两次确认无Java进程；旧JAR及10份相关配置备份逐一核对哈希。只替换指定实例中的本模组JAR，确认仅一份；所有既有配置哈希保持不变。未启动游戏或推送GitHub，用户原CHANGELOG修改保留。
- 源/目标SHA-256 `5CF40CE7D1DC3509BD047255EA384F9DED71A5B42345F311A555F0DD8EC1EF5B`；旧JAR `CBFBF4D82E5D86F347AAA3FC07D7936EED426447416F86296265C70062AAE657`。备份清单 `部署备份/20261006-195454-protection-ammo-potions-Forge_47.4.21/deployment.json`；构建日志 `验证日志/build-protection-ammo-potions-20261006-195242.log`（均在维护上级目录）。
- 游戏内仍待用户验收：食物三槽/最后药品与备用装备保护、职业弹药偏好；近身连续反击与完整动画后恢复远程；药水扣瓶、不重复喝再生、再生II额外两倍食物回血；原版模式开关及外部生命加成；主动/许可/被动贴身保护、近威胁抢占、远程挡护、地形及主人下线重连。现有全局配置缺vanilla_mode时默认为false；启用需添加或修改该项。客户端与服务器必须同时使用本次更新JAR（新命令协议）。自动检查成功不等同游戏行为已验证。

## 贴身选敌、Better Combat前摇与苦力怕救主：2026-10-06（本地修复）

- 基线 `956543c9b3ff0fd8cbdf7bd6d7aa0e1e21c28096`；主控 `/root` 单独编辑主仓库main，共享Human/AdaptiveCombatGoal/配置接口由主控负责。只读审核 `/root/combat_stall_audit` 使用GPT6.1 Sol / medium，禁止编辑/构建。原工作树保留，用户原CHANGELOG片段未改。范围：BodyguardPolicy/Protection、SoldierOrder/CombatMode/HumanRelations、Human、AdaptiveCombatGoal、MeleeAttackGoal、BetterCombatNpcAnimator、AvoidCreeperGoal、新增CreeperSafetyPolicy、既有BodyguardAndVanillaModeTest及文档；无配置/版本/网络枚举变更。
- 原因：Human受伤回调及Adaptive.resolveThreat可在TARGET Goal以外改成自己的攻击者；BODYGUARD setTarget随切换取消pending，造成主人近处优先级被覆盖与前摇来回取消。现在非空直接指定目标统一重定向到保护选择，Adaptive的保护模式不再维护自身反击锁，主人距离严格优先（等距离仅极小保持偏好）；许可/被动普通目标仍沿现有权限，未获许可近威胁可暂停远处攻击并挡护。既有守卫目标记录过期不再清除BODYGUARD正在用的目标。
- Better Combat取消的空动画名原被客户端转换为duration1的默认挥刀，现清除攻击层并恢复idle。未完成前摇时普通战术防御/举盾及重复反击让出；两个近战入口在副作用前检查pending，避免再次假挥刀/扣冷却。严重低血/爆发伤害撤退仍可打断。实际出伤仍由原Human.finishBetterCombatUpswing处理，不新增攻击调度器。
- AvoidCreeperGoal原继续条件仅为路径未走完，随机逃跑16格；现在只避让正在有效引爆者（自然膨胀且方向为正，或已点燃），逐tick复核，到普通6格/带电9格安全线或开始冷却即停止路径，回交原跟随/保护/战斗Goal。目标点限安全线至外侧2格，随机寻路范围缩小；死亡/移除也结束，平静苦力怕不启动无目的逃跑。
- 用户确认所有攻击模式均有救主例外：BODYGUARD且在线主人在上述爆炸危险圈内时，正在引爆的苦力怕最高优先，沿同一canAttack权限入口允许提前击杀；不写永久授权，冷却/死亡/离开危险圈/切换命令后恢复普通规则。苦力怕避让与普通Adaptive退避/防御让出；原退避归队标记不阻挡救主。盾牌/武器切换/弹药与实际命中仍沿既有控制，不能保证引爆前击杀成功。
- 验证：源码选敌/权限/前摇/导航移交逐入口静态核查；本地Forge映射JAR javap确认getSwellDir/isIgnited/isPowered公开API；用户原CHANGELOG片段哈希保持。新增主人近距离压过自卫、持续/冷却/点燃/死亡/安全边界/带电/救主例外边界及关键接入回归，挂既有check入口，尚未编译或执行。未构建、未同步、未启动游戏、未推送。
- 待游戏验收：主动保护主人旁敌人与自身远处攻击者同时存在、连续挨打不来回换目标；许可/被动普通权限不扩大；Better Combat有/无盾及多目标慢武器的完整前摇/命中/后摇；自然/点燃/带电苦力怕引爆取消后立刻恢复、逃跑距离、多个苦力怕、所有模式主人旁引爆优先击杀及停止引爆后的权限恢复、原退避归队和水/岩浆生存控制。
- 最终只读审核补齐一项权限遗漏：原忽略目标记忆会拒绝曾被遗忘、随后在主人身边起爆的苦力怕，而逃跑已让出。仅有效救主期间跳过该忽略检查，存活/维度/距离仍受原门控制，不删除其记忆，危险结束即恢复。递归链与已运行DEFEND释放核查通过；源码分隔符与入口顺序静态检查、git diff --check通过，不等同编译。
- 主控本地整合提交 `9ff9c4474101535fe248037e50b945105cf4fdf2`；精确暂存仅本任务内容，用户原CHANGELOG片段哈希未变，当前仅该原修改留在工作区。新增回归未编译执行，测试实例仍为19:54部署产物。

## 构建及测试实例同步：2026-10-06 21:34（北京时间）

- 用户授权“构建并同步”；唯一目标 `E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21`，版本3.6.0，源码 `132baa32f7c0e5d6c5f58bdd28d712f8120d473a`。包含贴身保护统一选敌、Better Combat前摇/取消动画修复、苦力怕停止引爆及时停止避让及全模式救主例外。
- Java17、仓库gradlew.bat及三个完整只读API参数执行 `build lootScreeningTest --offline`，首轮成功，41任务（38执行/3已有结果）。BodyguardAndVanillaModeTest75、DefensiveBehaviorTest1119、GunCombatPolicyTest57、SoldierCombatModeTest53、UnifiedConfigTest1897、LootScreeningTest13391与其余check通过；可选依赖隔离374核心类通过。坏配置/缺失结构包ERROR为故障注入预期输出，非构建失败。
- JAR全部ZIP条目读取、修复类存在及MANIFEST版本核查通过。备份前与替换前两次确认无Java进程，旧JAR及10份相关配置备份逐一核对哈希，仅替换本模组JAR，实例中仅一份；既有配置哈希全部不变，用户原CHANGELOG未混入提交。未启动游戏或推送GitHub。
- 源/目标SHA-256 `3B6C80E4CD51949DAE4BCCD0B9065878F3AC98E7838CD4B1EAE811C42411B03E`；旧JAR `5CF40CE7D1DC3509BD047255EA384F9DED71A5B42345F311A555F0DD8EC1EF5B`。备份清单 `部署备份/20261006-213437-bodyguard-combat-repair-Forge_47.4.21/deployment.json`；构建日志 `验证日志/build-bodyguard-repair-20261006-213318.log`（均在维护上级目录）。
- 待游戏验收：主人旁近敌优先于士兵远处自卫目标、连续挨打下不来回卡刀；有/无盾Better Combat慢武器连续命中；普通/带电/点燃苦力怕停止引爆后立即恢复，保护全模式救主及危险结束后权限恢复。自动检查成功不代表真实游戏行为已验证。

## 低生命瞬间治疗优先及一秒饮用：2026-10-06（本地修复）

- 基线 `7abba3feea05bbdfd0fe646dd2bc26c4064a2b19`；主控 `/root` 单独编辑主仓库main，RecoverySupplies与Adaptive接入由主控唯一负责。只读审核 `/root/combat_stall_audit` 沿用GPT6.1 Sol / medium，未编辑或构建。范围：RecoverySupplies/PotionRecoveryPolicy、CombatFoodRecoveryGoal/IdleRecoveryGoal/AdaptiveCombatGoal、PotionRecoveryTest与文档；无新增物品、配置、版本或第二套库存使用控制器，既有用户CHANGELOG保留。
- 原因：闲置findBestSlot明确先选食物，战斗独立食物Goal占据ACTIVE_USES并参考projectedHealth，已排队食物回血能掩盖实际危险生命；退避药水还需安全距离才开始。现在实际HP严格低于最大生命30%且库存有可用饮用瞬间治疗时，共用原战斗食物Goal启动急救，覆盖战斗/无目标/退避，不等待食物批次冷却或食物预计回血，闲置Goal让出。普通恢复总开关保留；无可用药水仍沿原食物等回退，不生成免费药品。
- findBestSlot在食品偏好、普通/附魔金苹果和再生选择前优先瞬间治疗；长效再生不会误当急救。战斗/闲置/退避中的未完成食物可中断，完成食物照常登记；先用finishUse和原延迟托管结算归还副手/库存，ACTIVE_USES未释放前不会开第二次交换。食物连续批次也让出，药水不计入食物批次数。瞬间治疗II原效果、非玩家一次扣瓶/玻璃瓶归还及再生阻重复逻辑保留。
- 本地Forge映射JAR javap确认PotionItem.getUseDuration返回32 tick（1.6秒）。仅含HEAL的饮用药水将原时长限制为最多20 tick，短于20不延长；食品沿原加速、再生/增益饮用时长不变。createSession期望完成时间与实际advanceUseTime共用同一recoveryUseDuration，避免缩时间后不扣瓶或认定饮用未完成。
- 验证：源码急救入场、库存锁、中断/结算、退避及耗瓶调用链静态核查；只读审核未发现新增具体问题。新增30%严格边界、32/20/8/0时长、实际血量不读projectedHealth、闲置/退避选择、食物链让出及计时一致接入回归，接入既有PotionRecoveryTest/check，尚未编译执行。未构建、未同步、未启动游戏、未推送。
- 待游戏验收：战斗/闲置/退避从30%以上跌至以下，有食品与瞬间治疗I/II时先喝药，已排队食物回血不阻止；正吃食物时中断无扣食物/丢盾，无药水仍进食；实际喝药20 tick完成且仅扣一瓶，不无限喝；瞬间治疗完成后生命不再低于阈值即回原恢复规则，普通再生饮用保持原时长。
- 主控本地整合提交 `8e9241c175af8e0c17760c2da90262d8eb56744a`；源码分隔符、药水选择优先顺序、共享时长接入及精确暂存核查通过，用户原CHANGELOG片段哈希未变。新增回归尚未编译执行，实例仍为21:34部署JAR。


## 2026-10-06 友军锁定与反复换弹排查

- 基线：`4d51b42c09a5d7b1f397a3f64613338b2db055df`；主控 `/root` 在主工作树、main 编辑；`/root/combat_stall_audit`（GPT-6.1 Sol / medium）只读审核原生 TaCZ 字节码及脚本，无共用编辑会话。状态：实现完成，待构建与游戏验收。
- 范围：Human.setTarget、HumanGunner.tick、AdaptiveCombatGoal、GunnerGoal、TaczHumanReloadTransactions、TaczHumanReloadMixin、既有 GunCombatPolicyTest；版本/配置/存档不改。上次部署仍为 `9ff9c4474101535fe248037e50b945105cf4fdf2` 对应 JAR，本次及此前瞬间治疗修复均未部署。
- 证据：指定测试实例 debug.log 中持 M4A1 的 `1a3f35f1-8a40-4a53-8bec-e300773ff2e8` 连续约5.85秒一次 reload_timeout。只读存档：其空枪（弹匣0、膛内0）、三阶、GUARD/ACTIVE、retreat、恢复计数16；附近雇佣单位归属相同。日志未包含当时目标UUID或每次拒绝原因，不能断言全部超时只有一种原因。枪到枪装备事件可能仅为NBT改变，未以此认定实体反复复制主手。
- 原生证据：TaCZ 1.1.8-hotfix LivingEntityReload.lambda$reload$0 在 startReload 前发送 ServerMessageGunReload；本模组 start 可因使用物品、阈值、支付拒绝，所以失败仍会播音效。M4A1 脚本 start_reload 直接true、needCheckAmmo=false，不缺原生TaCZ子弹；没有证实脚本同步查询导致支付提前清除。
- 修复/接口：赋目标及持续 tick 拒绝控制关系友军，战术缓存继续/执行时重验有效威胁；正常使用物品/无补给等待清除失败看门狗，不触发拔枪恢复。reload 入口先拒绝物品使用/无补给，保留原阈值与支付检查；仅 Human 原生客户端通知延至 start成功后，其他实体维持原通知路径；查询不调用end，清理保留tick/cancel边界。超时debug补齐目标UUID、弹量、膛内、物品使用、换弹状态、库存用于后续诊断。不绕过原生时长、不免费填弹、不放宽实枪身份绑定。
- 回归：扩展既有 GunCombatPolicyTest 检查友军赋目标/实时权限/战术缓存/等待入口/通知先后与成功分支/查询无取消；尚未编译执行。实际验证：原生字节码与内置M4A1脚本只读检查、源码接入与diff检查。按用户规则未构建、未同步、未启动游戏、未修改存档、未推送。
- 游戏验收：现有两名同主雇佣士兵不互相锁定；进食/举盾后空枪完成完整换弹；无弹匣回退、最后付费弹匣不中断、多枪包/霰弹手动换弹/闲置低25%仍正确；若仍超时，根据新增debug上下文继续定位。此前低30%瞬间治疗优先与20tick饮用一并待验收。
- 主控本地整合提交：`df1c7a5a0829ce962d2613f6c1a886a268a2f5be`。只读审核无阻断问题；原生通知调用描述符/单次匹配与先后顺序核查通过，Java源码括号及暂存diff检查通过。用户原CHANGELOG片段哈希保持不变；未执行编译、自动回归、构建与同步。


## 2026-10-06 22:26 瞬间治疗与换弹/友军修复构建同步

- 用户要求“目前并同步”，结合当前待部署修复按“构建并同步”执行。构建源HEAD `ca4c864`（实现 `8e9241c175af8e0c17760c2da90262d8eb56744a` / `df1c7a5a0829ce962d2613f6c1a886a268a2f5be`）。版本3.6.0，Java17，仓库gradlew.bat，显式三个只读API路径。
- `build lootScreeningTest --offline` 成功，51秒、41任务（38执行/3最新）；GunCombatPolicyTest 68、PotionRecoveryTest 37、ReloadPaymentLedgerTest 36、HumanAmmoConfigSwitchTest 58等回归通过。日志：`../验证日志/build-reload-healing-20261006-222436.log`。测试故意注入损坏配置/缺失结构包时打印预期错误，最终任务全部成功。
- 唯一目标：`E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21`。备份及替换前两次核查Java进程均为空；旧JAR与10份相关配置备份并逐项校验，配置保持不变，目标仅一个本模组JAR。备份/清单：`../部署备份/20261006-222613-reload-healing-Forge_47.4.21/deployment.json`。
- 旧JAR SHA-256：`3B6C80E4CD51949DAE4BCCD0B9065878F3AC98E7838CD4B1EAE811C42411B03E`；新产物与实例SHA-256一致：`03963154060DCD585EAE07542BDACCFAEFD5AB91C10B97C3A578B2A54DC6D9CF`。
- 已同步：低30%生命瞬间治疗优先、最长20tick饮用；旧友军选敌/战术缓存清除、换弹拒绝不播音效、普通等待不重置拔枪、只读换弹查询。未启动游戏、未推送、未修改存档，游戏行为尚未验收。重点复测此前M4A1士兵友军锁定和完整换弹、食物/举盾后换弹、最后付费弹匣及低血量喝药。


## 2026-10-06 斯巴达默认装备导入修复

- 用户范围：只让斯巴达武器及盾牌正常自动联动；沉浸式盔甲未装是预期跳过，本次不改变其默认池、物品ID或安装依赖。
- 基线：`6acb8ce2a48cae54babae04931e969c9e927a4db`；main/主工作树，主控 `/root` 编辑；`/root/combat_stall_audit`（GPT-6.1 Sol / medium）只读核查已装JAR及审核，不共享编辑。范围：EquipmentIntegrationDefaults、SplitConfigFiles、TierConfigFilesTest、默认注释、README/CHANGELOG；不改公共战斗/动画逻辑、版本和实例。
- 当前证据：测试实例安装 SpartanWeaponry3.2.1 / SpartanShields3.1.1，mod ID与候选注册名正确，木盾为wooden；四阶主手/备用/弓弩/盾牌池均与默认一致、开关true且无_imported回执。旧入口仅LegacySplitConfigFiles.load中sources.isEmpty时seed；已有五个v2文件不再调用，导致提前生成或迁移配置漏导入。现有commonSetup.enqueueWork的reloadForServer可在注册表就绪后重试，但旧load链没有导入入口。沉浸式盔甲默认池已有条目，未安装时requires_mod正常跳过，用户已确认不装且不纳入本次修改。
- 修复：统一分阶load入口对开关开启、没有成功回执且仍为完整默认的非空池重试导入，仅加入已安装模组且已注册候选物品；登记回执避免后续删除又补回。自定义池/权重、空池、无效开关/回执、关闭开关保留；按池判断，允许其它仍为默认的池导入。已导入/缺依赖/未注册不重复写文件。
- 存储：有效现有v2文件先在原始JSON上仅修改导入池及回执，填默认键和说明仍只在内存；写入前核查原字节快照、生成并校验独立pre-equipment-import备份、临时文件后原子替换，保存失败重读当前文件。新配置首次导入直接写入新文件；旧源无效/备份失败禁止生成时不注入。global不写、不检查旧源恢复被删值。
- 回归新增：未注册时生成五文件→注册后加载并保存四阶完整导入/回执，精确原字节备份、global不变、独立属性/注释保留、已有回执删除不补回、空池/自定义权重/关闭/非法开关/缺模组/并发旧快照/损坏JSON保护。尚未编译执行。
- 实际验证：JAR mods.toml/物品注册字节码及资源只读检查、JSON语法/默认游戏参数不变、源码分隔符、diff与用户CHANGELOG原片段哈希核查。未构建、未同步、未改实例配置/存档、未启动游戏、未推送。
- 待构建与游戏验收：默认安装两斯巴达模组，重启后四阶池出现可用装备与回执，出生新单位可随机持有斯巴达武器/盾牌；不是每名单位必带，已经生成的实体不自动换装。用户自定义或空池及禁用开关仍按配置执行。
- 本地整合提交 `d355ddfb08940fb27c58823257c7b2a0c8668cd9`。只读审核发现并已修正非法开关默认true的问题，最终复核通过；自动回归未编译运行。测试实例JAR仍为上次部署SHA-256 `03963154060DCD585EAE07542BDACCFAEFD5AB91C10B97C3A578B2A54DC6D9CF`。


## 2026-10-06 自动导入全局总开关与自定义池共存

- 基线：`ad68a0fac082183bda0d883b8a871df02f8bff41`；main主工作树；主控 `/root` 编辑，`/root/combat_stall_audit`（GPT-6.1 Sol / medium）只读审核。范围：EquipmentIntegrationDefaults、LegacySplitConfigFiles、SplitConfigFiles、TierConfigProjection、默认配置、TierConfigFilesTest及文档；不改战斗/动画/掉落/空主手回退，未构建同步。
- 用户需求：默认开启的武器装备自动导入总开关；开启向装备池追加默认联动装备但保留玩家新增条目；关闭后加载不导入/改写装备池。玩家可删除默认原版条目，用合法自定义装备池替代。
- 配置：global.json的 `equipment_integration.auto_import_enabled=true`，有双语说明、全局排序位置。旧文件缺项在内存默认true；需要显式关掉时可手动添加该组，既有global不强制重写。总开关只控制自动导入，不禁用动画/物品自身兼容；原分阶automatic_spartan_weapons/shields仍分别有效，已导入条目关闭后保留。
- 实现：真实global先读，再统一门控新建和现有阶级导入；删除LegacySplit旧首次seed旁路，根导入入口亦检查总开关。seedRank从“默认相等才导入”改为“非空合法池首次追加”，保留自定义条目及已有同ID权重（含0）、计数和额外字段；空池、非法开关/回执、已有成功回执仍保护，不恢复后来删除项。新增装备仍为已安装/已注册的原斯巴达候选，默认权重1，不扩展其它模组。
- 自定义边界已向用户说明：默认池有原版武器/弓弩/盾牌/护甲，删除条目不会被schemafill恢复，空护甲/盾牌/备用池不补默认；主手/远程池无有效装备仍有原版安全回退。本次不改该战斗安全策略。完全按自定义装备出生还应关闭全局equipment_growth.enabled并按需关闭各阶allow_tacz_firearms，避免额外生成路径。
- 回归新增/调整：globalfalse且仅global存在的首次加载、关闭后五文件字节不变/无备份、开启保留自定义新增与已有weight0并追加、再关不删已导入项、缺项默认开启、非法总开关拒绝及根入口门控；此前自定义池测试更新为追加但保留原行权重。尚未编译或执行。
- 静态验证：默认JSON有效，除总开关新增外游戏默认参数不变，源码分隔符与diff核查、用户CHANGELOG原片段哈希保留。实例配置/存档与JAR未改，未启动游戏、未推送。待构建及游戏验收开关两状态与自定义装备出生。
- 只读审核发现global损坏时默认开启回退可能误授权；已将无效/不支持schema的global仅在内存关闭自动导入，并新增坏global与schema999不改四阶文件回归。其它游戏默认回退不变，原文件保留。
- 主控本地整合提交 `483d9f31a82390b3b7a31f5f9b475ab1c42accff`，只读最终审核通过，任务精确暂存与用户CHANGELOG保护检查通过。新增自动回归未编译运行，源码总开关及上一项斯巴达重试修复均未构建部署。


## 2026-10-06 沉浸式盔甲统一自动导入

- 用户纠正范围：equipment_integration.auto_import_enabled 同时管理斯巴达武器/盾牌与沉浸式盔甲，沉浸盔甲采用相同的首次追加、自定义保留与关闭不导入规则。本次未要求构建/同步。
- 基线 `583442c3c37e66f39bf8c4cfc8511b9482eec50d`，main主工作树；主控 `/root` 编辑；`/root/combat_stall_audit`（GPT-6.1 Sol / medium）只读核查本地API与审核。范围：EquipmentIntegrationDefaults、默认配置、新候选资源、TierConfigFilesTest及文档；总开关与配置备份边界继续复用，无第二套护甲替换机制。
- 将原基础armor_sets的沉浸套装移至只读候选资源defaults/equipment_integrations.json；基础新配置只含原版套装。沿用原范围：流浪者无候选，一阶wooden/bone/robe，二阶warrior/heavy/divine/wither，三阶prismarine/heavy/divine；套装权重分别1/1/38，三阶原版下界合金186不变。
- 安装immersive_armors且候选完整四部位已注册时，受总开关与各阶automatic_immersive_armors（默认true）控制，一次追加到非空armor_sets并写_imported_immersive_armors。相同head/chest/legs/feet套装去重，保留已有weight0、自定义附加字段和说明；空池、无效开关/回执、成功导入后的删除仍保留。关闭不删除旧配置显式套装，与斯巴达已导入装备一致。
- 本地只读依据：新二创内容/mods/[沉浸式盔甲] immersive_armors-1.7.2+1.20.1-forge.jar，mod ID正确；8材质×4部位32个唯一ID的模型/配方及套装注册字节码确认。未安装进测试实例，也未修改该JAR。
- 回归新增：基础池不预列联动、未注册不登记、整套追加/权重保留、去重保留weight0/注释、删除回执不补回、空池/分阶关闭、缺部位不导入；原空池/关闭/非法分阶开关用例扩充沉浸盔甲分支，根入口也保护空池。尚未编译运行。
- 静态验证：两个JSON有效，迁出候选与原套装ID/权重/分阶逐项相同，其余游戏默认参数与原版护甲池不变；源码分隔符与diff检查、用户CHANGELOG原片段哈希保留。未构建、未同步、未动实例配置/存档、未启动游戏、未推送。
- 待验收：安装沉浸盔甲后总开关开启可在一至三阶池看到候选及回执；关闭的新池不出现候选；旧套装不删，自定义套装/权重及删除记录保留。候选只参与随机出生，不保证每名士兵穿联动盔甲。
- 本地整合提交 `c5de283ead84f121d5fa0e31a533a19a30fee41f`，最终只读审核通过；自动回归未运行，尚未构建或部署。

## 2026-10-06 装备统一自动导入构建与测试实例同步

- 用户授权“构建同步”；源码基线 `47046426ea56a72fb949bce3e18af0114e905a2a`，版本3.6.0。包括斯巴达已有配置导入修复、全局自动导入开关及沉浸式盔甲统一导入；用户CHANGELOG未提交修改保留。
- Java17、仓库gradlew.bat、三个只读API完整路径参数执行 `build lootScreeningTest --offline`：BUILD SUCCESSFUL in 48s，41任务39执行2最新。TierConfigFilesTest 383、UnifiedConfigTest 1915、LootScreeningTest 13391检查通过；日志中异常为损坏输入/备份失败等回归用例的预期诊断。
- 构建日志：维护目录/验证日志/build-equipment-import-20261006-234752.log。
- 唯一目标 `E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21`；两次核查无游戏Java进程，备份11文件（旧JAR与10份配置）并逐一核对哈希后替换。备份：维护目录/部署备份/20261006-234916-equipment-import-Forge_47.4.21/deployment.json。
- 源/目标JAR SHA-256均 `3EEA3126F9E01A07F96BC6A327EB0BAC596A9FE46A86D641A279DDADF3A00980`；目标仅一份本模组JAR，配置哈希不变。未同步其他实例，未启动游戏，未推送。
- 待游戏验收：实例重启后确认默认联动装备追加及回执、自定义条目/权重保留、关闭总开关不导入；沉浸式盔甲需实际安装对应模组。自动化验证与构建成功不代表游戏内行为已验收。


## 2026-10-07 当前五配置清理与重要设置优先布局

- 基线 `3137bf2cfa899419734df2e3859b229472a60910`，main主工作树；主控 /root 编辑，/root/combat_stall_audit（GPT-6.1 Sol medium）只读审核。用户要求不再兼容旧版配置，停止生成继承/更新文件并重排配置；未要求构建或同步。
- 删除LegacySplitConfigFiles、LegacyTomlConfig、ConfigSchemaUpgrade、UnifiedConfig.migrate/alias及枪械旧键兼容。仅当前五JSON为设置来源；缺失文件按当前默认值生成，旧源无论合法或损坏均不读取、不转换、不删除。
- CurrentConfigDefaults只在内存填当前缺项，保留支付、枪械名单/权重的完整自定义映射，不再升级旧护甲池或说明。配置保存移除所有持久备份，保留源快照校验、临时文件和原子替换；已有历史文件不自动删除。
- 全局按原版模式/弹药消耗/装备导入/AI开关/生成/雇佣/伤害/联动/成长/结构顺序；各阶按属性/生成/装备/弹药/药水/雇佣/伤害/战斗/AI/移动散布/枪械/成长顺序。嵌套装备开关数量前置、枪械长名单后置。现有有效文件仅重排原字段，不持久化缺项默认，保留未知字段与说明；坏文件不写。
- 删除旧迁移/升级回归，新增旧源忽略、仅五文件生成、已有文件值/空池/缺项保留、重排幂等、无备份和用户映射保护回归；原联动回归保留但修改无备份与排版期望。新增/更新回归尚未编译运行。
- 待验证：构建自动回归；启动五文件重排与导入后目录干净、自定义值和出生行为符合配置。未构建、未同步、未修改实例配置或启动游戏、未推送。
- 静态验证通过：默认JSON有效且全部游戏默认值不变，修改Java分隔符核查、旧兼容引用无残留、用户CHANGELOG原片段SHA-256保留；只读代码复核通过。自动回归未编译运行。
- 本地整合提交 `7b0144f7537eed61a261f495572a23c79107dd10`，任务文件精确暂存；原有CHANGELOG修改继续保留为未提交。


## 2026-10-07 闲置水中上岸堵塞与撤退触发清理

- 基线 `ad50854de749116bce924a59bafcb160818c7b2d`；main主工作树，主控 /root 编辑，/root/combat_stall_audit（GPT-6.1 Sol medium）只读诊断/审核。范围Human、LeaveWaterWhenIdleGoal、ShoreProgressTracker、AdaptiveCombatGoal、RecentDamageTracker、CombatPressurePolicy、RetreatRecoveryPolicy、现有策略回归及文档；未要求构建或同步。
- 源码堵塞依据：原位置差包含Y方向，水中浮沉/微小绕圈反复清除stuckTicks；full shore search失败不停止原有live path，navigation.isDone=false使直接引导不运行；搜索将避免目标仍纳入fallback；Human水陆边界短暂清seekingShore后运行Goal不恢复。
- 新watchdog检查朝下一路径节点/岸边至少0.25格的有效水平接近或节点推进，60tick无进展重选目标；失败搜索40tick限频，恢复水导航+清旧路径+直接岸边移动。有效fallback在失败重试时保持方向与进展期限，只有卡住/目标失效才换岸边目标；避免目标从路径与fallback同时排除。Goal运行中可恢复丢失的上岸状态，仍服从战斗/撤退/水中拾取优先级和全服寻路预算。
- 删除80tick累计超过40%最大生命的consumeRetreatTrigger、锁100tick字段/方法/持久标记及canUse/tick入口；独立围攻撤退入口同时删除，以符合仅低血量战斗撤退。伤害采样仍服务近敌目标选择，围攻标记仅在已有低血撤退中辅助枪械反击，不触发或延长撤退。恢复结束改按真实生命阈值和最短承诺期判断；恢复物品使用损伤容忍配置、环境避险保留。
- 回归新增：绕圈无接近、有效接近重置期限、路径节点推进及没有岸边时有限期限；移除高血围攻撤退断言，更新真实生命恢复退出断言。自动回归尚未编译运行。
- 待游戏验收：闲置静水/流水与一格岸阶、窄河转弯、不可达岸边和多人同时入水，确认不受击也自动恢复上岸；高血短时受伤/多人围攻不触发战斗逃跑，低血撤退和恢复反击仍工作。未构建、未同步、未动实例配置或启动游戏、未推送。
- 最终只读审核通过：避免卡点贯穿完整寻路与备用小扫描，源中已无高伤害触发方法/标记引用；Java分隔符、diff检查及用户CHANGELOG原片段哈希核查通过。自动回归未编译运行。
- 本地整合提交 `3af5d57681a3dc79a4f9e6e1fa381469cbc55d2e`；原用户CHANGELOG改动继续保留为未提交，尚未构建或部署。


## 2026-10-07 低血量搏斗、真实恢复试探与双硬撤退底线

- 基线 `74c45c8676ca60102eeaf9dbecdc9eaaaff81a42`，主控 /root 在main编辑运行逻辑及CombatPressurePolicyTest；/root/combat_stall_audit（GPT-6.1 Sol medium）在独立工作树 `worktrees/low-health-combat-config` / `feature/low-health-combat-config` 编辑配置类、默认配置、UnifiedConfigTest和指南，提交 `d30858fcc539ece3d37171f08eb6bc036d2ffca6` 已审核并cherry-pick为 `21fef6f`。后续主控统一公共接口与指南，并由该agent只读审核主工作区。
- 用户决策：唯一有效目标同时生命<30%最大值且<30点时默认50%继续搏斗；本人硬撤退同时要求生命<15%且<25点。缺瞬间治疗时可有限试探，实际回血能抵御真实伤害则反击，否则退；沿用正常食物恢复和武器动作。
- 接口：CombatAiConfig.lowHealthCombat公开八字段record，每阶ai.low_health_combat；默认比例.15/绝对25/概率.5/试探40tick/观测80tick/续战保持80tick/失败重试100tick/回血安全倍率1.1。全局仍仅AI总开关；配置排版将本组放在低血阈值附近。
- 单一LowHealthCombatController供Human原生概率、RunFromTarget、Adaptive恢复/撤退和低血目标选择接入。纯状态决定FINISH/TRIAL/SUSTAIN/RETREAT；概率缓存按对手及恢复阶段保持，硬底线不再永久置true，返回搏斗恢复合法toAvoid且不误回主人驻点。
- 实际观测在Human.hurt/heal完成后取生命差，尊重防御/取消/生命上限和外部heal修正；记录不自动触发高血量撤退。试探无补给亦可，但无实际回血无法维持续战；无受伤样本不把逃跑时回血判为耐伤。净损失显著或最近一击可致死时提前收回搏斗许可，失败限频。
- 唯一对手扫描10tick限频，换目标立即刷新；其它可攻击玩家、Human、怪物及最近80tick其它存活合法攻击者都计入。样本保留至少80tick，即使自定义观测更短也不丢攻击者记忆。弱Map状态只保留UUID/数值，无强引用实体循环。
- 保留RecoverySupplies库存/消耗与CombatFoodRecoveryGoal。瞬间治疗guard覆盖背包、ACTIVE_USES和在用物品，直到结算；避免最后一瓶移副手后被误取消。站定搏斗可继续既有战斗食物恢复，原物品使用互斥与BC/TaCZ动作保持。
- 回归：配置解析/异常/四阶投射/独立覆盖；硬底线双条件及严格等号、残血双条件、一次概率、多次调用稳定、药水优先、无物品限时试探、真实回血覆盖、失败冷却、明显掉血早退、无受伤样本不批准持续搏斗。均未编译运行；用户本轮未要求构建同步。
- 待游戏验收：20/60及高外部生命NPC的双硬底线；单个残血敌人与第二名攻击者出现；食物/再生持续时反击及强火力撤退；最后一瓶瞬间治疗喝完扣瓶；Better Combat及弓弩/TaCZ副手进食时动作与恢复切换。未构建/同步/启动游戏/推送，保留原CHANGELOG修改。
- 最终只读复核与静态检查通过：Java分隔符、默认JSON、旧force/短伤引用、任务diff及用户CHANGELOG原片段哈希核查；自动回归未编译或执行。原critical_health_ratio仅恢复动作使用，独立旧危急比例撤退入口删除。


## 2026-10-07 3.6.5 构建与测试实例同步

- 用户授权“构建并同步为3.6.5版本”，主控 /root 在main执行，基线51488d6d52911a4ad58031f7e563d5a533c2467d；版本/更新日志提交5a3f8fc，最终构建源码41a4f582529ca7995d50ace61569fc6d27226f4d。原用户CHANGELOG中3.6.0及后续段落保留未提交，未推送。
- 纳入已整合的当前五配置布局与旧兼容移除、闲置上岸停滞恢复、短时高伤及独立围攻撤退删除、低血量搏斗与实际恢复试探。默认硬撤退同时要求本人低于15%和25点，弱单目标默认50%搏斗，各阶可配置。
- 首次完整构建在TierConfigFilesTest失败：预置七旧JSON的用例错误要求目录只有五JSON。262d95e将全新目录五文件和旧目录十二文件分开验证，继续确认旧源字节保留；第二次在UnifiedConfigTest失败：边界测试仍用已移除的旧平铺属性键。41a4f58将该输入改为当前attributes/damage_multipliers结构。均仅测试修正，未恢复旧配置兼容或放宽生产逻辑。
- Java17及三个显式API路径执行build lootScreeningTest --offline，最终BUILD SUCCESSFUL in 41s，41任务37执行4最新。CombatPressurePolicyTest162、TierConfigFilesTest286、UnifiedConfigTest123、LootScreeningTest13391检查通过，OptionalRuntimeTest无TaCZ/Curios/Maid链接380核心类，其余check任务通过。损坏JSON/元数据等ERROR为预期坏输入回归诊断。
- 成功日志：维护目录/验证日志/build-3.6.5-20261007-004921.log。JAR manifest确认Implementation-Version为3.6.5，两个mod ID经file.jarVersion使用此版本。
- 唯一部署目标E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21；备份前及替换前无非Gradle Java进程。备份旧JAR及14配置共15文件并核对每份SHA，备份维护目录/部署备份/20261007-005037-3.6.5-Forge_47.4.21/deployment.json。
- 目标mods只留hostile-humans-unified-1.20.1-3.6.5.jar；源/目标SHA256均380AF284878549CA93A419DBE2D77B5D49FCF11B6B1A7920A92E6B094DC26B47，原3.6.0 JAR已备份后移除，所有配置部署前后哈希不变。已有旧备份文件保留；启动后新逻辑不会继续创建迁移/导入备份。
- 未启动游戏、未推送、未同步其它实例。待游戏验收：水中无需受击自动上岸、低血量试探回血与反击/撤退切换、高最大生命的双硬底线、瞬间治疗最后一瓶扣除及BC/TaCZ动作关系；重启后当前五配置重排保留自定义值。


## 2026-10-07 3.6.5 发布状态

- 用户要求将3.6.5状态改为已发布；CHANGELOG版本标题明确标记“已发布 / Released”。3.6.5构建与测试实例同步已完成，源码main已推送至c7a8cdb64639b0929570447ea733551ee5e92704；本次仅更新发布状态文档并同步GitHub，不重新构建或替换JAR。原用户CHANGELOG未提交修改仍保留。


## 2026-10-08 导航与寻路全面修复（源码已整合，未构建）

- 基线 main d560837dfe1e7be69099a3bcb0e4c304b737fc46，版本仍3.6.5；用户明确批准：跟随直接浅/深水表面渡水，贴身保护优先主人跟随，长期卡住允许安全传送，撤退允许有界绕路，其余审查建议逐项修复。本轮未要求构建/同步/推送。
- 主控 /root 在main唯一编辑Human、SoldierOrder/Goal、Follow/Bodyguard、MoveControl、ShoreSeekingPolicy、MovementContinuity、普通/环境搜索预算及调用入口、公共接口、测试注册和文档。将实际士兵/临时援军移动移入MOVE Goal，生命周期仅校验目标/状态；停止只释放自有路线。
- /root/navigation_retreat（GPT-6.1 Sol medium），worktrees/navigation-retreat / fix/navigation-retreat，提交95e8f85f1697ec71f5b1663f1489c747a45d83a8整合为d4538bb：Adaptive/Native/Gunner/Lava及三个退路helper、CombatPressurePolicyTest；后续主控完成公共接入，并由该agent只读交叉审查，发现的水危险边界、候选筛选及连续性修复绕过安全约束均已补齐。
- /root/navigation_interactions（GPT-6.1 Sol medium），worktrees/navigation-interactions / fix/navigation-interactions：dc5e6c6、b0b6c8a、ad6f3c6、8991106、59b4f3f，依次整合为1023596/e8eb125/8b64c4d/323d553/65f8191。范围门/栅栏/活板门/梯子/TNT、HumanNavigation/NavigationSupport、安全通道租约及两测试；均无主仓库或实例写入权限。
- 行为：保留有效跟随倍率，上限3；正常4格开始、2.5格停止，失败逐步退避到40tick。持续200tick无接近/路径进展后允许近距离安全传送，贴身保护在4格内不触发救援；沿用远距离救援。落点只取已加载、世界边界内、无碰撞/危险的干地或实际水面，找不到不盲传。
- 水域：跟随/保护不触发闲置强制上岸；查询不再清路径。寻岸状态成功后登记，搜索候选导航器与当前导航租约隔离，失败恢复有效旧路；先扫描有限地下岸阶，再周期扫描地表，完整搜索仍最多两个候选/四次A*。水中退避无路时仅短距离水平安全输入，扫描完整扫掠范围的区块/边界/危险。
- 退路：共享整段安全筛选，最低敌距3格（已贴敌不能更近），最多损失min(3格,初距25%)，接近段最多6格/仅初8格；整路最多min(128格,直距两倍+12格)，最多192节点。所有候选、寻岸和连续性修复一致筛选，成功才登记；枪手失败进入安全短步。原生撤退作为关闭Adaptive AI时的单一回退，仍保留低血触发和双硬底线。
- 避险/通道：TNT当前导航、-11优先级、先接管再搜索，预算排队不耗失败次数，同一TNT100tick安全短步期限不因重启刷新；临时避险后驻点命令保留。梯子只辅助当前路线、不占MOVE、闲置也生效；垂直进展不误判卡住。通道只扫描附近节点，统一开门权限和关闭租约，只关闭本系统实际打开且无人占用/即将通过的门；100tick仍占用则保留开启。禁止默认穿过不可手动打开的关闭铁门，原按钮/拉杆交互保留。
- 性能：普通路径共用每服32次/每次最多4的FIFO预算；多候选搜索仅领取完整批次，余量不足时保留队列位置，避免稳定tick顺序下部分额度导致长期饿死；战术/退避/爆炸/连续性沿用分池预算，水射击上岸使用独立键但共用战术池，排队可逐tick续办。闲置寻岸每服每tick最多一批，完成后释放排队，避免最先tick的NPC独占。普通弓弩/三叉戟/近战/拾取/巡逻/漫游/声音追踪入口均接预算。
- 实际验证：全部300份Java由tree-sitter语法解析无错误；24项源码接入断言通过；映射JAR的MoveControl/PathNavigation方法只读核对；git diff --check通过；用户CHANGELOG原片段SHA256仍340C905E8C54142D3C0FC15961D82539DA50BD9FAC7C416055A43C4E7D039EC2。解析器安装于维护目录maintenance-tools/navigation-parser，不进入源码Git。
- 新增/扩展跟随救援、失败退避、寻岸公平排队、整段退路几何和通道/TNT回归；新任务已注册check。未执行Gradle、javac或Java测试；语法/源码检查不代表编译、运行或游戏行为通过。
- 待用户要求构建后执行自动回归；游戏验收：浅/深水跟随与贴身保护、不可达楼层长期救援、U形墙低血绕路与恢复、地下水/岩浆岸阶、TNT中断驻守后返回、多人排队过门/梯子、群体NPC寻路性能、Better Combat/TaCZ换武器及恢复动作不被移动破坏。未修改任何实例配置/JAR、未启动游戏、未推送；原用户CHANGELOG改动保留未提交。

- 主控最终本地整合提交 c18497ed95fce5dc7aa69b8d4469848d090aa411；最后复核全部300份Java解析无错误、完整批次预算三个接入口通过，暂存CHANGELOG仅含本任务未发布条目。主工作树仅剩原用户CHANGELOG未提交修改；无构建、同步或推送。


## 2026-10-08 导航修复构建与测试实例同步

- 用户授权“构建并同步到测试实例”；主控 /root 在 main 执行，导航基线44d8610，最终构建源码d279be3，版本保持3.6.5，未推送。
- 首次构建编译通过，但PickupPolicyWiringTest仍检查旧SoldierOrder.tick入口。0c8f574将断言改为tickMovement并增加MOVE Goal接入与生命周期不直接写导航的检查。第二次构建TntEscapeSourceTest的相对路径在JavaExec中找不到源码；d279be3由Gradle传入绝对路径并按UTF-8读取。均仅修正验证接入，未削弱生产行为或检查要求。
- Java17、三个显式只读API路径执行build lootScreeningTest --offline，最终BUILD SUCCESSFUL in 37s，43任务39执行4最新。SoldierFollowPolicyTest87、CombatPressurePolicyTest170、InteractionSafetyPolicyTest18、TntEscapeSourceTest8、PickupPolicyWiringTest76、PerformancePoliciesTest53077、LootScreeningTest13391检查通过；OptionalRuntimeTest无TaCZ/Curios/Maid链接388核心类。损坏配置用例的ERROR为预期诊断。
- 成功日志：维护目录/验证日志/build-navigation-20261008-final.log；JAR manifest版本3.6.5，新增导航预算与临时援军MOVE类已包含。
- 唯一目标E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21；部署前及部署后未检测到非Gradle Java进程。旧JAR及14份配置共15文件已备份并逐一核对哈希。备份维护目录/部署备份/20261008-103241-navigation-Forge_47.4.21/deployment.json。
- 源/目标JAR SHA256均E62FDDDD7F845FFD415EF2A04F0E09534E8066A23E9BAAF9023D1239A14438B6，目标mods仅一份本模组JAR，所有配置哈希不变。原用户CHANGELOG未提交修改保留。
- 未启动游戏、未同步其它实例、未推送。待游戏验收：水面跟随/贴身保护、长期卡住安全救援、绕墙撤退与地下水上岸、TNT临时避险及驻点恢复、多人门梯通行，以及Better Combat/TaCZ战斗移动衔接。自动回归通过不代表游戏内行为已验收。


## 2026-10-08 水面跟随导航类型修复（未构建）

- 用户反馈导航版测试JAR入水后士兵转圈、不跟随，并明确继续禁用水下游泳。基线main a179a63，主控 /root 在主工作树编辑；无新增开发agent，未要求构建同步。
- 依据：Human.updateSwimming此前对非上岸的入水实体一律选择WaterBoundPathNavigation；其SwimNodeEvaluator按实体全身水体判断节点（空气边缘返回BREACH，普通WaterBound不允许breach），并可选择水下节点。但Human.setSwimming(false)及水中移动速度的非负Y限制只允许水面移动，形成路径类型与可执行移动冲突。只读javap核对当前Forge映射API与节点实现。
- 修复：FOLLOW/BODYGUARD和临时援军跟随继续使用已有canFloat=true的HumanNavigation，浅/深水表面和岸上使用同一导航，不因入水停止原路线；闲置上岸继续让路，原有水中移动/上浮和禁止下潜保持。其它撤退/寻岸路径选择本次不扩展。
- 新增11项导航选择策略边界及一项编译入口ASM回归，覆盖岸上/水面/残留上岸状态、非跟随与AI关闭；尚未编译执行。本轮只做源码语法、diff和原用户CHANGELOG片段保护核查，不代表游戏行为已验收。
- 待构建及游戏验收：跟随入水后主人持续移动、停下再出水上岸、贴身保护穿水、深水仅水面移动、战斗/低血避险中断后恢复跟随。测试实例JAR、配置和存档未改，未启动游戏或推送。


## 2026-10-08 水面跟随修复构建与测试实例同步

- 用户授权“构建并同步”；main源码61bfdb2，Java17与三个显式API参数运行build lootScreeningTest --offline，BUILD SUCCESSFUL in 42s，43任务40执行3最新。SoldierFollowPolicyTest99项（含新增导航类型及实际入口检查）、其它check任务及LootScreeningTest13391项通过。日志维护目录/验证日志/build-surface-follow-20261008.log。
- 版本保持3.6.5。唯一目标E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21；两次部署前核查无非Gradle Java进程，旧JAR及14份配置共15文件备份并校验。备份维护目录/部署备份/20261008-113647-surface-follow-Forge_47.4.21/deployment.json。
- 源/目标SHA256均9895E1B012F5A5C9C72AB04064723233EF725EAA23DD5B1070DEB173984F63CB；目标仅一份本模组JAR，配置哈希不变，原用户CHANGELOG修改保留。未启动游戏、未推送或同步其它实例。
- 待游戏验收：FOLLOW/BODYGUARD入水持续跟随、停下后再行进、跨岸往返及深水保持水面；此次自动检查不替代转圈现象的游戏验证。


## 2026-10-08 食物、药水与护甲自由配置加载核查（未构建）

- 用户要求核查自由配置在新生成单位上的生效，补充护甲范围。主控 /root 在main单独编辑，无新agent；基线78a57d5db66fdaa44312b0d83d7fe7d4b1e86def。原用户CHANGELOG保留；未授权构建或实例修改。
- 完整链路与结论见docs/SUPPLY_CONFIG_AUDIT.md：五文件读取/投射/默认合并/分阶快照/实体生成/补给与成长。药水和护甲显式池可到达生成器，空池不会补回；坏schema/ID、全局AI补给门控、容量、自动导入与成长可形成不生效表象。
- 实际缺口：普通食物和两种金苹果发放此前硬编码，装备inventory为近战备用池。新增分阶food与SpawnFoodConfig接入真实RecoverySupplies，保持原19候选及分阶加权数量/金苹果默认；删除硬编码和空候选牛排回退，支持单份食物；药水和护甲无效ID/部位新增一次诊断。
- 新增SupplyConfigurationTest的真实临时五文件加载链回归，并注册check；未编译执行。静态302份Java解析无错误、默认JSON除food新增外逐项相同、diff检查和原用户CHANGELOG片段SHA256保护通过。
- 未改实例配置/JAR、未启动游戏或推送。后续验收按审查文档，尤其新生成/重启、自定义单池、药水数量/ID、关闭成长后护甲精确池和无效ID日志。


## 2026-10-08 食物药水配置复核、构建与测试实例同步

- 用户授权再次核查食物与药水配置，确认后构建同步。主控 /root 在 main 单独完成；源码基线及实际构建提交7d49570abd66ed3f9b1e2c6569432ada52b21fb3，版本保持3.6.5，无新agent。加载、默认合并、分阶快照及出生发放接入复核未发现新的覆盖问题。
- Java17和三个显式只读API参数执行build lootScreeningTest --offline，BUILD SUCCESSFUL in 1m 36s，44任务42执行2最新。SupplyConfigurationTest23、PotionRecoveryTest37、TierConfigFilesTest286、UnifiedConfigTest123、LootScreeningTest13391项及其余check任务通过。自定义注册ID测试验证加载快照，不替代第三方药水游戏使用验收；坏配置ERROR为预期诊断。日志维护目录/验证日志/build-supply-config-20261008.log。
- 唯一目标E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21；备份前及替换前无非Gradle Java进程。旧JAR及14份配置共15文件已备份并逐份核对SHA256，备份维护目录/部署备份/20261008-181658-supply-config-Forge_47.4.21/deployment.json。
- 源/目标JAR SHA256均BC6832DC0A34F66B0D83A1DFF5FE48F1F623E272329BEEBA42FC36124AFE6AA8；目标仅一份本模组JAR，14配置哈希全部不变。已有阶文件缺food时继续使用默认配额，自定义食物需按docs/SUPPLY_CONFIG_AUDIT.md添加food组；重启后仅新生成单位领取出生配置物品。
- 未启动游戏、未推送、未同步其它实例。待游戏验收：新生成各阶单一自定义食物池/数量、注册药水ID及零配额、护甲单套（关闭成长/自动导入），背包容量与旧士兵不补发。原用户CHANGELOG片段保护哈希仍340C905E8C54142D3C0FC15961D82539DA50BD9FAC7C416055A43C4E7D039EC2。


## 2026-10-08 女仆识别人类与身份牌关系兼容（源码整合，未构建）

- 用户批准上一轮建议的关系/行为。基线main 08745de8a26f231fe5f9514cd58057898e93c79e；主控/root独立编辑当前主工作树，无新agent。公共TouhouMaidCompat、mixin清单、构建注册与文档由主控负责，原用户CHANGELOG修改保留。
- 证据：Human经HumanMobEntityData继承TamableAnimal；女仆1.5.3的IAttackTask排除有主人的可驯服实体，DefaultMonsterType把其它可驯服实体归FRIENDLY，故野生人类也默认不进入主动攻击列表。实际测试实例原版女仆和只读modified-all API均有EntityMaid.m_6779_(LivingEntity)入口；近战/远程选敌均调用该入口，近邻感知不额外剔除人类。
- 接入：可选@Pseudo TouhouMaidHumanTargetMixin仅为已驯服女仆的人类目标改写canAttack，显式匹配开发/生产名，已安装目标类要求注入成功；非人类与未驯服女仆沿用原规则。TouhouMaidCompat复用IdentityBadgeAccess与HumanRelations，最高身份牌含Curios；野生敌对、同阶中立、高阶友好；正式/临时控制人类优先保护（含其它主人的士兵，延续女仆原默认不攻击已驯服对象）；未知/离线主人不判敌；实际攻击主人/女仆可还击、信标强制敌人覆盖身份牌。
- 保留女仆战斗任务门控、和平难度、队伍关系、MaidNoAttack名称、全局忽略名单与手工FRIENDLY分类；搜索/视线/距离及武器动作由女仆原生Brain控制，不接管导航或另建选敌循环。服务端每tick重检已有ATTACK_TARGET，仅清除失效目标和指向其的WALK_TARGET/LOOK_TARGET及路径；Mob目标同步清理。LivingAttackEvent为已飞出弹丸提供关系防误伤保护。
- 验证：305份Java树解析无错误，mixin JSON/测试注册、diff检查和原用户CHANGELOG哈希保护通过；javap只读确认两套女仆选敌入口、攻击名单API，以及当前Forge的EntityTracker/WalkTarget/受伤记录API。新增MaidHumanRelationTest（271项矩阵与实际接入检查）注册check，尚未编译或执行。未运行Gradle/javac，不声称Mixin加载及游戏行为已验收。
- 版本仍3.6.5，无构建、实例配置/JAR替换、游戏启动或推送。待用户要求构建后执行全套回归；游戏验收：无牌/低阶牌主动识别四阶野生人类、同阶/高阶和平及切牌即时停追、雇佣/临时控制后停止攻击、自卫与信标例外、弓弩/TaCZ弹丸防误伤、工作任务与黑名单保留、未安装女仆时可启动。


## 2026-10-08 主动贴身保护远射与友军弹丸穿透（源码整合，未构建）

- 需求：主动模式护卫可在武器射程内先发攻击远处敌人；互为友军的人类不挡枪弹、箭矢和三叉戟；正式雇佣/野生同阶人类不能因同类而获得此保护。基线main 232af9c，主控/root独立编辑，无新agent；上一任务女仆源码保留，用户明确暂不构建，本轮未授权构建/同步。
- 范围/接口：BodyguardProtection/BodyguardPolicy与SoldierOrder的公共选敌门控由主控顺序接入；GunRangePolicy.firingReach供GunnerGoal和护卫共享；HumanProjectileRelations.passesThrough(Projectile,Entity)为原生与TaCZ共用边界，仅人类发射者/人类目标参与，读取双向areCombatAllies，避免单向免伤被误认为互为友军。
- 远射：主动远程护卫按实际射程从士兵位置判断，按射程+12扩大主人中心的感知缓存，近距离威胁优先级保持；非主动模式不额外授予许可。移除BODYGUARD入口前遗留的通用48格目标截断，由护卫自身射程与12格士兵离主距离判定，FOLLOW/GUARD其它规则保持。远处射击沿用已有保持位置门控，不追离主人。
- 碰撞：Projectile.canHitEntity的SRG入口m_150171_在HEAD排除互为友军的人类，覆盖AbstractArrow及ThrownTrident派生碰撞，在点燃/伤害/反馈之前跳过。TaCZ EntityUtil绕过此原生谓词，故可选@Pseudo Redirect在findEntityOnPath和findEntitiesOnPath的Level.m_6249_候选查询中附加同一过滤，require=2保证两条都接入，在排序、穿透次数及命中副作用之前过滤。未重写或复制TaCZ射线算法，不改变地形碰撞。
- 验证：309份Java树解析无错误，mixin JSON、测试任务注册、共用射程及双向友军断言、git diff --check和原用户CHANGELOG片段哈希保护通过。javap只读确认当前Forge AbstractArrow调用Projectile.canHitEntity、ThrownTrident调用箭矢查找、TaCZ1.1.8两条EntityUtil候选查询与方法签名。新增HumanProjectileRelationsTest读取真实依赖字节码核查两条入口、双向友军接线与野生/雇佣不对称；扩展BodyguardAndVanillaModeTest射程/近威胁/许可边界；均已注册check但未编译执行。
- 版本仍3.6.5；未运行Gradle/javac、未改实例JAR/配置、未启动游戏或推送。待构建及游戏验收：主动枪/狙击在远处先发、弓弩各自射程边界、近威胁打断远射与12格不追离；同阵营野生枪线穿透/无击退受伤动画、火矢/药箭不对友军生效、穿透弹友军不消耗穿透次数、三叉戟穿过友军后命中敌人；野生与雇佣三阶战斗仍正常碰撞，PVP开关/控制权改变实时生效，女仆兼容及无TaCZ环境不受影响。


## 2026-10-08 女仆识别、主动护卫远射与友军弹丸过滤构建同步

- 用户明确授权“构建并同步”；主控/root在main执行，实际构建提交8ee0f0fc8302211bf75b85ba73afd68fba8918bc，包含女仆兼容8d4297c及远射/碰撞7984308。版本保持3.6.5。
- Java17及三个显式API参数执行build lootScreeningTest --offline，BUILD SUCCESSFUL in 1m 2s，46任务44执行2最新，无需源码修补。MaidHumanRelationTest271、BodyguardAndVanillaModeTest81、HumanProjectileRelationsTest14、LootScreeningTest13391及其余check任务通过；OptionalRuntimeTest无TaCZ/Curios/Maid链接396核心类通过。坏JSON/schema的ERROR为预期测试诊断。日志维护目录/验证日志/build-bodyguard-projectiles-20261008.log。
- 唯一目标E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21；备份前/替换前无非Gradle Java进程。旧JAR和14份配置共15文件已备份并逐一核对SHA256，备份维护目录/部署备份/20261008-225403-bodyguard-projectiles-Forge_47.4.21/deployment.json。
- 源/目标JAR SHA256均B53F20FC40504A1374AE2CFD99F171DF72D9906A0B4B4D027A3D3116C870B058，目标仅一份本模组JAR；manifest版本及三个新增可选/原生碰撞Mixin类和清单确认已打包。14配置哈希不变，原用户CHANGELOG片段保护哈希仍340C905E8C54142D3C0FC15961D82539DA50BD9FAC7C416055A43C4E7D039EC2。
- 未启动游戏、未推送、未同步其它实例。自动检查证明编译、关系/射程边界、真实TaCZ入口及依赖隔离，不替代启动时Mixin应用或游戏验收。待重启实例验证女仆按身份牌识別/切牌停追、主动护卫远射且近威胁优先、枪线/火矢/药箭/三叉戟穿过友军无命中副作用，以及野生与雇佣三阶仍可互相命中。


## 2026-10-08 野生人类对女仆统一中立（源码整合，未构建）

- 用户反馈高阶身份牌虽让女仆不攻击低阶野生人类，反向野生人类仍可能攻击女仆，要求所有野生人类默认不对女仆主动起仇恨。基线main 8cd89a6b5269b963dff7467273e0b124ded1ff14；主控/root顺序接入Human.java、HumanGunner与HumanTargeting，无新agent。没有本轮构建/同步授权。
- 边界：HumanTargeting.allowsWildHumanTarget先检查有效控制者，再通过ModList门控可选TouhouMaidCompat.isMaid，不在核心接口签名/字段引入女仆类型。所有阶级、信标来源且未被控制的人类，对已/未驯服女仆均中立，不依赖身份牌。保留真实近期受击的自卫，复用HumanRelations.isRecentHumanAttacker；队友告警与持久仇恨本身不能绕过。
- 接入：Human.canAttack阻止全部武器的攻击许可，setTarget拦截直接赋值，HumanTargeting主动分类入口排除中立女仆，LivingChangeTargetEvent覆盖外部赋目标；服务端tick清理既存无故女仆目标并停止旧导航。已雇佣/临时控制人类及女仆反向身份牌选敌逻辑保持。
- 验证：309份Java树解析无错误，核心可选依赖门控、真实受击入口、git diff --check及原用户CHANGELOG片段哈希保护通过。扩展MaidHumanRelationTest至281项预期检查，含中立/自卫/控制权分类和五个实际入口；已注册check，尚未编译执行。静态核查不代表编译或游戏行为验收。
- 版本仍3.6.5，未运行Gradle/javac、未改测试实例JAR/配置、未启动游戏或推送。待游戏验收：高阶/同阶/无牌主人的女仆附近野生各阶及信标人类不主动选敌，已有锁定在新版清理，女仆实际开火后野生人类正常自卫；雇佣/临时控制与其它生物目标保持，缺少女仆前置时启动正常。


## 2026-10-08 弓弩地形战斗、安全落差与雇佣弹丸玩家保护（源码整合，未构建）

- 需求：弓弩走位考虑峡谷、高低落差及流动浅岩浆，视线受阻不长期发呆，无法风筝时沿安全路径靠近/切近战绕障；全人类按当前生命评估自主下降。补充雇主永受保护，其他玩家分别按PVP关闭/开启过滤。基线main f8334d105b846e6eefc7f3a3c6418ac6b297285d；主控/root独立编辑主工作树。fall_safety_audit 使用GPT6.1 Sol medium只读审查，无编辑工作树；未创建其它agent，公共Human、导航、弓弩、托管及构建注册由主控接入。
- 审查依据：原combat STRAFE直接写输入绕过原版walkability；原生Mob最大下降随高生命/目标扩大。javap只读确认当前Forge JumpControl.jump、摔伤评估、PathNavigation和碰撞形状API。HumanFallPolicy预算=min(当前生命20%,当前生命-4,6)，几何最大12格；Human.expectedRawFallDamage只读原始计算，未调用causeFallDamage或更改真实摔伤。
- 公共接口：HumanTerrainSafety.safeTransition/safePath/safeStep用于节点、安装路径、退路和射界候选，HumanSafeJumpControl接管自主跳跃；Human.travel做最后输入/非受击惯性检查。地形变更/生命降低后下一路径节点重查。危险检查读取FluidTags.LAVA并扫四分之一格步长及五个足面采样；碰撞形状支持半砖/台阶，水、梯子和既有岩浆逃生入口保留，不能用安全门获得击退免疫。只读复审指出零输入仍有惯性缺口，已补入deltaMovement检查。
- RangedTerrainCombat共用弓弩安全侧移、搜索失败计数、射界下限放宽与安全靠近；共享导航预算等待不算失败。两次已完成失败且至少40tick遮挡或20次侧移失败时，在有备用武器且不违反驻守/护卫/退避条件下调用既有RangedWeaponCustody.beginCloseMelee，不新建攻击控制器。托管最少80tick，射界与活动空间持续20tick再恢复远程，最长400tick允许重试；无备用近战武器不凭空生成武器。同tick hand交换后tickShot禁射，使用原攻击动画及CloseDefenseHold。
- HumanProjectileRelations共用玩家边界：有效雇主始终跳过，其他玩家在allow_hired_pvp_damage关闭时跳过，开启可误伤；野生发射者不套用此规则。原生/TaCZ碰撞Mixin复用新条件，在点燃/药箭/击退/反馈前过滤。TaCZ ProjectileExplosion实际先发Forge Detonate再遍历命中列表，新增该事件过滤弹丸来源爆炸实体，防止单独范围伤害和爆炸击退；LivingAttackEvent在伤害反馈/报复记录前兜底，方块爆炸和环境火保持原规则。
- 验证：315份Java树解析无错误，git diff --check及原用户CHANGELOG片段SHA256保护通过；只读复审未发现台阶/梯子、水面跟随和托管的明确新增阻塞。新增RangedTerrainSafetyTest预期432项、扩展HumanProjectileRelationsTest预期23项，注册check，尚未编译或执行。静态解析及API核对不代表编译、Mixin加载或游戏行为验收。
- 版本保持3.6.5，无本轮构建授权，未运行Gradle/javac、未改实例配置/JAR、未启动游戏或推送。待构建及游戏验收：弓弩崖边绕行、流动浅岩浆、窄廊绕障/近战切回、半砖与梯子、低血量下降/高血量上限、被击退仍正常、水面跟随与脱困；雇主及其他玩家分别在PVP两种状态下测试枪线、火矢/药箭、三叉戟及爆炸弹，且原人类互为友军与野生/雇佣敌对边界保持。

- 源码交付提交：8bb8bab；主控已在main整合，原用户CHANGELOG修改仍独立留在工作区。


## 2026-10-08 地形近战追击入口复核（未构建）

- 基线main 4b4ae2f，主控/root独立续审。发现RangedHybridMeleeGoal会在手持近战时寻找远离目标的路径，原本用于弓弩短暂反击，不能承担本轮要求的狭窄地形绕障追击。此前只有托管保持期的检查不足以证明追击已实现。
- 新增RangedTerrainCombat.isTerrainMelee只读入口，地形托管排除RangedHybridMeleeGoal，并启用Human已有普通MeleeAttackGoal：复用原目标许可、共享导航预算、安全路径、实际位移停滞恢复与攻击/BetterCombat入口。短暂近身反击不改变；普通近战stop本来就保留合法目标，因此切回弓弩不丢目标。
- 315份Java语法解析、git diff --check通过，扩展RangedTerrainSafetyTest预期434项，新增双入口接入断言；尚未编译执行。已向用户请求构建/回归或同步的选择，因AGENTS明确限制未授权编译，在回复前继续仅静态核查。未改实例、未推送或启动游戏，目标仍需构建及地形游戏验收。


## 2026-10-08 移动轨迹一致性复核（未构建）

- 基线main febd0a2，主控/root只对仍有证据缺口的轨迹入口续审。javap确认当前地面导航没有自定义长距离canMoveDirectly捷径，但仍有单节点转弯推进；下一节点实时复查和travel足面前瞻必须同时保留。
- HumanTerrainSafety.guardTravel此前在有输入时只检查新输入方向。弓弩换向不等于速度立即转向，旧水平速度仍可能朝危险边缘滑动，现改为新输入与非受击旧速度轨迹都必须安全；最大前瞻从2格扩至4格以覆盖更快惯性，仍保留受击击退例外、客户端/下落/梯子/岩浆脱困边界。
- 此次补充不以纯语法检查证明运行安全；构建选择仍待用户回复，未运行编译或改实例。全目标仍需自动回归及峡谷/窄廊/浅流岩浆等游戏验收。


## 2026-10-09 移动目标追击连续性（源码整合，未构建）

- 用户接受上一目标以源码交付结束、暂不构建；新需求为减少追击旧位置后站等下一次刷新的停顿，同时限制性能开销。基线main 37613c0，主控/root独立编辑主工作树，无新agent/工作树；HumanNavigation、五类武器追击分支、构建注册由主控顺序接入，用户CHANGELOG保留。
- 当前源码证据：近战随机4–10tick刷新另有远距离延时，失败再加15tick，旧终点完成不能绕过冷却；位置快照在寻路结果前更新。弓/枪固定12tick、弩按随机延时、三叉戟按冷却追旧点。原生createPath可能复用旧目标块的有效路径；moveTo(Entity)失败可能丢旧路。不是每tick都应重算A-star。
- 新公共接口：PursuitRefreshPolicy.due只作O(1)位置/时间/节点判断；PursuitNavigation.update共享WeakHashMap状态（仅UUID/Vec3/时间，不强引用Human/目标/世界）和BasicNavigationBudget FIFO。最少4tick间隔，目标移出1.5格提前刷新，末两节点且移出0.5格预刷新，小位移最长20tick；静止有效路不周期重算，同位置失败20tick重试，明显位移可提前解锁。预算等待不记录失败、不消耗尝试间隔。
- HumanNavigation新增createFreshPath(Entity,int)，临时清除路径缓存后finally恢复旧路，生成成功才安装；无/不可用新路径不安装null，并恢复仍有效的旧路。替换路径走HumanTerrainSafety.safePath，允许有进展的安全局部路径，拒绝仅留在脚边的不可达路径。五类武器靠近分支接入；驻守/贴身保护、近战间距、合法目标、逃跑和水面直接靠近门控保留；近战30tick绕障恢复路线仍尊重有效所有权直到完成，不将射界换位当成普通追击。
- 新增PursuitNavigationTest预期20项：移动/静止/失败200tick序列与阈值、五类实际接入、共享预算/地形/新鲜路径入口，注册check，尚未编译执行。318份Java语法解析、diff检查及原用户CHANGELOG片段保护哈希通过。未运行Gradle/javac，未改实例/JAR、未启动游戏或推送。
- 待构建及游戏验收：持续跑动和急转弯目标、移动目标跨块/同块、追到旧终点不长时等待、障碍绕行与局部不可达、多人多单位下共享预算排队、水面跨岸、近战/弓弩/枪/三叉戟切换、驻守/贴身保护不越界；自动策略检查不能证明服务器帧时间或实际追击已验收。

- 本任务源码交付提交：3e48c190bf0c0bd51ae0b0b9bfde9e167585c53b；已本地整合到main，未构建/同步/推送，原用户CHANGELOG修改仍单独保留。

## 2026-10-09 可选联动接口规范化（进行中，未构建）

- 基线：97b4126b48d24b247c1aea64bdf12e9dd1036785。用户要求规范高风险兼容并暂关旧兼容；保留用户CHANGELOG，禁止构建/同步/推送。
- 主控/root：主工作树main，负责旧兼容停用、共享Mixin配置、README、集成审查及交付记录。
- tacz_boundary：worktrees/tacz-compat-boundaries，refactor/tacz-compat-boundaries，仅TaCZ换弹/射击边界及相关测试；保留弹药交易与友军规则，不修改共享JSON/构建/日志。
- client_boundary：worktrees/client-compat-boundaries，refactor/client-compat-boundaries，仅客户端TaCZ/BetterCombat/PlayerAnimator反射适配及诊断；不修改共享JSON/构建/日志。
- 接口：禁止用静默跳过必要Mixin来实现兼容；公开API优先，必要内部接口集中管理，保留可选依赖隔离。主控审核双方交付提交和静态验证后顺序整合。

### 接口规范化交付与整合结果

- 主控提交754f080：暂停Traveler's Backpack出生装备及渲染、Dungeons Mobs信标替换、Villager Names触发命名、Quark/mctb及Waystones结构覆盖；Mixin清单移除Polymorph/Pillagers Gun。仅farmers覆盖可自动选中，保留原结构资源但不启用；旧存档物品/名字/实体不删除。
- tacz_boundary使用GPT6.1 Sol medium，交付38710e884563c79817b2d1baf20e4cff09763171，主控cherry-pick d053410。TaczAmmoMixinContract集中方法及调用描述符，reload正文按无名签名与调用点识别，支持重命名/编号变化/内联，require=1/allow=1维持扣费唯一性；交易算法、线程、通知顺序和弹丸保护不改。
- client_boundary使用GPT6.1 Sol medium，交付acb2be010c4e3f3b85a5d2e843ff6c2e98a737a0，主控cherry-pick 072689c。ClientAnimationCompatibility集中客户端反射，按功能与操作诊断，缓存失败解析；保留TaCZ原生回退、现有攻击/护甲动画。javap核对35个可选类型，两个TaCZ非公开契约无等价公开NPC接口，仍明确保留，不宣称完全脱离内部API。
- 主控MaidTargetRules隔离上游排除配置，使用IAttackTask命名排除常量，异常时拒绝攻击人类并明确诊断；OptionalMixinPlugin只对女仆爆炸/斯巴达随机数两个非核心补丁检查精确签名、实例/静态及可注入正文，缺失警告跳过保留上游行为，必需弹药/友军钩子不允许被静默跳过。当前女仆1.5.3/斯巴达3.2.1字节码签名核对通过。
- 静态验证：323份Java源码tree-sitter解析无错误，JSON/启用与停用入口/必要钩子/任务接线检查和git diff --check通过。Mixin0.8.5 javap确认无名通配签名解析及0..MAX选择数量，缺少正文签名不阻止reload内联回退；TaCZ当前三个换弹调用及射击/tick边界各一次。新增TaczAmmoMixinContractTest、扩展MaidHumanRelationTest及StructureCompatTest，已接入check但未编译执行。
- 用户CHANGELOG完整文件SHA256 b05c6d8643feb7669502a7e1de0dd99d5d5d5a720f32f8f8263c85e0bf97dcbe保持。本轮版本仍3.6.5；没有Gradle/javac/构建/同步/游戏启动或push。README及docs/COMPATIBILITY.md已说明暂停范围、剩余内部契约和回退行为。
- 后续构建与游戏验收：有无可选前置启动、TaCZ各类换弹及费用/通知/取消、PlayerAnimator有无两种动画和护甲及音轨、女仆身份牌/显式排除/爆炸、友军/PVP弹丸过滤、旧结构覆盖不启用且农夫乐事保持；静态检查不证明Mixin实际应用或未来各版本语义兼容。

## 2026-10-09 兼容接口规范化与追击源码构建同步

- 用户明确授权“构建并同步”，唯一目标E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21。版本保持3.6.5；构建基线0ff90b02aaffad4230794f513e1487bad3197ff3。
- 首次构建遇PursuitNavigationTest错误要求TridentHybridGoal.tick直接调用追击，实际tickMelee/tickRanged均已接入。修正测试为检查tick到两个分支的调用及各自追击入口；提交ffab811d7aac0c406d6b9517f9f3989f68fa2ad7，仅修改测试，不改游戏逻辑。
- Java17、三个显式API参数执行build lootScreeningTest --offline，重试BUILD SUCCESSFUL in 41s，49任务45执行4最新。TaCZ弹药Mixin契约、女仆285、追击23、地形434、弹丸23、结构679、可选依赖隔离409核心类、掉落13391及全部check通过；无效配置/结构ERROR为预期回退测试输出。日志：验证日志/build-compat-boundaries-20261009-retry.log；首次失败另存build-compat-boundaries-20261009.log。
- 最终JAR版本/ZIP完整性、新适配器类、插件及停用Mixin清单核查通过。备份前与替换前均无非Gradle Java进程；旧JAR与14份相关配置逐一备份核对，备份：部署备份/20261009-181122-compat-boundaries-Forge_47.4.21/deployment.json。
- 源/目标SHA256均19CC34D9E42BB048CF5F983D9E8C80594BA138A26F53980346D9C31FA13DE4B3，目标仅一份统一模组JAR（3831714字节），14份配置哈希保持。原用户CHANGELOG完整文件SHA256 b05c6d8643feb7669502a7e1de0dd99d5d5d5a720f32f8f8263c85e0bf97dcbe保持。
- 未启动游戏、未推送或同步其它实例。构建与字节码合约测试不等同启动时Mixin应用及游戏验收；待重启实例验证普通/逐发/脚本换弹扣费及通知/取消、动画/护甲/音轨、有无PlayerAnimator、女仆身份牌与爆炸、友军/PVP弹丸、水面/地形追击连续性及旧兼容停用。

## 2026-10-09 修复兼容检查插件的ModLauncher字节码读取错误（源码，未构建）

- 用户反馈humangunner Mixin启动失败；读取测试实例latest.log定位18:13:11首次FATAL为SpartanWeaponryItemRandomizerMixin PREPARE，根因OptionalMixinPlugin.shouldApplyMixin调用getClassNode(name,false)。这是上一轮主控新引入错误，非TaCZ扣费钩子/斯巴达API签名失配；日志中的其它可选渲染ClassNotFound警告不是本次首个致命原因。
- 对当前实例实际Mixin0.8.5 javap：MixinLaunchPluginLegacy.getClassNode(name,false)直接抛IllegalArgumentException，单参getClassNode(name)委托true。改为readTarget(provider,name)使用单参服务入口，仍读取ClassNode而不定义/初始化目标Java类，不捕获并隐藏真实探测错误；两项可选补丁与必需弹药/友军门控保持。
- 新增ModLauncher约束provider夹具，拒绝false并验证真实插件边界使用受支持入口，防止只检查方法签名而漏掉服务模式。仅源码/静态验证，未重新编译或执行该测试；已有构建成功不能证明真实启动时服务模式可用。
- 本轮用户要求检查报错，未重新要求构建/同步，遵守只修改源码；测试实例仍为先前JAR，不能视为已修复部署。待构建同步后由用户重启验证Mixin准备与后续加载。保留用户CHANGELOG，未启动游戏或push。

## 2026-10-09 单一模组身份与命名空间（进行中）

- 用户确认仅统一本模组，显示名称Hostile Humans Unified，命名空间hostile_humans_unified；完全移除旧ID迁移兼容，仅面向新存档。本轮未授权构建/同步。
- 基线76bf1840738b969ff8b201b3df163fffa3efc2dc；主控/root负责Java运行入口、注册/网络/持久化标识、测试、mods.toml、Mixin配置、构建/打包检查及共享文档，主工作树main；原用户CHANGELOG保留。
- namespace_resources：独立worktrees/unified-namespace-resources，分支refactor/unified-namespace-resources，GPT6.1 Sol medium，只负责src/main/resources内assets/data/datapacks（含NBT）移动及引用更新，不编辑META-INF、Mixin JSON、Java、build.gradle、文档。原公共外部create/curios等命名空间不改。
- Java包路径保持实现组织，游戏运行身份/注册ID统一；禁止对Java包、第三方mod ID或用户实例文件进行盲目替换。源资源同路径合并前检查碰撞，不覆写冲突；仅静态解析核验，交付本地提交后主控整合。

### 单一身份与命名空间源码交付

- 主控71baa0a合并Forge入口：仅HostileHumans保留@Mod，HumanGunner由统一入口构造一次，共享事件总线，统一依赖表。HumanGunner.MOD_ID引用HostileHumans.MOD_ID；原生物品/实体/音效注册也使用同一常量。删除MissingMappingsEvent和三阶旧注册ID映射，以及已停用的旧名字缓存读取。
- 73份Java源码/测试中的运行字符串按语法节点更新，保留Java包/ASM类路径和版权来源。统一注册/资源引用、网络通道、翻译键、持久化标记及SavedData名称；配置目录仍hostile_humans_unified。旧配置不读取的测试夹具保持原文件名，不能视为运行别名兼容。
- namespace_resources（GPT6.1 Sol medium）交付f5dfc08f1ac50f7cd37ac681701dda9d472ac1e3，主控cherry-pick 6e12032。500资源路径迁移，509源文件合并为507；两语言文件各186键、共372键，无重复键冲突。160/160 NBT共1804字符串更新，非字符串payload及外部命名空间保持；64贴图字节保持；无旧tier_three_human别名资源。
- Mixin清单更名hostile_humans_unified.mixins.json、hostile_humans_unified.world.mixins.json及hostile_humans_unified.refmap.json，构建manifest和测试引用一致；Java包名仅是内部代码组织，保留原路径。新增UnifiedNamespaceTest接入check，检查最终JAR单身份/唯一初始化/无旧迁移/资源与refmap路径。已有打包审计改为核对维护后的源资源，不再要求与旧模组JAR字节相同。
- 静态验证通过：324份Java语法、全部资源JSON/mcmeta、精确UI翻译键、Mixin类与refmap/manifest接线、Python工具语法、metadata单身份/依赖、主源码无旧ID字面量；namespace_resources_check.py按76bf184基线复跑通过283 JSON、160 NBT、372翻译键、64PNG及全部507资源保真；额外99本地模型/纹理/结构/pool引用存在；git diff --check通过。
- 原用户CHANGELOG SHA256 b05c6d8643feb7669502a7e1de0dd99d5d5d5a720f32f8f8263c85e0bf97dcbe保持。未执行Gradle/javac或新测试，不构建/同步/启动游戏/push，版本暂保持3.6.5。测试实例仍为上次部署JAR；此前ModLauncher读取修复76bf184已包含源码，但尚未部署。
- 新存档限定为用户明确选择，无旧ID迁移/别名；后续验收需构建后验证单模组列表、无重复注册、物品/刷怪蛋/身份牌/弹药/配方/贴图、结构生成/战利品/自然生成、菜单与网络、持久化和可选联动。需更新外部脚本/数据包引用，当前没有改用户实例文件。

## 2026-10-09 3.7.0 测试构建与部署完成

- 用户授权版本3.7.0并构建同步测试实例。源码提交197ed5b1b0fde788dd3ef74f9dca25373955f224；CHANGELOG将此前未发布内容归入3.7.0（测试中），明确MODID与命名空间统一为hostile_humans_unified、删除旧ID迁移并仅面向新存档，可能不兼容旧版本存档。保留已有更新内容；修改前日志备份backups/changelog-before-3.7.0-20261009/CHANGELOG.md。
- Java17及三个显式只读API参数运行build lootScreeningTest --offline：BUILD SUCCESSFUL in 54s，50任务48执行2最新。单身份/资源871、女仆及ModLauncher读取288、配置分阶286/统一123、追击23、地形434、结构679、掉落13391检查及全部check通过；TaCZ弹药Mixin契约测试通过。日志：验证日志/build-3.7.0-20261009.log。错误配置诊断属于预期测试，不是构建失败。
- 备份前及替换前检查无非Gradle Java进程。备份旧3.6.5 JAR和14份相关配置，逐文件SHA256验证；部署备份/20261009-183134-3.7.0-Forge_47.4.21/deployment.json记录完整清单。
- 唯一部署目标E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21\mods\hostile-humans-unified-1.20.1-3.7.0.jar；3802307字节，源/目标SHA256均46DA766D3FDB2DF83918B5100D30DD74F1BFE630B8A5E0DD3CFC010CA80C332F。新JAR核对后移除旧3.6.5，目标仅一份统一模组JAR；14份原配置哈希不变，没有修改存档。
- 未启动游戏或推送GitHub；3.7.0保持测试中，尚非正式发布。待用户以新存档验收真实Mixin准备/启动、单一模组列表、资源/配方/结构/自然生成、持久化、TaCZ换弹/动画/友军过滤、女仆与斯巴达联动及追击导航；构建成功不代表这些游戏行为已验收。


## 2026-10-09 统一出生药水抽取与默认配额（源码完成，未构建）

- 主控/root独立实施，主工作树main，基线f6c2ff9；范围SpawnPotionConfig、RecoverySupplies、默认配置、两项既有配置回归及说明/日志，无新Agent或外部实例修改。
- 删除迅捷bonus字段解析、独立概率及预留名额逻辑，全部战术候选统一由insertPool按weight逐瓶抽取，允许重复且无单种上限。四阶恢复和战术总数分别独立可配置，默认均为1–2/2–4/4–7/6–8。
- 恢复池饮用strong_healing/long_regeneration/strong_regeneration权重2/1/1；战术池long_slowness/long_weakness/long_strength/strong_poison/strong_strength/long_swiftness权重5/1/10/2/5/8，迟缓/虚弱/剧毒为喷溅，其余饮用。现有喷溅选取原仅识别迟缓/虚弱，增加POISON以让新剧毒实际参与原投掷入口；冷却、射程及友军附近拒投规则保持。
- 已有配置的数量/池继续权威，不用新默认覆写；旧bonus不再读取、不自动迁移或删除用户字段，需手动把迅捷纳入tactical.pool。各阶potions.recovery/tactical.count_min/count_max已开放0–30瓶配置，修改重启后只对新生成且未领取补给的实体生效。
- 静态JSON核查四阶范围/全部权重/单一迅捷入口通过，对比HEAD确认非potions默认完全不变；两项测试的文本块JSON解析及git diff --check通过。更新默认池/瓶形、零配额、磁盘自定义战术9瓶迅捷池回归，尚未编译执行。用户本轮未要求构建，未运行Gradle/javac、未同步、未启动游戏或push。待后续授权构建及游戏验收新生各阶瓶数/重复迅捷/再生II和剧毒投掷。


## 2026-10-09 伤害配置合并、更名与测试实例配置清理（源码完成，未构建）

- 主控/root，main主工作树独立实施，基线ae5837079d8f543ef201db991591383299323a33；范围HumanGunner伤害入口、UnifiedConfig、女仆近战读取、TierConfigProjection废弃项清理、Waystones无效API及相关默认/配置回归/文档，无额外Agent。
- 弓弩旧全局1.2096×1.5合并为damage.arrow_damage_multiplier=1.8144，投掷三叉戟0.84×1.5合并为damage.thrown_trident_damage_multiplier=1.26；两个命中分支各只读取一次全局因子。新全局投射物范围0–100，容纳旧两个0–10有效值的乘积；各阶0–10独立倍率保留。近战全局改melee_damage_multiplier，近战对驯服女仆改human_melee_vs_tamed_maid_multiplier；分阶bow/trident/incoming分别改arrow_damage_multiplier/thrown_trident_damage_multiplier/damage_taken_multiplier，内存record访问器保留实现组织名称。
- 近刺、跳劈、目标承伤、枪械与女仆条件倍率用途不同，继续保留；非重复倍率不机械删除。no_waystones虽有读取与参数传递，但选择器已经固定不使用该参数，移除默认/说明、读取API、调用实参及无意义测试维度，保持农夫乐事覆盖。
- arrange在正常重排时删除明确废弃的旧伤害字段/相邻说明、失效的已知公式说明、potions.bonus及no_waystones，不为旧名建立数值读取别名或迁移档；未知扩展保留。更新默认、双语说明和CONFIGURATION.md。新增实际五文件回归，覆盖新值优先、72倍范围、清理持久化、未知扩展和只有五文件；更新默认/显式零/分阶独立/上限与结构选择测试，尚未编译运行。
- 用户回复已退出游戏，进程复核无非Gradle Java。单次备份14份旧配置至配置备份/20261009-193247-damage-potion-cleanup，update.json记录5份当前JSON的前后SHA256及9份归档路径。转换当前伤害值/分阶名并保留乘积；将仍为旧默认的各阶药水数量和池更新为上一项新规则，无检测到自定义药水覆盖；其余有效值/未知字段保留。旧单文件JSON、TOML和历史bak归档后从实例删除，主配置目录当前五JSON；最终哈希及JSON复核通过。未修改JAR/存档或其它实例。
- 静态核查伤害乘积、唯一实际入口、四阶字段与说明、无无效读取、默认非伤害设置（除no_waystones及相关说明）不变、测试文本块JSON、配置收据哈希及diff检查通过；未执行Gradle/javac或自动化回归，未启动游戏/push。测试实例JAR仍为上次3.7.0，不含本轮及药水更新，下一次游戏验收应先构建并同步新源码。待验收同武器伤害/单因子生效、各阶承伤、原版模式、女仆/枪械条件及新生成药水补给。


## 2026-10-09 当前配置有效性复核与历史说明清理（源码完成，未构建）

- 主控/root在main独立处理，基线39348425c14dea3a0bd42d2f4536cee5ca09cd8e。检查默认全局/四阶各组与配置快照及消费路径：属性、生命/移动、近战/弓弩/三叉戟/枪伤、分类散布、生成/在线日期、武器/护甲/盾/联动导入/装备成长、弹药、食物/药水、撤退/恢复/格挡/走位/跳跃、雇佣/消息/结构。没有通过仅搜索字段字面量就判定有效；保留仍参与当前分支的选项、内部使用的自动导入字段与记录以及未知扩展。
- 确认tacz.gunner_spread_degrees虽有解析和非Human回退读取，但唯一实际GunnerGoal创建入口为TaczIntegration.goal(Human)，人类实际采用RangedAccuracy的分阶分类散布；移除默认/解析/record组件/构造参数/无用clampSpread。非Human泛型备用分支保留原固定0.6，不新增配置。其它当前默认选项未发现可确认失效者；flee_hp、critical_health_ratio、临战恢复等均存在实际用途，未机械删除。
- 扩展五文件重排清理：识别失效的平铺属性、移速与散布字段、嵌套旧移速、全局旧流浪者额外判定及其说明；清除已停止功能名称、日期同步/装备数据包解释和历史标题。默认配置、CONFIGURATION.md和实例只描述当前使用方式，不再列出旧名称或迁移解释。既有更新日志/协作历史保留以便追溯。
- 无非Gradle Java进程，五份测试实例JSON逐文件备份到配置备份/20261009-194233-current-config-only，update.json含前后SHA256。更新当前说明、去除失效项，不改有效值或自定义池；实例仍只有五份JSON，没有新增历史文件。未修改JAR/存档、未启动游戏或同步其它实例。
- 静态验证：默认非说明值仅去除已确认无效的全局散布项，其余完全相等；默认/当前配置/说明无已确认废弃名称；删除散布访问器，record15组件与两个15参构造静态匹配；测试文本块JSON、五份备份与新配置哈希、diff检查通过。扩展TierConfigFilesTest覆盖失效字段及说明的磁盘清理、保留当前值/扩展且仅五文件，尚未编译运行。未获本轮构建授权，不运行Gradle/javac、未替换实例JAR或push；实际启动/配置清理/分类散布效果待下次构建及游戏验收。


## 2026-10-09 配置职责集中与逐项说明布局（源码完成，未构建）

- 主控 /root 在 main 主工作树独立实施，基线 137eb32efb1ba14d6a1195575ef857a6a9d594f4；无新 Agent 或工作树。用户确认雇佣人数上限和支付价格集中全局，人类输出只保留分阶并折算现有乘积。范围：配置投影/五文件读写/默认值、伤害消费入口、既有两项回归、配置指南/README/更新日志；其它会话工作树未修改。
- 全局 recruitment.max_hired_by_tier/payment_by_tier 唯一管理玩家雇佣；分阶不再声明 recruitment。无效全局文件禁止生存雇佣，坏阶级文件不再改变全局价格。全局保留 AI、弹药消耗、成长、原版模式和联动总开关；出生属性/装备/补给、AI细节、移动/散布、生成倍率与解锁日仍分阶。清理放错文件的已知选项，包括全局内的出生弹药/AI细节/成长参数、分阶内的全局开关和条件伤害，不建立旧名运行时别名。
- 人类近战/弓弩箭/投掷三叉戟/枪械输出分别只读取分阶因子，默认1.7/1.8144/1.26/0.5；容纳此前有效乘积的范围分别0–100/0–1000/0–1000/0–100。全局三叉戟近刺、跳劈、女仆、盾牌及其他兼容枪手修正用途不同，继续保留；本阶承伤仍0–10。没有改武器属性、弹速、攻击节奏或动画。
- 移除57种集中说明标题，将公式/格式移至对应字段，控制项按中文说明→英文说明→值排列，schema_version第一；区域开头仅留简短用途。补齐散布、生成、雇佣、枪械、装备名额及成长替换物品说明。已有文件只更新说明与布局/已知归属，不将缺失游戏值写入磁盘；显式空池、未完整配置的支付对象及未知扩展保留。增加字段相邻/无孤立说明/文件归属/独立伤害/全局价格/坏全局/重复加载稳定回归，尚未编译运行。
- 写入前进程检查无Java游戏进程；测试实例五JSON原件备份到 配置备份/20261009-200143-config-ownership-layout，update.json记录前后SHA-256及验证清单。一次性将当前各阶雇佣值移入全局，并把当前全局×各阶输出乘积写入各阶。静态逐值比较确认其余有效值、缺失项、空池和自定义内容未改变；没有在模组内添加迁移功能，实例目录仍仅五JSON。
- 静态检查通过：最终517处字段双语说明相邻、无孤立字段说明、五文件schema第一、备份/当前SHA-256、授权重定位之外全部有效值一致、默认其它游戏参数一致、两项测试文本块JSON和git diff --check。未执行Gradle/javac/Java回归、未启动游戏/推送。实例JAR未替换，仍为197ed5b构建的3.7.0，SHA-256 46DA766D3FDB2DF83918B5100D30DD74F1BFE630B8A5E0DD3CFC010CA80C332F；必须下次构建同步后再验收新配置的实际读取、伤害、雇佣及启动重排。


## 2026-10-09 配置整理后3.7.0构建与测试实例同步

- 用户授权构建并同步约定测试实例。主控main独立执行；源码基线e30591d4ca0df9a79573ba982930fe141cfaa129。首次构建在supplyConfigurationTest发现通用中文说明字段触发substring越界，提前识别通用说明后修复，源码提交3f25aeb62d3489fafe2f9fe960454ed010afe41b；未将失败产物投放。
- Java17、仓库gradlew.bat及三个显式只读API参数执行build lootScreeningTest --offline；修复后BUILD SUCCESSFUL in 48s，50任务46执行4最新。配置文件回归2039、统一配置124、补给配置23、命名空间871、掉落筛选13391检查及全部check通过。日志：验证日志/build-3.7.0-config-20261009-retry.log；首次失败另存build-3.7.0-config-20261009.log。无效JSON/版本测试的预期诊断不属于实际构建故障。
- 备份前与替换前确认无非Gradle Java进程。旧3.7.0 JAR及五份当前配置备份至部署备份/20261009-202300-3.7.0-config-Forge_47.4.21，deployment.json记录原件/备份哈希、源码提交、目标与新产物哈希。配置文件未改动，未同步其它实例或存档。
- 唯一目标E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21\mods\hostile-humans-unified-1.20.1-3.7.0.jar，3792534字节；源/目标SHA-256一致：B366B22BF136BFC9FFF98FFEC5AC4A50BCC2BD335D42745BEBBB629EE1E4D7A3。核对JAR内新伤害默认、去除全局重复枪伤以及单一模组身份。
- 未启动游戏或push，版本仍3.7.0测试中。待游戏验收配置读取/说明重排、雇佣人数与付款、各阶独立伤害以及新生单位食物/药水配额与自定义池；构建成功不等于游戏内行为已经验证。


## 2026-10-09 C2选敌编译崩溃的候选源码加固（未构建）

- 用户授权依据崩溃报告修补源码；主控 /root 在main主工作树独立处理，基线ecdbc4b014d8f4f39ff39a9e7382498d0c1cadf0，无新Agent。范围限NearestAttackableTargetGoalWithHumanLimiter、既有GunCombatPolicyTest、日志与本记录。
- 证据：测试实例hs_err_pid11432.log记录20:43:01 Java21.0.3，C2 CompilerThread0在编译该类m_8036_（canUse）时于jvm.dll发生EXCEPTION_ACCESS_VIOLATION；不是已证实的Java业务异常。10月3日/7日旧报告亦为C2原生崩溃，编译任务不同；该类自197ed5b部署以来未被本轮配置修改触及。根因不能由CompileTask单独确认。
- 将canUse的搜索、玩家警戒、25格同队警戒传播、15格玩家围攻数量检查分拆为命名方法；构造器只接收Human，移除入口冗余instanceof/条件表达式。人数查询捕获本次Player候选和观察者，不再在过滤回调中读取可变目标字段；0上限仍立即拒绝，正上限仍使用有上限的getEntities。保留原普通搜索间隔、枪手目标死亡后的快速重选、撤退门控和对话；canContinueToUse逐字比较未变。HotSpot仍可能内联新方法，因此仅为候选结构加固，不声称修复JVM或稳定性已证明。
- 更新既有ASM回归以跟随新搜索入口，并覆盖普通/快速搜索、玩家警戒、限额查询及超额拒绝的调用接入。静态核对控制流、范围、查询上限和持续目标逻辑，以及git diff --check通过；本轮未获构建指令，未运行Gradle/javac/回归，未替换JAR/修改实例参数、配置或存档，未启动游戏/push。待授权构建及保持其余条件一致的运行测试。
- 用户确认测试始终使用同一个旧存档，其中残留旧命名空间实体和方块；日志旧ID跳过警告可由该背景解释，不能据此认定新模组继续注册或引用旧ID。此次不新增旧ID兼容，也不删除或迁移存档内容。若需精确定位重复警告来源，应另查实体/方块/客户端预览等具体载入路径，当前不将其判为原生崩溃根因。


## 2026-10-10 统一成长系统（源码完成，未构建）

- 基线32a9a4e1e98e40157e14d0b53e9a8bdff356b5b2，开始时main干净；不修改历史维护资料、其它工作树或测试实例，不构建/编译/启动游戏/push。
- 主控/root负责main中的Human成长生命周期、健康与伤害入口、实际扣血Mixin、死亡确认、奖励保存及文档整合。配置负责人/root/growth_config使用GPT-6.1 Sol medium，独立worktrees/unified-growth-config、feature/unified-growth-config，允许UnifiedConfig/GrowthConfig/EquipmentGrowthPolicy/配置投影及默认/配置回归；提交301195109c18c896df54d20622d84845158df459，已cherry-pick为f3d9514。
- UI负责人/root/growth_ui使用GPT-6.1 Sol medium，独立worktrees/unified-growth-ui、feature/unified-growth-ui，允许HumanCommandNetwork/SoldierCommandScreen/GrowthSnapshot/语言文件/协议静态测试；提交9685ce50669713e9dcc4243ce81529eb773b185a，已cherry-pick为a129155。只读审查/root/growth_review同模型medium，不编辑源码。
- 公共接口：UnifiedConfig.growth()为服务器策略，装备共享其曲线与总开关；Tier.maxHealth()为完整基础生命；HumanGrowth.snapshot(Human)提供只读GrowthSnapshot；GrowthCombat.onTick调用HumanCommandNetwork.tickServer。协议7，token/UUID/entityId/维度/雇主/距离核验，10tick合并变化，客户端不读本地配置计算成长或决定显示。
- 成长0～5级默认50/60/70/80/90/100%，每级40成长值；经验出生90～110%，每12成长值增1个百分点，封顶125%。生命默认40/60/80/100，先阶段后经验，枪伤不变。全部成长规则和四阶日期/装备早期参数集中global.growth，不增加旧配置迁移或继承文件。
- 伤害采用LivingEntity.hurt及setHealth稳定SRG入口观察实际扣血并剔除嵌套重复，避免同次受伤内治疗/图腾抹去伤害；生物确认KILLED或玩家死亡重生后结算，第一击致死也先记贡献。每10实际生命伤害0.25，基础封顶4，严格超过300另加4，不要求尾刀、不识别Boss、不专门排除人偶，允许回血伤害累计。出生快照、贡献与未加载士兵奖励持久化；禁用失效未完成战斗但保留已赚奖励和培养记录，关闭时实际系数100%且UI隐藏。
- 审查修正：外部生命base明确所有权，避免成长值碰巧相等后接管；奖励规则保存目标首次参战快照，配置修改不追溯；reload记录禁用代次，避免同tick切换漏失效；命令NBT加载通过join.loadedFromDisk区分新生和旧实体；升级不免费回血，原版模式保持20基础生命。
- 最终边界修正：玩家死亡重生通过PlayerEvent.Clone确认，在END tick合并结算，避免立即重生漏记最终一击；普通DISCARDED不奖励。当前命名空间已有士兵在原版模式下保存的外部基础生命仍可恢复，成长升级/开关变化不额外回血，不接管其他模组独立修改的基础值。
- 静态验证：Python UI契约9项、服务器源码契约10项全部通过；资源JSON解析、成长双语字段、废弃Java接口引用和git diff --check通过。只读javap及现有SRG映射核对LivingEntity.hurt/setHealth和Forge PlayerEvent.Clone接口；没有执行编译或启动验证。新增Java成长算术/奖励边界/持久化回归已接入既有UnifiedConfigTest，尚未编译运行。
- 待授权构建与游戏验收：Mixin实际注入；真实生命扣除与回血/图腾/取消死亡、多方尾刀；卸载/保存/重启及奖励恰好领取一次；外部生命/原版模式/总开关；多人面板切换、服务器客户端不同配置；多士兵战斗性能。没有更新测试实例配置/JAR、启动游戏或push。版本保持3.7.0，配置/UI提交已整合，主控提交覆盖核心与说明。

## 2026-10-10 统一成长计划逐项复核（源码补充，未构建）

- 主控 /root 基于 fb5522a 在 main 复核，不派生编辑会话、不修改实例。前一轮属于有效进展：配置/UI/核心提交已整合并产生静态证据；本轮继续检查生命周期与配置切换。
- 发现原版模式重载仅刷新生命、近战基础属性仍停留在初始化值。抽出 TierAttributes.applyCombatBase，在 HumanGrowth 检测服务器配置实例变化时刷新该值，保留移动、护甲及外部 attribute modifiers；不改变成长总开关、枪伤和其他配置职责。
- 扩展服务器源码契约至11项并通过；UI契约仍9项通过。新增 docs/GROWTH_ACCEPTANCE.md，逐项列出当前接入证据、未运行验证和后续验收顺序。未执行构建/Java回归/同步/游戏测试，完整目标继续等待适当运行证据，不作完成验收声明。

## 2026-10-10 统一成长3.7.0构建及测试实例同步

- 用户明确授权构建并同步。主控main独立执行；运行时代码基线655c61c，补充回归断言提交f50c84c。第一次构建源码编译通过，旧ASM断言仍在TierAttributes.apply查attackBase；第二次构建成长算术/持久化/配置测试通过后，旧默认JSON最后章节必须为TaCZ断言失败。分别改为核验初始化/重载均接入共用攻击基础方法，以及唯一全局growth和分阶不重复定义。未放宽成长计算或伤害规则，失败产物不投放。
- Java17、仓库gradlew.bat及三个显式只读API路径执行build lootScreeningTest --offline；最终BUILD SUCCESSFUL in 45s，50任务46执行4最新。GrowthProgressionTest 23、GrowthPersistenceTest 5、GrowthConfigTest、UnifiedConfigTest 128、TierConfigFilesTest 2068、LootScreeningTest 13391及全部check通过。三次日志保存在验证日志/build-3.7.0-growth-20261010*.log。
- 备份与替换前检查无Java游戏进程。原3.7.0 JAR及五份配置备份至部署备份/20261010-011704-3.7.0-growth-Forge_47.4.21，deployment.json包含源码提交及前后哈希。配置把原equipment_growth参数集中global.growth.profiles，保留原开关/日期/早期装备设置；增加阶段、经验与奖励项；各阶用完整生命40/60/80/100替换生命上下限随机项。逐值比较确认其他有效配置、自定义池及显式空池未改变，没有新增继承/迁移文件。
- 唯一投放E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21\mods\hostile-humans-unified-1.20.1-3.7.0.jar，3829613字节；源/目标SHA-256一致496A34D8FFA8477FD5468003674D3787A4F2E5A7CE0B952F9B08615198B468CD。独立复核备份/现件哈希、五配置归属/默认生命、产物新Mixin类及重映射目标字符串通过。
- docs/GROWTH_ACCEPTANCE.md已更新自动化证据。未启动游戏/修改存档/同步其他实例/push；版本仍3.7.0测试中，用户自行进行成长/奖励/存档/UI/性能游戏验收，目标不因构建通过而标记全部验收完成。

## 2026-10-10 成长面板、出生基础生命与战后跟随兜底（源码完成，未构建）

- 主控/root在main独立处理，基线37f432fd40b2249ba7f0be016fd3e9432840e464，初始工作区干净；无编辑Agent/新工作树，范围SoldierCommandScreen、SoldierFollowNavigation、GrowthConfig/默认曲线及回归/说明。枪械经验精度仅审查与建议，不改变枪伤或散布；本轮没有构建授权，不构建/同步/改实例配置/启动游戏/push。
- 成长区从96缩到72像素，文本及分隔线重排；两个进度条厚度3像素，移除经验拖动手柄，操作区和点击坐标仍共享growthHeight，服务器快照/关闭隐藏逻辑保持。
- 跟随旧逻辑将同一路径节点推进当作救援进展，可能不断延后近距离卡住的传送。分离progressTick（路径重搜）与approachTick（有效靠近主人），只有比历史最近距离再缩短超过0.5格或进入跟随间距才重置救援期限；200tick无有效接近即调用原安全传送入口，不要求48格距离。战斗时FOLLOW分支仍清理监视状态，战后重新起算；传送仍核验同维度、无撤退/熔岩/骑乘，以及附近安全落点/碰撞/已加载区块/世界边界，不盲传地下地牢上方。
- 用户明确只要求随机浮动前基础生命更高，允许±10%经验出生浮动后的区间重叠。全时段核对发现第32/33天一阶和二阶都为48；二阶progress_nodes第二值0.40→0.45解决，仍10天起点/70天满成长，其他阶不变。GrowthConfig缺失/无效节点从各阶正式默认读取，避免硬编码等距回退破坏该修正。默认0～200天独立数值检查通过，其后各曲线满级固定；新增Java逐日阶级顺序回归，尚未编译执行。野生出生状态固定且曲线单调，因此过去出生的低阶基础值不会高于同日低阶新生值；不限制已雇佣练级或外部修改。
- Python UI源码契约10项通过，git diff检查通过；补充跟随有效接近/抖动/近距长时间堵塞Java回归，未执行。需下次授权构建后验收紧凑布局、真实战后堵塞及安全传送。实例现有显式二阶节点0.4未擅自改动；后续同步时核对该设置与玩家自定义值。
- 当前枪械附加角误差由分阶weapon_spread_degrees.firearms读取；正常GunnerGoal与撤退AdaptiveCombatGoal共用RangedAccuracy入口，对yaw/pitch分别高斯抽样并截断±2.5σ，随后调用TaCZ operator.shoot，成长经验暂不参与。建议经验90/100/110/125%对应附加散布1.10/1.00/0.90/0.75倍，逐百分点线性插值、越界截断、成长关闭1倍；拟统一全局growth区域配置锚点，保留各阶分类基础值，不修改TaCZ枪包自身弹丸散布/伤害，仅待用户确认方案后实现。

## 2026-10-10 原版模式与统一成长互斥（源码完成，未构建）

- 用户要求原版模式开启时成长模式禁用且不改变原版模式逻辑。主控/root在main独立处理，基线97b799c7752f8b2f9578881ddd3607fcbb14da09；范围GrowthConfig/UnifiedConfig、默认注释/配置说明、两项回归及本记录。没有新Agent/工作树，不构建/同步/改实例配置/启动游戏/push。
- GrowthConfig在构造服务器策略时采用configuredEnabled && !vanillaMode；不分别在各消费点拼接条件、不改写持久配置。装备策略与属性/奖励/UI共用这一有效值，出生曲线仍可确定潜在状态。现有reload通知与奖励epoch机制自然覆盖进入原版模式后的未完成贡献失效，已获待领取奖励和实体培养记录保留。
- GrowthHealth、VanillaModePolicy、TierAttributes、HumanGunner原版生命/近战分支均未改动；成长停用使其额外倍率归1，基础生命仍由原版规则固定20，原有远程倍率等不变，装备不追溯替换。默认注释补充互斥优先级，并纠正此前残留的按生命比例切换描述，文档跟随当前不额外治疗的实现。
- 扩展GrowthConfigTest覆盖原版模式×成长开关四组合、装备有效开关一致、潜在出生阶段、配置偏好不被改写及退出模式恢复，尚未编译运行。Python服务器源码契约12项通过；JSON解析与git diff检查通过。游戏内总开关切换/奖励保存/面板隐藏与原版模式回归待后续授权构建及用户验收。


## 枪械经验与分档培养

- 基线：1210364c59aca5e1ae96d1de23a6aaa45e6ced4e；负责人主控；工作树为源码主仓库，分支main，开始时干净；本任务单人顺序实施，无并行编辑。
- 范围：GrowthConfig/Progression/HumanGrowth、RangedAccuracy、TaczIntegration、TierConfigProjection、默认全局配置、成长回归和文档；共享接口及配置由主控统一接入。
- 接口：experienceCost(currentPercent)提供下一百分点成本，服务器快照同步该值；gunSpreadMultiplier/gunDamageMultiplier统一读取服务器保存的经验及有效成长开关，不使用阶段倍率或客户端配置。TaCZ原伤害入口只追加一次经验因子。100%为枪械基准，低于100%按既定散布方案增加误差而不减少枪伤；出生随机范围保持90–110%。
- 规则：低于100%每点3；其后五档3/5/8/12/16；枪械每5百分点减少基础附加误差5%，最低75%；100%到经验上限线性增伤至10%。全局开放参数，移除旧cost及说明，不更改实例配置。
- 验证：新增Java边界/余数/可配置参数/禁用规则回归，尚未编译执行；Python源码契约与JSON/差异检查结果见本条后续记录。构建、同步、游戏验收和push未执行。

- 本轮结果：37项Python源码契约检查通过、281个资源JSON解析通过、git diff --check通过；Java回归已补充但未编译执行。提交包含本任务明确文件；实例配置/JAR未改动。

## 构建同步：枪械经验与地形更新

- 用户明确要求构建并同步；源码6e0d7b6c4c90b6fa08c1181d8e1ee52433e9b486，版本保持3.7.0。Java17及三个显式只读API路径，仓库gradlew.bat执行build lootScreeningTest --offline，BUILD SUCCESSFUL in 1m 49s（52任务，50执行、2最新）。GrowthProgressionTest 61项、GrowthConfigTest、CombatTerrainPolicyTest 28项及全部check通过。坏配置回归的预期ERROR不代表构建失败。
- 再次检查无游戏或未知Java进程后，备份旧JAR和五份配置；更新唯一模组JAR及global.json中的新经验/枪械参数，删除旧cost及说明。已有自定义值保留，四阶配置逐字节保持；未修改存档、其它实例或启动游戏。
- 目标：E:\MC\game\.minecraft\versions\1.20.1-Forge_47.4.21；产物hostile-humans-unified-1.20.1-3.7.0.jar，3855678字节；源/目标SHA-256一致：4F53C860D6F95EEA60E39E7FC49CD9A1B285F4166C81B4826F9C978BDB227533。
- 备份：E:\MC\Create-Delight\HostileHumansUnified-维护\部署备份\20261010-131457-3.7.0-firearm-growth-Forge_47.4.21；deployment.json保存文件哈希与配置变更。构建日志：E:\MC\Create-Delight\HostileHumansUnified-维护\验证日志\build-3.7.0-firearm-growth-20261010-131237.log。游戏内散布/伤害、跨档培养/UI、地形绕路/悬崖策略仍待用户验收；未push。


## 战斗中受困的士兵命令救援

- 基线：a37234bd9a924197c182e2f3e07a79028b832d26；主控单人实施，源码主工作树、main，开始时干净，无并行编辑。
- 原因：SoldierOrderGoal仅在没有目标时启用，FOLLOW有目标时清理跟随计时；BODYGUARD归队也受战斗MOVE仲裁影响，GUARD/PATROL没有对应安全传送兜底。持续仇恨使原救援不能覆盖坑洞内的士兵。
- 范围：新SoldierStuckRecovery、HumanGunner服务器生命周期、SoldierOrder接口与巡逻终点保存、SafePositions共享落点校验、跟随Java回归及Python接入契约；共享接入和文档由主控负责。
- 接口：独立状态不依赖目标是否存在，不新增Goal、不接管正常路径。10tick错峰位置采样，偏离监测原点超过2格才重置停滞；约200tick无位移，距离命令点超出到达范围后，预算内两次间隔20tick不可达确认才尝试传送。可达路径不计失败，预算等待不计失败，不改变已有路线；正常站定攻击且能走回命令点不会触发此兜底。
- 目的地：FOLLOW/BODYGUARD使用同维度有效主人；GUARD使用命令锚点；PATROL使用保存的最后成功巡逻终点，无记录退回锚点。HOLD_POSITION不参与。巡逻终点仅由命令路径成功规划写入，不由追击路径写入；换命令/解除雇佣清除。安全落点最多12次候选，与当前点至少相距2格；复用身体碰撞、危险地形、世界边界和已加载区块校验，不盲目传送。
- 成功处理：清除目标/仇恨及追击、绕障、护卫、命令路线、短步输入和横移惯性，复位摔落距离；保持命令、攻击模式、装备、雇主、生命和培养不变。40tick暂停普通选敌（保留护卫苦力怕救主例外），200tick救援冷却。反复跳跃/恢复物品只延迟执行不清空计时；不在空中、恢复物品占手、手工背包、撤退、水中/上岸、梯子、熔岩及爆炸逃生时抢救援控制权。
- 验证：42项Python源码契约通过、git diff --check通过。Java回归新增四模式边界、10秒门槛及到达距离检查，未编译执行。本轮无构建、实例配置修改、同步、游戏启动或push。
- 待游戏内验收：图示无阶梯坑洞内持续仇恨，分别测试跟随/护卫回主人、驻守回锚点、巡逻回已保存终点；正常远程站定射击不误传送，撞墙跳跃不停重置，主人离线/跨维度/无安全落点时不盲传送；落点改变、切命令、多人多士兵共享预算、传送后重新交战。


## 成长配置归属与阅读布局整理

- 基线df4ab91，主控，源码主工作树/main；单人实施，共享配置接口、默认资源、格式化和文档由主控负责。
- 核查：stage_percent/stage_cost、出生经验范围与cap、below_base_cost/cost_by_band、战斗奖励、枪械经验增伤/散布、分阶伤害与分类基础散布均存在实际读取入口。补充band_width_percent，默认5，经验培养及枪械散布共用，可调1–100整数。
- 结构：共用growth保留全局；各阶growth_profile保存时间曲线和early_*品质细项。GrowthConfig只从tiers.rank.growth_profile读取分阶配置；原global.profiles不作为双重运行时入口。当前文件值在加载前安全搬移：预读校验目标、目标明确字段优先、缺项保留来源、所有目标写入成功后才清理来源；并发/坏文件不删来源，不生成继承文件。
- 布局：全局原版模式/成长等常用项前置，各阶基础属性/伤害/生成/补给前置，growth_profile和大装备池靠后；分区标题和说明→值组间空行改善扫描。保持标准JSON，不假设可以控制编辑器配色；写入统一格式以保持重复加载稳定，保留未知扩展、自定义空池和有效值。
- 范围：GrowthConfig/UnifiedConfig、TierConfigProjection/SplitConfigFiles、新GrowthProfileFiles、默认资源、Java与Python回归及配置说明。没有构建、实例配置写入、同步或push。新增Java文件搬移/优先级/坏文件/格式稳定和可配置档宽回归，等待明确构建后执行。

- 本轮结果：45项Python源码契约检查通过、281个资源JSON解析通过、git diff --check通过；默认有效值在结构搬移前后逐项比较一致（仅新增档宽默认5），不改变当前平衡。Java回归已更新并新增文件搬移与坏文件保留检查，未编译运行。
