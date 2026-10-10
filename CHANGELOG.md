# Hostile Humans Unified — 更新日志 / Changelog

## 未发布 / Unreleased

- 士兵装备面板空护甲槽增加原版头盔/胸甲/护腿/靴子轮廓，副手增加盾牌轮廓。玩家Shift转移护甲、盾牌/图腾和武器时优先对应空装备槽；已有装备不覆盖，余量/普通物品进入背包，沿用服务器物品校验和手动护甲锁。
- 主动拾取盾牌限制为背包3面：达到或超过3面后只以更好盾牌替换较差备用盾，地面大堆逐面处理；玩家手动放入、出生生成和恢复物品托管不受上限限制，也不清理额外盾牌。替换先成功掉落旧盾再提交，绑定盾不主动丢弃，保留食物槽保护。
- 持盾选择改为按最大耐久升级，取消已持有原版盾就不再升级的保留规则；同档保留当前盾，不按剩余耐久来回切换。已确认可格挡的无耐久盾优先，兼容各模组盾牌，不按模组名称选用。恢复/换弹托管及正在举盾期间不抢换装备。本轮尚未构建或同步。

- 已雇佣士兵增加弹药耗尽对话：通用箭矢用完，或所有枪械已无可用弹药且通用弹匣不足换弹时通知雇主。扫描含背包中的备用/已收起武器，正在换弹时不误报；不消耗弹药模式、野生单位及编辑背包期间不报告。每次耗尽只成功提示一次，标记随实体保存，补充对应弹药后重新启用提醒；多士兵共享状态报告限频，错开发送，未轮到的在原供给检查中重试，不增加消息队列或扫描频率。

- 追击跳砍由近战Goal协调：结合当前武器距离、相对接近速度、攻击冷却与Better Combat前摇预算，在接近目标的外圈尝试起跳；贴身、转向绕路、撤退、驻守及不安全落点不发起追击跳砍。保留分阶跳跃概率/间隔，远距离追击仍保留独立于攻击冷却的概率移动跳跃，接近目标后由跳砍逻辑协调，地形上台阶与撤退跳跃保持。
- 明确追击跳砍的暴击入口：本次主动追击起跳在真实离地后，上升和下降时的实际近战命中均使用1.5倍基础近战属性伤害，落地、换目标、换武器或超时失效；Better Combat延迟命中沿用同一入口，不另造攻击或动画。原版模式不增加此暴击，枪械及弹射物不受影响。尚未构建/同步或游戏验收。

- 对讲机名册增加成长等级与战斗经验摘要，士兵详情显示两条培养进度和当前门槛。数值及显示开关由服务器提供，未加载士兵显示最后保存状态；成长关闭或原版模式开启时隐藏，缺少保存记录不虚构数值。复用分页刷新，对讲机协议升级为6。

- 重新划分成长配置职责：全局保留成长总开关、属性阶段倍率、共用培养门槛/经验上限、战斗奖励及枪械经验修正；各阶日期曲线和材质/配枪/附魔/图腾/三叉戟等早期装备细项移至各阶growth_profile。当前自定义值保存到新位置后才清理原位置，不生成继承文件；各阶明确配置优先，不覆盖自定义装备池。新增可配置经验档宽，培养成本与枪械散布共用，默认5个百分点保持原行为。
- 配置常用开关和属性前置，大装备池与细项后置；增加分区标题和字段组间空行，保留紧邻字段的双语说明与标准JSON格式。本轮源码整理，尚未构建、改动实例配置或同步。

- 为正式雇佣士兵增加独立于战斗目标的受困救援：持续约10秒无实质位移，且两次预算内寻路确认不能返回命令目的地时，跟随/护卫尝试安全传送至主人附近，区域驻守回驻守点附近，巡逻回最近一次成功规划的巡逻终点附近（缺少记录时回巡逻中心）。成功后清除旧战斗/移动状态，短暂暂停选敌并设置救援冷却；原地驻守、撤退、涉水/上岸及熔岩应急行为保持，不因正常站定射击或预算等待直接传送。所有落点检查碰撞、危险地形、世界边界与区块加载；本轮尚未构建或同步。

- 战斗经验接入枪械：以100%经验为散布基准，每5个百分点减少5%的基础附加瞄准误差，最低75%；低于100%反向增加。枪伤在100%经验至培养上限之间线性增加，默认125%时额外增加10%，不叠加成长阶段倍率。全局成长关闭或原版模式开启时停用这两项修正。
- 经验培养改为分档逐点扣费：低于100%每点3成长值；100–104%、105–109%、110–114%、115–119%、120%以上依次每点3/5/8/12/16。跨档重新计算下一点成本，保留余数；服务器面板同步当前门槛。全局growth.experience及growth.firearms开放相关参数，移除旧统一cost字段与说明；出生经验默认90–110%保持。

- 统一战斗地形受阻判断：追击按实际可达末端评估部分路径，危险通行按方向短期记忆；受阻后分批搜索侧方、后方和不同高度的安全绕路，并让追击、弓弩、枪械、三叉戟与防御共用路线归属。能保持射界时继续远程攻击，只有确实可近战或可安全接近时才因地形切换近战；无法接近且受攻击时尝试有实际遮挡的安全位置。保留防摔、撤退、士兵命令及水中策略，不新增竞争移动的Goal。源码检查通过，构建与地形游戏验收待执行。

- 调整涉及本模组人类的弹射物受伤冷却：人类射出的弹射物命中非玩家生物，或人类受到弹射物攻击时，后续命中不再因原版受伤冷却被忽略或仅补差额。原版模式下停用此调整；玩家目标、无关生物交战、近战、爆炸及持续状态伤害沿用原规则。保留原伤害来源、护甲/盾牌、友军过滤与实际扣血成长结算；不重置共享受伤计时器。本轮未构建或同步。

- 原版模式优先禁用统一成长：集中使用服务器有效开关，使属性成长、战斗培养、新生装备成长和面板同时停用；不改写growth.enabled或清空养成记录，离开原版模式后恢复配置选择。原版生命和武器近战实现保持，既有装备不重发。

- 缩小士兵成长区域高度（96→72），经验显示改为无拖动手柄的只读进度条，保持服务器权威及隐藏布局行为。跟随传送兜底独立跟踪实际靠近玩家的进展，路径节点推进不再刷新救援期限；战后跟随约10秒无有效接近时，即使仅相距10～20格也会尝试在玩家身边安全传送。
- 核对默认整个成长时段的分阶出生基础生命；二阶曲线第二个中间节点由0.40改为0.45，消除第32～33天与一阶同为48点的情况，保留各阶起止日期和基础生命。允许出生随机经验导致生命区间重叠，不强制覆盖玩家自定义曲线或外部生命加成。

- 将装备日期成长强化为统一成长系统：全局 `growth.enabled` 默认开启，集中管理四阶时间曲线、0～5级属性阶段、随机战斗经验与雇佣培养。完整基础生命改为40/60/80/100，阶段默认50%～100%，经验出生90%～110%、雇佣培养最高125%；阶段只在升级时改变，经验每1个百分点生效。生命、近战、弓弩箭和投掷三叉戟先算阶段再算经验；枪械仅使用独立经验修正。
- 正式雇佣士兵按实际生命伤害记录贡献，在目标确认死亡后结算，无需尾刀：每完整10点得0.25成长值，基础封顶4，个人伤害严格超过300再加4；允许回血伤害累计，不识别Boss或专门过滤人偶。默认每40成长值升一级，经验每个百分点按所在档位消耗成长值；全部规则可在全局配置调整。保存出生快照、培养余数、贡献及卸载奖励，处理死亡取消、图腾和重复结算。
- 士兵面板新增服务器权威成长快照、整数等级与经验滑块，客户端本地配置不参与数值或显示判断；会话token/UUID/实体ID/维度校验，限频发送变化。统一成长关闭时按100%属性计算并移除经验影响，隐藏成长区域、停止培养，保留既有养成记录且不重发装备。移除旧随机生命配置和过时说明。本轮未构建、部署或进行游戏内验收。

- 针对测试实例C2编译线程崩溃涉及的选敌方法进行候选加固：拆分搜索、玩家警戒与围攻人数查询，移除仅支持Human入口中的冗余类型分支，查询固定使用本次候选目标；保留撤退、快速转火、警戒传播和围攻人数规则。此调整尚不构成JVM崩溃已解决的结论，需构建及运行对照验证。

- 全面核查当前配置读取与实际调用，删除不再影响人类枪手的全局散布项，统一使用各阶武器分类散布。清理失效的平铺属性、移速/散布与流浪者旧判定残留；配置说明仅保留当前字段、公式和指令用法，去除旧名称及迁移解释。当前有效数值与自定义装备池保持。

- 整理全局与分阶配置职责：各阶雇佣上限、支付物品与数量统一到全局 `recruitment.max_hired_by_tier/payment_by_tier`；属性、装备、补给、战斗AI、分类散布和生成解锁保持分阶。人类输出仅使用各阶 `damage_multipliers` 及 `tacz.damage_multiplier`，移除全局重复输出因子；默认近战1.7、弓弩1.8144、投掷三叉戟1.26、枪械0.5，保持此前有效乘积。近刺、跳劈、女仆与盾牌等条件修正独立保留。每个控制项的双语说明紧邻其值，池格式和公式跟随对应字段，区域开头仅留简短用途；清理放错文件的已知选项，保留自定义池和未知扩展，不生成兼容或迁移文件。

- 统一出生药水权重抽取：移除迅捷单独概率入口，加入战术池且允许重复；新增饮用生命恢复II、力量II和喷溅剧毒II。恢复池权重为治疗II 2、长效再生I 1、再生II 1；战术池为长效迟缓I 5、长效虚弱I 1、长效力量I 10、剧毒II 2、力量II 5、长效迅捷I 8。四阶恢复与战术各自默认总数均为流浪者1–2、一阶2–4、二阶4–7、三阶6–8瓶，各阶count_min/max继续开放配置；保留已有自定义池与数量，旧bonus不再生效。

## 3.7.0 — 2026-10-09（测试中 / Testing）

- **模组 ID 与命名空间变更，可能不兼容旧版本存档。** 原 `hostile_humans` 与 `humangunner` 统一改为 `hostile_humans_unified`，模组列表仅保留一个 **Hostile Humans Unified**。本版本仅面向新存档，完全移除旧注册 ID 的迁移与别名兼容；旧实体、物品等不能依靠旧 ID 自动恢复。命令、脚本和数据包中的引用需同步使用新命名空间。
- 合并 Forge 初始化入口，统一实体、物品、音效、菜单、网络通道、翻译键、资源与持久化标识。更新模型、配方、战利品、自然生成和结构 NBT 引用；配置目录继续使用 `config/hostile_humans_unified/`。
- 规范高风险联动入口：TaCZ 换弹不再绑定编译器生成的 lambda 方法名，按签名与精确调用匹配并保留弹药扣除的严格检查；集中 TaCZ、Better Combat、Player Animator 客户端反射契约及失败诊断，保留原生动画回退。仍依赖部分上游内部接口，不承诺任意未来版本自动兼容。
- 隔离女仆显式排除规则，接口异常时拒绝攻击人类并记录原因；女仆爆炸及斯巴达随机数修补增加精确接口能力检查。修复该检查插件请求 ModLauncher 不支持的未转换字节码，导致 `humangunner.mixins.json` 在启动准备阶段失败的问题，改用受支持的读取入口。
- 暂停旧版继承的 Traveler’s Backpack 自动装备及专用渲染、Dungeons Mobs 信标替换、Villager Names 触发命名、Quark／mctb 与 Waystones 结构覆盖，以及 Polymorph、Pillagers Gun 内部修补。保留农夫乐事、Curios、斯巴达装备、沉浸式盔甲和当前枪械／动画／女仆联动。
- 增加单一模组身份、资源命名空间、换弹契约和 ModLauncher 读取方式检查；修正三叉戟追击测试，使其覆盖实际近战与远程分支。自动检查不替代启动和游戏内验收。


