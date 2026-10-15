# 导航测试与多实体动态观察 / Navigation diagnostics

需要创造模式或管理员权限，仅本模组Human，最多8只。物品：`hostile_humans_unified:navigation_test_pointer`与`hostile_humans_unified:navigation_observer_helmet`；创造标签页获取，没有生存配方。

## 操作

- 右键实体：加入观察并聚焦；已有成员只聚焦，不取消其他任务。
- 右键空气：轮换焦点。
- 右键地板：点击面相邻空间为当前焦点的脚部目的地；其他实体继续独立任务。
- 潜行右键实体：移除这一只，并仅释放本人的对应测试。
- 潜行右键空气/方块：清空本人的全部观察与测试。

先选择A并指定左侧目的地，再选择B指定右侧，二者同时执行。戴头盔只选实体、不点目的地，即纯观察。摘头盔隐藏画面，不取消测试。编号/颜色为观察者内稳定槽位，头顶标号、路径、请求点/终点及独立卡片一致；焦点额外显示详细面板和候选点。

## 控制与同步边界

复用现有强化地面导航、普通配额、生命/落差/危险地形、开门/栅栏门/木活板门、梯子、连续性修复及水面上岸辅助。固定点任务独占MOVE/LOOK，暂停战斗选敌、命令/传送救援干扰，不改原命令/归属。铁门不增加开关解谜规划，不扩大路径范围或节点预算。

任务独立最长120秒；到达/取消/状态失效/128格范围外/退出/跨维度时释放对应成员，其他任务继续。服务器以成员身份验证控制，不以当前焦点验证，因此切换焦点不会取消路径。多人可共同观察，一只实体的测试租约仍独占，移除观察不会取消他人测试。

每5tick发送最多8成员的全量帧；每实体256剩余节点、最近16候选，服务器候选保留48条/5秒；网络协议为2。客户端使用服务器位置，即使实体暂时未被客户端追踪也可显示；维度/UUID及2秒新鲜度检查保留。帧取代旧集合，避免移除后的残留路径。

## 渲染与诊断

一个静态BufferBuilder复用原生存储，每实体结束提交，禁止每帧新建；相机相对坐标在转float之前完成。每实体概览显示编号、模式、状态、节点和请求点；焦点详情含实际终点、生命/摔落预算、有效范围、配额/地形阻断及地面调用耗时。焦点详情位于左侧，字号80%，宽度上限为GUI宽度42%与240像素中的较小值（小窗口最小目标80像素，仍限制在屏内）；长文字换行，每行背景贴合文字。概览卡片最多155 GUI像素宽。小窗口可调低GUI缩放。

路径/请求点/实际终点用实体色，下一节点白；焦点敌人红、候选紫/绿/红。评分通过不保证采用；请求点可能是试算，活动路线和实际终点才代表当前执行事实。空路径原因未知；调用耗时包含复用，不是全服A*时间；不显示全部内部扩展节点。

## English

Creative/operator-only Human diagnostics, up to8 members. Click Humans to add/focus, use air to cycle focus, click ground to assign only that member's destination. Other tests continue. Sneak-click a Human removes it; sneak-use air/block clears all your members/tests. Wear the helmet for independently colored paths, numbered nameplates/cards and focus details. Removing the helmet hides observation without cancelling tasks.

One reusable native buffer replaces per-frame allocations. Camera-relative coordinates avoid distant float precision loss. Bounded replace-all frames (protocol2) carry eight members, 256 path nodes/member and16 recent samples/member every5ticks. Orders, ownership, safety, normal quotas and exclusive per-Human controller leases remain intact; switching focus never releases another member's task.

Validate long-running distant viewing,8 simultaneous distinct destinations, stable slot colors/removal, focus cycling, lifecycle cancellation, tracking loss, multiplayer and passage/height/water behavior in game. Automated checks do not prove live rendering acceptance.

## 颜色图例界面 / Color legend

游戏中默认按F8打开可滚动图例，Esc或完成关闭；可在控制设置的Hostile Humans Unified分类重绑。HUD提示显示当前绑定。无需先选实体或佩戴头盔即可查阅。界面不暂停游戏，也不修改现有观察或导航任务。

图例列出1～8槽位颜色、同色实体/请求点/实际终点/路线的区别、白色下一节点、焦点敌人红色大框及候选紫/绿/红色小框；评分或完整路径结果不等于最终采用。图例只作颜色参考，实体实际信息仍在观察面板。

Press F8 in game for the scrollable color legend; rebind under Controls > Hostile Humans Unified. Esc or Done closes it. It explains the eight slot colors, route/position/request/endpoint markers, white next nodes, focused enemies and candidate evaluation colors. The game and navigation tests continue while it is open.
