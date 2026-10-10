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
