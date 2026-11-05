package dev.felix.hostilehumans.loot.client;

import com.google.gson.*;
import dev.felix.hostilehumans.client.ui.framework.InputValue;
import dev.felix.hostilehumans.client.ui.framework.PanelButton;
import dev.felix.hostilehumans.client.ui.framework.PanelGeometry;
import dev.felix.hostilehumans.client.ui.framework.PanelGroup;
import dev.felix.hostilehumans.client.ui.framework.PanelItemRow;
import dev.felix.hostilehumans.client.ui.framework.UiRect;
import dev.felix.hostilehumans.client.ui.framework.PanelScreen;
import dev.felix.hostilehumans.client.ui.framework.UiLayout;
import dev.felix.hostilehumans.loot.LootDocuments;
import dev.felix.hostilehumans.loot.LootEditorNetwork;
import dev.felix.hostilehumans.loot.LootSimpleDraft;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Lossless tree editor with small chest-pool shortcuts; no client authority over loot rolls. */
public final class LootEditorScreen extends PanelScreen {
    private final UUID token;
    private CompoundTag view;
    private JsonObject draft = LootDocuments.empty();
    private final List<String> path = new ArrayList<>();
    private final InputValue<String> search = InputValue.text("",128,false), id = InputValue.text("hhu_author:chests/new_table",256,true);
    private InputValue<String> value = InputValue.text("{}",65536,true);
    private final InputValue<String> key = InputValue.text("",128,false), item = InputValue.text("minecraft:bread",256,true);
    private final InputValue<Integer> weight = InputValue.integer(1,1,100000), min = InputValue.integer(1,1,64), max = InputValue.integer(1,1,64);
    private String loadedId = "", revision = "", saved = "", savedId = "hhu_author:chests/new_table", message = "";
    private boolean library = true, waiting, replace, itemList, advanced, guideOpen, showItemId;
    private String guideTopic="basics";
    private static final List<String> GUIDE_TOPICS=List.of("basics","chance","advanced","examples","containers","files");
    private int poolIndex, entryIndex=-1;
    private final InputValue<Integer> rolls=InputValue.integer(1,0,128);
    private PanelButton saveAction, bindAction;
    private final InputValue<String> itemSearch=InputValue.text("",128,false);
    private List<LootItemCatalog.Entry> itemChoices=List.of();
    private PanelItemRow selectorRow;
    private net.minecraft.world.item.ItemStack pickedStack=net.minecraft.world.item.ItemStack.EMPTY;
    private boolean writePickedData;
    private String itemPreviewId="";
    private int itemPreviewDue;
    private String appliedItemSearch="";
    private int itemPage, itemSearchDue;
    private static final int ITEM_PAGE_SIZE=12;
    private int deleteUntil, bindUntil, discardUntil;
    private int ticks, clearUntil;
    private boolean closed;

