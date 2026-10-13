# Scorched Guns 可选联动

3.8.0发布范围包含本页声音、换弹、物品与投掷物改进；发布验证和部署结果见docs/releases/3.8.0-release.md，历史测试批次见WORKSTREAMS.md。

## 依据与依赖边界

本机只读研究对象：ScorchedGuns-0.5.5-1.20.1.jar，SHA-256：
`f339dd8444eaf4623449cd11db092beb1f02f14f5d03045a0394070b0ea50170`。

以该JAR的公开类型、方法描述符及字节码为依据，未使用私有反射、假玩家或第二套移动目标。枪械/医疗服务端框架没有新增Mixin；后续客户端附着火光使用精确签名门控的窄Mixin，详见末节。运行依赖可选，声明范围为0.5.5至0.6之前；该范围不是对所有未来版本的行为保证。缺席时不得加载可选API桥，已有TaCZ和First Aid独立工作。

构建时显式提供 `-PscgunsApiJar=<只读JAR完整路径>` 与 `-PframeworkApiJar=<Framework只读JAR完整路径>`，连同已有四份compileOnly API参数一起引用。Framework是焦燃原生网络的公开API依赖，运行时由焦燃原本的前置提供；不另打包或增加无焦燃时的运行要求。API JAR不打入发行包，不复制游戏实例。

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
- 装填开始时扣费一次，复用原生公开换弹时间修正，并按原生动画长度排定服务端阶段；弹匣式在完整流程结束后装填，逐发式在每次装填阶段结束时加入reloadAmount指定的弹量，收尾完成后才允许射击；能量枪按一次流程结束充满。全局关闭弹药消耗时免弹匣费用，枪内资源仍正常推进与补充。
- 以枪上的稳定事务UUID关联人类保存的剩余加载tick。临时近战期间继续计时，回枪后继承进度；该枪离开其主手、副手及背包后作废事务，不填满替换枪、不退费。
- 原生射速、连发、投射物和武器效果由公开NPC接口推进。没有移植玩家持续按键等专属操作；特殊能量/持续束武器需逐件游戏验收。
- Scorched Guns人类枪伤替代原生NPC与玩家目标难度伤害缩放，再乘HHU专用联动倍率、各阶枪伤及现有培养枪伤；不再进入普通近战/箭矢增伤。不改变玩家枪伤。

`scguns.ai_accuracy_modifier` 越大越准。原生NPC额外误差锥角为 `min(25°, 5 × 难度因子 / 精度)`，与武器/配件自己的散布分别计算。HHU战斗经验收缩只作用于该AI误差，不再次叠加TaCZ角度散布；武器固有散布继续通过Scorched Guns原生枪械数据包修改。

TaCZ专用动画只接受TaCZ枪。Scorched Guns当前按原生持握类型适配双枪、单手、机枪及火箭筒，其余回退双手，并使用原生物品渲染；未复制完整玩家关键帧换弹。详细构建边界见末节。

## 配置与物资

出生配枪分别使用`tacz`/`scguns`目录和独立白名单，各自以全局`spawn_pool_source`选择global或tier，默认TaCZ global、焦燃tier；空池按自动收录开关处理，非空严格限制。两后端独立全局`gun_blacklist`优先于出生名单，同时禁止拾取、雇主装配和使用；雇佣后接受全部未禁用的可识别枪。历史键名 `tacz` 下的分阶类型权重、配枪概率和各阶 `allow_tacz_firearms` 当前仍同时管理两种后端，`tacz.enabled` 仅控制TaCZ后端，`scguns.enabled` 仅控制Scorched Guns后端。Scguns原生lmg/heavy及能量类型分别映射到现有mg/rpg/rifle类型池，费用仍按原生类型独立配置。

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

护甲受既有全局自动导入开关及各阶 `equipment_loadout.automatic_scguns_armor` 控制。普通护甲名单集中于defaults/equipment_integrations.json，与沉浸式盔甲共用追加导入入口。流浪者redcoat及ridgetop头盔组合；一阶anthralite/cog_knight/redcoat及iron_mask/brass_mask头盔组合；二阶treated_brass/adrien；三阶diamond_steel/scrap。按原生材质核对Scrap整套护甲为21点，已从低阶移至三阶；所有默认护甲组合权重均为1，详见ARMOR_POOL_AUDIT.md。覆盖0.5.5注册的七种普通套装与三件独立头盔，共31件普通护甲。外骨骼四件套、核心和扩展模块不在自动导入表中。仅追加已注册的完整套装，保留自定义池与权重，显式空池不补回。原生仅针对玩家的外骨骼模块、面罩与特殊护甲事件不自动复制到人类。

## 玩家袭击信号弹

新增 `hostile_humans_unified:scguns_signal_flare`，由玩家发射原生RaidFlareEntity，默认使用原生copper袭击；不是HHU人类援军，不由士兵投掷。配置raid_id必须同时有原生raid与flare定义。检查维度、和平难度和已有/待触发袭击，接受发射后才消费物品并启动玩家冷却。

