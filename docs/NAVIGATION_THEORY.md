# 人类导航理论源码地图

玩家/维护者阅读入口：外部知识库 `E:\个人知识库\Hostile Humans Unified\05-开发参考\导航理论\00-人类导航理论总览.md`。共8篇，覆盖总览、原版、选点、安全、预算、特殊移动、诊断与修改边界。当前机制正文不放构建/部署日志。

## 责任与证据

| 理论结论 | 当前源码依据 |
|---|---|
| 复用原版PathFinder，扩展地面节点与安装/执行否决 | `HumanNavigation.createPathFinder/HumanNodeEvaluator/moveTo/tick` |
| 地面倍率2，水域导航另一个对象，按水状态切换 | `Human`构造、`updateSwimming/selectShoreNavigation/startCombatShoreDetour`；`ShoreSeekingPolicy.shouldUseWaterNavigator` |
| 路线刷新最少4tick，移动1.5格，失败20tick重试 | `PursuitRefreshPolicy.due`、`PursuitNavigation.update` |
| 地形半径8/16/24，两层水平距离、8方向、多站立层、每批2并尽早采用 | `CombatTerrainPolicy`、`CombatTerrainNavigation.candidates/standingPositions/update` |
| 地形部分推进与完整路径条件不同 | `CombatTerrainPolicy.partialUseful`、`CombatTerrainNavigation.usefulPath` |
| 射位有限随机候选、4槽批次，等待遍历结束，距离/位移/节点/高度代价 | `RangedFiringPosition.findVisiblePath`、`IncrementalBestSearch` |
| 增强撤退/战术不同评分与不同池 | `AdaptiveCombatGoal.chooseRetreatPath/chooseTacticalPath`、`retreatSearchRadius` |
| 普通32，Optional64/32/8与FIFO，非全局A*拦截 | `BasicNavigationBudget`、`OptionalPathBudget`、`FairWorkBudget`；`HumanNavigation.createPath`未统一扣额 |
| 闲置找岸独立1授权、64探测、2目的地可能各试两导航器 | `ShoreSeekingPolicy`、`LeaveWaterWhenIdleGoal`、`Human.findNearestShorePath` |
| 当前生命预算、12格封顶、原生原始落伤估计 | `HumanFallPolicy`、`Human.getMaxFallDistance/expectedRawFallDamage` |
| 脚部采样与身体碰撞责任不能混为一谈 | `HumanTerrainSafety.assessStep/footprintReason/shoreExitReason/guardTravel`、`MovementContinuity.tryStep` |
| 撤退192节点、段净空、前8接近6、长度与净增距离约束 | `RetreatPathSafety`、`RetreatPathSafetyPolicy` |
| 命令整路检查，保留模式例外 | `SoldierOrder.allowsCombatPath/allowsCombatPosition/allowsTarget` |
| 门交互不等于解机关，近门±2开关扫描 | `DoorInteractHumanGoal`、`OpenDoorsGoal/OpenFenceGoal/OpenTrapdoorGoal`、`PassageLease`、`InteractionSafetyPolicy` |
| 梯子上下节点与垂直助力分别实现 | `HumanUtil.createLadderNodeFor`、`LadderClimbGoal` |
| 三类恢复、定向失败边、延长退路、雇佣安全救援 | `MovementContinuity/MovementContinuityProgress`、`HumanNavigation.avoidTransition`、`RetreatRouteContinuity`、`SoldierFollowNavigation/SoldierStuckRecovery` |
| 测试隔离与有限观测 | `NavigationDebugService.TestGoal/snapshot`、`NavigationDebugFrame`、`NavigationDebugClient/NavigationLegendScreen` |
| 默认基础范围40/40/40/48，增强半径18/尝试10、跟随倍率1.35 | `defaults/hostile_humans_unified.json`、`TierConfigProjection`、`TierAttributes`、`UnifiedConfig` |
| 实时范围不只配置基础值 | `HumanRelations.tick`枪械/信标临时修饰，信标+88 |

Java源码根：`src/main/java/`。公共辅助默认位于`club/someoneice/humangunner/`，纯策略位于`dev/felix/hostilehumans/core/`，实体/节点/交互位于`com/craftix/hostile_humans/`。

## 原版实现依据

只读核对本机 Forge 1.20.1-47.4.16 official mapped JAR，未运行Gradle或游戏。核查PathNavigation、GroundPathNavigation、PathFinder、WalkNodeEvaluator、MoveControl、GoalSelector、基础Mob落差和LivingEntity落伤入口。维护目录 `验证日志/navigation-theory` 保存此次字节码资料，避免把网上其他MC版本的实现混入当前结论。

- PathNavigation构造：基础节点阈值`floor(FOLLOW_RANGE*16)`，构造后不随实时属性自动重建；createPath读取实时FOLLOW_RANGE作为距离参数。
- PathFinder：open set、前驱、节点malus、邻居启发距离权重1.5、有界展开、累积路程限制、目标曼哈顿容差与部分路径回退。
- GroundPathNavigation：目标空气/实心位置高度调整、地面节点能力。
- WalkNodeEvaluator：原生相邻/对角/台阶/下降、身体尺寸与碰撞分类、getMaxFallDistance入口。
- PathNavigation执行/缓存：同目标活动路径复用、moveTo安装、下一节点与MoveControl输入、卡住/下一节点超时。
- Mob.getMaxFallDistance：无目标3，有目标粗略生命/难度启发；Human覆盖为自己的当前生命预算。
- LivingEntity.calculateFallDamage：落伤免疫标签、跳跃效果、ceil基础式；规划没有调用hurt模拟完整伤害流程。

## 文档边界

本次仅补理论与修正旧说明：水面导航不是所有水状态的唯一导航器；局部脚部评估不等于完整身体碰撞；每tick池不等于全模组总A*次数硬上限。没有修改源码、默认值、配置或现有导航行为。未来分层路径、机关规划和新增观测字段仅列设计方向，未作为当前能力。
