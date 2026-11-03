# 架构审查记录

从源码模块地图出发，按调用链记录权威状态、生命周期、热路径和验证证据。表中“待深入”仅表示已划定审查入口。

| 范围 | 核对入口与不变量 | 状态 |
|---|---|---|
| 听觉与群战感知 | HumanPerception → HumanAwareness → HearingRelevance → 线索；预算不足延后，声音不直接赋予仇恨 | 听觉入队与脚步排序已修。导航预算归还已修，感知双层额度归还已修，目标识别待深入 |
| 战斗、伤害与阵营能力 | HumanBehaviorControl → ActionCoordinator → 各武器/恢复/撤退执行；取消须归还移动、手部和朝向控制权 | 已查动作权限表的超时、抢占、会话取消；各执行模块的物理状态收尾待深入 |
| 导航、拾取与装备 | HumanNavigationOwnership、路径预算、掉落占位与背包交换；失败与延期区分，资源只能结算一次 | 待深入 |
| 客户端渲染、UI、观察和监控 | HumanRenderer、PanelScreen、HumanMonitorService、MonitorRemoteView；客户端呈现与服务器权限分离，结束清理输入、声音、票据 | 待深入 |
| 可选模组、Mixin 与网络 | 逐项核查加载门槛、收发线程、消息权限、版本适配及缺依赖回退 | 待深入 |
| 属性成长、雇佣、经济与培养 | progression、soldier、economy、cultivation；规则由服务端决定，交易及成长保存有明确提交点 | 待深入 |
| 自然生成、据点、配置与资源 | world、settlement、config、bootstrap；模板坐标变换、存档兼容和数据加载 | 待深入 |

## 已核查的外部做法

- [Linux 实时调度队列](https://github.com/torvalds/linux/blob/master/kernel/sched/rt.c)采用每优先级一条队列。听觉只有五个固定等级，直接按等级分队即可，入队无需搬移积压，取最高等级最多检查五条队列。512 条总容量、同等级先入先出、满时淘汰最低等级尾部及处理进度均保留。
- [Unreal Animation Budget Allocator](https://dev.epicgames.com/documentation/unreal-engine/animation-budget-allocator-in-unreal-engine)用画面重要程度动态调整动画更新。与现有距离/人数分档同方向，是否引入帧耗时反馈需先检查当前分档的成本和兼容动画状态。
- [Valve Source NPC 小队策略槽](https://github.com/ValveSoftware/source-sdk-2013/blob/master/src/game/server/ai_basenpc.cpp)通过释放可占用槽位协调共享目标。HHU 已有拾取目标占位，战斗中能否借鉴需先区分开火资格、选敌及移动位置争用。

- [Linux 调度带宽回收](https://docs.kernel.org/6.13/scheduler/sched-deadline.html)区分实际执行和可回收的预算。HHU 在 FairWorkBudget 内退回明确取消或实际未使用的预约余额。既有阵营轮转、每 tick 上限和已执行计数保留。未调用取消的离场请求仍依赖既有过期机制。

- 感知队列核对了维度与全服原子预约，以及饱和维度不会阻塞其他维度。沿用导航轮的预算回收原则，取消、缩小和无效预约均按原维度归还未使用余额，同时归还全服额度。需求所属阵营与批次规则沿用现有调度。

## 战斗动作权限

- HumanBehaviorControl 在多个同 tick 入口推进时钟。ActionCoordinator 每个时钟值只执行一次到期扫描，直接通过迭代器移除到期动作。正常取得空闲资源时无需创建抢占清单。
- 抢占仍先验证整组资源，失败时保留所有旧所有者。会话关闭先禁止准入，再执行实际移动、枪械、恢复等清理，最终取消旧令牌。权限回收与物理动作收尾是两层职责。