当前复用已有信号弹图标，通过创造栏/命令获取，尚无合成配方；制作成本待后续确定。实际袭击仍可能被原生机制或其他事件拒绝，发射成功不保证必然出现波次。

## 验证与验收边界

源码契约、真实JAR公开签名、Java语法、JSON/TOML及差异检查可在不编译情况下进行。它们不替代类型编译、可选模组缺席链接测试、客户端/专服启动或实战。

本次已执行构建检查及可选依赖缺席链接用例（472个核心类）。游戏重点检查：Scguns单独及与TaCZ同装、普通/能量/连发/特殊枪、弹匣费用与临时近战后台装填、跨卸载计时、出生物资及自定义池、四种医疗消费与中断、友军/雇主/PVP、持枪盾减伤、投掷物误伤与地形、原生袭击多人并发/保存/结束、客户端动画、专服及多士兵负载。

### 独立枪伤配置

四阶文件各自的 `scguns.damage_multiplier` 默认1.0，仅控制Scorched Guns；`tacz.damage_multiplier` 默认0.5，仅控制TaCZ。Scorched Guns人类子弹去除原生NPC难度/枪伤系数及命中玩家的原版难度缩放，改用全局 `scguns.human_gun_damage_multiplier`（默认0.7），并叠乘分阶倍率和共用战斗经验枪伤加成（默认满经验额外10%）。

Scorched Guns不使用四阶 `weapon_spread_degrees.firearms`（TaCZ专用），保留原生武器/配件散布与NPC精度入口。共用成长配置的经验散布缩放继续应用于原生NPC瞄准误差，默认每5个百分点缩小5%、最低75%；不重复缩放武器固有散布。

### NPC持握、背负盾牌和枪口火光

- 客户端入口ScgunsNpcAnimator，依赖隔离在ScgunsNativePose。读取原生GunItem.getModifiedGun(stack).getGeneral().getGripType(stack)，不写死物品ID、不伪造Player、不读取本地玩家输入。双枪、单手、机枪及火箭筒做NPC姿势适配；其他双手和未知类型回退普通双手持枪。双枪原生模型已含两把枪，仅渲染一次，不复制实体或装备。手臂/袖子随士兵自身朝向，特殊姿势不是完整复制玩家换弹/近战动画。
- 所有本模组枪手（TaCZ及焦燃）主手持枪、副手为已识别盾牌时，显示于躯干背部，左右惯用手镜像；蹲伏随躯干，原版鞘翅额外外移。当前源码最新修正提高背盾位置、倾角12度改为25度并绕X转180度校正方向，原版盾单独补偿原生渲染中心，旗帜/附魔仍由原生渲染器显示；这组修正尚未构建。使用ItemDisplayContext.NONE避开手持/物品框额外变换；自定义大型盾牌和其他背部附件仍需游戏内检查。切回非枪械自然恢复手持，不改副手库存、防护、耐久或服务器判定。
- 原生GunItemStackRenderer会传Minecraft.player；改为调用接受LivingEntity的GunRenderingHandler.renderWeapon(human, ...)，枪体、配件、原生枪口坐标/固有模型变换、火光共用同一矩阵。焦燃EntityMuzzleFlashHandler还在RenderLivingEvent.Post估算一次NPC世界坐标火光，新增仅客户端、仅Human、仅本次附着渲染成功时取消的窄Mixin，避免重复/错位；OptionalMixinPlugin读取目标方法完整签名，不存在或变化时跳过并警告，保留上游回退。不取消Forge事件，不影响玩家/原生枪手。
- 原生NPC射击提供火光包，但不提供玩家双枪射击计数；利用每次接收的原生火光令牌更新DualWieldShotTracker，使原生备用枪口偏移可用于人类。同一包多次渲染不重复计数；客户端实体卸载清理，未新增战斗网络包或逐tick扫描。
- 原生持握、附着火光及此前背盾入口已在后续测试重构建中验证编译并同步实例，不能继续笼统标为“尚未构建”。当前新增的大角度双枪肩枢轴/躯干视觉转向和背盾方向/位置修正仅通过119项Python及差异检查，尚未构建或同步。全部游戏效果均未验收：双枪、普通步枪、机枪、火箭筒；水平/大角度俯仰射击及移动、蹲伏、左右手；消音器/枪口配件、双枪左右火光、不同盾牌/鞘翅和武器切换。原生枪包自身错误的枪口元数据不在本次自动修正范围。


## 2026-10-11 斯巴达塔盾背负位置及正反面（源码待构建）

实际检查SpartanShields 3.1.1：basic_shield_base使用builtin/generated；tower_shield_base使用builtin/entity，TowerShieldBEWLR原生渲染中对Y/Z翻转，塔盾模型以零为中心。此前把所有非原版盾当烘焙物品，遗漏塔盾的中心补偿，形成截图中的侧移与朝向错误。

背负定位改由BackShieldPose统一入口分三条路径：原版盾；斯巴达盾（按Minecraft当前BakedModel.isCustomRenderer区分塔盾实体模型和普通烘焙模型）；其他盾的既有烘焙回退。原版与斯巴达实体盾分别进行上下/正反面校正并抵消ItemRenderer的半格原点平移；普通斯巴达盾不套实体中心补偿。按实际模型识别，未硬编码具体塔盾物品列表、未引用斯巴达私有类。

