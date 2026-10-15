# 敌人发现、警觉与锁定源码地图

知识库正文：`E:\个人知识库\Hostile Humans Unified\02-战斗与生存\敌人发现、警觉与锁定.md`；开发参考：`05-开发参考/敌人感知原理与修改入口.md`。本轮只研究文档，保留未构建的导航交接实现，不改源码/配置。

## 主要入口

- Human.registerGoals：0原版HurtByTarget＋alertOthers，1玩家Limiter，2愤怒LivingEntity（13,true,false），4自主Mob（5,false,false）。HumanGunner.configureSoldierOrders另加-11贴身保护与-10驻守目标Goal。
- NearestAttackableTargetGoalWithHumanLimiter：首次候选继承原版TargetingConditions；canSelectPlayer检查HumanUtil.isLookingAtTarget或isAlert，附近15格已有目标数量对比Config.maxTargeting。初次朝向成功先警觉并通知25格同team，之后才限数量。canContinueToUse覆写省略LOS/unseen计数，保留存活/同维度/攻击许可/队伍/实时范围条件。
- HumanUtil.isLookingAtTarget：视线方向dot到目标眼睛方向 > 1-0.4/d，同时hasLineOfSight，远处锥角收窄。已警觉不再要求这个额外朝向，但新候选LOS仍保留。
- NearestAttackableTargetGoalCustom：forCombat().range(...).selector(...)没有ignoreLineOfSight；mustSee=false仅不能据此推出首次透视。非玩家候选盒水平R/垂直4；玩家getNearestPlayer单独查询。
- Human.canAttack/isAngryAt/setTarget/hurt：统一许可、模式/命令/阵营、真实受击授权、实际目标赋值与切换。身份牌主动敌意清理在HumanGunner维护入口，友军战术过滤在HumanRelations等；不是每种关系都只靠原版Team。
- HumanAwareness/BlockMixin/DoorMixin/EventHandler：非创造玩家破坏/放置/部分通道操作、伤害事件调查；以actor包围盒扩展16查人，记录事件BlockPos，不校验声传播或LOS。爆炸单独以事件方块盒扩展16。放置警觉及玩家受伤警觉调用alert，actor盒扩展10，无同队/LOS过滤，只置isAlert。
- InvestigateSoundGoal：已有攻击目标或睡眠等不启用；调查距离平方<1000，移动到事件点，结束清空并冷却；未直接setTarget。单一调查位置覆盖，不是通用听觉队列或振动模型。
- AdaptiveCombatGoal：resolveThreat保留当前/受击/逃生记忆；最多4威胁、600tick记忆、回忆64格；60tick内至少3受击且多来源触发80tick集中锁定；约6tick近敌重排，已有战斗才触发，部分分支无LOS。非任意未知玩家雷达。
- HumanRelations/HumanSignalItem：敌对信标指定召唤者、范围+88、128格重锁与200tick/64格反击；defend/followOwnerAssault给主人附近48盒内适用人类授权/指派，不走普通初次目视。BodyguardProtection：5tick主人中心扫描，普通未知主动敌人需LOS，已指向/已授权等例外。
- SoldierCombatMemory/SoldierOrder：600tick无输出放弃、60tick忽略与威胁解除；与增强30秒记忆、身份牌600tick报复记忆不是同一状态。
- ShieldDefenseController与枪械后端：来袭箭轨迹预判/射手信息、伤害事件40tick枪火压力，不是监听每次GunShoot来广播枪声目标。RangedShotClearance/GunFireAuthorization负责真正开火射界。
- HostileHumanTargeting是其他怪物发现Human的反向机制，不混作Human自身扫描32格限制。

## 本机原版核查

只读官方映射Forge1.20.1-47.4.16 JAR，资料在维护目录验证日志/target-awareness：

- TargetingConditions默认checkLineOfSight/testInvisible=true；有效范围max(2,R*visibility)，Sensing.hasLineOfSight；不是所有实体进AABB就锁定。
- TargetGoal.mustSee控制继续阶段unseen计数，默认unseenMemoryTicks60，经过reducedTickDelay。玩家Limiter覆写不使用它。
- HurtByTargetGoal候选ignoreLineOfSight/ignoreInvisibilityTesting；start设置unseenMemoryTicks300。同运行类求援对无目标/非攻击者盟友及适用主人等检查，alertOther最终调用setTarget，未作LOS。
- Goal.reducedTickDelay为向上除2。普通玩家随机间隔10→5，自定义13→7、5→3；含义为适用canUse的随机跳过，不能当严格世界tick扫描周期。

## 配置核查与文档校正

默认及既定Forge47.4.21实例读取的基础FOLLOW_RANGE为40/40/40/48，native_gameplay.max_targeting为3。文档明确其为普通玩家选择入口的15格局部计数，不是setTarget全局硬限；0不关闭反击/信标。实例配置只读，没有覆盖。

文档将初次视线、警觉、保留、事件调查、受击/通知与特殊指派分开；没有将无LOS分支伪装成完善声传播，也没有把朝向公式当固定视锥或把unseen参数当统一丢锁秒数。当前头盔没有目标来源/警觉来源字段，后续观测扩展仅列设计方向。
