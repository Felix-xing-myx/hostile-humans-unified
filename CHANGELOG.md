# Hostile Humans Unified — 更新日志 / Changelog

## 3.2.0 — 2026-09-26

### 简体中文

- **贴岸上岸助跳**
  - 只有寻岸路径前方存在近距离的干燥高岸，且生物接近水面时，才触发短促、限高的上推和向岸助力。
  - 助跳持续时间与触发冷却受限，深水中不会触发，避免再次从水中异常跃出。
- **水中姿势与移动**
  - 大多数浅水及接近水面的移动不再维持游泳姿势；只有需要持续游泳时才使用游泳姿势。
  - 修复寻岸范围不足及贴岸时无法上岸的问题，并限制助跳只在岸边触发，避免在水中被抛向高空。
  - 搜索范围扩展至 128 格并加密岸边方向采样；无可达路线时仍保持寻岸优先级并定期重试，不再把移动控制交回可能继续深入水中的战斗寻路。
  - 战斗、闲暇和撤退时都会寻岸；撤退选点可优先选择远离威胁的岸边，寻岸移动也会更强烈地避开不必要的水路。
  - 正在寻岸时，水下较低处的敌人不再压过上岸目标、把人类拉回深水；只有没有寻岸、逃跑或恢复氧气需求时，才会继续追踪水下目标。
  - 修复水下战斗目标移动覆盖寻岸路线的问题；若暂时找不到完整可达路径，人类会持续朝最近的已加载干燥岸点移动，并在抵达/受阻后重试寻路，而不是停在水中等待。
  - 枪械、弓、弩、三叉戟用户寻岸时保留瞄准与攻击，但不会用战术走位覆盖寻岸导航；只有具备射界时才进行远程攻击。
- **验证**
  - Java 17 下执行 `gradlew clean build --offline` 成功；项目配置的 Gradle `check` 检查通过。
  - 未启动 Minecraft 进行游戏内测试。

### English

- **Controlled shore pop**
  - A brief, capped upward impulse and small forward push now trigger only near the surface when a nearby dry, higher landing lies ahead on the shore route.
- **Water posture and movement**
  - Humans no longer retain the swimming pose in most shallow-water and near-surface movement; the pose remains for situations that require sustained swimming.
  - Fixes shore-search and shore-exit issues, and confines the small pop to the bank to prevent Humans from being launched high into the air while in water.
  - Shore searches now cover 128 blocks with denser directional sampling. If no route is reachable, shore-seeking retains movement priority and retries instead of handing control back to combat pathing that may lead farther into water.
  - Humans seek shore while idle, fighting, or retreating; retreat route selection can favor a bank farther from the threat, and pathfinding more strongly avoids unnecessary water routes.
  - While shore seeking, a lower underwater enemy can no longer override the exit goal and pull the Human deeper; underwater pursuit resumes only when there is no shore, retreat, or breath-recovery priority.
  - Prevents combat target movement from overwriting an active shore route. If no complete route is currently available, Humans keep steering toward the nearest loaded dry bank and retry pathfinding after reaching or stalling near it instead of waiting motionless in water.
  - Gun, bow, crossbow, and trident users keep aiming and attacking while shore navigation controls movement; ranged attacks fire when a clear shot is available, without tactical strafing overriding the shore path.
- **Validation**
  - `gradlew clean build --offline` succeeded on Java 17; the project's Gradle `check` tasks passed.
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
