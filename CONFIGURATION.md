# 配置文件说明

主配置文件为 `config/hostile_humans_unified.json`。首次启动后会在实例的配置目录生成。请使用 UTF-8 编码并保持合法 JSON 格式；JSON 不支持 `//` 注释，也不要在最后一个字段后添加逗号。修改后需完整重启游戏。多人游戏中，服务器端配置决定实际玩法。

四个阶级键对应关系如下：

| 配置键 | 人类阶级 |
|---|---|
| `roamer` | 流浪者 |
| `tier1` | 一阶 |
| `tier2` | 二阶 |
| `tier3` | 三阶 |

## `tiers`：各阶属性

每个阶级拥有独立设置：

- `health_min`、`health_max`：生命值范围。2 点生命值相当于 1 颗心。
- `attack_damage`、`armor`、`armor_toughness`、`knockback_resistance`：基础攻击伤害、护甲、护甲韧性和击退抗性。
- `follow_range`：目标探测距离属性。
- `melee_damage_multiplier`、`bow_damage_multiplier`、`trident_damage_multiplier`、`incoming_damage_multiplier`：本阶造成或承受的伤害倍率。
- `spawn_multiplier`：本阶自然生成准入倍率。设为 0 可关闭对应的普通自然生成入口；不影响结构固定生成和信号装置召唤。
- `melee_cooldown_min`、`melee_cooldown_max`：近战攻击冷却范围，单位为 tick。
- `gun_spread_degrees`：本阶枪械散布基准，单位为度。狙击枪在基准上减 0.5 度，步枪不变，机枪加 0.5 度，手枪和冲锋枪加 1 度，霰弹枪加 1.5 度；无法识别的枪种使用基准值加 0.5 度。
- `projectile_spread_degrees`：本阶弓与三叉戟的散布基准；弩比此值低 0.5 度。

`normal_movement_speed`、`combat_movement_speed` 和 `retreat_movement_speed` 是为兼容旧配置保留的字段，目前不会决定人类实际移动速度。

## `damage`：通用伤害倍率

这里设置人类近战、弓箭、三叉戟、投射速度、跳跃攻击等通用伤害倍率。各阶的对应倍率会与本区设置共同作用；最终伤害还会经过护甲、附魔和其他模组的伤害处理。驯服女仆相关字段仅在对应模组存在时适用。

## `spawning`：自然生成与密度

- `enabled`：启用或关闭模组的人类自然生成准入。
- `admission_chance`、`battle_admission_chance`：普通自然生成与大型战斗遭遇的准入概率。
- `roamer_legacy_roll`：流浪者额外随机判定。
- `encounter_cooldown_ticks`：成功生成一批后，同维度再次尝试的冷却时间，单位为 tick。
- `nearby_horizontal_radius`、`nearby_vertical_radius`、`density_divisor`：附近人类密度计算范围和抑制强度。

这些值不是“每秒刷新概率”。地形、光照、玩家距离、原版生物容量、密度与冷却等条件也会影响生成。关闭自然刷新不会清除已有实体，也不会关闭刷怪蛋、命令、结构固定单位或信号装置召唤。

## `ai`：战斗行为

可调整增强战斗 AI 的开关、撤退与恢复血量阈值、寻找掩体范围、恢复动作间隔、盾牌格挡时长与概率、战术走位和战斗跳跃等行为。`item_recovery_enabled` 控制 AI 是否使用可用恢复物品。关闭增强 AI 不会关闭实体、雇佣关系或基本战斗。

## `recruitment`：雇佣上限与玩家间伤害

`max_hired_by_tier` 分别设置每位玩家可正式雇佣的流浪者、一阶、二阶和三阶人类上限，默认值为 12、10、6、3。

`allow_hired_pvp_damage` 默认关闭。开启后，雇佣人类可在其交战模式允许的情况下伤害其他玩家及其雇佣人类；自己的主人和同一主人的单位仍受保护。

雇佣费用和身份牌资格不在这个配置文件中调整。

## `tacz`：可选枪械兼容

此区域仅在安装 TaCZ 且 `enabled` 开启时生效。相关字段控制各阶枪手获得枪械的概率、枪械掉落率、伤害倍率、枪械类型权重和枪械池。

- `tier_gun_type_weights`：设置不同阶级对手枪、冲锋枪、步枪、机枪、狙击枪和霰弹枪等类型的抽取权重。权重越高，抽到该类枪的相对机会越大。
- `gun_whitelist`：枪械 ID 与权重表，用于野生枪手的候选枪械池；正权重表示允许抽取，0 表示明确排除。
- `hired_gun_additional_whitelist`：只追加已雇佣人类背包界面允许输入的枪械，不扩展野生枪手抽取范围。
- `gun_blacklist`、`excluded_gun_namespaces`：禁止指定枪械 ID 或来源命名空间。
- `auto_add_new_guns`、`auto_added_gun_weight`、`excluded_auto_gun_types`：控制是否将新注册枪械自动加入野生枪手候选池及其权重、排除类型。
- `human_gun_damage_multiplier`、`tier_damage_multipliers`、`shield_damage_multiplier`：调整枪械对人类、各阶单位以及持盾格挡目标的伤害倍率。

野生枪手抽取和玩家手动给已雇佣人类输入枪械使用不同规则。基础白名单为空时，已雇佣人类可接受能识别的 TaCZ 枪械；基础白名单非空时，只接受其中正权重枪械，以及 `hired_gun_additional_whitelist` 中正权重枪械。`auto_add_new_guns` 不会放宽手动输入限制。

## 独立 TOML 设置

部分结构联动设置仍在 `config/hostile_humans.toml`，例如 Waystones 结构内容开关。该文件与统一 JSON 分开读取；其选项不会替代本页介绍的属性、刷新、AI、雇佣和 TaCZ 设置。
