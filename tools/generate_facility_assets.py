"""Original 32px development assets. No downloaded artwork or runtime generation."""
from pathlib import Path
import argparse, json, math, struct, zlib
SIZE=32
NS="hostile_humans_unified"
COLORS={"crimson":(187,65,66),"azure":(97,158,194),"verdant":(110,157,91),"ashen":(163,151,169)}
class Tile:
    def __init__(self,bg=(0,0,0,0)):
        self.p=[[(tuple(bg)+(255,))[:4] for x in range(SIZE)] for y in range(SIZE)]
    def rect(self,x,y,w,h,c):
        c=(tuple(c)+(255,))[:4]
        for yy in range(max(0,y),min(SIZE,y+h)):
            for xx in range(max(0,x),min(SIZE,x+w)):self.p[yy][xx]=c
    def line(self,x,y,u,v,c,width=1):
        n=max(abs(u-x),abs(v-y),1)
        for i in range(n+1):self.rect(round(x+(u-x)*i/n),round(y+(v-y)*i/n),width,width,c)
    def disc(self,x,y,r,c):
        for yy in range(y-r,y+r+1):
            for xx in range(x-r,x+r+1):
                if (xx-x)**2+(yy-y)**2<=r*r:self.rect(xx,yy,1,1,c)
    def save(self,path):
        path.parent.mkdir(parents=True,exist_ok=True)
        raw=b"".join(bytes([0])+bytes(v for pixel in row for v in pixel) for row in self.p)
        def chunk(k,data):return struct.pack(">I",len(data))+k+data+struct.pack(">I",zlib.crc32(k+data)&0xffffffff)
        path.write_bytes(bytes([137,80,78,71,13,10,26,10])+chunk(b"IHDR",struct.pack(">IIBBBBB",32,32,8,6,0,0,0))+chunk(b"IDAT",zlib.compress(raw,9))+chunk(b"IEND",b""))
def emblem(t,f,c):
    if f=="crimson":
        for x,y,h in [(9,12,9),(15,8,16),(21,12,9)]:t.line(x,y,x-2,y+h,c,2)
    elif f=="azure":
        t.rect(8,8,17,17,c);t.rect(11,11,11,11,(28,40,48));t.rect(14,9,5,14,c);t.rect(9,14,15,4,c)
    elif f=="verdant":
        for y,x in [(10,6),(15,9),(20,12)]:t.line(x,y,15,25,c,2);t.line(30-x,y,17,25,c,2)
    else:
        t.line(8,8,24,24,c,3);t.line(23,8,7,24,c,3);t.line(7,21,12,26,(220,212,196),2);t.line(20,26,25,21,(220,212,196),2)
def panel(accent,kind,face,faction=None):
    t=Tile((39,45,49));t.rect(1,1,30,30,(60,67,70));t.rect(3,3,26,26,(29,37,42))
    for x,y in [(2,2),(28,2),(2,28),(28,28)]:t.rect(x,y,2,2,(137,143,143))
    if face=="bottom":
        for x in (7,14,21):t.rect(x,5,3,22,(74,81,82))
    elif faction:
        if face=="top":t.rect(5,5,22,22,(36,43,47))
        t.rect(4,4,24,2,accent);emblem(t,faction,accent)
    elif kind=="faction_task_board":
        t.rect(2,2,28,28,(89,65,43));t.rect(4,4,24,24,(151,120,74))
        if face=="side":
            for x,y,w,h in [(6,7,9,13),(17,11,9,14)]:
                t.rect(x,y,w,h,(215,205,168));t.rect(x+2,y+4,w-4,1,(118,106,84));t.rect(x+2,y+7,w-4,1,(118,106,84));t.rect(x+3,y,2,2,(165,82,65))
        else:
            for y in (8,16,24):t.rect(4,y,24,1,(106,80,47))
    elif kind=="training_center":
        if face=="top":
            for x in (8,16,24):t.disc(x,16,4,accent);t.disc(x,16,2,(35,43,48))
        else:
            for x in (6,13,20):t.rect(x,9,5,13,accent);t.rect(x+1,11,3,9,(52,65,68))
    else:
        for x in (8,15,22):t.line(x,7,x,25,accent)
        for y in (9,16,23):t.line(6,y,26,y,accent)
        t.rect(13,13,7,7,(222,192,117));t.rect(15,15,3,3,(35,43,48))
    return t