- 改善移动目标追击连续性：近战、弓弩、枪械与三叉戟在目标明显移动或旧路径将结束时限频预刷新，失败/预算等待保留有效旧路，避免追到旧位置后长时停等；静止有效路不固定重算，无法到达的目标采用退避重试。

- 优化弓弩地形走位与遮挡响应：安全寻找射界、沿路靠近和持续近战绕障；全人类按当前生命限制自主落差并检查跳跃及停止后的惯性。补齐雇佣弹丸的玩家过滤：雇主始终保护，非雇主按PVP开关判定，覆盖TaCZ范围爆炸伤害与击退。

- 修复野生人类主动攻击女仆：所有阶级默认对女仆中立，不依赖主人身份牌，并清理无故锁定；保留受到该女仆实际攻击后的近期自卫，已雇佣与临时控制的战斗规则保持。

- 主动贴身保护允许按当前武器射程提前攻击远处敌人，仍优先主人近处威胁并限制追击距离；将人类友军弹丸过滤前移到碰撞候选阶段，覆盖TaCZ普通/穿透弹、弓弩和三叉戟，防止点燃、击退及命中反馈，保留野生与雇佣同阶人类的战斗碰撞。

- 新增车万女仆选敌兼容：已驯服女仆按主人身份牌识别野生人类，同阶中立、高阶友好，保护已雇佣与临时援军；保留自卫/信标例外，切牌后清理失效追击并防止弹丸误伤。

- 修复食物自由配置缺失：出生食物与两种金苹果改由各阶food控制，保留原默认配额，空食物池不补默认物品；完善自定义药水与护甲无效ID/部位诊断及配置生效说明。

- 修复雇佣士兵入水跟随转圈：跟随与贴身保护保留支持浮水的地面导航，不再切到要求水下节点的游泳导航；继续禁止下潜。

- 全面修复导航与寻路冲突：统一MOVE调度、恢复跟随倍率，跟随/贴身保护可直接水面渡水，长时卡住允许安全救援传送；退路有界绕墙、失败保留有效旧路，补齐水中退避和地下岸阶、门梯通行与TNT避险，并对普通及环境搜索实施公平预算。

## 3.6.5 — 2026-10-07（已发布 / Released）

- 新增分阶低血量搏斗策略：唯一对手同时低于30%生命和30点时默认50%收尾搏斗；其余无即时治疗时限时试探，根据实际回血/受伤决定续战，失败冷却并防止30%附近重抽。自身同时低于15%和25点才硬撤退；瞬间治疗会话优先，拆除强制撤退永久写死逃跑概率，并恢复低血返战目标。

- 修复闲置水中浮沉/绕圈被误认为上岸进展、寻路失败旧路径阻塞直接岸边移动及短暂丢失上岸状态；卡住后限频重选岸边，保留有效备用方向。删除短时高伤与独立围攻撤退触发，只保留低血量战斗撤退及环境避险。

- 移除旧JSON/TOML配置迁移、别名及旧默认值升级，不再生成迁移或装备导入备份；仅生成当前五文件。按常用设置优先重排现有有效配置，保留自定义值、说明及空池，缺项仅在内存补全。

- 修复贴身保护被自卫与战术选敌抢占，优先主人附近威胁；修复 Better Combat 取消动画误播及前摇期间格挡/重复反击，保留紧急撤退。苦力怕避让只随有效引爆进行，到安全距离或停止引爆立即结束；贴身保护全攻击模式可优先击杀主人身边即将爆炸的苦力怕。

### 简体中文

- 将沉浸式盔甲默认套装改为与斯巴达装备共用全局自动导入开关，安装且四部位注册后首次追加，支持分阶automatic_immersive_armors和导入回执；保留既有套装、自定义权重及空池。新基础池不再预置联动套装，原分阶范围/套装权重不变。
- 增加默认开启的全局equipment_integration.auto_import_enabled，关闭时启动不导入或改写装备池；开启时首次追加可用斯巴达武器/盾牌，保留玩家自定义条目、权重、空池和导入记录。全局开关在新建和已有分阶配置中统一生效，已导入条目不因关闭被删除。
- 修复斯巴达武器/盾牌默认联动仅首次无旧配置时导入，分阶配置生成过早后不再重试的问题；注册表就绪后一次补入未导入的默认装备池，保留自定义/空池、关闭开关和已完成导入记录。
- 修复雇佣后旧友军目标残留于选敌与退避缓存；持枪攻击实时重查权限。换弹等待进食/举盾或缺弹匣时不反复重置拔枪；仅原生换弹成功且弹匣付费通过后通知客户端播放动作和音效，失败尝试不循环播开头，状态查询不取消原生换弹。
- 实际生命严格低于30%且有可用瞬间治疗药水时，优先喝药而非食物；战斗/闲置与退避共用库存托管，食物待回血不遮蔽急救阈值，未完成进食可安全中断归还。瞬间治疗饮用超过20 tick时缩为20 tick，已更快的药水不延长，原扣瓶与效果逻辑保留。
- 新增默认关闭的全局原版模式：所有人类含雇佣士兵基础生命20、基础近战1，取消本模组近战额外倍率，保留武器/附魔/效果及外部生命修饰；切换保存受伤比例和原有阶级生命。新增贴身保护移动命令，约2–4格护卫、最多12格追击、主人近处新威胁优先返回，远程可在身边反击，沿用现有盾牌/攻击/弹药/生存导航。新增许可攻击模式，只对自卫、主人受攻击或主人主动攻击的已授权目标出伤；被动贴身保护可提前挡护，主人实际受伤后反击且保留自卫。旧命令序号保留，双界面与网络协议同步更新。
- 修复人类喝完药水未扣除的问题，保留玻璃瓶与副手归还；已有再生效果时不重复饮用含再生药水。再生I/II按本阶食物回血间隔额外恢复1/2点生命，可与食物及原版再生叠加，效果结束即停止。
- 自动拾取保留三个食物槽位及最后一份恢复药品、必要备用近战与各部位护甲，食物不足时留空位，按数量与品质保留较充足的食物，同类更好物品可升级。枪手优先弹匣、弓弩手优先通用箭矢、近战两者同等；临时换武器不改变原武器需求。远程近身防卫至少手持近战40 tick，等待挥击/Better Combat收招后才自动回远程，近身威胁持续时可多次反击；手动管理装备仍立即生效。
- 禁止主手持 TaCZ 枪械时执行原生或 Better Combat 近战、反击及延迟出伤，空枪同样适用；切换主手近战武器后恢复。修复玩家目标和驻守记忆保留死亡目标，枪手击杀后短时跳过随机重搜等待。同一把枪跨目标及 Goal 重启不重复掏枪，保留原生射速、换弹与有限弹药规则。
- 整理新生成的五份配置：全局不再遗留只有说明的阶级/装备/解锁区域，相关说明移至阶级文件。四阶采用统一分区顺序，配置值前增加双语用途、单位与范围说明，补齐弹药、药水、枪械和雇佣参数；配置数值、物品池及游戏逻辑不变，现有有效文件仅重排字段，保留自定义值。
- 加快雇佣士兵闲置跟随并加入距离迟滞与有界路径更新；按阶配置普通/追赶倍率。主人下线仅将跟随改为当前位置区域驻守，保留其它指令和攻击模式。外部存档不再因主人离线或跨维度漏存主人UUID；离线真实死亡持久记录原因和地点，上线提示，卸载与丢弃不报死亡。
- 弹匣简洁贴图增加两条压槽。四阶配置新增potions：独立设置出生恢复/战术药水池、瓶数范围及迅捷药水概率，支持已注册模组药水；空池不补默认药水，原药水发放硬编码移除。
- 简化通用弹匣贴图轮廓与高光；人类箭矢改名通用箭矢（物品ID不变）。箭矢和弹匣拾取评分提升至160，瞬间治疗药水降至55，高于面包、低于牛排和金胡萝卜等高品质食物。
- 配置重组为全局及四阶独立JSON；属性、装备、战斗AI、枪械概率、雇佣条件和出生弹药按阶设置。仅读取当前五份JSON，不再迁移旧JSON或TOML；全局弹药消耗默认开启，关闭后弓弩和枪械换弹不要求或扣除库存。
- 人类箭矢与通用弹匣使用专属32×32透明像素贴图；新增工作台无序配方：4支原版箭、1根木棍、1根羽毛合成32支人类箭矢，3份火药和2个铁锭合成2个通用弹匣。弹匣配方仅在安装TaCZ时加载。
- 闲置换弹仅在弹匣余量严格低于容量25%时触发，战斗中必须打空（包括膛内剩余子弹）才开始换弹。统一入口再次检查阈值，避免旁路调用提前收费。
- 远程人类改为有限弹药：弓弩统一消耗64堆叠的人类箭矢，每次成功射击／齐射扣1；TaCZ 使用24堆叠的通用弹匣，每次有效启动换弹按手枪／冲锋枪1、步枪／霰弹枪2、狙击枪3、机枪4、重型武器5扣除。原生逐发装填按完整换弹周期收费，库存供弹枪也使用有限弹仓；取消、切枪和卡顿恢复不能无偿补满。
- 新生成流浪者、一阶、二阶、三阶相应职业分别携带32–128／64–128／128–192／192支箭矢，6–12／12–24／32–48／48–72个弹匣；首仓枪内子弹保留。枪内和储备弹药耗尽后，优先有箭弓弩，再现有近战武器；补给后恢复可用武器，三叉戟无限投掷不变。旧存档不自动补满，NPC临时弩载荷及不可回收箭矢不能变成玩家免费弹药。
- 修复 TaCZ NPC 动画播放状态在传送、重新追踪、模型或资源重载后遗留的问题；动作优先级读取实时同步状态，旧瞄准、换弹或单次动画不再持续占用资源包动画。每次模型设置先清理上一帧姿势，保留 Better Combat 接入。
- TaCZ 持枪和瞄准可使用其原生第三人称接口，不再要求 Player Animator；无此前置或缺少枪包动画资源时，换弹与射击使用基础模型动作。枪包精确关键帧仍在 Player Animator 可用时播放，不改变服务端攻击、弹药或换弹逻辑。
- 未雇佣的敌对信标人类在原有掉落规则完成后，食物逐件再以 20% 概率保留，附魔金苹果全部排除；非食物掉落不变。新来源标记持久保存并兼容仍有旧信标目标标记的单位，正式雇佣后仍完整返还库存。
- 修复原生 TaCZ 动画适配器中 lambda 暴露可选类型的问题，确保未安装 TaCZ 时核心类仍可加载。构建及自动化检查已通过；传送、重追踪、资源包与可选依赖组合仍需游戏内验收。

### English

