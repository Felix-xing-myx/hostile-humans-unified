# 命令、自然作者状态与小队接口

这是本轮命令模块的接入契约。网络、客户端、配置解析、实体生命周期与物品注册由主控整合；本文不表示这些外围入口已经接完。

## 统一边界

`OrderDistances.context(human)` 是唯一驻守上下文解析，给出作者/玩家来源、可用性、原因、稳定原点、`GarrisonSettings`、日常半径和追击外圈。其他消费者不直接读 NBT 决定活动范围。

- `SoldierOrder.get` 把旧 HOLD_POSITION/GUARD/PATROL 逻辑映射成 GARRISON，`raw` 仅供迁移；旧五值 ordinal 不变，GARRISON 追加为第六值。UI 使用显式 FOLLOW/BODYGUARD/GARRISON 数组。
- `GarrisonSettings.player/natural` 读取不迁移存档；旧 GUARD/HOLD 默认半径48、PATROL默认32，存在旧 radii 覆盖时沿原命令读取。主动编辑后只写 `order_garrison.patrol_radius`，清旧 radii.guard/patrol/garrison；follow 独立保留。
- `SoldierOrder.apply(human,order,garrison,followDistance)` 是单兵和小队共用写入口；null 表示不修改该部分。参数修改不重取锚点；切入驻守才取当前脚部方块锚点。需要明确重新设岗时可调用 `set(human,GARRISON)`。
- 跟随/护卫的活动半径约束士兵身体与移动，不代替武器射程。士兵位于主人合法圈内时，仍可依原许可、感知和实际武器射程站定打击圈外目标；不能沿攻击路线越出身体活动圈。护卫原有12格贴身战术圈保留。
- 驻守按 D07 对目标、完整 Path、射位与局部走位共同使用有限外圈。声音、拾取、闲逛始终用日常圈，不借追击扩大。
- `GuardCombatState` 从首次离开日常圈起计服务器时间，导航预算等待也不续期；受击、换敌、重新入圈均不能刷新这次400tick。触界/失效/到时进入回位，到位80tick才可重新出圈。
- 回位不传送、不强加载。不可达按原预算和失败退避重试。原地站桩、固定路线、随机巡逻分别回原点、最近合法路点、原点附近。

`orders.garrison_leave_threshold` 不拖延触界或缩圈回位；用于回位绕障的有限余量。进入回位时冻结总路径最远半径为 max(当前离原点距离, 回位点离原点距离)+此余量，默认余量4；不会随每次局部侧步继续扩张。这样允许墙角回位先有限远一步，不能逐点单调逼近而卡墙，也不能借回位重新追击。

## 配置接入

初始化/重载全局配置后调用：

```java
OrderDistances.configure(new OrderDistances.Defaults(
    followDistance, garrisonRadius, returnDistance, leaveThreshold, walkSpeed,
    chaseExtraRadius, chaseMaxRadius, chaseDurationTicks, chaseReturnCooldownTicks,
    routeVerticalTolerance, routeFailureBackoffTicks, routeReachDistance,
    routeTimeout, routeMaxPoints, routeWaitDefault));
SquadData.configureLimits(new SquadData.Limits(maxSquads, maxMembers, nameLength));
```

内置默认对应方案：48/32、回位0.8、行速0.65、外扩16/封顶96/400tick/80tick，路点1.5/高差0.75/200实际执行tick/全失败100tick，默认64点；小队8×32，名16 Unicode 字符。构造器限制非法配置值，不为大半径扩大导航预算。

## 实体生命周期与控制优先级

1. 安装原 `SoldierOrderGoal` 到所有 Human；无主人且没有 natural_state 时不占 MOVE。
2. 每个服务端 Human 的生命周期调用 `SoldierOrder.tick`，放在只有 `hasOwner()` 才执行的分支之外，确保无主自然驻守也推进追击时钟。该方法不分配或停止导航。
3. join 时调用 `NaturalState.onJoin(human,event.loadedFromDisk())`。普通磁盘加载只验证，不重新定原点。
4. 正式雇佣命令优先于自然作者状态；存在 temporary_owner 时自然状态暂停。固定点开发测试、熔岩/上岸/撤退仍优先。
5. 解雇沿现有 `SoldierOrder.clear` 清玩家实例控制；自然作者字段、自然原点、自然 once 完成状态保留。自然运行态 `route_runtime` 与玩家可选路线的 `order_route_runtime` 分开。
6. 自然工具 `NaturalState.set/clear` 在受雇期间仅编辑作者状态，不重置当前玩家战斗状态。

## 作者数据与新实例契约

作者字段 `hostile_humans_unified:natural_state`：schema=1，stay/chase_on_hate/patrol_radius，可选 route、route_mode、route_speed、route_rotation、route_mirror。每个 route 点保存有限本地 x/y/z 和 wait。

`natural_origin` 单独保存实例维度、精确脚部位置、作者身体基准 yaw、右向/前向单位基向量，以及明确的放置 rotation/mirror。头部运行时转动不改变它。普通保存、区块卸载、重启、雇佣与解雇不重取原点。

明确的新实体无固定路线时，`onJoin(...,false)` 可一次记录当前位置；有固定路线时必须提供放置上下文，不能从最终 yaw 推镜像。

