package club.someoneice.humangunner;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;

/** Both combat and inspection consume the same pure resolver; inspection never repairs the input. */
public final class AttributeDetailGrowthViewTest {
    public static void main(String[] args) {
        GrowthConfig config=new UnifiedConfig(new JsonObject()).growth();
        CompoundTag saved=new CompoundTag();saved.putInt("level",2);saved.putInt("experience_percent",105);
        saved.putDouble("stage_progress",17.5);CompoundTag before=saved.copy();
        var layers=HumanGrowth.layers(saved,config);
        check(layers.known(),"saved state known");
        close(layers.healthMultiplier(),config.stagePercent(2)/100*1.05,"health shared layers");
        close(layers.damageMultiplier(),config.damageStagePercent(2)/100*1.05,"damage shared layers");
        close(layers.gunDamage(),config.gunDamageMultiplier(105),"independent firearm experience");
        check(saved.equals(before),"valid saved state unchanged");
        saved.putInt("level",99);saved.putInt("experience_percent",999);before=saved.copy();
        var capped=HumanGrowth.layers(saved,config);
        close(capped.experience(),config.experienceCapPercent()/100,"view uses runtime cap");
        check(saved.equals(before),"view does not persist its clamp");
        check(!HumanGrowth.layers(null,config).known(),"absent state remains unknown");
        System.out.println("AttributeDetailGrowthViewTest: all checks passed");
    }
    private static void close(double value,double expected,String message){check(Math.abs(value-expected)<1e-9,message);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
