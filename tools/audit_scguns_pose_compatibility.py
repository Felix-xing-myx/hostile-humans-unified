"""Read-only native machine-gun pose extension API audit; no compilation or game startup."""
import argparse
import json
import re
import subprocess
from pathlib import Path


def audit(jar, javap):
    contracts = {
        "common.GripType": ["MINI_GUN", "MINI_GUN_2", "MINI_GUN_3", "MINI_GUN_4", "MINI_GUN_5"],
        "client.render.pose.AimPose": ["getIdle()"],
        "client.render.pose.AimPose$Instance": ["getLeftArm()", "getRightArm()", "getRenderYawOffset()", "getItemTranslate()", "getItemRotation()"],
        "client.render.pose.LimbPose": ["getRotationAngleX()", "getRotationAngleY()", "getRotationAngleZ()", "getRotationPointX()", "getRotationPointY()", "getRotationPointZ()"],
    }
    poses = ["MiniGunPose", "MiniGun2Pose", "MiniGun3Pose", "MiniGun4Pose", "MiniGun5Pose"]
    for pose in poses:
        contracts["client.render.pose." + pose] = [pose + "()"]
    for name, members in contracts.items():
        result = subprocess.run([str(javap), "-p", "-classpath", str(jar), "top.ribs.scguns." + name],
                                capture_output=True, text=True, check=True).stdout
        for member in members:
            assert any(line.lstrip().startswith("public ") and
                       (member in line if "(" in member else re.search(r"\b" + re.escape(member) + r";", line))
                       for line in result.splitlines()), (name, member)
        if name.rsplit(".", 1)[-1] in poses:
            assert "public final class" not in result, "Native pose no longer extensible: " + name
            for factory in ("getUpPose()", "getForwardPose()", "getDownPose()"):
                assert any(factory in line and line.lstrip().startswith("protected ") for line in result.splitlines()), (name, factory)
    return {"jar": jar.name, "pose_variants": len(poses), "checked_classes": len(contracts), "runtime_tested": False}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path)
    parser.add_argument("--javap", type=Path, default=Path("D:/Java/17/bin/javap.exe"))
    args = parser.parse_args()
    print(json.dumps(audit(args.jar, args.javap), indent=2))
