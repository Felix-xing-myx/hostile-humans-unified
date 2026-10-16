# 行为模块维护入口

本记录覆盖本轮审查的恢复、武器分类、伤害、举盾、成长、装备生成和导航主调用链。接口统一的是规则与状态归属，不把不同职责集中成一个全能控制器；表中的入口是维护入口，不代表新增Java interface类型。

| 职责 | 维护入口 | 调用约束 |
| --- | --- | --- |
| 战斗顺路补给 | `CombatResupply` / `CombatResupplyConfig` | `SoldierPickupPolicy.canCombatResupplyNow` 独立许可，`HumanLootManager.collectCombatSupply` 仅保护性存储，不持有MOVE/LOOK/装备控制 |
| 恢复用品选择、使用与背包托管 | `RecoverySupplies` | 各恢复Goal申请同一UseSession，不自行扣物品或换回副手 |
| 恢复效果推进 | `RecoverySupplies.tickEffects` | 每个服务器LivingTick一次，在菜单编辑锁之前；内部依次推进再生、MedicalSupplies、食物，不改装备 |
| 使用完成补充结算 | `RecoverySupplies.onUseCompleted` | Human先执行原生completeUsingItem，再在成功结束使用后调用；原生效果、药水消费和瞬间治疗II补充值不散落在实体里 |
| First Aid可选适配 | `FirstAidCompat` / 内部`FirstAidApiBridge` | 仅Human且必须拥有实际UseSession；Forge完成事件处理原生结果，公开API桥在安装后才解析；不改玩家 |
| 弓弩分类、远程威胁及战术走位资格 | `CombatWeaponPolicy` | 普通弓弩子类及斯巴达共用判断；威胁判断不授权攻击、不更换武器 |
| 枪械身份与能力 | `GunSupport` | 物理持枪禁盾使用isGunItem；有弹/可装填的作战资格使用usableGun，不混合两个含义 |
| 物理武器托管 | `GunCustody` / `RangedWeaponCustody` | 保留各自实体物品事务；枪/弓的换弹、加载、近战租约不同，不能合并成无差别交换 |
| 分阶与成长伤害倍率 | `HumanDamageScaling` | 只在既有LivingHurt阶段调用一次；枪伤先分类绕过此处，三叉戟先于箭矢识别；友军授权、被动盾减伤仍在各自原阶段 |
| 跳砍暴击 | `PursuitJumpAttack.strike` | 唯一真实近战命中入口，读取全局倍率，原版模式停用；临时属性在finally撤销，不新增攻击调度 |
| 举盾与反击窗口 | `ShieldDefenseController` | 单个Human共用状态所有者，近战/预测箭矢/枪火/护卫请求不能各自启停盾 |
| 成长与生命 | `HumanGrowth` / `GrowthHealth` | 服务端决定与保存，UI只使用同步快照；装备不追溯替换 |
| 出生装备 | `HumanSpawnEquipment.completeIfReady` | 等待HumanData与注册表，按既定阶段完成一次；不在多个生成回调各自发装备 |
| 导航与地形 | `NavigationSupport`、`CombatTerrainNavigation`、两类寻路预算 | 基础追击与可选绕路预算保留各自职责，防摔/命令/水面调度继续优先，不增加竞争Goal |

## 明确目的地的分档导航接口

- `NavigationSearchConfig`是UnifiedConfig持有的重启期不可变全局配置，对应`navigation_search`，不修改FOLLOW_RANGE。距离和节点上限独立并按档位单调归一；远程池至少容纳最大作业。
- `LongRangeNavigation.search(Human, BlockPos, Purpose, accuracy)`返回READY（完整安全Path）、PENDING（保留目的地/阶段/排队）、FAILED（已拒绝或失败缓存）、UNSUPPORTED（水域等非HumanNavigation由原调用者回退）。不安装路径、不争MOVE/LOOK、不解析目标UUID；调用者持有合法位置及实际动作控制权。
- `accepted`登记导航实际保留的Path，避免moveTo等价路线复用造成引用失配；`cancel`取消用途，`cancelAll`用于命令边界。失败缓存为每人每用途16条纯几何/场景记录；WeakHashMap值不持Entity/Level引用。等待中的坐标更新保留FIFO和已尝试档位。
- 普通档领取1份Basic额度；扩展/远程独立FairWorkBudget按ceil(nodes/1024)完整领取，默认全服8份/tick。每用途每实体每tick最多一次实际查询，升级跨tick，不将局部随机候选升级为远程任务。
- `HumanNavigation.createBoundedPath`用独立PathFinder/HumanNodeEvaluator与加载区块快照查询，复制门/浮水/跨栅栏旗标，保留梯子、失败边和safeTransition；已加载目标列规范化后搜索。查询不改原版私有targetPos/reachRange/卡住计时。
- 接纳bounded路线时记录实际路径及目的地；`getTargetPos`展示对应目标；`recomputePath`按原20tick节制设置刷新、清已接管延期标记。追击/拾取/跟随/调查/测试均识别needsBoundedRefresh并回原预算；stop清标志，不向原版旧target自动重算。
- 安全/命令只会否决，不能因扩大档位强行放行。已完成但被安全或命令拒绝的查询不升级。失去旧路的调查续同一destination，不重复计点；纯排队不增加失败或触发跟随救援。
- 路线接纳计时与查询隔离分开：moveTo对等价活动路只更新速度，不重置原100tick物理停滞监测；真正换入新bounded Path才清timeoutCachedNode/timeoutTimer/timeoutLimit并更新lastTimeoutCheck。首次接纳消耗trial标记，缓存重交不清延期环境刷新。活动bounded路不继承原版上一条路的private isStuck；原版真实停滞仍通过stop释放活动引用、保留原检测结果。
- 诊断使用既有96字符预算字段附带有界统计，协议不变。展开数为getNeighbors真实调用次数，不能冒充全部访问数或精确失败原因。局部路径的原范围与倍率保持。

## 视觉拾取与绕行连续性

| 职责 | 接口 | 约束 |
|---|---|---|
| 物品视觉证据 | `HumanLootVision.inspect / lastSeen` | `VISIBLE / HIDDEN / DEFERRED`；复用HumanPerception真实头部视野，COLLIDER射线；4射线/人/tick并共享PerceptionBudget rays；64条探测/64条已见，位置缓存600tick，不保存Entity/Level引用 |
| 普通候选查询 | `HumanLootManager.NearbySearch` | 查询延期返回null并保持阶段；评分Double.NaN仅表示延期，保留当前候选；视觉先于价值评分，不把HIDDEN加入无价值缓存 |
| 有界选择 | `PileFirstSelection.advance` | 分数NaN保留候选，下轮续查；broad供应器null保留pile阶段；旧调用普通分数行为不变 |
| 战斗补给 | `CombatResupply.State` | UUID候选列表/游标跨tick，pending不受启动间隔限制，快照最长80tick；不接管MOVE/LOOK；每轮一次成功交易 |
| 拾取路线 | `ValuableItemPickupGoal / PickupRouteProgress` | 使用knownItemPosition，不使用隐藏实时坐标；新旧路均完整、安全、剩余节点符合命令；配额等待不计停滞，失败只释放自有路线 |
| 路径失败记忆 | `LootRetryPolicy / FailedItem` | 与装备价值筛除分离；200/400/800/1200tick最低等待，静态场景1200tick兜底；32项，位置变化或局部已加载方块变化允许重评估 |
| 拾取辅助观察 | `HumanAttention.observeLoot` | 负优先级请求，仅辅助闲置朝向；不抢战斗或真实受击观察 |

无全局A*范围或节点阈值扩容。完整路线尚未算出不能以部分路成功替代；视觉延期也不能作为路线失败写入长期记忆。知识库职责入口为“士兵装备、拾取与掉落”“活动预算与调度”及导航理论02/04。

## 本轮修正

