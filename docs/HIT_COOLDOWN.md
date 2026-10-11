# 受伤冷却与人类近战命中

2026-10-11，基线e18ee5ba488d84cbf2324b80bacba5bb5b6c897d加当前工作区。源码已实现，尚未构建/投放或游戏验收。

## 两个时间不能混为一谈

攻击间隔决定士兵何时能再次挥刀；目标的受伤冷却决定已经命中的攻击是否能扣血。较快武器可能按原间隔打出第二击，但目标还在上一击的受伤冷却中，原生规则会吞掉相同或更小的伤害，较大伤害也可能只计算差额。本次只处理后一层。

## 当前规则

在服务器上，本模组人类发出的直接近战攻击绕过目标的受伤冷却判定，按这次合法命中正常结算伤害，包括对生物和玩家的攻击。来源必须是Human、直接命中实体必须就是攻击者、伤害类型必须为原生mob_attack或mob_attack_no_aggro，并排除弹射物/枪弹和爆炸。普通近战与Better Combat经过原生Mob.doHurtTarget的延迟命中均适用。

不会因此加快攻击速度、缩短动画前摇、补发攻击或增加伤害倍率。攻击范围、冷却、攻击许可、雇主/友军保护、盾牌、防护、附魔及Forge伤害取消继续经过原来流程。反伤、火焰、状态、环境伤害，以及其他生物对人类的近战，不因这条规则获得绕过能力。

原版模式开启时关闭本模组新增的冷却绕过规则。原有弹射物范围仍独立保留：人类射向非玩家的弹射物，以及人类受到的弹射物可绕过；玩家受到弹射物不因这项规则绕过。近战此次按用户要求处理人类发出的攻击，包含玩家目标。

## 统一入口与维护

HumanAttackCooldownPolicy是受伤冷却判定的统一入口，近战分支先判断上述条件，其余委托既有ProjectileCooldownPolicy。复用已经注册的LivingEntityProjectileCooldownMixin及其精确Slice，依旧只重定向LivingEntity.hurt中一次BYPASSES_COOLDOWN标签查询，不增加第二个Mixin、不修改原DamageSource，也不清空invulnerableTime/hurtTime。

因此目标共享受伤计时仍存在，不相关攻击继续按原生规则处理。没有重新调用hurt或直接写生命，没有绕开正常的关系/Forge事件/护甲/盾牌链；第三方模组若自身还有额外受伤限制，该限制仍可能生效。Mixin启动兼容仍需实际启动验收。

苦力怕冒险击杀预测同步读取这一规则：绕过时按照现有真实武器间隔预计连续命中，不再额外等待目标受伤冷却，也不强加10tick间隔；原版模式仍按旧受伤保护预算估算。只调整预测，不改变士兵实际攻击调度。

## 验证

170项Python静态/实际JAR接口检查通过，6份默认数值附录核对通过。Java测试已补直接/间接来源、原版模式、普通生物攻击、弹射物/爆炸排除、苦力怕快武器时间边界，并将新入口加入真实字节码禁止改血/清无敌帧检查；本轮未编译执行Java。

待游戏验收：相同快速武器连续两次合法命中均有伤害，攻击次数和动画节奏不增加；普通/Better Combat、玩家/怪物目标、盾挡、友军与雇主保护、原版模式、苦力怕持续攻击预测及其他模组伤害事件组合。未构建、同步、启动游戏或推送。

源码追溯：HumanAttackCooldownPolicy.java、ProjectileCooldownPolicy.java、mixin/LivingEntityProjectileCooldownMixin.java、CreeperMeleeCommitment.java、CreeperFinishPolicy.java、ProjectileCooldownPolicyTest.java。
