"""Export Markdown default-value appendices; never changes runtime configuration.

Uses the packaged JSON and its current TierConfigProjection storage layout.
This is a documentation exporter, not a replacement for the Java config loader.
Review this projection whenever TierConfigProjection changes. Handwritten chapters
and Java constants must be maintained separately.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[1]
DEFAULTS = PROJECT / "src/main/resources/defaults/hostile_humans_unified.json"
INTEGRATIONS = PROJECT / "src/main/resources/defaults/equipment_integrations.json"
RANKS = ("roamer", "tier1", "tier2", "tier3")


def plain(value):
    if isinstance(value, dict):
        return {k: plain(v) for k, v in value.items() if not k.startswith("_")}
    if isinstance(value, list):
        return [plain(v) for v in value]
    return value


def project_defaults(root):
    """Mirror documented rank ownership without executing/loading Minecraft."""
    global_data = copy.deepcopy(root)
    for group in ("tiers", "equipment_loadouts", "tier_ai", "_tier_file_metadata"):
        global_data.pop(group, None)
    global_data["ai"] = {k: v for k, v in root["ai"].items()
                         if k in ("enabled", "_说明_enabled_中文", "_说明_中文")}
    tacz = global_data["tacz"]
    for rank in RANKS:
        tacz.pop(rank + "_gun_chance", None)
    for group in ("tier_damage_multipliers", "tier_gun_type_weights"):
        tacz.pop(group, None)
    global_data["spawning"]["progression"].pop("first_spawn_day_by_tier", None)
    result = {"global": global_data}
    for rank in RANKS:
        data = copy.deepcopy(root["tiers"][rank])
        data["schema_version"] = 2
        data["equipment_loadout"] = copy.deepcopy(root["equipment_loadouts"][rank])
        data.setdefault("spawning", {})["first_spawn_day"] = root["spawning"]["progression"]["first_spawn_day_by_tier"][rank]
        data["tacz"] = copy.deepcopy(root.get("_tier_file_metadata", {}).get(rank, {}).get("tacz", {}))
        data["tacz"]["gun_chance"] = root["tacz"][rank + "_gun_chance"]
        data["tacz"]["damage_multiplier"] = root["tacz"]["tier_damage_multipliers"][rank]
        data["tacz"]["gun_type_weights"] = root["tacz"]["tier_gun_type_weights"][rank]
        data["ai"] = copy.deepcopy(root.get("tier_ai", {}).get(rank, root["ai"]))
        data["ai"].pop("enabled", None)
        data.pop("equipment_growth", None)
        result[rank] = data
    return result


def rows(value, prefix="", note=""):
    if isinstance(value, dict):
        if not plain(value):
            yield prefix, "{}", note or "空对象。"
        for key, item in value.items():
            if key.startswith("_"):
                continue
            help_text = value.get("_说明_" + key + "_中文", "")
            path = prefix + "." + key if prefix else key
            yield from rows(item, path, help_text or note)
    elif isinstance(value, list):
        if not value:
            yield prefix, "[]", note or "空候选列表，不代表缺失字段。"
        for index, item in enumerate(value):
            yield from rows(item, f"{prefix}[{index}]", note)
    else:
        yield prefix, json.dumps(value, ensure_ascii=False), note


def cell(text):
    return str(text).replace("|", "&#124;").replace("\n", "<br>").replace("`", "&#96;")


def render(name, data, source_hash):
    title = "默认值-" + name
    output = [f"# {title}", "", "返回 [[配置索引]] · [[00-总览与导航]]", "",
              f"配置文件：`config/hostile_humans_unified/{name}.json`。以下为源码打包默认值；不是任何已运行实例的实测值。", "",
              f"默认资源 SHA-256：`{source_hash}`。由 `tools/export_knowledge_defaults.py` 生成。", "",
              "路径中的 `[0]` 表示列表第一项。权重是相对抽签份额；只有说明明确写概率时才按概率理解。20 tick=1秒，2点生命=1颗心。", "",
              "字段解释沿用资源中文帮助；实际行为还要结合玩法章节和Java读取限制。盾耐久损耗冷却当前为2 tick，见 [[盾牌防御]]；本次已同步更正源码旧帮助。", ""]
    count = 0
    for group, value in data.items():
        if group.startswith("_"):
            continue
        output += [f"## {group}", ""]
        if isinstance(value, dict) and value.get("_说明_中文"):
            output += [value["_说明_中文"], ""]
        output += ["| 字段路径 | 默认值 | 作用与单位 |", "|---|---|---|"]
        for path, val, help_text in rows(value, group, data.get("_说明_" + group + "_中文", "")):
            output.append(f"| `{path}` | `{cell(val)}` | {cell(help_text or '此项是列表/映射内的具体值，含义见所属分组及玩法章节。')} |")
            count += 1
        output.append("")
    output += ["## 来源", "", "- `src/main/resources/defaults/hostile_humans_unified.json`", "- `src/main/java/club/someoneice/humangunner/TierConfigProjection.java`：实际五文件归属。", "- `SplitConfigFiles.java`、`UnifiedConfig.java`：加载、缺项与有效值规则。", "", f"本页记录 {count} 个默认叶子值（列表逐项展开，含空列表/对象）。", ""]
    return "\n".join(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--vault", type=Path, default=Path(r"E:\个人知识库\Hostile Humans Unified"))
    parser.add_argument("--check", action="store_true", help="Check generated Markdown without writing")
    args = parser.parse_args()
    root = json.loads(DEFAULTS.read_text(encoding="utf-8"))
    source_hash = hashlib.sha256(DEFAULTS.read_bytes()).hexdigest().upper()
    output = args.vault / "04-配置与数值" / "默认值附录"
    pages = {output / f"默认值-{name}.md": render(name, data, source_hash)
             for name, data in project_defaults(root).items()}
    integrations = json.loads(INTEGRATIONS.read_text(encoding="utf-8"))
    pages[output / "装备联动导入候选.md"] = "\n".join([
        "# 装备联动导入候选", "", "返回 [[配置索引]] · [[联动模组总表]]", "",
        "以下完整抄录当前装备联动导入表。它是安装相应模组后首次追加的候选，不是每次生成必定穿戴的装备。空池、导入开关和导入记录仍控制是否追加。斯巴达装备来自注册表动态枚举，不能从这张固定表穷举所有安装组合。", "",
        "来源：`src/main/resources/defaults/equipment_integrations.json`。SHA-256：`" + hashlib.sha256(INTEGRATIONS.read_bytes()).hexdigest().upper() + "`。", "",
        "```json", json.dumps(integrations, ensure_ascii=False, indent=2), "```", ""])
    stale = []
    for path, content in pages.items():
        if args.check:
            if not path.exists() or path.read_text(encoding="utf-8") != content:
                stale.append(str(path))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
    if stale:
        raise SystemExit("Outdated Markdown: " + ", ".join(stale))
    print(f"{'Checked' if args.check else 'Wrote'} {len(pages)} Markdown appendices.")


if __name__ == "__main__":
    main()
