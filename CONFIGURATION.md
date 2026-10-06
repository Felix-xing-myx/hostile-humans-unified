# 配置文件说明

配置位于 `config/hostile_humans_unified/`，首次启动生成五个 JSON 文件，均为 `schema_version: 2`。修改后完整重启生效；多人游戏以服务端配置为准。请使用 UTF-8 和合法 JSON，保留 `_说明_中文`、`_description_en` 等说明字段。

| 文件 | 内容 |
|---|---|
| `global.json` | 总开关、通用伤害、自然生成规则、共享枪械池与兼容、玩家间伤害、聊天限额及结构设置 |
| `roamer.json` | 流浪者的属性、战斗 AI、装备、出生弹药、生成倍率与解锁日期、枪械概率和雇佣条件 |
| `tier1.json` | 一阶的上述独立设置 |
| `tier2.json` | 二阶的上述独立设置 |
| `tier3.json` | 三阶的上述独立设置 |

阶级文件直接使用 `attributes`、`movement`、`damage_multipliers`、`weapon_spread_degrees`、`combat`、`spawning`、`equipment_loadout`、`equipment_growth`、`ammunition`、`ai`、`tacz`、`recruitment` 等顶层区域，不再套 `tiers.tier1`。各文件的 `ai` 参数独立；只有增强 AI 总开关 `global.json` 的 `ai.enabled` 控制所有阶级。

旧七模块、旧单文件和 `humangunner` 配置仅作为缺失新文件的迁移来源，优先级为旧模块 > 旧统一单文件 > 最旧 JSON；原 `hostile_humans.toml` 提供更低优先级的结构/战斗设置。迁移前保留旧文件并备份：JSON 后缀 `.pre-tier-layout.bak`，TOML 后缀 `.pre-tier.bak`。迁移完成后请只编辑五个新文件，模组不再注册或生成旧 TOML。

已有新文件优先且不会被自动重写。缺少字段仅在内存中补默认值；需要调整新增选项时可按本指南手动添加。坏文件及不支持的版本保留原样，在内存中使用该文件默认值；坏阶级文件禁止该阶的生存雇佣，防止错误费用导致免费雇佣。坏旧源或备份失败时暂缓生成相关新文件并记录日志。自定义装备空池、权重表和招聘价格不会被默认条目填回。所有读取都在启动阶段完成。

回滚旧版本时旧文件仍在，但新布局中的修改不会自动写回旧布局，需手动转换。

## 出生弹药与全局消耗开关

`global.json` 中设置：

```json
"ammunition": { "consume_ammunition": true }
```

默认开启。关闭后，弓弩不要求箭矢库存、不扣箭矢，枪械换弹不要求通用弹匣库存、不扣弹匣；正常射击、装填和换弹动作继续执行。枪械战斗中打空才换弹，闲置时弹匣余量严格低于容量 25% 才换弹。三叉戟仍可无限投掷。

每个阶级文件中设置：

```json
"ammunition": {
  "arrows": { "min": 32, "max": 128 },
  "magazines": { "min": 6, "max": 12 }
}
```

| 阶级 | 箭矢默认范围 | 通用弹匣默认范围 |
|---|---:|---:|
| 流浪者 | 32–128 | 6–12 |
| 一阶 | 64–128 | 12–24 |
| 二阶 | 128–192 | 32–48 |
| 三阶 | 192 | 48–72 |

范围含两端，只给生成时携带对应武器的单位一次性发放，不补充已有实体。数量按件数填写，不是组数；箭矢堆叠 64、弹匣堆叠 24。允许零，箭矢每项限制 0–1920，弹匣每项 0–720；上下限反向会自动交换，非整数或错误类型使用默认。背包实际空间限制最终存放量。

弓弩每次成功射击扣一支箭矢（多重射击一次一支）。枪械每次成功开始换弹扣弹匣：手枪/冲锋枪 1，步枪/霰弹枪 2，狙击枪 3，机枪 4，重型武器 5，未知类型 2。开启消耗时，缺弹会回退到有弹的其他远程武器或现有近战武器。

## 阶级文件：属性

每个阶级拥有独立设置：

