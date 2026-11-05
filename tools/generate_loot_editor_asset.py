"""Loot tool sprite in the established 32px author-tool style."""
from pathlib import Path
import json
from generate_facility_assets import Tile
root=Path(__file__).resolve().parents[1]/"src/main/resources/assets/hostile_humans_unified"
t=Tile();t.rect(3,6,26,23,(16,24,30));t.rect(5,8,22,19,(63,78,84));t.rect(7,10,18,15,(26,39,46))
t.rect(5,7,22,2,(220,182,98));t.rect(5,25,22,2,(123,101,61));t.rect(7,15,18,2,(173,143,81));t.rect(14,14,4,6,(233,197,109));t.rect(15,16,2,2,(48,48,41))
t.rect(19,2,10,10,(27,39,44));t.rect(20,3,8,8,(139,197,193));t.rect(22,5,2,2,(28,53,61));t.rect(25,8,2,2,(28,53,61))
t.line(3,28,1,30,(137,182,183),2)
t.save(root/"textures/item/loot_editor.png")
p=root/"models/item/loot_editor.json";p.parent.mkdir(parents=True,exist_ok=True)
p.write_text(json.dumps({"parent":"minecraft:item/generated","textures":{"layer0":"hostile_humans_unified:item/loot_editor"}},indent=2)+"\n",encoding="utf-8")
