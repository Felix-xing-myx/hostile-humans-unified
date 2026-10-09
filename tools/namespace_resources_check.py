"""Consolidate/audit resource namespaces without compiling or launching Minecraft.

The NBT walker copies all non-string payload bytes verbatim (including NaNs,
array contents and modified UTF-8). Only tag names and TAG_String payloads are
rewritten, with their unsigned-short byte lengths recalculated. Gzip framing is
regenerated only when its decompressed NBT actually changes.
"""
import argparse
from collections import Counter, defaultdict
import gzip
import json
from pathlib import Path
import re
import struct
import subprocess


ROOT = Path(__file__).resolve().parents[1]
PREFIXES = tuple(f"src/main/resources/{name}/" for name in ("assets", "data", "datapacks"))
OLD = re.compile(rb"hostile_humans(?!_unified)|humangunner")
NEW = b"hostile_humans_unified"


def rename(value):
    return OLD.sub(NEW, value)


def target_path(path):
    return rename(path.encode()).decode()


class NBT:
    def __init__(self, raw, rewrite):
        self.raw, self.offset, self.rewrite = raw, 0, rewrite
        self.stats = Counter()
        self.changed_strings = Counter()

    def take(self, count):
        if count < 0 or self.offset + count > len(self.raw):
            raise ValueError(f"NBT payload outside file at {self.offset}, size {count}")
        data = self.raw[self.offset:self.offset + count]
        self.offset += count
        return data

    def string(self, kind):
        value = self.take(struct.unpack(">H", self.take(2))[0])
        converted = rename(value) if self.rewrite else value
        self.stats[kind] += 1
        if converted != value:
            self.stats[f"changed_{kind}"] += 1
            self.changed_strings[value] += 1
        return struct.pack(">H", len(converted)) + converted

    def payload(self, tag):
        self.stats[f"tag_{tag}"] += 1
        if tag in (1, 2, 3, 4, 5, 6):
            return self.take({1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}[tag])
        if tag in (7, 11, 12):
            length = self.take(4)
            count = struct.unpack(">i", length)[0]
            if count < 0:
                raise ValueError("negative NBT array length")
            return length + self.take(count * {7: 1, 11: 4, 12: 8}[tag])
        if tag == 8:
            return self.string("values")
        if tag == 9:
            header = self.take(5)
            element, count = struct.unpack(">Bi", header)
            if count < 0 or (count and element == 0):
                raise ValueError("invalid NBT list length/type")
            return header + b"".join(self.payload(element) for _ in range(count))
        if tag == 10:
            parts, names = [], set()
            while True:
                element = self.take(1)
                parts.append(element)
                if element == b"\0":
                    return b"".join(parts)
                name = self.string("keys")
                if name in names:
                    raise ValueError(f"duplicate NBT compound key: {name!r}")
                names.add(name)
                parts.extend((name, self.payload(element[0])))
        raise ValueError(f"unknown NBT tag type {tag}")

    def run(self):
        tag = self.take(1)
        if tag == b"\0":
            raise ValueError("empty NBT root")
        result = tag + self.string("keys") + self.payload(tag[0])
        if self.offset != len(self.raw):
            raise ValueError("trailing NBT payload")
        return result


def nbt_bytes(data):
    return gzip.decompress(data) if data.startswith(b"\x1f\x8b") else data


def transform(path, data, stats):
    if path.endswith(".nbt"):
        raw = nbt_bytes(data)
        identity = NBT(raw, False)
        assert identity.run() == raw, f"NBT lossless round trip: {path}"
        converter = NBT(raw, True)
        converted = converter.run()
        stats["nbt_files"] += 1
        stats.update(converter.stats)
        if converted != raw:
            stats["changed_nbt_files"] += 1
            return gzip.compress(converted, mtime=0) if data.startswith(b"\x1f\x8b") else converted
        return data
    if path.endswith((".json", ".mcmeta", ".md")):
        return rename(data)
    assert not OLD.search(data), f"unhandled old namespace in binary resource: {path}"
    return data


def json_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def load_json(data):
    return json.loads(data, object_pairs_hook=json_object)


def canonical(path, data):
    # Git's Windows checkout can materialize CRLF for text resources.
    return data.replace(b"\r\n", b"\n") if path.endswith((".json", ".mcmeta", ".md")) else data


