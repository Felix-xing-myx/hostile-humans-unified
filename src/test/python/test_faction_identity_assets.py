"""Resource/geometry contracts; no Java compiler or game is launched."""
from source_paths import SourcePath as Path
import hashlib,json,struct,unittest,zlib
from functools import lru_cache
ROOT=Path(__file__).resolve().parents[3]
ASSETS=ROOT/"src/main/resources/assets/hostile_humans_unified"
FACTIONS=("crimson","azure","verdant","ashen")
def model(name):return json.loads((ASSETS/"models/item"/(name+".json")).read_text(encoding="utf-8"))
def mesh(name):
 value=model(name)
 return model(value["base"]["parent"].split(":item/",1)[1]) if value.get("loader")=="forge:separate_transforms" else value
def texture(ref):return ASSETS/"textures"/(ref.split(":",1)[1]+".png")
def dimensions(path):
 data=path.read_bytes();assert data[:8]==b"\x89PNG\r\n\x1a\n"
 return struct.unpack(">II",data[16:24])
@lru_cache(maxsize=32)
def rgba(path):
 data=path.read_bytes();pos=8;compressed=bytearray();info=None
 while pos<len(data):
  size=struct.unpack(">I",data[pos:pos+4])[0];kind=data[pos+4:pos+8];chunk=data[pos+8:pos+8+size]
  if kind==b"IHDR":info=struct.unpack(">IIBBBBB",chunk)
  elif kind==b"IDAT":compressed.extend(chunk)
  pos+=12+size
 w,h,depth,mode,_,_,interlace=info
 assert depth==8 and mode==6 and interlace==0,"expected ordinary RGBA PNG"
 raw=zlib.decompress(compressed);stride=w*4;out=bytearray();previous=bytearray(stride);offset=0
 for _ in range(h):
  filt=raw[offset];row=bytearray(raw[offset+1:offset+1+stride]);offset+=stride+1
  for n in range(stride):
   a=row[n-4] if n>=4 else 0;b=previous[n];c=previous[n-4] if n>=4 else 0
   if filt==0:predictor=0
   elif filt==1:predictor=a
   elif filt==2:predictor=b
   elif filt==3:predictor=(a+b)//2
   elif filt==4:
    estimate=a+b-c;distances=(abs(estimate-a),abs(estimate-b),abs(estimate-c))
    predictor=(a,b,c)[distances.index(min(distances))]
   else:raise AssertionError("invalid PNG filter")
   row[n]=(row[n]+predictor)&255
  out.extend(row);previous=row
 return w,h,bytes(out)

def components(path):
 w,h,pixels=rgba(path)
 points={(x,y) for y in range(h) for x in range(h) if pixels[(y*w+x)*4+3]>=128}
 result=[]
 while points:
  first=points.pop();part={first};pending=[first]
  while pending:
   x,y=pending.pop()
   for dx,dy in ((-1,-1),(-1,0),(-1,1),(0,-1),(0,1),(1,-1),(1,0),(1,1)):
    nxt=x+dx,y+dy
    if nxt in points:points.remove(nxt);part.add(nxt);pending.append(nxt)
  result.append(part)
 return result

