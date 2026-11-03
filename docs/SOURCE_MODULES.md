# 源码模块与修改地图

代码统一使用 `dev.felix.hostilehumans`。这是一份当前维护地图，不是待实施方案。源码按功能归位，资源/注册/存档命名空间仍为 `hostile_humans_unified`。

## 模块归属

| 目录（统一包根目录下） | 职责 | 生产类数量 |
|---|---|---:|
| `audio` | 声音注册 | 1 |
| `behavior` | 行为会话、权限和生命周期 | 10 |
| `behavior/combat` | 战斗中枢、策略选择与目标协调 | 25 |
| `behavior/combat/defense` | 盾牌、压力防御与推进 | 22 |
| `behavior/combat/gun` | 枪械通用接口、装填与移动执行 | 27 |
| `behavior/combat/melee` | 近战执行、双持、跳砍与攻速 | 17 |
| `behavior/combat/ranged` | 弓弩、箭矢、射界与远程武器托管 | 30 |
| `behavior/combat/recovery` | 食物、药水与恢复事务 | 9 |
| `behavior/combat/retreat` | 撤退、逃生与绝境反击 | 14 |
| `behavior/combat/throwable` | 真实投掷物事务与投掷策略 | 7 |
| `bootstrap` | 统一启动与注册 | 11 |
| `client` | 客户端会话与注册 | 2 |
| `client/render` | 实体模型、动画与第一人称呈现 | 37 |
| `client/render/tacz` | 可选TaCZ原生第一人称模型、只读骨骼采样与共用换弹片段/声音计划；不操作玩家或服务端弹药 | 19 |
| `client/ui` | 共用界面样式及指挥/背包界面 | 10 |
| `client/ui/framework` | 统一客户端主题、原生控件树、布局、绘图与会话适配 | 24 |
| `client/ui/gun` | 可选枪械监视HUD的原生资源与命中反馈适配，不借用真实玩家物品或上游全局HUD状态 | 2 |
| `common` | 无游戏依赖的通用集合/缓存 | 6 |
| `compat` | 可选联动的安全检测和公共桥 | 7 |
| `compat/firstaid` | 对应可选模组的独立适配 | 3 |
| `compat/jeg` | 对应可选模组的独立适配 | 10 |
| `compat/maid` | 对应可选模组的独立适配 | 3 |
| `compat/scguns` | 对应可选模组的独立适配 | 17 |
| `compat/spartan` | 对应可选模组的独立适配 | 2 |
| `compat/tacz` | 对应可选模组的独立适配 | 15 |
| `config` | 公共配置读写与默认值 | 6 |
| `cultivation` | 服务器培养候选、周期、账本、训练权限和协议 | 22 |
| `cultivation/client` | 培养客户端表单、席位与13维候选比较 | 2 |
| `damage` | 统一伤害流程与关系过滤 | 8 |
| `economy` | 币值、钱包与原子交易、通行及任务经济接入 | 27 |
| `economy/client` | 钱包、整批报价、通行与基础任务的客户端操作页 | 2 |
| `entity` | 人类实体、数据与同步 | 19 |
| `equipment` | 出生装备、装备池及补给配置 | 31 |
| `faction` | 阵营身份、关系、声望与数据定义 | 25 |
| `faction/ability` | 可复用阵营能力 | 3 |
| `faction/visual` | 身份绑带外观数据、同步与呈现 | 7 |
| `inventory` | 真实背包、交换、拾取与装备保护 | 21 |
| `item` | 通用物品规则 | 1 |
| `mixin` | 原版/可选模组注入接入；只转交所属功能模块 | 86 |
| `monitor` | 监控会话、接管与声音接收 | 17 |
| `monitor/client` | 监控HUD、输入与摄像机 | 16 |
| `monitor/combat` | 接管战斗意图、武器执行与输入寿命 | 7 |
| `monitor/remote` | 远程监控视点、区块与实体追踪票据生命周期 | 1 |
| `navigation` | 搜索、路径跟随、安全、移动与导航预算 | 52 |
| `network` | 共用实体数据网络 | 3 |
| `observer` | 诊断快照、观察名单与网络 | 17 |
| `observer/client` | 观察详情、图层与属性界面 | 3 |
| `perception` | 视觉、声音、识别与目标记忆 | 39 |
| `performance` | 共享配额与活动分档 | 7 |
| `progression` | 成长、专精、经验与属性 | 27 |
| `settlement` | 据点区域几何、模板变换与维度实例索引 | 26 |
| `settlement/client` | 据点结构作者表单、管理与岗位指派 | 4 |
| `soldier` | 雇佣、小队、命令、召回和对白 | 57 |
| `world` | 自然出生和生成实体 | 3 |