保留躯干锚点、25度倾斜及鞘翅偏移；旗帜、附魔和额外模型层仍由对应原生渲染器绘制。TaCZ/焦燃共用入口，服务端库存、防护和耐久不变。123项Python检查及差异检查通过；本次未构建、未同步、未启动游戏，待视觉验收原版盾/基础盾/塔盾、左右手、蹲伏、鞘翅及资源包覆盖。

## 2026-10-11 Sulfurhead 非玩家免伤兼容（源码待构建）

实查ScorchedGuns-0.5.5-1.20.1.jar：SulfurheadEntity直接继承Monster；hurt覆盖在服务端只允许DamageSource.getEntity为Player，其余立即返回false。这阻断了普通伤害事件，无法单靠Forge伤害事件放行。

新增可选ScgunsSulfurheadDamageMixin，仅服务器且真实伤害归属为本模组Human时调用Monster正常hurt流程。野生和已雇佣人类均适用；包括归属为Human的近战和弹射物，不伪装成雇主、不直接扣血、不忽略护甲/抗性/无敌规则或其他模组取消事件。其他非玩家生物、环境伤害及玩家原规则保留；无法归属到Human的持续伤害不额外放行。

这是目标模组内部行为修补，不是公开API。OptionalMixinPlugin检查目标类、方法签名及Monster直接父类，缺失或结构变化时跳过；方法内部语义未来改变仍需复核，不能承诺所有未来版本适用。未安装焦燃时不加载目标或引用其Java类型。不修改焦燃原JAR。

尚未编译、构建或同步。验收：士兵近战、弓弩、TaCZ与焦燃枪械正常造成伤害；护甲与事件取消仍有效；其他生物/环境原免伤保留；无焦燃环境启动；成长贡献和击杀归属正确。

## 2026-10-11 17:15 构建与测试投放完成

版本保持3.7.0。已包含统一背盾资源配置及Sulfurhead人类伤害放行，并保留此前枪械池/黑名单改动。Java17离线build lootScreeningTest成功：52任务，50执行、2已是最新；124项Python检查通过；482个核心类在无可选模组条件下链接成功。成品字节码确认Sulfurhead修补调用已映射的Monster.m_6469_，没有递归调用自身伤害覆盖。

检测测试实例无运行中游戏进程后，备份旧JAR及配置，替换唯一3.7.0 JAR并核对SHA-256一致。目标：E:/MC/game/.minecraft/versions/1.20.1-Forge_47.4.21/mods/hostile-humans-unified-1.20.1-3.7.0.jar；大小3,995,873字节。

SHA-256：E44F6DAB456BADDBF940EE39A78A9FC9694EA1674308496D5D6E40B258C46C99。
备份：E:/MC/Create-Delight/HostileHumansUnified-维护/部署备份/20261011-171514-3.7.0-back-shields-sulfurhead-Forge_47.4.21。
源码基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加当前未提交工作区。未编辑实例配置、未启动游戏、未推送。Mixin实际运行注入、Sulfurhead近战/弓弩/两种枪械伤害及背盾正反面/资源包重载仍待用户游戏验收；自动化成功不代表这些行为已验证。

## 2026-10-11 焦燃人类开火音量与换弹声音（源码待构建）

ScgunsIntegration：本模组人类开火音量在之前计算结果基础上乘0.5，公式为max(0,焦燃mobGunfireVolume−0.5)×0.5。原生配置依旧生效；不修改玩家、焦燃原生生物或TaCZ音量。

换弹过去已有固定手枪声音入口；现改为实际Gun.getSounds().getReload()与getCock()：成功支付并建立换弹事务后播放一次换弹声，装填完成时播放一次上膛声。优先使用已注册SoundEvent保留原声配置；原生定义为空时沿用通用手枪对应音效。由服务端在士兵位置向附近玩家广播，HOSTILE分类，换弹/上膛音量保持1。后台换弹仍以士兵位置为声源，不在每tick或换武器时重复播放。未成功开始换弹（例如缺弹匣）不播放。客户端不增加换弹动画或动画计时器。

通过已安装ScorchedGuns-0.5.5的javap确认公开声音读取接口；未修改上游JAR。没有证据能仅凭源码判定用户听不到旧声音的唯一原因，实际响度与播放仍需游戏复测。源码基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加当前未提交工作区；本轮未编译、构建或同步。

## 2026-10-11 焦燃人类独立全局枪伤（源码待构建）

全局文件global.json的scguns.human_gun_damage_multiplier，默认0.7，即保留70%伤害；0无枪伤，1不额外减伤，允许0～100。替代旧scguns.npc_damage_multiplier，旧项及对应说明由当前布局整理移除，不沿用旧含义或旧值、不生成继承文件。已有新项的自定义值保留；启动加载时将缺失的新项写入当前全局文件。各阶scguns.damage_multiplier默认1及经验枪伤加成继续生效，TaCZ不变。

