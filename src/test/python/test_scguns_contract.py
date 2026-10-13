"""Optional integration boundaries and installed-JAR public API contracts; no game/compilation."""
import json
import os
import struct
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/club/someoneice/humangunner"

def source(name):
    return (PKG / (name + ".java")).read_text(encoding="utf-8")

def public_methods(blob):
    """Read method descriptors without loading any optional class/dependency."""
    cursor = 8
    def u1():
        nonlocal cursor
        value = blob[cursor]; cursor += 1
        return value
    def u2():
        nonlocal cursor
        value = struct.unpack_from(">H", blob, cursor)[0]; cursor += 2
        return value
    def skip(n):
        nonlocal cursor
        cursor += n
    cp = [None] * u2()
    index = 1
    while index < len(cp):
        tag = u1()
        if tag == 1:
            length = u2(); cp[index] = blob[cursor:cursor+length].decode("utf-8", errors="replace"); skip(length)
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18): skip(4)
        elif tag in (5, 6): skip(8); index += 1
        elif tag in (7, 8, 16, 19, 20): skip(2)
        elif tag == 15: skip(3)
        else: raise AssertionError(f"Unknown constant pool tag {tag}")
        index += 1
    skip(6); skip(2 * u2())
    def attributes():
        nonlocal cursor
        for _ in range(u2()):
            skip(2); size = struct.unpack_from(">I", blob, cursor)[0]; skip(4 + size)
    for _ in range(u2()): skip(6); attributes()
    methods = set()
    for _ in range(u2()):
        flags, name, descriptor = u2(), u2(), u2()
        if flags & 1: methods.add((cp[name], cp[descriptor]))
        attributes()
    return methods

