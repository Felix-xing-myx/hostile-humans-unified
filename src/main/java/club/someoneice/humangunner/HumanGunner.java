package club.someoneice.humangunner;


import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import com.craftix.hostile_humans.entity.entities.ModEntityType;
import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.HumanUtil;
import com.mojang.logging.LogUtils;
import com.craftix.hostile_humans.patch.HostileHumansEquipmentPatch;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod(HumanGunner.MOD_ID)
public final class HumanGunner {
    public static final String MOD_ID = "humangunner";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final String GUN_ROLL_DONE = MOD_ID + ":gun_roll_done";
    private static final String INCOMING_GUNFIRE_UNTIL = MOD_ID + ":incoming_gunfire_until";
    private static final AtomicBoolean WARNED_EMPTY_GUN_POOL = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_READY_GUN_POOL = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_FIRST_EQUIP = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_FIRST_ADAPTIVE_ARROW = new AtomicBoolean();
    private static final Set<Human> RIVALRY_CONFIGURED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Human> COMBAT_AI_CONFIGURED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Human> SOLDIER_ORDERS_CONFIGURED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Human> INITIALIZED_HUMANS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final double ARROW_AIR_DRAG = 0.99D;
    // Vanilla arrows and Spartan Weaponry bolts use 0.05 gravity.
    private static final double ARROW_AIM_GRAVITY = 0.05D;
    private static final double BALLISTIC_SEARCH_STEP = 0.25D;
    private static final double BALLISTIC_MAX_TICKS = 60.0D;
    public static final String ADAPTIVE_BALLISTICS_APPLIED =
            MOD_ID + ":adaptive_ballistics_applied";
    public static final String NPC_CROSSBOW_PROJECTILE =
            MOD_ID + ":npc_crossbow_projectile";
    private static final UUID COMBAT_MOVEMENT_MODIFIER_ID =
            UUID.fromString("82624020-1000-4000-8000-000000000301");
    private static final Set<String> PILLAGERS_GUN_FIREARMS = Set.of(
            "pistol", "shotgun", "assault_rifle", "snipers_rifle", "bazooka"
    );