实查上游AIGunEvent：子弹additionalDamage和持久字段AIDamageScale均包含“原生难度倍率×mobGunDamageMultiplier”；ProjectileEntity.getDamage还会乘通用globalDamageMultiplier；bullet伤害类型又设置了when_caused_by_living_non_player，使玩家受伤再经原版难度缩放。不能只在最终伤害事件除以原倍率，否则零倍率、简单难度的非线性公式和附加伤害都会有问题。

实现：在Forge EntityJoinLevelEvent的服务端入口，仅对Human发射且存在原生AI缩放标记的焦燃ProjectileEntity，将附加伤害通过公开Gun.getAdditionalDamage/ProjectileEntity.setAdditionalDamage还原，移除AIDamageScale；去标记使重新加入世界不会重复处理。其余弹射物字段、物理、特效、射手归属不变。不修改全局上游配置，不重新实现射击或弹道。AIDamageScale是上游没有公开setter的原生数据字段，访问集中在这一个边界，未来上游修改该结构仍需复核。

其次经原版DamageSource.scalesWithDifficulty（1.20.1 SRG m_7986_）窄Mixin，仅对Human来源的焦燃子弹返回false，避免命中玩家时再缩放；核心Mixin通过ScgunsCompat隔离可选模组类。玩家射击、焦燃原生生物、其他生物/弹射物的难度行为不变。

最终仍保留武器/配件/附魔、焦燃通用globalDamageMultiplier、其他模组伤害事件、护甲/抗性。全局0.7 × 本阶scguns.damage_multiplier × 战斗经验枪伤加成仅在现有LivingHurtEvent入口应用一次，不替换伤害类型，不直接扣血，不伪装成玩家。

验证：125项Python契约/资源/API签名检查通过；新覆盖公开getWeapon/setAdditionalDamage/Gun.getAdditionalDamage及两层缩放入口范围。尚未Java编译、构建或同步；需游戏验收简单/普通/困难下同武器同目标同护甲伤害一致（随机暴击/部位等因素须固定），分别测试目标为玩家与怪物、附魔附加伤害、原生NPC倍率为0、其他生物原规则、分阶倍率/经验与友军保护。客户端无需单独计算伤害。

源码基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加当前工作区；来源ScgunsIntegration、ScgunsHumanDifficultyMixin、ScgunsCompat、ScgunsSettings、SplitConfigFiles、TierConfigProjection及打包默认配置。

## 2026-10-11 焦燃投掷物全开放与共享战术池（源码待构建）

默认使用名单开放当前焦燃10种徒手投掷物：grenade、stun_grenade、nail_bomb、gas_grenade、choke_bomb、molotov_cocktail、hellfire_bomb、swarm_bomb、beacon_grenade、shotball。GrenadeItem原生工厂已覆盖其StunGrenadeItem子类，新增Shotball的原生公开实体构造接入；Shotball按原生0.04重力计算，其余沿用0.03，拖曳0.99。信号枪发射的袭击信号弹及榴弹枪弹药不是徒手投掷物，不纳入该库存投掷入口。仍消耗真实库存，由原生实体负责爆炸/特效；不增设自动无限投掷或第二套攻击目标。

所有阶级potions.tactical.pool新增item-only条目：scguns:grenade权重3、scguns:stun_grenade权重1、scguns:nail_bomb权重3。药水六种权重5/1/10/2/5/8不变，合计38。药水条目仍填写item+potion+weight；投掷物填写item+weight，省略potion。新生人类在同一池中逐件抽取，数量合计：流浪者1～2、一阶2～4、二阶4～7、三阶6～8。并非另外附送这些数量的手榴弹；没有保证每个单位都有手榴弹。不改变恢复药水池与恢复药水数量。

非药水仅允许已接入、全局允许且功能开启的焦燃投掷物；恢复池仍拒绝item-only条目。可选物品在出生时解析，未安装焦燃或投掷被禁用时剔除候选后重新按剩余权重抽取，不减少请求的战术总数。背包满时沿用插入失败停止规则。初始补给仍受分阶potions.enabled与ai.item_recovery_enabled控制，不给已生成单位补发。投掷物作为出生库存进入现有死亡掉落流程，不另加死亡战利品表，避免重复制造掉落。

TacticalSupplyDefaults只更新与此前完整默认列表精确相同的战术池/投掷使用名单；玩家非默认名单、自定义权重、空池与数量设置保留。当前配置原位更新，不生成继承或更新文件。全局使用名单与分阶出生池分开：默认允许使用全部10种，默认出生仅有三种；玩家可自行调整两者。

战斗使用保留冷却、许可、视线、友军安全与弧线检查。修复候选遍历：一种道具安全检查失败时继续寻找其他候选，防止毒气弹的较大安全半径阻止可用手榴弹。服务器拒绝生成实体时仍不扣物品、不重复重试。群体战斗/火焰/毒气/蜂群的实际伤害由焦燃负责，投出后队友进入区域仍可能危险。

验证：126项Python契约/默认配置/API检查通过，确认10个物品均在实际JAR注册表、Shotball公开构造存在、三种出生权重和总数不变。已补Java配置测试覆盖混合池解析、恢复池拒绝、默认更新与自定义/空池保留，但本轮未编译运行Java测试。未构建、未同步、未启动游戏。待验收自然新生库存、未安装焦燃、不同投掷物效果/弹道、友军安全、耗材与死亡库存掉落。

