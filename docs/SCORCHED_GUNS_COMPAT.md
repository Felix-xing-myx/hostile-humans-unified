# Scorched Guns 可选联动框架

2026-10-11已使用Java17完成离线build lootScreeningTest（52任务）及117项Python检查，并备份同步Forge 47.4.21测试实例。正式编译、配置导入及可选依赖缺席链接检查通过；未启动游戏，枪械、医疗、投掷物与袭击仍待游戏内验收。

## 依据与依赖边界

本机只读研究对象：ScorchedGuns-0.5.5-1.20.1.jar，SHA-256：
`f339dd8444eaf4623449cd11db092beb1f02f14f5d03045a0394070b0ea50170`。

以该JAR的公开类型、方法描述符及字节码为依据，未使用私有反射、新Mixin、假玩家或第二套移动目标。运行依赖可选，声明范围为0.5.5至0.6之前；该范围不是对所有未来版本的行为保证。缺席时不得加载可选API桥，已有TaCZ和First Aid独立工作。

构建时额外显式提供 `-PscgunsApiJar=<只读JAR完整路径>`，连同已有四份compileOnly API参数一起引用。API JAR不打入发行包，不复制游戏实例。

| 职责 | 接入方式 | 状态归属 |
| --- | --- | --- |
| 武器识别与参数 | GunItem.getModifiedGun、Gun、GunModifierHelper | 原生武器/配件定义 |
| 射击与弹道 | AIGunEvent.performGunAttack | 原生投射物、散布、效果、预测 |
| 战术与移动 | GunSupport → CompositeGunSupport → GunnerGoal | HHU既有射击、撤退、近战租约、导航 |
| 能量 | ForgeCapabilities.ENERGY / IEnergyStorage | 原生能力；不直接写Energy标签 |
| 普通弹药 | Gun.getAmmoCount；隔离AmmoCount写入 | 0.5.5公开API无计数setter，沿用其原生NPC格式 |
| 医疗 | 原生HealingBandageItem识别 + Forge使用事件 | MedicalSupplies / RecoverySupplies统一库存托管 |
| 投掷物 | 原生物品公开create工厂 | 原生实体拥有引爆、持续效果和归属 |
| 袭击 | RaidFlareEntity、RaidConfig、RaidManager | 原生波次、装备、保存及奖励 |
| 护甲 | 既有装备池追加导入与ArmorItem属性 | 各阶池及玩家自定义权重 |
| 友军命中 | 可取消GunProjectileHitEvent | 原生伤害/附加效果前取消；既有关系判断 |

## 枪械与弹匣

只安装Scorched Guns也能使用统一枪手和合成通用弹匣。普通弹药枪与可充放电能量枪均计入枪械目录；按原生武器类型配置每次装填的弹匣费用。出生首仓直接填满，不另扣弹匣。

- 普通枪打空后换弹；能量枪不足一次射击所需能量后补能。闲置时均只在容量严格低于25%时补满。
- 装填开始时扣费一次，按原生换弹时长及装填速度计时；到期填满弹药或通过原生能量能力充满。全局关闭弹药消耗时免弹匣费用，枪内资源仍正常推进与补充。
- 以枪上的稳定事务UUID关联人类保存的剩余加载tick。临时近战期间继续计时，回枪后继承进度；该枪离开其主手、副手及背包后作废事务，不填满替换枪、不退费。
- 原生射速、连发、投射物和武器效果由公开NPC接口推进。没有移植玩家持续按键等专属操作；特殊能量/持续束武器需逐件游戏验收。
- Scorched Guns枪伤在原生NPC难度及伤害系数之后，乘HHU专用联动倍率、各阶枪伤及现有培养枪伤；不再进入普通近战/箭矢增伤。不改变玩家枪伤。

`scguns.ai_accuracy_modifier` 越大越准。原生NPC额外误差锥角为 `min(25°, 5 × 难度因子 / 精度)`，与武器/配件自己的散布分别计算。HHU战斗经验收缩只作用于该AI误差，不再次叠加TaCZ角度散布；武器固有散布继续通过Scorched Guns原生枪械数据包修改。

