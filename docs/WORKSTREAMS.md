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