- `attributes.health_min`、`attributes.health_max`：生命值范围。2 点生命值相当于 1 颗心。
- `attributes.attack_damage`、`armor`、`armor_toughness`、`knockback_resistance`、`follow_range`：基础攻击伤害、防护属性和目标探测距离。
- `movement.base_speed`：本阶人类的基础移动速度，默认 `0.105`，即普通玩家基础移速 `0.1` 的 1.05 倍；允许范围 `0.01–0.2`。疾跑与迅捷等效果继续沿用原版属性修正；自定义动作减速也作为属性修正参与计算。
- `damage_multipliers.melee_damage_multiplier`、`bow_damage_multiplier`、`trident_damage_multiplier`、`incoming_damage_multiplier`：本阶造成或承受的伤害倍率。
- `spawning.spawn_multiplier`：本阶自然生成准入倍率。设为 0 可关闭对应的普通自然生成入口；不影响结构固定生成和信号装置召唤。
- `combat.melee_cooldown_min`、`melee_cooldown_max`：近战攻击冷却范围，单位为 tick。
- `weapon_spread_degrees.firearms`：本阶每种枪械分类的独立散布值，单位为度。可分别设置 `sniper`、`rifle`、`mg`、`pistol`、`smg`、`shotgun`；其他或无法识别的枪种使用 `other`。数值越小越精准。
- `weapon_spread_degrees.projectiles`：本阶 `bow`（弓）、`crossbow`（弩）、`trident`（三叉戟）的独立散布值，互不联动，单位为度。
- 旧配置中的 `gun_spread_degrees` 和 `projectile_spread_degrees` 仍可读取；若对应的新分类值未填写，旧字段会按原来的差值规则提供回退值。

旧配置中的 `normal_movement_speed`、`combat_movement_speed` 和 `retreat_movement_speed` 不再参与计算；请改用各阶的 `movement.base_speed`。`ai.food_use_speed_multiplier` 与 `ai.shield_use_speed_multiplier` 的范围为 `0.1–1`，分别控制进食和普通举盾时叠加的速度属性修正；默认 `0.7` 即进食时附加 `-30%` 修正，默认 `0.2` 即普通举盾时附加 `-80%` 修正。预判格挡来袭弹射物时，举盾移速至少保留 `0.7` 倍，以便继续移动。喝药不额外施加移速惩罚。

## `damage`：通用伤害倍率

全局倍率与各阶对应倍率相乘，不互相覆盖。例如全局近战倍率为 0.8、该阶倍率为 1.5，则此部分合计为 1.2 倍。近战、弓箭和三叉戟按各自路径应用，TaCZ 子弹不再叠加近战倍率；枪伤由 `tacz` 中的全局与阶级倍率控制。目标的人类承伤倍率、护甲、附魔和其他模组仍会参与伤害处理，配置倍率不是最终扣血比例。投射速度是弹道参数，不是伤害倍率。`tamed_maid_melee_damage_multiplier` 控制人类近战对已驯服女仆造成的伤害，不是女仆对人类造成的伤害。

## `spawning`：自然生成与密度

- `enabled`：启用或关闭模组的人类自然生成准入。
- `admission_chance`、`battle_admission_chance`：普通自然生成与大型战斗遭遇的准入概率。
- `encounter_cooldown_ticks`：成功生成一批后，同维度再次尝试的冷却时间，单位为 tick。
- `nearby_horizontal_radius`、`nearby_vertical_radius`、`density_divisor`：附近人类密度计算范围和抑制强度。

这些值不是“每秒刷新概率”。地形、光照、玩家距离、原版生物容量、密度与冷却等条件也会影响生成。关闭自然刷新不会清除已有实体，也不会关闭刷怪蛋、命令、结构固定单位或信号装置召唤。

`admission_chance` 与各阶 `spawning.spawn_multiplier` 都支持小数。普通自然生成的准入概率为两者相乘后限制到 0–1，例如 0.08 × 0.1 = 0.008，即一次合格尝试的 0.8%。流浪者不再叠加旧的额外 1/200 判定；旧文件保留的 `roamer_legacy_roll` 不再读取。生物群系权重是另一层候选选择，并非最终生成数量或概率。

### 新手保护与分阶解锁