- Add default-off global vanilla mode: all Humans, including recruits, use 20 base health and 1 base melee damage without mod-specific melee factors, retaining weapon/effect modifiers, injury percentage and saved tier health. Add Close Protection movement with nearby positioning, a 12-block chase leash and owner-centered threat reprioritization; existing shields, ranged attacks, ammo and survival navigation remain authoritative. Add Authorized Attack for self-defense, owner defense and owner-initiated fights only. Passive protection may intercept before damage but attacks after actual owner injury or in self-defense. Preserve old enum ordinals and update both screens and channel protocols.
- Charge one potion after successful NPC drinking and preserve bottle/offhand custody; do not redrink regeneration potions while regeneration remains active. Regeneration I/II independently add 1/2 HP per rank food-healing interval, stacking with food and native regeneration only while the effect lasts.
- Reserve three backpack food slots, one recovery medicine and necessary melee/armor backups during automatic pickups; keep empty space when food is missing and retain substantial stacks by combined quantity/value. Gunners favor magazines, bow/crossbow users favor Universal Arrows, and melee users value both equally, including temporary weapon swaps. Hold close-defense weapons for at least 40 ticks and finish vanilla/Better Combat swings before automatic ranged restoration; continuing threats permit repeated counters, while manual equipment management remains immediate.
- Block native and Better Combat melee, counters and pending hits while a TaCZ gun occupies the main hand, including empty guns; melee resumes after switching weapons. Reject dead player/remembered guard targets and briefly bypass randomized gunner reacquisition after a kill. Reuse the same gun binding across targets and goal restarts without repeated draws, retaining native fire/reload timing and finite ammunition.
- Organize newly generated configuration files: move orphan rank/loadout/unlock guidance out of global settings, use consistent rank section ordering, and place bilingual purpose, unit and range help next to fields including ammunition, potions, firearms and hiring. Defaults, pools and gameplay behavior are unchanged; existing valid files reorder fields while preserving custom values.
- Speed up idle hired follow with distance hysteresis and bounded route updates; configure normal/catch-up multipliers by rank. Logout converts only FOLLOW to GUARD at the current position, preserving other orders and combat modes. Save owner UUIDs even when owners are offline or in another dimension. Persist confirmed offline deaths and report causes/locations on login; unloads and discards do not produce death notices.
- Add two stamped grooves to the magazine icon. Per-rank potions configure recovery/tactical pools, bottle counts and bonus chance, including registered mod potions; empty pools grant none and hardcoded provisioning is removed.
- Simplify the universal magazine sprite and rename Human Arrow to Universal Arrow without changing its ID. Raise ammo pickup value to 160; lower instant healing to 55, above bread but below steak and golden carrots.
- Consolidate configuration into global and four rank files, with independent AI and spawn ammunition. Read only the current five JSON files without legacy JSON/TOML migration; the default-on ammunition switch can waive reserves and deductions without bypassing reload timing.
- Added dedicated 32×32 transparent pixel-art textures and shapeless crafting-table recipes: 4 vanilla arrows, 1 stick and 1 feather produce 32 Human Arrows; 3 gunpowder and 2 iron ingots produce 2 Universal Magazines. The magazine recipe loads only with TaCZ installed.
- Idle reloads require strictly less than 25% magazine capacity; combat reloads require all loaded rounds, including the chamber, to be spent. The shared accepted reload boundary checks these thresholds before charging supplies.
- Humans now use finite ammunition: Human Arrows stack to 64 and cost 1 per successful bow shot/crossbow volley; Universal Magazines stack to 24 and cost 1/2/3/4/5 per accepted reload for pistol/SMG, rifle/shotgun, sniper, machine gun and heavy weapons. Manual feeds pay once per cycle; inventory-feed guns use finite internal rounds. Cancellation, weapon changes and stall recovery do not refill ammunition for free.
- Initial ranged reserves by tier are 32–128/64–128/128–192/192 arrows and 6–12/12–24/32–48/48–72 magazines, retaining the initially loaded firearm. Exhausted gunners prefer a supplied bow/crossbow, then existing melee equipment; resupply restores availability. Tridents stay unlimited. Existing saves are not replenished automatically, and NPC temporary crossbow payloads/unrecoverable arrows cannot supply free player ammunition.
- Reset TaCZ NPC playback across teleports, tracking gaps, model changes and resource reloads. Animation priority reads live synchronized actions, releases stale actions, and clears the previous model pose while preserving Better Combat integration.
- Native TaCZ hold/aim poses and basic reload/fire gestures work without Player Animator or matching keyframe assets. Gun-pack keyframes remain available with Player Animator; server-side attacks, ammunition and reload rules are unchanged.
- After existing loot rules, unhired hostile-beacon Humans retain each food item with an additional 20% chance and never drop enchanted golden apples. Non-food loot is unchanged. Persistent origin tracking supports legacy beacon markers; formally hired Humans still return their full inventory.
- Removed an optional TaCZ type from a synthetic lambda method descriptor so core classes remain linkable without TaCZ. Build and automated checks passed; teleport, retracking, resource-pack and optional-dependency combinations still require in-game acceptance testing.

## 3.6.0 — 2026-10-03

### 简体中文

本版本汇总 3.5.5 发布后的装备、配置、个人进度、士兵反馈和战斗导航重构。以下内容记录实现与修复，不将自动检查视为游戏内验收结果。

#### 战斗与行为连续性

- 撤退路线在接近终点时提前续接，并根据近处追兵的实时方向检查路线是否仍安全；寻找新路线时保留旧路线，地面路线耗尽时用经过碰撞与落脚检查的短步暂时拉开距离。闲置回血和移动辅助不再覆盖撤退速度。通用无进展检测缩短为 5 tick，先恢复移动输入，再尝试局部脱困，最后使用独立共享预算重寻路；主动攻击、原地驻守和正常站定射击不因该检测被强制移动。
- 提升受击与落地后的移动连续性：原生逃跑不再每次落地先停路，失败重规划保留现有路线并限频重试；受击中的撤退只记录反击授权，不把路线交回进攻。通用停滞检测跨路线/路点更换记录真实位移，近期受伤及撤退时缩短恢复等待，移动恢复物品使用不屏蔽检测。废弃路线清理残留移动输入，避免原地奔跑；战术撤退能识别被替换路线，缺失导航不再被反击阻止重规划。
- 完善 Better Combat 高低差攻击：攻击、控距共用原点及目标碰撞箱内的可达瞄准点，控距不再被旧俯仰角误判；起手、前摇和延迟出伤保留俯仰，避免普通看向控制重置。高大、正上方及斜下方目标使用有限三维方向，仍保留距离、视线及实际出伤命中盒检查。
- 正式雇佣人类死亡时完整掉落背包全部物品、主副手及四件护甲，保留死亡前的数量、耐久与 NBT，不应用野生掉落概率。按死亡时雇佣身份保存物品快照并清理旧掉落副本，避免漏掉或重复；正式雇佣的援军不受临时援军无掉落标记影响。野生人类及未雇佣临时援军保持原有规则。
- Better Combat 延迟出伤阶段持续朝向本次攻击目标，路径推进或后退不再把身体立即转向路点；地面与水中移动沿用原路径，出伤后解除朝向约束。撤退、目标失效或武器切换不会被攻击朝向占用，仍按实时范围与视线判断命中，不保证必中。
- 主人离线不再强制士兵待命、清除仇恨或禁止拾取，保留玩家设置的移动及攻击模式。跟随模式在主人缺席时暂停跟随而不阻断已授权战斗；清理旧存档自动待命标记，恢复此前坐下状态。
- 士兵自动聊天改为按主人共享限额：普通台词默认 60 秒间隔、每 5 分钟最多 3 条；重要状态报告使用独立限额、去重及有界合并队列，优先显示低血量警告，避免多名士兵同时刷屏。限额写入雇佣配置并保留双语说明，主动雇佣及命令确认仍即时反馈。
- 修复防御状态占用移动后站桩：远程防御检查真实导航状态，缺失路线时按武器射程后撤或推进；失败路线及时交回武器 AI，不再假设旧走位路线仍存在。近战持盾按实际命中范围继续推进，普通战斗格挡也使用移动盾牌入口。
- 近战在有效攻击距离内受击后短时尝试小幅、安全侧移，不再直接锁定静止控距；侧面被挡时交回正常追击。近战持盾复用同一控距规则，食物、拉弓及撤退不受影响，不修改攻击冷却。
- 持盾不再禁用导航停滞检测，5 tick 无进展时先恢复移动输入，持续受阻才执行局部脱困与限额重寻路；持盾仍可开门和按实际上坡路点起跳，不触发随机战斗跳跃。近战被防御打断时保留有效路线并解除重新起步的检查等待。
- 人类受击的基础击退力度减半，站在陆地时限制额外上抛至 0.12，保留原版击退抗性、事件及伤害无敌时间，避免将更快反应变成额外高频伤害。
- 难度时间改为按玩家 UUID 保存累计在线游戏 tick，24000 tick 为个人一天；取消世界日历偏移与 `day sync`，离线、暂停、睡觉跳夜和原版时间命令不推进个人难度。旧日历不迁移，首次使用从个人第 1 天开始。
- 普通自然刷新、区块初始生成、自然大型遭遇和装备成长统一按生成点同维度最近的存活非旁观玩家的个人进度决定；两类信号召唤及既有实体不变。管理员日期指令增加可选玩家参数，控制台须指定在线玩家。
- 在线计时每 200 tick 批量结算，退出和停服补齐余量，沿用世界正常保存；异步生成只读取不可变玩家快照，不访问存档或可变计时表。保留通用配置缺项补全与错误保护，本轮不新增旧配置迁移分支。

#### 装备、配置与维护重构