来源：SpawnPotionConfig、ScgunsThrowables、TacticalSupplyDefaults、SplitConfigFiles、RecoverySupplies、默认配置；基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加当前未提交工作区。


## 2026-10-11 焦燃声音与玩家节奏换弹修正（源码待构建）

用户实测17:59构建版声音调整未生效，追加要求换弹与玩家节奏一致；已明确接受“复用原生公开参数及动画时长，由士兵现有控制器执行”，不直接调用只接受Player的玩家入口。

声音：测试实例mobGunfireVolume=8，旧入口将7.5减半为3.75仍超过混音上限；普通SoundEvent通道也没有使用焦燃的换弹距离。现集中到ScgunsSounds，使用公开PacketHandler/FrameworkNetwork/S2CMessageGunSound：射击和换弹分别读取焦燃gunShotMaxDistance/reloadMaxDistance，服务端附近广播，HOSTILE分类；muzzle=false避免重复火光，reload=true正确选择换弹衰减。原生GunShotSound对输入音量及距离因子作平方，因此枪声参数为sqrt(min(1,max(0,mobGunfireVolume−0.5))×0.5)，在未饱和输入下保留比例、饱和输入下近距离最大增益0.5，避免再次因大数截顶而听不出变化。换弹/上膛读取Gun.Sounds，缺项用原生通用手枪声音，输入1。未修改玩家、原生NPC、TaCZ声音或全局焦燃配置。换弹仍需实测是否清晰可闻，不能仅凭静态检查宣称所有静音原因已消除。

换弹：旧代码用reloadTimer除以getModifiedReloadSpeed(stack,1)，误把原生“所需时间的修正”当作速度；且忽略动画完成门槛。现在ScgunsReloadTiming使用GunEnchantmentHelper.getRealReloadSpeed/getMagReloadSpeed、武器容量/reloadAmount和现有RELOAD_SPEED属性（没有该属性时按1），不重复手写附魔/配件公式。ScgunsReloadPlan按原生动画length×20×修正后时间/原始reloadTimer建立阶段。弹匣式是一整段；manual为准备、按缺弹量及reloadAmount重复装填、收尾，逐次加入真实枪内弹药，收尾期间仍禁止射击。通用弹匣只在建立整笔事务时扣一次，能量枪一次计费/结束充满，不按FE数量建循环。闲置低于25%及战斗打空规则保持。

原生动画是公开格式资源而不是玩家换弹API：从焦燃JAR assets/<namespace>/animations/item/<item>.animation.json只读按需缓存，不扫描整个JAR、不加载客户端或Gecko动画控制器、不访问私有字段、不伪造玩家。当前原生枪均具备此路径；未知/缺失/无效资源退回公开时间参数。选择对应carbine动画（如存在）。客户端资源包对动画的覆盖不会改变服务端计时，非标准路径的额外枪包需要复核该资源边界；此项不保证未来任意架构更新自动兼容，也未复制每一条原生动画音效关键帧，仅对齐阶段计时并广播换弹/上膛声。

事务仍绑定枪上UUID，进度/步骤保存在Human服务端NBT，同tick最多推进一次；临时近战、卸载/重启不重启已支付事务，原枪离开库存则作废且不填充替代枪。旧已支付且无steps的事务仅按原计时完成一次。每阶段改变弹量而非每tick修改装备NBT，声音仅在阶段开始/最终完成广播。标准Player库存/外骨骼/副产物机制没有被冒充调用，士兵仍使用通用弹匣规则。

构建新增只读compileOnly Framework API参数 -PframeworkApiJar；默认本机framework-forge-1.20.1-0.8.0.jar，依赖不打包。所有可选引用仍隔离在Scguns适配器，缺席链接测试排除ScgunsSounds/ScgunsReloadTiming，纯ScgunsReloadPlan保留在核心链接检查。

验证：127项Python静态/配置/实际Scguns JAR公开API检查全部通过，无跳过。新增Java ScgunsReloadPlanTest使用实际双枪动画确认普通56tick、半时间修正28tick，覆盖manual每次装填、末次不足批量、空缺与缺资源回退；已接入check，但未编译运行。未构建、未同步、未推送、未启动游戏。待验收近远距离枪声与换弹声、双枪/普通弹匣/逐发枪/能量枪时长、快手附魔与配件、后台近战、存档恢复和通用弹匣扣费。

负责人主控；main主工作树；基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加当前未提交工作区，共用文件顺序修改，保留此前修改。涉及ScgunsIntegration、ScgunsSounds、ScgunsReloadTiming、ScgunsReloadPlan、build.gradle及测试/文档。


## 2026-10-11 投掷落点修正与震撼弹移除（源码完成，待构建）

震撼弹不再进入默认使用名单与各阶战术生成池；运行时也明确排除 StunGrenadeItem，防止其作为 GrenadeItem 子类绕过名单。启动时只移除现有配置中的对应条目，保留其他自定义条目、权重和数量；不回收已存在的物品。默认出生保留手榴弹、钉子炸弹，权重各3，与六种战术药水共用总数量。

