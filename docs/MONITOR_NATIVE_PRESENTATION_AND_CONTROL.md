# 监控原生枪械表现与流畅接管：实施约定

状态：两模块已实施，完整自动构建与回归通过，3.13.28已同步既定实例；实际游戏画面和网络手感尚待验收。

## 目标和现有链路

监控和原生旁观共用 HumanCameraView 与 HumanSpectatorView；AFTER_ENTITIES先选择受支持的TaCZ原生第一人称骨骼，否则保留隔离NPC手臂。两条入口均与真实玩家状态隔离。

接管鼠标已经从原版LocalPlayer.turn入口取得处理后的输入，本地cameraAngles和隔离手臂姿态均直接使用controlYaw/controlPitch。原延迟不能笼统归为转头同步：改造前位置按普通实体跟踪（默认3tick）、客户端lerpTo 3tick及相机帧插值，且移动需等待服务端反馈。

## 模块分工

| 模块 | 拥有的状态与接口 | 禁止越界 |
|---|---|---|
| monitor | 接管租约、输入序号、实际消费ack、post-travel权威运动快照 | 不接收客户端位置、速度、伤害或弹药 |
| monitor/client | 本地视角、有限输入历史、隔离预测体、校正与相机位置 | 不更改真实玩家或被跟踪Human的物理状态/本地控制标志 |
| client/render/tacz | 独立枪械模型、原生骨骼采样、NPC皮肤手臂、显示缓存 | 不使用真实玩家operator或共享display状态机，不运行有权修改NBT的枪包动作上下文 |
| compat/tacz | 现有权威换弹工作与视觉元数据、共享clip/声音计划 | 不因显示完成而提前填弹或结束服务器换弹 |
| HumanSpectatorView | 单一的原生枪械／旧手臂绘制选择入口 | 不再散布逐枪判断；原世界第三人称渲染不被接管 |
| Mixin | 必需的原版输入/相机/碰撞调用桥接 | 保持薄层，不承载新的AI或武器状态机 |

## 枪械表现

以实际安装TaCZ1.1.7-hotfix2和编译API 1.1.8-hotfix分别核对兼容边界。公共模型工厂和动画资源可创建独立model/ObjectAnimation，直接采样骨骼；ObjectAnimation.update的进度单位为纳秒，不应误传秒。替换lefthand_pos/righthand_pos委托，避免原生委托读取Minecraft.player。

服务端记录换弹开始时空仓/枪膛/弹量/扩容等只读视觉元数据，继续用Work/WorkRate/WorkTick推进。覆盖弹匣式、空仓/战术、扩容和intro/loop/end逐发换弹。进入中途直接seek，不从头重播；采样骨骼静音，现有声音层仍是唯一声音所有者。自定义枪包无法识别的脚本轨道必须安全回退，不能宣称任意Lua均已复用。

接口：TaczSpectatorFirstPerson.tryRender(Human,partialTick,PoseStack,MultiBufferSource,light)。传入旧frame之前的camera-relative世界视图矩阵，adapter使用独立镜头坐标、手部投影与原生定位；成功跳过旧臂，失败保留原显示。普通调用方只通过无TaCZ类型描述符的可选依赖门禁访问。

缓存按观察身份、稳定枪身份、换弹会话、资源代次隔离。切枪、退出、取消、死亡、卸载、刷新均清理，不因每发弹量NBT变化重建模型，不允许弱键值反向强引用形成泄漏。异常须隔离矩阵、缓冲及已绘制内容，禁止整世界反复报错。

## 接管预测

仍发送20Hz方向/动作/角度，增加序号；只向当前接管者发送20Hz post-travel快照。ack必须是本tick实际消费的输入，而不是最大收到序号。客户端固定32项历史、单次最多8步重放，按serverTick拒绝旧包，以权威位置/速度重建基准；有限短校正只作用于显示，不混进下一步物理。