- 重构装备联动：首次识别斯巴达武器/盾牌时，将可用条目写入原生配置权重池并保存导入记录，实体生成不再事后替换。玩家删除已导入条目后不重复补回，支持自定义权重；空盾牌池不再强制补原版盾牌。刷新旧内置说明但保留自定义备注。
- 增加 `natural_spawn_firearms_enabled` 总开关，控制自然生成及自然大型战斗的人类是否额外携带枪械；原有枪械概率、白名单和成长倍率保留，关闭不影响其他装备与已有单位。成功生成枪械时保留配置池近战备用，均计入总武器数量。
- 生成武器数量上限统一为流浪者 2 把、一至三阶 3 把；新增第二、第三把的条件概率，默认普通近战携带 1/2/3 把的概率为 55%/36%/9%，流浪者为 55%/45%。远程备用与枪械纳入上限，备用武器存入背包；奖励武器池仅参与额外名额，不再替换主手。后续拾取和主人配装不受生成上限限制。
- 下调流浪者默认护甲至皮革 60%、锁链 30%、铁甲 10%，移除钻石和高档沉浸式盔甲套装，默认整体品质低于一阶护甲池。配置升级仅替换完全匹配旧内置默认的流浪者护甲池，保留自定义权重/条目及其他阶级装备；不重写已有实体或主人配装，仍允许拾取战利品。
- 修复近战人类（包括流浪者）回血后持续逃跑：达到恢复作战血量且不再处于低血量时，可在最短撤退阶段结束后恢复战斗，不再必须与追击者拉开 24 格。保留短时高压避险；退出时清理自有逃跑路徑和逃跑标记，上岸交接不再遗留战斗逃跑状态。
- 修复近战双方在台阶或攻击范围边缘互相僵持：控距必须满足实际武器命中框和视线，不能仅凭名义距离停止追击；Better Combat 起手与命中时对准目标，包含高低差。区分本次近战后退/侧绕与旧远程走位，防止移动控制器误取消近战脱困。无有效路径时保留追击重试，按实际位移检测 30 tick 停滞后限频尝试绕行；绕行路径有短时执行窗口，不立即被原路径覆盖，换敌时立即解除该窗口。防御/换武器中断不清空有效仇恨，原地驻守与撤退仍受各自规则控制。
- 区分野生敌对台词与已雇佣士兵的日常、命令和状态反馈，补充中英本地化；聊天消息带士兵名称，避免友方继续使用驱逐玩家的对话。
- 对讲机命令与直接交互共用反馈入口。食物耗尽、真实武器/护甲/盾牌破损会通知主人；切换装备不触发破损报告。
- 雇佣费用同步至客户端，合同说明与实际服务端费用一致；无效物品配置拒绝扣款，支付物品与合同相同时保留手持合同供独立消耗。
- 配置升级统一补齐缺失选项、双语说明与示例，保留已有值和损坏文件；伤害说明明确全局与阶级倍率相乘，不互相覆盖，枪械子弹不叠加近战倍率。
- 自然生成倍率支持小数并补充计算说明。移除流浪者额外 1/200 准入和重复低权重入口，与其他阶级共用自然生成策略，保留唯一权重 10、单只生成入口；日期、安全距离、灯光、密度和遭遇冷却仍生效。
- 增加可关闭的自然装备成长：四阶默认于第 40、50、70、80 天达到原有品质。成长起点、早期装备、枪械概率、附魔、图腾与三叉戟保留规则均可配置，仅在生成时处理，不重写已有单位。信号召唤、刷怪蛋、命令和结构固定单位不受影响。
- 装备配置迁入第七个模块 `equipment.json`，四阶共用近战、远程、备用武器、副手、盾牌和加权护甲套装入口，支持模组物品、数量及附魔。移除旧装备数据包监听、三份旧内置装备表和三阶事后强化补丁。
- 原生装备、斯巴达武器/盾牌、沉浸式盔甲与 TaCZ 纳入统一生成流程，按阶控制自动联动；生成完成后统一应用储备和成长。不再重复抽取远程概率或重复生成三阶装备。
- 修复 Curios 身份牌专属栏位的数据命名空间与玩家分配，保留五种身份牌标签；只识别功能槽中的身份牌，补齐三阶人类的已有背部栏位分配。
- 人类拒绝乘坐船、机械动力坐垫等载具，并清理存档遗留的乘坐/坐下状态，避免被自动吸附后停止战斗。自然索敌允许末影人，仍遵守雇佣交战模式与阵营关系。
- 移除旧寻箱、开箱盖 Goal 及对应方块实体 Mixin、接口和配置，避免无关箱子导航抢占行动；地面拾取与士兵背包界面保留。
- 来袭箭矢预测加入弹道阻力、重力及双方运动的短时碰撞判断；远程攻击与预测举盾使用受限打断策略，举盾时统一朝向威胁，静默远距离威胁不维持格挡。跳跃不抢占防御，旧枪击计时不在重新加载时误判为新攻击。
- 主动拾取许可统一约束各移动/交战模式及经过时拾取。区域驻守的物品与路径均受区域限制，拾取停止时不取消已经启动的战斗导航。
- 将旧 `HostileHumansEquipmentPatch` 拆为正式库存保管与盾牌耐久模块。库存交换只修改指定装备槽，扫描完整 30 格背包；拾取先确认完整容量再提交，满背包暂存有掉落兜底。若其他模组阻止掉落物生成，换装中止并保留原装备，不提交部分库存。
- 原版格挡与 TaCZ 子弹格挡共用盾牌磨损入口，保留已雇佣单位的玩家式耐久和耐久附魔；拒绝非有限伤害，防止异常伤害溢出。破损音效与报告统一按槽位去重，不把副手切换当作破损。
- 移除未注册的旧找水/三叉戟 Goal、空 Goal 基类、被替换的旧移动控制器、空命名桥接、无效连击状态及未读取的旧配置项；清理空 `setCombatTask`、无效跳跃冷却和无作用定时调用。
- 装备生成直接使用正式入口，不再依赖旧生成包装类；备用近战武器只在生成时提供，武器损坏或移走后不再定时凭空制造新武器，仍可使用现有工具或空手反击。
- 配置旧字段只在统一迁移阶段转换，运行读取使用规范化配置，消除重复回退。保留实际需要的存档与配置兼容，不删除仍生效的动画桥接和可选模组入口。
- 护甲、双手与背包的 NBT 编解码共用一个实现，保存空列表时清除旧键，避免已移除物品在复用存档标签后重新出现；保留槽位、耐久和物品 NBT 格式。
- 删除未参与构建的 `basePatch` 资源覆盖，更新中英文 README、配置指南与性能审查记录。新增库存守恒、存档空槽、成长边界、入口连接和发行 JAR 残留检查；这些独立检查不替代实际游戏测试。

### English

This release consolidates equipment, configuration, personal progression, soldier feedback and combat-navigation changes since 3.5.5. Automated checks do not replace in-game acceptance testing.

#### Combat and movement continuity

- Retreats anticipate route endpoints and close pursuers' changing direction, retain existing routes while planning, and bridge exhausted ground routes with collision/floor-checked local steps. Idle recovery and movement helpers no longer overwrite retreat speed. Five-tick progress checks restore movement input before local recovery and independently budgeted path repair, without forcing intentional stationary behavior to move.
- Movement continuity after hits/landings now preserves native escape routes until replacements are ready, keeps retaliation from preempting retreat, and tracks actual progress across path changes. Shared navigation recovery handles moving recovery actions, clears stale movement input, and repairs interrupted retreat routes despite counterfire.
- Better Combat elevation handling shares attack origins and reachable in-box aim points across spacing and attacks, preserves pitch through windup, and handles tall/overhead/lowered targets without relaxing live range or visibility checks.
- Formally hired humans drop their full backpack and equipped inventory with original counts, durability and NBT. Death snapshots preserve hiring status and replace duplicate owned drops; hired reinforcements bypass temporary no-drop flags. Wild humans and unhired temporary support retain their existing rules.
- Better Combat windups retain opponent-facing orientation through delayed impact instead of turning toward navigation nodes. Ground/water movement remains available; facing releases after impact and yields to retreat or invalidated attacks. Hits still use live geometry and visibility.
- Owner logout preserves movement and combat orders, target acquisition and configured pickup. Following pauses when the owner is unavailable without stopping authorized combat; legacy automatic-standby flags are cleared.
- Automatic soldier messages now share per-owner budgets. Chatter defaults to a 60-second interval and three messages per five minutes; status reports have separate limits, bounded batching and deduplication, with critical health prioritized. Limits are configurable with bilingual comments; player-initiated hiring and command confirmations remain immediate.
- Mobile shield defense now restores interrupted ranged routes, preserves melee pursuit and participates in navigation stall recovery. Physical hit reactions are shortened without resetting damage immunity.
- Personal online progression replaces the world calendar. Rank unlocks and spawn equipment use the same nearby player's progress; sleep and vanilla time commands no longer advance difficulty. Commands can target an online player; world-time synchronization is removed. Timers settle in 200-tick batches with logout/shutdown settlement and read-only snapshots for asynchronous spawn queries.

#### Equipment, configuration and maintenance

- Reworked equipment integration: registered Spartan weapons/shields are imported once into native weighted configuration pools with persistent receipts. Spawn-time replacements were removed; deleting imported rows keeps them deleted. Custom weights are respected, and empty shield pools no longer create vanilla shields. Known stock guidance is refreshed without overwriting custom notes.
- Added `natural_spawn_firearms_enabled` for extra firearms in natural spawns and automatic natural battles. Existing firearm chances, whitelists and growth scaling remain; disabling it leaves other gear and existing entities untouched. Gunners retain their configured melee weapon, with both weapons counted toward the spawn cap.
- Unified spawn weapon caps: two for Roamers, three for Tier I–III. Conditional second/third rolls default to 55%/36%/9% for ordinary melee allocations, or 55%/45% for Roamers. Ranged backups and guns count toward the cap; extra weapons go into the backpack. Bonus pools only fill additional slots, never replace the main hand. Later looting and owner loadouts are not capped.
- Lowered default Roamer armor to 60% leather, 30% chainmail and 10% iron, removing diamond and high-end Immersive Armors sets. The default pool is weighted below Tier I. Only the exact former stock Roamer armor pool is migrated; custom entries/weights and other ranks are preserved. Existing gear, owner loadouts and loot pickup are not reset.
- Fixed prolonged retreat after melee humans, including Roamers, recover their health. Once actual health meets the resume threshold and is no longer low, the minimum retreat commitment can end without a mandatory 24-block gap. Short-lived pressure retreats remain protected; stopped retreat goals release their escape routes and flags, including shore handoffs.
- Fixes melee stalemates on steps and near attack-range edges. Spacing requires actual weapon hitbox eligibility and visibility, not nominal reach alone; Better Combat aligns its upswing and impact with the opponent, including height differences. Explicit melee backsteps/flanks are distinct from stale ranged strafing. Failed paths retain pursuit retries; 30 ticks without actual movement trigger a rate-limited alternate-route attempt, with a brief execution window that ends immediately on target change. Defense/weapon interruptions retain valid targets; hold-position and retreat rules remain intact.
- Separates hostile wild-human dialogue from hired soldiers' daily, command and status responses. Adds Chinese/English localization and speaker names, without friendly soldiers using eviction lines.
- Radio commands and direct interactions share feedback. Owners receive food-exhaustion and actual equipment-break notices; equipment switches do not count as breakage.
- Synchronizes recruitment terms to clients so contract descriptions match server payment. Invalid item settings reject payment; when contracts are also currency, the held hiring contract is reserved for its separate consumption.
- Configuration upgrades fill missing options and bilingual guidance while preserving custom values and malformed files. Damage guidance explains multiplicative global/tier factors and prevents firearm bullets from also receiving melee scaling.
- Documents fractional natural-spawn multipliers. Removes the Roamer's extra 1/200 gate and duplicate low-weight entry, sharing the common admission policy while retaining its weight-10 single-unit entry and existing date, safety, light, density and cooldown checks.
- Adds optional natural equipment progression, reaching baseline quality on days 40, 50, 70 and 80 by default. Start dates, early equipment, firearm chances, enchantments, totems and tridents are configurable. Progress applies at spawn, not to existing units; flares, beacons, eggs, commands and fixed structure units are exempt.
- Moves equipment customization into the seventh module, `equipment.json`, with shared melee, ranged, reserve, offhand, shield and weighted armor pools for all four ranks. Supports mod items, quantities and enchantments; removes legacy equipment datapack loading and the separate Tier III enhancement patch.
- Unifies native gear and optional Spartan weapons/shields, Immersive Armors and TaCZ generation with per-rank switches, followed by reserves and progression. Removes duplicate ranged rolls and Tier III generation passes.
- Fixes the Curios badge slot namespace and player assignment, retaining all five badge tags. Reads functional badge slots only and includes Tier III in the existing back-slot assignment.
- Rejects boats, Create seats and other mounts, clearing saved riding/sitting state to prevent automatic mounting from disabling combat. Autonomous targeting includes Endermen while respecting soldier modes and faction relationships.
- Removes the old chest-seeking/lid-opening goal, associated block-entity mixin, interface and setting. Ground-item pickup and soldier inventories remain available.
- Incoming-arrow prediction accounts for drag, gravity and both entities' motion. Ranged attacks and predictive blocks use bounded interruption windows with consistent defensive facing; quiet distant threats no longer sustain blocking. Defensive movement does not inherit jump conflicts or stale loaded gunfire timers.
- Applies pickup permissions consistently across movement/combat modes and incidental pickup. Area-guard items and routes remain within the area; stopping pickup does not cancel a newly started combat route.
- Replaces `HostileHumansEquipmentPatch` with formal inventory custody and shield-durability modules. Single-slot exchanges scan all 30 backpack slots; pickup commits only with sufficient total capacity, and stowing has a world-drop fallback. If another mod cancels the drop, replacement aborts without losing the old equipment or committing a partial inventory transfer.
- Vanilla and TaCZ shield wear share one policy, retaining player-style durability and Unbreaking for hired soldiers. Rejects non-finite damage and prevents durability-cost overflow. Break sounds/reports share per-slot deduplication without treating offhand swaps as breakage.
- Removes unregistered water/trident goals, an empty goal base, replaced movement controller, no-op naming bridge, inactive flurry state and unused legacy options. Deletes empty combat-task refresh calls, a write-only jump cooldown and ineffective periodic work.
- Uses the formal spawn pipeline directly. Melee reserves are generated only at spawn rather than manufactured periodically after loss or breakage; existing tools and empty-handed retaliation remain available.
- Normalizes legacy configuration aliases once instead of repeating fallbacks in runtime getters. Retains necessary save/config migrations, animation bridges and optional-mod entrypoints.
- Shares armor, hand and backpack NBT codecs. Empty saves remove stale keys, preventing removed items from reappearing when a tag is reused, while preserving slot, durability and item-NBT formats.
- Removes unused `basePatch` resource overlays and updates both READMEs, configuration guidance and audit records. Adds item-conservation, empty-save, growth-boundary, wiring and packaged-artifact checks; independent checks do not replace in-game testing.