## 修改时的联动检查

| 修改内容 | 首先修改 | 一起核查 |
|---|---|---|
| 接管/HUD | monitor、monitor/client | entity/Human、perception、client/render、网络协议与清理 |
| 背包与副手 | inventory | equipment、恢复/武器托管、client/ui、真实物品事务 |
| 战斗方式 | behavior/combat 下对应执行模块 | behavior 的动作权限、中枢、导航和可选武器适配 |
| 视觉/声音 | perception | 目标选择、共享信息、observer、接管的只读感知 |
| 属性与成长 | progression | config、equipment、伤害、属性同步与observer界面 |
| 阵营技能 | faction/ability | faction定义、触发模块、伤害生命周期；策略与技能仍分离 |
| 任意类移动/改名 | 所属模块 | 导入、Mixin配置/类名字符串、反射、JavaExec、源码测试索引、知识库 |

## 模块接口与约束

- 模块内部实现优先包内可见；只有真实跨模块调用所需的类型、方法和快照公开。`private` 状态不会因目录迁移直接公开。
- `Human` 仍是实体接入点，`HumanRuntime` 仍负责共用事件汇聚；不宣称通过目录迁移已经消除了所有跨模块依赖。
- 模块调用使用统一规则入口，禁止客户端重算服务器权威行为。可选依赖继续经compat适配，缺席时正常回退。
- Mixin是接入胶水，不在其中重写另一套战斗、背包或感知规则。源码与资源通过完整类名关联，改名必须检查注册配置。
- 新功能先在模块内建立状态/生命周期/取消收尾；需要公共能力时再形成小接口，不创建无职责的转发层。
- 生产与Java测试的目录必须匹配package；Python源码检查按唯一类名定位，结构检查独立验证实际路径，不保留旧包别名。

新增或移动类后，运行 `python tools/update_source_modules.py` 更新清单、类索引和测试查找表；它只读取源码，不编译。新模块先登记清晰的职责。

现有实体桥接字段维持原有调用语义，仍是内部过渡接入面。此轮不重新设计全部状态归属；后续新增状态应经模块接口管理，不扩大直接共享的可变字段。

## 文件清单

### audio

`ModSoundEvents`

### behavior

`ActionCoordinator`、`ActionProgressSnapshot`、`BehaviorDebugSnapshot`、`ConditionalGoal`、`HumanBehaviorControl`、`HumanGoalExecutionBridge`、`HumanMovementInputAuthority`、`InputExecutionLease`、`StaticCombatGoalHost`、`TargetPreservingConditionalGoal`

### behavior.combat

`AdaptiveCombatGoal`、`CloseDefenseHold`、`CloseRangedTactics`、`CombatAiConfig`、`CombatPreference`、`CombatStrategySet`、`CombatWeaponPolicy`、`CreeperFinishPolicy`、`CreeperMeleeCommitment`、`CreeperSafetyPolicy`、`FinisherPolicy`、`GuardCombatState`、`HostileHumanTargeting`、`HostileTargetingConfig`、`HumanAttackCooldownPolicy`、`HumanCombatAuthority`、`HumanCombatDirector`、`HumanFinisherSelection`、`HumanTargetCoordinator`、`HumanTargeting`、`HumanThreatAssessment`、`PeacefulHumanPolicy`、`ProjectileCooldownPolicy`、`VanillaModePolicy`、`WeaponTransitionPolicy`

### behavior.combat.defense

`CombatPressurePolicy`、`DamagePressureTracker`、`GuardedRangedCharge`、`GunMeleeProtection`、`GunnerShieldProtection`、`PressureDefenseCycle`、`PressureGuardFacing`、`PressureReactionGoal`、`PressureThreatDirections`、`ProjectileReactionGoal`、`ProjectileShieldPolicy`、`RecentDamageTracker`、`ShieldAdvancePolicy`、`ShieldDefenseController`、`ShieldDefenseMovement`、`ShieldDefensePolicy`、`ShieldDurability`、`ShieldEnchantmentRoll`、`ShieldPickupPolicy`、`ShieldPressureConfig`、`ShieldProjectileBackend`、`ShieldQualityPolicy`

### behavior.combat.gun

