"""Synchronize the shipped perception catalog from the single Java setting registry."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "src/main/java/club/someoneice/humangunner/PerceptionConfig.java").read_text(encoding="utf-8-sig")
settings = re.findall(r'add\(m,"([^"]+)",([.\d]+),([.\d]+),([.\d]+),"([^"]+)"\);', source)
if not settings:
    raise SystemExit("No perception setting declarations found")
catalog = {"_说明_中文": "服务器统一感知、识别、记忆、声音、信标和诊断配额；时间单位为服务器tick，20tick约1秒，重启后生效。",
           "_description_en": "Server perception, recognition, memory, sounds, beacon and diagnostic budgets. Times are server ticks (20 per second); restart to apply."}
for path, default, low, high, description in settings:
    section, key = path.split(".")
    value = float(default)
    group = catalog.setdefault(section, {})
    group[key] = int(value) if value.is_integer() else value
    chinese, english = description.split(" / ", 1)
    group[f"_说明_{key}_中文"] = f"{chinese}；范围{low}–{high}。"
    group[f"_description_{key}_en"] = f"{english}; range {low}–{high}."
target = ROOT / "src/main/resources/defaults/hostile_humans_unified.json"
text = target.read_text(encoding="utf-8-sig")
data = json.loads(text)
if data.get("perception") != catalog:
    if "perception" in data:
        data["perception"] = catalog
        text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    else:
        end = text.rfind("}")
        section = json.dumps(catalog, ensure_ascii=False, indent=2)
        section = "\n".join("  " + line for line in section.splitlines())
        text = text[:end].rstrip() + ',\n  "perception": ' + section.lstrip() + "\n}\n"
    target.write_text(text, encoding="utf-8")
print(f"Perception catalog: {len(settings)} settings")