    private LootEditorScreen(CompoundTag view) {
        super(tr("title"),PanelGeometry.Size.L); this.view = view; token = view.getUUID("token");
        if (view.contains("error")) message = view.getString("error");
        if (view.contains("json")) {
            draft=LootDocuments.parse(view.getString("json"));loadedId=view.getString("id");id.set(loadedId);
            savedId=loadedId;revision=view.getString("revision");library=false;
        }
        saved=canonical();resetValue();resetSimple();
        LootDraftCache.restore(this);
    }
    public static void show(CompoundTag update) {
        var mc = Minecraft.getInstance(); if (mc.level == null) return;
        if (update.getBoolean("opening")) { mc.setScreen(new LootEditorScreen(update)); return; }
        LootEditorScreen screen=mc.screen instanceof LootEditorScreen current?current:LootEditorReturn.parent();
        if(screen!=null&&screen.token.equals(update.getUUID("token")))screen.accept(update);
    }
    private void accept(CompoundTag update) {
        waiting = false;
        for (String name : update.getAllKeys()) view.put(name, update.get(name).copy());
        message = update.contains("error") ? update.getString("error") : update.contains("notice") ? update.getString("notice") : "";
        if (update.contains("export")) message += " · " + update.getString("export");
        if (update.contains("json")) {
            draft = LootDocuments.parse(update.getString("json")); loadedId = update.getString("id"); id.set(loadedId);
            revision = update.getString("revision"); saved = canonical(); savedId=loadedId; path.clear(); library = false; resetValue();resetSimple();
        }
        rebuildWidgets();
    }
    @Override public void removed(){LootDraftCache.save(this);super.removed();}
    JsonObject cacheState(){
        var data=new JsonObject();data.add("draft",draft.deepCopy());
        data.addProperty("picked",pickedStack.isEmpty()?"":pickedStack.save(new CompoundTag()).toString());
        data.addProperty("pickedData",writePickedData);data.addProperty("dirty",dirty());
        data.addProperty("rawValue",value.text());data.addProperty("key",key.text());data.addProperty("rolls",rolls.text());
        data.addProperty("entry",entryIndex);var segments=new JsonArray();path.forEach(segments::add);data.add("path",segments);
        data.addProperty("id",id.text());data.addProperty("loaded",loadedId);data.addProperty("revision",revision);
        data.addProperty("saved",saved);data.addProperty("savedId",savedId);
        data.addProperty("library",library);data.addProperty("advanced",advanced);data.addProperty("pool",poolIndex);
        data.addProperty("item",item.text());data.addProperty("weight",weight.text());data.addProperty("min",min.text());data.addProperty("max",max.text());
        return data;
    }
    void restoreCache(JsonObject data){
        if(view.contains("json")&&!data.get("dirty").getAsBoolean())return;
        draft=LootDocuments.parse(data.get("draft").toString());id.set(data.get("id").getAsString());
        loadedId=data.get("loaded").getAsString();revision=data.get("revision").getAsString();
        saved=data.get("saved").getAsString();savedId=data.get("savedId").getAsString();
        library=data.get("library").getAsBoolean();advanced=data.get("advanced").getAsBoolean();
        path.clear();resetValue();resetSimple();poolIndex=data.get("pool").getAsInt();
        item.set(data.get("item").getAsString());weight.set(data.get("weight").getAsString());min.set(data.get("min").getAsString());max.set(data.get("max").getAsString());
        if(data.has("path")){
            for(var segment:data.getAsJsonArray("path"))path.add(segment.getAsString());
            try{current();}catch(RuntimeException invalid){path.clear();}
            value.set(data.get("rawValue").getAsString());key.set(data.get("key").getAsString());rolls.set(data.get("rolls").getAsString());
            entryIndex=data.get("entry").getAsInt();
        }
        if(data.has("picked")&&!data.get("picked").getAsString().isEmpty())try{
            pickedStack=net.minecraft.world.item.ItemStack.of(TagParser.parseTag(data.get("picked").getAsString()));
            writePickedData=data.get("pickedData").getAsBoolean();
        }catch(com.mojang.brigadier.exceptions.CommandSyntaxException invalid){pickedStack=net.minecraft.world.item.ItemStack.EMPTY;writePickedData=false;}
        view.putBoolean("active",false);message="cache_restored";
    }
    boolean closed(){return closed;}
    private static Component tr(String name, Object... args) { return Component.translatable("loot_editor."+name,args); }
    private UiLayout.Node group(String key){return node(body(new PanelGroup(tr(key),null,this::theme)));}
    private UiLayout.Node note(Component text) { return text(text,()->theme().muted()); }
    private UiLayout.Node button(Component label,Runnable run) {
        var b = body(new PanelButton(label, ignored->run.run(),this::theme).compact(true)); b.active = !waiting; return node(b);
    }
    private boolean hasJei(){return net.minecraftforge.fml.ModList.get().isLoaded("jei");}
    private int jeiSideBudget(){return hasJei()?Math.min(232,Math.max(0,width-400)):0;}
    @Override protected int leftGutter(){return jeiSideBudget()*2/5;}
    @Override protected int rightGutter(){return jeiSideBudget()-leftGutter();}
    @Override protected int bottomGutter(){return hasJei()?26:0;}
    public UiRect editorBounds(){return frame;}
    public List<UiRect> itemDropAreas(){
        if(waiting||library||selectorRow==null)return List.of();
        int left=Math.max(selectorRow.getX(),viewport.x()),top=Math.max(selectorRow.getY(),viewport.y());
        int right=Math.min(selectorRow.getX()+selectorRow.getWidth(),viewport.right());
        int bottom=Math.min(selectorRow.getY()+selectorRow.getHeight(),viewport.bottom());
        return right>left&&bottom>top?List.of(new UiRect(left,top,right-left,bottom-top)):List.of();
    }
    /** Ghost selection is a local author draft: item data may be referenced, no inventory transfer or server request. */
    public boolean acceptGhostItem(net.minecraft.world.item.ItemStack stack){
        if(itemDropAreas().isEmpty())return false;
        return selectItem(stack);
    }
    private boolean selectItem(net.minecraft.world.item.ItemStack stack){
        if(waiting||library||selectorRow==null||stack.isEmpty())return false;
        var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if(id==null||LootItemCatalog.entry(id.toString())==null)return false;
        if(stack.hasTag()&&stack.getTag().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16384){message="stack_data_large";rebuildWidgets();return false;}
        pickedStack=stack.copy();pickedStack.setCount(1);writePickedData=true;
        item.set(id.toString());itemList=false;message="item_selected";rebuildWidgets();return true;
    }
    private UiLayout.Node itemButton(LootItemCatalog.Entry item,Component detail,Runnable run){return itemButton(item,detail,false,run);}
    private UiLayout.Node itemButton(LootItemCatalog.Entry item,Component detail,boolean selected,Runnable run){
        var row=body(new PanelItemRow(item.icon(),item.title(),Component.literal(item.detail()),detail,b->run.run(),this::theme));
        row.selected(selected);row.active=!waiting;return node(row);
    }
    private void treeNode(List<UiLayout.Node> nodes,String key,JsonElement value,Runnable run){
        var item=LootDisplayText.item(value);
        if(item!=null)nodes.add(itemButton(item,Component.empty(),run));
        else nodes.add(button(LootDisplayText.field(key).copy().append(" : "+LootDisplayText.summary(value)),run));
    }
    @Override protected boolean fieldsEnabled() { return !waiting; }
    private String canonical() { return LootDocuments.JSON.toJson(draft); }
    private boolean dirty() { return !canonical().equals(saved) || !id.text().equals(savedId); }
    private boolean mayDiscard() {
        if (!dirty() || ticks < discardUntil) return true;
        discardUntil = ticks+100; message = "discard_confirm"; rebuildWidgets(); return false;
    }
    private void send(String action, String target, String expected, String json, int page, boolean replace) {
        if (waiting) return; waiting = true; deleteUntil = bindUntil = 0;
        LootEditorNetwork.request(new LootEditorNetwork.Request(token,action,target,expected,json,search.text(),page,replace)); rebuildWidgets();
    }
    @Override protected boolean rightAlignBandLast(){return true;}
    @Override protected void buildBand() {
        band(new PanelButton(tr("library"), b->{guideOpen=false;library=true; rebuildWidgets();},this::theme).selected(!guideOpen&&library));
        band(new PanelButton(tr("draft"), b->{guideOpen=false;library=false;advanced=false;resetSimple();rebuildWidgets();},this::theme).selected(!guideOpen&&!library&&!advanced));
        band(new PanelButton(tr("advanced_tab"), b->{guideOpen=false;library=false;advanced=true;entryIndex=-1;path.clear();resetValue();rebuildWidgets();},this::theme).selected(!guideOpen&&!library&&advanced));
        band(new PanelButton(tr("guide.tab"),b->openGuide(guideTopic),this::theme).selected(guideOpen));
        PanelButton clear=band(new PanelButton(tr(ticks<clearUntil?"cache_confirm":"cache_clear"),b->{
            if(ticks>=clearUntil){clearUntil=ticks+80;rebuildWidgets();return;}
            draft=LootDocuments.empty();loadedId=revision="";id.set("hhu_author:chests/new_table");saved=canonical();savedId=id.text();
            path.clear();resetValue();resetSimple();clearUntil=0;message="cache_cleared";LootDraftCache.save(this);rebuildWidgets();
        },this::theme).danger(true));
        clear.active=!waiting;
    }
    private void openGuide(String topic){guideTopic=topic;guideOpen=true;scrollReset();rebuildWidgets();}
    private UiLayout.Node guideContent(){
        List<UiLayout.Node> nodes=new ArrayList<>();
        nodes.add(note(tr("guide.intro")));
        for(int row=0;row<GUIDE_TOPICS.size();row+=3){
            List<UiLayout.Node> choices=new ArrayList<>();
            for(String topic:GUIDE_TOPICS.subList(row,Math.min(row+3,GUIDE_TOPICS.size())))
                choices.add(node(body(new PanelButton(tr("guide."+topic+".title"),b->openGuide(topic),this::theme).compact(true).selected(guideTopic.equals(topic)))));
            nodes.add(UiLayout.row(6,choices));
        }
        nodes.add(group("guide."+guideTopic+".title"));
        for(int i=1;i<=6;i++)nodes.add(note(tr("guide."+guideTopic+"."+i)));
        return UiLayout.column(8,nodes);
    }
    @Override protected UiLayout.Node buildContent() {
        selectorRow=null;
        if(guideOpen)return guideContent();
        List<UiLayout.Node> nodes = new ArrayList<>();
        if (!message.isEmpty()) nodes.add(text((net.minecraft.client.resources.language.I18n.exists("loot_editor."+message)?tr(message):Component.literal(message)),()->theme().accent()));
        if (waiting) nodes.add(note(tr("waiting")));
        if (view.contains("position")) {
            var p = NbtUtils.readBlockPos(view.getCompound("position"));
            nodes.add(note(tr("container",p.getX(),p.getY(),p.getZ(),view.getInt("halves"),view.getString("containerTable"))));
        }
        if (library) {
            nodes.add(group("library_group"));
            nodes.add(field(tr("search"), search));
            nodes.add(button(tr("search_apply"),()->send("list","","","",0,false)));
            nodes.add(button(tr("external_reload"),()->{if(!dirty())send("reload",loadedId,"","",0,false);else{message="save_first";rebuildWidgets();}}));
            nodes.add(button(tr("new"),()->{
                if (!mayDiscard()) return;
                draft=LootDocuments.empty(); path.clear(); id.set("hhu_author:chests/new_table"); loadedId=""; revision=""; saved="";
                library=false;advanced=false;resetValue();resetSimple();rebuildWidgets();
            }));
            nodes.add(note(tr("page",view.getInt("page")+1,view.getInt("pages"),view.getInt("total"))));
            List<UiLayout.Node> paging = new ArrayList<>();
            if(view.getInt("page")>0) paging.add(button(tr("previous"),()->send("list","","","",view.getInt("page")-1,false)));
            if(view.getInt("page")+1<view.getInt("pages")) paging.add(button(tr("next"),()->send("list","","","",view.getInt("page")+1,false)));
            if(!paging.isEmpty()) nodes.add(UiLayout.row(6,paging));
            for (var entry : view.getList("ids",Tag.TAG_STRING)) {
                String name=entry.getAsString(); nodes.add(button(LootDisplayText.table(name),()->{if(mayDiscard()) send("load",name,"","",0,false);}));
            }
            List<UiLayout.Node> bottomPaging=new ArrayList<>();
            if(view.getInt("page")>0)bottomPaging.add(button(tr("previous"),()->send("list","","","",view.getInt("page")-1,false)));
            if(view.getInt("page")+1<view.getInt("pages"))bottomPaging.add(button(tr("next"),()->send("list","","","",view.getInt("page")+1,false)));
            if(!bottomPaging.isEmpty())nodes.add(UiLayout.row(6,bottomPaging));
            nodes.add(note(tr("sources_help")));
        } else {
            nodes.add(group("identity_group"));
            nodes.add(field(tr("id"), id));
            nodes.add(note(tr("id_help")));
            if(!loadedId.isEmpty()) nodes.add(note(tr("source",view.getString("source"),view.getBoolean("active")?tr("active").getString():tr("inactive").getString())));
            nodes.add(button(tr("guide.edit_help"),()->openGuide(advanced?"advanced":"basics")));
            if(!loadedId.isEmpty())nodes.add(button(tr("export"),()->send("export",loadedId,"","",0,false)));
            if(!loadedId.isEmpty()&&!loadedId.startsWith("hhu_author:"))nodes.add(node(body(new PanelButton(tr("copy"),b->{
                var original=new ResourceLocation(loadedId);id.set("hhu_author:copies/"+original.getNamespace()+"/"+original.getPath());
                message="copied_draft";rebuildWidgets();
            },this::theme).selected(!id.text().startsWith("hhu_author:")))));
            if (!advanced) {
                basic(nodes);
            } else {
            nodes.add(group("rules_group"));
            nodes.add(button(tr("guide.tree_help"),()->openGuide("advanced")));
            nodes.add(note(Component.literal("$" + (path.isEmpty()?"":" / "+String.join(" / ",path.stream().map(k->LootDisplayText.field(k).getString()).toList())))));
            if(!path.isEmpty()) nodes.add(button(tr("up"),()->{path.remove(path.size()-1);resetValue();rebuildWidgets();}));
            JsonElement node=current();
            String name=path.isEmpty()?"root":path.get(path.size()-1);
            String context=name;
            if(path.size()>1){String parent=path.get(path.size()-2);
                if(parent.equals("pools"))context="pool";else if(parent.equals("entries"))context="entry";}
            nodes.add(note(tr("help."+helpKey(context))));
            if(node.isJsonObject() && node.getAsJsonObject().has("type"))
                nodes.add(note(tr("node_type",LootDisplayText.summary(node.getAsJsonObject().get("type")))));
            if (node.isJsonObject()) {
                for(var entry:node.getAsJsonObject().entrySet()) {
                    String child=entry.getKey();treeNode(nodes,child,entry.getValue(),()->enter(child));
                }
            } else if (node.isJsonArray()) {
                for(int i=0;i<node.getAsJsonArray().size();i++) {
                    String child=Integer.toString(i);treeNode(nodes,child,node.getAsJsonArray().get(i),()->enter(child));
                }
            } else {
                nodes.add(field(tr("value"),value)); nodes.add(note(tr("json_value_help")));
                nodes.add(button(tr("apply_value"),()->edit(false)));
            }
            if(node.isJsonObject() || node.isJsonArray()) {
                if(node.isJsonArray() && name.equals("entries")) itemFields(nodes);

                nodes.add(note(tr("advanced")));
                if(node.isJsonObject()) nodes.add(field(tr("key"),key));
                nodes.add(field(tr("json_fragment"),value));nodes.add(note(tr("json_fragment_help")));
                nodes.add(button(tr("append"),()->edit(true)));
            }
            if(!path.isEmpty()) nodes.add(button(tr(ticks<deleteUntil?"delete_confirm":"delete"),()->{
                if(ticks>=deleteUntil){deleteUntil=ticks+100;rebuildWidgets();return;}
                remove();deleteUntil=0;rebuildWidgets();
            }));
            }
            if(view.contains("containerRevision")) {
                nodes.add(group("container_group"));
                nodes.add(note(tr("binding_help")));
                if(view.getBoolean("occupied"))nodes.add(button(tr(replace?"replace_on":"replace_off"),()->{replace=!replace;rebuildWidgets();}));
                nodes.add(note(tr("double_help")));
            }
        }
        return UiLayout.column(6,nodes);
    }
    private void resetSimple(){
        poolIndex=0;entryIndex=-1;itemList=false;writePickedData=false;pickedStack=net.minecraft.world.item.ItemStack.EMPTY;
        var pool=selectedPool();if(pool!=null&&pool.has("rolls")&&pool.get("rolls").isJsonPrimitive())rolls.set(pool.get("rolls").getAsString());else rolls.set("1");
    }
    private JsonObject selectedPool(){var pools=LootSimpleDraft.pools(draft);return pools!=null&&poolIndex>=0&&poolIndex<pools.size()&&pools.get(poolIndex).isJsonObject()?pools.get(poolIndex).getAsJsonObject():null;}
    private void basic(List<UiLayout.Node> nodes){
        nodes.add(group("pools_group"));
        nodes.add(note(tr("workflow")));
        nodes.add(button(tr("add_pool"),()->{try{LootSimpleDraft.addPool(draft);poolIndex=LootSimpleDraft.pools(draft).size()-1;entryIndex=-1;rolls.set("1");message="draft_updated";}catch(RuntimeException bad){message="pools_invalid";}rebuildWidgets();}));
        var pools=LootSimpleDraft.pools(draft);
        if(pools==null||pools.isEmpty()){nodes.add(note(tr("pool_empty")));return;}
        List<UiLayout.Node> choices=new ArrayList<>();
        for(int i=0;i<pools.size();i++){int choice=i;choices.add(button(tr("pool_label",i+1),()->{
            setFocused(null);
            poolIndex=choice;entryIndex=-1;deleteUntil=bindUntil=0;var pool=selectedPool();if(pool!=null&&pool.has("rolls")&&pool.get("rolls").isJsonPrimitive())rolls.set(pool.get("rolls").getAsString());
            rebuildWidgets();
        }));}
        for(int start=0;start<choices.size();start+=4)nodes.add(UiLayout.row(4,choices.subList(start,Math.min(start+4,choices.size()))));
        var pool=selectedPool();if(pool==null){nodes.add(note(tr("complex_pool")));return;}
        nodes.add(note(tr("selected_pool",poolIndex+1)));
        if(!pool.has("rolls")||pool.get("rolls").isJsonPrimitive()){
            nodes.add(field(tr("pool_rolls"),rolls));nodes.add(button(tr("set_rolls"),()->{if(rolls.valid()){pool.addProperty("rolls",rolls.value().orElse(1));message="draft_updated";}rebuildWidgets();}));
        }else nodes.add(note(tr("complex_rolls")));
        nodes.add(note(tr("pool_preserved")));
        var entries=LootSimpleDraft.entries(pool);if(entries==null){nodes.add(note(tr("complex_pool")));return;}
        for(int i=0;i<entries.size();i++){
            int choice=i;var simple=LootSimpleDraft.item(entries.get(i));
            var label=simple==null?tr("complex_entry",i+1):tr("entry_label",i+1,simple.id(),simple.weight(),simple.minimum(),simple.maximum());
            Runnable select=()->{
                entryIndex=choice;deleteUntil=bindUntil=0;writePickedData=false;pickedStack=net.minecraft.world.item.ItemStack.EMPTY;
                if(simple==null){path.clear();path.addAll(List.of("pools",Integer.toString(poolIndex),"entries",Integer.toString(choice)));advanced=true;resetValue();}
                else{item.set(simple.id());weight.set(Integer.toString(simple.weight()));min.set(Integer.toString(simple.minimum()));max.set(Integer.toString(simple.maximum()));}
                rebuildWidgets();
            };
            var display=LootDisplayText.item(entries.get(i));
            if(display!=null)nodes.add(itemButton(display,simple==null?tr("complex_entry",i+1):tr("entry_metrics",i+1,simple.weight(),simple.minimum(),simple.maximum()),choice==entryIndex,select));
            else nodes.add(button(label,select));
        }
        nodes.add(button(tr("new_item"),()->{entryIndex=-1;writePickedData=false;pickedStack=net.minecraft.world.item.ItemStack.EMPTY;item.set("minecraft:bread");weight.set("1");min.set("1");max.set("1");rebuildWidgets();}));
        nodes.add(group("items_group"));
        nodes.add(note(tr(entryIndex<0?"adding_item":"editing_item",entryIndex+1)));
        itemFields(nodes);
        if(entryIndex>=0)nodes.add(button(tr(ticks<deleteUntil?"delete_confirm":"delete_entry"),()->{
            if(ticks>=deleteUntil){deleteUntil=ticks+100;rebuildWidgets();return;}
            entries.remove(entryIndex);entryIndex=-1;deleteUntil=0;message="draft_updated";rebuildWidgets();
        }));
    }
    private void itemFields(List<UiLayout.Node> nodes){
        var selected=LootItemCatalog.entry(item.text());
        if(selected!=null&&writePickedData&&!pickedStack.isEmpty()&&pickedStack.getItem()==selected.icon().getItem())selected=LootItemCatalog.describe(pickedStack);
        else if(!advanced&&entryIndex>=0&&selectedPool()!=null){
            var entries=LootSimpleDraft.entries(selectedPool());
            if(entries!=null&&entryIndex<entries.size()){
                var preview=LootDisplayText.item(entries.get(entryIndex));
                if(preview!=null&&preview.id().toString().equals(item.text()))selected=preview;
            }
        }
        itemPreviewId=item.text();itemPreviewDue=0;
        var selectedIcon=selected==null?net.minecraft.world.item.ItemStack.EMPTY:selected.icon();
        selectorRow=body(new PanelItemRow(selected==null?net.minecraft.world.item.ItemStack.EMPTY:selected.icon(),
                selected==null?tr("choose_item"):selected.title(),
                selected==null?Component.literal(item.text()):Component.literal(selected.detail()),
                tr("item_drop_hint"),b->{itemList=!itemList;if(itemList)searchItems();rebuildWidgets();},this::theme));
        selectorRow.active=!waiting;nodes.add(node(selectorRow));
        if(advanced||showItemId)nodes.add(field(tr("item"),item));
        if(!advanced)nodes.add(button(tr(showItemId?"item_id_hide":"item_id_show"),()->{showItemId=!showItemId;rebuildWidgets();}));
        var player=Minecraft.getInstance().player;
        if(player!=null&&!player.getOffhandItem().isEmpty())nodes.add(button(tr("pick_offhand"),()->selectItem(player.getOffhandItem())));
        if(itemList){
            nodes.add(field(tr("item_search"),itemSearch));
            int pages=Math.max(1,(itemChoices.size()+ITEM_PAGE_SIZE-1)/ITEM_PAGE_SIZE);
            itemPage=Math.min(itemPage,pages-1);
            nodes.add(note(tr("page",itemPage+1,pages,itemChoices.size())));
            List<UiLayout.Node> paging=new ArrayList<>();
            if(itemPage>0)paging.add(button(tr("previous"),()->{itemPage--;rebuildWidgets();}));
            if(itemPage+1<pages)paging.add(button(tr("next"),()->{itemPage++;rebuildWidgets();}));
            if(!paging.isEmpty())nodes.add(UiLayout.row(6,paging));
            for(var display:itemChoices.stream().skip((long)itemPage*ITEM_PAGE_SIZE).limit(ITEM_PAGE_SIZE).toList()){
                boolean same=display.id().toString().equals(item.text())&&net.minecraft.world.item.ItemStack.isSameItemSameTags(display.icon(),selectedIcon);
                nodes.add(itemButton(display,Component.empty(),same,()->selectItem(display.icon())));
            }
            if(itemChoices.isEmpty())nodes.add(note(tr("items_empty")));
        }
        nodes.add(note(tr("icon_type_help")));
        nodes.add(UiLayout.row(6,List.of(field(tr("weight"),weight),field(tr("count_min"),min),field(tr("count_max"),max))));
        nodes.add(node(body(new PanelButton(tr(entryIndex<0?"add_item":"update_item"),b->addItem(),this::theme).selected(true))));
    }
    private void searchItems(){
        appliedItemSearch=itemSearch.text();itemChoices=LootItemCatalog.search(appliedItemSearch);itemPage=0;itemSearchDue=0;
    }
    private void enter(String child){path.add(child);entryIndex=-1;deleteUntil=bindUntil=0;resetValue();rebuildWidgets();}
    private JsonElement current(){JsonElement node=draft;for(String p:path)node=node.isJsonObject()?node.getAsJsonObject().get(p):node.getAsJsonArray().get(Integer.parseInt(p));return node;}
    private void resetValue(){value=InputValue.text(current().isJsonPrimitive()?current().toString():"{}",65536,true);}
    private void edit(boolean append){
        JsonObject backup=draft.deepCopy();
        try {
            JsonElement next=LootDocuments.parse("{\"value\":"+value.text()+"}").get("value");
            JsonElement node=current();
            if(append){if(node.isJsonArray())node.getAsJsonArray().add(next);else{
                String k=key.text().trim();if(k.isEmpty()||node.getAsJsonObject().has(k))throw new IllegalArgumentException("key_exists");
                node.getAsJsonObject().add(k,next);
            }}else{
                String child=path.remove(path.size()-1);var parent=current();path.add(child);
                if(parent.isJsonObject())parent.getAsJsonObject().add(child,next);else parent.getAsJsonArray().set(Integer.parseInt(child),next);
            }
            LootDocuments.parse(canonical());message="draft_updated";
        }catch(RuntimeException error){draft=backup;message="json_invalid";}
        rebuildWidgets();
    }
    private void remove(){String child=path.remove(path.size()-1);var parent=current();if(parent.isJsonObject())parent.getAsJsonObject().remove(child);else parent.getAsJsonArray().remove(Integer.parseInt(child));resetValue();message="draft_updated";}
    private void addItem(){
        if(!weight.valid()||!min.valid()||!max.valid()||min.value().orElse(1)>max.value().orElse(1)||ResourceLocation.tryParse(item.text())==null||!net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(ResourceLocation.tryParse(item.text()))){message="item_invalid";rebuildWidgets();return;}
        JsonArray entries=advanced?current().getAsJsonArray():LootSimpleDraft.entries(selectedPool());
        if(entries==null){message="pools_invalid";rebuildWidgets();return;}
        JsonObject original=!advanced&&entryIndex>=0?entries.get(entryIndex).getAsJsonObject():new JsonObject();
        JsonObject edited=LootSimpleDraft.patch(original,new LootSimpleDraft.Item(item.text(),weight.value().orElse(1),min.value().orElse(1),max.value().orElse(1)));
        if(writePickedData&&!pickedStack.isEmpty()&&item.text().equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(pickedStack.getItem()).toString()))
            edited=LootSimpleDraft.withStackData(edited,pickedStack.hasTag()?pickedStack.getTag().toString():"{}");
        if(!advanced&&entryIndex>=0)entries.set(entryIndex,edited);else {entries.add(edited);if(!advanced)entryIndex=entries.size()-1;}
        writePickedData=false;pickedStack=net.minecraft.world.item.ItemStack.EMPTY;
        message="draft_updated";rebuildWidgets();
    }
    private static String helpKey(String key){return switch(key){case "root","pool","entry","function","condition","pools","entries","rolls","bonus_rolls","weight","quality","conditions","functions","type","name","count","children","expand","random_sequence"->key;default->"generic";};}
    @Override protected void buildFooter(){
        footer(tr("close"),b->onClose());saveAction=null;bindAction=null;
        if(guideOpen){
            footer(tr("guide.back"),b->{guideOpen=false;scrollReset();rebuildWidgets();});
        } else if(!library){
            saveAction=footer(tr("save_apply"),b->{if(id.valid())send("publish",id.text(),id.text().equals(loadedId)?revision:"",canonical(),0,false);});
            if(view.contains("containerRevision"))bindAction=footer(tr(ticks<bindUntil?"bind_confirm":"bind"),b->{
                if(ticks>=bindUntil){bindUntil=ticks+100;rebuildWidgets();return;}
                send("bind",loadedId,view.getString("containerRevision"),"",0,replace);
            });
        }
        actions();
    }
    private void actions(){
        if(saveAction!=null)saveAction.active=!waiting&&id.valid()&&id.text().startsWith("hhu_author:")&&(dirty()||!view.getBoolean("active"));
        if(bindAction!=null)bindAction.active=!waiting&&!dirty()&&!loadedId.isEmpty()&&view.getBoolean("active")&&(!view.getBoolean("occupied")||replace);
    }
    @Override protected void formChanged(){
        super.formChanged();actions();
        if(itemList&&!appliedItemSearch.equals(itemSearch.text()))itemSearchDue=ticks+5;
        if(selectorRow!=null&&!itemPreviewId.equals(item.text()))itemPreviewDue=ticks+5;
    }
    @Override public void tick(){
        super.tick();ticks++;
        if(ticks%20==0)LootDraftCache.save(this);
        if(itemList&&itemSearchDue>0&&ticks>=itemSearchDue){searchItems();rebuildWidgets();}
        else if(itemPreviewDue>0&&ticks>=itemPreviewDue){itemPreviewDue=0;rebuildWidgets();}
        else if(deleteUntil==ticks||bindUntil==ticks)rebuildWidgets();
    }
    @Override public void onClose(){
        LootDraftCache.save(this);closed=true;
        LootEditorNetwork.request(new LootEditorNetwork.Request(token,"close","","","","",0,false));super.onClose();
    }
}