`CompositeGunSupport`、`FirearmCompatConfig`、`GunAimPoint`、`GunAttackAction`、`GunAttackMovementState`、`GunCombatProfile`、`GunCustody`、`GunDoctrine`、`GunDrawContinuity`、`GunFireAuthorization`、`GunHudData`、`GunMovementExecution`、`GunOcclusionApproach`、`GunPositioning`、`GunRangePolicy`、`GunReloadAuthority`、`GunReloadPolicy`、`GunReloadSession`、`GunSpawnPools`、`GunSpecialization`、`GunSpreadPolicy`、`GunSupport`、`GunTargetMotion`、`GunTrackingPolicy`、`GunnerGoal`、`HumanGunAcceptance`、`ReloadPaymentLedger`

### behavior.combat.melee

`BetterCombatMeleeCombat`、`JumpAttackConfig`、`MeleeActionControl`、`MeleeAttackGoal`、`MeleeAttackTiming`、`MeleeCombatRange`、`MeleeJumpPolicy`、`MeleePursuitProgress`、`MeleeSpacing`、`MeleeSpacingPolicy`、`NpcDualWield`、`NpcMeleeWeaponContext`、`PursuitJumpAttack`、`TridentCombatModifiers`、`TridentHybridGoal`、`TridentMovementExecution`、`VanillaMeleeSweep`

### behavior.combat.ranged

`AmmoWeaponSelection`、`ArrowAttackCadence`、`ArrowVolleyPolicy`、`BowAttack`、`BowMovementExecution`、`BowRangePolicy`、`CrossbowGoal`、`CrossbowMovementExecution`、`HumanAmmo`、`HumanAmmoItem`、`HumanAmmoPolicy`、`HumanAmmoSpawn`、`HumanArrowCombat`、`ManualArrowBallistics`、`RangedAccuracy`、`RangedCombatProfile`、`RangedEngagement`、`RangedEngagementProgress`、`RangedFiringMemory`、`RangedFiringPosition`、`RangedHybridMeleeGoal`、`RangedMeleeCycle`、`RangedMovementDecision`、`RangedShotClearance`、`RangedStrafeSpeed`、`RangedTerrainCombat`、`RangedTerrainPolicy`、`RangedWeaponCustody`、`RangedWindowCandidates`、`VolleyContinuation`

### behavior.combat.recovery

`CombatConsumableGoal`、`CombatRecoveryGoal`、`HumanRecoveryEffects`、`IdleRecoveryGoal`、`MedicalSupplies`、`OffhandFoodUse`、`PotionRecoveryPolicy`、`RecoverySelectionPolicy`、`RecoverySupplies`

### behavior.combat.retreat

`AvoidCreeperGoal`、`AvoidTNTGoal`、`LowHealthCombatConfig`、`LowHealthCombatController`、`LowHealthCombatPolicy`、`RecoveryIntentPolicy`、`RetreatEscapeProgress`、`RetreatLastStand`、`RetreatPathSafety`、`RetreatPathSafetyPolicy`、`RetreatRecoveryPolicy`、`RetreatRouteContinuity`、`RetreatShoreEscape`、`RunFromTarget`

### behavior.combat.throwable

`PotionRangedAttackGoal`、`PotionThrowing`、`TacticalThrowables`、`ThrowableBallistics`、`ThrowableEffectContext`、`ThrowableSource`、`ThrowableTransactions`

### bootstrap

`ClientUiResourceEvents`、`ContentRegistry`、`CultivationSettlementIntegration`、`EventHandler`、`EventHandlerMod`、`HostileHumansUnified`、`HumanRuntime`、`ModEntityType`、`ModItems`、`ServerSetup`、`UnifiedCreativeTab`

### client

`ClientSessionEvents`、`ClientSetup`

### client.render

`BackShieldPose`、`BetterCombatNpcAnimator`、`ClientAnimationCompatibility`、`ClientRenderer`、`DebugWorldLines`、`EmfAnimationCompat`、`HumanAnimatedItemInHandLayer`、`HumanBackpackLayer`、`HumanCameraView`、`HumanGunPresentation`、`HumanPlayerModel`、`HumanRenderer`、`HumanShieldPresentation`、`HumanSpectatorCamera`、`HumanSpectatorGunView`、`HumanSpectatorMotion`、`HumanSpectatorPresentation`、`HumanSpectatorProjection`、`HumanSpectatorView`、`JegNativePose`、`JegNpcAnimator`、`PlayerAnimatorModelBridge`、`ScgunsMachineGunPose`、`ScgunsNativePose`、`ScgunsNpcAnimator`、`ShieldBlockPose`、`SpawnerEntityRenderer`、`SpectatorArmMask`、`SpectatorAudioListener`、`SpectatorMeleePresentation`、`SpectatorMotionTrack`、`SpectatorSkinTextures`、`TaczAnimationPolicy`、`TaczNativeAnimation`、`TaczNpcAnimator`、`TaczNpcEffects`、`TaczReloadPlayback`