全局 `scguns.throwables.initial_speed` 默认1.6格/tick（范围0.5～3），替代旧的1.0速度上限和最少12 tick飞行限制。按可达飞行时间求初速度，使用原生0.99阻力；一般重力0.03、弹丸0.04、信标手雷0.025。移动目标使用水平速度预判，上限0.4格/tick、最多预判20 tick，避免无限外推。

碰撞引爆道具预测首次落点；普通手榴弹预测引信结束位置，并用最多8次二分候选补偿地面反弹（水平0.75、垂直-0.25，按原生更新顺序）。毒气弹按原生约21 tick开始释放、窒息弹按40 tick激活预测。每次失败仍冷却40 tick，不逐tick求解。预测遇墙、入水、误差超过1.5格或落点附近有友军时不投掷；真实投射物继续由原生工厂生成，保留原生引信、伤害及效果。

其他可用道具：毒气手雷 `scguns:gas_grenade`（硫磺毒气、迟缓/虚弱/恶心，遇火爆炸）；窒息弹 `scguns:choke_bomb`（消耗空气、窒息伤害、恶心并灭火）；燃烧瓶 `scguns:molotov_cocktail`（火焰爆炸）；地狱火炸弹 `scguns:hellfire_bomb`（灵魂火爆炸）；虫群炸弹 `scguns:swarm_bomb`（召唤原生Swarm并留下中毒区域）；信标手雷 `scguns:beacon_grenade`（生成SignalBeacon并召来SkyCarrier）；弹丸 `scguns:shotball`（弹跳碰撞伤害，反弹后伤害衰减）。这些保留使用能力，未新增到自然生成池。

限制：不是制导；目标后续转向、实体中途拦截、原生弹丸特殊反弹、烟雾激活后的滚动及其他模组改变物理仍可能影响实际结果。没有修改原生引爆方式或缩短引信。当前仅静态/API检查及离线平地模型核对，未构建、未同步，实机仍需验收近/远距离、移动目标、斜坡、障碍和多人友军。


## 2026-10-11 战术池新增三种投掷物（源码待构建）

各阶默认potions.tactical.pool包含毒气手雷scguns:gas_grenade、窒息弹scguns:choke_bomb、虫群炸弹scguns:swarm_bomb，权重均为3；手榴弹与钉子炸弹仍各3。震撼弹不在池内，运行时使用入口也继续排除。六种药水权重不变；投掷物与药水共用总数，流浪者1～2、一阶2～4、二阶4～7、三阶6～8，不额外赠送三件物品。

TacticalSupplyDefaults启动时识别完整的旧默认药水池与“药水+手榴弹/钉子炸弹”池（先移除震撼弹），升级为新完整默认池。自定义权重、条目、空池和数量保留；已有单位不追溯补发。全局允许使用的9种列表不变，只扩大默认出生抽取范围。未安装焦燃时沿用可选物品过滤，继续抽取可用药水。

130项Python静态/实际JAR接口检查通过，六份默认配置数值附录已更新；补充旧默认池升级Java回归案例但未编译运行。未构建、未同步实例；待实机验收新生库存、虫群阵营及窒息区域效果。


2026-10-11：焦燃枪械全局默认scguns.human_gun_damage_multiplier由0.5调整为0.7，同等枪械条件下为玩家基准70%；分阶及战斗经验继续叠乘。已有配置显式值保留，本轮未更新实例配置、未构建同步。


## 2026-10-11 19:44 3.7.0测试构建与同步

已用Java17执行离线build lootScreeningTest，BUILD SUCCESSFUL（43秒，53任务）；134项Python静态/实际API检查及6份默认附录核对通过。构建中修复了遭遇解锁日、装备满成长日的旧测试预期，以及高压参数/投掷初速度的双语说明格式。成功日志：E:\MC\Create-Delight\HostileHumansUnified-维护\验证日志\build-3.7.0-pressure-growth-20261011-194301.log。先前失败日志保留，失败产物未部署。

当前工作区3.7.0已同步到E:/MC/game/.minecraft/versions/1.20.1-Forge_47.4.21；投放前确认无Minecraft游戏进程。唯一目标JAR 4020836字节，源/目标SHA-256：9CAB70A9F9A4BB87722E528F07E3F58D30016E494AA11E6DB24462D1F8793B24。

备份旧JAR及global/roamer/tier1/tier2/tier3五份配置至：E:\MC\Create-Delight\HostileHumansUnified-维护\部署备份\20261011-194424-3.7.0-pressure-growth-Forge_47.4.21。deployment.json记录逐项变更及配置哈希。本次同步明确指定的新默认：完整生命40/40/60/80、近战/弓弩1.2/1.4/1.6/1.8、投戟1.0/1.1/1.2/1.3、四阶时间成长50日、流浪者最早第2日、焦燃全局枪伤0.7；补入非枪械阶段75/80/85/90/95/100及高压防御配置。其他阶级设置逐项核对保持不变，既有的新曲线/高压自定义项不强制覆盖。