- 将回血推进移出菜单写锁：编辑装备暂停AI写物品，不暂停已经支付的疗程或冷却。食物回血改为持久化剩余加载tick，避免实体重载后tickCount重置导致旧截止日期长时间不可达；旧已支付食物疗程不丢失，缺少新计时字段时在一个正常间隔后恢复。
- First Aid的完成结果即使只改变NBT也必须消费一件，原生已消费则不再扣；异常增多的返回堆叠不授予效果，仍保留其他处理器的返回结果。
- 删除无写入方的jump_attack_until旧事件路径；跳砍的公开配置接入PursuitJumpAttack，默认1.5不变，0至10对应属性修正量factor−1。同步更正“只在下落”的旧注释。
- 战术和盾牌共用威胁判断，HumanUtil与武器托管共用弓弩分类，补认模组BowItem/CrossbowItem子类及非原生ProjectileWeaponItem的斯巴达弓弩。

## 验证边界

本轮执行源码契约、Java语法解析、资源解析和差异检查，不执行Gradle、类型编译、构建、实例配置更新、同步或游戏测试。重点游戏验收：菜单开启期间疗程与抗性倒计时一致；食物回血跨卸载/重启；First Aid堆叠与中断；兼容弓威胁预判及近战切换；跳砍倍率0/1/1.5/2和原版模式；TaCZ枪伤不叠近战倍率、护盾和友军过滤保持。

## 行为配置入口

`UnifiedConfig.jumpAttack()` 与 `firstAid()` 是不可变的服务器配置快照。跳砍消费全局专用参数，普通移动跳跃消费各阶CombatAiConfig，不互相覆盖。FirstAidSettings只控制人类效果/品质；公开API继续负责原生敷用与恢复节奏。既有医疗疗程使用已保存的消费快照，关闭识别不清空已付款效果。

## 水面追击与高崖出口

`HumanTerrainSafety.transitionReason`在落差豁免之前检查水到陆地转换：真实水面的一格出口高度预算、站立体积及相邻节点跨越空间；只检查已加载区块。HumanNavigation的临时失败边同时匹配浮水起始节点和实际前一节点。

`WaterCombatNavigation`是水中追击的停滞观察与上岸租约协调器，不注册新MOVE目标、不另建寻路器。约40 tick无水平位移才调用现有findNearestShorePath，每次最多四次路径预算，失败40 tick后重试；已能在距离和视线内攻击的单位不判为追击停滞。只接受可达、安全且符合SoldierOrder活动范围的水面地面路线，拒绝水下导航回退；不清除目标、不传送、不破坏地形。

已安装上岸路线用ShoreProgressTracker观察下一节点的实际接近与推进，停滞后暂避路段并排除该出口。PursuitNavigation、MovementContinuity和Human的水面状态维护让位于此租约；失效、目标变化或生存撤退时只释放自己拥有的路线。没有可达出口时按预算重试，不将任意崖顶当作可执行路线。

## 雇主对正式士兵的伤害保护

HumanOwnerProtection统一判断当前玩家是否为目标Human的正式雇主，HumanRelations.isOwnedBy复用HumanRelationshipPolicy.isFormalOwner的UUID判定。此方向不读取PVP、身份牌或野生阵营，也不把临时援军当成正式士兵。

Human.hurt在任何战斗计时、护甲损耗、受击/自卫处理之前拒绝雇主来源；Forge AttackEntityEvent和LivingAttackEvent使用同一规则，间接伤害优先读取DamageSource.getEntity，缺失时回退到直接Projectile的owner。HumanProjectileRelations在碰撞候选阶段跳过雇主自己的正式士兵，复用既有原版/TaCZ普通及穿透弹入口；爆炸Detonate删除受保护目标，避免外置爆炸击退。TaCZ Pre使用公开可取消事件，在护盾/枪火压力/身份牌记忆之前取消并清零伤害。本地只读API字节码确认该事件可取消且原生子弹在取消后直接返回。

所有规则实时读取实体正式归属，解雇及转让即时改变保护，不缓存雇主、不新增存档字段。其他玩家、士兵对玩家的既有PVP逻辑、友军人类碰撞及环境伤害保持原有职责；失去来源归属的环境效果不推测为雇主攻击。

## 弓弩临时近战与主动脱离

RangedHybridMeleeGoal继承普通MeleeAttackGoal，近战阶段直接super.tick，避免第二套追击、站位和命中调度。MeleeAttackGoal.tickAttackOnly仅抽取原有冷却递减与checkAndPerformAttack，使主动拉开距离时仍可沿用相同反击规则而不再下达追击移动。

RangedMeleeCycle只累计实际运行的近战tick，默认80 tick后在无前摇/挥击的安全边界开始找射界；复用RangedFiringPosition的增量搜索与共享预算，等待期间继续普通近战。合格路线必须完整可达、安全、沿路服从士兵活动范围，且终点有射界并能增大距离。先成功安装新路才释放旧追击元数据；已安装路线不按每tick随机重选，10 tick核验目标位移与路线安全（目标位移平方超过4即失效），停滞20 tick或脱离80 tick则回归近战。搜索最多40 tick，失败后设置60 tick重试门槛并从零累计80 tick近战；不是60 tick到期就立即脱离。短暂举盾不累计战斗tick；若已在搜索/脱离阶段被打断，则interrupt返回近战并重设该周期。

武器仍由RangedWeaponCustody独占托管，至少六格且有射界时尝试原入口归还，CloseDefenseHold保护未完成攻击。举盾或生存Goal打断时只释放本Goal所属脱离路线，不清除仇恨、不停止接管方路线；原地驻守和水中/上岸不主动脱离。常规近战累计时间在短暂举盾后保留，目标或实际武器改变才重置会话。

## 盾牌损耗与破盾

`ShieldDurability`统一主动格挡与枪手副手盾的损耗事务：服务器按`1 + floor(damage * 1.5)`调用ItemStack.hurtAndBreak，保留原生耐久附魔及物品钩子。每个人类共用4 tick（0.2秒）损耗冷却，换手/换盾不绕过；根据调用前后的耐久或数量确认实际损耗，完全免损不进入冷却。破损副手盾只结束该手正在进行的物品使用，不中断主手枪械换弹。

调用者继续提供现有阶段的伤害：原生活动格挡提供被挡伤害，TaCZ主动格挡提供减伤前枪伤，枪手被动盾提供护甲等结算后、20%减伤前的LivingDamageEvent金额。此次统一公式，不迁移伤害事件阶段。损耗冷却不取消减伤或格挡反馈。

主动破盾复用Human.blockUsingShield与ItemStack.canDisableShield，不硬编码斧头类型；成功后shieldCoolDown至少100 tick。Human.startUsingItem在所有主动举盾入口执行禁用检查，弓弩射击不缩短已有禁用。GunnerShieldProtection不读取此冷却，主手枪＋副手盾的被动减伤独立，副手盾损坏/移除后按既有装备检查失效。

## 普通战斗进食与反击

`RecoverySupplies` 是选食物、申请 UseSession、托管副手与库存、连续进食和恢复结算的唯一入口；`OffhandFoodUse` 只负责服务器上的食物动作计时、有限消耗及视觉状态。普通食物和金苹果均不调用原版 startUsingItem，不占用主手弓弩/枪械的使用状态，不拥有 MOVE/LOOK，不停止导航、不控制攻击目标、不附加移动减速。正常武器控制器继续负责攻击、切换近战、追击及撤退。删除了原先抢占战斗导航的 CombatEatingMovementGoal。

所有恢复 Goal 使用 RecoverySupplies.isUseInProgress 等待自己的会话，不能以“主手正在使用物品”判断副手食物是否完成。每个食物动作每 tick 仅推进一次，完成前标记终态，经物品 finishUsingItem 和 Forge 开始/完成钩子执行真实消耗；未消耗不追加本模组食物回血。中断、紧急治疗和手动背包操作结束动作，副手盾及容器余物仍通过原有精确交换与安静等待流程归还。客户端同步的进食标志仅用于视觉，不决定物品消耗或恢复。

进食本身不发起跳跃或专属拉距。PlayerLikeMovementGoal 只在确实沿远离目标方向疾跑时允许进食中的随机撤离跳跃；地形跳跃与正常跳砍仍由原控制器决定。药水和 First Aid 药品继续使用原恢复会话，不纳入食物并行动作。移除失效的 food_use_speed_multiplier 配置，举盾减速配置保持独立。

## 可选枪械与医疗联动

