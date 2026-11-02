"""Refresh module ownership and test lookup indexes from the actual Java tree (no compilation)."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "src/main/java"
MANIFEST = ROOT / "docs/module-map.json"

def main():
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    namespace = data["namespace"]
    modules = data["modules"]
    for spec in modules.values():
        spec["classes"] = []
    index = {}
    for file in sorted(BASE.rglob("*.java")):
        package = re.search(r"(?m)^package ([\w.]+);", file.read_text(encoding="utf-8"))[1]
        if not package.startswith(namespace + "."):
            raise SystemExit("Outside unified namespace: " + str(file))
        module = package[len(namespace) + 1:]
        if module not in modules:
            raise SystemExit("Register module purpose in docs/module-map.json first: " + module)
        if file.stem in index:
            raise SystemExit("Duplicate class name: " + file.stem)
        modules[module]["classes"].append(file.stem)
        index[file.stem] = {"class": package + "." + file.stem,
                            "path": file.relative_to(ROOT).as_posix()}
    data["modules"] = {key: modules[key] for key in sorted(modules)}
    for spec in modules.values():
        spec["classes"].sort()
    for file, value in ((MANIFEST, data), (ROOT / "src/test/resources/hhu-module-sources.json", dict(sorted(index.items())))):
        file.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    guide = ROOT / "docs/SOURCE_MODULES.md"
    text = guide.read_text(encoding="utf-8")
    table = "| 目录（统一包根目录下） | 职责 | 生产类数量 |\n|---|---|---:|\n"
    classes = ""
    for module, spec in data["modules"].items():
        table += f"| `{module.replace('.', '/')}` | {spec['purpose']} | {len(spec['classes'])} |\n"
        classes += "### " + module + "\n\n" + "、".join("`" + name + "`" for name in spec["classes"]) + "\n\n"
    text = re.sub(r"(?s)(## 模块归属\n\n).*?(?=## 修改时的联动检查)", lambda m: m[1] + table + "\n", text)
    text = re.sub(r"(?s)(## 文件清单\n\n).*", lambda m: m[1] + classes, text)
    guide.write_text(text, encoding="utf-8")
    print(f"Indexed {len(index)} production classes in {len(modules)} functional packages.")

if __name__ == "__main__":
    main()