class FactionIdentityAssets(unittest.TestCase):
 def test_all_sixteen_cuffs_are_real_hollow_models(self):
  for faction in FACTIONS:
   for grade in range(1,5):
    value=mesh(f"faction_{faction}_band_{grade}")
    self.assertGreaterEqual(len(value["elements"]),8)
    self.assertNotEqual(value.get("parent"),"minecraft:item/generated")
    self.assertFalse(any(all(a<8<b for a,b in zip(e["from"],e["to"])) for e in value["elements"]),"cuff centre must remain empty")
    for ref in value["textures"].values():self.assertTrue(texture(ref).exists(),ref)
 def test_neutral_hardware_is_not_faction_tinted(self):
  for faction in FACTIONS:
   for grade in range(1,5):
    for element in mesh(f"faction_{faction}_band_{grade}")["elements"]:
     for face in element["faces"].values():
      self.assertEqual(face.get("tintindex"),0 if face["texture"]=="#cloth" else None)
 def test_worn_atlases_cover_builtins_and_unknown_faction_fallback(self):
  for faction in FACTIONS+("base",):
   hashes=[]
   for grade in range(1,5):
    for part in ("cloth","trim"):
     path=ASSETS/f"textures/entity/faction_band/{faction}/grade_{grade}_{part}.png"
     self.assertEqual(dimensions(path),(512,128))
    hashes.append(hashlib.sha256((ASSETS/f"textures/entity/faction_band/{faction}/grade_{grade}_trim.png").read_bytes()).hexdigest())
   self.assertEqual(len(set(hashes)),4,"every rank needs a distinct plaque")
 def test_all_twentyfour_eggs_have_matching_faction_tier_and_boss_resources(self):
  seen=set()
  for faction in FACTIONS:
   for tier in range(1,4):
    for boss in (False,True):
     suffix="_boss" if boss else ""
     value=model(f"faction_{faction}_tier{tier}{suffix}_spawn_egg")
     self.assertEqual(value["parent"],"minecraft:item/generated")
     self.assertEqual(value["textures"]["layer1"],"minecraft:item/spawn_egg_overlay")
     detail=texture(value["textures"]["layer2"])
     self.assertEqual(detail.stem,f"egg_{faction}_{tier}{suffix}")
     self.assertEqual(value["textures"]["layer0"],"minecraft:item/spawn_egg")
     self.assertEqual(dimensions(detail),(32,32))
     seen.add(hashlib.sha256(detail.read_bytes()).hexdigest())
  self.assertEqual(len(seen),24,"tier/boss must never collapse to the same resource")
 def test_mesh_uvs_and_display_scales_are_bounded(self):
  for faction in FACTIONS:
   for grade in range(1,5):
    value=mesh(f"faction_{faction}_band_{grade}")
    for element in value["elements"]:
     self.assertTrue(all(-16<=a<b<=32 for a,b in zip(element["from"],element["to"])))
     for face in element["faces"].values():
      self.assertTrue(all(0<=v<=16 for v in face["uv"]))
    self.assertIn("firstperson_lefthand",value["display"])
    self.assertIn("thirdperson_lefthand",value["display"])
 def test_inventory_illustrations_are_separate_from_worn_and_held_textures(self):
  for faction in FACTIONS:
   for grade in range(1,5):
    root=model(f"faction_{faction}_band_{grade}")
    self.assertEqual(root["loader"],"forge:separate_transforms")
    gui=model(root["perspectives"]["gui"]["parent"].split(":item/",1)[1])
    self.assertEqual(gui["parent"],"minecraft:item/generated")
    for ref in gui["textures"].values():
     self.assertIn(":item/faction_identity/",ref)
     self.assertEqual(dimensions(texture(ref)),(64,64))
    for ref in mesh(f"faction_{faction}_band_{grade}")["textures"].values():
     self.assertIn(":item/faction_identity/",ref)
     self.assertEqual(dimensions(texture(ref)),(256,64))
 def test_rank_stripes_are_countable_on_both_short_faces(self):
  for faction in FACTIONS+("base",):
   for grade in range(1,5):
    path=ASSETS/f"textures/entity/faction_band/{faction}/grade_{grade}_trim.png"
    w,h,pixels=rgba(path)
    for tile in (2,3):
     row=[pixels[((h//2)*w+tile*h+x)*4:((h//2)*w+tile*h+x)*4+4] for x in range(h)]
     marks=[p[3]>=128 and p[0]>=150 for p in row]
     self.assertEqual(sum(on and (i==0 or not marks[i-1]) for i,on in enumerate(marks)),grade,(faction,grade,tile))
 def test_inventory_and_egg_rank_marks_survive_their_final_layers(self):
  for faction in FACTIONS:
   for grade in range(1,5):
    path=ASSETS/f"textures/item/faction_identity/{faction}/band_{grade}_icon_trim.png"
    w,h,pixels=rgba(path)
    marks=[pixels[(53*w+x)*4+3]>=128 and pixels[(53*w+x)*4]>=150 for x in range(12,48)]
    self.assertEqual(sum(on and (i==0 or not marks[i-1]) for i,on in enumerate(marks)),grade)
   for tier in range(1,4):
    for suffix in ("","_boss"):
     path=ASSETS/f"textures/item/faction_identity/egg_{faction}_{tier}{suffix}.png"
     w,h,pixels=rgba(path)
     marks=[pixels[(26*w+x)*4+3]>=128 and pixels[(26*w+x)*4]>=245 for x in range(8,24)]
     self.assertEqual(sum(on and (i==0 or not marks[i-1]) for i,on in enumerate(marks)),tier)
 def test_approved_silhouettes_are_not_fragmented_or_accidentally_joined(self):
  def parts(faction):return components(ASSETS/f"textures/entity/faction_band/{faction}/grade_1_trim.png")
  claws=sorted(parts("crimson"),key=lambda part:sum(x for x,y in part)/len(part))
  self.assertEqual(len(claws),3,"exactly three separated claw marks")
  self.assertGreater(len(claws[1]),len(claws[0])*1.2)
  self.assertGreater(len(claws[1]),len(claws[2])*1.2)
  self.assertEqual(len(parts("azure")),2,"shield frame and brace require clear separating space")
  self.assertEqual(len(parts("verdant")),1,"wing feathers must share their root")
  self.assertEqual(len(parts("ashen")),1,"crossed dagger blades, guards and handles must connect")
 def test_runtime_keeps_server_policy_and_parent_limb_animation(self):
  java=ROOT/"src/main/java/dev/felix/hostilehumans"
  layer=(java/"FactionVisualLayer.java").read_text(encoding="utf-8")
  for guard in ("entity.isInvisible()", "entity.isSpectator()", "style().equals(\"none\")", "limb.translateAndRotate(poses)", "finally { poses.popPose(); }"):
   self.assertIn(guard,layer)
  self.assertNotIn("new float[]",layer)
  self.assertIn("if (hired) drawHired",layer)
  self.assertIn("getParentModel().rightArm, chest ? 1.0F : .5F, 5.08F",layer)
  self.assertNotIn("HIRED, 0xFFFFFF, .035F",layer)
  catalog=(java/"FactionBandAppearance.java").read_text(encoding="utf-8")
  self.assertIn(' : "base"',catalog)
  self.assertIn("Math.max(1, Math.min(4, grade))",catalog)
if __name__=="__main__":unittest.main()