`spawning.progression.enabled` 默认开启。`safe_days` 为完全禁止自然生成人类的天数，默认 `1`；各阶级文件的 `spawning.first_spawn_day` 设置对应阶级最早出现的模组日期，默认流浪者第 3 天、一阶第 5 天、二阶第 10 天、三阶第 20 天。日期从第 1 天开始计数，每天为 24000 tick。各阶必须同时满足安全期结束和自身日期要求；因此默认第 2 天也不会自然生成人类。达到日期只是解锁生成资格，原有光照、密度、概率等条件仍然生效。安全天数允许 `0–1000000`，各阶日期允许 `1–1000000`。

每位玩家按 UUID 独立保存累计在线游戏时间，所有维度沿用该玩家自己的进度。24000 个在线 tick 算一天，在 20 TPS 下约 20 分钟；离线、单人暂停不累计，睡觉跳夜、原版时间指令和 `doDaylightCycle` 不影响难度。旧世界日历和偏移不迁移，首次使用个人计时从第 1 天开始。生成点按同维度最近的存活非旁观玩家决定解锁阶级和装备成长；已有敌人不会按遇到的新玩家降级，所以这不是多人之间完全隔离的安全保护。

计时每 200 tick 批量结算一次，并在退出时结算余量；数据随世界正常保存，不逐 tick 扫描生物，也不在刷新判断中读写文件。进程异常终止可能丢失最近一次世界保存后的计时。服务端线程使用玩家当前位置与未结算的在线余量；异步区块生成读取最多约 10 秒前的不可变快照，登录、退出、重生和换维度会更新快照。该大版本不要求旧日历兼容；已有模块配置仍保留通用缺项补全和错误保护，不新增旧版本迁移分支。建议发布升级时备份旧配置并重新生成，以获取最新说明。

| 指令 | 功能 | 权限 |
|---|---|---|
| `/hostilehumans day get` | 查看自己的个人在线日期 | 所有玩家 |
| `/hostilehumans day get <玩家>` | 查看指定在线玩家的日期 | 管理员等级 2 |
| `/hostilehumans day set 1 [玩家]` | 设置个人日期，随后仅在线时继续流逝 | 管理员等级 2 |
| `/hostilehumans day add 2 [玩家]` | 增减个人日期，保留日内进度，最低第 1 天 | 管理员等级 2 |

不指定玩家时操作执行者自己；控制台需指定在线玩家。旧 `day sync` 指令已经移除，不再提供世界时间同步功能。

该限制适用于普通自然生成、区块生成时的自然生物和自动大型战斗遭遇。大型战斗只选择已解锁的一阶或二阶单位。友方支援信号弹、敌对信标、刷怪蛋、命令及结构固定单位不受该日期限制；已有实体不会被清除。时间偏移单独存入世界的 `data/hostile_humans_progression.dat`，不会串到其他世界。

全局生成规则在 `global.json` 的 `spawning` 中；本阶生成倍率与解锁日位于阶级文件的 `spawning` 中。

## 阶级 `equipment_loadout` 与 `equipment_growth`：装备配置与成长

四阶人类分别配置近战、弓弩、备用武器、副手、盾牌与护甲套装池，支持已注册的模组物品 ID、数量、附魔和相对权重。权重无需合计为 100；空池使用该位置的内置回退。填写示例直接保留在配置文件的双语说明中。旧 `human_loadouts` 装备数据包入口已经移除，三阶也使用同一配置生成流程。

斯巴达武器/盾牌在首次识别到已安装模组时写入配置池，之后与自定义条目统一按权重抽取，不再替换生成后的装备。`automatic_spartan_weapons/shields` 控制首次导入，`_imported_spartanweaponry/_imported_spartanshields` 保存导入记录；保留记录后删除条目不会重新补回。关闭导入不删除已写入条目；禁止某物品生成，应删除对应条目或设置权重为 0。`requires_mod` 可省略。空数组保留，不要删除整个配置键。

