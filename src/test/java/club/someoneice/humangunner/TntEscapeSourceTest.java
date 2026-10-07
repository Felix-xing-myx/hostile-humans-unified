package club.someoneice.humangunner;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source-only acceptance and emergency fallback checks; never loads Minecraft classes. */
public final class TntEscapeSourceTest {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/craftix/hostile_humans/entity/ai/goal/AvoidTNTGoal.java"));
        String selection = source.substring(source.indexOf("boolean canUse()"), source.indexOf("private boolean unsafe()"));
        check(selection.contains("return true;") && !selection.contains("searchRoute()")
                && !selection.contains("nextSearchTick"), "threat enters escape without needing a route or budget");
        String search = source.substring(source.indexOf("private boolean searchRoute()"), source.indexOf("boolean canContinueToUse()"));
        check(search.indexOf("OptionalPathBudget.claim") < search.indexOf("if (budget == 0)")
                && search.indexOf("if (budget == 0)") < search.indexOf("retries++"), "queued budget never consumes route retries");
        check(search.indexOf("navigation.moveTo(replacement") < search.indexOf("path = accepted")
                && search.contains("navigation.moveTo(previous, sprintSpeedModifier)"), "replacement acceptance precedes ownership and rejection preserves old route");
        check(search.contains("OptionalPathBudget.cancel") && search.contains("finally"), "actual budget work always releases its queue entry");
        check(source.contains("mob.tickCount < fallbackUntilTick")
                && source.contains("fallbackUntilTick = mob.tickCount + 100"), "route-less fallback has a finite deadline");
        check(source.contains("continueWaterRetreatFallback(toAvoid, sprintSpeedModifier)"), "water fallback accepts the TNT Entity threat");
        check(source.contains("expandTowards(offset)") && source.contains("offset.scale(0.5D)")
                && source.contains("isFaceSturdy") && source.contains("getFluidState")
                && source.contains("getBlockPathType") && source.contains("BlockTags.FIRE"), "land fallback checks swept collisions, both supports, fluids and hazards");
        check(!source.contains("human.toAvoid ="), "TNT preserves combat threat ownership");
        System.out.println("TntEscapeSourceTest: 8 source checks passed");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