TaCZ专用动画只接受TaCZ枪。Scorched Guns使用基础双手持枪姿势和原生物品渲染，未仿造其玩家动画或声称完整关键帧换弹。

## 配置与物资

出生配枪继续使用既有枪械目录、白名单及分阶类型权重；历史键名 `tacz` 下的共用选枪规则和各阶 `allow_tacz_firearms` 当前同时管理两种后端，`tacz.enabled` 仅控制TaCZ后端，`scguns.enabled` 仅控制Scorched Guns后端。Scguns原生lmg/heavy及能量类型分别映射到现有mg/rpg/rifle类型池，费用仍按原生类型独立配置。

全局文件新增 `scguns`，按开关、枪械精度/伤害、弹匣类型费用、医疗、投掷物、信号弹分组；启动/重启生效。医疗和投掷物不自动加入出生物资，可放入士兵背包或配置到已有物资池。

四种原生医疗用品默认值如下，全部可修改或删除对应条目。只改变HHU人类，玩家沿用原生物品效果。使用时间、治疗量、共同冷却、品质、附加效果ID/等级/时间均开放。

| 原生ID后缀 | 治疗生命点 | 使用tick | 共用冷却tick | 品质 | 附加效果 |
| --- | ---: | ---: | ---: | ---: | --- |
| basic_poultice | 8 | 32 | 100 | 65 | 无 |
| honey_sulfur_poultice | 16 | 32 | 200 | 70 | 再生I，200 tick |
| enchanted_bandage | 24 | 32 | 200 | 80 | 再生II，200 tick；伤害吸收I，800 tick |
| dragon_salve | 32 | 32 | 400 | 90 | 再生II，400 tick；伤害吸收I，1400 tick |

治疗为完成敷用时恢复，持续效果由效果系统推进，不套用First Aid分次疗程。四种药共用冷却：无再生时使用shared_cooldown_ticks基础值（默认100），有再生则自动以配置的再生持续时间为冷却，多条再生取最长。已有再生时不重复使用带再生的药品；伤害吸收不阻止再用，也不延长冷却；效果提前解除不绕过剩余冷却。出生默认值调整不覆盖玩家已写入的自定义治疗量、时间与品质。继承最后一份恢复用品保护，原生已消费不重复扣除。原生撕裂效果在治疗完成后移除。

投掷物从真实背包扣除，复用当前合法目标，不新增导航或目标选择器。默认白名单为grenade、stun_grenade、nail_bomb；工厂另支持gas_grenade、choke_bomb、molotov_cocktail、hellfire_bomb、swarm_bomb及beacon_grenade类型，以实际注册ID加入白名单。投掷间隔、距离、引信、友军排除半径和品质可配置。失败检查也限频，检查弹道与落点附近友军。

原生气体/燃烧/蜂群/信标范围效果可能持续、影响移动中的友军或破坏地形，未全局替换其伤害规则。默认关闭这些类型的自动使用；显式加入白名单前应验证。取消原生子弹友军命中发生在效果之前，但不是替换全部射线候选的保证，不能将它表述为所有场景均无阻碍穿透。

护甲受既有全局自动导入开关及各阶 `equipment_loadout.automatic_scguns_armor` 控制。普通护甲名单集中于defaults/equipment_integrations.json，与沉浸式盔甲共用追加导入入口。流浪者scrap/redcoat及ridgetop头盔组合；一阶scrap/anthralite/cog_knight/redcoat及iron_mask/brass_mask头盔组合；二阶treated_brass/adrien；三阶diamond_steel。覆盖0.5.5注册的七种普通套装与三件独立头盔，共31件普通护甲。外骨骼四件套、核心和扩展模块不在自动导入表中。仅追加已注册的完整套装，保留自定义池与权重，显式空池不补回。原生仅针对玩家的外骨骼模块、面罩与特殊护甲事件不自动复制到人类。

## 玩家袭击信号弹

