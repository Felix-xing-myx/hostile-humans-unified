package club.someoneice.humangunner;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Display layers are local; developer control requests are separately authorized by the server. */
public final class NavigationLegendScreen extends Screen {
    private record Row(int color, Component text) {}
    private static final String[] PAGES = {"overview", "memory", "vision", "clues", "action", "budget", "legend"};
    private int page, scroll, maxScroll;
    public NavigationLegendScreen() { super(label("title")); }
    private static Component label(String key, Object... args) { return Component.translatable("navigation_debug.detail." + key, args); }
    private int panelWidth() { return Math.min(510, width - 24); }
    @Override protected void init() {
        int w = Math.max(32, Math.min(70, (width-16)/PAGES.length));
        for (int i=0;i<PAGES.length;i++) {
            final int index=i;
            addRenderableWidget(Button.builder(label("page."+PAGES[i]), b -> { page=index; scroll=0; })
                    .bounds(width/2-w*PAGES.length/2+i*w, 26, w, 20).build());
        }
        addRenderableWidget(Button.builder(label("entity"), b -> {
            var entries=NavigationDebugClient.visibleEntries();
            if (entries.isEmpty()) return;
            NavigationDebugNetwork.cycleFocus();scroll=0;
        }).bounds(width/2-150,height-52,100,20).build());
        addRenderableWidget(Button.builder(label("stop_test"), b -> NavigationDebugNetwork.stopFocusedTest())
                .bounds(width/2-50,height-52,100,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width/2+50,height-52,100,20).build());
        for(int i=0;i<4;i++) {
            final int index=i;
            addRenderableWidget(Button.builder(layerLabel(i), b -> {
                NavigationDebugClient.LAYERS[index]=(NavigationDebugClient.LAYERS[index]+1)%3;
                b.setMessage(layerLabel(index));
            }).bounds(width/2-200+i*100,height-28,100,20).build());
        }
    }
    private Component layerLabel(int i) {
        String scope=i==1&&NavigationDebugClient.LAYERS[i]==1?"scope.vision_focus":"scope."+NavigationDebugClient.LAYERS[i];
        return label("layer",label("layer."+i),label(scope));
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double delta) { scroll=Mth.clamp(scroll-(int)(delta*22),0,maxScroll); return true; }
    private List<Row> rows() {
        List<Row> rows=new ArrayList<>();
        var entries=NavigationDebugClient.visibleEntries();
        if(entries.isEmpty()) { rows.add(new Row(-1,label("stale"))); return rows; }
        var focus=entries.stream().filter(NavigationDebugClient::diagnosticFocus).findFirst().orElse(entries.get(0));
        var s=focus.snapshot(); var p=s.perception();
        rows.add(new Row(NavigationDebugClient.slotColor(focus.slot()),label("focus",focus.slot()+1,s.name())));
        rows.add(new Row(-1,label("readonly")));
        if(s.capacityTruncated()) rows.add(new Row(-1,Component.literal("因网络容量截断部分诊断细节；观察实体、焦点与动作权限数据保留")));
        if(page!=6) {
            rows.add(new Row(-1,label("state",p.awareness(),p.decision())));
            rows.add(new Row(-1,label("reason",p.reason())));
            rows.add(new Row(-1,label("sample",p.tick())));
        }
        if(page==0) {
            for(var e:entries) rows.add(new Row(NavigationDebugClient.slotColor(e.slot()),label("state", "#"+(e.slot()+1)+" "+e.snapshot().name(),e.snapshot().perception().decision())));
        }
        if(page==2) {
            rows.add(new Row(-1,label("geometry")));
            rows.add(new Row(-1,label("vision_modes")));
        }
        if(page==4) behaviorRows(rows, s.behavior());
        if(page==4 || page==5) {
            rows.add(new Row(-1,Component.translatable("navigation_debug.card_title",focus.slot()+1,s.name(),Component.translatable("navigation_debug.mode."+s.mode()),"")));
            rows.add(new Row(-1,Component.translatable("navigation_debug.hud_route",Component.translatable("navigation_debug.result."+s.state()),s.firstNode(),s.totalNodes(),s.complete(),s.truncated())));
            rows.add(new Row(-1,Component.literal(s.navigator()+" | "+s.budget()+" | "+s.searchMillis()+" ms | "+s.obstruction())));
            for(var sample:s.samples()) rows.add(new Row(-1,Component.literal(sample.source()+" / "+sample.result()+" / "+sample.position()+" / "+sample.score())));
        }
        if(page==6) {
            for(int i=0;i<8;i++) rows.add(new Row(NavigationDebugClient.slotColor(i),Component.translatable("navigation_debug.help.entity",i+1)));
            for(String key:List.of("next","pending","accepted","rejected","controls")) rows.add(new Row(-1,Component.translatable("navigation_debug.help."+key)));
            for(String kind:List.of("visible","dynamic","last","recognition","sound","direction","beacon","los","blocked","vision_near_base","vision_known_near_base","vision_new_ring","vision_known_ring")) rows.add(new Row(-1,label("marker."+kind)));
        } else {
            for(var row:p.rows()) if(matches(row.group())) rows.add(new Row(-1,Component.literal(row.label()+": "+row.value())));
            for(var marker:p.markers()) if(page==2 && (marker.kind().equals("los") || marker.kind().equals("blocked"))) rows.add(new Row(-1,Component.literal(marker.kind()+": "+marker.label())));
        }
        return rows;
    }
    private static void behaviorRows(List<Row> rows, BehaviorDebugSnapshot snapshot) {
        rows.add(new Row(-1, Component.literal("服务器动作 · 会话 " + (snapshot.authorityPresent() ? snapshot.session() : "尚无协调器")
                + " · 采样tick " + snapshot.tick())));
        rows.add(new Row(-1, Component.literal("移动执行桥: " + snapshot.bridge())));
        if (snapshot.bridge().equals("unbound")) rows.add(new Row(-1, Component.literal("unbound：以下仅显示原GoalSelector运行状态，尚无移动权限归属保证")));
        if (snapshot.actions().isEmpty()) rows.add(new Row(-1, Component.literal("当前协调动作：无")));
        for (var action : snapshot.actions()) {
            String kind = action.lease() ? "租约" + action.leaseTicks() + "t / 心跳 " + (action.lastHeartbeat() == Long.MIN_VALUE ? "尚无" : action.lastHeartbeat()) : "动作时限";
            rows.add(new Row(-1, Component.literal("#" + action.session() + "/" + action.sequence() + " " + action.name() + " · " + action.status()
                    + " · p" + action.priority() + " · " + (action.interruptible() ? "可中断" : "不可中断"))));
            rows.add(new Row(-1, Component.literal("持有 " + String.join(" + ", action.heldResources()) + " / 请求 " + String.join(" + ", action.resources()) + " · 剩余 " + action.remainingTicks() + "t · " + kind)));
        }
        for (var goal : snapshot.goals()) rows.add(new Row(-1, Component.literal("Goal " + goal.name() + " · p" + goal.priority() + " · " + String.join("/", goal.flags())
                + " · " + (goal.entered() ? "已启动" : "未启动") + (goal.executing() ? "/回调中" : "") + " · " + goal.state()
                + (goal.tokenSequence() == 0 ? "" : " · #" + goal.tokenSession() + "/" + goal.tokenSequence()))));
        for (var progress : snapshot.progress()) rows.add(new Row(-1, Component.literal(progress.kind() + (progress.item().isEmpty() ? "" : " · " + progress.item())
                + (progress.remainingTicks() < 0 ? "" : " · 已用 " + progress.elapsedTicks() + "t / 剩余 " + progress.remainingTicks() + "t") + " · " + progress.detail())));
        for (var feedback : snapshot.feedback()) rows.add(new Row(-1, Component.literal("最近反馈 #" + feedback.session() + "/" + feedback.sequence()
                + " " + feedback.name() + " · " + feedback.status() + " · " + feedback.reason())));
        if (snapshot.truncated()) rows.add(new Row(-1, Component.literal("动作/Goal/反馈已按协议上限截断")));
    }
    private boolean matches(String group) {
        return NavigationDebugViewPolicy.pageContains(page, group);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        renderBackground(g);
        var rows=rows(); int left=(width-panelWidth())/2,top=52,bottom=height-60,wrap=Math.max(8,panelWidth()-34);
        int content=0; for(var row:rows) content+=font.split(row.text(),wrap).size()*11+7;
        maxScroll=Math.max(0,content-(bottom-top));scroll=Mth.clamp(scroll,0,maxScroll);
        g.fill(left,top-4,left+panelWidth(),bottom+4,0xD0101820);
        g.drawCenteredString(font,title,width/2,10,0xFFFFFF);
        g.enableScissor(left,top,left+panelWidth(),bottom);
        try {
            int y=top-scroll;
            for(var row:rows) {
                if(row.color()>=0)g.fill(left+8,y+1,left+17,y+10,0xFF000000|row.color());
                for(var line:font.split(row.text(),wrap)) {g.drawString(font,line,left+24,y,0xE5EAF0,false);y+=11;} y+=7;
            }
        } finally {g.disableScissor();}
        super.render(g,mouseX,mouseY,partialTick);
    }
}
