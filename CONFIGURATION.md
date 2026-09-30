# 配置文件说明

配置位于 `config/hostile_humans_unified/` 文件夹，首次启动后生成以下六个 JSON 文件。各文件仍使用下表所列的顶层区域，例如 `spawning.json` 内的 `spawning.progression`。请使用 UTF-8 编码并保持合法 JSON 格式；JSON 不支持 `//` 注释，也不要在最后一个字段后添加逗号。配置说明保存在 `_说明_中文`、`_description_en` 等字段中，保留原有双语说明与枪械名单示例。修改后需完整重启游戏。多人游戏中，服务器端配置决定实际玩法。

| 文件 | 顶层区域 | 内容 |
|---|---|---|
| `tiers.json` | `tiers` | 各阶属性、移速、伤害、武器散布和近战冷却 |
| `spawning.json` | `spawning` | 自然生成、密度、安全期与分阶日期 |
| `combat.json` | `damage`、`ai` | 通用伤害、战斗行为与恢复策略 |
| `recruitment.json` | `recruitment` | 雇佣人数上限与玩家间伤害 |
| `tacz.json` | `tacz` | 枪械概率、伤害、白名单与权重 |
| `compatibility.json` | `better_combat` | 可选模组兼容开关 |

### 旧配置迁移与故障回退

启动时，缺少的新文件会从旧 `config/hostile_humans_unified.json` 的对应区域生成；没有旧单文件且尚无新模块时，也支持迁移 `humangunner.json` 和 `humangunner_ai.json`。生成前会将旧单文件备份为 `hostile_humans_unified.json.pre-split.bak`；旧文件本身不修改、不删除。各区域内的自定义值、说明字段和额外字段随对应区域保留。尚未配置的值继续使用默认值，仍支持的旧字段不被新数值默认项覆盖。

**已存在的新模块文件优先，请在新文件中修改配置。** 旧单文件仅为缺失文件的迁移来源与故障回退来源，不再与正常新文件叠加。缺失模块的迁移来源也不存在时，生成该模块的默认配置。如果旧迁移文件损坏，暂不生成缺失模块，记录错误并在内存中使用默认值，以免覆盖尚未恢复的设置。

某个模块无法解析或版本不受支持时，仅该模块回退到旧单文件中的对应区域或默认值，日志记录具体文件；损坏文件不被覆盖，其他正常模块继续生效。缺少的说明、新手保护及 Better Combat 选项会安全补入，已有设置不重置。所有配置仅在启动时读取，不在实体每 tick 中读取磁盘。

原有 Forge TOML 配置（如 `hostile_humans.toml`）仍由原加载机制管理，本次不移动它们。回滚到只支持单文件配置的模组版本时，该版本会读取保留的旧 JSON；拆分后新增的修改需要手动合并回旧文件。

四个阶级键对应关系如下：

| 配置键 | 人类阶级 |
|---|---|
| `roamer` | 流浪者 |
| `tier1` | 一阶 |
| `tier2` | 二阶 |
| `tier3` | 三阶 |

## `tiers`：各阶属性

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

这里设置人类近战、弓箭、三叉戟、投射速度、跳跃攻击等通用伤害倍率。各阶的对应倍率会与本区设置共同作用；最终伤害还会经过护甲、附魔和其他模组的伤害处理。`tamed_maid_melee_damage_multiplier` 控制人类近战对已驯服女仆造成的伤害，不是女仆对人类造成的伤害；该字段仅在对应模组存在时适用。

## `spawning`：自然生成与密度

- `enabled`：启用或关闭模组的人类自然生成准入。
- `admission_chance`、`battle_admission_chance`：普通自然生成与大型战斗遭遇的准入概率。
- `roamer_legacy_roll`：流浪者额外随机判定。
- `encounter_cooldown_ticks`：成功生成一批后，同维度再次尝试的冷却时间，单位为 tick。
- `nearby_horizontal_radius`、`nearby_vertical_radius`、`density_divisor`：附近人类密度计算范围和抑制强度。