- `GunSupport`保留物理枪身份和可战斗枪两种语义，`CompositeGunSupport`按当前武器分派。`GunnerGoal`只管理共用战术，不引用TaCZ/Scguns API；`TaczGunMechanics`隔离原TaCZ原生状态操作，ScgunsIntegration只调用原生NPC射击接口，不注册原生MOVE目标。
- `GunSupport.magazineCost`统一普通/能量枪计费入口；Scguns装填用稳定物品事务UUID和人类服务端剩余加载tick关联，切近战不重复收费、不每tick同步物品进度。
- `MedicalSupplies`统一First Aid与Scguns用品识别、品质、时长、可使用性和效果推进。`RecoverySupplies`继续独占UseSession和物品托管；原生完成事件只在持有会话时授予效果，不改玩家。
- `ScgunsCompat`为无可选类型签名的入口，投掷物与袭击API隔离在安装后才调用的桥内；完整范围及限制见[Scorched Guns说明](SCORCHED_GUNS_COMPAT.md)。

## 2026-10-11 闲置注视玩家调整（源码待构建）

原HumanLookAtPlayerGoal复制了原版注视逻辑，但实际构造默认概率0.8、注册距离64格，造成闲置时连续盯人。现复用原版LookAtPlayerGoal：范围4格、每次候选检查2%概率、原版短暂注视时长；结束后等待100～200服务器tick（5～10秒）才允许下一次候选检查。保留RandomLookAroundGoal，空闲间隔可以自然转头。并非按秒计算2%概率，不保证固定周期看玩家。

仅在无战斗目标时启动或继续，战斗瞄准交由近战/弓弩/枪械Goal；没有修改跟随、护卫、导航、仇恨与创造模式规则。注视是低优先级LOOK任务，其他行为可以抢占；被抢占后同样进入间隔。全部在服务端AI中执行，所有阶级及野生/已雇佣单位均适用。

来源：HumanLookAtPlayerGoal.java、Human.registerGoals；基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d上的当前工作区。未构建、未同步。待游戏验收：创造模式及友好玩家在4格外不再被闲置注视，近处偶尔短暂注视，转头间隔与战斗/跟随无冲突。

## 2026-10-11 焦燃人类开火音量与换弹声音（源码待构建）

ScgunsIntegration：本模组人类开火音量在之前计算结果基础上乘0.5，公式为max(0,焦燃mobGunfireVolume−0.5)×0.5。原生配置依旧生效；不修改玩家、焦燃原生生物或TaCZ音量。

换弹过去已有固定手枪声音入口；现改为实际Gun.getSounds().getReload()与getCock()：成功支付并建立换弹事务后播放一次换弹声，装填完成时播放一次上膛声。优先使用已注册SoundEvent保留原声配置；原生定义为空时沿用通用手枪对应音效。由服务端在士兵位置向附近玩家广播，HOSTILE分类，换弹/上膛音量保持1。后台换弹仍以士兵位置为声源，不在每tick或换武器时重复播放。未成功开始换弹（例如缺弹匣）不播放。客户端不增加换弹动画或动画计时器。

通过已安装ScorchedGuns-0.5.5的javap确认公开声音读取接口；未修改上游JAR。没有证据能仅凭源码判定用户听不到旧声音的唯一原因，实际响度与播放仍需游戏复测。源码基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加当前未提交工作区；本轮未编译、构建或同步。


## 2026-10-11 焦燃声音与玩家节奏换弹修正（源码待构建）

用户实测17:59构建版声音调整未生效，追加要求换弹与玩家节奏一致；已明确接受“复用原生公开参数及动画时长，由士兵现有控制器执行”，不直接调用只接受Player的玩家入口。

声音：测试实例mobGunfireVolume=8，旧入口将7.5减半为3.75仍超过混音上限；普通SoundEvent通道也没有使用焦燃的换弹距离。现集中到ScgunsSounds，使用公开PacketHandler/FrameworkNetwork/S2CMessageGunSound：射击和换弹分别读取焦燃gunShotMaxDistance/reloadMaxDistance，服务端附近广播，HOSTILE分类；muzzle=false避免重复火光，reload=true正确选择换弹衰减。原生GunShotSound对输入音量及距离因子作平方，因此枪声参数为sqrt(min(1,max(0,mobGunfireVolume−0.5))×0.5)，在未饱和输入下保留比例、饱和输入下近距离最大增益0.5，避免再次因大数截顶而听不出变化。换弹/上膛读取Gun.Sounds，缺项用原生通用手枪声音，输入1。未修改玩家、原生NPC、TaCZ声音或全局焦燃配置。换弹仍需实测是否清晰可闻，不能仅凭静态检查宣称所有静音原因已消除。

换弹：旧代码用reloadTimer除以getModifiedReloadSpeed(stack,1)，误把原生“所需时间的修正”当作速度；且忽略动画完成门槛。现在ScgunsReloadTiming使用GunEnchantmentHelper.getRealReloadSpeed/getMagReloadSpeed、武器容量/reloadAmount和现有RELOAD_SPEED属性（没有该属性时按1），不重复手写附魔/配件公式。ScgunsReloadPlan按原生动画length×20×修正后时间/原始reloadTimer建立阶段。弹匣式是一整段；manual为准备、按缺弹量及reloadAmount重复装填、收尾，逐次加入真实枪内弹药，收尾期间仍禁止射击。通用弹匣只在建立整笔事务时扣一次，能量枪一次计费/结束充满，不按FE数量建循环。闲置低于25%及战斗打空规则保持。

原生动画是公开格式资源而不是玩家换弹API：从焦燃JAR assets/<namespace>/animations/item/<item>.animation.json只读按需缓存，不扫描整个JAR、不加载客户端或Gecko动画控制器、不访问私有字段、不伪造玩家。当前原生枪均具备此路径；未知/缺失/无效资源退回公开时间参数。选择对应carbine动画（如存在）。客户端资源包对动画的覆盖不会改变服务端计时，非标准路径的额外枪包需要复核该资源边界；此项不保证未来任意架构更新自动兼容，也未复制每一条原生动画音效关键帧，仅对齐阶段计时并广播换弹/上膛声。

事务仍绑定枪上UUID，进度/步骤保存在Human服务端NBT，同tick最多推进一次；临时近战、卸载/重启不重启已支付事务，原枪离开库存则作废且不填充替代枪。旧已支付且无steps的事务仅按原计时完成一次。每阶段改变弹量而非每tick修改装备NBT，声音仅在阶段开始/最终完成广播。标准Player库存/外骨骼/副产物机制没有被冒充调用，士兵仍使用通用弹匣规则。

构建新增只读compileOnly Framework API参数 -PframeworkApiJar；默认本机framework-forge-1.20.1-0.8.0.jar，依赖不打包。所有可选引用仍隔离在Scguns适配器，缺席链接测试排除ScgunsSounds/ScgunsReloadTiming，纯ScgunsReloadPlan保留在核心链接检查。

验证：127项Python静态/配置/实际Scguns JAR公开API检查全部通过，无跳过。新增Java ScgunsReloadPlanTest使用实际双枪动画确认普通56tick、半时间修正28tick，覆盖manual每次装填、末次不足批量、空缺与缺资源回退；已接入check，但未编译运行。未构建、未同步、未推送、未启动游戏。待验收近远距离枪声与换弹声、双枪/普通弹匣/逐发枪/能量枪时长、快手附魔与配件、后台近战、存档恢复和通用弹匣扣费。

负责人主控；main主工作树；基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加当前未提交工作区，共用文件顺序修改，保留此前修改。涉及ScgunsIntegration、ScgunsSounds、ScgunsReloadTiming、ScgunsReloadPlan、build.gradle及测试/文档。


2026-10-11：GuardedRangedCharge取代GuardedCrossbowCharge，统一弓弩举盾后台准备。BowAttack/CrossbowGoal只经ShieldDefenseController的pauseRangedUseForGuard、restoreRangedUseAfterGuard与hasRangedCounterWindow接入；控制器管理状态与窗口，武器Goal继续负责真实射击。

## 高压防御入口（2026-10-11，待构建）

