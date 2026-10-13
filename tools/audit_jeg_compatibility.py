"""Read-only public JEG API/Mixin contract audit. Does not load the game or compile Java."""
import argparse
import re
import subprocess
from pathlib import Path

CONTRACTS = {
    "common.GripType": ["MINI_GUN", "getHeldAnimation()"],
    "client.render.pose.AimPose": ["getIdle()"],
    "client.render.pose.AimPose$Instance": ["getRightArm()", "getLeftArm()", "getRenderYawOffset()", "getItemTranslate()", "getItemRotation()"],
    "client.render.pose.LimbPose": ["getRotationAngleX()", "getRotationAngleY()", "getRotationAngleZ()", "getRotationPointX()", "getRotationPointY()", "getRotationPointZ()"],
    "client.render.pose.MiniGunPose": ["MiniGunPose()"],
    "item.GrenadeItem": ["create(net.minecraft.world.item.ItemStack, net.minecraft.world.level.Level, net.minecraft.world.entity.LivingEntity, int)"],
    "item.WaterBombItem": ["create(net.minecraft.world.level.Level, net.minecraft.world.entity.LivingEntity, int)"],
    "item.MolotovCocktailItem": ["create(net.minecraft.world.level.Level, net.minecraft.world.entity.LivingEntity, int)"],
    "entity.throwable.ThrowableItemEntity": ["setItem(net.minecraft.world.item.ItemStack)"],
    "entity.throwable.ThrowableWaterBombEntity": ["onDeath()"],
    "faction.GunnerMobSpawner": ["reassessWeaponGoal(net.minecraft.world.entity.PathfinderMob)", "onLivingUpdate(net.minecraftforge.event.entity.living.LivingEvent$LivingTickEvent)", "onEntityJoinWorld(net.minecraftforge.event.entity.EntityJoinLevelEvent)", "onSpecialSpawn(net.minecraftforge.event.entity.living.MobSpawnEvent$FinalizeSpawn)"],
    "entity.ai.GunAttackGoal": ["m_8036_()", "m_8037_()"],
    "entity.ai.AIGunEvent": ["performGunAttack(net.minecraft.world.entity.Mob, net.minecraft.world.entity.LivingEntity, net.minecraft.world.item.ItemStack, ttv.migami.jeg.common.Gun, float, boolean)"],
    "item.GunItem": ["getModifiedGun(net.minecraft.world.item.ItemStack)"],
    "common.Gun$General": ["getGripType()", "getFireMode()", "getBurstAmount()", "getBurstDelay()", "getFireTimer()"],
    "common.Gun$Reloads": ["getReloadType()", "getReloadTimer()", "getAdditionalReloadTimer()", "getReloadAmount()"],
    "common.Gun$Projectile": ["getItem()", "getSpeed()", "isGravity()"],
    "common.Gun$Sounds": ["getReloadStart()", "getReloadLoad()", "getReloadEnd()", "getEjectorPull()", "getEjectorRelease()", "getFire()", "getSilencedFire()"],
    "util.GunModifierHelper": ["getModifiedAmmoCapacity(net.minecraft.world.item.ItemStack, ttv.migami.jeg.common.Gun)", "getModifiedProjectileSpeed(net.minecraft.world.item.ItemStack, double)", "getModifiedProjectileGravity(net.minecraft.world.item.ItemStack, double)", "getModifiedProjectileLife(net.minecraft.world.item.ItemStack, int)", "isSilencedFire(net.minecraft.world.item.ItemStack)"],
    "util.GunEnchantmentHelper": ["getReloadInterval(net.minecraft.world.item.ItemStack, int)", "getRate(net.minecraft.world.item.ItemStack, ttv.migami.jeg.common.Gun)", "getProjectileSpeedModifier(net.minecraft.world.item.ItemStack)"],
    "entity.projectile.ProjectileEntity": ["getShooter()"],
    "entity.Splash": ["getOwner()"],
    "entity.Bubble": ["getOwner()"],
    "client.handler.GunRenderingHandler": ["applyWeaponScale(net.minecraft.world.item.ItemStack, com.mojang.blaze3d.vertex.PoseStack)", "renderWeapon(net.minecraft.world.entity.LivingEntity, net.minecraft.world.item.ItemStack, net.minecraft.world.item.ItemDisplayContext, com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.MultiBufferSource, int, float)", "entityIdForMuzzleFlash"],
    "network.PacketHandler": ["getPlayChannel()"],
    "network.message.S2CMessageGunSound": ["S2CMessageGunSound(net.minecraft.resources.ResourceLocation, net.minecraft.sounds.SoundSource, float, float, float, float, float, int, boolean, boolean)"],
}