## 3.5.5 — 2026-10-01（累计 3.5.0 之后的更新，不含 3.5.0）

### 简体中文

- 修复近战控距后退受阻或双方位置重叠时持续停止导航的问题：尝试安全后退及两侧脱困，均不可行时允许普通追击重寻路，不再等待外力推动。
- 近战保持距离统一设在实际攻击范围以内，留出至少 0.5 格余量；修正短攻击范围被固定最小控距抬高的问题，追击启动与控距使用同一门槛。
- 已雇佣人类的护甲磨损由整套共享冷却改为四个部位各自独立的 3 秒冷却，每件每次最多损耗 1 点耐久；仍应用耐久附魔与火抗物品保护。
- 模组衍生源码的许可证统一为 GPL-2.0-only，更新对应许可证与说明。
- 增加各阶雇佣支付物品与数量配置，支持其他模组注册的物品 ID，默认绿宝石及数量不变；合同提示与费用不足提示显示对应物品，无效配置阻止扣款，创造雇佣仍免费。
- Better Combat 缺少当前武器配置时回退到原版近战、攻击距离和冷却；近战武器破损后可使用背包工具，没有工具则空手反击。
- 区域驻守可反击远距离伤害来源，保留战斗中的近距离威胁切换；越界作战最多持续 20 秒，随后返回驻点，返回中受击可重新锁敌并重置计时。闲暇主动拾取不再被驻守返回导航抢占，物品与拾取路径限制在驻守区域内。
- 名册排序、阶级分组与人数统计共用不可变视图；雇佣、解雇、转移与重排立即失效，避免面板刷新重复筛选全队。
- 掉落物候选在同一筛选批次共用背包容量与替换摘要，不再对每个候选重复遍历全部槽位；空位、同类合并、受保护物品及价值替换门槛保持不变，摘要不用于实际物品交换或跨 tick 缓存。
- 水中上岸助跳按方块列去重，减少重复流体和碰撞探测，并阻止探测未加载区块；水面扫描复用同一次扫描已读取的流体。药水掉落评分由多遍效果扫描改为单遍，保留原优先级；恢复食物评分减少重复属性读取。
- 射界搜索与紧急撤退/岩浆脱困增加服务器级独立候选预算及轮转等待，减少多单位同 tick 集中寻路；等待保留搜索进度，不触发失败兜底，射界搜索不会因持续等待被旧超时重置。战术失败兜底不再叠加到最后一批寻路。
- 调查与警觉事件共享同 tick 范围查询，减少破坏方块、开门、放置与受伤时的重复广播扫描；装备耐久、附魔或装备更换后及时清除旧掉落物价值判断，保留失败寻路冷却。恢复药水选择不再重复解析同一份效果。
- 战术选位也改为完整分批比较，每人每 tick 最多检查 4 个候选；保留射界与盟友间距评分及寻路前上界过滤，可用临时路径先执行，后续改善路径时才更新导航。
- 优化同 tick 的身份牌与枪械背包查询、射界候选点预筛选、失败寻路重试、分批找水与岩浆脱困、战术选位盟友查询和掉落物评分；减少重复反射及不必要的审计字符串构造。
- 合并同 tick 的主人保护范围查询，保留逐目标实时关系判断；背包装备一次分类、名册排序复用、读取请求独立限流与分页处理减少重复计算。已加载单位的一键召回也分批执行，单个召回保持即时响应。
- 撤退选位分帧比较，每个人类每 tick 最多尝试 4 条候选路径，先执行可用路径并继续搜索；避免启动与 tick 重复规划、无效候选重复寻路及补充搜索反复停止现有导航。
- 大量掉落物的评分分批执行，每 tick 最多处理 64 个新候选；保留当前物品堆优先策略，背包变化后使旧筛选结果失效。减少碰撞开门候选的集合分配及提前跳跃的重复方块形状探测。
- 射界搜索分批完整推进，区分未完成与失败，避免中途被上岸兜底覆盖；减少没有弓弩时的重复背包扫描与恢复物品布尔查询的全量评分，保留原武器和补给优先级。
- 召回未加载士兵不再同步等待区块加载：每 tick 最多提交 4 个加载请求，全服最多维持 16 个活动召回票据；排队时间不消耗加载超时。日常雇佣记录更新不再重复生成完整实体 NBT，保存与离开时仍保留完整快照。
- 增加可配置的新手安全期与自然生成分阶解锁：默认安全期 1 天，流浪者、一阶、二阶、三阶最早于第 3、5、10、20 天出现。自动大型战斗同样遵循解锁规则，两类信号装置召唤不受影响。
- 增加 `/hostilehumans day get/set/add/sync` 指令。所有维度使用主世界日历加存档偏移，睡觉会推进模组日期；修改天数后仍继续随主世界流逝，可随时重新对齐。
- 旧配置自动补入缺失的新选项，不重置已有设置；世界时间偏移随存档保存。
- 配置拆分到 `config/hostile_humans_unified/` 下的六个模块文件，保留双语说明和枪械名单示例。旧单文件自动迁移、备份并保留；新模块优先，损坏模块独立回退，不覆盖输入或重置其他模块。

### English

- Fixes melee spacing repeatedly stopping navigation when backward movement is blocked or fighters overlap. Humans try safe backward and lateral steps, then yield to normal pursuit if none is available instead of waiting for an external push.
- Melee spacing stays inside effective attack reach with at least half a block of margin. Short-reach weapons no longer inherit an excessive fixed minimum; pursuit and spacing use the same threshold.
- Hired humans' armor now uses independent three-second wear cooldowns for all four slots instead of one shared set-wide gate. Each piece loses at most one durability point per event, retaining Unbreaking and fire-resistant-item handling.
- Standardizes the derivative mod source license as GPL-2.0-only and updates the corresponding license notices.
- Adds per-tier recruitment item IDs and quantities, supporting registered items from other mods while retaining emerald defaults. Contract and insufficient-payment tooltips reflect the selected item; invalid settings block payment, and creative hiring remains free.
- Missing Better Combat weapon profiles fall back to vanilla melee, reach and cooldown. After a weapon breaks, humans can use stored tools or fight empty-handed when no tool remains.
- Area guards can retaliate against distant attackers while retaining nearby-threat switching. Out-of-area combat lasts up to 20 seconds before returning to the post; incoming hits can restart combat and the timer. Idle pickup retains movement ownership and stays within the guarded area, including its path.
- Roster ordering, tier groups and counts share an immutable view, invalidated immediately by hiring, dismissal, transfers and reordering to avoid repeatedly filtering the whole roster.
- Loot candidates share a batch-local inventory capacity/eviction summary instead of repeatedly traversing every slot. Empty slots, compatible stack merging, protected items and replacement thresholds retain their behavior; the summary is never used for actual transfers or cached across ticks.
- Shore-step probes deduplicate block columns, avoid repeated fluid/collision reads and reject unloaded chunks. Surface scans reuse fluid states already read during the same scan. Potion loot scoring uses a single effect pass with the original priority order; recovery-food scoring avoids repeated property reads.
- Firing-lane and emergency retreat/lava searches use separate server-wide candidate budgets with rotating waits to reduce simultaneous pathfinding bursts. Waiting retains search progress instead of triggering failure fallbacks or stale-search resets. Tactical fallbacks no longer add an extra path to the final candidate batch.
- Investigation and alert events share same-tick neighborhood queries across block, door and damage notifications. Equipment, durability and enchantment changes invalidate stale loot-value decisions without clearing failed-path cooldowns. Recovery-potion selection avoids parsing the same effects twice.
- Tactical repositioning now compares all candidates incrementally, evaluating at most four per human per tick. Firing-lane and ally-spacing scores and pre-path upper-bound filtering are retained; provisional routes run immediately and navigation changes only when the selected route improves.
- Optimizes same-tick badge and stored-firearm queries, firing-position prefilters, failed path retries, incremental water/lava searches, tactical ally queries and loot scoring; reduces repeated reflection and unnecessary audit string construction.
- Reuses same-tick owner-protection spatial queries while checking each target's relationships live. Single-pass equipment classification, cached roster ordering, independent read throttling and leaner pagination reduce repeated work. Bulk recalls of loaded soldiers are also processed incrementally; individual recalls remain immediate.
- Retreat route selection is incremental, with at most four candidate path attempts per human per tick. Usable provisional routes run while selection continues; duplicate planning, repeated candidate paths and unnecessary navigation restarts are avoided.
- Loot scoring is incremental, examining at most 64 new candidates per tick while retaining committed-pile priority. Inventory changes invalidate stale screening decisions. Passage checks allocate fewer collections, and predictive jumps avoid repeated shape probes of the same block.
- Firing-lane searches retain and process all candidates incrementally, distinguishing pending work from failure so shore fallbacks do not interrupt them. Repeated bow/crossbow absence scans and full scoring for supply-presence queries are reduced without changing weapon or supply priorities.
- Unloaded-soldier recall no longer blocks while loading chunks: at most four loading requests start per tick, with sixteen active recall tickets server-wide. Queue time does not consume the loading timeout. Routine roster refreshes avoid full entity NBT snapshots; saves and entity removal retain complete snapshots.
- Adds configurable beginner protection and natural-spawn unlock dates. By default, protection lasts one day; Roamers and Tiers I–III unlock on days 3, 5, 10, and 20. Automatic battle encounters respect these dates; support flares and hostile beacons remain unaffected.
- Adds `/hostilehumans day get/set/add/sync`. All dimensions share the Overworld calendar plus a saved offset. Sleeping advances the mod date, edited dates continue following the Overworld, and the offset can be reset to resynchronize.
- Missing options are added to existing configurations without resetting custom settings. Calendar offsets persist per world.
- Splits configuration into six module files under `config/hostile_humans_unified/`, preserving bilingual descriptions and firearm-list examples. Legacy settings migrate with a retained backup. New modules take precedence; malformed modules fall back independently without overwriting input or resetting other modules.

## 3.5.0 — 2026-09-29（累计 3.4.0 之后的更新）

### 简体中文

- 增加士兵的 Better Combat 兼容选项，默认启用。检测到 Better Combat 时，士兵读取武器攻击范围、攻击形状与冷却，并可攻击范围内的多个目标；未安装时自动回退到模组原有战斗逻辑。
- 改进 Better Combat 攻击动画，使士兵的身体、护甲和手持武器随攻击动作连续变化；支持双手武器的攻击动作与待机姿势。
- 改进 TaCZ 枪手的瞄准、射击和换弹动画及换弹音效，并调整开火时的朝向，使枪械保持对准目标。
- 枪手持续失去目标视野、且目标仍在 10 格外时，会暂时放宽优势距离，靠近或侧移寻找射界；短暂遮挡不触发，重新看见目标后恢复正常控距。
- 修正盾牌切换与真实破损的音效判定；护甲和工具仅在实际损坏时播放对应的原版破损音效。
- 近战控距会读取当前武器的攻击距离：安装 Better Combat 时使用其武器范围，未安装时使用 Forge 实体攻击距离属性。人类能够面朝目标后退调整距离。
- 修复已雇佣人类使用近战武器时耐久不正确的问题；武器与护甲均应用耐久附魔判定。已雇佣人类的整套护甲增加共享磨损冷却，降低高频受击造成的耐久消耗。
- 调整玩家动画资源包兼容：Better Combat 与 TaCZ 的战斗动画在相应动作期间优先显示，其他状态可继续使用资源包动画。