    public HumanGunner() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        HumanGunnerRegistries.ITEMS.register(modBus);
        HumanGunnerRegistries.MENUS.register(modBus);
        HumanCommandNetwork.register();
        modBus.addListener(HumanGunner::onRegisterSpawnPlacements);
        modBus.addListener(HumanGunner::onBuildCreativeTab);
        if (ModList.get().isLoaded("touhou_little_maid")) {
            TouhouMaidCompat.register();
        }
        GunSupport.get().registerEvents();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, HumanGunner::onLivingChangeTarget);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, HumanGunner::onHumanFriendlyFire);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, HumanGunner::onLivingHurt);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, HumanGunner::onSoldierDamage);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, HumanGunner::onLivingDamage);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, HumanGunner::onLivingDeath);
        // Run after ordinary LivingTick listeners so late compatibility code
        // cannot leave the persistent tier health roll replaced by an old base.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, HumanGunner::onLivingTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, HumanGunner::onEquipmentChange);
        MinecraftForge.EVENT_BUS.addListener(HumanGunner::onEntityJoin);
        // Canceling FinalizeSpawn skips initialization, not entity insertion.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, NaturalHumanSpawnRules::trackNaturalMember);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, NaturalHumanSpawnRules::onNaturalMemberJoin);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, HumanGunner::onEntityLeave);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, HumanGunner::onLivingDrops);
        MinecraftForge.EVENT_BUS.addListener(HiredHumanRecall::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(HiredHumanRecall::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(HiredHumanRecall::onPlayerLogout);
        MinecraftForge.EVENT_BUS.addListener(HiredHumanRecall::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(OwnerOfflinePolicy::onPlayerLogout);
        MinecraftForge.EVENT_BUS.addListener(OwnerOfflinePolicy::onPlayerLogin);
        LOGGER.info("Hostile Humans Unified initialized; optional TaCZ firearms {}", GunSupport.get().enabled() ? "enabled" : "disabled");
    }

    private static void onRegisterSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(
                ModEntityType.HUMAN1.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                NaturalHumanSpawnRules::checkSquadSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );
        event.register(
                ModEntityType.HUMAN2.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                NaturalHumanSpawnRules::checkSquadSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );
        event.register(
                HumanGunnerRegistries.TIER_THREE_HUMAN.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                NaturalHumanSpawnRules::checkSquadSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );
    }

    private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(HumanGunnerRegistries.ROAMER_IDENTITY_BADGE);
            event.accept(HumanGunnerRegistries.TIER_ONE_IDENTITY_BADGE);
            event.accept(HumanGunnerRegistries.TIER_TWO_IDENTITY_BADGE);
            event.accept(HumanGunnerRegistries.TIER_THREE_IDENTITY_BADGE);
            event.accept(HumanGunnerRegistries.ULTIMATE_IDENTITY_BADGE);
            event.accept(HumanGunnerRegistries.SOLDIER_ROSTER);
            event.accept(HumanGunnerRegistries.ROAMER_CONTRACT);
            event.accept(HumanGunnerRegistries.TIER_ONE_CONTRACT);
            event.accept(HumanGunnerRegistries.TIER_TWO_CONTRACT);
            event.accept(HumanGunnerRegistries.TIER_THREE_CONTRACT);
            event.accept(HumanGunnerRegistries.ROAMER_SIGNAL_FLARE);
            event.accept(HumanGunnerRegistries.TIER_ONE_SIGNAL_FLARE);
            event.accept(HumanGunnerRegistries.TIER_TWO_SIGNAL_FLARE);
            event.accept(HumanGunnerRegistries.TIER_THREE_SIGNAL_FLARE);
            event.accept(HumanGunnerRegistries.ROAMER_HOSTILE_BEACON);
            event.accept(HumanGunnerRegistries.TIER_ONE_HOSTILE_BEACON);
            event.accept(HumanGunnerRegistries.TIER_TWO_HOSTILE_BEACON);
            event.accept(HumanGunnerRegistries.TIER_THREE_HOSTILE_BEACON);
        }
    }

    private static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Human human && !human.level().isClientSide) {
            if (!initializeHumanIfReady(human)) {
                return;
            }
            if (OwnerOfflinePolicy.tick(human)) {
                return;
            }
            HumanRelations.tick(human);
            RandomizedHumanHealth.ensureApplied(human);
            HiredHumanHealthWarning.tick(human);
            if (human.getPersistentData().hasUUID(HumanRelations.TEMP_OWNER)) {
                if (human.hasOwner()) {
                    // Recruitment is authoritative even if a save/reload or
                    // another event observes the entity between tame() and the
                    // normal conversion cleanup.
                    human.getPersistentData().remove(HumanRelations.TEMP_OWNER);
                    human.getPersistentData().remove(HumanRelations.TEMP_UNTIL);
                    human.getPersistentData().remove(HumanRelations.NO_DROPS);
                } else {
                    long until = human.getPersistentData().getLong(HumanRelations.TEMP_UNTIL);
                    if (human.level().getGameTime() >= until) {
                        human.discard();
                        return;
                    }
                    Player owner = human.level().getPlayerByUUID(
                            human.getPersistentData().getUUID(HumanRelations.TEMP_OWNER));
                    if (owner != null && human.distanceToSqr(owner) > 100.0D
                            && human.getTarget() == null && human.tickCount % 20 == 0) {
                        human.getNavigation().moveTo(owner, 1.15D);
                    }
                }
            }
            // The menu is backed directly by HumanData/equipment. While it is
            // open, no AI inventory writer may race the player's slot actions.
            if (HumanInventorySession.isEditing(human)) {
                return;
            }
            EquipmentBreakSounds.tick(human);
            SoldierOrder.tick(human);
            // The external Hostile Humans index can lag an entity's owner
            // after a transfer or reload. The entity NBT is authoritative;
            // repair only mismatches and spread these checks across ticks.
            if (human.hasOwner() && (human.tickCount + (human.getId() & 127)) % 100 == 0) {
                com.craftix.hostile_humans.entity.data.HumanServerData data =
                        com.craftix.hostile_humans.entity.data.HumanServerData.get();
                if (data != null) {
                    com.craftix.hostile_humans.entity.data.HumanData indexed =
                            data.getHumanMob(human.getUUID());
                    if (indexed == null || !human.getOwnerUUID().equals(indexed.getOwnerUUID())) {
                        data.updateOrRegisterHumanMob(human);
                    }
                }
            }
            RecoverySupplies.tickGradualHealing(human);
            // Settle interrupted hand/inventory exchanges and reconcile a
            // stored shield. TaCZ may keep MAIN_HAND in an item-use state, so
            // custody tracks the exact recovery hand instead of treating any
            // active use as a reason to postpone the check indefinitely.
            RecoverySupplies.tickHandCustody(human);
            GunCustody.tick(human);
            RangedWeaponCustody.tick(human);
            if ((human.tickCount + (human.getId() & 7)) % 8 == 0) {
                RecoverySupplies.auditOwnedShields(human);
                GunCustody.auditOwnedGuns(human);
            }
            if ((human.tickCount + (human.getId() & 63)) % 100 == 0) {
                ensureMeleeFallback(human);
            }
            if ((human.tickCount + Math.floorMod(human.getId(), 20)) % 20 == 0) {
                HumanManagedLoadout.equipStoredMeleeIfMainHandEmpty(human);
                HumanLootManager.optimizeOwnedEquipment(human);
            }
            GunSupport.get().tickIdleReload(human);
            if (human.getTarget() instanceof Player player
                    && HumanRelations.effectiveOwner(human) == null
                    && IdentityBadgeAccess.blocksInitiatedHostility(human, player)
                    && !HumanRelations.isForcedHostileTo(human, player)) {
                human.setTarget(null);
                human.setLastHurtByMob(null);
            }
        }
    }

    public static void openHiredHumanUi(net.minecraft.server.level.ServerPlayer player, Human human) {
        if (!HumanRelations.isOwnedBy(human, player)) return;
        if (player.isShiftKeyDown()) {
            HumanInventoryMenu.open(player, human);
        } else {
            HumanCommandNetwork.openFor(player, human);
        }
    }

    private static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Human human && !human.level().isClientSide) {
            RecoverySupplies.auditEquipmentChange(
                    human, event.getSlot(), event.getFrom(), event.getTo()
            );
            GunCustody.auditEquipmentChange(
                    human, event.getSlot(), event.getFrom(), event.getTo()
            );
            EquipmentBreakSounds.observeEquipmentChange(
                    human, event.getSlot(), event.getFrom(), event.getTo()
            );
        }
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        // Tridents keep their separate hybrid trajectory. Ordinary arrows and
        // Spartan bolts are finalized at the head of their first server tick:
        // applying here is too early and launch initialization can overwrite it.
        if (event.getEntity() instanceof ThrownTrident arrow
                && arrow.getOwner() instanceof Human shooter) {
            improveHumanProjectile(shooter, arrow);
            return;
        }
        if (!(event.getEntity() instanceof Human human)) {
            return;
        }
        initializeHumanIfReady(human);
    }

    private static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Human human)) {
            return;
        }
        Entity.RemovalReason reason = human.getRemovalReason();
        if (reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.DISCARDED) {
            // Chunk unload and dimension transfer must retain external inventory.
            return;
        }
        com.craftix.hostile_humans.entity.data.HumanServerData serverData =
                com.craftix.hostile_humans.entity.data.HumanServerData.get();
        if (serverData != null) {
            serverData.humanGunner$removeHuman(human.getUUID());
        }
    }

    static boolean initializeHumanIfReady(Human human) {
        if (INITIALIZED_HUMANS.contains(human)) {
            return true;
        }
        // Direct EntityType#create/add paths and a few compatibility spawners
        // can publish the join event before Hostile Humans has registered its
        // server-side HumanData. Defer all inventory/loadout work until that
        // data is available; otherwise Spartan replacement calls putItemAway
        // against null data and recovery provisioning can be skipped.
        if (human.getData() == null) {
            return false;
        }
        RandomizedHumanHealth.applyOnce(human);
        boolean newTierThreeLoadout = TierThreeLoadout.configureOnce(human);
        TierAttributes.apply(human);
        MovementSpeedController.normal(human);
        InventoryTotemProtection.stowOffhandTotem(human);
        SpartanEquipmentCompat.applyOnce(human);
        if (newTierThreeLoadout) {
            TierThreeLoadout.ensureRangedWeapon(human);
        }
        GuaranteedShieldLoadout.applyOnce(human);
        ShieldEnchantmentRoll.applyOnce(human);
        configureTierRivalry(human);
        configureCombatAi(human);
        configureSoldierOrders(human);
        SoldierCombatMode.initialize(human);
        if (GunSupport.get().enabled() && !human.getPersistentData().getBoolean(GUN_ROLL_DONE)) {
            HumanGunnerConfig config = HumanGunnerConfig.get();
            long availableGuns = config.availableGunCount();
            if (availableGuns == 0) {
                if (WARNED_EMPTY_GUN_POOL.compareAndSet(false, true)) {
                    LOGGER.warn("Human Gunner found no configured firearms in the loaded TaCZ common gun index");
                }
            } else {
                if (LOGGED_READY_GUN_POOL.compareAndSet(false, true)) {
                    LOGGER.info(
                            "Human Gunner is ready with {} configured TaCZ firearms (tier-1 chance {}, tier-2 chance {}, tier-3 chance {}, roamer chance {})",
                            availableGuns,
                            config.tier1GunChance(),
                            config.tier2GunChance(),
                            config.tier3GunChance(),
                            config.roamerGunChance()
                    );
                }

                // Persist the result, including a chance miss, so saving and reloading an
                // entity cannot repeatedly reroll its equipment.
                human.getPersistentData().putBoolean(GUN_ROLL_DONE, true);
                tryEquipGun(human, false);
            }
        }
        ensureMeleeFallback(human);
        ((StaticCombatGoalHost) human).humanGunner$installStaticCombatGoals();
        INITIALIZED_HUMANS.add(human);
        return true;
    }

    /** Used by the base Human tick to preserve the server-side menu lock. */
    public static boolean isManualInventoryOpen(Human human) {
        return HumanInventorySession.isEditing(human);
    }



    static boolean isUnderRecentGunfire(Human human) {
        int until = human.getPersistentData().getInt(INCOMING_GUNFIRE_UNTIL);
        return until > 0 && until >= human.tickCount;
    }

    static int recentGunfireUntil(Human human) {
        return human.getPersistentData().getInt(INCOMING_GUNFIRE_UNTIL);
    }




    private static void onSoldierDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (!event.getEntity().level().isClientSide && event.getAmount() > 0
                && event.getSource().getEntity() instanceof Human human && human.hasOwner()) {
            human.soldierCombatMemory.hit(event.getEntity().getUUID(), human.level().getGameTime());
        }
    }

    private static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Human attacker) {
            if (event.getNewTarget() != null && (OwnerOfflinePolicy.isOwnerOffline(attacker)
                    || HumanRelations.allied(attacker, event.getNewTarget())
                    || !SoldierOrder.allowsTarget(attacker, event.getNewTarget()))) {
                event.setNewTarget(null);
            }
        }
    }

    /**
     * Cancels every direct and owner-attributed indirect hit between allied
     * tiered humans before ordinary damage modifiers observe it. Vanilla bows,
     * crossbows, Spartan arrows/bolts and thrown tridents all expose their Human
     * owner through DamageSource#getEntity. Roamers deliberately never satisfy
     * areSameFaction, so their existing infighting remains fully enabled.
     */
    private static void onHumanFriendlyFire(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof Human attacker
                && event.getEntity() instanceof Human defender
                && shouldBlockHumanDamage(attacker, defender)) {
            event.setCanceled(true);
        }
    }

    private static void onLivingHurt(LivingHurtEvent event) {
        Entity attacker = event.getSource().getEntity();
        Entity directEntity = event.getSource().getDirectEntity();
        if (event.getEntity() instanceof Player protectedPlayer && attacker instanceof LivingEntity livingAttacker) {
            HumanRelations.defend(protectedPlayer, livingAttacker);
        }
        if (attacker instanceof Player attackingPlayer) {
            HumanRelations.followOwnerAssault(attackingPlayer, event.getEntity());
        }
        if (event.getEntity() instanceof Human damagedHuman) {
            event.setAmount(event.getAmount() * (float) TierAttributes.of(damagedHuman).incomingDamage());
            EquipmentBreakSounds.captureBeforeDamage(damagedHuman);
        }
        if (event.getEntity() instanceof Human defender && attacker instanceof Player player
                && !HumanRelations.isControlledBy(defender, player)) {
            IdentityBadgeAccess.rememberPlayerAttack(defender, player);
        }
        if (attacker instanceof Human humanAttacker) {
            if (event.getEntity() instanceof Player player
                    && HumanRelations.allied(humanAttacker, player)) {
                event.setCanceled(true);
                return;
            }
            if (directEntity == attacker) {
                float multiplier = (float) (UnifiedConfig.get().damage("human_melee_damage_multiplier", 1.7)
                        * TierAttributes.of(humanAttacker).meleeDamage());
                Human human = humanAttacker;
                if (human.getMainHandItem().getItem() instanceof net.minecraft.world.item.TridentItem) {
                    // The requested trident reduction applies to both the
                    // thrown projectile and the newly supported close stab.
                    multiplier *= (float) UnifiedConfig.get().damage("trident_stab_damage_multiplier", .42);
                }
                if (!human.onGround()
                        && human.getDeltaMovement().y < -0.02D
                        && human.getPersistentData().getInt("humangunner:jump_attack_until") >= human.tickCount) {
                    multiplier *= (float) UnifiedConfig.get().damage("jump_attack_damage_multiplier", 1.5);
                    human.getPersistentData().remove("humangunner:jump_attack_until");
                }
                event.setAmount(event.getAmount() * multiplier);
            } else if (directEntity instanceof ThrownTrident) {
                event.setAmount(event.getAmount()
                        * (float) (UnifiedConfig.get().damage("human_trident_damage_multiplier", .84)
                        * TierAttributes.of(humanAttacker).tridentDamage()
                        * UnifiedConfig.get().damage("trident_velocity_damage_multiplier", 1.5)));
            } else if (directEntity instanceof AbstractArrow) {
                event.setAmount(event.getAmount()
                        * (float) (UnifiedConfig.get().damage("human_bow_damage_multiplier", 1.008)
                        * TierAttributes.of(humanAttacker).bowDamage()
                        * UnifiedConfig.get().damage("bow_velocity_damage_multiplier", 1.5)));
            }
            return;
        }

        if (!(attacker instanceof Pillager) || directEntity == null) {
            return;
        }
        ResourceLocation directType = BuiltInRegistries.ENTITY_TYPE.getKey(directEntity.getType());
        if (directType.getNamespace().equals("pillagers_gun")) {
            event.setAmount(event.getAmount()
                    * (float) HumanGunnerConfig.get().pillagerGunDamageMultiplier());
        }
    }

    private static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof Human human
                && !human.level().isClientSide
                && event.getAmount() > 0.0F) {
            EquipmentBreakSounds.detectAfterDamage(human);
            // LivingDamageEvent carries the final post-armour, post-enchantment
            // amount. Counting it avoids treating blocked or mitigated attack
            // input as health actually lost.
            RecentDamageTracker.record(human, event.getAmount());
            if (event.getSource().getEntity() instanceof Player player
                    && HumanRelations.isOwnedBy(human, player)
                    && RecruitmentPolicy.betrayed(human.getHealth() - event.getAmount(), human.getMaxHealth())) {
                RecruitmentLedger.get(player.getServer()).dismiss(human.getUUID());
                human.setOwnerUUID(null);
                human.setTame(false);
                IdentityBadgeAccess.rememberPlayerAttack(human, player);
                human.setTarget(player);
                player.displayClientMessage(Component.translatable("message.humangunner.dismissed"), true);
            }
        }
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Human human) || human.level().isClientSide) {
            return;
        }
        if (human.getPersistentData().getBoolean(HumanRelations.NO_DROPS)) {
            return;
        }
        if (!event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                && InventoryTotemProtection.tryUseInventoryTotem(human, event.getSource())) {
            // LivingDeathEvent is posted before LivingEntity performs its death
            // transition. Restoring health and cancelling here provides the same
            // survival boundary without injecting into an obfuscated vanilla method.
            event.setCanceled(true);
            return;
        }
        RecruitmentLedger.get(human.getServer()).dismiss(human.getUUID());
        // Hostile Humans clears equipment inside dropCustomDeathLoot before
        // LivingDropsEvent. Capture every owned stack while the slots and
        // external backpack are still authoritative.
        HumanEquipmentDrops.captureBeforeDeath(human);
    }

    private static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob)) {
            return;
        }

        double chance = HumanGunnerConfig.get().gunDropChance();
        if (event.getEntity() instanceof Human human) {
            if (human.getPersistentData().getBoolean(HumanRelations.NO_DROPS)) {
                event.getDrops().clear();
                return;
            }
            HumanEquipmentDrops.rebuildOwnedDrops(human, event.getDrops(), chance);
            return;
        }
        // Preserve the existing 1% Pillagers Gun compatibility rule.
        boolean keepOneGun = event.getEntity().getRandom().nextDouble() < chance;
        boolean[] alreadyKept = {false};
        event.getDrops().removeIf(drop -> {
            if (!isDroppedGun(drop.getItem())) {
                return false;
            }
            if (!keepOneGun || alreadyKept[0]) {
                return true;
            }
            alreadyKept[0] = true;
            return false;
        });
    }

    public static void tryEquipGun(Human human, boolean forceRanged) {
        if (human.level().isClientSide || !GunSupport.get().enabled()) {
            return;
        }

        HumanGunnerConfig config = HumanGunnerConfig.get();
        double chance;
        String tierGunTypeKey;
        if (TierThreeHuman.isTierThree(human)) {
            chance = config.tier3GunChance();
            tierGunTypeKey = "tier3";
        } else if (human.getTier() == HumanTier.LEVEL1) {
            chance = config.tier1GunChance();
            tierGunTypeKey = "tier1";
        } else if (human.getTier() == HumanTier.LEVEL2) {
            chance = forceRanged ? config.forcedRangedChance() : config.tier2GunChance();
            tierGunTypeKey = "tier2";
        } else if (human.getTier() == HumanTier.ROAMER) {
            chance = config.roamerGunChance();
            tierGunTypeKey = "roamer";
        } else {
            return;
        }
        if (human.getRandom().nextDouble() >= chance) {
            return;
        }

        config.rollAvailableGun(human.getRandom(), tierGunTypeKey).ifPresent(gunId -> {
            ItemStack gun = GunSupport.get().createLoadedGun(gunId);
            if (gun.isEmpty()) {
                LOGGER.warn("TaCZ rejected configured Human Gunner firearm {}", gunId);
                return;
            }

            GunCustody.registerPrimaryGun(human, gun);

            ItemStack previousWeapon = human.getMainHandItem().copy();
            human.setItemSlot(EquipmentSlot.MAINHAND, gun);
            // Make the gun enter Forge's drop collection reliably; the global
            // drop filter below is the sole 1% gate. Bound tier-3 gear remains
            // unconditionally removed.
            human.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
            // Keep one melee fallback for the custody-owned two-block retreat
            // counterattack. Do not retain an old bow/crossbow: the gun remains
            // the authoritative ranged weapon for this NPC.
            if (!previousWeapon.isEmpty() && !HumanUtil.isRangedWeapon(previousWeapon)) {
                human.putItemAway(previousWeapon);
            }
            // This runs before the static combat goal set is installed at the
            // end of EntityJoinLevelEvent.
            human.setCombatTask();
            ensureMeleeFallback(human);
            if (LOGGED_FIRST_EQUIP.compareAndSet(false, true)) {
                LOGGER.info("Human Gunner first successful equipment roll: {} received {}", human.getTier(), gunId);
            }
            LOGGER.debug(
                    "Equipped npc={} tier={} with TaCZ firearm {}",
                    human.getUUID(), human.getTier(), gunId
            );
        });
    }

    public static void applyFirstTickArrowBallistics(Human shooter, AbstractArrow projectile) {
        if (projectile instanceof ThrownTrident
                || projectile.getPersistentData().getBoolean(ADAPTIVE_BALLISTICS_APPLIED)) {
            return;
        }
        projectile.getPersistentData().putBoolean(ADAPTIVE_BALLISTICS_APPLIED, true);
        improveHumanProjectile(shooter, projectile);
    }

    private static void improveHumanProjectile(Human shooter, AbstractArrow projectile) {
        net.minecraft.world.entity.LivingEntity target = shooter.getTarget();
        if (target == null || !target.isAlive()
                || (target instanceof Human other && areCombatAllies(shooter, other))) {
            return;
        }
        boolean trident = projectile instanceof ThrownTrident;
        boolean spartanBolt = projectile.getPersistentData().getBoolean(SpartanRangedCompat.NPC_BOLT);
        float velocity = trident ? 2.9F : spartanBolt ? 3.15F : 2.7F;
        boolean crossbow = !trident && (spartanBolt
                || projectile.getPersistentData().getBoolean(NPC_CROSSBOW_PROJECTILE)
                || shooter.getMainHandItem().getItem() instanceof CrossbowItem
                || (!(shooter.getMainHandItem().getItem() instanceof BowItem)
                && shooter.getOffhandItem().getItem() instanceof CrossbowItem));
        double spreadDegrees = RangedAccuracy.projectileSpreadDegrees(shooter, crossbow, trident);
        Vec3 origin = projectile.position();
        // Aim high through the upper torso. The stronger gravity bias adds the
        // requested long-range clearance on top of this 85%-height target point.
        Vec3 targetPoint = target.position().add(0.0D, target.getBbHeight() * 0.85D, 0.0D);
        if (!trident) {
            Vec3 ballisticDirection = solveArrowBallistics(
                    origin, targetPoint, target.getDeltaMovement(), velocity
            );
            double horizontalDistance = Math.hypot(
                    targetPoint.x - origin.x, targetPoint.z - origin.z
            );
            double launchAngle = Math.toDegrees(Math.atan2(
                    ballisticDirection.y,
                    Math.hypot(ballisticDirection.x, ballisticDirection.z)
            ));
            Vec3 shotDirection = RangedAccuracy.applyAngularSpread(
                    ballisticDirection, spreadDegrees, shooter.getRandom());
            projectile.shoot(shotDirection.x, shotDirection.y, shotDirection.z, velocity, 0.0F);
            // Required by the first-tick compatibility fallback: if an
            // external launcher already spawned the arrow, force an immediate
            // motion packet instead of letting the client keep spawn velocity.
            projectile.hasImpulse = true;
            projectile.getPersistentData().putDouble(
                    MOD_ID + ":adaptive_ballistics_distance", horizontalDistance
            );
            projectile.getPersistentData().putDouble(
                    MOD_ID + ":adaptive_ballistics_angle", launchAngle
            );
            if (LOGGED_FIRST_ADAPTIVE_ARROW.compareAndSet(false, true)) {
                LOGGER.info(
                        "Adaptive arrow ballistics applied on first projectile tick: projectile={}, distance={}, angle={}, speed={}",
                        BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()),
                        String.format(java.util.Locale.ROOT, "%.2f", horizontalDistance),
                        String.format(java.util.Locale.ROOT, "%.2f", launchAngle),
                        velocity
                );
            }
            return;
        }

        double travelTicks = Mth.clamp(origin.distanceTo(targetPoint) / velocity, 0.0D, 12.0D);
        Vec3 aim = targetPoint.add(target.getDeltaMovement().scale(travelTicks));
        Vec3 delta = aim.subtract(origin);
        // Tridents used to aim at the eyes and then add 0.14 blocks of lift per
        // horizontal block, which sent medium-range throws roughly one block
        // above the victim. Compensate expected gravity over flight time instead.
        double arc = 0.025D * travelTicks * travelTicks;
        Vec3 shotDirection = RangedAccuracy.applyAngularSpread(
                new Vec3(delta.x, delta.y + arc, delta.z), spreadDegrees, shooter.getRandom());
        projectile.shoot(shotDirection.x, shotDirection.y, shotDirection.z, velocity, 0.0F);
        projectile.hasImpulse = true;
    }

    /**
     * Solves the low ballistic arc used by AbstractArrow. Unlike the former
     * horizontal-distance multiplier, this accounts for both 0.99 air drag
     * and the 0.05 downward acceleration applied after every arrow tick.
     */
    private static Vec3 solveArrowBallistics(
            Vec3 origin, Vec3 targetPoint, Vec3 targetMotion, double launchSpeed
    ) {
        Vec3 boundedMotion = new Vec3(
                targetMotion.x,
                Mth.clamp(targetMotion.y, -0.15D, 0.15D),
                targetMotion.z
        );
        double speedSquared = launchSpeed * launchSpeed;
        double previousTicks = 0.05D;
        Vec3 best = requiredArrowVelocity(
                origin, targetPoint, boundedMotion, previousTicks
        );
        double bestError = Math.abs(best.lengthSqr() - speedSquared);

        for (double ticks = BALLISTIC_SEARCH_STEP;
                ticks <= BALLISTIC_MAX_TICKS;
                ticks += BALLISTIC_SEARCH_STEP) {
            Vec3 required = requiredArrowVelocity(origin, targetPoint, boundedMotion, ticks);
            double requiredSquared = required.lengthSqr();
            double error = Math.abs(requiredSquared - speedSquared);
            if (error < bestError) {
                best = required;
                bestError = error;
            }
            if (requiredSquared <= speedSquared) {
                // The first feasible crossing is the low arc. Refine it so
                // shoot() normalizing the direction does not materially alter
                // the predicted flight time.
                double low = previousTicks;
                double high = ticks;
                for (int iteration = 0; iteration < 12; iteration++) {
                    double middle = (low + high) * 0.5D;
                    Vec3 middleRequired = requiredArrowVelocity(
                            origin, targetPoint, boundedMotion, middle
                    );
                    if (middleRequired.lengthSqr() > speedSquared) {
                        low = middle;
                    } else {
                        high = middle;
                    }
                }
                return requiredArrowVelocity(origin, targetPoint, boundedMotion, high);
            }
            previousTicks = ticks;
        }

        // A target outside the physical low-arc envelope should still receive
        // the closest possible shot instead of falling back to a flat vector.
        return best;
    }

    private static Vec3 requiredArrowVelocity(
            Vec3 origin, Vec3 targetPoint, Vec3 targetMotion, double ticks
    ) {
        double dragDisplacement = (1.0D - Math.pow(ARROW_AIR_DRAG, ticks))
                / (1.0D - ARROW_AIR_DRAG);
        if (dragDisplacement < 1.0E-6D) {
            return targetPoint.subtract(origin);
        }
        Vec3 predictedTarget = targetPoint.add(targetMotion.scale(ticks));
        Vec3 displacement = predictedTarget.subtract(origin);
        double gravityDrop = ARROW_AIM_GRAVITY / (1.0D - ARROW_AIR_DRAG)
                * (ticks - dragDisplacement);
        return new Vec3(
                displacement.x / dragDisplacement,
                (displacement.y + gravityDrop) / dragDisplacement,
                displacement.z / dragDisplacement
        );
    }

    private static void ensureMeleeFallback(Human human) {
        HumanData data = human.getData();
        if (data == null) {
            return;
        }
        boolean hasGun = GunSupport.get().isGun(human.getMainHandItem())
                || GunSupport.get().isGun(human.getOffhandItem());
        // Count the hand copy only while custody has temporarily withdrawn the
        // backpack fallback. At every stable gunner state the required melee
        // weapon must occupy an actual HumanData inventory slot.
        boolean hasMelee = GunCustody.isActive(human)
                && HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem());
        int replacementSlot = -1;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stored = data.getInventoryItem(i);
            hasGun |= GunSupport.get().isGun(stored);
            hasMelee |= HumanLootManager.isDedicatedMeleeWeapon(stored);
            if (replacementSlot < 0 && stored.isEmpty()) {
                replacementSlot = i;
            }
        }
        if (!hasGun || hasMelee) {
            return;
        }

        // A firearm owner must always retain one dedicated melee fallback in
        // the backpack. If supplies filled every slot, evict the lowest-value
        // candidate through the centralized valuation policy instead of
        // silently leaving the gunner unable to defend at contact distance.
        if (replacementSlot < 0) {
            replacementSlot = HumanLootManager.lowestMandatoryMeleeEvictionSlot(human);
            if (replacementSlot < 0) {
                return;
            }
            ItemStack evicted = data.getInventoryItem(replacementSlot).copy();
            if (!evicted.isEmpty()) {
                human.spawnAtLocation(evicted);
            }
        }

        ItemStack fallback;
        if (TierThreeHuman.isTierThree(human)) {
            fallback = new ItemStack(Items.DIAMOND_SWORD);
            fallback.getOrCreateTag().putBoolean(TierThreeLoadout.BOUND_GEAR, true);
        } else if (human.getTier() == HumanTier.LEVEL2) {
            fallback = new ItemStack(Items.IRON_SWORD);
        } else if (human.getTier() == HumanTier.LEVEL1) {
            fallback = new ItemStack(Items.STONE_SWORD);
        } else {
            fallback = new ItemStack(Items.IRON_AXE);
        }
        data.setInventoryItem(replacementSlot, fallback);
        human.getPersistentData().putBoolean(MOD_ID + ":melee_fallback_added", true);
    }

    static boolean isRangedWeapon(ItemStack stack) {
        return !stack.isEmpty()
                && (GunSupport.get().isGun(stack)
                || stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof TridentItem
                || SpartanEquipmentCompat.isSpartanRangedWeapon(stack));
    }

    private static void configureTierRivalry(Human human) {
        HumanTier tier = human.getTier();
        if (TierThreeHuman.isTierThree(human)) {
            human.team = "human_level3";
        } else if (tier == HumanTier.LEVEL1) {
            human.team = "human_level1";
        } else if (tier == HumanTier.LEVEL2) {
            human.team = "human_level2";
        } else if (tier == HumanTier.ROAMER) {
            // Hostile Humans uses this field for its own attack eligibility.
            // A UUID-backed faction also repairs old entities whose saved team
            // accidentally matches another roamer.
            human.team = "human_roamer_" + human.getUUID();
            return;
        } else {
            return;
        }

        if (!RIVALRY_CONFIGURED.add(human)) {
            return;
        }
        human.addTargetGoal(
                1,
                new NearestAttackableTargetGoal<>(
                        human,
                        Human.class,
                        10,
                        true,
                        false,
                        candidate -> candidate instanceof Human other && areTierRivals(human, other)
                )
        );
    }

    private static void configureCombatAi(Human human) {
        CombatAiConfig config = CombatAiConfig.get();
        if (!config.enabled() || !COMBAT_AI_CONFIGURED.add(human)) {
            return;
        }

        RecoverySupplies.provisionInitialKit(human, config);

        if (human.getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanOpenDoors(true);
        }

        AttributeInstance movement = human.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            movement.removeModifier(COMBAT_MOVEMENT_MODIFIER_ID);
        }
        MovementSpeedController.normal(human);

        // Survival must be able to pre-empt the hired-order idle MOVE lock
        // (priority -6), even when that order briefly clears the attack target.
        human.addCombatGoal(-8, new AdaptiveCombatGoal(human));
        human.addCombatGoal(-3, new PlayerLikeMovementGoal(human));
        human.addCombatGoal(-3, new CombatConsumableGoal(human));
        human.addCombatGoal(-2, new CombatFoodRecoveryGoal(human));
        human.addCombatGoal(-2, new IdleRecoveryGoal(human));
        // Item search must briefly outrank the hired-order idle movement lock;
        // its own predicate keeps it disabled unless the owner opted in.
        human.addCombatGoal(-7, new ValuableItemPickupGoal(human));
        // Emergency lava escape must override combat and all hired movement orders.
        human.addCombatGoal(-12, new LavaEscapeGoal(human));
        human.addCombatGoal(1, new IdleLeaveWaterGoal(human));
    }

    private static void configureSoldierOrders(Human human) {
        if (!SOLDIER_ORDERS_CONFIGURED.add(human)) return;
        human.addCombatGoal(-6, new SoldierOrderGoal(human));
    }

    private static boolean areTierRivals(Human first, Human second) {
        if (HumanRelations.effectiveOwner(first) != null || HumanRelations.effectiveOwner(second) != null) {
            return false;
        }
        int firstTier = factionTier(first);
        int secondTier = factionTier(second);
        return firstTier > 0 && secondTier > 0 && firstTier != secondTier;
    }

    public static boolean areSameFaction(Human first, Human second) {
        // Roamers intentionally use hostile, individual factions. Some legacy
        // saves or spawn paths can temporarily give two roamers the same team
        // string, but that must never grant friendly-fire immunity.
        if (first.getTier() == HumanTier.ROAMER || second.getTier() == HumanTier.ROAMER) {
            return false;
        }
        return !first.team.isEmpty() && first.team.equals(second.team);
    }

    public static boolean areCombatAllies(Human first, Human second) {
        return shouldBlockHumanDamage(first, second);
    }

    /** Used by the base entity's attack gates without exposing relationship internals. */
    public static boolean canControlledHumanAttack(Human attacker, LivingEntity target) {
        if (!SoldierCombatMode.hasCombatController(attacker)) return true;
        if (target instanceof Player || (target instanceof Human other
                && SoldierCombatMode.hasCombatController(other))) {
            return !HumanRelations.allied(attacker, target);
        }
        return true;
    }

    /** A wild human, including a beacon wave, can answer a same-tier attacker. */
    public static boolean canWildHumanRetaliateAgainst(Human wild, LivingEntity attacker) {
        return HumanRelations.isRecentHumanAttacker(wild, attacker);
    }

    static boolean shouldBlockHumanDamage(Human attacker, Human defender) {
        // A wild same-tier human must be able to land the initiating hit on a
        // hired/temporary human. The defender can then create a real self-
        // defense authorization. The reverse direction remains blocked until
        // the owned human has such an authorization.
        return HumanRelationshipPolicy.blockHumanDamage(
                HumanRelations.effectiveOwner(attacker) != null,
                HumanRelations.effectiveOwner(defender) != null,
                HumanRelations.allied(attacker, defender));
    }

    private static int factionTier(Human human) {
        if (TierThreeHuman.isTierThree(human)) {
            return 3;
        }
        if (human.getTier() == HumanTier.LEVEL1) {
            return 1;
        }
        if (human.getTier() == HumanTier.LEVEL2) {
            return 2;
        }
        return 0;
    }

    private static boolean isDroppedGun(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (GunSupport.get().isGun(stack)) {
            return true;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId.getNamespace().equals("pillagers_gun")
                && PILLAGERS_GUN_FIREARMS.contains(itemId.getPath());
    }
}
