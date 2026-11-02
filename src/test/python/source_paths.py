"""Test-only Java source lookup by unique class name; resource paths retain normal pathlib behavior.

Modules own their files; wiring tests inspect those same files without depending on a historical flat package.
Structural tests use pathlib directly and validate exact ownership against docs/module-map.json.
"""
from pathlib import Path as NativePath
from functools import lru_cache
ROOT=NativePath(__file__).resolve().parents[3]/"src/main/java/dev/felix/hostilehumans"
@lru_cache(maxsize=1)
def index():
    result={}
    for p in ROOT.rglob("*.java"):
        if p.name in result:raise AssertionError("Ambiguous Java source: "+p.name)
        result[p.name]=p
    return result
class SourcePath(type(NativePath())):
    def __truediv__(self, other):
        result=super().__truediv__(other)
        if result.suffix==".java" and not result.is_file() and result.is_relative_to(ROOT):
            target=index().get(result.name)
            if target is not None:return SourcePath(target)
        return result
    def glob(self, pattern, **kwargs):
        if pattern=="*.java" and self==ROOT:return super().rglob(pattern, **kwargs)
        return super().glob(pattern, **kwargs)