轻量LivingEntity预测体不加入世界、不运行AI/背包/战斗。优先复用原版travel及原版collide的碰撞和台阶，move只桥接碰撞结果，隔离Block接触回调、伤害、事件、声音和粒子。原生跳跃只保留纯运动与冷却，不二次派发Forge事件。普通地面/墙角/半砖/楼梯/跳跃与可安全支持的原版流体/梯子属于实施范围；有回调的弹跳/黏滞特殊方块、未知流体、滑翔和未加载区块必须明确回退权威相机，保留后续纯物理adapter接点。

服务器实际速度、碰撞尺寸、台阶、重力和效果决定参数；不把首领视觉缩放当碰撞，不写死普通玩家速度。聊天/菜单/失焦暂停输入；单机暂停不累计历史。退出、目标失效、租约失效与权限变化清空全部预测状态，服务器Human保留原AI恢复入口。

## 验证和发行

1. 协议与纯规则：序号/ack、旧token/旧tick、有限数值、缓存与重放上限；换弹clip/阶段/进度/取消规则。
2. 编译/API与隔离：当前JAR公共接口、客户端/可选依赖类加载、Mixin映射、共享状态不被写入、缓冲与矩阵收尾。
3. 自动集成：真实碰撞/台阶、直行/斜行/跳跃/药水/尺寸及服务器/客户端状态对照；标准枪包动画和循环声音计划。
4. 用户游戏验收：监控/原生旁观、空仓/战术/扩容/逐发、快慢换弹、进入中途、切枪与退出；接管0/50/150/300ms延迟、地形、暂停/背包/死亡和AI恢复。自动构建不冒充游戏内测试。
5. 全部自动门禁通过后走统一release.py，dist留存，确认实例停止、备份旧JAR、三处SHA-256一致，配置不改，不自行启动游戏或推送。


## 实施审查补充

- TaCZ使用公开Bedrock数据及独立ObjectAnimation，只采骨骼，不执行玩家Lua上下文。当前JAR内M870和M1014均标记magazine，但有完整逐发clip；循环按真实intro/loop/end能力与原生FEEDING/FINISHING阶段识别。M870逐壳供弹不重置整体FEEDING截止点；FINISHING早报时仍保留尚未完成的最后循环。
- 第一人称枪械在独立手部FOV下绘制，先排空世界顶点，完成后finally排空手部顶点并恢复投影；不清世界深度。原生枪械muzzle节点转入已有NPC枪口光入口，骨骼静音，音效仍只有原换弹层一个所有者。
- 换弹模型按稳定显示资源缓存，物品只在真实来源或NBT/数量变化后更新隔离副本。未知Java模型类型、节点或动画格式保留旧呈现；拔枪及枪托等未采样前台动作也沿原NPC动作，避免用静态持枪覆盖它们。没有宣称任意Lua或所有第一人称动作均重放。
- 预测体构造专用客户端Mixin隔离Forge尺寸、实体构造和能力附着事件；真实实体路径不变。所有跳跃、碰撞和move副作用均隔离。Human固定不趴游，当前native travel不依赖swimming/Pose，已传眼高与尺寸，不额外改变潜泳机制。
- 小校正同时平移前后帧轨迹并抵消展示偏移，不在每次包到达时重置previous；只在模拟tick将偏移减半。0.35格以内且无碰撞才平滑。服务器0.5秒无新快照、32历史/8步重放或4096方块预检越界均回退权威视图。
- 实际验证：477项Python（13跳过）；Java编译、NativeReloadPlan、MonitorInputReconciliation、MonitorNativeMotionBridge、MonitorPresentationTrack、MonitorPredictionBytecode及HumanSpectatorProjectionContract通过。Shapes碰撞/跳跃桥与ASM检查不等于完整游戏物理对照；实际图形、枪包取景、低顶/复杂地形及多人网络手感尚待用户验收。


## 正式交付

3.13.27完整build/check + lootScreeningTest成功，118个Gradle任务（114执行、4复用）；无TaCZ等依赖的实际JVM链接覆盖1092个核心类。编译和重混淆产物均通过预测Mixin/输入契约，第一人称投影、换弹计划与连续收包轨迹回归通过。Python477项（13跳过）、默认附录/Wiki/公式及空白检查通过。3.13.26失败凭证保留且未部署；修复后3.13.27才作为正式产物。