### English

- Adds a Better Combat option for soldiers, enabled by default. When Better Combat is installed, soldiers read weapon range, attack shape, and cooldown, and can hit multiple targets within an attack. Without it, they fall back to this mod's combat logic.
- Improves Better Combat attack animations so the soldier's body, armor, and held weapon move continuously with the attack. Two-handed weapons support both attack animations and an idle pose.
- Improves TaCZ gunner aiming, firing, and reloading animations and reload sounds. Firing alignment is adjusted to keep guns pointed at the target.
- After prolonged loss of sight with the target more than 10 blocks away, gunners temporarily relax their preferred range to approach or flank for a firing lane. Brief occlusion does not trigger this behavior, and normal spacing resumes once the target is visible.
- Distinguishes shield switching from actual breakage; vanilla armor and tool break sounds play only when the corresponding item really breaks.
- Melee spacing reads the current weapon's attack reach: Better Combat weapon range when installed, or Forge's entity-reach attribute otherwise. Humans can step backward while facing their target to adjust distance.
- Fixes durability loss for hired humans' melee weapons. Durability enchantments apply to weapons and armor. Hired soldiers now share an armor-wear cooldown across the full armor set to reduce durability loss under rapid attacks.
- Improves compatibility with player-animation resource packs: Better Combat and TaCZ combat animations take priority during their corresponding actions, while resource-pack animations remain available in other states.

## 3.4.0 — 2026-09-28（含 3.3.14 的累计更新）

### 简体中文

- 延续 3.3.14 的独立创造模式选项卡：统一展示模组物品，并按类别排列。
- 对讲机士兵列表和详情为四种阶级显示不同颜色与文字徽章，便于直接辨认阶级；徽章配色保持低饱和度，并保留原有状态与血量信息。
- 和平模式仅清除野生人类；已雇佣士兵保留，但停止战斗。处理和平模式切换及重新加载时的名册状态。
- 对讲机改为像素风格名册界面，支持逐名查看状态、单独召回、调整指令、手动刷新、排序和滚动浏览；未加载士兵的状态可查看，但操作按钮锁定。批量召回移入界面，避免下蹲右键误触。
- 优化对讲机列表的悬停预览、空白区域点击及排序操作，减少指针经过士兵按钮间隙时的信息闪烁；士兵指令界面采用相近风格，并支持 Esc 返回上一级。
- 加入低频名册核查及实体移除后的及时清理，减少失效的雇佣记录占用名额；区块未加载不直接视为实体消失。
- 霰弹枪优势保持距离从 3–8 格调整为 1–6 格；人类枪械瞄准点下移至目标碰撞箱高度的 70% 或更低的原有眼睛高度，普通射击与撤退反击均适用。
- 源码检查不能代替游戏内验证。对讲机交互、和平模式跨区块行为及枪械命中仍需实测。

### English

- Carries forward the 3.3.14 dedicated Creative Mode tab, grouping the mod's items by category.
- Adds distinct muted-color, labeled tier badges to the radio's soldier list and detail view, keeping status and health information visible.
- Peaceful difficulty removes wild Humans but retains hired soldiers in a non-combat state, including roster handling across difficulty changes and reloads.
- Reworks the radio as a pixel-style roster with individual status, recall and orders, manual refresh, sorting, and scrolling. Unloaded soldiers remain visible while their order controls are disabled. Recall-all moves into the UI to prevent accidental sneak-right-click recalls.
- Improves roster hover previews, blank-area clicks, and ordering so crossing gaps between soldier rows does not flicker the overview. The soldier command screen shares the visual style and Esc returns to the previous menu.
- Adds a low-frequency roster audit and immediate cleanup after actual entity removal, without treating an unloaded chunk alone as deletion.
- Changes the shotgun's preferred engagement band from 3–8 to 1–6 blocks. Gun aim now uses 70% of the target hitbox height or the original eye height, whichever is lower, for both normal fire and retreating counterfire.
- Source checks do not replace in-game verification of radio interactions, Peaceful behavior across chunks, or gun hits.

## 3.3.14 — 2026-09-28

### 简体中文

- 新增“敌对人类：统一版”创造模式独立选项卡，以一阶身份牌为图标，集中展示两个保留的 modId 下注册的全部物品。
- 移除刷怪蛋和材料选项卡中的旧入口，避免物品在多个分类重复出现。新增物品会自动进入独立选项卡。
- 自动构建与检查不能代替游戏内确认选项卡图标、名称和物品显示。

### English

- Adds a dedicated Hostile Humans Unified Creative Mode tab with a Tier I identity badge icon. It collects all items registered under both retained mod IDs.
- Removes the old Spawn Eggs and Ingredients tab entries to avoid duplicate listings. Newly registered items join the dedicated tab automatically.
- Automated build and checks do not replace in-game verification of the tab icon, title, and contents.

## 3.3.13 — 2026-09-27

### 简体中文

- 已雇佣人类受到一次伤害时，每件符合条件的护甲最多消耗 1 点耐久，不再随该次伤害量增加磨损。
- 野生人类原有的护甲破损机制与盾牌耐久机制不变。
- 自动检查不等同于游戏内的受击与装备耐久验收。

### English

- Each eligible armor piece worn by a hired Human now loses at most one durability point per damaging hit, regardless of the hit's damage amount.
- Wild Humans' armor-break behavior and shield durability behavior are unchanged.
- Automated checks do not replace in-game validation of damage and equipment wear.

## 3.3.12 — 2026-09-27

### 简体中文

- 阶级随机血量只修复本模组生成的血量值或原始 50/60 血基础值，不再每刻覆盖其他模组设置的最大生命值基础值。
- 逃跑、恢复、近期伤害压力和雇佣单位低血量警告继续依据实体当前的最大生命值计算比例；例如当前最大生命值为 500 时，30% 对应 150 血，而不是原始基础血量的 30%。
- 加入外部设置 500 血不会被阶级随机血量覆盖的策略检查。仍需在安装相关血量修改模组的游戏环境中验收。

### English

- Tier health rolls now repair only this mod's rolled value or the original 50/60-health base, rather than overwriting another mod's maximum-health base every tick.
- Retreat, recovery, recent-damage pressure, and hired-unit low-health warnings continue to use the entity's current maximum health. At a live maximum of 500, for example, 30% means 150 health rather than 30% of the original base.
- Adds a policy check ensuring an external 500-health base is preserved. In-game validation with the relevant health-modifying mod is still needed.

## 3.3.11 — 2026-09-27

### 简体中文

- 修复弓、弩等远程单位切换近战武器后残留侧移指令的问题，避免近战时继续横向漂移。
- 远程单位射击时在移动控制更新后重新面向目标，不改变原有寻路与侧移命令。
- 调整远程走位的客户端动作表现：躯干、肩部、双腿和装备整体平滑偏转，最大偏转角度为 45°；切换近战武器后退出该姿态。
- 构建与自动检查已通过；实际动画、命中表现及多人同步仍需游戏内验证。

### English

- Clears leftover ranged-strafe commands when bow, crossbow, and other ranged users switch to melee weapons, preventing lateral drift during melee combat.
- Restores target-facing yaw after movement control updates during ranged attacks without replacing the active path or strafe command.
- Smooths the client-side ranged movement pose: torso, shoulders, legs, and equipment turn together, with a maximum 45° offset. Switching to melee exits this pose.
- The build and automated checks passed; animation, hit behavior, and multiplayer synchronization still need in-game validation.

## 3.3.7 — 2026-09-27

### 简体中文

- 弓、弩和三叉戟的完整射击周期再次延长，使持续射速约为 3.3.6 的 70%；三叉戟近战攻击冷却不变。
- 各阶弓的默认散布改为 1.5°、1.2°、0.9°、0.6°；弩改为 1.2°、0.9°、0.6°、0.3°，阶差仍为 0.3°。三叉戟散布不变。
- 弓与弩的箭矢伤害倍率再提高 20%；三叉戟伤害维持不变。
- 已存在的实例配置不会仅因更新 JAR 而自动覆盖，测试实例中的旧默认散布需同步更新；实战射速和命中效果仍需游戏内验收。

### English

- Lengthens the full bow, crossbow, and trident firing cycles again, targeting about 70% of version 3.3.6's sustained fire rate. Trident melee cooldown is unchanged.
- Default bow spread is now 1.5°, 1.2°, 0.9°, and 0.6° by tier; crossbow spread is 1.2°, 0.9°, 0.6°, and 0.3°. The 0.3° tier step remains, and trident spread is unchanged.
- Raises bow and crossbow projectile damage by another 20%; trident damage is unchanged.
- Existing instance configurations are not automatically overwritten by a JAR update; old default spread values in the test instance must also be updated. In-game firing rate and accuracy still need validation.

## 3.3.6 — 2026-09-27

### 简体中文

- 水中拾取时由拾取目标暂时占用移动控制；同一堆有用物品拾取完毕后，才恢复闲置上岸策略，避免在水中原地反复转向。
- 优先清理当前物品堆；短时间记住不值得拾取、无法到达或拾取失败的物品，并在物品移动或内容变化后重新评估，减少来回奔走及重复筛选。
- 弓、弩、枪和三叉戟在水中失去射界时优先寻找可到达的射击位置；找不到时限时尝试上岸，恢复射界或开始逃跑后交还移动控制。
- 构建和自动检查不能替代实际游戏中的复杂水域、岸高及战斗验收。

### English

- Water-loot pursuit temporarily owns movement until useful items in the current pile are collected, then yields to idle shore seeking instead of repeatedly turning in place.
- Prefer finishing the current loot pile and temporarily remember unwanted, unreachable, or failed items; re-evaluate when an item moves or changes to reduce ping-pong movement and repeated scoring.
- Bow, crossbow, gun, and trident users seek a reachable firing lane when sight is blocked in water, then make a time-limited shore attempt if none is found. Movement control is released when sight returns or retreat begins.
- Build and automated checks do not replace in-game validation around complex water, shoreline heights, and combat.

## 3.3.5 — 2026-09-27

### 简体中文

- 将弓手射击侧移的整体速度从上一版降低 30%。
- 修复弩手仅在 20–24 格狭窄距离内侧移的问题：敌人进入 16 格内才后撤，退到 20 格后恢复侧移，以避免边界抖动。
- 弩手侧移速度低于弓手；弩手的远距离狙击、失去射界后的重新选位和紧急撤退仍维持原有优先级。
- 构建与策略检查不等同于游戏内验收，实际走位效果仍需测试。

### English

- Reduces the bow user's overall firing-strafe speed by 30% from the previous release.
- Fixes crossbow strafing being limited to a narrow 20–24-block band: retreat now starts inside 16 blocks and strafing resumes at 20 blocks to avoid boundary oscillation.
- Crossbow users strafe more slowly than bow users while retaining long-range shots, firing-lane repositioning, and emergency retreat priorities.
- Build and policy checks do not replace in-game movement testing.

## 3.3.4 — 2026-09-27

### 简体中文

- 改进弓手与弩手的射击侧移：提高侧移加成，并修正地面与水中侧移控制被寻路或低速系数覆盖的问题。
- 枪手在限定的交战区域内拉开身位、回摆及调整射角；侧移时同步修正与目标的距离，避免持续绕向同一侧或频繁左右抖动。
- 水中战斗路径长期无法缩短目标距离时启用短时直接推进，使枪手仍会尝试进入对应武器的优势射程。
- 继续修正水中战斗、拾取和岸边移动的优先级；本版本的实际侧移速度与复杂水域行为仍需游戏内验收。

### English

