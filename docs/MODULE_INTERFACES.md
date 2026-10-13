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