### client.render.tacz

`NativeAimTimeline`、`NativeAnimationWeight`、`NativeBoltPlan`、`NativeCrouchPlan`、`NativeFrameClock`、`NativeJumpSway`、`NativeMovementPlan`、`NativeRecoilCurve`、`NativeRecoilTrack`、`NativeReloadAudio`、`NativeReloadPlan`、`NativeShotTimeline`、`TaczMonitorRecoil`、`TaczNativeOptics`、`TaczNativePresentation`、`TaczOpticGraphicsState`、`TaczOpticRenderContext`、`TaczPresentationResources`、`TaczSpectatorFirstPerson`

### client.ui

`CommandPanelChrome`、`CommandPanelLayout`、`CommandPanelScreen`、`HumanInventoryScreen`、`PatrolDraftClient`、`PatrolEditorClient`、`PatrolWaitScreen`、`SoldierCommandScreen`、`SoldierRosterScreen`、`SquadCommandScreen`

### client.ui.framework

`InputValue`、`PanelButton`、`PanelCheck`、`PanelDisclosure`、`PanelEditBox`、`PanelGeometry`、`PanelGroup`、`PanelHealth`、`PanelKeyValue`、`PanelMeter`、`PanelPageSlider`、`PanelRosterRow`、`PanelScreen`、`PanelSessionView`、`PanelSlider`、`PanelText`、`PendingAction`、`RosterPageCache`、`UiColor`、`UiDraw`、`UiLayout`、`UiRect`、`UiTheme`、`UiThemes`

### client.ui.gun

`ScgunsMonitorHud`、`TaczMonitorHud`

### common

`BoundedSearchCycle`、`BoundedSelection`、`CachedReflection`、`IncrementalBestSearch`、`TickRevisionMemo`、`TickValueGate`

### compat

`DungeonMobs`、`FarmersDelight`、`NativeAimCompatibility`、`NativeMonitorGunCompatibility`、`OptionalMixinPlugin`、`SpectatorSoundContract`、`TravelersBackpack`

### compat.firstaid

`FirstAidApiBridge`、`FirstAidCompat`、`FirstAidSettings`

### compat.jeg

`JegCompat`、`JegGunIdentity`、`JegGunTypes`、`JegIntegration`、`JegMonitorInput`、`JegMonitorRecoilAdapter`、`JegMonitorWeapons`、`JegReloadPlan`、`JegSounds`、`JegThrowables`

### compat.maid

`MaidHumanRelationPolicy`、`MaidTargetRules`、`TouhouMaidCompat`

### compat.scguns

`ScgunsCompat`、`ScgunsGunIdentity`、`ScgunsIntegration`、`ScgunsMedical`、`ScgunsMedicalBridge`、`ScgunsMonitorInput`、`ScgunsMonitorRecoilAdapter`、`ScgunsMonitorWeapons`、`ScgunsRaidBridge`、`ScgunsReloadPlan`、`ScgunsReloadTiming`、`ScgunsSettings`、`ScgunsSignalItem`、`ScgunsSounds`、`ScgunsThrowableAdapter`、`ScgunsThrowableDamage`、`ScgunsThrowables`

### compat.spartan

`SpartanEquipmentCompat`、`SpartanRangedCompat`

### compat.tacz

`TaczAmmoMixinContract`、`TaczGunItemIdentity`、`TaczGunMechanics`、`TaczHearingBridge`、`TaczHumanReloadTransactions`、`TaczIntegration`、`TaczMaidIntegration`、`TaczMonitorAudio`、`TaczMonitorInput`、`TaczMonitorShotSoundClient`、`TaczMonitorSpread`、`TaczMonitorWeapons`、`TaczNpcMagazine`、`TaczReloadSoundClient`、`TaczReloadVisualTimeline`

### config

`Config`、`ConfigFieldHelp`、`CurrentConfigDefaults`、`SplitConfigFiles`、`TierConfigProjection`、`UnifiedConfig`

### cultivation