包含此前待构建的声音/换弹时长、投掷弹道与战术池、背盾贴近、弓弩防御后台准备、高压中断恢复及枪手换近战举盾等源码修改。源码基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加当前未提交工作区，无新增提交或推送。未启动游戏；格挡衔接、声音、投掷落点、渲染、后台换弹和实际伤害/成长仍待用户游戏验收。以上测试投放状态覆盖本页此前对应修改的“待构建”状态，不表示已通过游戏验收。


## 2026-10-11 自然战术池：燃烧瓶替换为毒气手雷

四阶默认potions.tactical.pool的scguns:molotov_cocktail替换为scguns:gas_grenade，权重保留3。当前自然生成投掷物为手榴弹、钉子炸弹、毒气手雷、窒息弹、虫群炸弹，均权重3，与原有六种战术药水共用数量和权重抽取；数量范围不变。燃烧瓶仍可由玩家交给士兵使用，未从全局可使用投掷物名单移除。

精确匹配上一套完整默认战术池的配置会在现有默认池刷新入口替换；玩家自定义列表和显式空池保留，不改写已生成库存。毒气手雷复用现有原生GasGrenadeItem工厂、21 tick释放落点预测和至少16格友军安全半径。本轮未修改测试实例配置。

146项Python静态/API检查通过；新增默认池替换的Java用例待编译执行。知识库及默认附录已同步，无构建、同步、推送；自然生成抽取和毒气区域友军风险待游戏验收。


## 2026-10-11 焦燃投掷初速度再次修正（源码待构建）

实际核查ScgunsThrowables及ScorchedGuns-0.5.5原生ThrowableItemEntity：发射入口确实将plan.velocity传入原生实体，并非未接入配置。旧求解器把initial_speed视为上限，只按整数tick选择速度，尤其短距离反弹候选会自动降速；极短候选还受最少2tick限制。统一新增无外部依赖的ThrowableBallistics，按实际配置初速度求最早可达低弧线，并以原生“移动→0.99阻力→重力”顺序计算末尾不足一tick的位移。发射速度不再随候选距离自行变小，不在求解后直接放大速度破坏落点。

全局scguns.throwables.initial_speed仍默认1.6格/tick，范围0.5～3，含义改为实际初速度。最大主动投掷距离仍默认20格，未擅自扩张；命中/反弹预测、视线、攻击许可、友军安全检查和消耗真实库存保留。仍使用原生工厂及实体负责引爆和效果，不修改玩家投掷、不新增制导。无法求解或安全校验失败时不投掷。

主控/root顺序编辑main主工作树；基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加既有工作区，保留已有全部修改。涉及ScgunsThrowables、ThrowableBallistics、默认配置说明、CombatPressurePolicyTest及新Python契约；公共接口无变化。173项Python静态/API检查通过；独立数值参考核算36组速度/重力/距离的初速和离散落点通过，另核对默认初速可达32格（不代表主动距离设置变更）。Java数值测试已补，尚未编译运行；6份数值附录重新导出。未构建、实例同步、游戏启动、提交或推送，当前安装JAR仍为21:35构建。待游戏检验近/远距离、移动目标、地面反弹及毒气/窒息弹实际效果位置。

## 2026-10-11 焦燃投掷轨道、距离与集群目标（源码待构建）

用户补充确认投掷物能飞出，问题主要是轨道/落点，不再以“原地零速度”作为已确认故障。实际核查ScorchedGuns-0.5.5-1.20.1.jar：普通手榴弹及其派生的毒气/窒息/钉子/虫群/燃烧瓶/地狱火道具，公开工厂使用的带投掷者构造继承ThrowableGrenadeEntity的0.05F重力；旧预测用了ThrowableItemEntity基础0.03，造成实际下坠更快、落点偏近。信标手雷独立0.025F且反弹；Shotball为0.04F、物理会反弹但首次碰撞即产生效果，因此以首次碰撞预测效果点，不等待160tick寿命。原生效果不改。

ScgunsThrowables.flightProfile集中维护已支持工厂的物理/效果时间映射：普通手榴弹按fuse，毒气按第21tick、窒息按第41tick激活，信标按fuse，触碰型按首次命中。使用原生float精度，复用ThrowableBallistics固定初速度离散低弧求解。反弹落点搜索上限由8增至12次；延时投掷物按真实效果时间预判水平运动，水平速度最多0.4格/tick、预判最多60tick及12格位移，避免长引信无限外推。预测仍不保证目标不会转向，碰到中途生物也可能改变原生反弹/引爆时机。

出手点移至身体前方（半身宽+0.2格），先检查眼睛到出手点之间没有碰撞方块，不能隔墙生成。调用原生Projectile.shoot初始化速度及朝向，速度等于求解结果长度、不增加随机散布；保留原生工厂、所有权、引爆/云/爆炸及真实库存消耗。不是修改玩家物品或强行制导，也没有重置飞行中实体的速度。