`global.json` 的 `tacz.natural_spawn_firearms_enabled` 控制自然生成（含自然大型战斗）额外携带枪械，默认开启；关闭不会影响其他装备、已有枪械或信号召唤。枪械仍用原有按阶概率与 gun ID 白名单，并保留近战武器。生成武器总上限为流浪者 2 把、其他阶级 3 把，包含手中和背包中的枪械、远程与近战武器。`weapon_count` 控制第二、第三把的递减概率；远程单位的近战备用计入总数，之后拾取与主人配装不受此生成上限限制。`bonus_mainhand` 现在参与额外名额抽取，不再替换主手。自然装备成长最后处理，固定品质需关闭全局 `equipment_growth.enabled`。

`equipment_growth.enabled` 默认开启，普通自然生成和自动战斗遭遇的装备随模组日期逐渐接近原有品质。四阶默认分别于第 40、50、70、80 天达到完整品质；成长起点、早期装备、早期枪械概率系数、附魔保留概率与等级、图腾和三叉戟保留概率均可调整。成长日期与自然生成解锁日期独立配置。关闭成长后使用原有品质。

成长只在生成时处理并记录，不会每天给已有单位换装；支援信号弹、敌对信标、刷怪蛋、命令和结构固定单位不受自然装备成长限制。士兵的备用近战装备仅在生成时提供，武器损坏后从现有背包选择工具或空手，不会定时凭空补出新武器。

## `ai`：战斗行为与动作速度


全局 `ai.enabled` 控制增强战斗 AI；各阶文件的 `ai` 独立设置撤退与恢复血量阈值、寻找掩体范围、恢复动作间隔、盾牌格挡时长与概率、战术走位和战斗跳跃等行为。`item_recovery_enabled` 控制 AI 是否使用可用恢复物品。`food_use_speed_multiplier` 和 `shield_use_speed_multiplier` 分别设置进食与普通举盾时使用的速度修正倍率；预判弹射物的短暂格挡会保留至少 `0.7` 倍移速。关闭增强 AI 不会关闭实体、雇佣关系或基本战斗。

## `recruitment`：雇佣上限与玩家间伤害

各阶文件的 `recruitment.max_hired` 分别设置每位玩家可正式雇佣的流浪者、一阶、二阶和三阶人类上限，默认值为 12、10、6、3。

`allow_hired_pvp_damage` 默认关闭。开启后，雇佣人类可在其交战模式允许的情况下伤害其他玩家及其雇佣人类；自己的主人和同一主人的单位仍受保护。

各阶文件的 `recruitment.payment` 指定雇佣费用的物品 ID 与数量，默认仍为 8、24、72、216 个绿宝石，合同另外消耗。支持已安装模组注册的物品，不按 NBT 区分；跨背包多个格子合计，物品不足不会部分扣款。数量为 0 时仅消耗合同；无效配置或未注册物品会阻止生存雇佣，创造模式仍免费。若支付物品也是合同，手持的雇佣合同不计入可付款数量。服务器会向客户端同步有效费用，用于合同提示。具体填写示例和边界说明保留在配置文件的双语说明中。身份牌资格不在此项调整。

### 士兵聊天限额 / Soldier message limits

`config/hostile_humans_unified/global.json` 中的 `recruitment.soldier_messages` 按主人共享限额，不随士兵数量叠加。普通自动台词默认至少间隔 60 秒，每 5 分钟最多 3 条；可将 `chatter_max_per_five_minutes` 设为 0 关闭普通台词。重要状态报告默认至少间隔 5 秒，每分钟最多 6 批，优先显示低血量警告，每批最多显示 3 条详情，其余用数量摘要表示。同一士兵的同类待发报告合并，待发队列最多保留 32 项，超额报告只计入摘要，因此部分报告会延后或省略详情。玩家主动雇佣和下达命令的确认不受此限额影响。间隔以游戏 tick 计算，20 TPS 时 20 tick 为一秒。

`recruitment.soldier_messages` shares message budgets across each owner's soldiers. Default chatter limits are one message per 60 seconds and three per five minutes; a zero chatter quota disables it. Status reports have a separate five-second interval and six-batch-per-minute limit. Critical health reports take priority; each batch shows up to three details and summarizes the rest. Pending reports are deduplicated by soldier and category, capped at 32 entries, and may be delayed or summarized. Player-initiated hiring and command confirmations remain immediate. Intervals use game ticks (20 ticks per second at 20 TPS).

## `better_combat`：士兵的 Better Combat 兼容

