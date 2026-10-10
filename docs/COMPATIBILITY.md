# 可选模组兼容维护

范围为 Minecraft 1.20.1 Forge 对应版本线。公共 API 优先；Java 的 public 可见性不等于上游承诺稳定。以下整理减少普通重构的影响，不承诺任意未来版本均兼容。

## 接入边界

| 功能 | 维护入口 | 更新时检查 |
| --- | --- | --- |
| TaCZ 弹药／换弹 | `TaczAmmoMixinContract` 与三个弹药 Mixin | 方法描述符、调用数量、ShooterDataHolder 字段、交易语义 |
| TaCZ／Better Combat／Player Animator 客户端 | `ClientAnimationCompatibility` | 公共与非公开成员、动画数据、模型及护甲桥接；按功能／操作记录一次诊断 |
| 女仆身份牌与阵营 | `TouhouMaidCompat`、`MaidTargetRules` | 任务公共接口和显式排除规则；规则读取不兼容时拒绝攻击人类并报错 |
| 女仆爆炸、斯巴达异步随机数补丁 | `OptionalMixinPlugin` | 只在精确方法签名存在时启用；方法改变时明确警告并保留上游行为 |
| TaCZ 友军弹丸过滤 | `TaczHumanProjectileCollisionMixin` | 两条射线查询的调用结构；仍严格要求两个注入 |
| First Aid 医疗／战术用品 | `FirstAidCompat`、`FirstAidApiBridge` | 公开ItemHealing敷用毫秒值、AbstractPartHealer分次与间隔；Forge Start/Finish消费语义；不接入玩家身体模型 |

First Aid对照本机`1.20.1-1.1`只读JAR实现。可选依赖声明及ModList门控隔离API类，核心代码不暴露First Aid类型；使用公开API，不读内部配置字段、不用反射或Mixin。Forge Finish收到原物品快照与原生结果，敷料补扣一件，吗啡保留原生扣除，使用中断不结算。原生默认敷料敷用3000毫秒、每22秒一次共2次，绷带2500毫秒、每18秒一次共4次；人类每次分别恢复10点，共20／40点。上游服务器配置调整后，新疗程读取公开接口的新节奏，已开始疗程按保存快照完成；满血时该次治疗不溢出、不提前清冷却，卸载期间暂停剩余加载tick。没有新增持续扫描或竞争Goal。

构建新增只读compileOnly参数`-PfirstAidApiJar=<First Aid JAR完整路径>`（与路径作为一个参数正确引用）；工作树必须显式指定，未安装First Aid的运行环境不需要该JAR。默认路径指向约定测试实例，具体命令见AGENTS.md；不得将依赖JAR提交或打入产物。

TaCZ 换弹按方法描述符及精确调用匹配，不再依赖编译器合成的 lambda 方法名字。允许正文重命名或内联到 reload；必需调用仍要求恰好一次，避免静默跳过造成免费换弹、提前通知或重复扣费。上游方法参数、字段或语义改变仍需要适配。

TaCZ 非公开动画资产读取和持枪显示数据暂时保留：现有公开播放接口要求玩家类型，不能等价处理独立 NPC 动画栈；猜测枪包资源 ID 也不能正确处理自定义／默认显示资源。它们集中在客户端边界中，失败时记录原因并沿用已有回退行为。Player Animator 的模型变形实现接口仍属内部依赖。未增加第二套攻击或动画调度。

女仆 `IAttackTask.canAttack` 同时执行默认生物分类，不能直接代替身份牌判断；否则中立 Human 类型会在身份判断前被拒绝。其命名排除、配置忽略名单和用户设定 FRIENDLY 规则集中在 `MaidTargetRules`，身份牌及有效控制权继续由本模组关系策略决定。

`OptionalMixinPlugin` 仅用于非核心上游修补，通过 Mixin 字节码服务检查方法，不提前初始化可选模组。精确签名通过后，注入本身严格要求一次；没有使用全局 require=0。读取字节码错误明确失败，不能冒充依赖不存在。若女仆爆炸补丁被跳过，其原生枪械爆炸免伤行为可能恢复；若斯巴达补丁被跳过，异步随机数保护不再由本模组提供。应根据启动警告复核上游是否已有修复。

## 暂停的旧接入

| 模组 | 暂停内容 |
| --- | --- |
| Traveler's Backpack | 出生自动装备及专用渲染层 |
| Dungeons Mobs | 信标随机召唤红石傀儡替换 |
| Villager Names | 安装检测触发本模组本地名字列表的自动命名 |
| Quark／mctb、Waystones | 自动启用结构覆盖资源包 |
| Polymorph、Pillagers Gun | 对其内部实现的 Mixin 修补 |

旧源文件／结构资源保留待后续审查，但运行入口不再启用。不删除旧存档实体、物品、名字、缓存或配置；旧 `no_waystones` 设置继续可读取，但停用期间没有覆盖包可启用。Farmer's Delight 的结构覆盖仍独立启用，普通通用物品配置仍可填写玩家安装模组的有效 ID。

## 验证边界

接口整理期间只执行静态源码／JSON／差异检查和依赖 JAR 的 javap 核对。新增 TaCZ 合约测试接入 check，但未授权构建前不编译或执行。

构建后仍需验证：无可选前置启动；TaCZ 普通／逐发／脚本换弹与费用、取消和通知；有无 Player Animator 的动画、护甲与换弹音轨；女仆身份牌／显式排除与爆炸；友军及 PVP 两类弹丸边界；旧覆盖包不启用且农夫乐事仍可用。新上游版本应在相应版本环境验证后登记，静态签名匹配不能证明语义兼容。
