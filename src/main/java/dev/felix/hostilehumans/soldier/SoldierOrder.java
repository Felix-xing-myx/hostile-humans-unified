package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.behavior.combat.retreat.RetreatPathSafety;
import dev.felix.hostilehumans.faction.HumanRelations;
import dev.felix.hostilehumans.inventory.SoldierPickupPolicy;
import dev.felix.hostilehumans.navigation.BasicNavigationBudget;
import dev.felix.hostilehumans.navigation.GarrisonReturnNavigation;
import dev.felix.hostilehumans.navigation.HumanTerrainSafety;
import dev.felix.hostilehumans.navigation.LongRangeNavigation;
import dev.felix.hostilehumans.navigation.MovementContinuity;
import dev.felix.hostilehumans.navigation.MovementSpeedController;
import dev.felix.hostilehumans.navigation.PursuitNavigation;
import dev.felix.hostilehumans.navigation.SafePositions;
import dev.felix.hostilehumans.navigation.SoldierFollowNavigation;
import dev.felix.hostilehumans.navigation.SoldierStuckRecovery;
import dev.felix.hostilehumans.observer.NavigationDebugService;
import dev.felix.hostilehumans.perception.HumanPerception;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Persistent movement policy. Legacy ordinals remain stable; new writes use three logical modes. */
public enum SoldierOrder {
    HOLD_POSITION, GUARD, FOLLOW, PATROL, BODYGUARD, GARRISON;
    private static final Map<Human, Path> ROUTES = new WeakHashMap<>();
    private static final Map<Human, Vec3> RETURN_POINTS = new WeakHashMap<>();
    static final String ORDER = "hostile_humans_unified:soldier_order";
    public static final String ANCHOR = "hostile_humans_unified:order_anchor";
    public static final String ANCHOR_DIMENSION = "hostile_humans_unified:order_anchor_dimension";
    private static final String PATROL_DESTINATION = "hostile_humans_unified:patrol_destination";
    private static final String NEXT_PATROL = "hostile_humans_unified:next_patrol_move";
    private static final String RETURNING_FROM_RETREAT = "hostile_humans_unified:returning_from_retreat";
    private static final String RETURN_RETRY = "hostile_humans_unified:order_return_retry_at";
    public static SoldierOrder raw(Human h) {
        return switch (h.getPersistentData().getString(ORDER)) {
            case "HOLD_POSITION" -> HOLD_POSITION;
            case "GUARD" -> GUARD;
            case "PATROL" -> PATROL;
            case "BODYGUARD" -> BODYGUARD;
            case "GARRISON" -> GARRISON;
            default -> FOLLOW;
        };
    }
    public static SoldierOrder logical(SoldierOrder order) {
        return order == HOLD_POSITION || order == GUARD || order == PATROL ? GARRISON : order;
    }
    public static SoldierOrder get(Human h) { return logical(raw(h)); }
    public static boolean isGarrison(Human h) { return OrderDistances.context(h).constrained(); }
    public static void set(Human h, SoldierOrder order) { set(h,order,false); }
    static void set(Human h, SoldierOrder requested, boolean preserveNavigation) {
        java.util.Objects.requireNonNull(requested, "order");
        if (!preserveNavigation) HumanBehaviorControl.interruptMovement(h, "soldier movement order changed");
        GarrisonSettings settings = GarrisonSettings.player(h);
        if(requested==HOLD_POSITION) settings=new GarrisonSettings(true,false,settings.radius());
        else if(requested==GUARD) settings=new GarrisonSettings(false,true,settings.radius());
        else if(requested==PATROL) settings=new GarrisonSettings(false,true,settings.radius());
        OwnerOfflinePolicy.clearForDismiss(h); h.setOrderedToSit(false);h.setInSittingPose(false);
        NaturalRoute.pause(h,true); resetMovementState(h); h.guardCombatState.reset();NaturalRoute.settingsChanged(h,false);
        SoldierOrder order=logical(requested);
        if(order==GARRISON) {
            GarrisonSettings.set(h,settings,true);
            boolean resumeDrawn=get(h)!=GARRISON&&h.getPersistentData().getCompound(GarrisonSettings.PLAYER).getBoolean(PatrolRoutes.AUTHORED)
                    &&h.getPersistentData().contains(ANCHOR,net.minecraft.nbt.Tag.TAG_LONG)
                    &&h.level().dimension().location().toString().equals(h.getPersistentData().getString(ANCHOR_DIMENSION));
            if(!resumeDrawn){h.getPersistentData().putLong(ANCHOR,h.blockPosition().asLong());
                h.getPersistentData().putString(ANCHOR_DIMENSION,h.level().dimension().location().toString());
                PatrolRoutes.reached(h,OrderDistances.context(h),h.position());}
        }
        h.getPersistentData().putString(ORDER,order.name());
        h.getPersistentData().remove(RETURNING_FROM_RETREAT);
        if(!preserveNavigation)h.getNavigation().stop();
        if(order!=FOLLOW)h.setTarget(null);
    }
    /** Same write path for single and squad UI. Preserves last garrison settings across mode switches. */
    public static void apply(Human h,SoldierOrder order,GarrisonSettings settings,Integer followDistance) {
        order = order == null ? get(h) : logical(order);
        if(settings!=null)GarrisonSettings.set(h,settings);
        if(followDistance!=null)OrderDistances.setFollow(h,followDistance);
        if(get(h)!=logical(order))set(h,logical(order));
        else if(raw(h)!=logical(order)){
            GarrisonSettings.set(h,GarrisonSettings.player(h));
            h.getPersistentData().putString(ORDER,logical(order).name());
        }
    }
    public static void initialize(Human h) {
        h.setOrderedToSit(false);h.setInSittingPose(false);
        if(!h.getPersistentData().contains(ORDER))set(h,FOLLOW);
    }
    private static void resetMovementState(Human h) {
        LongRangeNavigation.cancelAll(h);PursuitNavigation.clear(h);BodyguardProtection.clear(h);
        GarrisonReturnNavigation.clear(h);
        SoldierFollowNavigation.clear(h);SoldierStuckRecovery.clear(h);ROUTES.remove(h);
        RETURN_POINTS.remove(h);
        h.getPersistentData().remove(PATROL_DESTINATION);h.getPersistentData().remove(NEXT_PATROL);
        h.getPersistentData().remove(RETURN_RETRY);
    }
    public static void clear(Human h) {
        OwnerOfflinePolicy.clearForDismiss(h);resetMovementState(h);h.guardCombatState.reset();
        h.getPersistentData().remove(ORDER);h.getPersistentData().remove(ANCHOR);h.getPersistentData().remove(ANCHOR_DIMENSION);
        h.getPersistentData().remove(PatrolRoutes.PLAYER_CENTER);
        h.getPersistentData().remove(RETURNING_FROM_RETREAT);NaturalRoute.clearPlayerRuntime(h);h.getNavigation().stop();
        // Natural author settings/origin survive hiring and dismissal.
    }
    public static void beginReturnFromRetreat(Human h) {
        if(!h.hasOwner()&&!NaturalState.hasState(h))return;
        h.getPersistentData().putBoolean(RETURNING_FROM_RETREAT,true);h.forgetSoldierTarget();
    }
    public static boolean isReturningFromRetreat(Human h) { return h.getPersistentData().getBoolean(RETURNING_FROM_RETREAT); }
    /** Validation has no navigation side effects. Called every server tick, including combat. */
    public static void tick(Human h) {
        if(!(h.level() instanceof ServerLevel level))return;
        OrderDistances.Context c=OrderDistances.context(h);
        if(!c.constrained()&&h.guardCombatState.returning())h.guardCombatState.reset();
        if(!h.guardCombatState.returning())GarrisonReturnNavigation.clear(h);
        if(!h.hasOwner()&&!c.constrained())return;
        long now=level.getGameTime();LivingEntity target=h.getTarget();
        if(h.hasOwner()&&get(h)==BODYGUARD)BodyguardProtection.update(h);
        if(c.constrained()) {
            boolean valid=target!=null&&target.isAlive()&&target.level()==h.level()
                    &&HumanPerception.canTarget(h,target)&&!HumanRelations.allied(h,target)
                    &&(!h.hasOwner()||h.soldierCombatMemory.allowed(target.getUUID(),now)&&SoldierCombatMode.allowsTarget(h,target));
            h.guardCombatState.tick(now,!c.insideDaily(h.position())&&!NaturalRoute.allowsFollowingPosition(h,h.position()),
                    valid&&c.available()&&c.horizontalDistanceSquared(h.position())>=c.combatRadius()*c.combatRadius(),valid,
                    OrderDistances.defaults().chaseDurationTicks());
            if(!c.available())h.guardCombatState.beginReturn();
            if(c.available()&&h.guardCombatState.returning()&&h.guardCombatState.returnRadius()<0)
                bindReturn(h,c);
            if(target!=null||h.isFleeing||h.isInLava()||h.isSeekingShore()||NavigationDebugService.controls(h))
                NaturalRoute.pause(h,target!=null||h.isFleeing);
        }
        if(target!=null&&(!target.isAlive()||target.level()!=h.level()||HumanRelations.allied(h,target)
                ||!allowsTarget(h,target)||h.hasOwner()&&h.soldierCombatMemory.timedOut(target.getUUID(),now)))
            h.forgetSoldierTargetWithoutMovement();
    }
    public static boolean shouldFollowThroughWater(Human h) {
        if(h.isFleeing||h.isInLava())return false;
        if(h.hasOwner())return (get(h)==FOLLOW||get(h)==BODYGUARD)&&BodyguardProtection.owner(h)!=null;
        if(!h.getPersistentData().hasUUID(HumanRelations.TEMP_OWNER))return false;
        var p=h.level().getPlayerByUUID(h.getPersistentData().getUUID(HumanRelations.TEMP_OWNER));
        return p!=null&&p.isAlive()&&!p.isSpectator();
    }
    static void tickMovement(Human h) {
        if(!(h.level() instanceof ServerLevel level))return;
        OrderDistances.Context c=OrderDistances.context(h);
        if(!h.hasOwner()&&!c.constrained())return;
        if(h.isInLava()||h.isSeekingShore()||h.isFleeing||NavigationDebugService.controls(h)) {NaturalRoute.pause(h,h.isFleeing);return;}
        if(c.constrained()){tickGarrison(h,c);return;}
        ServerPlayer owner=BodyguardProtection.owner(h);SoldierOrder order=get(h);
        LivingEntity target=h.getTarget();
        if(isReturningFromRetreat(h)) {
            if(target!=null)h.forgetSoldierTarget();
            if(owner!=null&&h.distanceToSqr(owner)>16)SoldierFollowNavigation.tick(h,owner);
            else if(owner!=null){h.getPersistentData().remove(RETURNING_FROM_RETREAT);stopMovement(h);}
            return;
        }
        if(order==BODYGUARD){
            if(owner==null){BodyguardProtection.clear(h);if(target==null)stopMovement(h);return;}
            if(h.distanceToSqr(owner)>OrderDistances.followSquared(h)){h.forgetSoldierTarget();teleportBesideOwner(h,owner);}
            BodyguardProtection.update(h);return;
        }
        if(target==null&&h.isActivelyCollectingLoot()&&SoldierPickupPolicy.canCollectNow(h))return;
        if(order==FOLLOW){
            if(owner==null){if(target==null)stopMovement(h);return;}
            if(h.distanceToSqr(owner)>OrderDistances.followSquared(h)){h.forgetSoldierTarget();target=null;teleportBesideOwner(h,owner);}
            if(target!=null&&!allowsTarget(h,target)){h.forgetSoldierTarget();target=null;}
            if(target==null)SoldierFollowNavigation.tick(h,owner);else SoldierFollowNavigation.clear(h);
        }
    }
    private static void tickGarrison(Human h,OrderDistances.Context c) {
        if(!c.available()){stopMovement(h);NaturalRoute.stopOwned(h);NaturalRoute.pause(h,false);return;}
        long now=h.level().getGameTime();
        if(isReturningFromRetreat(h))h.guardCombatState.beginReturn();
        if(h.guardCombatState.returning()&&h.guardCombatState.returnRadius()<0)
            bindReturn(h,c);
        if(h.guardCombatState.returning()) {
            Vec3 destination=RETURN_POINTS.getOrDefault(h,NaturalRoute.returnPoint(h,c));
            if(GarrisonReturnNavigation.tick(h,c,destination)){
                GarrisonReturnNavigation.clear(h);
                h.guardCombatState.returnedToPost(now,OrderDistances.defaults().chaseReturnCooldownTicks());
                RETURN_POINTS.remove(h);
                h.getPersistentData().remove(RETURNING_FROM_RETREAT);stopMovement(h);NaturalRoute.pause(h,true);
            }
            return;
        }
        if(h.getTarget()!=null)return;
        if(c.settings().stay()) {
            if(!NaturalRoute.reached(h,c.origin(),OrderDistances.defaults().returnDistance())) {
                if(h.tickCount%5==0||h.getNavigation().isDone())moveToPost(h,BlockPos.containing(c.origin()),OrderDistances.defaults().walkSpeed(),true);
            }else stopMovement(h);
            return;
        }
        MovementSpeedController.normal(h);
        Path idleRoute=ROUTES.get(h);
        if(idleRoute!=null&&MovementContinuity.ownsRoute(h,idleRoute)&&!allowsDailyPath(h,idleRoute))stopMovement(h);
        if(h.isActivelyCollectingLoot()&&SoldierPickupPolicy.canCollectNow(h))return;
        if(!c.insideDaily(h.position())&&!NaturalRoute.allowsFollowingPosition(h,h.position())){h.guardCombatState.beginReturn();return;}
        if(NaturalRoute.tick(h,c))return;
        if(h.getNavigation().isDone()&&now>=h.getPersistentData().getLong(NEXT_PATROL)) {
            if(!BasicNavigationBudget.tryAcquire(h,4))return;
            try {
                int paths=0,r=c.settings().radius();
                for(int attempt=0;attempt<8&&paths<4;attempt++) {
                    int dx=h.getRandom().nextInt(r*2+1)-r,dz=h.getRandom().nextInt(r*2+1)-r;
                    if(dx*dx+dz*dz>r*r)continue;
                    BlockPos destination=SafePositions.nearFloor((ServerLevel)h.level(),(int)Math.floor(c.origin().x)+dx,
                            (int)Math.floor(c.origin().z)+dz,(int)Math.floor(c.origin().y));
                    if(destination==null||!h.level().hasChunkAt(destination))continue;
                    paths++;if(moveToPostWithGrant(h,destination,OrderDistances.defaults().walkSpeed(),false))break;
                }
            }finally{BasicNavigationBudget.cancel(h);}
            h.getPersistentData().putLong(NEXT_PATROL,now+80+h.getRandom().nextInt(81));
        }
    }
    public static void stopMovement(Human h) {
        GarrisonReturnNavigation.stopOwned(h);
        SoldierFollowNavigation.stopOwned(h);Path route=ROUTES.remove(h);
        if(MovementContinuity.ownsRoute(h,route))h.getNavigation().stop();BasicNavigationBudget.cancel(h);
    }
    private static void moveToPost(Human h,BlockPos pos,double speed,boolean returning) {
        long now=h.level().getGameTime(),retry=h.getPersistentData().getLong(RETURN_RETRY);
        if(now<retry&&retry<=now+OrderDistances.defaults().routeFailureBackoffTicks())return;
        if(!h.level().hasChunkAt(pos)||!BasicNavigationBudget.tryAcquire(h,1))return;
        try {
            if(moveToPostWithGrant(h,pos,speed,returning))h.getPersistentData().putLong(RETURN_RETRY,now+10);
            else h.getPersistentData().putLong(RETURN_RETRY,now+OrderDistances.defaults().routeFailureBackoffTicks());
        }finally{BasicNavigationBudget.cancel(h);}
    }
    private static boolean moveToPostWithGrant(Human h,BlockPos pos,double speed,boolean returning) {
        Path path=h.getNavigation().createPath(pos,0);
        return installPostPath(h,path,pos,speed,returning);
    }
    public static boolean installPostPath(Human h,Path path,BlockPos pos,double speed,boolean returning) {
        if(path==null||!path.canReach()||!HumanTerrainSafety.safePath(h,path)
                ||(returning?!allowsReturnPath(h,path):!allowsDailyPath(h,path)))return false;
        if(!RetreatPathSafety.tryMove(h,path,speed))return false;
        ROUTES.put(h,h.getNavigation().getPath());h.getPersistentData().putLong(PATROL_DESTINATION,pos.asLong());return true;
    }
    public static boolean allowsCombatPath(Human h,Path path) {return allowsPath(h,path,false);}
    public static boolean allowsDailyPath(Human h,Path path) {return allowsPath(h,path,true);}
    private static double returnRadius(Human h,OrderDistances.Context c) {
        return h.guardCombatState.returnRadius()>=0?h.guardCombatState.returnRadius():
                initialReturnRadius(h,c);
    }
    private static double initialReturnRadius(Human h,OrderDistances.Context c) {
        return Math.max(Math.sqrt(c.horizontalDistanceSquared(h.position())),
                Math.sqrt(c.horizontalDistanceSquared(NaturalRoute.returnPoint(h,c))))+OrderDistances.defaults().leaveThreshold();
    }
    private static void bindReturn(Human h,OrderDistances.Context c) {
        Vec3 destination=NaturalRoute.returnPoint(h,c);RETURN_POINTS.put(h,destination);
        h.guardCombatState.bindReturnRadius(Math.max(Math.sqrt(c.horizontalDistanceSquared(h.position())),
                Math.sqrt(c.horizontalDistanceSquared(destination)))+OrderDistances.defaults().leaveThreshold());
    }
    public static boolean allowsReturnPath(Human h,Path path) {
        OrderDistances.Context c=OrderDistances.context(h);
        if(!c.available()||path==null||path.isDone())return false;
        double radius=returnRadius(h,c);
        for(int i=path.getNextNodeIndex();i<path.getNodeCount();i++)
            if(c.horizontalDistanceSquared(Vec3.atBottomCenterOf(path.getNodePos(i)))>radius*radius)return false;
        return true;
    }
    /** Purpose dispatcher for follower shortcuts/local repair, retaining existing safety/test ownership. */
    public static boolean allowsNavigationPosition(Human h,Vec3 point) {
        if(NaturalRoute.allowsFollowingPosition(h,point))return true;
        if(NavigationDebugService.controls(h)||h.isFleeing||h.isInLava()||h.isSeekingShore())return true;
        OrderDistances.Context c=OrderDistances.context(h);
        if(c.constrained()&&(h.guardCombatState.returning()||c.settings().stay()&&h.getTarget()==null)) {
            double radius=returnRadius(h,c);return c.available()&&c.horizontalDistanceSquared(point)<=radius*radius;
        }
        if(h.isActivelyCollectingLoot())return allowsLootPosition(h,point.x,point.z);
        return h.getTarget()!=null?allowsCombatPosition(h,point):allowsDailyPosition(h,point);
    }
    private static boolean allowsPath(Human h,Path path,boolean daily) {
        if(path==null||path.isDone())return false;
        for(int i=path.getNextNodeIndex();i<path.getNodeCount();i++) {
            Vec3 p=Vec3.atBottomCenterOf(path.getNodePos(i));
            if(daily?!allowsDailyPosition(h,p):!allowsCombatPosition(h,p))return false;
        }return true;
    }
    public static boolean allowsTarget(Human h,LivingEntity target) {
        if(target==null||!target.isAlive()||target.level()!=h.level())return false;
        OrderDistances.Context c=OrderDistances.context(h);
        if(c.constrained()) {
            if(!c.available())return false;
            if(h.guardCombatState.returning()) {
                // Return never permits pursuit, but a confirmed recent shooter can be answered at weapon range.
                double defenseReach=Math.max(6,BodyguardProtection.rangedReach(h));
                return isRecentSelfAttacker(h,target)&&h.distanceToSqr(target)<=defenseReach*defenseReach
                        &&(!h.hasOwner()||h.soldierCombatMemory.allowed(target.getUUID(),h.level().getGameTime())
                        &&SoldierCombatMode.allowsTarget(h,target));
            }
            boolean outside=!c.insideDaily(target.position());
            if(outside&&(!c.settings().chase()||!h.guardCombatState.canExcursion(h.level().getGameTime())))return false;
            if(!c.insideCombat(target.position()))return false;
            if(!h.hasOwner())return true; // wild perception/relationship remain in their existing callers
        }
        if(!h.hasOwner()||h.level().isClientSide)return true;
        if(SoldierStuckRecovery.recovering(h)&&!BodyguardProtection.protectingFromExplosion(h,target))return false;
        boolean rescue=BodyguardProtection.protectingFromExplosion(h,target);
        if((!rescue&&!h.soldierCombatMemory.allowed(target.getUUID(),h.level().getGameTime()))||!SoldierCombatMode.allowsTarget(h,target))return false;
        if(isReturningFromRetreat(h)&&!rescue)return false;
        SoldierOrder order=get(h);
        if(order==BODYGUARD)return rescue||BodyguardProtection.allowsTarget(h,target);
        if(order==FOLLOW){Player owner=BodyguardProtection.owner(h);double reach=BodyguardProtection.rangedReach(h);
            return owner==null||h.distanceToSqr(owner)<=OrderDistances.followSquared(h)
                    &&(target.distanceToSqr(owner)<=OrderDistances.followSquared(h)
                    ||BodyguardProtection.ranged(h)&&h.distanceToSqr(target)<=reach*reach);}
        return true;
    }
    /** Release MOVE to the weapon Goal; all combat movement remains forbidden while returning. */
    public static boolean yieldsReturnToDefense(Human h) {
        LivingEntity target=h.getTarget();
        return h.guardCombatState.returning()&&target!=null&&allowsTarget(h,target);
    }
    public static boolean allowsCombatPosition(Human h,Vec3 point) {
        OrderDistances.Context c=OrderDistances.context(h);
        if(c.constrained()) {
            if(!c.available()||h.guardCombatState.returning()||isReturningFromRetreat(h))return false;
            if(c.settings().stay()&&!c.settings().chase())return false;
            return c.insideDaily(point)||c.settings().chase()&&h.guardCombatState.canExcursion(h.level().getGameTime())&&c.insideCombat(point);
        }
        if(!h.hasOwner())return true;
        Player owner=BodyguardProtection.owner(h);
        return owner==null?get(h)!=BODYGUARD:owner.distanceToSqr(point)<=OrderDistances.followSquared(h);
    }
    /** Sound, pickup and idle patrol never borrow the combat excursion ring. */
    public static boolean allowsDailyPosition(Human h,Vec3 point) {
        OrderDistances.Context c=OrderDistances.context(h);
        if(c.constrained())return c.available()&&!c.settings().stay()&&!h.guardCombatState.returning()&&c.insideDaily(point);
        if(!h.hasOwner())return true;
        Player owner=BodyguardProtection.owner(h);return owner==null?get(h)!=BODYGUARD:owner.distanceToSqr(point)<=OrderDistances.followSquared(h);
    }
    public static boolean isHoldingPosition(Human h) {
        OrderDistances.Context c=OrderDistances.context(h);
        if(c.constrained())return !c.available()||h.guardCombatState.returning()||c.settings().stay()
                &&(!c.settings().chase()||h.getTarget()==null||!h.guardCombatState.canExcursion(h.level().getGameTime()));
        Player owner=BodyguardProtection.owner(h);LivingEntity target=h.getTarget();
        return h.hasOwner()&&owner!=null&&target!=null&&(get(h)==BODYGUARD&&target.distanceToSqr(owner)>BodyguardPolicy.CHASE_SQUARED
                ||get(h)==FOLLOW&&target.distanceToSqr(owner)>OrderDistances.followSquared(h));
    }
    public static void onSelfAttacked(Human h,LivingEntity attacker) {
        if(attacker==null||!isGarrison(h)||HumanRelations.allied(h,attacker))return;
        long now=h.level().getGameTime();h.guardCombatState.attacked(attacker.getUUID(),now,!OrderDistances.context(h).insideDaily(h.position()));
        if(h.hasOwner())h.soldierCombatMemory.threatened(attacker.getUUID(),now);
    }
    public static boolean allowsLootPosition(Human h,double x,double z) {
        if(isHoldingPosition(h))return false;
        return allowsDailyPosition(h,new Vec3(x,h.getY(),z))&&allowsDailyPosition(h,h.position());
    }
    public static boolean isReturningToHoldPosition(Human h) {
        OrderDistances.Context c=OrderDistances.context(h);
        return c.constrained()&&c.available()&&c.settings().stay()
                &&!NaturalRoute.reached(h,c.origin(),OrderDistances.defaults().returnDistance());
    }
    public static BlockPos recoveryPost(Human h) {
        OrderDistances.Context c=OrderDistances.context(h);
        return c.constrained()?BlockPos.containing(NaturalRoute.returnPoint(h,c)):h.blockPosition();
    }
    private static boolean isRecentSelfAttacker(Human h,LivingEntity target) {
        int since=h.tickCount-h.getLastHurtByMobTimestamp();return target==h.getLastHurtByMob()&&since>=0&&since<=40;
    }
    public static boolean teleportBesideOwner(Human human, ServerPlayer owner) {
        if (human.tickCount % 10 != 0 || owner.level() != human.level()
                || human.isFleeing || human.isInLava() || human.isPassenger()) return false;
        for (int attempt = 0; attempt < 12; attempt++) {
            int dx = human.getRandom().nextInt(7) - 3;
            int dz = human.getRandom().nextInt(7) - 3;
            var pos = SafePositions.nearOwner(human, owner, owner.getBlockX() + dx, owner.getBlockZ() + dz);
            if (pos == null) continue;
            SoldierFollowNavigation.clear(human);
            human.teleportTo(pos.x, pos.y, pos.z);
            human.getNavigation().stop();
            return true;
        }
        // No blind fallback into a block or onto the surface above a cave.
        return false;
    }
}
