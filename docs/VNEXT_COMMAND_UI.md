# 指挥界面与网络接入

本阶段基于命令底层提交 a9f9ba57。界面仅显示服务端快照，提交命令不直接改客户端选中态；距离拖动只保留临时预览，松开才提交。单兵与小队共用 `CommandView` 包适配和 `CommandPanelScreen` 三列布局，几何、单兵写入、批量所有权/加载状态规则继续由既有模块解析。

## 主控需要的接入

- 调用 `SquadCommandNetwork.register()` 注册新增 `squad_commands` channel，协议1。
- 旧 `HumanCommandNetwork.register()` 现在公开，协议从7升8；`SoldierRosterNetwork` 从6升7。客户端与服务端必须同版本。
- 注册 `SquadCommandTokenItem(new Item.Properties())`，ID `hostile_humans_unified:squad_command_token`；注册 `NaturalStateToolItem`，ID `hostile_humans_unified:natural_state_tool`。两个构造器公开且限制堆叠1。
- 把两物品加入创造模式标签；不加配方、不在雇佣时发放。模型已分别引用原版 name_tag/stick，语言提供名称及操作提示。
- 本阶段不编辑 HumanRuntime、注册表、配置或实体生命周期。既有 GrowthCombat 调用 HumanCommandNetwork.tickServer，其内部转调小队刷新。既有名册退出/停服钩子同步清理单兵与小队会话。

集成必须同时提供主控接口：`HumanFactions.id(Human)`、`AttributeDetailNetwork.requestOwned(int entityId,UUID humanId)`、`SquadCommands.reanchor(ServerPlayer,UUID token,int number):Result`，以及 `SoldierOrder.apply` 中 null order 表示不修改的修正。UI分支不创建替代类或复制这些机制。

## 玩家操作

单兵/小队面板为跟随、护卫、驻守三入口，战斗模式仍为原四种语义。驻守的 stay 与 chase 两开关独立；follow 活动距离和驻守半径均8～96、步长1。stay=true/chase=false时距离条置灰但保留显示。开启追击显示服务端外圈和时限。

两个面板驻守列都有“当前位置重新设岗”。该操作独立提交，不把调半径当作重新取家。单兵经合法会话调用 `SoldierOrder.set(h,GARRISON)`，保留当前参数并写主控新增维度；小队经统一 `SquadCommands.reanchor` 固定名单，逐人取当前脚部位置。未加载成员照常保持原命令/锚点。

`CommandView.reason` 来自服务端 `OrderDistances.context(h).reason()`。缺驻守点、旧点无维度、驻守点在其它维度均显示具体恢复提示；不会把角色无声留在原地。驻守点与 `order_anchor_dimension` 的核心读写由主控接入。

小队顶部显示真实总数、可提交/未同步人数、三模式人数；控件显示首个已加载成员，提示本次所改设置会批量套用。成员弹层逐条标注已加载或未同步，保持同一会话；结果显示成功、未加载、拒绝、失败四类人数，周期刷新不清掉上一次结果。所有成员都未加载时仍能查看名单，不补发命令。

名册保留分页、阶级筛选、召回、成长详情、顺序调整。卡片左上小方框或 Shift 点击支持多选（一次最多64），小队管理面板可编入/移出、按队筛选、改名、绑定手持令牌。点击队名编辑，服务端按配置字符上限过滤与截断；默认16个 Unicode 码点，不按UTF-16半字符截断。小队筛选期间关闭手工排序，避免与完整名册排序产生不可见交换。

名册的完整指挥按钮打开当前同维度、64格内已加载士兵的独立面板；属性详情按钮仅请求主控提供的只读属性 channel，不在本阶段计算属性。

令牌右键打开绑定队伍；未绑定的本人令牌首次绑定1队，潜行右键按配置队号循环。别人的绑定不能打开/改写。自然状态调试棒只调用 `NaturalStateCommand.useDebugTool`，权限2；普通右键切预设，潜行右键检查。

## 服务端边界

单兵每次提交均携带 token、human UUID、entityId；服务端复核会话、维度、存活、正式所有者、64格距离和请求限流。确认后回发完整状态，失效按token关闭面板。旧面板的回复/关闭不能影响新token面板。

小队每次批处理（含重新设岗）均复核实际主/副手物品为 `SquadCommandTokenItem`、物品绑定本人且队号匹配、服务端会话仍有效，再调用统一批处理 API。不能仅靠伪造 NBT 或曾经打开过的token提交。没有强加载、召回、跨维度瞬移或离线补发。已有模式、拾取、距离、驻守参数均沿同一单兵写入口。

名册编队/改名/绑定沿当前名册token验证，成员在 SquadData 再次复核正式雇佣账本和已加载实体归属。分队前检查容量，部分失败不会移走原队中未成功的成员。所有新包都有显式方向、数量边界、字符串上限和距离枚举校验；结果保留可翻译 Component，不在服务端提前转成固定语言字符串。

`HumanData.getPersistedCommandData` 是只读快照白名单，不改变实体完整保存。保留 order_radii/order_garrison/order_anchor/order_anchor_dimension、命令/战斗/拾取、完整growth、faction/boss、natural_state/natural_origin；不复制 inventory 或路线运行日志，不另造 specialty NBT。

## 验证边界

本阶段静态测试检查协议、显式三模式、所有提交的会话与身份顺序、真实手持令牌验证、服务器回包、临时滑杆与服务端选中态分离、名册多选上限、只读成长、Unicode命名依赖、维度原因/重新设岗入口、语言与模型及无配方。Java只进行语法解析，未执行语义编译或 Gradle。

仍需主控统一编译，以及游戏内检查三列实际字宽/缩放、滑杆与按钮命中、名册输入焦点、多人重放/伪造包、混合已加载队伍、同模式重新设岗与旧存档维度恢复。静态检查不能替代这些验收。