- Human.hurt在super.hurt及自卫授权后，按实际生命差调用ShieldDefenseController.recordPressureDamage；observeBlockedHit仅续期已经触发的压力。
- ShieldPressureConfig统一读取全局规则，RecoveryPressureWindow负责有界统计与安静期；不驱动导航、撤退或物品交换。
- RecoverySupplies通过shouldSuppressRecovery统一禁止新恢复、打断现有使用，并沿原结算入口归还盾牌。LivingTick按恢复结算→prepareHighPressureDefense→GunCustody.tick顺序协调。
- GunCustody复用实际枪械/近战交换与close-defense租约，高压期间拒绝提前还枪；TaCZ及Scguns继续使用既有后台换弹事务。
- AdaptiveCombatGoal优先进入DEFEND；盾控制器在高压期间维持格挡，恢复普通策略时仍由原控制器接管，不另建移动或开火循环。


## 2026-10-11 敌对怪物主动发现人类（待构建）

这是本模组核心的长期规则，不依赖焦燃枪械、TaCZ或任何可选模组。EntityJoinLevelEvent服务端入口通过HostileHumanTargeting为符合条件的Mob添加NearestAttackableTargetGoal<Human>，焦燃Sulfurhead/Dissident/Cog Knight等继承Monster的敌对单位和其它标准Enemy生物共同使用这个入口。野生、信标和雇佣人类均可成为目标，不以是否雇佣作为敌对判据。

仅补充发现目标，不替换怪物的移动、导航、攻击、射击、伤害或技能。新Goal优先级10，只在没有存活目标时启动，让原生玩家目标、受击反击和任务目标保持原优先级。使用原生随机选敌节奏、视线、攻击许可与目标继续有效性检查；范围为原生发现距离且最多32格，不新增每tick范围扫描。远程单位不强制拥有通往目标的步行路径。对目标的战斗权限以怪物canAttack以及双方isAlliedTo为准，不强行突破其它模组的攻击禁止。

默认自动覆盖Enemy接口，排除NeutralMob（例如未被激怒的末影人、僵尸猪灵）、OwnableEntity宠物和本模组Human，避免宠物和同类收到新选敌Goal。全局hostile_mob_targeting.enabled默认true；included_entities为空，可明确填入未标记为Enemy、但使用标准Goal攻击AI的实体注册ID，也可明确允许某个中立类型；excluded_entities默认为空，优先于额外接入和自动识别。额外接入不能覆盖宠物/人类排除或原生canAttack否决。完整重启生效。

安装时检查实际targetSelector，避免重复添加；不持久化安装标签，区块卸载再加载时原生重建AI后仍正确安装。只改变服务端AI，不增加客户端网络包。采用独立Brain、仅对Player执行攻击、特殊阵营或自定义选敌循环的模组生物仍需具体适配：本入口不保证所有模组的怪物都能自动使用原攻击方式伤害人类。

验证：146项Python静态/API契约检查通过；已核对Forge映射JAR的GoalSelector、NearestAttackableTargetGoal、TargetGoal与分类接口以及本机焦燃三个代表怪物的Monster继承关系。新增Java配置解析用例未编译执行，本轮无构建、同步、游戏启动或推送。

待游戏验收：不装焦燃时僵尸/骷髅/苦力怕主动发现野生和雇佣人类；安装焦燃后原生枪手实际追击、射击和技能；已有玩家目标不被抢占；中立/宠物/同队单位不误选；区块重载无重复Goal；配置关闭、名单优先级及远程隔河攻击；大量怪物下的选敌开销。


## 水面近战统一入口（2026-10-11）

WaterCombatNavigation.controlMelee协调普通/临时近战和举盾推进；PursuitNavigation在普通寻路前调用Human.approachCombatTargetInWater。HumanMoveControl执行当帧直向/命中范围内停步，MovementContinuity让出当帧局部移动；WaterCombatPolicy提供纯距离/方向策略。surfaceCombat确保普通战斗不请求水下导航；岸路和撤退仍由原租约管理。SoldierPickupPolicy及选敌切换阻止拾取移动残留。详见COMBAT_TERRAIN.md，源码已接入，待构建与游戏验收。

## 枪手局部交战（2026-10-11）

CloseRangedTactics统一GunnerGoal的局部迂回、GunCustody恢复枪械后的试射观察与ShieldDefenseController接收到的真实敌对生命损失。只返回战术决策，不自行交换物品、发射子弹或重写装填；完整路径受8格交战边界、命令区域、地形与共享搜索预算限制。盾牌和近战动画保持原单一入口。详见CLOSE_GUN_TACTICS.md；源码已改，未构建，待游戏验收。

## 2026-10-11 高压换盾的无效挥刀修复

MeleeCombatRange.canStartAttack作为近战起手的统一校验，检查目标存活与攻击许可、主手可近战、未有提交中的攻击、高压强制防御不在持续，并复用canStrike的真实武器范围/命中几何和视线规则。Human.doHurtTarget在生成Better Combat攻击计划、播放动画或普通伤害之前执行此门槛。GunnerGoal、AdaptiveCombatGoal、TridentHybridGoal的临时反击也先校验，移除提前swing；原生盾后立即反击同样校验并由Human拥有动画。换成近战武器本身不应成为挥刀请求，远处目标、隔墙目标和高压强制举盾期间不能主动起刀；压力解除后正常近身反击与原有前摇/冷却继续工作。

这是攻击起手门槛，不修改已经提交的Better Combat攻击完成逻辑，也不改盾牌减伤、受击动画或后台换弹。156项Python静态/API检查通过（新增动画前校验与调用方禁止提前挥手契约），Java编译和实际游戏表现未验证；本轮未构建或投放。


2026-10-11：PressureDefenseCycle为ShieldDefenseController私有的高压防御周期模块，集中管理实际举盾计时、破局观察与新伤害失败判断。所有恢复/近战/枪械调用仍询问shouldSuppressRecovery，普通盾牌请求和Adaptive DEFEND通过isPressureBreakAttempt让出控制权。阈值由ShieldPressureConfig统一提供；没有第二套恢复或导航执行器。


2026-10-11：弓弩统一射击节奏入口HumanArrowCombat，委托ArrowAttackCadence保存服务器真实时间门槛。BowAttack/CrossbowGoal和GuardedRangedCharge共享装填完成、等待与发射许可；Human/SpartanRangedCompat仅在实际插入弹射物后提交射击状态。正常/后台进度只推进一次，弓射后冷却与弩装填后等待不因Goal中断重置。枪械和三叉戟不接入此节奏。


2026-10-11：UnifiedConfig.Tier拆分bowDamage/crossbowDamage；HumanArrowCombat.isCrossbowProjectile统一读取弹射物发射来源（原生弩标记、本模组与斯巴达弹标记），HumanDamageScaling按类型选一次倍率，不依赖命中时主手。分阶配置独立bow_damage_multiplier/crossbow_damage_multiplier，旧共用项退役。


2026-10-11：HumanAttackCooldownPolicy统一分派直接人类近战与既有弹射物受伤冷却规则。LivingEntityProjectileCooldownMixin复用原Slice/单次标签查询，改调用此入口；不改原伤害源/目标计时/攻击调度。CreeperMeleeCommitment读取同一入口调整只读预测。细节见HIT_COOLDOWN.md。


## 2026-10-11 TaCZ换弹动画等比缩放（源码待构建）

主控/root独占main主工作树E:/MC/Create-Delight/HostileHumansUnified-维护/project；基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加全部既有未提交工作区，无并行编辑或新提交。新增TaczReloadVisualTimeline服务器视觉快照与TaczReloadPlayback纯播放策略；顺序接入TaczHumanReloadTransactions、HumanMobEntityData、TaczNpcAnimator、TaczNativeAnimation、ClientAnimationCompatibility。实际装填与弹药不受动画控制；公共接口仅增加临时快照get/set，主网络协议升级5避免新旧实体同步布局混用。

以原生数据估算总时长、首阶段实际倒计时修正倍率、收尾阶段校准终点；公开SpeedModifier仅作用于动作层，结束/取消淡出，切回武器或重新渲染按服务器进度恢复。未知脚本延长可保留末尾姿势，不退回固定长度动画或无限重播。接口/关键帧/时间线不可用时程序动作回退，未安装Player Animator也按时间线缩放程序手势。完整机制与限制见docs/TACZ_RELOAD_ANIMATION.md。

