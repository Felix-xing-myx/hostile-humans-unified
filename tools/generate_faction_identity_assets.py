"""Deterministic native pixel-art atlas and item meshes. Run with Pillow; no game or network access.

The four glyphs extend the existing faction insignia vocabulary. Generated PNGs are
new assets; source images are not modified. Use --preview for a contact sheet.
"""
from pathlib import Path
import argparse, json, math, zipfile, io
from PIL import Image, ImageDraw, ImageFont, ImageChops
ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/hostile_humans_unified"
FACTIONS = {"crimson": (165,42,54), "azure": (53,77,137), "verdant": (83,133,107), "ashen": (130,116,104)}
SILVER = (235,228,208,255)
GOLD = (205,169,95,255)

def save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=False)

def glyph(draw, faction, x, y, color, scale=1, aspect=1):
    """Approved flat silhouettes in a normalized 16-unit design space, rasterized at native resolution."""
    def poly(points,fill=color): draw.polygon([(round(x+px*scale/aspect),round(y+py*scale)) for px,py in points],fill=fill)
    def line(points,fill=color,width=1): draw.line([(round(x+px*scale/aspect),round(y+py*scale)) for px,py in points],fill=fill,width=max(1,round(width*scale)))
    if faction=="crimson":
        # Central claw is larger; flanks remain separate, shorter cuts.
        poly([(0,13),(2,7),(5,3),(3,10)])
        poly([(4,15.5),(7,5),(11,0),(8,11)])
        poly([(10,14),(12,8),(15,4),(13,11)])
    elif faction=="azure":
        # Upright shield, but stable base and a blunt, horizontal recovery brace.
        poly([(2,1),(13,1),(14,3),(13,12),(10,15),(5,15),(2,12),(1,3)])
        poly([(3.5,3),(11.5,3),(12,4),(11.5,11),(9.5,13),(5.5,13),(3.5,11),(3,4)],(0,0,0,0))
        poly([(6.25,5),(8.75,5),(8.75,6.25),(10.75,6.25),(10.75,9),(8.75,9),(8.75,11.5),(6.25,11.5),(6.25,9),(4.25,9),(4.25,6.25),(6.25,6.25)])
        poly([(6.8,6.8),(8.2,6.8),(8.2,8.2),(6.8,8.2)],(0,0,0,0))
    elif faction=="verdant":
        poly([(0,9),(14,0),(11,5),(2,10),(2,10.5),(14,6),(11,10),
              (2,11.7),(2,12.3),(13,11.5),(9,15),(3,14),(2,15.5),(0,12)])
    elif faction=="ashen":
        # Build each dagger around a continuous axis: broad blade, compact guard, thick short grip.
        # Shared stations overlap slightly, avoiding detached handles after rasterization.
        def dagger(mirror):
            def tr(points):
                result=[]
                for along,side in points:
                    px=.5+(along+side)*math.sqrt(.5)
                    py=(along-side)*math.sqrt(.5)
                    result.append((15-px if mirror else px,py))
                return result
            poly(tr([(0,0),(2,2),(10.5,2),(11,1.4),(11,-1.4),(10.5,-2),(2,-2)]))
            poly(tr([(4,-.5),(7,-.5),(7,.5),(4,.5)]),(0,0,0,0))
            poly(tr([(10.8,-2.5),(12,-2.5),(12,2.5),(10.8,2.5)]))
            poly(tr([(11.8,-1),(18,-1),(18,1),(11.8,1)]))
            poly(tr([(18,-1.4),(20,-1.4),(20,1.4),(18,1.4)]))
        dagger(False);dagger(True)
    else:
        poly([(5,1),(10,1),(14,7),(10,14),(5,14),(1,7)])
        poly([(6,4),(9,4),(11,7),(8,11),(4,7)],(0,0,0,0))