`ActiveRuntimeClock`、`CultivationCommands`、`CultivationContent`、`CultivationCycle`、`CultivationData`、`CultivationFacade`、`CultivationFacility`、`CultivationFacilityPolicy`、`CultivationNbt`、`CultivationNetwork`、`CultivationPlanner`、`CultivationProfile`、`CultivationRecord`、`CultivationRules`、`CultivationRuntime`、`CultivationService`、`CultivationSnapshot`、`CultivationSnapshotCodec`、`CultivationTrainingBlock`、`CultivationTrainingBlockEntity`、`CultivationTrainingGoal`、`TrainingCredit`

### cultivation.client

`CultivationPauseObserver`、`CultivationScreen`

### damage

`DamagePartition`、`DamagePipelineState`、`HealthLossReceipt`、`HumanDamageEvents`、`HumanDamagePipeline`、`HumanMeleeDamage`、`HumanOwnerProtection`、`HumanProjectileRelations`

### economy

`BrokerIndex`、`CuriosWalletAccess`、`Currency`、`CurrencyItem`、`EconomyBrokerBlock`、`EconomyBrokerEntity`、`EconomyContent`、`EconomyEvents`、`EconomyNetwork`、`EconomyPolicy`、`EconomyRecovery`、`EconomyService`、`EconomyTables`、`EconomyTaskBoardBlock`、`EconomyTasks`、`EconomyTraderBlock`、`EconomyView`、`InventoryTransaction`、`MoneyMath`、`MoneyRate`、`ParleyFlagItem`、`TaskLedger`、`WalletBalance`、`WalletCodec`、`WalletInventory`、`WalletItem`、`WalletPayloadCodec`

### economy.client

`EconomyScreen`、`WalletClientKeys`

### entity

`AggressionMode`、`FactionBossHuman`、`Human`、`HumanAbility`、`HumanData`、`HumanEntity`、`HumanFood`、`HumanHelper`、`HumanManagerEventHandler`、`HumanMobEntityData`、`HumanServerData`、`HumanServerDataCleanup`、`HumanTier`、`HumanUtil`、`HumansClientData`、`HumansDataSync`、`HumansServerDataClientSync`、`PotionRangedAttackMob`、`TierThreeHuman`

### equipment

`ArmorSwapConfig`、`ArmorSwapPolicy`、`ConfiguredEquipmentLoadout`、`ConfiguredHumanEquipment`、`EquipmentArchetypes`、`EquipmentBreakSounds`、`EquipmentCondition`、`EquipmentConfigMigration`、`EquipmentGrowthPolicy`、`EquipmentIntegrationDefaults`、`EquipmentWearCooldown`、`EquipmentWearLimit`、`FactionEquipmentConfig`、`FactionEquipmentEnchantments`、`FactionEquipmentGuns`、`FactionEquipmentPolicy`、`FactionEquipmentPools`、`FirearmSpawnEquipment`、`FoodInventoryValue`、`GuaranteedShieldLoadout`、`HumanSpawnEquipment`、`NaturalEquipmentGrowth`、`NaturalHumanSpawnRules`、`SpawnFoodConfig`、`SpawnMeleeReservePolicy`、`SpawnPotionConfig`、`SpawnSafety`、`SpawnSupplyMigration`、`SpawnTotemSupply`、`SpawnWeaponCountPolicy`、`TacticalSupplyDefaults`

### faction

`BadgePolicy`、`EncounterCooldown`、`FactionAuthoringCommands`、`FactionContent`、`FactionDefinitions`、`FactionEconomyLedger`、`FactionPassage`、`FactionRecruitment`、`FactionRules`、`FactionStandingData`、`HumanFactions`、`HumanRelations`、`HumanRelationshipPolicy`、`IdentityBadgeItem`、`IdentityBandItem`、`IdentityBands`、`PassageCodec`、`PassagePermit`、`StandingAccess`、`StandingCommands`、`StandingEffects`、`StandingIncidents`、`StandingPolicy`、`StandingQuota`、`StandingReward`

### faction.ability

`DodgeAbility`、`FactionAbilities`、`FactionAbility`

### faction.visual

`CuriosBandAccess`、`FactionBandAppearance`、`FactionBandFit`、`FactionVisualClient`、`FactionVisualCodec`、`FactionVisualLayer`、`FactionVisualNetwork`

### inventory