def audit(jar, javap):
    output = {}
    for name, contracts in CONTRACTS.items():
        result = subprocess.run([str(javap), "-classpath", str(jar), "-p", "-c", "ttv.migami.jeg." + name], capture_output=True, text=True, check=True)
        output[name] = result.stdout
        if name == "client.render.pose.MiniGunPose":
            for factory in ("getUpPose()", "getForwardPose()", "getDownPose()"):
                if not any(factory in line and line.lstrip().startswith("protected ") for line in result.stdout.splitlines()):
                    raise AssertionError(f"Missing extensible native machine-gun pose: {factory}")
        for contract in contracts:
            lines = [line for line in result.stdout.splitlines() if contract in line and line.lstrip().startswith("public ")]
            if not lines:
                raise AssertionError(f"Missing public JEG API: {name}.{contract}")
    def body(text, name):
        start = text.index(name + "(")
        remaining = text[start:]
        end = re.search(r"\n  (?:public|protected|private) ", remaining)
        return remaining[:end.start()] if end else remaining
    goal = output["entity.ai.GunAttackGoal"]
    if "protected final T shooter;" not in goal or "T extends net.minecraft.world.entity.PathfinderMob" not in goal:
        raise AssertionError("Native goal shooter shadow no longer erases to final PathfinderMob")
    water = body(output["entity.throwable.ThrowableWaterBombEntity"], "onDeath")
    if water.count("WhirpoolEntity.createLandWhirpool:") != 1:
        raise AssertionError("Hand-water-bomb attribution needs one native owner argument hook")
    projectile = output["entity.projectile.ProjectileEntity"]
    candidates = sum(body(projectile, method).count("Level.m_6249_:") for method in ("findEntityOnPath", "findEntitiesOnPath"))
    if candidates != 3:
        raise AssertionError(f"Collision hook expected 3 native candidate queries, found {candidates}")
    if len(re.findall(r"double 0\.9d", body(projectile, "ignoreEntity"))) != 1:
        raise AssertionError("Native same-type random immunity must expose exactly one 0.9 constant")
    if body(projectile, "onHitEntity").count("Entity.m_6469_:") != 1:
        raise AssertionError("Damage normalization hook must match exactly one hurt call")
    if body(output["client.handler.GunRenderingHandler"], "renderWeapon").count("ShootingHandler.isShooting:()Z") != 1:
        raise AssertionError("NPC flash hook must match exactly one input-state call")
    for effect in ("entity.Splash", "entity.Bubble"):
        if body(output[effect], "m_8119_").count("Level.m_45933_:") != 1:
            raise AssertionError(f"{effect}: expected one water area candidate query")
    if body(output["entity.Splash"], "m_8119_").count("LivingEntity.m_6469_:") != 2:
        raise AssertionError("Splash damage attribution must cover both native hurt calls")
    return sum(map(len, CONTRACTS.values()))

if __name__ == "__main__":
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument("jar", type=Path)
    parser.add_argument("--javap", type=Path, default=Path(r"D:\Java\17\bin\javap.exe"))
    args = parser.parse_args()
    print(f"JEG public API contracts: {audit(args.jar, args.javap)}; collision/damage/flash boundaries match.")