这些值不是“每秒刷新概率”。地形、光照、玩家距离、原版生物容量、密度与冷却等条件也会影响生成。关闭自然刷新不会清除已有实体，也不会关闭刷怪蛋、命令、结构固定单位或信号装置召唤。

### 新手保护与分阶解锁

`spawning.progression.enabled` 默认开启。`safe_days` 为完全禁止自然生成人类的天数，默认 `1`；`first_spawn_day_by_tier` 分别设置各阶最早出现的模组日期，默认流浪者第 3 天、一阶第 5 天、二阶第 10 天、三阶第 20 天。日期从第 1 天开始计数，每天为 24000 tick。各阶必须同时满足安全期结束和自身日期要求；因此默认第 2 天也不会自然生成人类。达到日期只是解锁生成资格，原有光照、密度、概率等条件仍然生效。安全天数允许 `0–1000000`，各阶日期允许 `1–1000000`。

所有维度共享主世界日历，并叠加存档中保存的模组时间偏移。睡觉跳过夜晚、原版时间指令都会相应改变模组日期；关闭 `doDaylightCycle` 会暂停日历的正常推进。已有世界初次加载此功能时沿用当前主世界日期，不自动重置。模组日期指令不修改主世界时间。

| 指令 | 功能 | 权限 |
|---|---|---|
| `/hostilehumans day get` | 查看模组日期和主世界日期 | 所有玩家 |
| `/hostilehumans day set 1` | 将模组日历设为第 1 天起点，之后继续跟随主世界流逝 | 管理员等级 2 |
| `/hostilehumans day add 2` | 增加 2 天；负数减少天数，最低为第 1 天 | 管理员等级 2 |
| `/hostilehumans day sync` | 清除偏移，对齐当前主世界日期与日内时间 | 管理员等级 2 |

该限制适用于普通自然生成、区块生成时的自然生物和自动大型战斗遭遇。大型战斗只选择已解锁的一阶或二阶单位。友方支援信号弹、敌对信标、刷怪蛋、命令及结构固定单位不受该日期限制；已有实体不会被清除。时间偏移单独存入世界的 `data/hostile_humans_progression.dat`，不会串到其他世界。

`spawning.json` 在启动时仅补入缺少的 `spawning.progression` 字段及双语说明，保留已经调整的值，无需删除或重置配置。

## `ai`：战斗行为与动作速度

可调整增强战斗 AI 的开关、撤退与恢复血量阈值、寻找掩体范围、恢复动作间隔、盾牌格挡时长与概率、战术走位和战斗跳跃等行为。`item_recovery_enabled` 控制 AI 是否使用可用恢复物品。`food_use_speed_multiplier` 和 `shield_use_speed_multiplier` 分别设置进食与普通举盾时使用的速度修正倍率；预判弹射物的短暂格挡会保留至少 `0.7` 倍移速。关闭增强 AI 不会关闭实体、雇佣关系或基本战斗。

## `recruitment`：雇佣上限与玩家间伤害

`max_hired_by_tier` 分别设置每位玩家可正式雇佣的流浪者、一阶、二阶和三阶人类上限，默认值为 12、10、6、3。

`allow_hired_pvp_damage` 默认关闭。开启后，雇佣人类可在其交战模式允许的情况下伤害其他玩家及其雇佣人类；自己的主人和同一主人的单位仍受保护。

`payment_by_tier` 按阶级指定雇佣费用的物品 ID 与数量，默认仍为 8、24、72、216 个绿宝石，合同另外消耗。支持已安装模组注册的物品，不按 NBT 区分；跨背包多个格子合计，物品不足不会部分扣款。数量为 0 时仅消耗合同；无效配置或未注册物品会阻止生存雇佣，创造模式仍免费。具体填写示例和边界说明保留在配置文件的双语说明中。身份牌资格不在此项调整。

## `better_combat`：士兵的 Better Combat 兼容