178项Python静态/API检查通过，6份默认附录核对通过；Java回归用例已补但未编译运行。未构建、实例更新、启动游戏、提交或推送，已安装仍为21:35测试JAR；本项及上一项投掷速度修正均尚未投放。游戏验收重点：空仓/战术/逐发换弹、配件修正、特殊脚本、后台换弹切回、重新渲染、多人同步和第一发开火。


## 2026-10-11 持枪误触发近战修正（源码待构建）

TaCZ近距离紧急开火准备曾把meleePrepTickCount清为0，但原生LivingEntityMelee.scheduleTickMelee在0时执行枪托攻击、-1才是不排队。因此这个攻击不经过Human.doHurtTarget，能够绕过已有持枪近战限制，造成单体伤害与击退。本次改为-1，保留清除疾跑和近战时间锁的原用途，不改变正常射速、装填或后台换弹。

防护分三层：既有Human普通近战与Better Combat延迟命中继续核验主手物理枪械身份；安装TaCZ时使用公开可取消的GunMeleeEvent阻止本模组人类启动枪托攻击（不影响玩家，独立于NPC枪械联动开关）；新增GunMeleeProtection在服务器LivingAttackEvent最高优先级拦截持枪人类的直接近战。包括原版mobAttack/noAggro/playerAttack及DamageSourceMix替换的human/human_with_name伤害类型；排除间接命中、弹射物、已识别枪伤和爆炸，不将荆棘/状态效果误认作近战。焦燃与TaCZ均通过GunSupport物理身份识别覆盖，关闭联动仍不能拿枪平A。切到主手近战武器后可以正常攻击，枪放副手不妨碍主手武器近战。

未来枪械联动应接入GunSupport的物理身份边界，不能仅依赖可开关的射击后端；若上游新增独立攻击入口或自定义直接近战伤害类型，仍应核对其公开取消接口和伤害分类，不能保证任意未知模组都自动覆盖。

主控/root顺序编辑main主工作树；基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加全部既有未提交修改，未建立并行编辑。修改TaczGunMechanics、TaczGunItemIdentity、HumanGunner；新增GunMeleeProtection，补GunCombatPolicyTest字节码回归和4项Python契约。182项Python静态/API检查通过（包括只读核对原生零计数触发路径和公开事件可取消）；Java回归尚未编译运行。未构建、同步、启动游戏、提交或推送；当前安装仍是21:35测试JAR，之前投掷初速度和换弹缩放修正也仍待构建。

待游戏验收：TaCZ贴身连续开枪不额外平A或枪托击退；焦燃同场景；两种联动分别关闭时主手枪仍不能近战；Better Combat安装/缺席；普通子弹与爆炸伤害保持；切近战后正常反击、切回继续装填；单装/双装/皆不装可选枪械模组。


## 2026-10-11 枪手主动进入优势射程（源码待构建）

核查确认两个停步入口：GunnerGoal.canUse原先以最大开火距离限制整个Goal，超距后射击与追击一起停用；CombatTerrainNavigation的原地射击回退只检查可开火，在安全否决或寻路失败记录存在时，会使仍在优势距离之外的枪手持续停步。

现在枪手Goal按有效目标、攻击许可和武器状态运行，不再以开火距离决定是否追击；硬开火距离在tick中瞄准/发射之前独立核验，超距只逼近、不发射并允许现有追击疾跑。地形原地射击回退增加GunRangePolicy.withinPreferredRange判定，只有处于对应类型优势距离带内才可以因此停止移动。仍在带外时沿用PursuitNavigation、移动目标路径刷新、地形绕路、寻路预算和安全检查；无安全路线时不强行越崖。射击可及范围、各枪型优势距离带、原地驻守、贴身保护追击边界、上岸与高压/低血生存控制不变，不新增第二个移动Goal或持续范围扫描。

适用TaCZ和焦燃的共同GunnerGoal。不能把本次修复理解为任何地形都能走通，或驻守士兵会无视命令远追。目标仍由既有目标选择和攻击模式授权，不增加索敌范围。

主控/root顺序编辑main主工作树，基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加全部既有未提交修改；共享文件GunnerGoal/GunRangePolicy由主控负责。涉及这两个生产文件、GunCombatPolicyTest和test_gunner_approach_contract.py；无配置项/网络/存档结构变更，无新提交。185项Python静态/API检查通过，补充Java优势距离边界与编译后字节码回归但未编译运行；未构建、实例同步或推送，游戏行为待验收。

待验收：手枪/霰弹枪/步枪/狙击枪远距离取得目标后连续逼近优势距离；障碍解除或绕路可达时不因旧失败记录持续站桩；硬射程外不发射；移动目标连续追击；原地驻守不追、贴身保护不越主人12格边界；水域上岸和高压防御仍有移动优先权。


## 2026-10-11 护卫拾取期间TaCZ对空气开枪（源码待构建）

用户确认TaCZ、主动攻击、贴身保护且允许拾取时，周围无敌人仍向四周开枪；是否始于最后一名敌人死亡尚不明确。只读核查发现拾取目标是独立ItemEntity，不直接写入战斗目标；普通开火与撤退反击分别持有目标，TaCZ最终operator.shoot之前缺少共用权限校验，也没有公开GunShootEvent处的目标复核。现有日志未保存该次目标UUID/状态，不能宣称已复现或确定唯一触发路径。

新增GunFireAuthorization作为普通射击、撤退反击、后端和TaCZ原生事件的统一只读开火许可。Control.shoot新增显式LivingEntity target参数，射击者、主手真实枪栈、黑名单许可、目标有效性、同维度/实际仍被服务器登记、视线、硬射程、阵营、攻击许可和当前护卫选择均必须有效。正在主动拾取、无目标、目标死亡/移除/卸载/旁观、目标与当前战斗目标不一致、护卫已换目标等状态禁止发射。焦燃原生AIGunEvent使用同一个显式目标，不再重新取可能不同的human.getTarget。TaCZ通过公开可取消GunShootEvent在服务器最高优先级再次拦截，取消发生在原生开枪通知之前；玩家不受影响。不增加射速或额外扫描，不停止后台换弹、不取消已发出的弹射物。GunnerGoal失效目标分支立即清除瞄准显示。

主控/root顺序编辑main主工作树，基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加全部既有未提交修改。范围：GunFireAuthorization、GunSupport/CompositeGunSupport、GunnerGoal/AdaptiveCombatGoal、TaczIntegration/ScgunsIntegration及接口回归测试。公共接口变更仅内部Control.shoot显式目标参数；配置/网络/存档不变。189项Python静态/API检查通过，Java字节码回归已补但未编译；未构建、实例同步、启动游戏、提交或推送，用户场景尚待游戏验收。

待验收：TaCZ主动攻击+贴身保护+拾取开关启用，击杀最后目标后正常拾取且不空放；无敌人持续闲置；目标卸载/死亡/切换、护卫更近威胁抢占；普通/撤退反击保持正常；焦燃同条件及无枪械联动启动。若仍出现枪声/火光，应区分真实弹药消耗和客户端表现，并记录目标/枪械与发生时刻后进一步定位。


## 2026-10-11 装备品质纳入真实剩余耐久（源码待构建）

旧equipmentScore的耐久因子为0.55+0.45×剩余比例，即将损坏时仍保留55%评分；inventoryValue还固定增加35，使破损的高阶装备持续有拾取价值。统一新增EquipmentCondition：正常可损坏装备的整体装备分按剩余耐久比例线性折损，原背包固定35加分也乘同一比例，属性分不重复乘算。剩余50%价值约按50%计，剩余10%约按10%计，零剩余价值为0；真正不可损坏或Unbreakable装备不降分，数值截断避免异常耐久造成负分或超满分。

主动搜索、原版贴近拾取、拾取提交和盾牌专用提交都拒收剩余耐久不超过10%的装备（EquipmentCondition.MIN_AUTOMATIC_PICKUP_PERCENT=10，当前为内部统一常量，未新增配置项）。装备范围沿用既有识别：护甲、近战工具/武器、弓弩、三叉戟、盾牌和可识别枪械；仅对采用物品耐久机制的物品折损，不把枪械弹量/能量当耐久。即使空装备栏的穿戴加分很高，也不能绕过残破拒收。高于门槛的装备继续比较品质、背包容量和补给预留。

