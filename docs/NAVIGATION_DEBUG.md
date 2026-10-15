# 导航测试与动态观察 / Navigation diagnostics

工具只支持本模组存活的Human。操作需要创造模式或管理员权限；没有生存配方，物品可从模组创造标签页获取。两个物品分别为：

- `hostile_humans_unified:navigation_test_pointer`：导航测试指针。
- `hostile_humans_unified:navigation_observer_helmet`：导航观察头盔，装备在头部。

## 操作

1. 右键人类选中；只观察自主AI时，到这里停止操作，戴上头盔即可。
2. 指定点测试时，右键方块，将点击面相邻的方块空间作为脚部目标。通常点击地板顶面；侧面点击可能指定不可站立的位置，导航可以拒绝。
3. 潜行右键空气或方块，释放本人的测试与选择。到达、120秒超时、退出、跨维度、实体离开128格观察范围等也释放测试。摘头盔只停止观察，不取消尚在进行的固定点任务。

固定点测试临时拥有MOVE/LOOK，阻止战斗选敌、士兵命令及传送脱困干扰。原命令、归属、装备和生命不改写；结束后重新交还自主AI，不恢复过时的攻击目标。正在使用物品、恢复占手、撤退、岩浆、睡眠、骑乘或菜单编辑等状态不允许开始；任务中进入不适合状态也取消。

测试复用现有HumanNavigation、普通预算、路径安全、开门/栅栏门/木活板门、梯子及MovementContinuity。地面防摔检查仍根据当前生命；测试没有扩大搜索范围、节点预算、强制危险路线或提供传送。水面测试使用可浮水地面导航和既有上岸移动辅助。铁门仅有既有附近可见按钮/拉杆交互，不增加先走到开关再开门的任务规划。

## 观察内容

服务器每5tick只向佩戴头盔的选中者发快照。显示选择实体、最近请求点/固定目的地、敌人、当前实际路径及终点、下一节点、完整/部分路径、生命、摔落伤害预算、最大允许落差、有效搜索范围、最近额度申请和最近地形阻断。

路径最多包含256个剩余节点，超出明确标记省略，实际终点仍单独显示。候选最多48条，保留5秒；HUD显示最近5条的来源、坐标、结果和可用评分。线条可透墙，颜色：请求点黄、敌人红、路径/实际终点青、下一节点白、尝试/部分紫、评分通过/采用绿、拒绝红点。评分通过不等于最终采用，实际活动路线才是移动事实。

地面路径请求/安装入口覆盖各行为的请求结果；战斗地形、可见射击位、增强撤退和战术点另记录候选评估，找岸记录选出结果。预算使用原池原数值；观察不请求额外路径。没有佩戴头盔时不采集候选，不建立全服持续轨迹。

“最近请求点”可能是试算候选，不保证是最终决策；“实际终点”来自当前路径节点，部分路径可能离请求点很远。`no_path`只表示空结果，不证明是节点耗尽或距离超限。耗时来自最近地面导航调用，包括路径复用和同步检查，不是全服寻路总耗时或严格A*耗时。记录的是候选点，不是A*探索过的全部内部节点；未接入评分的行为只能看到请求和实际路线。

客户端只展示服务器快照；两秒无更新、跨维度、UUID不匹配、摘头盔或退出时隐藏。选择/任务不写入存档。

## 建议验收

在同一超平坦场景比较“固定点测试”与“只观察自主AI”：直线路面、近/远入口长墙、U形墙、梯子、一格台阶、当前血量不同的落差、木门/栅栏门/铁门开关、水边。另检查潜行取消、到达/超时恢复、两玩家抢占、不同观察者、摘头盔、退出和重载，以及无可选模组的服务端启动。

## English

Creative/operator-only Human diagnostics: select a Human with the pointer; click a block to request the adjacent foot-space as a temporary destination. Sneak-use cancels. Wear the helmet for passive live observation; selection alone does not change AI. Removing the helmet hides observation but does not cancel an active test.

Fixed tests temporarily own movement and suppress combat/order interference and teleport rescue, while retaining real navigation, shared quotas, fall safety, passage/ladder interactions and continuity repair. No new iron-door puzzle solver, larger range or search allowance is provided. Orders and ownership are retained. Tests end on arrival, cancellation, invalid lifecycle/range, or after120seconds.

Bounded server snapshots distinguish requested candidates from the live route and its actual endpoint. Candidate scores are not final decisions. Empty paths have an unknown cause; displayed ground-navigation call time includes cache reuse. Internal A* expansion nodes and uninstrumented candidate scores are not shown. Multiplayer and rendering require in-game acceptance.