全局scguns.throwables新默认：maximum_range=32格（原20），initial_speed=2.0格/tick（原1.6）。新增cluster_radius=4格、cluster_minimum_targets=3、cluster_cooldown_ticks=100tick。当前合法仇恨目标附近最多16名合法可见敌人参与密度比较，优先投向集中位置的实际敌人，避免平均点落在墙里或悬崖；中立动物不因为可受伤就自动算敌人，仍检查自主敌人分类/反击授权/仇恨和士兵命令。只改变战术投掷瞄准点，不改变主战斗目标。达到3名集群时成功投掷冷却默认5秒（普通仍10秒，实际取两者更小值），保留友军安全/视线/弹道/最低距离及禁用品限制。毒气/窒息仍至少16格安全半径，不为集群放宽。

没有可用投掷物的单位不扫描敌群。现有20tick分散检查、失败40tick节流保留，密度比较最多16×16；不增加常驻Goal/范围扫描。所有新参数位于同一throwables配置区域，注释紧邻对应值。旧配置/测试实例本轮不改写，已有明确设置的20格/1.6仍会优先于新默认；需在未来明确配置更新时调整，不能把源码新默认宣称已同步生效。

主控/root顺序编辑main主工作树，基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d及所有既有未提交修改。涉及ScgunsThrowables、ScgunsSettings、默认资源、CombatPressurePolicyTest和Python投掷/API契约。198项Python静态/API检查通过，包含实际原生构造重力继承及特殊重力契约；独立平地数值参考核查16组距离/效果时间的反弹位置。Java数值测试扩充32格、2.0初速及0.05F重力边界，但未编译运行。6份默认附录同步。未构建、同步实例、启动游戏、提交或推送；安装包仍为22:52构建。

待验收：各五种出生炸弹在近/远、静止/移动、高低差与反弹场景落点；信标与Shotball首次效果时机；集中3个以上敌人时更密集投掷；友军介入、墙前发射、主动/许可/被动权限；实际耗材与性能。重点区分触碰引爆与延时反弹，不以统一“落地处”解释全部原生物品。

## 2026-10-11 焦燃投掷物品质与冷却调整（源码待构建）

投掷物品质统一从50提高为170，沿用HumanLootManager.inventoryValue→ScgunsSettings.throwableQuality的共用入口，同时作用于拾取、背包保留及替换。该值高于一般战术药水和非职业优先弹药140/近战职业弹药160，低于本职业优先弹药180；不会保证无限保留所有投掷物。食物槽位、最后恢复药品和必要武器预留沿原规则，未改出生抽取权重、投掷伤害或物品堆叠。

普通投掷cooldown_ticks由200降为160tick（10→8秒）；集群cluster_cooldown_ticks由刚加入的100降为80tick（5→4秒），实际集群冷却取两者较小值。均在全局scguns.throwables配置，注释紧邻数值；失败预检40tick、每20tick分散检查及全部友军/权限/弹道约束不变。

主控/root在main主工作树顺序修改ScgunsSettings、默认JSON、既有Python配置/拾取接线契约，基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d及全部既有未提交修改。198项Python静态/API检查通过，6份默认附录已导出核对。未运行Java编译/构建、未同步实例或推送，原有实例的quality=50/cooldown_ticks=200等明确值不会自动被新默认覆盖，需以后指定配置更新时处理。源码待构建与游戏验收。

待验收：有其他低价值物品时优先保留/拾取炸弹，饱和背包仍保护食物药品；单体与密集敌群投掷间隔，真实库存消耗和友军安全；不把出生抽取权重误当背包品质。

## 2026-10-12 焦燃投掷物伤害配置（源码待构建）

全局 `global.json` → `scguns.throwables.damage_multipliers` 按物品ID控制本模组人类投出的直接伤害，范围0～100，1为原生，0为零直接伤害但保留效果。

| 物品ID | 默认倍率 | 默认直接伤害 |
|---|---:|---|
| scguns:grenade | 32/19 ≈ 1.6842105263 | 32点，保留距离衰减 |
| scguns:nail_bomb | 24/14 ≈ 1.7142857143 | 每条命中弹片24点，数量/撕裂不变 |
| scguns:molotov_cocktail | 1 | 保持原生直接爆炸，后续燃烧不变 |
| scguns:hellfire_bomb | 1 | 保持原生直接爆炸，后续灵魂火不变 |
| scguns:shotball | 1 | 初始9点，保留反弹衰减 |

服务器在Forge LivingHurtEvent中根据直接伤害实体的真实投掷者与物品ID调整一次，不改伤害类型或直接扣血，护甲/抗性照常处理。独立于枪械70%与成长倍率；玩家与其他生物投掷不变。毒气/虫群不开放；窒息原生伤害不保留投掷者归属，无法通过此入口可靠区分，因此不开放。地面后续火焰也不追踪改伤。

已有全局配置在下次正常加载时补齐缺失倍率，保留显式自定义值（包括0）；不改战术池/权重/投掷速度/冷却。当前测试实例配置与JAR未修改。来源：ScgunsSettings.java、ScgunsThrowableDamage.java、ScgunsIntegration.java、TacticalSupplyDefaults.java，以及ScorchedGuns 0.5.5实际JAR伤害调用。出生默认值索引见知识库附录。