背包内护甲优化与备用近战选择复用折损后的equipmentScore。盾牌拾取及达到3面上限后的替换改用同一inventoryValue，防止盾牌旧高底分绕过耐久折损；实际副手盾牌的稳定穿戴排序仍遵守此前用户约定，按最大耐久选择，真正无耐久且能格挡的盾最高，不按每次磨损来回换盾。食物/弹药/医疗品质与保护槽位不变。玩家手动放入的装备、现有库存和生成装备不被强制删除，菜单操作不经过自动拾取门槛。

主控/root顺序编辑main主工作树；基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加全部既有未提交修改。范围EquipmentCondition、HumanLootManager、ShieldPickupPolicy、LootScreeningTest及新增Python契约；公共配置/网络/存档结构不变。192项Python静态/API检查通过；Java物品级回归补充耐久全区间单调性、精确10%边界、满损/不可损坏和破钻石甲对完好铁甲比较，但未编译运行。未构建、实例同步、游戏启动、提交或推送。

待验收：满地破甲/破武器/破盾不捡；较高耐久的低阶装备胜过严重磨损高阶装备；已满背包按折损价值替换；盾牌到3面上限只用更好的一面替换；玩家手动超量及低耐久装备保留、食物药品保护不变。


## 2026-10-11 盾牌按短窗口累计损耗（源码待构建）

将“首击扣损、冷却期间丢弃后续伤害”替换为2 tick（0.1秒）累计结算。每面实际盾栈分别累计已承接伤害，窗口内不因弹丸多而漏计；窗口到期按`1 + floor(累计伤害 × 1.5)`调用一次原生hurtAndBreak，固定+1及向下取整均只做一次。8颗各4点的命中累计32点，对应49耐久。伤害和减伤立即结算，只有耐久最多延后2 tick。

主动普通格挡/主动TaCZ盾/枪手被动盾仍统一经ShieldDurability；保持既有伤害口径和减伤比例。TaCZ主动盾在原生穿甲拆分前登记一次，不重复登记两种伤害源；被动盾累计各部分实际最终伤害。耐久附魔在合并事务中正常判定，完全避免磨损时没有额外惩罚冷却；新窗口继续累计。不可损坏盾不强扣耐久。

服务器只处理有待结算记录的单位，不扫描弹丸或附近实体。换装备前、开启装备编辑前、序列化前、死亡/离开维度及服务器停止前结清旧盾债务；先移除队列再调用原生回调，防止递归重复扣除。盾碎只清理仍指向该盾的槽位，不清除替换盾或无关主手动作。刷新相同物品引用不提前结算。

主控/root独立顺序编辑main主工作树；基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d及全部既有未提交修改。涉及ShieldDurability、HumanGunner事件注册、Human装备/保存入口、HumanInventorySession、TaCZ说明及相关测试/默认注释。未新增配置键、网络包或存档格式。194项Python静态/API契约通过；Java数值边界回归已补，未编译执行。未构建、同步、游戏启动、提交或推送。

待游戏验收：霰弹枪多弹丸、连射跨窗口、TaCZ穿甲/非穿甲两段、焦燃及普通箭、耐久附魔、临界破盾、换盾/手动转移、死亡掉落和保存重启；核对总损耗、减伤与后台换弹不受意外影响。

2026-10-11：ScgunsThrowables.flightProfile集中对应公开工厂的原生重力/反弹/效果时机；原生Projectile.shoot提交求解后的发射速度与朝向。clusterAim在节流预检中为已有战斗目标选择局部敌群瞄准点，不调用setTarget，不拥有MOVE/LOOK；ScgunsSettings提供全局距离、初速与集群三个参数。详见SCORCHED_GUNS_COMPAT.md；源码待构建。

## 2026-10-11 远程弹道障碍检测（源码待构建）

远程感知视线与实际开火路径分开。原先眼睛到目标眼睛无遮挡便被用于开火/站位判断；箭矢的服务端出手点是眼睛下方0.1格，枪械瞄准躯干而非目标眼睛，都会出现“看得见，弹道被方块边缘挡住”。实际核对本机TaCZ/焦燃JAR：两者原生服务端子弹使用投掷者前后位置的平均值加眼高，不等于第三人称模型枪口；不能把画面枪口的位置直接当作碰撞起点。

新增RangedShotClearance作为共用服务端检查入口。枪手、弓手和弩手的开火条件与射击位置搜索使用同一几何检查；枪械最终许可和弓弩实际出手入口再复核。被挡时停止无效开火，交给原有受预算约束的射击换位/地形回退，不新建移动Goal；驻守、撤退及上岸的移动控制权保持原约束。普通与撤退枪械射击共用起点、瞄准点及预判，避免两处不同公式。

弓弩/斯巴达重弩复用HumanGunner既有低弧求解器，并沿名义弹道逐tick检查方块碰撞形状；三叉戟也检查既有补偿弹道。不是简单拿较低出手点到敌人拉直线，因此保留已有越过矮掩体的弹道空间。不会抽取随机散布、消耗库存、重置装填/拉弓或缩短攻击间隔。枪械后端经GunSupport提供瞄准点与检查所需速度/重力；焦燃使用原生公开配件、附魔速度/重力修正，检查用物理参数与原生负责瞄准分开，避免重复预测。TaCZ与焦燃可单独安装。

同一tick、同一实体/武器与双方位置/运动状态复用一次检查；候选位置沿用OptionalPathBudget。每条弹道至多60段，到目标平面截止，不检测目标背后的无关墙。没有新增范围扫描或客户端弹道计算。这里是名义轨道障碍检查，不承诺散布后每颗弹丸命中，也不把敌人后续变向、移动方块、特殊子弹制导/穿墙或水中阻力当作已完整仿真。原有眼睛感知视线仍作为发现/攻击前提。

主控/root顺序修改main主工作树，基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d，保留全部既有未提交修改。涉及RangedShotClearance、GunSupport/CompositeGunSupport/ScgunsIntegration、GunFireAuthorization/HumanArrowCombat、GunnerGoal/AdaptiveCombatGoal、BowAttack/CrossbowGoal、RangedFiringPosition和共用箭矢求解器可见性。201项Python静态/API契约检查通过，核对了本机原生箭矢出手偏移及焦燃瞄准/起点字节码；未运行Java编译/构建、未更新实例、未启动游戏、未提交推送。

待游戏验收：台阶/半砖/栅栏/窗沿和高低差处能看见头部但躯干或出手位置被挡；正常换位后恢复开火；弓弩可越过矮障碍且射速/装填保持；TaCZ/焦燃普通及撤退射击、重弩/三叉戟；驻守不越界、上岸与撤退不抢移动、多名士兵对遮挡目标的性能。


枪手和弓弩手共用近身试射、真实掉血回退与完整路径8格边界。弓弩恢复调用RangedWeaponCustody，枪械恢复调用GunCustody；慢速弩试射时长覆盖装填及既有待射时间。水中/上岸/攀爬/地形近战不强行试弓；无备用近战时不取消拉弦。目标切换与手动装备清理旧状态，不重写射击冷却或另造攻击入口。


## 焦燃机枪姿势数据边界

ScgunsMachineGunPose通过五个原生MiniGun姿势类的protected扩展工厂读取AimPose/LimbPose公开数据，缓存后按俯仰插值；不反射私有字段，不调用Player回调或本地近战/装填状态。ScgunsNpcAnimator仅暴露float bodyOffset给HumanPlayerModel，核心接口不出现焦燃类型；原生枪械绘制前在同一矩阵应用物品变换，火光与附件沿原绘制入口。缺失依赖/原生渲染失败沿既有回退。tools/audit_scguns_pose_compatibility.py只读检查扩展类、protected工厂和公开数据方法；不能代替Java编译或游戏视觉验收。


## 导航诊断接口

