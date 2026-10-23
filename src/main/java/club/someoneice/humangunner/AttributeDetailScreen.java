package club.someoneice.humangunner;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** Thirteen scrollable final values with compact hover help and one pinned inline explanation. */
public final class AttributeDetailScreen extends Screen {
    private record Hit(int top,int bottom,int row) { }
    private record TagHit(int left,int top,int right,int bottom,List<String> dimensions) { }
    private final Screen parent;
    private final UUID token;
    private AttributeDetailSnapshot snapshot;
    private final List<Hit> hits=new ArrayList<>();
    private final List<TagHit> tagHits=new ArrayList<>();
    private Set<String> highlighted=Set.of();
    private String expanded="";
    private int scroll,maxScroll,ticks,contentTop=91;
    private boolean suppressClose;
    public AttributeDetailScreen(Screen parent,UUID token,AttributeDetailSnapshot snapshot) {
        super(text("title"));this.parent=parent;this.token=token;this.snapshot=snapshot;
    }
    Screen parent(){return parent;} UUID token(){return token;}
    void update(AttributeDetailSnapshot next) {
        if(next.target().equals(snapshot.target())&&next.entityId()==snapshot.entityId()&&next.tick()>=snapshot.tick())snapshot=next;
    }
    private boolean managedParent(){return parent instanceof SoldierCommandScreen||parent instanceof SoldierRosterScreen;}
    void serverClosed(){suppressClose=true;minecraft.setScreen(managedParent()?null:parent);}
    void disconnect(){suppressClose=true;}
    private static Component text(String key,Object...args){return Component.translatable("attribute_detail."+key,args);}
    private static Component dimension(String key){return text("dimension."+key);}
    @Override protected void init(){hits.clear();tagHits.clear();scroll=0;maxScroll=0;addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(right()-95,panelTop()+panelHeight()-33,88,20).build());}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){if(++ticks%20==0)AttributeDetailNetwork.keepAlive(token);}
    @Override public void removed(){if(!suppressClose)AttributeDetailNetwork.close(token);}
    @Override public void onClose(){
        if(managedParent()) {
            // The old screen's removed() closed its capability when details opened.
            // Send before setScreen/removed closes this detail capability as well.
            if(!suppressClose&&minecraft.getConnection()!=null)
                AttributeDetailNetwork.returnToParent(token,parent instanceof SoldierRosterScreen);
            minecraft.setScreen(null); // only a fresh server opening may restore command controls
        } else minecraft.setScreen(parent);
    }
    private int panelHeight(){return CommandPanelLayout.height(height);}
    private int panelTop(){return (height-panelHeight())/2;}
    private int left(){return (width-CommandPanelLayout.width(width))/2;}
    private int right(){return width-left();}
    private int viewTop(){return contentTop;}
    private int viewBottom(){return Math.max(viewTop()+12,panelTop()+panelHeight()-45);}
    @Override public boolean mouseScrolled(double x,double y,double delta){scroll=Mth.clamp(scroll-(int)(delta*24),0,maxScroll);return true;}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0){
            for(TagHit tag:tagHits)if(x>=tag.left()&&x<tag.right()&&y>=tag.top()&&y<tag.bottom()){
                highlighted=highlighted.equals(Set.copyOf(tag.dimensions()))?Set.of():Set.copyOf(tag.dimensions());return true;
            }
            if(x>=left()&&x<right()&&y>=viewTop()&&y<viewBottom())for(Hit hit:hits)if(y>=hit.top()&&y<hit.bottom()){
                String key=snapshot.rows().get(hit.row()).dimension();expanded=expanded.equals(key)?"":key;return true;
            }
        }
        return super.mouseClicked(x,y,button);
    }
    private static String number(double value){return String.format(Locale.ROOT,"%.4f",value).replaceFirst("0+$","").replaceFirst("\\.$","");}
    private static String displayed(AttributeDetailSnapshot.Row row){
        if(!row.known())return text("unknown").getString();
        return switch(row.unit()){
            case "percent"->String.format(Locale.ROOT,"%.2f%%",row.value()*100);
            case "hp"->String.format(Locale.ROOT,"%.2f HP",row.value());
            case "factor"->"×"+number(row.value());
            default->number(row.value())+" "+text("unit."+row.unit()).getString();
        };
    }
    private static boolean comparable(AttributeDetailSnapshot.Row row) {
        return row.known() && row.notes().stream().noneMatch(note -> note.key().equals("reference_unknown"));
    }
    private static String arrow(AttributeDetailSnapshot.Row row){return !comparable(row)?"?":row.value()>row.baseline()+1e-6?"↑":row.value()<row.baseline()-1e-6?"↓":"≈";}
    private static int color(AttributeDetailSnapshot.Row row){return !comparable(row)?0xB9C1CA:row.value()>row.baseline()+1e-6?0x95D7AB:row.value()<row.baseline()-1e-6?0xE59A94:0xD7DFE7;}
    private List<Component> detail(AttributeDetailSnapshot.Row row){
        List<Component> result=new ArrayList<>();
        result.add(text("stored_effective",number(row.stored()),number(row.effective())));
        result.add(text("base",number(row.base())));
        for(var layer:row.layers())result.add(text("layer_line",text("layer."+layer.key()),text("operation."+layer.operation()),number(layer.value())));
        result.add(text("final",displayed(row)));
        result.add(text(!comparable(row)?"comparison.unknown":row.value()>row.baseline()+1e-6?"comparison.higher":row.value()<row.baseline()-1e-6?"comparison.lower":"comparison.similar",number(row.baseline())));
        for(var note:row.notes()) {
            if(note.key().startsWith("gun_category_"))
                result.add(text("gun_category_value", text("gun_category."+note.key().substring("gun_category_".length())), note.detail()));
            else result.add(text("note."+note.key(),note.detail()));
        }
        return result;
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
        renderBackground(graphics);int left=left(),right=right(),wrap=Math.max(24,right-left-18);
        int panelY=panelTop(),accent=CommandPanelChrome.accent(snapshot.faction());
        CommandPanelChrome.frame(graphics,left,panelY,right-left,panelHeight(),accent);
        graphics.drawString(font,title,left+12,panelY+9,CommandPanelScreen.TEXT,false);
        CommandPanelChrome.badge(graphics,left+12,panelY+34,snapshot.faction());
        Component name=text("header",snapshot.name(),snapshot.boss()?text("boss"):text("ordinary"));
        graphics.drawString(font,font.plainSubstrByWidth(name.getString(),Math.max(1,wrap-25)),left+30,panelY+34,accent,false);
        graphics.drawString(font,text("health_budget",String.format(Locale.ROOT,"%.2f",snapshot.currentHealth()),String.format(Locale.ROOT,"%.3f",snapshot.budget())),left+30,panelY+47,0xD2DCE6,false);
        Component status=text(snapshot.enabled()?"enabled":"disabled");
        graphics.drawString(font,font.plainSubstrByWidth(text("sample",snapshot.tick(),status).getString(),Math.max(1,right-left-116)),left+12,panelY+panelHeight()-27,0x91A6B6,false);
        tagHits.clear();int tagX=left+12,tagY=panelY+64;
        if(snapshot.labels().isEmpty())graphics.drawString(font,text("no_labels"),tagX,tagY+3,0x8D9DAA,false);
        for(var label:snapshot.labels()){
            Component name=text("label."+label.key());int tagWidth=font.width(name)+12;
            if(tagX+tagWidth>right-5){tagX=left+12;tagY+=17;}
            graphics.fill(tagX,tagY,Math.min(right-4,tagX+tagWidth),tagY+15,highlighted.equals(Set.copyOf(label.dimensions()))?0xFF43634F:0xFF2E414D);
            graphics.drawString(font,name,tagX+6,tagY+3,0xDAE8DB,false);
            tagHits.add(new TagHit(tagX,tagY,Math.min(right-4,tagX+tagWidth),tagY+15,label.dimensions()));tagX+=tagWidth+4;
        }
        int hintY=tagY+21;contentTop=Math.min(hintY+14,panelY+panelHeight()-57);
        graphics.drawString(font,font.plainSubstrByWidth(text("hint").getString(),wrap),left+12,hintY,0x91A6B6,false);
        int top=viewTop(),bottom=viewBottom(),y=top-scroll;hits.clear();
        graphics.enableScissor(left,top,right,bottom);
        AttributeDetailSnapshot.Row hovered=null;
        for(int index=0;index<snapshot.rows().size();index++){
            var row=snapshot.rows().get(index);int rowTop=y;
            Component heading=Component.literal(expanded.equals(row.dimension())?"▾ ":"▸ ").append(dimension(row.dimension()));
            Component shown=Component.literal(arrow(row)+" "+displayed(row));
            int valueWidth=Math.min(wrap/2,Math.max(72,font.width(shown)+4));
            List<FormattedCharSequence> headingLines=font.split(heading,Math.max(1,wrap-valueWidth-10));
            List<FormattedCharSequence> valueLines=font.split(shown,Math.max(1,valueWidth));
            int headingHeight=Math.max(25,Math.max(headingLines.size(),valueLines.size())*11+10);
            graphics.fill(left+7,y,right-7,y+headingHeight,highlighted.contains(row.dimension())?0xFF344E43:index%2==0?0xFF202E3A:0xFF253441);
            graphics.fill(left+7,y+4,left+9,y+headingHeight-4,highlighted.contains(row.dimension())?accent:0xFF455B68);
            int lineY=y+6;for(var line:headingLines){graphics.drawString(font,line,left+14,lineY,0xFFE1E7EC,false);lineY+=11;}
            lineY=y+6;for(var line:valueLines){graphics.drawString(font,line,right-13-font.width(line),lineY,color(row),false);lineY+=11;}
            y+=headingHeight;
            if(expanded.equals(row.dimension())){
                for(Component line:detail(row))for(var wrapped:font.split(line,wrap-8)){
                    graphics.fill(left+7,y,right-7,y+12,0xFF1B2832);graphics.drawString(font,wrapped,left+15,y+1,0xC4D1DC,false);y+=12;
                }
                y+=4;
            }
            hits.add(new Hit(rowTop,y,index));
            if(mouseX>=left&&mouseX<right&&mouseY>=Math.max(top,rowTop)&&mouseY<Math.min(bottom,y))hovered=row;
            y+=2;
        }
        graphics.disableScissor();maxScroll=Math.max(0,y+scroll-bottom);scroll=Mth.clamp(scroll,0,maxScroll);
        if(maxScroll>0){int travel=Math.max(1,bottom-top-18);int bar=top+(int)(travel*(scroll/(double)maxScroll));graphics.fill(right-3,bar,right-1,bar+18,0xFF87A7B7);}
        super.render(graphics,mouseX,mouseY,partialTick);
        if(hovered!=null&&!expanded.equals(hovered.dimension())){
            List<Component> tooltip=new ArrayList<>();tooltip.add(dimension(hovered.dimension()));
            tooltip.add(text("stored_effective",number(hovered.stored()),number(hovered.effective())));
            for(var layer:hovered.layers().subList(0,Math.min(4,hovered.layers().size())))tooltip.add(text("layer_line",text("layer."+layer.key()),text("operation."+layer.operation()),number(layer.value())));
            for(var note:hovered.notes())if(note.key().equals("current_bounds"))tooltip.add(text("note.current_bounds",note.detail()));
            tooltip.add(text("click_expand"));graphics.renderComponentTooltip(font,tooltip,mouseX,mouseY);
        }
    }
}