- Improves bow and crossbow firing strafes with a stronger speed bonus and fixes land/water strafe inputs being overridden by navigation or low-speed control factors.
- Gunners reposition and return within a bounded combat area, adjusting their distance and firing angle instead of circling endlessly or rapidly twitching left and right.
- When a water-combat path fails to close the gap for an extended period, Humans briefly steer toward the target so gunners can reach their weapon-specific preferred range.
- Further refines water-combat, pickup, and shore-movement priorities. Actual strafe speed and complex water scenarios still require in-game validation.

## 3.3.3 — 2026-09-27

### 简体中文

- 将人类在水面的站位略微下调，让身体更多地浸入水中；上岸助跳仍使用独立的岸面高度判定。
- 弓手与弩手在射击侧移时获得临时的 50% 移速提升，不改变追击和撤退速度。
- 改进浅层流动水中的上岸辅助：识别一格高的干燥落脚点、按高度延长受限助跳，并避免刚离开水面就过早停止向岸边移动。
- 构建及静态检查不等同于游戏内验收，仍需测试流动水、源头水和不同高度的岸边。

### English

- Lowers the Human's resting position slightly in water so more of the body is submerged; bank-step assistance retains separate landing-height checks.
- Grants bow and crossbow users a temporary 50% movement-speed increase while firing and strafing, without changing pursuit or retreat speed.
- Improves shallow flowing-water exits by identifying dry one-block ledges, extending a height-limited bank step as needed, and continuing the shoreward push after crossing the waterline.
- Build and static checks do not replace in-game validation of flowing water, source blocks, and different bank heights.

## 3.3.2 — 2026-09-27

### 简体中文

- 优化水中战斗、寻岸和浅水拾取的优先级；改进导航卡点恢复，并减少不必要的路径与方块探测。
- 修复远程人类在战斗结束后可能一直保持近战武器和举盾状态的问题；调整装备破损音效。
- 战斗中会重新评估近处合法敌人：远处目标近期威胁较低时优先处理明显更近的目标，同时保留雇佣模式的仇恨授权限制。
- 本版本仍需在游戏中验证目标切换、水中移动和不同武器的实际行为。

### English

- Refines priorities for water combat, shore seeking, and shallow-water loot; improves recovery from stalled navigation while reducing unnecessary path and block probes.
- Fixes ranged Humans sometimes remaining on melee weapons and shield use after combat; adjusts equipment-break sounds.
- Re-evaluates nearby eligible enemies during combat: a significantly closer threat can take priority when the distant target has dealt little recent damage, without bypassing hired-unit authorization rules.
- In-game validation is still needed for target switching, water movement, and weapon behavior.

## 3.3.1 — 2026-09-27

### 简体中文

- 贴岸移动时，同一 tick、同一位置及方向的干燥落脚点检测只运行一次；位置或方向改变后立即重新检测，减少反复读取方块与流体状态。
- Java 17 下完整 `clean build --offline` 与项目 `check` 通过；未启动游戏进行实测。
- 这是 3.3.0 发布后的性能审查修正；尚未取得游戏内主线程采样，不能据此认定所有生物卡顿已完全消除。

### English

- Reuses the dry-landing probe result for the same Human, tick, position, and movement direction; any position or direction change triggers an immediate fresh probe, reducing repeated block and fluid reads near shore.
- The full Java 17 `clean build --offline` and project `check` passed; Minecraft was not launched for live testing.
- This is a post-3.3.0 performance-audit fix. An in-game main-thread profile is still needed before claiming that all-entity stutter is fully resolved.

## 3.3.0 — 2026-09-27

### 简体中文

- 人类在水中保持直立，不再为了追逐较低的目标下潜；依据真正的水面高度上浮，脚部接近水面后平稳停住。
- 入水时临时提高击退抗性，离水后撤销；受到箭矢等攻击时不再被反复推离目标。
- 战斗与逃跑继续由战斗 AI 主导，可以渡河、还击和选择干燥岸边作为战术位置；闲暇时主动寻岸，不强制战斗单位上岸。
- 水陆交界不再因眼睛入水或站立碰撞结果反复切换导航并中断路径。
- 上岸路径搜索增加每服务器 tick 的总配额，限制完整寻路的距离；远岸仍可作为方向引导。缩小大批人类同时入水时的即时岸边扫描。
- 远程射界搜索先排除明显不合适的候选点；战术侧移先用分数上界过滤，再运行完整寻路，保持原有可达候选的选择规则。
- 避免已死亡并从数据索引移除的人类在最后一次 tick 中反复产生误导性警告。
- 已完成编译与策略检查；尚需用户在游戏内验证水面、渡河、上岸和大量单位交战时的实际表现。

### English

- Humans remain upright in water and no longer dive after lower underwater targets. Buoyancy now uses the actual top of the water column and settles when the feet approach the surface.
- A temporary knockback-resistance modifier applies in water and is removed on land, preventing repeated arrow hits from pushing combatants back indefinitely.
- Combat and retreat keep movement ownership: Humans can cross water, fight, and prefer a dry tactical position when available. Idle Humans seek shore without forcing combatants to do so.
- Waterline navigation no longer repeatedly switches and cancels paths based on eye submersion or shoreline standing clearance.
- Shore pathfinding has a per-server-tick budget and a bounded full-path distance; distant banks remain available for directional steering. Immediate scans are smaller when many Humans enter water together.
- Ranged firing-position search rejects obviously unsuitable candidates before pathfinding. Tactical repositioning uses score upper bounds to avoid paths that cannot win, preserving the reachable-candidate selection rule.
- Prevents misleading missing-index warnings from removed Humans during their final tick.
- Compilation and policy checks are complete; in-game verification of surfacing, crossing, shore exits, and large battles remains with the player.

## 3.2.0 — 2026-09-26

### 简体中文

- **贴岸上岸助跳**
  - 只有寻岸路径前方存在近距离的干燥高岸，且生物接近水面时，才触发短促、限高的上推和向岸助力。
  - 助跳持续时间与触发冷却受限，深水中不会触发，避免再次从水中异常跃出。
- **水中姿势与移动**
  - 大多数浅水及接近水面的移动不再维持游泳姿势；只有需要持续游泳时才使用游泳姿势。
  - 修复寻岸范围不足及贴岸时无法上岸的问题，并限制助跳只在岸边触发，避免在水中被抛向高空。
  - 搜索范围扩展至 128 格并按多个方向分环采样；闲暇时即使暂时找不到完整路径，也会朝最近的干燥落脚点移动并定期重试。
  - 闲暇时主动寻岸；战斗与撤退由各自 AI 主导移动。战斗寻路可把水视为可通行路线，允许为追击河对岸目标而渡水，也能沿战斗路径踏上岸边。
  - 岸边辅助不再抢占战斗的 MOVE 控制；出现战斗目标或逃跑状态时立即让出导航，避免不同 AI 反复覆盖路径。闲置的人类仍可优先于跟随/巡逻等待命移动离开水域。
  - 给岸边搜索设置硬预算：每次最多检查 64 个方块列、只尝试 2 个候选路径，并按单位错峰；避免同步寻路长时间占用集成服务器 tick 线程、拖慢全世界生物。
  - 枪械、弓、弩、三叉戟的战斗仍可正常瞄准、攻击与移动；闲暇寻岸时才由岸边辅助控制导航。
- **验证**
  - Java 17 下执行 `gradlew clean build --offline` 成功；项目配置的 Gradle `check` 检查通过，包括 62 项战斗压力策略检查和 10,332 项运行策略检查。
  - 未启动 Minecraft 进行游戏内测试。

### English

- **Controlled shore pop**
  - A brief, capped upward impulse and small forward push now trigger only near the surface when a nearby dry, higher landing lies ahead on the shore route.
- **Water posture and movement**
  - Humans no longer retain the swimming pose in most shallow-water and near-surface movement; the pose remains for situations that require sustained swimming.
  - Fixes shore-search and shore-exit issues, and confines the small pop to the bank to prevent Humans from being launched high into the air while in water.
  - Shore searches cover up to 128 blocks with ring-based directional sampling. While idle, Humans keep moving toward a dry fallback and retry periodically if a complete route is temporarily unavailable.
  - Humans seek shore while idle; combat and retreat AI retain movement ownership. Combat pathfinding treats water as traversable, allowing pursuit across rivers and movement onto shore along the combat route.
  - Shore assistance no longer claims combat MOVE control and yields as soon as a combat target or retreat is active, preventing competing goals from repeatedly replacing each other's navigation. Idle Humans can still leave water ahead of follow/patrol movement.
  - Shore searches have a hard budget of 64 inspected block columns and two path candidates per attempt, staggered per Human. This prevents synchronous pathfinding from monopolizing the integrated-server tick and stalling all entities.
  - Gun, bow, crossbow, and trident users keep their normal combat aiming, attacking, and movement; shore assistance controls navigation only while idle.
- **Validation**
  - `gradlew clean build --offline` succeeded on Java 17; Gradle `check` passed, including 62 combat-pressure policy checks and 10,332 runtime-policy checks.
  - Minecraft was not launched for in-game testing.

## 3.1.34 — 2026-09-26

### 简体中文

- **扩展主动寻岸**
  - 将寻岸搜索范围从 16 格扩展到 48 格，优先选择可达的干燥落脚点。
  - 寻岸导航长时间没有进展时，会重新搜索路线并尝试不同岸边落点。
  - 不再因普通导航正在移动或刚受过伤而阻止闲置单位寻岸；战斗、逃跑、驻守以及跟随水中玩家的优先级保持不变。
- **验证**
  - Java 17 下执行 `gradlew build --offline` 成功；项目配置的 Gradle `check` 检查通过。
  - 未启动 Minecraft 进行游戏内测试。

### English

- **Expanded autonomous shoreline seeking**
  - Expanded shore searches from 16 to 48 blocks, prioritizing reachable dry standing positions.
  - Shore navigation now searches for a different route or bank position when progress stalls.
  - Ordinary navigation and recent damage no longer prevent idle Humans from seeking shore; combat, fleeing, guard orders, and following an owner who is still in water retain priority.
- **Validation**
  - `gradlew build --offline` succeeded on Java 17; the project's Gradle `check` tasks passed.
  - Minecraft was not launched for in-game testing.

## 3.1.33 — 2026-09-26

### 简体中文

- **修复寻岸卡滩**
  - 寻找干燥岸边时优先使用地面导航，水中导航作为无法到达时的备用方案。
  - 仅当寻岸路径指向更高的岸边节点时，增加平滑的爬升辅助，让直立状态下的人类能够越过浅滩最后一级；普通上浮仍受原有限速控制。
- **验证**
  - Java 17 下执行 `gradlew clean build --offline` 成功；Gradle `check` 及项目行为、招募、兼容性、配置和结构数据检查通过。
  - 未启动 Minecraft 进行游戏内测试。

### English

- **Shoreline pathing fix**
  - Shore searches now prefer ground navigation for dry destinations, with water navigation as a fallback.
  - A smooth climb assist is enabled only when the shore route's next waypoint is higher, helping upright Humans clear the final shallow bank while normal surfacing remains capped.
- **Validation**
  - `gradlew clean build --offline` succeeded on Java 17; Gradle `check` and the project's behavior, recruitment, compatibility, configuration, and structure-data checks passed.
  - Minecraft was not launched for in-game testing.

## 3.1.32 — 2026-09-26

### 简体中文

- **水中行为补全**
  - 提前启动缺氧上浮，并恢复足够的持续上升速度，避免接近水面时被过低的速度上限卡住。
  - 角色露出水面后继续保持稳定的水面移动状态，抑制惯性导致的跃出水面和反复沉浮。
  - 严重缺氧且无法直接上浮时，允许寻岸行为优先于战斗、驻守等移动指令。
  - 已在水中的角色可以沿水路脱离浅水或水岸；陆地寻路仍会避开水域。