- `NavigationDebugService.controls` 仅表示固定点测试的临时移动租约；Human阻止新增攻击目标，LivingTick保留成长/既有恢复效果与临时单位寿命，但暂停命令、传送脱困及战斗写入。TestGoal复用原导航/安全/配额，不修改原命令或归属，结束释放而不恢复过时仇恨。
- `watching`、`searched`、`candidate`、`budget`、`blocked` 为只观察入口，未佩戴头盔时不采集候选；候选通过评分不等于路线安装。`HumanNavigation`提供地面请求/安装，四类选点器提供评估，原有配额数值不变。
- `NavigationDebugSnapshot`只含几何与服务器权威数值；`NavigationDebugFrame`含最多8实体及焦点，感知扩展帧仅S2C、每观察者5tick、每实体路径256/最新候选16（服务器缓存48）。`NavigationObserverSelection`维持稳定槽位与唯一服务端焦点；指针与F8都调用同一个cycle，客户端无本地焦点覆盖。导航通道5保留空负载C2S轮换请求，经头盔/权限/会话校验，只变更观察选择，既有S2C帧布局不变；测试租约校验观察成员身份，切换焦点不会取消其他任务。客户端复用唯一原生BufferBuilder，每实体及时提交，先以double减相机坐标再绘制。客户端限定头盔、维度、实体UUID及数据新鲜度，不反算伤害或寻路。使用边界见[NAVIGATION_DEBUG](NAVIGATION_DEBUG.md)。


## 拾取战斗交接与已越过路点

Human.setTarget在super完成后读取实际getTarget结果，只有实际对象变化才更新实体自有PickupCombatWindow（20tick、不存档）。获得新目标通过SoldierPickupPolicy中断ValuableItemPickupGoal并停止对应WrappedGoal；只清拾取自有Path或其连续性修复、局部输入，已替换路线不取消。普通拾取各入口共用canCollectNow，start再次复核；战斗顺路补给独立。

HumanNavigation.tick在原地形复核前尝试advancePassedCombatWaypoint：连续tick/同Path/同索引、跳跃或刚落地、存活战斗目标、平直相邻同高WALKABLE，PassedWaypointPolicy确认前向窄走廊跨越，地面扫掠noCollision、safeStep、safeTransition及命令许可后仅advance一次。水域、撤退、受击、门梯/活板门、转角、高低差排除。不换Path、不重算、不改坐标/速度或伤害。纯策略回归由CombatTerrainPolicyTest调用NavigationHandoffPolicyTest；真实交接和物理移动仍需游戏验收。

## 共同感知与目标位置准入

`HumanPerception`拥有服务端逐目标知识状态；每份Track的`RecognitionEvidence`管理实际目视证据/负检测起点，`BodyVisibilitySamples`提供27个AABB样本；先查缓存身体比例点/眼部，再申请独立目标补查批次（默认9），所有射线共同计入原维度/服务器池，成功后取消下轮预留。可见点更新LOOK与缓存，未查完不得产生负证据。最多两份识别并行，LOOK主焦点独立且唯一。未采样不启动遮挡计时；近场由NearVisionPolicy按身体柱体/水平角判断，柱体外保留配置插值视锥；头盔使用服务器base/top几何对，end=(headingX,halfAngle,headingZ)，不客户端重算配置。`canTarget`为确认且动态有效的攻击准入，`knownPosition`只返回合法动态/最后位置。所有目标赋值、威胁恢复、护卫、射击与战术导航必须消费这条边界，不能从线索UUID恢复隐藏实体坐标。`HumanAwareness`在平静/警觉均采样和投递脚步位置线索；运动采样半径取walk/run/swim三者最大值，再按事件各自半径投递。`HumanPerception.reactToClue`独立提交有限LOOK，不依赖调查MOVE启动，驻守不离岗；新线索/位置改动与回看冷却控制重复请求，仍不能抢视觉首次识别/战斗/撤退。`HumanAttention`在tick末仲裁朝向：普通导航身体由MoveControl意图确定，头部单侧默认60°；普通观察不申请Goal LOOK，不能阻止战斗/移动。真实受击由IncomingHitResponseGoal短暂自卫回看，移交后恢复原动作。不可攻击来源的可靠受击方向仍可获得短暂匿名观察，不授予攻击许可；盾防、近战出手及射击请求优先于普通观察；`PerceptionInvestigationGoal`只占自己的MOVE路径并沿用安全/命令/预算。

`BeaconReportingService`与`BeaconReportPolicy`独立负责同玩家/维度报告SavedData，不授予目视知识；`HumanSignalItem`仅成功召唤后续共享截止。`PerceptionConfig`集中配置，`PerceptionPolicy`集中时序/识别/几何规则。`PerceptionDebugSnapshot/Codec`随观察帧发送，`NavigationDebugViewPolicy`只筛选展示。详见[感知源码地图](ENEMY_AWARENESS.md)。

## 视觉、移动与战斗如何交接

视觉识别、观察请求、主攻击目标与目标记忆是四个不同状态。最多两个目标独立累计目视证据；已确认目标分别保存动态期限。人类只集中攻击一个主目标，识别其他目标不暂停现有战斗，杀死主目标后直接从仍有动态位置且许可有效的记录中接替。

| 情境 | 身体与移动 | 头部与识别 |
|---|---|---|
| 普通导航、跟随、调查、追击 | 移动控制要求的方向决定身体朝向 | 默认单侧最多60°，并受实体原有上限限制；超出范围的观察请求不能拉转身体 |
| 闲置驻足 | 可以平滑转身观察 | 自然环顾和主识别请求提供看向位置 |
| 原地瞄准、实际近战出手、举盾或明确战术侧移 | 使用实际战斗动作的朝向规则 | 盾防、近战出手和射击请求优先于普通观察，不用头部请求冒充攻击方向 |
| 真实受击，但未在处理6格内已确认敌人 | 可以短暂暂停普通移动并回看来向，默认最多20tick，转到来向后提前结束 | 来袭观察可中断远处普通识别；未知来源仍需目视确认，不读取隐藏射手坐标 |
| 逃生、岩浆、爆炸等紧急情况 | 保留生存任务与路线 | 普通观察不能抢占；受击回看也不能阻止必要逃生 |

主目标选择由共同入口完成，贴身保护和增强战术只提供角色或压力建议。普通建议默认至少保持当前主目标20tick；已确认近处紧急威胁、原有拥挤压力判断与多来源集中处理可例外。原有60tick多来源压力窗口、80tick集中处理及近敌优先条件继续生效。驻守保留目标与返岗约束、主人攻击许可、阵营、和平和菜单/固定点测试等限制仍须分别通过，记忆不自动续攻击许可。

已确认目标的动态位置默认600tick（30秒）。目视或动态期内该来源真实直接伤害刷新其自己的期限；转头、切换主目标和识别其他敌人不删除记录。到期后冻结最后合法位置，保留600tick（30秒）的位置线索；旧敌身份默认2400tick（120秒）用于加快重认。声音、求援、信标及其他敌人的伤害均不能刷新动态期限。改变主人清理旧知识；普通行动命令改变取消旧动作和线索，但保留仍合法的身份记录，当前能否攻击重新按命令判断。

两个识别名额使用独立轮转游标，即使每轮只允许1个快速采样，也不会永远只检查第一个。已确认主目标定期取得复核机会；新增目标与主目标维护交错时，按该目标安排的检测周期累计证据。真实连续负检测达到40tick（2秒）清零，配额等待不算负检测或免费证据。真实受击可暂时让较远识别让出一个名额；被暂停的进度保留，恢复后继续需要实际有效目视。

配置入口：`global.json → perception`；`memory.dynamic_ticks=600`、`recognition.break_ticks=40`、`attention.moving_head_angle=60`、`attention.hit_response_ticks=20`、`selection.minimum_hold_ticks=20`。时间均为服务器tick（20tick约1秒），这是模组默认值；已有实例自定义值继续按其配置执行。

头盔分别显示主攻击目标、选择原因、两个识别对象及暂停进度、观察请求与实际朝向负责人、身体/头部角度、各目标动态剩余与攻击许可。几何视野按实际头部yaw与俯仰展示，不能把请求看向某处等同于已经实际朝向它。导航、身体与当前观察请求分别判断，实际射击仍须弹药、许可、朝向及射界通过。

## 枪手直接侧移与朝向边界