def cloth(grade,resolution=128):
    im=Image.new("RGBA",(resolution*4,resolution));d=ImageDraw.Draw(im);r=resolution
    for tile in range(4):
        x=tile*r
        d.rectangle((x,0,x+r-1,r-1),fill=(88,88,88,255))
        d.rectangle((x,r//8,x+r-1,r*7//8-1),fill=(213,213,213,255))
        d.rectangle((x,r//8,x+r-1,r//8+max(1,r//16)-1),fill=(237,237,237,255))
        d.rectangle((x,r*13//16,x+r-1,r*7//8-1),fill=(146,146,146,255))
    return im

def trim(faction,grade,resolution=128,aspect=2.08):
    # Aspect correction compensates for a square UV tile stretched onto a wide, shallow cuff face.
    im=Image.new("RGBA",(resolution*4,resolution));d=ImageDraw.Draw(im);r=resolution
    metal=GOLD if grade==4 else SILVER
    for tile in range(4):
        x=tile*r
        if grade==4:
            d.rectangle((x,0,x+r-1,r//8-1),fill=GOLD)
            d.rectangle((x,r*7//8,x+r-1,r-1),fill=GOLD)
        if tile in (0,1):
            h=r*3//4;stamp=Image.new("RGBA",(round(h/aspect),h))
            glyph(ImageDraw.Draw(stamp),faction,0,0,metal,h/16,aspect)
            im.alpha_composite(stamp,(x+(r-stamp.width)//2,(r-h)//2))
        else:
            d.rectangle((x,r//8,x+r-1,r*7//8-1),fill=(31,37,39,255))
            w=r//8;start=(r-(grade*2*w-w))//2
            for mark in range(grade):
                xx=x+start+mark*2*w
                d.rectangle((xx,r//4,xx+w-1,r*3//4-1),fill=metal)
    return im

def gui_icon(faction,grade):
    """Dedicated inventory illustration, not a crop of a worn UV atlas or the held mesh."""
    fabric=Image.new("RGBA",(64,64));d=ImageDraw.Draw(fabric)
    d.polygon([(8,21),(18,10),(49,10),(57,18),(57,42),(48,56),(9,56)],fill=(105,105,105,255))
    d.polygon([(9,22),(18,14),(47,14),(54,20),(44,28),(17,28)],fill=(225,225,225,255))
    # A real open cuff silhouette, readable as an item rather than a rectangular colour swatch.
    d.polygon([(18,17),(44,17),(49,21),(42,25),(20,25),(15,22)],fill=(0,0,0,0))
    d.rectangle((10,26,49,56),fill=(200,200,200,255))
    d.polygon([(50,26),(56,22),(56,47),(50,55)],fill=(146,146,146,255))
    metal=Image.new("RGBA",(64,64));d=ImageDraw.Draw(metal);c=GOLD if grade==4 else SILVER
    d.line([(8,22),(9,57),(49,57),(57,48),(57,18),(49,10),(18,10),(8,21)],fill=(40,44,46,255),width=2)
    if grade==4:d.line([(10,26),(49,26),(49,56),(10,56),(10,26)],fill=GOLD,width=2)
    glyph(d,faction,20,28,c,1.25)
    d.rectangle((12,50,47,57),fill=(30,36,38,255))
    start=(60-(grade*8-4))//2
    for i in range(grade):d.rectangle((start+i*8,51,start+i*8+3,56),fill=c)
    return fabric,metal

def tint(im,color):
    fill=Image.new("RGBA",im.size,(*color,255));rgb=ImageChops.multiply(im,fill);rgb.putalpha(im.getchannel("A"));return rgb

def item_mesh(faction,grade):
    texture=f"hostile_humans_unified:item/faction_identity/{faction}/band_{grade}_material"
    def faces(which,rect,tinted=False):
        result={}
        for name in which:
            value={"uv":rect,"texture":"#cloth" if tinted else "#trim"}
            if tinted:value["tintindex"]=0
            result[name]=value
        return result
    elements=[]
    pieces=[([3,5,4],[13,11,5]),([3,5,11],[13,11,12]),([3,5,5],[4,11,11]),([12,5,5],[13,11,11])]
    for start,end in pieces:elements.append({"from":start,"to":end,"faces":faces(["north","south","west","east","up","down"],[8,0,12,16],True)})
    # Outer coloured faces use the full motif layout; thin relief keeps trim away from Z-fighting.
    for name,start,end,uv in [
        ("north",[3,5,3.99],[13,11,4],[0,0,4,16]),
        ("south",[3,5,12],[13,11,12.01],[4,0,8,16]),
        ("west",[2.99,5,4],[3,11,12],[8,0,12,16]),
        ("east",[13,5,4],[13.01,11,12],[12,0,16,16])]:
        elements.append({"from":start,"to":end,"faces":faces([name],uv)})
    # A solid raised clasp/badge gives the held model a real silhouette, rather than a flat sprite.
    # One outer emblem surface prevents doubled contours at small GUI scales.
    return {"textures":{"cloth":texture+"_cloth","trim":texture+"_trim","particle":texture+"_cloth"},"elements":elements,
       "display":{"gui":{"rotation":[12,65,0],"translation":[0,0,0],"scale":[1.32,1.32,1.32]},
       "ground":{"rotation":[0,0,0],"translation":[0,2,0],"scale":[.65,.65,.65]},
       "fixed":{"rotation":[0,0,0],"translation":[0,0,0],"scale":[.85,.85,.85]},
       "thirdperson_righthand":{"rotation":[35,-60,0],"translation":[0,1,0],"scale":[.6,.6,.6]},
       "thirdperson_lefthand":{"rotation":[35,60,0],"translation":[0,1,0],"scale":[.6,.6,.6]},
       "firstperson_righthand":{"rotation":[0,-35,8],"translation":[0,0,0],"scale":[.65,.65,.65]},
       "firstperson_lefthand":{"rotation":[0,35,-8],"translation":[0,0,0],"scale":[.65,.65,.65]}}}

def item_model(faction,grade):
    base=f"hostile_humans_unified:item/faction_identity/{faction}/band_{grade}"
    return {"loader":"forge:separate_transforms","textures":{"particle":base+"_material_cloth"},
            "base":{"parent":base+"_mesh"},"perspectives":{"gui":{"parent":base+"_icon"}}}


def vanilla_egg_layers(jar):
    with zipfile.ZipFile(jar) as archive:
        return tuple(Image.open(io.BytesIO(archive.read("assets/minecraft/textures/item/"+name+".png"))).convert("RGBA").resize((32,32),Image.Resampling.NEAREST)
                     for name in ("spawn_egg","spawn_egg_overlay"))

def egg_detail(faction,tier,boss):
    # Sparse decorations over the real vanilla egg silhouette and spots, never a new shell.
    im=Image.new("RGBA",(32,32));d=ImageDraw.Draw(im);metal=GOLD if boss else SILVER
    glyph(d,faction,11,10,metal,.56)
    if boss:
        d.line([(11,7),(11,4),(14,6),(16,3),(18,6),(21,4),(21,7)],fill=GOLD,width=1)
    for i in range(tier):
        xx=14-(tier-1)*3+i*6
        d.rectangle((xx,25,xx+2,26),fill=(251,227,177,255))
    return im

def write_json(path,data):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+"\n",encoding="utf-8")

def generate(vanilla_layers=None):
    images={}
    for faction,color in {**FACTIONS,"base":(145,145,145)}.items():
        for grade in range(1,5):
            fabric,metal=cloth(grade),trim(faction,grade)
            folder=ASSETS/f"textures/entity/faction_band/{faction}"
            save(fabric,folder/f"grade_{grade}_cloth.png");save(metal,folder/f"grade_{grade}_trim.png")
            if faction!="base":
                item_folder=ASSETS/f"textures/item/faction_identity/{faction}"
                save(cloth(grade,64),item_folder/f"band_{grade}_material_cloth.png")
                save(trim(faction,grade,64,10/6),item_folder/f"band_{grade}_material_trim.png")
                gui_cloth,gui_metal=gui_icon(faction,grade)
                save(gui_cloth,item_folder/f"band_{grade}_icon_cloth.png");save(gui_metal,item_folder/f"band_{grade}_icon_trim.png")
                models=ASSETS/f"models/item/faction_identity/{faction}"
                write_json(models/f"band_{grade}_mesh.json",item_mesh(faction,grade))
                texture_base=f"hostile_humans_unified:item/faction_identity/{faction}/band_{grade}_icon"
                write_json(models/f"band_{grade}_icon.json",{"parent":"minecraft:item/generated","textures":{"layer0":texture_base+"_cloth","layer1":texture_base+"_trim"}})
                write_json(ASSETS/f"models/item/faction_{faction}_band_{grade}.json",item_model(faction,grade))
                comp=tint(gui_cloth,color);comp.alpha_composite(gui_metal);images[f"item_{faction}_{grade}"]=comp
            comp=tint(fabric,color);comp.alpha_composite(metal);images[f"{faction}_{grade}"]=comp
    folder=ASSETS/"textures/item/faction_identity"
    for faction,color in FACTIONS.items():
        for tier in range(1,4):
            for boss in (False,True):
                suffix="_boss" if boss else "";key=f"{faction}_{tier}{suffix}"
                detail=egg_detail(faction,tier,boss);save(detail,folder/("egg_"+key+".png"))
                model={"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/spawn_egg","layer1":"minecraft:item/spawn_egg_overlay","layer2":"hostile_humans_unified:item/faction_identity/egg_"+key}}
                write_json(ASSETS/f"models/item/faction_{faction}_tier{tier}{suffix}_spawn_egg.json",model)
                if vanilla_layers:
                    base,coloured=vanilla_layers
                    comp=tint(base,color);comp.alpha_composite(tint(coloured,tuple(c*2//5 for c in color)))
                    comp.alpha_composite(detail);images["egg_"+key]=comp
    return images

def preview(images,output):
    sheet=Image.new("RGB",(1120,880),(19,26,31));d=ImageDraw.Draw(sheet)
    font=ImageFont.truetype("C:/Windows/Fonts/arial.ttf",22)
    small=ImageFont.truetype("C:/Windows/Fonts/arial.ttf",15)
    d.text((28,18),"HOSTILE HUMANS UNIFIED / FIELD IDENTIFICATION",font=font,fill=(230,230,214))
    for row,(faction,color) in enumerate(FACTIONS.items()):
        y=62+row*199;d.line((24,y,1094,y),fill=(64,72,77));d.text((28,y+12),faction.upper(),font=font,fill=color)
        for grade in range(1,5):
            x=210+(grade-1)*215
            # Front and clasp-side tiles are shown at integer pixel scale, preserving the real atlas.
            band=images[f"{faction}_{grade}"].crop((0,0,128,128)).resize((128,64),Image.Resampling.NEAREST)
            sheet.paste(band,(x,y+28),band)
            d.text((x+137,y+45),"G"+str(grade)+(" / COMMAND" if grade==4 else ""),font=small,fill=(203,207,202))
        for tier in range(1,4):
            for boss in (False,True):
                x=210+(tier-1)*285+(124 if boss else 0)
                egg=images[f"egg_{faction}_{tier}"+("_boss" if boss else "")].resize((80,80),Image.Resampling.NEAREST)
                sheet.paste(egg,(x,y+105),egg)
                d.text((x+7,y+182),str(tier)+(" BOSS" if boss else " REG"),font=small,fill=(187,192,190))
    save(sheet,output)

def render_mesh(model, color, size=160):
    """Offline orthographic check of the shipped JSON mesh/UVs, not a screenshot of Minecraft."""
    textures={k:Image.open(ASSETS/("textures/"+v.split(":",1)[1]+".png")).convert("RGBA")
              for k,v in model["textures"].items() if k!="particle"}
    out=Image.new("RGBA",(size,size));depth=[float("inf")]*(size*size)
    ax,ay=math.radians(model["display"]["gui"]["rotation"][0]),math.radians(-model["display"]["gui"]["rotation"][1])
    model_scale=model["display"]["gui"]["scale"][0]
    def project(p):
        x,y,z=[v-8 for v in p]
        x,z=x*math.cos(ay)+z*math.sin(ay),-x*math.sin(ay)+z*math.cos(ay)
        y,z=y*math.cos(ax)-z*math.sin(ax),y*math.sin(ax)+z*math.cos(ax)
        return size/2+x*size/17*model_scale,size/2-y*size/17*model_scale,z
    def triangle(points,uv,tex,tinted,shade):
        p0,p1,p2=points;u0,u1,u2=uv
        den=(p1[1]-p2[1])*(p0[0]-p2[0])+(p2[0]-p1[0])*(p0[1]-p2[1])
        if abs(den)<1e-6:return
        for yy in range(max(0,int(min(p[1] for p in points))),min(size,int(max(p[1] for p in points))+1)):
            for xx in range(max(0,int(min(p[0] for p in points))),min(size,int(max(p[0] for p in points))+1)):
                a=((p1[1]-p2[1])*(xx+.5-p2[0])+(p2[0]-p1[0])*(yy+.5-p2[1]))/den
                b=((p2[1]-p0[1])*(xx+.5-p2[0])+(p0[0]-p2[0])*(yy+.5-p2[1]))/den;c=1-a-b
                if min(a,b,c)<-1e-6:continue
                z=a*p0[2]+b*p1[2]+c*p2[2];idx=yy*size+xx
                if z>=depth[idx]:continue
                u=a*u0[0]+b*u1[0]+c*u2[0];v=a*u0[1]+b*u1[1]+c*u2[1]
                pixel=tex.getpixel((min(tex.width-1,max(0,int(u*tex.width/16))),min(tex.height-1,max(0,int(v*tex.height/16)))))
                if pixel[3]<128:continue
                rgb=tuple(int(pixel[k]*shade*(color[k]/255 if tinted else 1)) for k in range(3))
                out.putpixel((xx,yy),(*rgb,255));depth[idx]=z
    for element in model["elements"]:
        x0,y0,z0=element["from"];x1,y1,z1=element["to"]
        quads={"north":[(x1,y1,z0),(x0,y1,z0),(x0,y0,z0),(x1,y0,z0)],
          "south":[(x0,y1,z1),(x1,y1,z1),(x1,y0,z1),(x0,y0,z1)],
          "east":[(x1,y1,z1),(x1,y1,z0),(x1,y0,z0),(x1,y0,z1)],
          "west":[(x0,y1,z0),(x0,y1,z1),(x0,y0,z1),(x0,y0,z0)],
          "up":[(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)],
          "down":[(x0,y0,z1),(x1,y0,z1),(x1,y0,z0),(x0,y0,z0)]}
        for face,desc in element["faces"].items():
            pts=[project(p) for p in quads[face]];u0,v0,u1,v1=desc["uv"];uv=[(u0,v0),(u1,v0),(u1,v1),(u0,v1)]
            tex=textures[desc["texture"][1:]];shade={"up":1,"down":.5,"north":.92,"south":.72,"east":.76,"west":.82}[face]
            for indices in [(0,1,2),(0,2,3)]:triangle([pts[j] for j in indices],[uv[j] for j in indices],tex,desc.get("tintindex")==0,shade)
    return out

def mesh_sheet(output):
    sheet=Image.new("RGB",(900,820),(19,26,31));d=ImageDraw.Draw(sheet)
    font=ImageFont.truetype("C:/Windows/Fonts/arial.ttf",20)
    d.text((20,15),"CUFF MESH / OFFLINE JSON + UV PREVIEW",font=font,fill=(225,225,211))
    for row,(faction,color) in enumerate(FACTIONS.items()):
        y=50+row*190;d.text((20,y),faction.upper(),font=font,fill=color)
        for grade in range(1,5):
            im=render_mesh(item_mesh(faction,grade),color)
            sheet.paste(im,(130+(grade-1)*185,y+8),im)
            d.text((180+(grade-1)*185,y+165),"G"+str(grade),font=font,fill=(186,192,188))
    save(sheet,output)

def small_sheet(output):
    sheet=Image.new("RGB",(900,600),(19,26,31));d=ImageDraw.Draw(sheet)
    font=ImageFont.truetype("C:/Windows/Fonts/arial.ttf",19)
    d.text((18,12),"HELD MESH AT 16 PX / GUI USES SEPARATE ICONS",font=font,fill=(230,230,216))
    for row,(faction,color) in enumerate(FACTIONS.items()):
        y=48+row*135;d.text((18,y+36),faction.upper(),font=font,fill=color)
        for grade in range(1,5):
            x=180+(grade-1)*175
            im=render_mesh(item_mesh(faction,grade),color,size=16).resize((112,112),Image.Resampling.NEAREST)
            sheet.paste(im,(x,y),im)
            d.text((x+42,y+110),"G"+str(grade),font=font,fill=(193,198,194))
    save(sheet,output)

def inventory_sheet(images,output):
    sheet=Image.new("RGB",(900,790),(19,26,31));d=ImageDraw.Draw(sheet)
    font=ImageFont.truetype("C:/Windows/Fonts/arial.ttf",20)
    d.text((20,14),"DEDICATED INVENTORY ICONS / 64 PX + 16 PX CHECK",font=font,fill=(230,230,216))
    for row,(faction,color) in enumerate(FACTIONS.items()):
        y=55+row*180;d.text((15,y+40),faction.upper(),font=font,fill=color)
        for grade in range(1,5):
            x=155+(grade-1)*182;im=images[f"item_{faction}_{grade}"]
            large=im.resize((112,112),Image.Resampling.NEAREST);sheet.paste(large,(x,y),large)
            tiny=im.resize((16,16),Image.Resampling.NEAREST).resize((48,48),Image.Resampling.NEAREST)
            sheet.paste(tiny,(x+32,y+116),tiny)
    save(sheet,output)

if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("--preview",type=Path)
    parser.add_argument("--vanilla-client-jar",type=Path);args=parser.parse_args()
    if args.preview and not args.vanilla_client_jar:parser.error("--preview requires --vanilla-client-jar for truthful vanilla egg layers")
    images=generate(vanilla_egg_layers(args.vanilla_client_jar) if args.vanilla_client_jar else None)
    if args.preview:
        preview(images,args.preview)
        mesh_sheet(args.preview.with_name("cuff-models.png"))
        small_sheet(args.preview.with_name("cuff-16px.png"))
        inventory_sheet(images,args.preview.with_name("inventory-icons.png"))
    print("Generated four-faction identity atlases, 16 cuff meshes and 24 egg models.")
