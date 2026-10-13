"""Export Markdown default-value appendices; never changes runtime configuration.

Uses the packaged JSON and its current TierConfigProjection storage layout.
This is a documentation exporter, not a replacement for the Java config loader.
Review this projection whenever TierConfigProjection changes. Handwritten chapters
and Java constants must be maintained separately.
"""
import argparse
import copy
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


GROUP_NAMES = {
    "vanilla_mode": "原版模式", "hostile_mob_targeting": "其他怪物选敌",
    "damage": "共享伤害规则", "equipment_integration": "装备自动联动", "armor_swap": "护甲自动换装", "combat_resupply": "战斗顺路补给",
    "spawning": "生成与解锁", "ai": "战斗与生存行为", "recruitment": "雇佣与名册",
    "better_combat": "Better Combat近战", "ammunition": "弹药与出生补给",
    "native_gameplay": "基础玩法与建筑", "tacz": "TaCZ枪械", "growth": "统一成长",
    "jump_attack": "追击跳砍", "first_aid": "First Aid医疗", "scguns": "焦燃枪械联动",
    "shield_pressure": "高压盾牌防御", "attributes": "基础属性", "movement": "移动速度",
    "damage_multipliers": "分类型伤害倍率", "weapon_spread_degrees": "武器散布",
    "combat": "基础攻击节奏", "food": "出生食物", "potions": "出生药水与战术道具",
    "growth_profile": "本阶成长曲线与装备品质", "equipment_loadout": "出生装备池",
}
GLOBAL_ORDER = ("vanilla_mode", "growth", "recruitment", "ammunition", "ai", "damage",
                "jump_attack", "shield_pressure", "spawning", "equipment_integration", "armor_swap", "combat_resupply",
                "tacz", "scguns", "first_aid", "better_combat", "hostile_mob_targeting", "native_gameplay")
TIER_ORDER = ("attributes", "damage_multipliers", "spawning", "ammunition", "food", "potions",
              "movement", "combat", "ai", "weapon_spread_degrees", "tacz", "scguns",
              "growth_profile", "equipment_loadout")


def render(name, data):
    title = "默认值-" + name
    output = [f"# {title}", "", "返回 [[配置索引]] · [[00-总览与导航]]", "",
              f"配置文件：`config/hostile_humans_unified/{name}.json`。以下为源码打包默认值；不是任何已运行实例的实测值。", "",
              "路径中的 `[0]` 表示列表第一项。权重是相对抽签份额；只有说明明确写概率时才按概率理解。20 tick=1秒，2点生命=1颗心。", "",
              "表格给出完整内置设置，机制、计算顺序和限制见 [[配置索引]] 中的对应主题。已有服务器的显式自定义值可以不同。", "",
              "## 分组导航", ""]
    order = GLOBAL_ORDER if name == "global" else TIER_ORDER
    groups = [group for group in order if group in data]
    groups += [group for group in data if not group.startswith("_") and group != "schema_version" and group not in groups]
    output += [" · ".join(f"[[#{GROUP_NAMES.get(group, group)}（{group}）|{GROUP_NAMES.get(group, group)}]]" for group in groups), ""]
    for group in groups:
        value = data[group]
        output += [f"## {GROUP_NAMES.get(group, group)}（{group}）", ""]
        if isinstance(value, dict) and value.get("_说明_中文"):
            output += [value["_说明_中文"], ""]
        output += ["| 字段路径 | 默认值 | 作用与单位 |", "|---|---|---|"]
        for path, val, help_text in rows(value, group, data.get("_说明_" + group + "_中文", "")):
            output.append(f"| `{path}` | `{cell(val)}` | {cell(help_text or '此项是列表/映射内的具体值，含义见所属分组及玩法章节。')} |")
        output.append("")
    return "\n".join(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--vault", type=Path, default=Path(r"E:\个人知识库\Hostile Humans Unified"))
    parser.add_argument("--check", action="store_true", help="Check generated Markdown without writing")
    args = parser.parse_args()
    root = json.loads(DEFAULTS.read_text(encoding="utf-8"))
    output = args.vault / "04-配置与数值" / "默认值附录"
    pages = {output / f"默认值-{name}.md": render(name, data)
             for name, data in project_defaults(root).items()}
    integrations = json.loads(INTEGRATIONS.read_text(encoding="utf-8"))
    integration_page = [
        "# 装备联动导入候选", "", "返回 [[配置索引]] · [[联动模组总表]]", "",
        "以下完整抄录当前装备联动导入表。它是安装相应模组后首次追加的候选，不是每次生成必定穿戴的装备。空池、导入开关和导入记录仍控制是否追加。斯巴达装备来自注册表动态枚举，不能从这张固定表穷举所有安装组合。", "",
        "套装权重是本阶有效组合中的相对份额；完整基础护甲值见 [[盔甲池与阶级护甲值]]。", ""]
    for provider, ranks in integrations.items():
        if provider.startswith("_"):
            continue
        integration_page += [f"## {provider}", "", "| 候选路径 | 默认内容 | 说明 |", "|---|---|---|"]
        for path, value, note in rows(ranks, provider):
            integration_page.append(f"| `{path}` | `{cell(value)}` | {cell(note)} |")
        integration_page.append("")
    pages[output / "装备联动导入候选.md"] = "\n".join(integration_page)
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
