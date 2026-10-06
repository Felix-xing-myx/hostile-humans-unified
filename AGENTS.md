# Hostile Humans Unified 协作规则

本文件适用于本源码仓库及由其创建的独立 Git 工作树。用户当前指令优先；外部维护资料与旧会话只作查证依据，不能替代当前源码。

## 范围与授权

- 源码主仓库：`E:\MC\Create-Delight\HostileHumansUnified-维护\project`；维护资料、产物、备份在其上级目录。整合包总目录不是本项目 Git 仓库，不得为模组任务初始化整个整合包。
- 主控可以在用户提出的开发任务内自主拆分、分派、建立工作树、审核提交、解决冲突并本地整合。需求含义不明确或明显扩大范围时说明依据并询问用户。
- 发现真实逻辑漏洞、风险或证据冲突时，主动说明依据和替代方案，尊重用户最终范围决定。
- 未有具体开发需求时只接管、核查和完善协作记录，不派生功能任务。

## 接管与记录

- 先读当前适用的 `AGENTS.md`、README、CHANGELOG、构建配置及相关源码，重新核对分支、HEAD、未提交修改、暂存区和工作树。交接快照不是永久状态。
- 保留用户和其他会话的修改，禁止未经明确要求使用 `git reset --hard`、`git checkout --` 或等效清理命令。不要整文件覆盖解决合并冲突。
- 主控维护 `docs/WORKSTREAMS.md`：每项任务写明需求、状态、基线完整 SHA、负责人/会话、工作树、分支、文件范围、共享文件负责人、公共接口、交付提交、验证和未解决问题。
- 只暂存本任务的明确文件；禁止直接 `git add .` 或 `git add -A` 混入其他修改。提交前核对暂存差异。
- 源码仓库外历史资料及 `analysis-only` 目录只用于查证，不当作当前源码修改。

## 主控与开发者

- 一个功能任务原则上一个独立工作树和分支；多个编辑会话不能共用工作目录。主控从明确提交创建工作树，未提交修改不会自动带入，需在记录中说明。
- 适合并行时使用独立开发会话或子 Agent；分派前登记负责人、路径、基线和允许修改范围。向已有开发会话发送该任务及反馈在用户授权范围内。
- 开发者只编辑自己分配的工作树，不自行合并到 `main`、替换实例 JAR、发布版本或推送 GitHub。
- 开发者交付本地提交 SHA、文件清单、公共接口变化、实际执行的验证命令/结果及未解决问题。没有运行的验证明确写“未执行”。
- 主控审核行为和接口后逐项整合；每项整合都检查与现有 AI、武器切换、移动、动画和配置的关系。Git 无冲突不代表逻辑兼容。
- 版本号、更新日志、共用配置接口和发布记录由主控负责。README 不写每次发布都要手工更新的固定当前版本号。

## 共享文件与现有实现

- `Human.java`、`AdaptiveCombatGoal.java`、`UnifiedConfig.java`、`HumanRenderer.java`、`build.gradle` 等易重叠文件必须指定一个接入负责人或顺序实施。独立逻辑尽量放在各自模块，事先约定接口。
- 已有 `BetterCombatMeleeCombat`、`BetterCombatNpcAnimator`、`TaczNpcAnimator`、`PlayerAnimatorModelBridge`、`TaczNpcAnimatorMixin`。先读当前实现及调用链，不按旧计划重复建立攻击或动画控制系统。
- 近战现有入口包括 `MeleeAttackTiming`、`MeleeCombatRange` 与 `Human.doHurtTarget` 的 Better Combat 前摇/延迟出伤。改变冷却、命中或移动时检查这些入口及退避/换武器取消规则。
- 客户端动画经 `HumanPlayerModel` 和 `HumanRenderer` 接入；可选依赖、客户端/服务端隔离、护甲和手持物动画均需核查。保留未安装可选模组时的回退行为。

## 构建环境与只读依赖

- 当前基线为 Minecraft 1.20.1、Forge 47.4.16、Java 17；每次以 `gradle.properties`、`build.gradle` 和实际环境重新核对。Java 路径为 `D:\Java\17`，使用仓库的 `gradlew.bat`。
- 没有用户的“构建”要求，不运行 Gradle 构建或会触发编译的验证任务。已有授权后按修改选择验证；自动检查不能替代游戏内验收。
- `build.gradle` 的三个 compileOnly API 默认路径依赖工作树位置。独立工作树显式传入完整参数；公共 API JAR 只读，不复制整套实例，不提交 API JAR、构建缓存、日志、存档或凭据。
- PowerShell 构建参考（仅在用户要求构建后执行；额外测试按任务选择）：

```powershell
$env:JAVA_HOME = 'D:\Java\17'
& .\gradlew.bat build lootScreeningTest --offline `
  '-PtaczApiJar=E:\MC\Create-Delight\versions\Create-Delight-Remake\mods\tacz-1.20.1-1.1.8-hotfix.jar' `
  '-PcuriosApiJar=E:\MC\Create-Delight\versions\Create-Delight-Remake\mods\curios-forge-5.14.1+1.20.1.jar' `
  '-PmaidApiJar=E:\MC\Create-Delight\最初二创内容\mods\[车万女仆]touhoulittlemaid-1.5.3-modified-all.jar'
```

## 同步、发布与验收

- “构建”授权主控构建；同步仅针对用户已约定的目标。“构建并同步”由主控直接完成已指定目标的构建和同步；缺少目标时先建立明确发布清单，不猜测“三处”或同步所有历史实例。
- 只有用户明确要求推送时才执行 GitHub push。不自行启动游戏，游戏内验收由用户反馈。
- 正式发布顺序：核对版本/日志 → 构建及适当验证 → 检查目标实例是否运行 → 备份旧文件 → 替换本模组 JAR → 核对源/目标 SHA-256 → 登记并报告结果。目标运行时停止替换，需用户关闭或明确授权停止相关进程。
- 历史部署位置仅作参考，文件名版本不代表 JAR 内容身份；每次按用户指定目标核查实际文件与哈希，不默认四处同步。
- 报告最终行为变化、已整合任务、实际验证及具体游戏内测试事项。分别说明静态检查、自动化验证、游戏内验收和实例同步状态。
