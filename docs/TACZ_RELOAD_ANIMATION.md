# TaCZ换弹动画按实际时长缩放

## 实现范围

只改变HHU人类的换弹视觉，不改变原生换弹脚本、子弹到账、通用弹匣扣除、攻击冷却、换弹音效及后台换弹。玩家动画不接入此入口。没有新增必需前置或分项配置。

服务器TaczHumanReloadTransactions在授权换弹成功后创建TaczReloadVisualTimeline。HumanMobEntityData同步一份临时CompoundTag，包含枪ID、起点、预计终点、装填/收尾参考值及阶段标记。服务器依当前枪的空仓/战术换弹数据估算完整终点；cooldown是完成时间点，不与feed简单相加，逐发装填估算考虑缺弹数。首个原生装填阶段的实际倒计时校正速度修正；后续装填阶段可延长终点并保留收尾预算，进入收尾阶段后以原生剩余时间校准真正结束点。

快照仅启动、首次原生校准、阶段改变或终点漂移超过2tick时同步，不每帧发包。取消、完成、授权失效和原生状态清理会删除快照；弱引用避免额外持有实体。它不写入实体存档，既有后台换弹事务及其卸载规则保持不变。客户端只读快照；服务端拒绝客户端调用写入，未增加客户端上传进度的网络入口。实体同步结构变化，主网络协议由4升为5，客户端和服务端需使用同一新版。

## 动画播放

TaczNpcAnimator使用Player Animator公开SpeedModifier，并只挂到独立动作层。普通开火动作恢复速度1；走路、瞄准、身体朝向等层不调速。KeyframeAnimation.getLength、KeyframeAnimationPlayer动画起始tick构造器及公开当前/停止tick接口提供初始定位和剩余速度计算，新增部分没有读取私有字段或修改共享动画资源。

初次观察、资源重载、换枪切回或重建模型时，依服务器时间线定位当前动作进度；新换弹起点识别连续换弹，时间线包晚于原生状态时暂用程序动画，收到正确枪ID快照后恢复关键帧。后续终点更新调节剩余播放速度，不重新从头播放。有效动画末尾预留2个关键帧tick，防止Player Animator先自行失效；服务器仍在换弹而未知脚本延长时可保留末尾姿势，确认结束后4tick淡出。开火可抢占结束淡出，不让旧动作吞掉第一发的视觉。

没有Player Animator时，原生持枪与程序换弹手势也按时间线归一化。缺失调速接口、动画、时间线，或资源声明无限循环/长度不合法时回退程序动画，不强迫循环reload_upper，也不影响装填完成。

## 边界与验收

默认枪械数据可提供完整初始时长；第三方Lua脚本不一定提前公开总时长，因此初始值是估计，实际阶段用于校准。不能承诺任意脚本、任意配件下手部关键帧与装填生效点逐帧重合。速度有0～8的安全边界，异常极短换弹仍以服务器完成为准。

178项Python静态/API检查通过；本机Player Animator 1.0.2-rc1公开调速/定位接口和TaCZ 1.1.8的ReloadState阶段/倒计时已只读核对。TaczAnimationPolicyTest新增进度、半速/倍速、恢复定位及末尾等待边界用例，尚未编译或执行。没有运行构建、同步实例或启动游戏。

游戏需验证：普通步枪快/慢换弹、空仓与战术换弹、逐发霰弹枪、配件时长变化、特殊脚本、切近战后台继续再切回、重新渲染、资源重载、无Player Animator回退、多人同看及结束立即开火。

源码：TaczHumanReloadTransactions.java、TaczReloadVisualTimeline.java、HumanMobEntityData.java、TaczNpcAnimator.java、TaczReloadPlayback.java、TaczNativeAnimation.java、ClientAnimationCompatibility.java、NetworkHandler.java。