`CombatResupply`、`CombatResupplyConfig`、`ControlledEquipmentPolicy`、`HumanEquipmentDrops`、`HumanEquipmentExchange`、`HumanInventoryContainer`、`HumanInventoryCustody`、`HumanInventoryMenu`、`HumanInventorySession`、`HumanLootManager`、`HumanManagedLoadout`、`InventoryTotemProtection`、`LootRetryPolicy`、`PickUpLoot`、`PickupCombatWindow`、`PickupRouteProgress`、`PileFirstSelection`、`SoldierPickupPolicy`、`SupplyInventoryPolicy`、`ValuableItemPickupGoal`、`WildArmorWear`

### item

`VanillaPotionStacks`

### mixin

`AbstractArrowBallisticsMixin`、`BeardifierMix`、`BlockMixin`、`ChunkGeneratorMix`、`DamageSourceMix`、`DoorMixin`、`HumanGoalSelectorExecutionMixin`、`HumanMonitorAiStepMixin`、`HumanMonitorInputMixin`、`HumanMonitorMouseMixin`、`HumanMonitorSoundBroadcastMixin`、`HumanProjectileCollisionMixin`、`HumanRecoveryEffectMixin`、`HumanShieldItemPropertyMixin`、`HumanSpectatorBobMixin`、`HumanSpectatorCameraMixin`、`HumanSpectatorProjectionAccess`、`HumanTotemRecoveryMixin`、`HumanWrappedGoalExecutionMixin`、`ItemStackWildArmorWearMixin`、`JegHumanAiOwnershipMixin`、`JegHumanDamageMixin`、`JegMonitorAimingInputMixin`、`JegMonitorReloadInputMixin`、`JegMonitorShootingInputMixin`、`JegMonitorShotMixin`、`JegNativeGunGoalMixin`、`JegNpcMuzzleFlashMixin`、`JegProjectileCollisionMixin`、`JegSplashDamageAttributionMixin`、`JegWaterAreaCollisionMixin`、`JegWaterBombOwnerMixin`、`JegWaterThrowOriginMixin`、`LivingEntityDamagePipelineMixin`、`LivingEntityGrowthDamageMixin`、`LivingEntityProjectileCooldownMixin`、`MonitorPredictionConstructionMixin`、`MonitorPredictionEntityAccess`、`MonitorRemoteChunkMapMixin`、`MonitorRemoteClientOptionsMixin`、`MonitorRemoteEntityTrackingMixin`、`MonsterMobsMixin`、`PillagersGunAmmoMixin`、`PlayerDamagePipelineMixin`、`PolymorphConcurrencyMixin`、`ScgunsChokeThrowOriginMixin`、`ScgunsChokeVictimFilterMixin`、`ScgunsCloudVictimFilterMixin`、`ScgunsGasThrowOriginMixin`、`ScgunsHumanAimMixin`、`ScgunsHumanDifficultyMixin`、`ScgunsHumanShieldMixin`、`ScgunsMonitorAimingInputMixin`、`ScgunsMonitorHudMixin`、`ScgunsMonitorReloadInputMixin`、`ScgunsMonitorShootingInputMixin`、`ScgunsMonitorShotMixin`、`ScgunsNpcMuzzleFlashMixin`、`ScgunsSulfurheadDamageMixin`、`ScgunsSwarmThrowOriginMixin`、`SettlementStructureStartMixin`、`SettlementTemplatePlacementMixin`、`SpartanWeaponryItemRandomizerMixin`、`SpectatorGunSoundDistanceMixin`、`SpectatorTaczAnimationSoundDistanceMixin`、`TaczHumanGunScriptMixin`、`TaczHumanProjectileCollisionMixin`、`TaczHumanReloadMixin`、`TaczHumanShootMixin`、`TaczMonitorAimInputMixin`、`TaczMonitorReloadInputMixin`、`TaczMonitorShootInputMixin`、`TaczMonitorShotSoundMixin`、`TaczMonitorSoundBroadcastMixin`、`TaczNpcAnimatorMixin`、`TaczNpcMuzzleFlashMixin`、`TaczScopeAimMixin`、`TaczScopeAttachmentIndexMixin`、`TaczScopeCameraMixin`、`TaczScopeRenderCompatibilityMixin`、`ThrowableAreaEffectCloudMixin`、`ThrowableSplashPotionMixin`、`ThrownTridentLifecycleMixin`、`TouhouMaidGunHurtMixin`、`TouhouMaidHumanTargetMixin`、`VanillaPotionStackSizeMixin`

### monitor