def baseline_files(ref):
    names = subprocess.check_output(["git", "ls-tree", "-r", "--name-only", ref], cwd=ROOT).decode().splitlines()
    names = [name for name in names if name.startswith(PREFIXES)]
    batch = subprocess.check_output(["git", "cat-file", "--batch"], cwd=ROOT,
                                    input="".join(f"{ref}:{name}\n" for name in names).encode())
    result, offset = {}, 0
    for name in names:
        end = batch.index(b"\n", offset)
        _, kind, size = batch[offset:end].split()
        assert kind == b"blob", f"expected resource blob: {name}"
        offset = end + 1
        count = int(size)
        result[name] = canonical(name, batch[offset:offset + count])
        assert batch[offset + count:offset + count + 1] == b"\n"
        offset += count + 1
    assert offset == len(batch)
    return result


def current_files():
    return {p.relative_to(ROOT).as_posix(): canonical(p.as_posix(), p.read_bytes())
            for prefix in PREFIXES for p in (ROOT / prefix).rglob("*") if p.is_file()}


def plan(source):
    groups = defaultdict(list)
    stats = Counter(source_files=len(source))
    for path, data in sorted(source.items()):
        converted = transform(path, data, stats)
        stats["moved_files"] += target_path(path) != path
        stats["changed_payload_files"] += converted != data
        groups[target_path(path)].append((path, converted))
    output = {}
    for path, entries in groups.items():
        if len(entries) == 1:
            output[path] = entries[0][1]
            continue
        # The only permitted path collision is merging the two language maps.
        assert "/lang/" in path and path.endswith(".json"), f"resource collision: {path}"
        merged = {}
        for original, data in entries:
            for key, value in load_json(data).items():
                assert key not in merged, f"language key collision: {original}: {key}"
                merged[key] = value
        output[path] = (json.dumps(merged, ensure_ascii=False, indent=2) + "\n").encode()
        stats["merged_language_files"] += 1
        stats["merged_language_keys"] += len(merged)
    stats["output_files"] = len(output)
    return output, stats


def validate(actual, expected):
    assert actual.keys() == expected.keys(), f"resource paths differ: {actual.keys() ^ expected.keys()}"
    stats = Counter()
    for path, data in actual.items():
        assert target_path(path) == path, f"old resource path: {path}"
        if path.endswith(".nbt"):
            # Compare the entire decompressed stream against the baseline plan;
            # this covers numeric payloads, arrays and external names as well.
            raw = nbt_bytes(data)
            assert raw == nbt_bytes(expected[path]), f"NBT preservation failure: {path}"
            assert NBT(raw, False).run() == raw
            assert not OLD.search(raw), f"old NBT namespace: {path}"
            stats["verified_nbt_files"] += 1
        else:
            assert data == expected[path], f"resource preservation failure: {path}"
            assert not OLD.search(data), f"old resource reference: {path}"
        if path.endswith((".json", ".mcmeta")):
            obj = load_json(data)
            stats["verified_json_files"] += 1
            if "/lang/" in path:
                stats["verified_translation_keys"] += len(obj)
        if path.endswith(".png"):
            stats["verified_unchanged_png_files"] += 1
    return stats


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", default="76bf1840738b969ff8b201b3df163fffa3efc2dc")
    parser.add_argument("--apply", action="store_true", help="rewrite resources after collision preflight")
    args = parser.parse_args()
    source = baseline_files(args.baseline)
    expected, stats = plan(source)
    actual = current_files()
    if args.apply:
        assert actual == source, "--apply requires resources identical to the baseline"
        # Preflight parses every NBT/JSON and detects every collision before writes.
        validate(expected, expected)
        for name, data in expected.items():
            destination = ROOT / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(data)
        for name in source.keys() - expected.keys():
            (ROOT / name).unlink()
        for prefix in PREFIXES:
            for directory in sorted((ROOT / prefix).rglob("*"), key=lambda p: len(p.parts), reverse=True):
                if directory.is_dir() and not any(directory.iterdir()):
                    directory.rmdir()
        actual = current_files()
    stats.update(validate(actual, expected))
    print(json.dumps(dict(sorted(stats.items())), indent=2))


if __name__ == "__main__":
    main()
