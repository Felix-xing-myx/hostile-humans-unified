package dev.felix.hostilehumans.loot.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;

/** Client-only display/search catalog. Resource IDs are never localized in saved loot JSON. */
@Mod.EventBusSubscriber(modid=HumanRuntime.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class LootItemCatalog {
    public record Entry(ResourceLocation id, ItemStack icon, String name, String english, String mod, String terms) {
        public ItemStack icon(){return icon.copy();}
        public Component title(){return Component.literal("["+id.getNamespace()+"] "+name);}
        public String detail(){return english.equals(name)?id.toString():english+" · "+id;}
    }
    private static Map<ResourceLocation,Entry> entries=Map.of();
    private static List<Entry> choices=List.of();
    private static java.util.function.Supplier<? extends Collection<ItemStack>> variantSource=List::of;
    private static String language="";
    private static ClientLanguage english;
    private LootItemCatalog(){}
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) resources->{
            entries=Map.of();choices=List.of();english=null;language="";
        });
    }
    /** Optional integrations expose native examples without leaking their own types into core UI. */
    public static void variants(java.util.function.Supplier<? extends Collection<ItemStack>> source){
        variantSource=Objects.requireNonNull(source);entries=Map.of();choices=List.of();language="";
    }
    private static void ensure(){
        var mc=Minecraft.getInstance();String selected=mc.getLanguageManager().getSelected();
        if(!entries.isEmpty()&&selected.equals(language))return;
        if(english==null)english=ClientLanguage.loadFrom(mc.getResourceManager(),List.of("en_us"),false);
        Map<ResourceLocation,Entry> loaded=new LinkedHashMap<>();
        for(var id:ForgeRegistries.ITEMS.getKeys().stream().sorted().toList()){
            var item=ForgeRegistries.ITEMS.getValue(id);if(item==null)continue;
            ItemStack icon=item.getDefaultInstance();if(icon.isEmpty())continue;String name=icon.getHoverName().getString();
            String en=english.getOrDefault(item.getDescriptionId(),name);
            String mod=ModList.get().getModContainerById(id.getNamespace()).map(c->c.getModInfo().getDisplayName()).orElse(id.getNamespace());
            loaded.put(id,new Entry(id,icon,name,en,mod,(id+" "+name+" "+en+" "+mod).toLowerCase(Locale.ROOT)));
        }
        Map<String,Entry> unique=new LinkedHashMap<>();
        for(var base:loaded.values())unique.put(variantKey(base.id(),base.icon()),base);
        int count=0;Set<ResourceLocation> representedTypes=new HashSet<>();
        for(var example:variantSource.get()){
            if(++count>32000)break;
            if(example.isEmpty())continue;
            var id=ForgeRegistries.ITEMS.getKey(example.getItem());var base=loaded.get(id);if(base==null)continue;
            if(example.hasTag()&&example.getTag().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16384)continue;
            if(representedTypes.add(id))unique.remove(variantKey(id,base.icon()));
            String key=variantKey(id,example);
            if(!unique.containsKey(key)){var icon=example.copy();icon.setCount(1);unique.put(key,describeKnown(base,icon));}
        }
        entries=Collections.unmodifiableMap(loaded);choices=List.copyOf(unique.values());language=selected;
    }
    public static Entry entry(String raw){
        var id=ResourceLocation.tryParse(raw);if(id==null)return null;ensure();return entries.get(id);
    }
    public static Entry describe(ItemStack stack){
        if(stack.isEmpty())return null;
        var id=ForgeRegistries.ITEMS.getKey(stack.getItem());if(id==null)return null;
        var base=entry(id.toString());if(base==null)return null;
        return describeKnown(base,stack);
    }
    private static String variantKey(ResourceLocation id,ItemStack stack){return id+"|"+(stack.hasTag()?stack.getTag():"{}");}
    private static Entry describeKnown(Entry base,ItemStack stack){
        String name=stack.getHoverName().getString(),en=base.english();
        if(stack.getHoverName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text&&text.getArgs().length==0)
            en=english.getOrDefault(text.getKey(),name);
        return new Entry(base.id(),stack.copy(),name,en,base.mod(),(base.id()+" "+name+" "+en+" "+base.mod()).toLowerCase(Locale.ROOT));
    }
    public static List<Entry> search(String query){
        ensure();String[] terms=query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        return choices.stream().filter(e->Arrays.stream(terms).allMatch(term->term.startsWith("@")?(e.id().getNamespace()+" "+e.mod()).toLowerCase(Locale.ROOT).contains(term.substring(1)):e.terms().contains(term))).toList();
    }
    public static Component name(String id){var entry=entry(id);return entry==null?Component.literal(id):entry.title();}
}