`HumanMonitorAudio`、`HumanMonitorControl`、`HumanMonitorItem`、`HumanMonitorNetwork`、`HumanMonitorService`、`LatestTickInput`、`MonitorAccessPolicy`、`MonitorInputHistory`、`MonitorLoadoutSnapshot`、`MonitorMotionSnapshot`、`MonitorPhysicsSnapshot`、`MonitorRecoilNetwork`、`MonitorRecoilSnapshot`、`MonitorSequencedInput`、`MonitorTargetBinding`、`MonitorTravelSpeed`、`MonitorWeaponSnapshot`

### monitor.client

`HumanMonitorClient`、`HumanMonitorScreen`、`MonitorActionButton`、`MonitorGunHud`、`MonitorGunInput`、`MonitorGunRecoil`、`MonitorHitFeedback`、`MonitorHudLayout`、`MonitorIconFit`、`MonitorMovementPrediction`、`MonitorPhysicalMotion`、`MonitorPredictionBody`、`MonitorPredictionEnvironment`、`MonitorPredictionIntent`、`MonitorPresentationTrack`、`NativeNpcRecoilTrack`

### monitor.combat

`MonitorArrowActions`、`MonitorCombatInputGate`、`MonitorCombatSession`、`MonitorGunActions`、`MonitorGunBackends`、`MonitorGunTrigger`、`MonitorLoadout`

### monitor.remote

`MonitorRemoteView`

### navigation

`BasicNavigationBudget`、`CombatTerrainNavigation`、`CombatTerrainPolicy`、`DoorInteractHumanGoal`、`FenceInteractGoal`、`GarrisonReturnNavigation`、`GuardReturnProgress`、`HumanFallPolicy`、`HumanFloatGoal`、`HumanMovementModifiers`、`HumanNavigation`、`HumanNavigationOwnership`、`HumanPathFollower`、`HumanSafeJumpControl`、`HumanTerrainSafety`、`HumanWaterNavigation`、`InteractionSafetyPolicy`、`LadderClimbGoal`、`LavaEscapeGoal`、`LeaveWaterWhenIdleGoal`、`LocalPathFollowPolicy`、`LongRangeNavigation`、`MovementContinuity`、`MovementContinuityProgress`、`MovementSpeedController`、`MovementSpeedLimitPolicy`、`NavigationSearchConfig`、`NavigationSupport`、`OpenDoorsGoal`、`OpenFenceGoal`、`OpenTrapdoorGoal`、`OptionalPathBudget`、`PassageLease`、`PassedWaypointPolicy`、`PlayerLikeMovementGoal`、`PursuitNavigation`、`PursuitRefreshPolicy`、`RandomStrollGoalFar`、`RandomStrollGoalWithHome`、`RouteExecutionLease`、`RouteProgressPolicy`、`RouteSearchPolicy`、`SafePositions`、`ShoreProgressTracker`、`ShoreSeekingPolicy`、`SoftSpeedLimit`、`SoldierFollowNavigation`、`SoldierStuckRecovery`、`SprintHopPolicy`、`TrapdoorInteractGoal`、`WaterCombatNavigation`、`WaterCombatPolicy`

### network

`MessageHumanData`、`MessageHumansData`、`NetworkHandler`

### observer

`AttributeDetailClient`、`AttributeDetailNetwork`、`AttributeDetailPolicy`、`AttributeDetailSnapshot`、`AttributeObserverItem`、`AttributeReference`、`NavigationDebugFrame`、`NavigationDebugFrameCodec`、`NavigationDebugNetwork`、`NavigationDebugService`、`NavigationDebugSnapshot`、`NavigationDebugViewPolicy`、`NavigationObserverHelmet`、`NavigationObserverPreferences`、`NavigationObserverSelection`、`NavigationObserverState`、`NavigationTestItem`

### observer.client

`AttributeDetailScreen`、`NavigationDebugClient`、`NavigationLegendScreen`

### perception

`AttentionFacingPolicy`、`BeaconReportPolicy`、`BeaconReportingService`、`BodyVisibilitySamples`、`CachedOcclusionProof`、`HearingRelevance`、`HumanAttention`、`HumanAwareness`、`HumanLookAtPlayerGoal`、`HumanLootVision`、`HumanPerception`、`HumanReportPropagation`、`HumanSensoryEvents`、`HumanTargetSharing`、`HumanTurnControl`、`IdleSurvey`、`IncomingHitResponseGoal`、`InvestigateSoundGoal`、`NearVisionPolicy`、`NearestAttackableTargetGoalCustom`、`NearestAttackableTargetGoalWithHumanLimiter`、`NegativeObservationSweep`、`PerceptionBudget`、`PerceptionConfig`、`PerceptionCoordinationPolicy`、`PerceptionDebugCodec`、`PerceptionDebugSnapshot`、`PerceptionDiscoveryPolicy`、`PerceptionInvestigationGoal`、`PerceptionPolicy`、`PerceptionTargetGoal`、`RecognitionEvidence`、`SoundExclusionMemory`、`SoundInvestigationMemory`、`TargetReportLedger`、`ThreatScorePolicy`、`TurnGesture`、`TurnResponse`、`VisualOcclusionMemory`

