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

尚无具体功能需求，不创建开发会话、子任务或额外工作树。后续功能任务须选择届时确认的基线提交，不能自动沿用上表初始 HEAD。

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