`soldier_melee_enabled` 默认开启。安装 Better Combat 时，士兵的近战会读取武器的攻击形状、范围和攻击速度冷却，并可命中攻击范围内的多个敌对目标；玩家自身的攻击不会被接管。未安装 Better Combat 时会自动回退到模组原有的 AI、分阶随机攻击间隔、伤害和单体近战判定。玩家也可以手动关闭此项；修改后需重启游戏。

现有配置缺少 `better_combat` 区域时，启动时会自动补入该区域及中英文说明，不会覆盖其他配置值。

## `tacz`：可选枪械兼容

此区域仅在安装 TaCZ 且 `enabled` 开启时生效。相关字段控制各阶枪手获得枪械的概率、枪械掉落率、伤害倍率、枪械类型权重和枪械池。

- `tier_gun_type_weights`：设置不同阶级对手枪、冲锋枪、步枪、机枪、狙击枪和霰弹枪等类型的抽取权重。权重越高，抽到该类枪的相对机会越大。
- `gun_whitelist`：野生枪手的枪械 ID 与抽取权重表。键必须是 TaCZ 枪械注册 ID，格式为 `命名空间:枪械路径`。正整数是同类枪械中的相对权重，系统会按权重比例抽取；**权重总和不要求等于 100**。`0` 会明确排除该枪。
- `auto_add_new_guns`：紧邻 `gun_whitelist` 的自动收录开关。开启时，未列出的可用枪械会按 `auto_added_gun_weight` 进入野生枪手的候选池；若只想让野生枪手使用白名单内正权重枪械，请关闭此项。`excluded_auto_gun_types` 可排除自动收录的枪械类别。
- `hired_gun_additional_whitelist`：仅额外允许玩家把列出的枪械放入已雇佣人类背包，不影响野生枪手抽选。键格式相同；值为任意正整数即表示允许，具体大小不影响允许结果。基础 `gun_whitelist` 为空时，已雇佣人类可以接受所有能识别的 TaCZ 枪械；基础表非空时，只接受基础表中的正权重枪械或此追加表中的正权重枪械。

两个名单都是 JSON 对象，**每条枪械 ID 是一个用双引号括起的键**；键和值之间用冒号 `:`，ID 内的冒号用于分隔命名空间和路径；条目之间使用英文逗号 `,`。权重无需凑到 100，程序会按正权重之和计算比例。准确的 JSON 示例直接写在默认配置文件的 `_说明_枪械名单_中文` 和 `_description_gun_lists_en` 说明字段中。请将示例 ID 替换成枪包实际注册的 ID，末项后不要加逗号，并保持整个文件为合法 JSON。`hired_gun_additional_whitelist` 的正权重值只表示允许，并不参与随机权重计算。
- `gun_blacklist`、`excluded_gun_namespaces`：禁止指定枪械 ID 或来源命名空间。
- `human_gun_damage_multiplier`、`tier_damage_multipliers`：调整人类枪械伤害的基础倍率和各阶额外倍率。
- `shield_damage_multiplier`：人类举盾格挡 TaCZ 子弹后实际承受的生命伤害比例；例如 `0.3` 表示保留 30% 伤害（减伤 70%）。
- `tamed_maid_damage_multiplier`：已驯服女仆受到的 TaCZ 子弹伤害比例，默认 `0.15`。它不检查攻击者类型，因此人类枪击女仆也会应用该倍率；人类枪械基础倍率和阶级倍率会先作用。女仆受到人类近战攻击则由 `damage.tamed_maid_melee_damage_multiplier` 控制。

野生枪手抽取和玩家手动给已雇佣人类输入枪械使用不同规则。基础白名单为空时，已雇佣人类可接受能识别的 TaCZ 枪械；基础白名单非空时，只接受其中正权重枪械，以及 `hired_gun_additional_whitelist` 中正权重枪械。`auto_add_new_guns` 不会放宽手动输入限制。

## 独立 TOML 设置

部分结构联动设置仍在 `config/hostile_humans.toml`，例如 Waystones 结构内容开关。该文件与统一 JSON 分开读取；其选项不会替代本页介绍的属性、刷新、AI、雇佣和 TaCZ 设置。
