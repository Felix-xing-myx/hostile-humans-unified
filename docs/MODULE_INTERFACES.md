# 行为模块维护入口

本记录覆盖本轮审查的恢复、武器分类、伤害、举盾、成长、装备生成和导航主调用链。接口统一的是规则与状态归属，不把不同职责集中成一个全能控制器；表中的入口是维护入口，不代表新增Java interface类型。

| 职责 | 维护入口 | 调用约束 |
| --- | --- | --- |
| 恢复用品选择、使用与背包托管 | `RecoverySupplies` | 各恢复Goal申请同一UseSession，不自行扣物品或换回副手 |
| 恢复效果推进 | `RecoverySupplies.tickEffects` | 每个服务器LivingTick一次，在菜单编辑锁之前；内部依次推进再生、First Aid、食物，不改装备 |
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
| 导航与地形 | `NavigationSupport`、`CombatTerrain`、两类寻路预算 | 基础追击与可选绕路预算保留各自职责，防摔/命令/水面调度继续优先，不增加竞争Goal |

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

RangedMeleeCycle只累计实际运行的近战tick，默认80 tick后在无前摇/挥击的安全边界开始找射界；复用RangedFiringPosition的增量搜索与共享预算，等待期间继续普通近战。合格路线必须完整可达、安全、沿路服从士兵活动范围，且终点有射界并能增大距离。先成功安装新路才释放旧追击元数据；已安装路线不按每tick随机重选，10 tick核验目标位移与路线安全，停滞20 tick或脱离80 tick则回归近战。搜索最多40 tick，失败后重新进行近战周期再尝试。

武器仍由RangedWeaponCustody独占托管，至少六格且有射界时尝试原入口归还，CloseDefenseHold保护未完成攻击。举盾或生存Goal打断时只释放本Goal所属脱离路线，不清除仇恨、不停止接管方路线；原地驻守和水中/上岸不主动脱离。常规近战累计时间在短暂举盾后保留，目标或实际武器改变才重置会话。

## 盾牌损耗与破盾

`ShieldDurability`统一主动格挡与枪手副手盾的损耗事务：服务器按`1 + floor(damage * 1.5)`调用ItemStack.hurtAndBreak，保留原生耐久附魔及物品钩子。每个人类共用4 tick（0.2秒）损耗冷却，换手/换盾不绕过；根据调用前后的耐久或数量确认实际损耗，完全免损不进入冷却。破损副手盾只结束该手正在进行的物品使用，不中断主手枪械换弹。

调用者继续提供现有阶段的伤害：原生活动格挡提供被挡伤害，TaCZ主动格挡提供减伤前枪伤，枪手被动盾提供护甲等结算后、20%减伤前的LivingDamageEvent金额。此次统一公式，不迁移伤害事件阶段。损耗冷却不取消减伤或格挡反馈。

主动破盾复用Human.blockUsingShield与ItemStack.canDisableShield，不硬编码斧头类型；成功后shieldCoolDown至少100 tick。Human.startUsingItem在所有主动举盾入口执行禁用检查，弓弩射击不缩短已有禁用。GunnerShieldProtection不读取此冷却，主手枪＋副手盾的被动减伤独立，副手盾损坏/移除后按既有装备检查失效。

## 普通战斗进食与反击

`RecoverySupplies.isEatingFood` 只认可受统一物品托管管理、正在副手使用的食物，不包含药品、盾牌或完成后的归还等待。`CombatEatingMovementGoal` 在普通武器控制器之前持有 MOVE/LOOK，复用 `MeleeAttackGoal.tickAttackOnly` 的攻击冷却、Better Combat 前摇与伤害入口；近处经 GunCustody/RangedWeaponCustody 精确交换主手备用近战武器，副手食物继续使用。无备用近战武器时不强制裸手覆盖远程物品。

拉距每12 tick至多尝试一个共享预算内的路径，保留仍安全有效的路线；候选位置必须符合 SoldierOrder，完整路线经 RetreatPathSafety 检查。跳跃复用分阶 combatJump 参数与 HumanTerrainSafety，不在待命中前摇中起跳。PlayerLikeMovementGoal 和自适应普通侧移/追击阶段让出控制权；真正撤退、防御及紧急脱险继续优先，不重定义低血量策略。水中/上岸期间不接管移动，原地驻守不拉距。