def item(name,accent=(200,180,127),faction=None,denom=None):
    t=Tile()
    if denom:
        metal={"copper":(194,121,79),"silver":(186,199,204),"gold":(226,190,87)}[denom]
        t.disc(16,16,13,(55,51,47));t.disc(16,15,12,metal);t.disc(16,15,9,(53,61,65));emblem(t,faction,accent)
    elif name=="faction_wallet":
        t.rect(4,8,24,19,(37,27,24));t.rect(5,9,22,16,(112,79,54));t.rect(6,11,20,1,(174,135,81));t.rect(18,16,11,6,(59,43,36));t.rect(22,18,3,2,(216,184,112))
        for i,c in enumerate(COLORS.values()):t.rect(7+i*4,23,3,2,c)
    elif name=="parley_flag":
        t.line(8,4,8,28,(155,126,84),2);t.rect(10,5,17,11,(225,226,212));t.rect(24,14,3,4,(225,226,212))
    elif name=="training_supplies":
        t.rect(6,7,21,22,(51,45,32));t.rect(7,8,19,19,(154,127,73));t.rect(11,6,3,23,(64,66,53));t.rect(21,6,3,23,(64,66,53));t.rect(13,13,9,8,(207,193,136));t.rect(16,11,3,12,(207,193,136))
    elif name=="settlement_selection_tool":
        t.line(7,26,23,10,(49,40,29),5);t.line(7,25,23,9,(194,158,86),3);t.rect(18,3,11,11,(82,145,173));t.rect(21,6,5,5,(34,52,60));t.rect(26,2,4,4,(214,222,205))
    else:
        c=(109,172,195) if name=="growth_potential_token" else (194,156,215)
        t.disc(16,16,12,(45,52,61));t.disc(16,15,10,c);t.disc(16,15,7,(45,52,61))
        spokes=4 if name=="growth_potential_token" else 5
        for i in range(spokes):
            a=2*math.pi*i/spokes-math.pi/2;t.line(16,15,round(16+8*math.cos(a)),round(15+8*math.sin(a)),c,2)
    return t
def generate(root):
    assets=root/"src/main/resources/assets"/NS
    def write(rel,obj):
        p=assets/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,indent=2)+"\n",encoding="utf8")
    blocks={"settlement_information":(None,(158,180,183)),"training_center":(None,(190,166,101)),"faction_trader":(None,(162,177,130)),"faction_task_board":(None,(203,182,129))}
    for f,c in COLORS.items():blocks["settlement_control_"+f]=(f,c);blocks["faction_broker_"+f]=(f,c)
    for name,(f,c) in blocks.items():
        for face in ("top","bottom","side"):panel(c,name,face,f).save(assets/"textures/block"/(name+"_"+face+".png"))
        write("models/block/"+name+".json",{"parent":"minecraft:block/cube_bottom_top","textures":{k:NS+":block/"+name+"_"+k for k in ("top","bottom","side")}})
        write("blockstates/"+name+".json",{"variants":{"":{"model":NS+":block/"+name}}})
        write("models/item/"+name+".json",{"parent":NS+":block/"+name})
    for name in ("settlement_selection_tool","training_supplies","growth_potential_token","primary_expansion_token","faction_wallet","parley_flag"):
        item(name).save(assets/"textures/item"/(name+".png"));write("models/item/"+name+".json",{"parent":"minecraft:item/generated","textures":{"layer0":NS+":item/"+name}})
    for f,c in COLORS.items():
        for d in ("copper","silver","gold"):
            name="coin_"+f+"_"+d;item(name,c,f,d).save(assets/"textures/item"/(name+".png"));write("models/item/"+name+".json",{"parent":"minecraft:item/generated","textures":{"layer0":NS+":item/"+name}})
    curios=root/"src/main/resources/data/hostile_humans_unified/curios/slots/wallet.json";curios.parent.mkdir(parents=True,exist_ok=True);curios.write_text(json.dumps({"size":1,"order":220,"icon":NS+":item/faction_wallet","validators":["curios:tag"]},indent=2)+"\n",encoding="utf8")
    entities=root/"src/main/resources/data/hostile_humans_unified/curios/entities/player.json";entities.parent.mkdir(parents=True,exist_ok=True);entities.write_text(json.dumps({"entities":["minecraft:player"],"slots":["wallet"]},indent=2)+"\n",encoding="utf8")
    atlas=root/"src/main/resources/assets/minecraft/atlases/blocks.json";atlas.parent.mkdir(parents=True,exist_ok=True)
    atlas_data=json.loads(atlas.read_text(encoding="utf8")) if atlas.exists() else {"sources":[]}
    icon={"type":"minecraft:single","resource":NS+":item/faction_wallet"}
    if icon not in atlas_data["sources"]:atlas_data["sources"].append(icon)
    atlas.write_text(json.dumps(atlas_data,indent=2)+"\n",encoding="utf8")
    tag=root/"src/main/resources/data/curios/tags/items/wallet.json";tag.parent.mkdir(parents=True,exist_ok=True);tag.write_text(json.dumps({"replace":False,"values":[NS+":faction_wallet"]},indent=2)+"\n",encoding="utf8")
    print("Generated",len(blocks),"block models/faces and 18 item icons")
if __name__=="__main__":generate(Path(__file__).resolve().parents[1])