新增 `hostile_humans_unified:scguns_signal_flare`，由玩家发射原生RaidFlareEntity，默认使用原生copper袭击；不是HHU人类援军，不由士兵投掷。配置raid_id必须同时有原生raid与flare定义。检查维度、和平难度和已有/待触发袭击，接受发射后才消费物品并启动玩家冷却。

当前复用已有信号弹图标，通过创造栏/命令获取，尚无合成配方；制作成本待后续确定。实际袭击仍可能被原生机制或其他事件拒绝，发射成功不保证必然出现波次。

## 验证与验收边界

源码契约、真实JAR公开签名、Java语法、JSON/TOML及差异检查可在不编译情况下进行。它们不替代类型编译、可选模组缺席链接测试、客户端/专服启动或实战。

本次已执行构建检查及可选依赖缺席链接用例（472个核心类）。游戏重点检查：Scguns单独及与TaCZ同装、普通/能量/连发/特殊枪、弹匣费用与临时近战后台装填、跨卸载计时、出生物资及自定义池、四种医疗消费与中断、友军/雇主/PVP、持枪盾减伤、投掷物误伤与地形、原生袭击多人并发/保存/结束、客户端动画、专服及多士兵负载。

### 独立枪伤配置

四阶文件各自的 `scguns.damage_multiplier` 默认1.0，仅控制Scorched Guns；`tacz.damage_multiplier` 默认0.5，仅控制TaCZ。Scorched Guns继续叠乘原生NPC难度系数、全局 `scguns.npc_damage_multiplier` 以及共用战斗经验枪伤加成（默认满经验额外10%）。

Scorched Guns不使用四阶 `weapon_spread_degrees.firearms`（TaCZ专用），保留原生武器/配件散布与NPC精度入口。共用成长配置的经验散布缩放继续应用于原生NPC瞄准误差，默认每5个百分点缩小5%、最低75%；不重复缩放武器固有散布。

### NPC持握、背负盾牌和枪口火光

- 客户端入口ScgunsNpcAnimator，依赖隔离在ScgunsNativePose。读取原生GunItem.getModifiedGun(stack).getGeneral().getGripType(stack)，不写死物品ID、不伪造Player、不读取本地玩家输入。双枪、单手、机枪及火箭筒做NPC姿势适配；其他双手和未知类型回退普通双手持枪。双枪原生模型已含两把枪，仅渲染一次，不复制实体或装备。手臂/袖子随士兵自身朝向，特殊姿势不是完整复制玩家换弹/近战动画。
- 所有本模组枪手（TaCZ及焦燃）主手持枪、副手为已识别盾牌时，显示于躯干背部并倾斜12度，左右惯用手镜像；蹲伏随躯干，原版鞘翅额外外移。使用ItemDisplayContext.NONE避开手持/物品框额外变换；自定义大型盾牌和其他背部附件仍需游戏内检查。切回非枪械自然恢复手持，不改副手库存、防护、耐久或服务器判定。
- 原生GunItemStackRenderer会传Minecraft.player；改为调用接受LivingEntity的GunRenderingHandler.renderWeapon(human, ...)，枪体、配件、原生枪口坐标/固有模型变换、火光共用同一矩阵。焦燃EntityMuzzleFlashHandler还在RenderLivingEvent.Post估算一次NPC世界坐标火光，新增仅客户端、仅Human、仅本次附着渲染成功时取消的窄Mixin，避免重复/错位；OptionalMixinPlugin读取目标方法完整签名，不存在或变化时跳过并警告，保留上游回退。不取消Forge事件，不影响玩家/原生枪手。
- 原生NPC射击提供火光包，但不提供玩家双枪射击计数；利用每次接收的原生火光令牌更新DualWieldShotTracker，使原生备用枪口偏移可用于人类。同一包多次渲染不重复计数；客户端实体卸载清理，未新增战斗网络包或逐tick扫描。
- 静态/原生公开签名检查已通过；尚未构建或游戏内验证。需要验收双枪、普通步枪、机枪、火箭筒；水平/俯仰射击及移动、蹲伏、左右手；消音器/枪口配件、双枪左右火光、不同盾牌/鞘翅和武器切换。原生枪包自身错误的枪口元数据不在本次自动修正范围。