构建/dist/测试实例SHA-256一致，实例配置保持不变，完整发行凭证见维护目录maintenance-tools/releases/3.13.27/state.json。未启动游戏、未进行实际GPU或多人延迟验收、未推送。重点待验收：空仓/战术/扩容/逐发换弹与中途观察，盾牌/切枪/退出，普通与头目接管的直走、斜走、跳跃、楼梯、低顶、流体、梯子、暂停及不同网络延迟。


## 实际运行依赖的接口校验

3.13.27实际日志确认：测试实例TaCZ为1.1.7-hotfix2，编译API为1.1.8-hotfix；前者完全没有GunDisplay.enablesTransparency()Z，导致原生模型绘制在提交顶点前失败并回退旧臂。透明材质访问现集中按公开能力解析，旧版沿其原生entityCutout材质，缺少该扩展不再使整套换弹退回。

新增TaczNativeRuntimeLinkageTest遍历原生展示模块及本轮时钟/声音适配的258处直接字段、方法和lambda句柄，分别对编译API与实际实例JAR核对完整JVM描述符/公开可访问性。3.13.27旧类作为负对照，精确检出上述唯一缺失方法。TaczNativeMaterialCompatibilityTest分别在两版真实依赖类加载环境调用能力入口，新版开关true/false及旧版缺方法均通过；不启动Minecraft或图形上下文。

统一构建辅助脚本从release-settings.json指定实例读取modId=tacz的[[mods]]声明，排除仅依赖TaCZ的附属模组，再显式传taczRuntimeJar给回归；不升级/替换依赖。默认编译API与实际安装依赖必须分别标明，不能再把编译JAR当作实例JAR。公开克隆可用-PtaczRuntimeJar指定目标；未提供时只检查所选API，不宣称验证未知实例。


3.13.28收尾：上述实际依赖接口兼容修复随完整构建及121项Gradle任务通过，正式JAR与dist和约定实例哈希一致；实际TaCZ JAR哈希未改变。知识库、负对照和两版真实类行为凭证已留存，未启动游戏或推送，原生动画实际画面仍待用户验收。

## 连续移动展示与镜头晃动

- HumanSpectatorMotion统一当前观察对象的精确double游戏时钟、插值/接管预测位置和有界步态。GameRenderer.bobView的已核验SRG客户端薄注入补齐Human镜头，复用原版变换和视角摇晃选项；原生手部独立投影单独应用一次，同帧取样幂等。
- TaCZ每枪资源优先，显式默认其次，最后按公开默认类型复制原生prototype再绑定独立模型；不操作共享display/model/Lua或LocalPlayer。NativeMovementPlan按距离取循环相位，0.2秒交叉淡化，原生run_start/run/run_hold/run_end、方向与ADS步态；换弹让出，最近开火回闲置。加权监听仅用于加法通道，scale围绕1加权。
- 换弹checkpoint先把long游戏时间转double再加partialTick，避免长时间世界浮点精度丢失；ADS只做展示平滑，响应时间0.07秒，不改变权威条件。没有新增网络包/后台线程或服务器预算。
- 待实际图形验收：TaCZ1.1.7各枪包的步行/跑步/侧移/瞄准/换弹，长时间世界高帧率，普通/头目、临时接管、跳跃、暂停/退出/切枪、关闭视角摇晃。自动验证不代表GPU帧率或手感已验收。

## 后台装填音轨与原生枪声受众

