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

默认每5tick发送最多8成员的全量帧；每实体256剩余节点、最近16候选，服务器候选保留48条/5秒；协议3的帧包含有界感知快照。客户端使用服务器位置，即使实体暂时未被客户端追踪也可显示；维度/UUID及3秒新鲜度检查保留。帧取代旧集合，避免移除后的残留路径。

## 渲染与诊断

一个静态BufferBuilder复用原生存储，每实体结束提交，禁止每帧新建；相机相对坐标在转float之前完成。每实体概览显示编号、模式、状态、节点和请求点；焦点详情含实际终点、生命/摔落预算、有效范围、配额/地形阻断及地面调用耗时。焦点详情位于左侧，字号80%，宽度上限为GUI宽度42%与240像素中的较小值（小窗口最小目标80像素，仍限制在屏内）；长文字换行，每行背景贴合文字。概览卡片最多155 GUI像素宽。小窗口可调低GUI缩放。

路径/请求点/实际终点用实体色，下一节点白；依法取得位置的焦点敌人红、候选紫/绿/红。评分通过不保证采用；请求点可能是试算，活动路线和实际终点才代表当前执行事实。空路径原因未知；调用耗时包含复用，不是全服A*时间；不显示全部内部扩展节点。

## English

Creative/operator-only Human diagnostics, up to8 members. Click Humans to add/focus, use air to cycle focus, click ground to assign only that member's destination. Other tests continue. Sneak-click a Human removes it; sneak-use air/block clears all your members/tests. Wear the helmet for independently colored paths, numbered nameplates/cards and focus details. Removing the helmet hides observation without cancelling tasks.

One reusable native buffer replaces per-frame allocations. Camera-relative coordinates avoid distant float precision loss. Bounded replace-all frames with perception snapshots carry eight members, 256 path nodes/member and16 recent samples/member every5ticks. Orders, ownership, safety, normal quotas and exclusive per-Human controller leases remain intact; switching focus never releases another member's task.

Validate long-running distant viewing,8 simultaneous distinct destinations, stable slot colors/removal, focus cycling, lifecycle cancellation, tracking loss, multiplayer and passage/height/water behavior in game. Automated checks do not prove live rendering acceptance.

## F8综合详情 / Comprehensive details

默认F8打开不暂停游戏的详情，可重绑。概览、目标/记忆、视野、声音/信标、动作/导航、预算六页支持滚动与图层/焦点显示。图例保留八槽颜色、路径、请求/终点和候选解释；详情列识别进度、动态/最后/旧敌剩余、回看队列、警觉原因、当前决策及真实Goal动作/控制标志。信标页显示共享发报剩余、最后使用、下一报告、报告年龄/TTL和拒收原因。

世界标记区分方向、识别、目视、动态位置、最后位置与信标网格。几何视野不保证无遮挡；LOS只显示已有采样及年龄。未知方向不显示隐藏射手身份，动态期结束不继续绘制隐藏目标实时框。面板明确未检测/配额等待、命令阻止和固定点接管。快照默认5tick、有观察者才生成，不因翻页重扫实体或改变AI。详情焦点和图层只调整显示，导航指针点击目的地仍是独立控制操作。

视野图层的“焦点·简略”只显示当前诊断焦点的一条短朝向线和一个8格以内的扇形轮廓，不显示多距离视野环、旧敌扩展环或历史LOS；它帮助看清朝向和近处角度，不代表实际视觉距离只有8格，也不展示完整三维视锥。“所有”模式显示各观察对象的完整视野环和实际LOS采样。其他导航、记忆、线索图层仍独立控制。F8切换实体仅改变诊断显示；随后用指针切换焦点，会解除旧F8覆盖并显示新焦点。焦点失效时自动回到有效成员，普通数据刷新不改变已选F8焦点。

F8 opens six read-only pages: overview, targets/memory, vision, clues/beacons, actions/navigation and budgets. Existing colors and navigation controls remain available. Marker positions respect the Human's knowledge stage; geometric cones are distinct from sampled LOS. Display controls never issue AI commands.

目标详情显示并行视觉识别数量（最多2）及各目标独立进度，注明“主观察/控制转头”或“视野内并行识别”。视野页显示配置的近/中/远完整夹角；世界视野标记使用同一服务器角度规则，仍只表达潜在几何范围。

身体可见性射线连接实际暴露的身体采样点。最近视觉结果显示身体遮挡检测配额等待/尚未完成；只呈现服务器已做的检测，不为诊断追加身体射线。