```snbt
"hostile_humans_unified:natural_new_instance": {
  author_yaw: 0.0f,
  rotation: 90,
  mirror: "none"
}
```

`author_yaw` 是模板作者基准身体朝向，**不是原版已经旋转过的最终实体 yaw**。rotation 只能0/90/180/270；mirror 为 none/left_right/front_back。先做作者额外镜像/旋转，再用作者基向量变成世界方向，再做明确放置镜像/旋转，最后平移到原版放好的实体脚部位置。每一步只应用一次。

未来模板入口在清掉旧实例字段后，也可直接调用 `initializeNewInstance(human,authorYaw,placementRotation,placementMirror)`。已有 origin 返回 false，不覆盖。普通 `loadedFromDisk=true` 即使残留标记也不会当作新副本初始化。

缺原点、原点异维度/未加载时等待；固定路线缺变换上下文时暂停并显示原因。空/格式非法路线回合法随机巡逻；越界点不会被执行。路点按水平距离和脚部高差判断，并检查短碰撞连线以免墙两侧误达。路线运行时保留索引、往返方向、实际执行tick、wait进度、once完成和整线退避。

`NaturalState.authorOnly(forgeData)` **仅返回命令模块的作者字段副本**，不修改入参；调用者可与阵营等模块的作者字段合并。它不是已实现的模板导出器，也不能直接替换完整 ForgeData，否则会丢其他模块作者字段。未来模板导出还必须在实体根层清 UUID/Owner，剥离玩家命令、成长随机实例快照、natural_origin、所有路线运行态，再显式标记新实例。本轮不改结构模板或 worldgen。

## 调试入口

由主控在命令事件调用 `NaturalStateCommand.register(dispatcher)`，注册权限2的：

```text
/hh natural set <entity> <stay> <chase> <radius 8..96>
/hh natural clear <entity>
/hh natural check <entity>
```

调试物品右键实体调用 `useDebugTool(player,human,inspect)`；inspect=true仅检查，否则循环四种驻守作者参数，保留半径。`describe` 返回实际参数、原点原因、路线错误/状态；主控可将提示换成共用语言键。不提供玩家画路线玩法。

## 小队数据与网络

`SquadData.get(server)` 挂主世界 SavedData。成员以 UUID 保存，雇佣账本证明正式归属，已加载实体再复核真实雇主和生存状态。未加载且账本仍归本人则保留；不能查不到实体就认定死亡。`validate(server,owner)` 在名册打开/刷新时调用，空队保留名字。

- `assign(server,owner,number,humanId)`：0移出，其余编队，先检查容量再从原队移走；失败不丢旧队。
- `rename(owner,number,name)`：过滤控制/格式字符和颜色码，按 Unicode 码点截断，不按 UTF-16 半字符截断。
- `squads(owner)`、`squad(owner,number)`、`membership(owner,humanId)` 是只读快照。
- 降低配置上限保留旧超限记录，允许读取和原队指挥，但禁止向配置范围外继续加入。
- `removeMember(id)` 可在死亡、解雇、转属时主动清理；未调用时，下一次 validate 仍从账本对齐。

`SquadCommands.open(player,number)` / `openToken(player,stack)` 返回 `Session(token,squad)`；新会话替换旧会话。`snapshot(player,token,number)` 返回名字、总数、可提交数、未同步数、成员 UUID/已加载/可提交/行动模式及行动模式计数。未加载不冒充已经生效的模式。

```java
new SquadCommands.Update(order, garrisonSettings, combatMode, pickupEnabled, followDistance)
SquadCommands.apply(player, token, number, update)
```

Update 每个字段都可 null，表示不修改。批量固定服务端成员快照，只处理当前已加载且仍为本人正式雇员的实体；逐人重新复核，没有距离过滤、强加载、跨维度传送或离线补发。返回 `Result(validSession,applied,unloaded,unauthorized,invalid,failed)`，`rejected()` 汇总无权限与失效；单个失败不撤销其他成功成员。它不创建小队 AI，仍逐人调用单兵写入口。

物品 hook：`bind`、`cycleToken`、`boundSquad`。这些方法只校验绑定，不识别物品注册类型；**网络必须每次检查玩家实际手持物品确为令牌，且 boundSquad(player,stack)==请求队号，再检查会话 token**。已有会话 token 不能代替持有物校验。编队/改名请求仍由名册网络的既有会话验证后调用 SquadData。

关闭界面/玩家退出调用 `SquadCommands.close(player)`；停服调用 `clearSessions()`。两个旧网络协议各提升一次，服务端快照是唯一界面事实。

## 验证边界

静态契约测试覆盖字段单权威、旧 ordinal、自然旧盘不重锚、模板字段分离、日常声音边界、未加载成员保留、所有权与会话重查、固定点测试释放和锚定回位不传送。`FiniteOrdersPolicyTest` 提供纯 Java 时钟/路线/护卫远射政策测试，待主控统一编译后执行。

没有执行 Gradle、实例部署或游戏内验收。语法解析不证明 Minecraft API 类型兼容；整合后仍需编译与路线/多人/边界实际场景验收。