- 后台速度策略backgroundReload与可听状态分离；服务器不再因收枪/盾防丢弃reload timeline。complete终态保存原Gun/Session/Start和最终Work，替换于下次begin，不授权装填/动画。真正取消仍清空。自然完成在end前解绑VISUAL_OWNERS，保留有限的最后一条终态凭证，客户端冻结其时钟。
- NativeReloadAudio与旧格式fallback捕获原枪资源并绑定Session/Start；不再查询当前主手或盾姿态来取消。完成派发末尾cue后释放队列但不停止已播尾音，取消/死亡/移除/世界退出/资源重载只停止自身句柄。
- 实际TaCZ1.1.7的枪声不是GunShootEvent包播放，而是SoundManager.sendSoundToNearby→ServerMessageSound→playMessageSound。薄可选服务端注入复用这条原生消息，为有效监视摄像机补齐受众，保留原枪/显示/音色/音量/距离；严格按原生区块追踪+方块坐标距离排除已收件人。接口变更可选回退原生，不另发射击事件音，避免双播。
- 原生distance<=0、缺失枪包音频仍可能无声，未靠未知声源统一兜底。实际游戏应测普通近旁、远处监视、后台换刀/举盾、恢复原枪连射、取消/死亡、逐发与单段音轨；尚未把自动校验等同声音实听。

## 起落惯性与空中步态

- 实际TaCZ1.1.7 FirstPersonRenderGunEvent.applyJumpingSway读取LocalPlayer插值高度和onGround，起跳速度参照.42、落地-.1格/tick，输入300/150ms衰减，-2×progress经JUMPING_DYNAMICS后对root.offsetY加-spring/16。其共享SecondOrderDynamics每~6ms用50ms虚拟步长并自行开线程；不能把玩家静态通道直接调用到NPC。
- NativeJumpSway按同参照/幅度给单个Observation独立状态；换算原生虚拟时钟后使用逐帧有界临界阻尼响应，不创建线程。高度/时间异常及不支持移动重置，首入空中不重播起跳；根节点同时带动枪与捕获的双手。ADS复用统一20%幅度。
- HumanSpectatorMotion统一插值眼位及落地读取；接管新增只读predictedOnGround，复用eyePosition既有预测有效性门槛，不改变输入/预测物理。上下游同帧同客户端线程读取，失败回退实体状态。
- NativeMovementPlan修正run_start先完成再run_hold，空中不选run_end，行走腾空/跑步hold使用原生0.6秒淡化；不恢复已删除的原版手部bob。
- SpectatorMotionTrackTest扩展30/60/144帧起跳落地、界限、收稳、同帧幂等、晚进入、垂直传送、台阶、失效和腾空起跑/停止场景。Java编译及起落轨迹回归已执行；实际GPU取景、接管延迟和跨枪包动作仍需验收。

## 接管战斗的依赖与生命周期

- `behavior/combat/HumanCombatAuthority`定义外部控制权限。公共近战、弓弩和枪械不依赖监视器战斗会话；没有外部控制时继续按AI视觉与目标规则执行。手动目标输入不生成感知记忆。
- `monitor/combat/MonitorCombatSession`只解释服务器已验令牌/世代/序号的按钮与视角，绑定/释放Controller。菜单/失焦、超时、换武器、死亡/卸载/退出及背包编辑统一取消未出手动作；重新接受输入先等待中性按钮帧。客户端不上传目标、物品、伤害、射速或位置。
- 近战通过服务器射线选实体，沿`Human.doHurtTarget`和`MeleeActionControl`保留Better Combat前摇及动作资源。空挥只播放同一combo profile，不调用伤害；接管朝向不被AI的faceTarget改写。
- TaCZ可选后端使用两版共有的双Supplier shoot/aim/reload/bolt，沿GunAttackAction和原生事件授权；成功才消耗半自动按键，AUTO和可持续BURST支持保持。手动换弹只绕过AI空仓/25%决策，不绕过支付、身份、容量、原生状态和速度。不可再从此处重复tick原生装填。
- 薄客户端Mixin隔离真实玩家TaCZ Shoot/Aim/Reload入口，按真实版本分离必需和可选方法名；输入读取原生绑定。默认F6，旧R一次迁移，其他自定义保留。
- 弓弩只生成未入世界的弹丸，复用HumanArrowCombat的动作、扣箭、发布、附魔、阵营续发。斯巴达重弩通过既有bolt工厂的手动视角分支，保持原生重弩和普通弩工厂隔离。未知非箭弩载荷不强制执行。
- 已投出的弹丸不受会话关闭影响；客户端预测体仍没有任何战斗权限。其他枪械模组的手动开火、交互和盾防未纳入本轮。
- 回归覆盖按钮重置/半自动及自动触发、协议全部按钮组合、1.1.7/1.1.8实际方法及Mixin入口、无可选依赖链接、弓弩共享扣箭和模块依赖约束。自动核对不等同实机注入、视觉/音效或多人延迟验收。