### performance

`ActivityBudgetConfig`、`ActivityLoadPolicy`、`AdaptiveActivityBudget`、`BudgetedSearch`、`BudgetedUpdates`、`FairWorkBudget`、`ScopedWorkBudget`

### progression

`DefenseAttributePolicy`、`DefenseAttributes`、`EffectiveAttributes`、`GrowthCombat`、`GrowthConfig`、`GrowthHealth`、`GrowthProfileFiles`、`GrowthProgression`、`GrowthRewards`、`GrowthSnapshot`、`HumanDamageScaling`、`HumanGrowth`、`MaxHealthCompatibilityPolicy`、`NaturalSpawnProgression`、`PlayerProgressionData`、`ProgressionClock`、`ProgressionCommands`、`SpecialtyAllocation`、`SpecialtyAttributes`、`SpecialtyBounds`、`SpecialtyBudgetSampler`、`SpecialtyConfig`、`SpecialtyData`、`SpecialtyLabels`、`SpecialtyPolicy`、`SpecialtyTrainingBridge`、`TierAttributes`

### settlement

`RegionBox`、`SettlementAccess`、`SettlementAuthoring`、`SettlementCodec`、`SettlementCommands`、`SettlementContent`、`SettlementControlBlock`、`SettlementFacility`、`SettlementGeneration`、`SettlementIndex`、`SettlementInformationBlock`、`SettlementInformationBlockEntity`、`SettlementInstance`、`SettlementLimits`、`SettlementModule`、`SettlementNetwork`、`SettlementPages`、`SettlementPlacementIdentity`、`SettlementPosts`、`SettlementPreview`、`SettlementRegion`、`SettlementSelectionTool`、`SettlementTemplate`、`SettlementTemplateFiles`、`SettlementWandPlacement`、`TemplateTransform`

### settlement.client

`SettlementGhostRenderer`、`SettlementPreviewClient`、`SettlementScreen`、`SettlementWandScreen`

### soldier

`BodyguardMovementGoal`、`BodyguardPolicy`、`BodyguardProtection`、`CommandHealthCodec`、`CommandHealthSnapshot`、`CommandRateLimit`、`CommandView`、`CreativeSoldierControllerItem`、`GarrisonSettings`、`HiredHumanAmmoWarning`、`HiredHumanHealthWarning`、`HiredHumanRecall`、`HostileBeaconFoodPolicy`、`HumanCommandNetwork`、`HumanSignalItem`、`NaturalRoute`、`NaturalState`、`NaturalStateCommand`、`NaturalStateToolItem`、`OfflineSoldierDeathReports`、`OrderConfiguration`、`OrderDistances`、`OwnerIndex`、`OwnerOfflinePolicy`、`PatrolCommandBatonItem`、`PatrolControlService`、`PatrolRouteEditor`、`PatrolRoutePolicy`、`PatrolRoutes`、`PatrolToolNetwork`、`RecruitmentConfigClient`、`RecruitmentConfigNetwork`、`RecruitmentContractItem`、`RecruitmentLedger`、`RecruitmentPayment`、`RecruitmentPolicy`、`RecruitmentTermsCache`、`SignalPolicy`、`SoldierChatBudget`、`SoldierCombatMemory`、`SoldierCombatMode`、`SoldierCommandFeedback`、`SoldierDialogue`、`SoldierMessageDispatcher`、`SoldierOrder`、`SoldierOrderGoal`、`SoldierRecallAnchor`、`SoldierRecallCharmItem`、`SoldierRecallProtection`、`SoldierRosterAudit`、`SoldierRosterItem`、`SoldierRosterNetwork`、`SquadCommandNetwork`、`SquadCommandTokenItem`、`SquadCommands`、`SquadData`、`TemporarySupportFollowGoal`

### world

`FactionSpawnEggItem`、`SpawnHandler`、`SpawnerEntity`