- **验证**
  - Java 17 下执行 `gradlew clean build --offline` 成功；Gradle `check` 及项目行为、招募、兼容性、配置和结构数据检查通过。
  - 未启动 Minecraft 进行游戏内测试。

### English

- **Water behavior improvements**
  - Surface ascent now starts earlier and retains enough upward speed to avoid stalling near the surface.
  - Humans maintain controlled movement at the surface after their eyes emerge, reducing momentum-driven hops and repeated submerging.
  - During critical low-air emergencies, shore-seeking can override combat and guard movement when direct ascent is insufficient.
  - Humans already in water can path out through shallow water or shore routes; land navigation continues to avoid water.
- **Validation**
  - `gradlew clean build --offline` succeeded on Java 17; Gradle `check` and the project's behavior, recruitment, compatibility, configuration, and structure-data checks passed.
  - Minecraft was not launched for in-game testing.

## 3.1.31 — 2026-09-26

### 简体中文

- **水中行为修复**
  - 陆地寻路不再主动选择水域；已经潜入水中的人类仍可正常进行水下寻路。
  - 修正上浮控制，并提高水中移动速度；移除水中主动跳出水面的逻辑。
  - 接近水面约两格且站立空间足够时，提前切换为直立姿势。
- **验证**
  - Java 17 下执行 `gradlew build --offline` 成功，Gradle `check` 及项目内的配置、招募、战斗策略、兼容性和结构数据检查均通过。
  - 未启动 Minecraft 做游戏内测试。

### English

- **Water behavior fixes**
  - Land-based pathfinding no longer selects water nodes; already-submerged Humans can still navigate underwater.
  - Corrected ascent control, increased underwater movement speed, and removed the logic that actively jumped Humans out of water.
  - Humans switch to an upright pose about two blocks below the surface when there is enough standing clearance.
- **Validation**
  - `gradlew build --offline` succeeded on Java 17. Gradle `check` and the project's configuration, recruitment, combat-policy, compatibility, and structure-data checks passed.
  - Minecraft was not launched for in-game testing.

## 3.1.30 — 2026-09-26

### 简体中文

- **水中移动修复**
  - 移除缺氧时反复触发的水下跳跃，改为平缓且限速的上浮；缺氧恢复期间保持水中移动，直到头部离开水面。
  - 不再把水底方块误判为岸边台阶，避免在海床附近反复跳跃。
  - 寻岸路径由生成它的导航器执行，修复地面路径被水中导航器接管后卡住的问题。
- **验证**
  - Java 17 下执行 `gradlew build --offline` 成功；Gradle `check` 的配置、兼容、招募、战斗策略及结构数据检查均通过。
  - 未启动 Minecraft 做游戏内测试。编译器仍输出通用的旧 API / unchecked 摘要提示；详细 deprecation/removal lint 已关闭。

### English

- **Swimming fixes**
  - Removed repeated underwater jumps during breath emergencies and replaced them with smooth, capped ascent. Swimming control remains active until the Human's head clears the surface.
  - Seabed blocks are no longer mistaken for shoreline steps, preventing repeated hopping near the bottom.
  - Shore paths are now executed by the navigation system that created them, avoiding stalls when a ground path was handed to water navigation.
- **Validation**
  - `gradlew build --offline` succeeded on Java 17; the configuration, compatibility, recruitment, combat-policy, and structure-data checks under Gradle `check` passed.
  - Minecraft was not launched for in-game testing. The compiler still prints generic deprecated-API/unchecked summary notes; detailed deprecation/removal lint is disabled.

## 3.1.29 — 2026-09-26

### 简体中文

- **水中移动与寻路**
  - 提高游泳时的水平推进力，抵消水中阻尼造成的速度损失。
  - 提高水域寻路代价，让人类优先绕行陆地；水仍然可通行，以便在没有陆路时通过。
  - 闲暇且无需恢复呼吸时会主动寻找可达岸边；战斗、逃跑、使用物品和紧急浮出水面逻辑优先。
- **验证**
  - Java 17 下完成编译与重映射打包；未运行 Gradle `check` 或游戏内测试。

### English

- **Swimming and pathfinding**
  - Increased horizontal swimming acceleration to counter water drag.
  - Raised water pathfinding cost so Humans prefer land routes while keeping water traversable when necessary.
  - Idle Humans now seek reachable shore when not recovering breath; combat, fleeing, item use, and emergency surfacing retain priority.
- **Validation**
  - Compilation and remapped packaging completed with Java 17; Gradle `check` and in-game testing were not run.

## 3.1.28 — 2026-09-25

### 简体中文

- **移动与跳跃**
  - 追击和撤退时会按战斗跳跃配置重复尝试跳跃；撤退跳跃统一由移动辅助逻辑处理，避免与战斗撤退逻辑重复触发。
  - 根据寻路上坡节点和前方一格高障碍物提前起跳，并检查头顶空间，减少撞墙后才起跳的情况。
  - 瞄准、开火或使用物品期间抑制普通跳跃，避免打断武器动作。
- **验证**
  - Java 17 下完成编译与重映射打包；未运行 Gradle `check` 或游戏内测试。

### English

- **Movement and jumping**
  - Humans now make repeated, configuration-paced hops while pursuing or retreating. Retreat hopping is centralized in the movement helper to avoid duplicate combat-AI triggers.
  - They can jump ahead of a one-block obstacle using path ascent and forward-block checks, with headroom validation, instead of waiting for collision.
  - Ordinary hops are suppressed while aiming, firing, or using an item so weapon actions are not interrupted.
- **Validation**
  - Compilation and remapped packaging completed with Java 17; Gradle `check` and in-game testing were not run.

## 3.1.27 — 2026-09-25

### 简体中文

- **战斗 AI**
  - 弓手和弩手在进攻时若被地形阻断视线，会寻找可达且能重新获得射击视线的位置；逃跑时仍由撤退逻辑控制移动。
  - 枪手只在确实沿寻路方向追击、且未瞄准或开火时启用疾跑，避免疾跑状态阻断射击。
  - 调整苦力怕目标选择与规避：优先躲避已开始膨胀的苦力怕；对自主决定攻击或已经进入交战的苦力怕不再被回避行为压制。
- **弹道与装备音效**
  - 弓、弩和三叉戟发射时直接使用本次攻击选定的目标计算弹道，避免目标切换时读到过期目标。
  - 盔甲和盾牌破损改用原版物品破损音效，并统一通过去重入口，避免同一次装备移除重复播放。
- **移动属性**
  - 各阶人类默认基础移速调整为普通玩家基础移速的 1.05 倍。
- **验证**
  - 已进行静态审查、配置 JSON 校验和构建编译；未运行游戏内验收。

### English

- **Combat AI**
  - When terrain blocks their line of sight while engaging, archers and crossbow users now seek reachable positions with a clear firing lane. Retreat movement remains controlled by the retreat logic.
  - Gunners sprint only while genuinely pathing toward a target and not aiming or firing, preventing sprint state from interrupting shots.
  - Refined creeper target selection and evasion: primed creepers are prioritized for avoidance, while creepers selected for autonomous combat are not suppressed by the avoidance goal.
- **Projectiles and equipment sounds**
  - Bow, crossbow, and trident launchers now calculate ballistics against the target selected for that attack, rather than a potentially stale current target.
  - Armor and shield breakage use the vanilla item-break sound through a shared deduplication gate, preventing duplicate playback for a single equipment removal.
- **Movement attributes**
  - Default base movement speed for every human tier is now 1.05 times the normal player's base movement speed.
- **Validation**
  - Static review, configuration JSON validation, compilation, and packaging completed; no in-game acceptance test was run.

## 3.1.26-unified — 2026-09-25

### 简体中文

- **战斗 AI 与生存**
  - 调整弓手、弩手与枪手的交战距离、后撤和反击行为，减少远程单位在战斗中无谓地冲向目标或撤退时停止还击。
  - 优化盾牌与远程攻击的协调：根据来袭箭矢进行概率性预判格挡；远程使用者只在箭矢即将命中时短暂防御，降低对射击、装填和近战行为的干扰。
  - 改善遭受近身压力时的反击窗口，减少单位持续后撤却不还击的情况。
  - 新增熔岩紧急脱离行为，并让寻路更倾向避开熔岩；落入熔岩时会尝试上浮并寻找可达的安全岸边。
- **武器、装备与属性**
  - 未安装 Spartan Weaponry 时，流浪者、一阶和二阶人类也会按概率使用原版弓或弩；概率依阶级递增（20%、40%、70%），三阶人类保证携带弓或弩之一。
  - 枪械散布可按阶级和枪械类型分别配置；弓、弩、三叉戟也有各自独立的逐阶散布设置。旧散布字段仍提供兼容回退。
  - 将移动速度配置统一为每阶基础速度，并沿用原版疾跑及迅捷效果的属性修正；旧的普通、战斗、撤退速度字段不再参与计算。
  - 进食和举盾时的移速修正改为配置项；默认进食保留 70% 移速，举盾保留 20% 移速。喝药不额外施加移速惩罚。
  - 被雇佣人类的护甲和盾牌改为逐步消耗耐久，不再使用概率直接损坏整件装备的方式。
  - 调低人类使用动作的声音倍率（至 1.5）。
- **配置与说明**
  - 重组默认配置，补充中英文说明；逐阶枪械/投射物散布和移动速度配置更加清晰。
  - 详细说明枪械白名单 ID 的 JSON 写法、条目分隔符、自动加入新枪械选项及按权重比例抽取规则；权重总和无需等于 100，并在默认配置说明内提供示例。
  - 澄清 TaCZ 盾牌承伤倍率和女仆枪械承伤倍率的作用方向。
- **验证**
  - Gradle `build` 及策略、配置、可选依赖和结构兼容检查通过。
  - 尚未进行游戏内运行时验收。

### English

- **Combat AI and survival**
  - Refined engagement spacing, retreating, and counterattacks for archers, crossbow users, and gunners, reducing unnecessary advances and silent retreats.
  - Improved shield coordination with ranged combat. Humans now probabilistically anticipate incoming arrows; ranged users guard only shortly before impact to minimize interruptions to firing, reloading, and melee behavior.
  - Improved counterattack windows under close-range pressure to prevent units from retreating continuously without fighting back.
  - Added emergency lava escape and pathfinding that prefers routes around lava. Humans caught in lava try to surface and find a reachable safe shore.
- **Weapons, equipment, and attributes**
  - Without Spartan Weaponry, roamers and Tier I/II humans can still spawn with a vanilla bow or crossbow, with tier-based chances of 20%, 40%, and 70%. Tier III humans are guaranteed one of the two.
  - Firearm spread is configurable by tier and weapon category. Bow, crossbow, and trident spread can also be configured independently for every tier. Legacy spread fields remain supported as fallbacks.
  - Consolidated movement configuration into a per-tier base speed while preserving vanilla sprint and Swiftness attribute modifiers. Legacy normal/combat/retreat speed fields no longer affect movement.
  - Added configurable movement modifiers for eating and blocking. Defaults retain 70% movement speed while eating and 20% while blocking; drinking adds no extra movement penalty.
  - Hired humans' armor and shields now lose durability gradually instead of being destroyed through a random whole-item break chance.
  - Reduced the sound multiplier for human item-use actions to 1.5.
- **Configuration and documentation**
  - Restructured the default configuration and added bilingual descriptions for clearer per-tier speed and weapon-spread settings.
  - Expanded the gun whitelist guidance with JSON ID syntax, separators, the adjacent auto-add option, and proportional weight selection. Weights do not need to total 100; an example is included in the default configuration descriptions.
  - Clarified what the TaCZ shield damage multiplier and tamed-maid firearm damage multiplier affect.
- **Validation**
  - Gradle `build` and the combat-policy, configuration, optional-dependency, and structure-compatibility checks passed.
  - No in-game runtime acceptance test has been performed.