## 监控完整体验扩展

- 权限集中MonitorAccessPolicy：创造任意有效Human，生存仅本人雇佣；持续校验与物品绑定、对讲机入口一致。监视器绑定UUID/维度/位置，实体右键只绑定，空气/方块右键进入；对讲机服务端owned ledger与真实实体共同鉴权。
- InventoryPaused为原租约内状态，进入菜单取消未完成动作、更新输入世代，NoAI沿原编辑锁临时保存，关闭后恢复世代与快照，不重建生物或退出接管。暂停仍占控制权，禁止第二控制者。失效会话仍正常释放。
- 生存监控背包采用无cursor的两格完整交换，只接受Human范围PICKUP；玩家格、shift、数字换栏、throw、drag、collect全部拒绝，关闭无需把NPC物品放回玩家背包。创造沿原菜单。灰队手部合法性统一ControlledEquipmentPolicy；普通受控装备与手动盾Owner.MANUAL、current-only双持互相遵守。
- MonitorLoadout由实物单件武器身份构建，最多32实物+空手；分离小菜单快照，只在内容/选择/世代变化时下发。Selection含token/generation/revision/服务器武器UUID，无任意物品数据；交换和同时清手通过HumanEquipmentExchange的一次布局事务，失败保留实物。数字1-9/滚轮选择，F副手，H补给。
- RecoverySupplies共享自动/手动持物执行内核，manual优先当前副手食物/饮用Potion，空副手或空瓶/空碗通过统一交换从真实背包补食物；成功食物回执后长按继续，松键取消；药水、失败或取消仍需松键重试；复用真实consume回执、原生药水与食物池。手动取消和普通动作暂停不重置既有再生药的脉冲计时。食物单独pendingFood由State同步，不能混入医疗池。
- NativeOptics持有独享附件model，读取真实附件/zoom，使用scope_pos+selected scope_view、原生stencil/division；ThreadLocal只存在同步渲染期。两个原生玩家FOV入口在任何授权Human camera时隔离，自有FOV只处理适合的NPC枪；结束恢复shader引用/actual program/纹理及mask，清ShaderInstance cache迫使后续apply重绑定，未知光影uniform不快照。
- MonitorRemoteView对原生ChunkMap坐标与TrackedEntity观察者距离施加局部锚点，不用ServerPlayer.setCamera（它会teleport身体）。每tick更新视点、保留原生PLAYER loading/simulation distance；身体和异步bootstrap仅小半径支持票据，按level/chunk引用计数，最后退出恢复body tracking后释放。100tick过期兜底，同维度；生存bootstrap坐标来自权威owned record，不信任物品里的旧位置。客户端最多200tick等待远程实体传输。
- 预测修正前后端各自插值衰减，包到达与tick边界连续；疾跑解释与服务器attack/use一致，不增网络频率、重放8步、历史32条和4096预检上限。失败回放丢弃并等待实际ack再恢复。普通旁观仍沿真实实体插值，不无依据外推碰撞。
- 新增：两版TaCZ光学及真实MC Shader缓存、Remote ChunkMap/Entity来源字节码，实际RecoveryCoordinator租约，五个战斗按钮全组合、菜单/选择codec上限、暂停/权限/物品跨域与布局静态契约。自动验证仍不等同客户端GL、原生注入、远程流式区块和多人游戏验收。

- 用户明确选择易按的H默认补给键并自行管理联动按键冲突；显示层每次render从真实KeyMapping读取武器格、背包、接管、指示、详情、副手、补给与退出键名，不缓存初始化字符串。改键后返回画面立即更新，菜单长键名按格宽缩放，不用硬编码数字或F8。已有自定义键及实例options不覆盖。


## 连续瞄准显示与多后端接管