`soldier_melee_enabled` 默认开启。安装 Better Combat 时，士兵的近战会读取武器的攻击形状、范围和攻击速度冷却，并可命中攻击范围内的多个敌对目标；玩家自身的攻击不会被接管。未安装 Better Combat 时会自动回退到模组原有的 AI、分阶随机攻击间隔、伤害和单体近战判定。玩家也可以手动关闭此项；修改后需重启游戏。

现有配置缺少 `better_combat` 区域时，启动时会自动补入该区域及中英文说明，不会覆盖其他配置值。

## `tacz`：可选枪械兼容

全局 `tacz` 管理兼容总开关与共享枪械池，各阶 `tacz` 管理 `gun_chance`、`damage_multiplier`、`gun_type_weights`。此区域仅在安装 TaCZ 且全局 `enabled` 开启时生效。相关字段控制各阶枪手获得枪械的概率、枪械掉落率、伤害倍率、枪械类型权重和枪械池。枪械掉落概率不限制正式雇佣单位：它们死亡时完整掉落背包及已装备物品，保留数量、耐久与 NBT；野生单位仍按概率掉落。

- 各阶文件 `tacz.gun_type_weights`：设置不同阶级对手枪、冲锋枪、步枪、机枪、狙击枪和霰弹枪等类型的抽取权重。权重越高，抽到该类枪的相对机会越大。
- `gun_whitelist`：野生枪手的枪械 ID 与抽取权重表。键必须是 TaCZ 枪械注册 ID，格式为 `命名空间:枪械路径`。正整数是同类枪械中的相对权重，系统会按权重比例抽取；**权重总和不要求等于 100**。`0` 会明确排除该枪。
- `auto_add_new_guns`：紧邻 `gun_whitelist` 的自动收录开关。开启时，未列出的可用枪械会按 `auto_added_gun_weight` 进入野生枪手的候选池；若只想让野生枪手使用白名单内正权重枪械，请关闭此项。`excluded_auto_gun_types` 可排除自动收录的枪械类别。
- `hired_gun_additional_whitelist`：仅额外允许玩家把列出的枪械放入已雇佣人类背包，不影响野生枪手抽选。键格式相同；值为任意正整数即表示允许，具体大小不影响允许结果。基础 `gun_whitelist` 为空时，已雇佣人类可以接受所有能识别的 TaCZ 枪械；基础表非空时，只接受基础表中的正权重枪械或此追加表中的正权重枪械。

两个名单都是 JSON 对象，**每条枪械 ID 是一个用双引号括起的键**；键和值之间用冒号 `:`，ID 内的冒号用于分隔命名空间和路径；条目之间使用英文逗号 `,`。权重无需凑到 100，程序会按正权重之和计算比例。准确的 JSON 示例直接写在默认配置文件的 `_说明_枪械名单_中文` 和 `_description_gun_lists_en` 说明字段中。请将示例 ID 替换成枪包实际注册的 ID，末项后不要加逗号，并保持整个文件为合法 JSON。`hired_gun_additional_whitelist` 的正权重值只表示允许，并不参与随机权重计算。
- `gun_blacklist`、`excluded_gun_namespaces`：禁止指定枪械 ID 或来源命名空间。
- `human_gun_damage_multiplier`、`tier_damage_multipliers`：调整人类枪械伤害的基础倍率和各阶额外倍率。
- `shield_damage_multiplier`：人类举盾格挡 TaCZ 子弹后实际承受的生命伤害比例；例如 `0.3` 表示保留 30% 伤害（减伤 70%）。
- `tamed_maid_damage_multiplier`：已驯服女仆受到的 TaCZ 子弹伤害比例，默认 `0.15`。它不检查攻击者类型，因此人类枪击女仆也会应用该倍率；人类枪械基础倍率和阶级倍率会先作用。女仆受到人类近战攻击则由 `damage.tamed_maid_melee_damage_multiplier` 控制。

野生枪手抽取和玩家手动给已雇佣人类输入枪械使用不同规则。基础白名单为空时，已雇佣人类可接受能识别的 TaCZ 枪械；基础白名单非空时，只接受其中正权重枪械，以及 `hired_gun_additional_whitelist` 中正权重枪械。`auto_add_new_guns` 不会放宽手动输入限制。