class ScgunsBoundary(unittest.TestCase):
    def test_sounds_and_reload_use_native_boundaries(self):
        integration = source("ScgunsIntegration")
        audio = source("ScgunsSounds")
        timing = source("ScgunsReloadTiming")
        self.assertIn("ScgunsSounds.fire(human, gun)", integration)
        self.assertNotIn("playSound(", integration)
        self.assertIn("new S2CMessageGunSound", audio)
        self.assertIn("human.getId(), false, reload", audio)
        self.assertIn("Math.sqrt(Math.min(1, original) * .5)", audio)
        self.assertIn("sendToNearbyPlayers", audio)
        self.assertIn("Config.SERVER.reloadMaxDistance.get()", audio)
        self.assertIn("GunEnchantmentHelper.getRealReloadSpeed(stack)", timing)
        self.assertIn("GunEnchantmentHelper.getMagReloadSpeed(stack)", timing)
        self.assertNotIn("net.minecraft.client", timing + audio)
        self.assertNotIn("FakePlayer", timing + integration)
        self.assertIn('state.put("steps", steps)', integration)
        self.assertLess(integration.index("ScgunsReloadTiming.plan("), integration.index("HumanAmmo.consumeMagazines(human, stack)"))

    def test_sulfurhead_restores_only_human_damage_pipeline(self):
        mixin = source("mixin/ScgunsSulfurheadDamageMixin")
        self.assertIn("@Pseudo", mixin)
        self.assertIn("source.getEntity() instanceof Human", mixin)
        self.assertIn("!level().isClientSide", mixin)
        self.assertIn("super.hurt(source, amount)", mixin)
        self.assertNotIn("setHealth", mixin)
        self.assertNotIn("getOwner", mixin)
        plugin = source("compat/OptionalMixinPlugin")
        self.assertIn("SCGUNS_SULFURHEAD_DAMAGE", plugin)
        self.assertIn('equals(target.superName)', plugin)
        config = json.loads((ROOT / "src/main/resources/hostile_humans_unified.mixins.json").read_text(encoding="utf-8"))
        self.assertIn("ScgunsSulfurheadDamageMixin", config["mixins"])
        jar = os.environ.get("SCGUNS_API_JAR")
        if not jar:
            self.skipTest("SCGUNS_API_JAR not configured")
        with zipfile.ZipFile(jar) as archive:
            methods = public_methods(archive.read("top/ribs/scguns/entity/monster/SulfurheadEntity.class"))
        self.assertIn(("m_6469_", "(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), methods)

    def test_client_grips_shields_and_flash_share_one_weapon_transform(self):
        client = ROOT / "src/main/java/com/craftix/hostile_humans/client/renderer"
        pose = (client / "ScgunsNativePose.java").read_text(encoding="utf-8")
        facade = (client / "ScgunsNpcAnimator.java").read_text(encoding="utf-8")
        layer = (client / "HumanAnimatedItemInHandLayer.java").read_text(encoding="utf-8")
        self.assertIn("getGeneral().getGripType(stack)", pose)
        self.assertIn("GripType.DUAL_WIELD", pose)
        self.assertIn("aim(support, model.head, 0)", pose)
        self.assertIn("renderer.renderWeapon(human, stack, context", pose)
        self.assertNotIn("getInstance().player", pose)
        self.assertNotIn("top.ribs.scguns", facade)
        self.assertIn("nativePoses.last().pose().set(poses.last().pose())", facade)
        self.assertIn("DualWieldShotTracker.get().clearShotCount(human.getId())", pose)
        self.assertIn("LAST_FLASH.get(human) != token", pose)
        self.assertLess(layer.index("renderBackShield(human"), layer.index("translateToHand(visualArm"))
        self.assertIn("getParentModel().body.translateAndRotate(poses)", layer)
        self.assertIn("? 25 : -25", layer)
        placement = (ROOT / "src/main/java/com/craftix/hostile_humans/client/renderer/BackShieldPose.java").read_text(encoding="utf-8")
        self.assertIn("BackShieldPose.resolve(human, item)", layer)
        self.assertIn("if (!profile.offhand())", layer)
        self.assertIn(".isCustomRenderer()", placement)
        self.assertIn("SimpleJsonResourceReloadListener", placement)
        self.assertIn("ItemDisplayContext.THIRD_PERSON_LEFT_HAND", placement)
        self.assertNotIn("spartanshields", placement)
        self.assertNotIn("Items.SHIELD", placement)
        self.assertNotIn("com.oblivioussp", placement)
        shield_branch = layer.split("if (arm != human.getMainArm()", 1)[1].split("getParentModel().translateToHand", 1)[0]
        self.assertIn("GunSupport.isGunItem", shield_branch)
        self.assertNotIn("Scguns", shield_branch)
        self.assertIn("rotateShoulder(support, model.head.yRot)", pose)
        model = (client / "HumanPlayerModel.java").read_text(encoding="utf-8")
        self.assertIn("- bodyYaw", model)
        self.assertIn("relativeAim - Mth.clamp(relativeAim, -25.0F, 25.0F)", model)
        self.assertNotIn("setItemSlot", layer)
        self.assertNotIn("setItemInHand", layer)
        mixin = (PKG / "mixin/ScgunsNpcMuzzleFlashMixin.java").read_text(encoding="utf-8")
        self.assertIn("event.getEntity() instanceof Human human", mixin)
        self.assertIn("consumeAttachedRender(human)", mixin)
        config = json.loads((ROOT / "src/main/resources/hostile_humans_unified.mixins.json").read_text(encoding="utf-8"))
        self.assertIn("ScgunsNpcMuzzleFlashMixin", config["client"])
        self.assertNotIn("ScgunsNpcMuzzleFlashMixin", config["mixins"])

    def test_throwables_share_tactical_counts_and_all_native_hand_items_are_allowed(self):
        config = json.loads((ROOT / "src/main/resources/defaults/hostile_humans_unified.json").read_text(encoding="utf-8"))
        allowed = config["scguns"]["throwables"]["allowed_items"]
        self.assertEqual(len(allowed), 14)
        self.assertIn("scguns:shotball", allowed)
        for rank, counts in zip(("roamer", "tier1", "tier2", "tier3"), ((1,2), (2,4), (4,7), (6,8))):
            tactical = config["tiers"][rank]["potions"]["tactical"]
            self.assertEqual((tactical["count_min"], tactical["count_max"]), counts)
            tools = {row["item"]: row["weight"] for row in tactical["pool"] if "potion" not in row}
            self.assertEqual(tools, {"scguns:grenade":3, "scguns:nail_bomb":3, "scguns:gas_grenade":3, "scguns:choke_bomb":3, "scguns:swarm_bomb":3, "jeg:grenade":3, "jeg:water_bomb":3})
            self.assertTrue(set(tools).issubset(allowed))
            self.assertEqual(sum("potion" in row for row in tactical["pool"]), 6)
        native = source("ScgunsThrowableAdapter")
        planner = source("ScgunsThrowables")
        self.assertIn("new top.ribs.scguns.entity.throwable.ThrowableShotballEntity(human.level(), human)", native)
        self.assertIn("new FlightProfile((double) .04F, false, fuse)", native)
        self.assertIn("new FlightProfile((double) .025F, true, fuse)", native)
        self.assertNotIn("scguns:stun_grenade", allowed)
        self.assertEqual(config["scguns"]["throwables"]["initial_speed"], 2.0)
        self.assertIn("!safeArea(human, plan.impact(), safety)", planner)
        self.assertLess(planner.index("human.level().addFreshEntity(projectile)"), planner.index("remainder.shrink(1)"))
        if os.environ.get("SCGUNS_API_JAR"):
            with zipfile.ZipFile(os.environ["SCGUNS_API_JAR"]) as jar:
                registrations = jar.read("top/ribs/scguns/init/ModItems.class")
                for item in allowed:
                    self.assertIn(item.split(":")[1].upper().encode(), registrations)

    def test_human_damage_replaces_both_difficulty_layers(self):
        adapter = source("ScgunsIntegration")
        join = adapter.split("private static void onProjectileJoin", 1)[1].split("private static void onProjectileHit", 1)[0]
        self.assertIn("event.getLevel().isClientSide", join)
        self.assertIn("projectile.getShooter() instanceof Human", join)
        self.assertIn('contains("AIDamageScale")', join)
        self.assertIn("projectile.setAdditionalDamage(Gun.getAdditionalDamage(weapon))", join)
        self.assertIn('remove("AIDamageScale")', join)
        self.assertNotIn("/", join.split("static boolean isHumanBullet", 1)[0])
        hook = source("mixin/ScgunsHumanDifficultyMixin")
        self.assertIn('method = "m_7986_()Z"', hook)
        self.assertIn("ScgunsCompat.usesIndependentDamage", hook)
        self.assertNotIn("top.ribs", hook)
        config = json.loads((ROOT / "src/main/resources/defaults/hostile_humans_unified.json").read_text(encoding="utf-8"))
        self.assertEqual(config["scguns"]["human_gun_damage_multiplier"], .7)
        self.assertNotIn("npc_damage_multiplier", config["scguns"])
        self.assertIn('removeOptions(object(data, "scguns"), List.of("npc_damage_multiplier"))', source("TierConfigProjection"))
        self.assertIn('!scguns.has("human_gun_damage_multiplier")', source("SplitConfigFiles"))

    def test_independent_rank_damage_keeps_shared_training(self):
        config = json.loads((ROOT / "src/main/resources/defaults/hostile_humans_unified.json").read_text(encoding="utf-8"))
        for rank in ("roamer", "tier1", "tier2", "tier3"):
            self.assertEqual(config["tiers"][rank]["scguns"]["damage_multiplier"], 1)
            self.assertEqual(config["tacz"]["tier_damage_multipliers"][rank], .5)
        scguns = source("ScgunsIntegration")
        self.assertIn("tierScgunsDamage(TierAttributes.key(attacker))", scguns)
        self.assertNotIn("tierGunDamage(", scguns)
        self.assertEqual(scguns.count("HumanGrowth.scaleGunDamage(attacker,"), 1)
        self.assertIn("HumanGrowth.gunSpreadMultiplier(human)", scguns)
        self.assertIn("tierGunDamage(TierAttributes.key(attacker))", source("TaczIntegration"))

    def test_shared_tactics_do_not_link_optional_gun_api(self):
        goal = source("GunnerGoal")
        self.assertNotIn("com.tacz.", goal)
        self.assertNotIn("top.ribs.scguns", goal)
        self.assertIn("operator.shoot(weapon, target, shotAim.pitch(), shotAim.yaw())", goal)
        self.assertIn("previous.aim(false)", source("CompositeGunSupport"))
        self.assertNotIn("new GunAttackGoal", source("ScgunsIntegration"))
        animator = (ROOT / "src/main/java/com/craftix/hostile_humans/client/renderer/TaczNpcAnimator.java").read_text(encoding="utf-8")
        self.assertIn("GunSupport.isTaczGunItem", animator)
        self.assertNotIn("GunSupport.get().isGun", animator)
        self.assertIn("instanceof IEnergyGun", source("ScgunsIntegration"))

    def test_energy_uses_public_capability_and_one_paid_reload_transaction(self):
        adapter = source("ScgunsIntegration")
        for api in ("ForgeCapabilities.ENERGY", "storage.receiveEnergy(", "extractEnergy(energyCost(weapon), false)"):
            self.assertIn(api, adapter)
        self.assertNotIn('putInt("Energy"', adapter)
        self.assertLess(adapter.index("HumanAmmo.consumeMagazines(human, stack)"), adapter.index('state.putUUID("transaction"'))
        self.assertIn('stack.getTag().getUUID(RELOAD).equals(state.getUUID("transaction"))', adapter)
        self.assertIn('GunSupport.get().magazineCost(gun)', source("HumanAmmo"))
        config = json.loads((ROOT / "src/main/resources/defaults/hostile_humans_unified.json").read_text(encoding="utf-8"))
        fees = config["scguns"]["magazines_per_reload_by_type"]
        self.assertEqual([fees[k] for k in ("pistol", "rifle", "sniper", "lmg", "heavy")], [1, 2, 3, 4, 5])
        self.assertEqual(fees["laser"], 2)

    def test_medical_custody_and_original_first_aid_remain_connected(self):
        facade = source("MedicalSupplies")
        self.assertIn("FirstAidCompat.tick(human)", facade)
        self.assertIn("ScgunsMedical.tick(human)", facade)
        self.assertIn("MedicalSupplies.tick(human)", source("RecoverySupplies"))
        medical = source("ScgunsMedical")
        self.assertIn("RecoverySupplies.ownsSupplyUse", medical)
        self.assertIn("PotionRecoveryPolicy.needsPotionCharge", medical)
        self.assertIn("human.level().isClientSide", medical)
        self.assertNotIn("getDeclaredField", medical)

    def test_medical_regeneration_owns_cooldown_not_absorption(self):
        medical = source("ScgunsMedical")
        self.assertIn("filter(ScgunsSettings.Effect::isRegeneration)", medical)
        settings = source("ScgunsSettings")
        self.assertIn("filter(Effect::isRegeneration).mapToInt(Effect::durationTicks)", settings)
        config = json.loads((ROOT / "src/main/resources/defaults/hostile_humans_unified.json").read_text(encoding="utf-8"))
        rows = config["scguns"]["medical_supplies"]
        expected = {"basic_poultice": (8, 65, []), "honey_sulfur_poultice": (16, 70, [200]),
                    "enchanted_bandage": (24, 80, [200, 800]), "dragon_salve": (32, 90, [400, 1400])}
        for name, (healing, quality, durations) in expected.items():
            row = rows["scguns:" + name]
            self.assertEqual(row["healing"], healing)
            self.assertEqual(row["quality"], quality)
            self.assertEqual(row["shared_cooldown_ticks"], 100)
            self.assertEqual([e["duration_ticks"] for e in row["effects"]], durations)

    def test_raid_is_player_owned_and_consumption_follows_acceptance(self):
        item = source("ScgunsSignalItem")
        self.assertLess(item.index("ScgunsCompat.launchFlare(serverPlayer)"), item.index("stack.shrink(1)"))
        raid = source("ScgunsRaidBridge")
        self.assertIn("new RaidFlareEntity(level, player", raid)
        self.assertIn("RaidManager.hasActiveRaidInDimension", raid)
        self.assertIn("validDimensions()", raid)
        self.assertNotIn("new Human", raid)

    def test_config_and_magazine_recipe_support_scguns_without_tacz(self):
        config = json.loads((ROOT / "src/main/resources/defaults/hostile_humans_unified.json").read_text(encoding="utf-8"))
        scguns = config["scguns"]
        self.assertEqual(len(scguns["medical_supplies"]), 4)
        self.assertGreater(scguns["ai_accuracy_modifier"], 0)
        self.assertEqual(scguns["signal_flare"]["raid_id"], "copper")
        recipe = json.loads((ROOT / "src/main/resources/data/hostile_humans_unified/recipes/human_magazine.json").read_text())
        self.assertEqual(recipe["conditions"][0]["type"], "forge:or")
        self.assertEqual({c["modid"] for c in recipe["conditions"][0]["values"]}, {"tacz", "scguns"})

    def test_ordinary_armor_table_excludes_exosuit_and_covers_registered_items(self):
        groups = json.loads((ROOT / "src/main/resources/defaults/equipment_integrations.json").read_text(encoding="utf-8"))["scguns"]
        self.assertEqual(set(groups), {"roamer", "tier1", "tier2", "tier3"})
        ids = {row[slot] for rows in groups.values() for row in rows for slot in ("head", "chest", "legs", "feet")}
        self.assertEqual(len(ids), 31)
        self.assertFalse(any("exo_suit" in id or "module" in id for id in ids))
        if os.environ.get("SCGUNS_API_JAR"):
            with zipfile.ZipFile(os.environ["SCGUNS_API_JAR"]) as jar:
                registrations = jar.read("top/ribs/scguns/init/ModItems.class")
                for id in ids:
                    self.assertIn(id.split(":")[1].upper().encode(), registrations, id)

    @unittest.skipUnless(os.environ.get("SCGUNS_API_JAR"), "Set SCGUNS_API_JAR for local API verification")
    def test_real_jar_public_signatures(self):
        entity = "Lnet/minecraft/world/entity/LivingEntity;"
        level = "Lnet/minecraft/world/level/Level;"
        stack = "Lnet/minecraft/world/item/ItemStack;"
        checks = {
            "entity/ai/AIGunEvent": [("performGunAttack", "(Lnet/minecraft/world/entity/Mob;" + entity + stack + "Ltop/ribs/scguns/common/Gun;F)V")],
            "item/GunItem": [("getModifiedGun", "(" + stack + ")Ltop/ribs/scguns/common/Gun;")],
            "common/Gun$General": [("getGripType", "(" + stack + ")Ltop/ribs/scguns/common/GripType;")],
            "common/GripType": [("id", "()Lnet/minecraft/resources/ResourceLocation;")],
            "client/handler/GunRenderingHandler": [
                ("applyWeaponScale", "(" + stack + "Lcom/mojang/blaze3d/vertex/PoseStack;)V"),
                ("renderWeapon", "(" + entity + stack + "Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V")],
            "client/handler/DualWieldShotTracker": [("incrementShotCount", "(I)V"), ("clearShotCount", "(I)V")],
            "client/handler/EntityMuzzleFlashHandler": [("onRenderLivingPost", "(Lnet/minecraftforge/client/event/RenderLivingEvent$Post;)V")],
            "common/Gun": [("getAdditionalDamage", "(" + stack + ")F"), ("getAmmoCount", "(" + stack + ")I"), ("getBurstCooldown", "(" + stack + ")I"), ("getProjectile", "(" + stack + ")Ltop/ribs/scguns/common/Gun$Projectile;")],
            "common/Gun$Projectile": [("getEnergyUse", "()I")],
            "common/Gun$Reloads": [("getReloadTimer", "()I"), ("getReloadAmount", "()I")],
            "util/GunEnchantmentHelper": [("getRealReloadSpeed", "(" + stack + ")I"), ("getMagReloadSpeed", "(" + stack + ")I")],
            "network/message/S2CMessageGunSound": [("<init>", "(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/sounds/SoundSource;FFFFFIZZ)V")],
            "common/Gun$Sounds": [("getReload", "()Lnet/minecraft/resources/ResourceLocation;"), ("getCock", "()Lnet/minecraft/resources/ResourceLocation;")],
            "config/RaidFlareConfig$FlareData": [("burstDelay", "()I")],
            "item/GrenadeItem": [("create", "(" + level + entity + "I)Ltop/ribs/scguns/entity/throwable/ThrowableGrenadeEntity;")],
            "item/GasGrenadeItem": [("create", "(" + level + entity + "IF)Ltop/ribs/scguns/entity/throwable/ThrowableGrenadeEntity;")],
            "entity/throwable/ThrowableShotballEntity": [("<init>", "(" + level + entity + ")V")],
            "entity/projectile/RaidFlareEntity": [("<init>", "(" + level + entity + "Ljava/lang/String;)V")],
            "config/RaidConfig": [("getRaidByRaidId", "(Ljava/lang/String;)Ltop/ribs/scguns/config/RaidConfig$RaidData;")],
            "event/GunProjectileHitEvent": [("getProjectile", "()Ltop/ribs/scguns/entity/projectile/ProjectileEntity;")],
            "entity/projectile/ProjectileEntity": [("getShooter", "()" + entity), ("getWeapon", "()" + stack), ("setAdditionalDamage", "(F)V")],
        }
        with zipfile.ZipFile(os.environ["SCGUNS_API_JAR"]) as jar:
            for cls, expected in checks.items():
                methods = public_methods(jar.read("top/ribs/scguns/" + cls + ".class"))
                for method in expected: self.assertIn(method, methods, cls)

if __name__ == "__main__":
    unittest.main()
