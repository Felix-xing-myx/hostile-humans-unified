package club.someoneice.humangunner;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** One documented, bounded source for all server perception settings; restart scoped. */
public final class PerceptionConfig {
    public record Setting(double fallback, double min, double max, String description) {}
    public static final Map<String, Setting> SETTINGS;
    static {
        Map<String, Setting> m = new LinkedHashMap<>();
        add(m,"vision.minimum_range",64,8,256,"最低基础视觉距离（格），之后应用原版可见度修正 / Minimum base visual range before vanilla visibility scaling");
        add(m,"vision.near_distance",8,1,64,"近距离视角锚点（格） / Near field-of-view distance anchor");
        add(m,"vision.middle_distance",32,2,128,"中距离视角锚点（格），小于前段时自动顺延 / Middle field-of-view distance anchor, ordered after near");
        add(m,"vision.far_distance",64,3,256,"远距离视角锚点（格），更远保持远段角度 / Far field-of-view distance anchor, retained beyond it");
        add(m,"vision.near_full_angle",160,10,180,"近距离完整视场角（度），锚点间线性过渡 / Near full field of view, interpolated between anchors");
        add(m,"vision.middle_full_angle",120,10,180,"中距离完整视场角（度） / Middle full field of view in degrees");
        add(m,"vision.far_full_angle",90,10,180,"远距离完整视场角（度） / Far full field of view in degrees");
        add(m,"vision.familiar_angle_multiplier",1.2,1,4,"旧敌视锥半角倍率 / Familiar-target half-angle multiplier");
        add(m,"vision.maximum_half_angle",90,5,90,"旧敌半角上限（度），不缩小陌生目标视野 / Familiar half-angle cap, never narrowing the base field");
        add(m,"recognition.base_ticks",10,1,200,"基础识别时长 / Recognition base ticks");
        add(m,"recognition.ticks_per_block",1.25,0,20,"每格识别时长增量 / Recognition ticks per block");
        add(m,"recognition.maximum_ticks",90,4,1200,"基础识别时长上限 / Maximum base recognition ticks");
        add(m,"recognition.minimum_ticks",4,1,200,"所有识别最短有效目视时长 / Minimum visible recognition ticks");
        add(m,"recognition.alert_factor",.5,.1,1,"单独警觉时长系数 / Alert-only duration factor");
        add(m,"recognition.familiar_factor",.5,.1,1,"单独旧敌时长系数 / Familiar-only duration factor");
        add(m,"recognition.combined_factor",.4,.1,1,"警觉且旧敌的独立组合系数，不连乘 / Combined duration factor, not multiplied");
        add(m,"recognition.break_ticks",10,1,200,"连续失去视觉后清零 / Consecutive lost-vision ticks before reset");
        add(m,"recognition.hurt_ticks",4,1,200,"真实直接伤害来源的快速识别时长 / Direct-hit source visible recognition ticks");
        add(m,"memory.dynamic_ticks",160,1,2400,"已确认目标动态位置许可时长 / Confirmed live-position lease ticks");
        add(m,"memory.last_position_ticks",600,20,12000,"最后合法位置线索期限 / Last-known position lifetime ticks");
        add(m,"memory.familiar_ticks",2400,20,72000,"旧敌重认标签期限 / Familiar identity lifetime ticks");
        add(m,"memory.maximum_targets",16,4,64,"逐目标记忆容量 / Per-human perception record capacity");
        add(m,"observation.alert_ticks",600,20,12000,"有效刺激后的警觉时间 / Alert lifetime after valid stimulus");
        add(m,"observation.direction_ticks",40,4,400,"无可见来源时方向观察上限 / Unseen direction observation limit");
        add(m,"observation.focus_ticks",120,10,1200,"首次识别任务占用上限 / Protected initial-recognition task limit");
        add(m,"observation.queue_size",4,1,16,"待观察来源容量 / Pending observation source capacity");
        add(m,"observation.look_cooldown_ticks",10,1,100,"同源回看请求最短间隔 / Same-source glance request cooldown");
        add(m,"observation.unknown_notice_ticks",200,20,1200,"陌生来袭方向期限 / Unknown incoming direction lifetime");
        add(m,"hearing.dig_radius",12,0,64,"挖掘声半径（格） / Digging sound radius");
        add(m,"hearing.block_radius",16,0,64,"破坏放置声半径 / Block change radius");
        add(m,"hearing.damage_radius",16,0,64,"玩家和怪物受伤半径 / Player and monster damage radius");
        add(m,"hearing.aid_radius",24,0,96,"真实友军求援半径 / Allied aid radius");
        add(m,"hearing.door_radius",10,0,64,"通道操作声半径 / Passage interaction radius");
        add(m,"hearing.explosion_radius",32,0,128,"爆炸声半径 / Explosion radius");
        add(m,"hearing.walk_radius",8,0,32,"警觉时普通脚步半径 / Alert walking radius");
        add(m,"hearing.run_radius",12,0,48,"警觉时奔跑落地半径 / Alert running and landing radius");
        add(m,"hearing.swim_radius",6,0,32,"警觉时水声半径 / Alert swimming radius");
        add(m,"hearing.walk_distance",.8,.1,8,"行走每次发声移动距离 / Walking movement per sound");
        add(m,"hearing.run_distance",.6,.1,8,"奔跑每次发声移动距离 / Running movement per sound");
        add(m,"hearing.swim_distance",1,.1,8,"游泳每次发声移动距离 / Swimming movement per sound");
        add(m,"hearing.step_cooldown_ticks",6,1,100,"脚步同源间隔 / Footstep source cooldown");
        add(m,"hearing.swim_cooldown_ticks",10,1,100,"水声同源间隔 / Swim source cooldown");
        add(m,"hearing.environment_cooldown_ticks",10,1,200,"普通事件同源去重间隔 / Environment source coalescing interval");
        add(m,"hearing.dig_cooldown_ticks",20,1,200,"持续挖掘去重间隔 / Continuous digging interval");
        add(m,"hearing.aid_cooldown_ticks",40,1,400,"同友军求援间隔 / Ally aid interval");
        add(m,"hearing.notice_ticks",200,10,2400,"普通声音期限 / Environment clue lifetime");
        add(m,"hearing.urgent_notice_ticks",300,10,2400,"求援爆炸期限 / Aid and explosion clue lifetime");
        add(m,"hearing.step_notice_ticks",100,10,1200,"脚步水声期限 / Footstep and swim clue lifetime");
        add(m,"hearing.maximum_clues",4,1,16,"每人线索上限 / Per-human clue capacity");
        add(m,"investigation.radius",6,1,24,"普通搜索半径 / Ordinary search radius");
        add(m,"investigation.duration_ticks",200,20,2400,"单次调查搜索时间 / Investigation duration");
        add(m,"investigation.maximum_points",3,1,12,"单次最多搜索点 / Maximum search points");
        add(m,"investigation.retry_ticks",40,5,400,"同线索失败重试间隔 / Failed clue retry interval");
        add(m,"investigation.maximum_retries",3,1,12,"同线索重试上限 / Maximum clue route attempts");
        add(m,"investigation.hold_ticks",20,1,200,"同级调查最短保持 / Minimum equal-priority clue hold");
        add(m,"investigation.repath_ticks",20,1,200,"声音改路最短间隔 / Sound route refresh interval");
        add(m,"investigation.repath_distance",2,.5,16,"声音改路最小位移 / Sound destination change threshold");
        add(m,"investigation.command_deviation",8,1,32,"跟随巡逻额外调查偏离 / Follow/patrol investigation deviation");
        add(m,"beacon.duration_ticks",6000,20,72000,"最后成功使用后的共享报告期 / Shared report lifetime from last successful use");
        add(m,"beacon.interval_ticks",160,20,2400,"周期报告间隔 / Periodic report interval");
        add(m,"beacon.grid_size",8,1,32,"报告三维网格边长 / Report three-dimensional grid size");
        add(m,"beacon.receiver_range",128,16,512,"同维度接收范围 / Same-dimension recipient range");
        add(m,"beacon.report_ticks",400,20,2400,"每份报告期限 / Individual report lifetime");
        add(m,"beacon.search_radius",8,1,32,"报告点搜索半径 / Report search radius");
        add(m,"beacon.vertical_search",8,1,32,"报告安全落点垂直搜索 / Report safe-floor vertical search");
        add(m,"budgets.candidates",16,4,64,"单次候选暂存 / Candidate limit per scan");
        add(m,"budgets.idle_interval_ticks",10,1,100,"平静检测间隔 / Calm scan interval");
        add(m,"budgets.active_interval_ticks",4,1,40,"识别警觉战斗检测间隔 / Active perception scan interval");
        add(m,"budgets.queries_per_level",64,1,4096,"每维度每tick邻域查询上限 / Neighborhood query limit per dimension per tick");
        add(m,"budgets.queries_per_server",128,1,8192,"每服务器每tick邻域查询上限 / Neighborhood query limit per server per tick");
        add(m,"budgets.event_query_fraction",.25,.05,.5,"声音事件最多占查询配额份额 / Maximum query fraction used by sound events");
        add(m,"budgets.rays_per_level",128,1,8192,"每维度每tick感知射线上限 / Perception ray limit per dimension per tick");
        add(m,"budgets.rays_per_server",256,1,16384,"每服务器每tick感知射线上限 / Perception ray limit per server per tick");
        add(m,"budgets.rays_per_human",2,1,16,"每人每轮快速候选检测上限，身体补查另计入总射线池 / Fast target probes per update; body fallback also consumes the shared ray pool");
        add(m,"budgets.body_rays_per_target",27,1,27,"每目标每轮身体补查射线上限，遇到可见点提前结束 / Body fallback rays per target per update, stopping on visibility");
        add(m,"budgets.notices_per_level",256,1,16384,"每维度每tick线索接收上限 / Clue deliveries per dimension per tick");
        add(m,"budgets.notices_per_server",512,1,32768,"每服务器每tick线索接收上限 / Clue deliveries per server per tick");
        add(m,"budgets.sources_per_level",128,1,8192,"每维度每tick脚步采样源上限 / Movement samples per dimension per tick");
        add(m,"budgets.sources_per_server",256,1,16384,"每服务器每tick脚步采样源上限 / Movement samples per server per tick");
        add(m,"budgets.notice_queue_ticks",10,1,100,"低优先级线索最长排队 / Maximum low-priority delivery delay");
        add(m,"debug.interval_ticks",5,1,40,"观察快照间隔 / Observer snapshot interval");
        add(m,"debug.transition_limit",32,1,64,"最近状态转换容量 / Recent state transition capacity");
        add(m,"debug.transition_ticks",400,20,2400,"最近状态转换期限 / Recent transition lifetime");
        SETTINGS = java.util.Collections.unmodifiableMap(m);
    }
    private static void add(Map<String,Setting> m,String key,double value,double min,double max,String description) {
        m.put(key,new Setting(value,min,max,description));
    }
    private final JsonObject root;
    PerceptionConfig(JsonObject root) { this.root = root.deepCopy(); }
    public static PerceptionConfig get() { return UnifiedConfig.get().perception(); }
    public int integer(String path) { return (int)number(path); }
    public double number(String path) {
        Setting s=SETTINGS.get(path); if(s==null) throw new IllegalArgumentException("Unknown perception setting "+path);
        String[] keys=path.split("\\."); JsonObject section=UnifiedConfig.object(root,keys[0]);
        return UnifiedConfig.number(section,keys[1],s.fallback(),s.min(),s.max());
    }
    public static JsonObject defaults() {
        JsonObject result=new JsonObject();
        SETTINGS.forEach((path,s)->{
            String[] keys=path.split("\\.");
            if(!result.has(keys[0])) result.add(keys[0],new JsonObject());
            JsonObject section=result.getAsJsonObject(keys[0]);
            section.addProperty(keys[1],s.fallback()); section.addProperty("_description_"+keys[1],s.description());
        });
        return result;
    }
}