## 原 TOML 设置

`global.json` 的 `native_gameplay` 包含 `disabled_structures`（逗号分隔结构禁用名单）、`max_targeting`（同时攻击玩家的人类上限，默认 3）、`no_waystones`（默认 false）、`battle_event`（HumanVsHuman、HumanVsPillager、Random、Disabled，默认 Random）。

各阶 `ai` 中还包含 `flee_hp`（旧低血量判断，默认 0.35）、`run_away_middle_fight_chance`（默认 0.5）、`mid_battle_buff_instead_of_run_chance`（原二阶临战恢复判定，默认 0.1）及 `greet_chance`（敌对台词概率，默认 0.05）。这些概率范围为 0–1；增强 AI 的 `retreat_health_ratio` 等仍控制对应增强行为。


## 阶级 `potions`：出生药水

`roamer.json`、`tier1.json`、`tier2.json`、`tier3.json` 分别配置本阶出生药水，新生成文件会包含这一节。已有五文件配置不会自动重写；可以把下面的 `potions` 区域加入对应阶级文件，未填写时先使用默认值。完整重启后生效，只影响后来生成且尚未发放初始补给的单位，既有人类不补发。

```json
"potions": {
  "enabled": true,
  "recovery": {
    "count_min": 2,
    "count_max": 4,
    "pool": [
      { "item": "minecraft:potion", "potion": "minecraft:strong_healing", "weight": 2 },
      { "item": "minecraft:potion", "potion": "minecraft:long_regeneration", "weight": 1 }
    ]
  },
  "tactical": {
    "count_min": 1,
    "count_max": 2,
    "pool": [
      { "item": "minecraft:splash_potion", "potion": "minecraft:long_slowness", "weight": 1 },
      { "item": "minecraft:splash_potion", "potion": "minecraft:long_weakness", "weight": 1 },
      { "item": "minecraft:potion", "potion": "minecraft:long_strength", "weight": 2 }
    ]
  },
  "bonus": {
    "item": "minecraft:potion",
    "potion": "minecraft:long_swiftness",
    "chance": 0.1
  }
}
```

- `enabled: false` 禁止本阶出生时发放药水；本阶 `ai.item_recovery_enabled: false` 仍会关闭整个初始恢复补给包。
- `count_min/max` 是药水瓶数，包含两端，各限于 0–30；反向范围会交换。0–0可关闭该组。药水仍按物品原有堆叠上限存放，背包满时停止，不挤走其他装备。
- `item` 是瓶子的物品ID，一般用 `minecraft:potion`（饮用）、`minecraft:splash_potion`（喷溅）或 `minecraft:lingering_potion`（滞留）。`potion` 是药水注册ID，支持原版与已安装模组的药水；标准药水物品及Potion NBT为前提。不支持在此直接填写任意自定义效果NBT。
- `pool` 中每瓶按 `weight` 相对权重独立抽选，无需合计100。正整数权重上限1000000；0、错误条目、缺失物品/药水ID会跳过，每池最多读取前128项。显式空数组 `[]` 不会补回默认药水。
- `bonus.chance` 是预留一瓶战术药水给该药水的概率，范围0–1，默认是迅捷。它占一个战术药水名额，不增加总瓶数；设为0关闭，战术数量为0或战术池为空也不发放。
- 药水配置控制携带；现有AI仍按其支持的效果决定饮用或投掷。配置模组药水并不额外新增该模组的特殊使用逻辑；滞留药水可以携带，但当前主动投掷入口只处理喷溅药水。

| 阶级 | 恢复药水瓶数 | 战术药水瓶数 | 默认迅捷概率 |
|---|---:|---:|---:|
| 流浪者 | 2–4 | 1–2 | 10% |
| 一阶 | 2–5 | 2–3 | 20% |
| 二阶 | 4–7 | 3–5 | 30% |
| 三阶 | 6–8 | 5–7 | 40% |

默认药水种类沿用强效治疗、长效再生、长效力量、喷溅长效缓慢/虚弱与长效迅捷。原先恢复数量与食物补给预设共用一次抽选、药水种类按固定顺序；现在药水数量在独立配置范围内均匀抽取、种类按池权重抽取。食物与金苹果原有补给预设不变。