`GunnerGoal.strafeAround`在已有射界、有效开火距离内且环境/生存分支不接管时执行直接移动调整距离与侧移，不调用findLateralPath或申请BasicNavigationBudget。`RangedTerrainCombat.strafeFacingTarget`要求目标合法、无暂停/逃生/站定/岸边接管和活动导航；按目标水平朝向复用tryStrafe的5组有界方向、1.25格安全前瞻及碰撞检查，并加SoldierOrder.allowsCombatPosition。安全输入提交前标明本tick战术朝向，防止普通导航canAim误拒枪射击；HumanMoveControl与原生枪械发射边界继续沿用。弓弩的strafe签名与行为保持不变，同用tryStrafe而不采用枪手额外转向或命令策略。停止局部侧移不取消CombatTerrainNavigation自有路线；超射程追击、遮挡射位、必要岸边状态与真正避险仍通过各自导航。优势距离带只调节前后输入，不是切回导航的条件；32tick直接换向，没有12tick固定空档。进入直接段时取消旧路径/扩展追击请求/短步补救，并用零STRAFE覆盖旧MoveControl MOVE_TO后提交当tick安全输入，无法移动也保持独立瞄准。

## 友善生物背景过滤与防御例外

HumanAwareness.passiveCreature统一分类：排除玩家、人类、Enemy/MONSTER后，其余LivingEntity作为背景；采样、emit、flush和HumanPerception声音候选前过滤。HumanPerception.allowsCreatureResponse控制视觉候选、攻击准入、动态/最后位置读取与观察继续；allowsCreatureClue在alert之前拒绝普通动物线索，失效线索标记完成让活动调查释放。Track.defenseUntil仅由本人真实非周期伤害或既有保护主人事件记录，不授予marked/liveUntil；尚未确认的记录按familiar_ticks到期，合法确认后沿原动态/旧敌期限。HumanRelations.followOwnerAssault提前忽略背景生物；assignGuardTarget显式区分defendingOwner，三参数ownerClue仅由已通过原保护/模式许可的服务端事件调用，二参数接口保留且不授予防御例外。普通动物脚步始终不刷新防御记录，完整生物类型不建立永久仇恨。

### 主动进攻射击窗口

弓/弩/枪统一调用RangedFiringPosition.findAttackWindowPath；防守分离保留findVisiblePath硬距约束。RangedWindowCandidates生成墙面开口投影、近场格点及轮转远场候选，loaded-only、支撑/身体碰撞/命令预筛；最多96保留、192次站位检查（开口面最多75块）。生成前取得Optional FIRING_LANE额度，VISIBLE_WAITING与会话同为pending；最大4候选/批，完整安全路径+实际终点名义弹道通过即交付当前批次最佳项。

RangedShotClearance以canTarget为已确认动态权限边界，canShoot/fromPosition共同检查原武器名义弹道，不附加眼部LOS否决；不改变瞄准点、实际散布、发射、弹药或原生物理。VisualOcclusionMemory转头仅解除完成的退避，不清空未变化射线几何的部分覆盖，sampleMask负责FOV集合变化；身体单批默认9、拥挤5、团战3，完整逻辑样本仍为27。

## 可见箭矢匿名防御

ProjectileReactionGoal与AdaptiveCombatGoal同优先级-9；已有合法主目标由战术Goal处理，无主目标时独立反应只占短MOVE/LOOK。ShieldDefenseController.findIncomingArrow仍负责32候选/3tick扫描、12tick轨迹预测和射程/友军/反击过滤，新增visibleArrow检查当前箭体遮挡；insideReactionView采用头部yaw的前向水平180°，不因俯仰收窄。

匿名入口allows/begin/continues/tick/endProjectileReaction仅租用同一控制器的PROJECTILE盾牌所有权；不使用射手进行canTarget/导航/攻击。期限由首次预计撞击确定，不被连射无限延长，结束释放仅自己的租约并设置原有反击窗口。原战斗tickCombat也只朝可见箭体，而非读取owner位置。未知来袭不会调用notice/标记/选敌，实际命中仍经HumanPerception.hurt取得警觉及来向。

## 本能高压保护与方向租约

recordPressureDamage分开两条输入：CloseRangedTactics仍仅接收isValidThreat的已知敌人；RecoveryPressureWindow/PressureDefenseCycle接收真实可盾防的生物/弹丸攻击生命损失，不以canTarget或攻击许可过滤。已被取消的友军伤害不会有实际HP损失；若确实扣血，也可触发防御而不生成仇恨。环境及BYPASSES_SHIELD不计入。方向只取移动直接弹丸的负速度或真实接触者相对方向，不读取远程owner坐标。

压力已触发但无合法主目标时PressureReactionGoal（-9）复用ShieldDefenseController的PRESSURE所有权；既有战术DEFEND在tickCombat中先执行高压稳定朝向分支，避免后续普通目标/箭体朝向覆盖。受击回看与匿名箭反应在高压下让出控制；已有目标合法时交还原战术掩体和防御移动。原恢复交接、枪械备用近战、阈值与PressureDefenseCycle保持。

PressureGuardFacing只保存角度：首次可靠输入立即平滑朝正面，单tick≤12°；同方向30°内变化继续跟随，跨方向切换至少间隔12tick。recordPressureDirection由真实扣血和有效ShieldBlock共同调用，格挡也更新方向但不冒充实际扣血。Human.markShieldFacingDirection与HumanAttention.pressureFacing传递同tick方向租约，HumanAttention的pressure请求携带显式pressure标志，在最终仲裁中不让route yaw覆盖盾面；HumanMoveControl仍复用moveWhileShieldFacing保留实际防御移动，保护实际盾面而非只转头。无来向时不猜射手，保持当前方向。该策略不是360°防护，多面攻击仍有未覆盖角度。

## 遮挡负缓存与当前几何证明

VisualOcclusionMemory.prepare接收当前FOV身体点及快速眼部/上次可见点、mask。转头按mask覆盖复核，不按8°盲目释放。完整负缓存有任意有效眼部/目标中心/目标眼位/尺寸变化、新部位或4tick证明过期时复核；实际blockState改变直接释放。partial重置仍保留occlusion_move_reset；negative要求当前姿态testedPoints完整或当前证明通过，不使用跨移动的旧sweep开启长冷却。

CachedOcclusionProof只接受已实际命中且以CollisionContext.of(h)确认完整碰撞的方块：最多8立方体×28当前点；缩进内部并匹配真实LOS端点微偏移，不能用形状bounds代替完整实体形状。支持证明所有当前点遮挡后10/20/40tick退避，不规则碰撞真实复查不超过首次间隔（默认10）。证明至少4tick复核一次，动点提前；可见、开窗、proof失败按原LOS预算恢复，判定不足不记为遮挡。

PerceptionBudget复用独立occlusion池，默认每人4/维度32/全服64份每tick，额度不足取消可选证明请求并退回真实LOS，不保留过期遮挡、不给额外射线。证明不写Ray或刷新目标位置；拒绝已确认负缓存时仅miss证据/取消补查，调试budget新增遮挡几何复核。新受击回看前4tick或实际hurtTime保留紧急复查，长focusHurt不永久绕过冷却。


## 射位保持与失位返回

RangedFiringMemory是弓/弩/枪原Goal调用的短期几何状态，不是新Goal或锁敌器。observe接收已计算名义射界与武器有效射程；WeakHashMap值仅含目标UUID、维度键、武器Item、脚位/进展、计时和Path。当前可用射界且站稳时记住一处射位200tick；切目标/维度/武器类型或权限失效清除，不保存隐藏实体坐标。

RangedTerrainCombat.tryStrafe在普通可射击输入上通过keepsFiringFloor调用HumanTerrainSafety.maintainsFiringFloor：最多5输入×中终点×中心四角，支撑下降超过0.6拒绝；每点仅复用原形状查询的当前附近3～4层，不使用全跌落深度。半砖仍允许；策略拒绝不报告地形失败，通用HumanFallPolicy、追击与必要退开不变。

recover只在失去射界时、原普通地形规划之前尝试，已有地形绕路和窗口搜索仍优先。返回≤16格旧点、40tick失败重试、最多2次、路线100tick/无进展40tick，合法完整安全路径及真实终点碰撞/射程/命令/名义弹道均需复核。每次探测与10tick复查共用FIRING_LANE 1份，额度等待不杀掉已建立路线，原最终旅行安全继续有效。只停止MovementContinuity确认属于自己的Path，外部接管不停止新路线。恢复射界即释放旧返回；近身≤4格、命令/测试/高压/避险/上岸等让位。不发布仇恨、不读隐藏射手、不瞬移。
