"""Read-only native call-site verification. Uses javap; never compiles or launches Minecraft."""
import argparse
import json
import hashlib
from pathlib import Path
import re
import subprocess


def inspect_native(jar, javap):
    def code(name):
        return subprocess.check_output([str(javap), '-p', '-c', '-classpath', str(jar), name], encoding='utf-8')
    def method(text, declaration):
        start = text.index(declaration)
        tail = text[start:]
        end = re.search(r'\n  (?:public|protected|private|static) ', tail[len(declaration):])
        return tail if end is None else tail[:len(declaration) + end.start()]
    selector = code('net.minecraft.world.entity.ai.goal.GoalSelector')
    wrapped = code('net.minecraft.world.entity.ai.goal.WrappedGoal')
    mob = code('net.minecraft.world.entity.Mob')
    assert 'private final java.util.Map<net.minecraft.world.entity.ai.goal.Goal$Flag, net.minecraft.world.entity.ai.goal.WrappedGoal> f_25344_;' in selector
    contracts = [
        ('selector_start', method(selector, 'public void m_25373_();'), 'WrappedGoal.m_8056_:()V'),
        ('selector_tick', method(selector, 'public void m_186081_(boolean);'), 'WrappedGoal.m_8037_:()V'),
        ('wrapped_start', method(wrapped, 'public void m_8056_();'), 'Goal.m_8056_:()V'),
        ('wrapped_tick', method(wrapped, 'public void m_8037_();'), 'Goal.m_8037_:()V'),
        ('wrapped_stop', method(wrapped, 'public void m_8041_();'), 'Goal.m_8041_:()V'),
    ]
    counts = {name: body.count(target) for name, body, target in contracts}
    assert all(count == 1 for count in counts.values()), counts
    start = contracts[0][1]
    assert start.index('java/util/Map.put:') < start.index('WrappedGoal.m_8056_:()V'), 'native control owner is installed before start'
    for name in ['wrapped_start', 'wrapped_stop']:
        _, body, target = next(entry for entry in contracts if entry[0] == name)
        assert body.index('putfield') < body.index(target), name + ' running state transition precedes delegate'
    for signature in ['public void m_21564_(float);', 'public void m_21570_(float);']:
        assert signature in mob, 'axis setter must remain public and non-final: ' + signature
    return {'jar': str(jar), 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'call_sites': counts, 'locked_flags_shadow': True, 'native_order': 'stop old / install native flag owner / start new',
            'axis_setters_public_nonfinal': True, 'java_compilation': False, 'mixin_runtime_application': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--javap', type=Path, required=True)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    result = inspect_native(args.jar.resolve(strict=True), args.javap.resolve(strict=True))
    payload = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output: args.output.write_text(payload, encoding='utf-8')
    print(payload)

if __name__ == '__main__': main()