NativeAimTimeline统一平滑服务端瞄准目标（tau45ms）和镜片倍率档位（tau70ms），按游戏时间+partialTick采样、同时间幂等，FOV、模型/镜片和鼠标共用结果。鼠标用TaCZ MathUtil.zoomSensitivityRatio及ZoomConfig客户端设置，不覆写真实玩家灵敏度、武器散布或服务端状态。

MonitorGunBackends共用菜单准入与服务端adapter factory。ScgunsMonitorWeapons/JegMonitorWeapons复用已有Control.shoot(null target)、GunAttackAction、授权、GunReloadSession及声音；原射击内核只为授权手动调用跳过不存在目标的状态读取，保留原生弹丸工厂和散布。空地射击桥由实际字节码方法/调用点计数门禁，SC兼顾原getLeadingDirection能力。三种枪的原绑定和短按捕获统一经MonitorGunInput；每种模组自身客户端处理器在监控时停止操作真实玩家。输入代际、菜单暂停与取消仍由原Session拥有。

玩家专属ChargeHandler伤害和SC/JEG的完整第一人称ADS/HUD未克隆；当前接入的是已有NPC支持枪械的射击与换弹。半自动成功才锁住一次按键，SC PULSE和JEG RELEASE_FIRE只为当前真实蓄力松键声明fireIntent，取消清除，持续枪不逐发重复预热。


## 手动TaCZ腰射精度

TaczMonitorWeapons在原生operator.shoot方向供应器前只抽取一对额外角度误差，TaczMonitorSpread集中计算hypot(3deg,tierGunSpread)*HumanGrowth.gunSpreadMultiplier*GunCombatProfile.spread*(1-serverAimProgress)^2，独立高斯每轴截断±2.5sigma。全ADS额外0且不消耗额外随机数；真实人类旋转、摄像机、子弹工厂、Native弹药/冷却/散布和自主AI入口不修改。方向供应器复用同一对偏差，不被客户端每帧重抽。新增纯策略分布/过渡回归登记到check；需用户构建指令后运行Java测试。


副手快捷切换与补给共用ControlledEquipmentPolicy.offhandFood/supplyRemainder分类：食物候选显式排除PotionItem；玻璃瓶/碗为可收纳余物。MonitorLoadout将副手余物视为补给侧，优先切回合格战斗副手，否则Need.empty仅收纳容器；RecoverySupplies仅在明确H输入时允许拿空容器换背包食物。统一HumanEquipmentExchange独占库存写入，失败保留完整原布局，不引入丢弃/删除/隐藏存储。手动持有完整药水仍允许按次饮用，不自动连喝。


## 监视武器HUD、命中反馈及补全动作

GunSupport.hud由各现有后端提供只读弹量、容量、模式及能量属性；MonitorWeaponSnapshot沿5tick租约复核且只在变化时发送，token/target/entityId/generation均核对。协议升为6；显示不借用LocalPlayer背包、不重算权威储备。JEG库存供弹沿原rounds虚拟键，SG沿能源capability，TaCZ沿附件容量和枪膛规则。

monitor/client统一布局和会话，client/ui/gun隔离原生贴图与音效；右下角6px安全边距，窄GUI缩放枪械HUD、收窄命令栏，始终在底部并排避开准星。TaCZ原生玩家HUD单独屏蔽，SG原生任意Post事件HUD使用可选客户端薄Mixin屏蔽，JEG沿既有hotbar取消；不全局取消其他mod的HUD。

TaCZ真实服务端碰撞生成的CLIENT Post/Kill互斥事件过滤当前被观察Human，使用局部300ms图标时钟、原生四象限贴图和枪包命中/爆头/击杀声音。音源传观察Human，沿SpectatorAudioListener收听；不改原生全局命中时间戳，不将原生Post误称为最终实际扣血证明。

下蹲使用真实canSlide与slide/idle/back资源，完全ADS权重归零；拉栓独立于reload，用同步bool上升边沿播放真实bolt轨道至自然结尾。物理冷却与动画长度独立；中途加入只能从该动作起点呈现。闲置副手不阻止原生手模，真实用品使用/同步盾防统一持有双臂和FOV。任意Lua自定义选轨不宣称全面兼容。
