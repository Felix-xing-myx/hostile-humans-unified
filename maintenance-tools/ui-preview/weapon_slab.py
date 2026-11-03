"""Replays the monitor weapon slab layout (MonitorGunHud) at 4x for review.

Not a screenshot: text metrics follow the vanilla font (CJK advance 9, ASCII 6, space 4) and the
numbers mirror the Java constants, so overlaps and clipped labels show up before a build.
"""
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(REPO, "docs/images/ui-preview")
S = 4
PW, PH = 128, 46
FONT = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc", 9 * S)

TEXT, MUTED, EDGE, BOTTOM, ACCENT, DANGER = 0xE6EBEF, 0x929FA9, 0x41525F, 0x090F13, 0xF2C66D, 0xE49B92

def adv(ch):
    if ch == " ":
        return 4
    return 9 if ord(ch) > 0x2E80 else 6

def width(text):
    return sum(adv(c) for c in text)

def clip_tail(text, limit):
    out = text
    while len(out) > 1 and width(out) > limit:
        out = out[1:]
    return out

def clip(text, limit):
    out, used = "", 0
    for c in text:
        a = adv(c)
        if used + a > limit:
            break
        out += c
        used += a
    return out

def rgba(c, a):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, int(a * 255))

def slab(d, ox, oy, name, amount, reserve, mode_label, capacity):
    x, y = ox, oy
    d.rectangle([x * S, y * S, (x + PW) * S, (y + PH) * S], fill=(42, 58, 68, 255))
    d.rectangle([x * S, y * S, (x + PW) * S, (y + PH) * S], fill=rgba(BOTTOM, .69))
    d.rectangle([x * S, y * S, (x + PW) * S, (y + PH) * S], outline=rgba(EDGE, .63))
    for i in range(6):
        x0 = (PW - 6) * i // 6
        d.rectangle([(x + 3 + x0) * S, y * S, (x + 3 + x0 + (PW - 6) // 6) * S, (y + 1) * S],
                    fill=rgba(ACCENT, 1 - i / 6))
    # gun art 54x28
    d.rectangle([(x + 2) * S, (y + 2) * S, (x + 56) * S, (y + 30) * S], outline=rgba(EDGE, .8))
    d.text(((x + 18) * S, (y + 10) * S), "枪械图", font=FONT, fill=rgba(MUTED, 1))
    # ammo, right aligned
    sc = min(2.0, 44 / max(1, width(amount)))
    amount_w = min(44, round(width(amount) * sc))
    big = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc", int(9 * S * sc))
    d.text(((x + PW - 4) * S - amount_w * S, (y + 2) * S), amount, font=big, fill=rgba(TEXT, 1))
    # reserve
    shown = clip_tail(reserve, PW - 8)
    d.text(((x + PW - 4) * S - width(shown) * S, (y + 21) * S), shown, font=FONT, fill=rgba(MUTED, 1))
    # separator
    d.rectangle([(x + 4) * S, (y + 31) * S, (x + PW - 4) * S, (y + 32) * S], fill=rgba(EDGE, 1))
    # bottom row: mode, name, capacity
    mode_w = 18
    d.rectangle([(x + 3) * S, (y + 33) * S, (x + 3 + mode_w) * S, (y + 43) * S], outline=rgba(EDGE, .9))
    d.text(((x + 4) * S, (y + 34) * S), mode_label, font=ImageFont.truetype(
        "/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc", int(9 * S * .55)), fill=rgba(TEXT, 1))
    cap_w = width(capacity) if capacity else 0
    if capacity:
        d.text(((x + PW - 4) * S - cap_w * S, (y + 33) * S), capacity, font=FONT, fill=rgba(MUTED, 1))
    name_x = x + 3 + mode_w + 4
    room = max(20, x + PW - 4 - cap_w - 3 - name_x)
    small = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc", int(9 * S * .85))
    d.text((name_x * S, (y + 34) * S), clip(name, int(room / .85)), font=small, fill=rgba(MUTED, 1))

img = Image.new("RGB", (300 * S, 196 * S), (15, 20, 23))
d = ImageDraw.Draw(img, "RGBA")
d.text((10 * S, 4 * S), "枪械板 128x46（按 Java 常数量出的实况）", font=ImageFont.truetype(
    "/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc", 8 * S), fill=rgba(MUTED, 1))
slab(d, 20, 20, "AK-47 突击步枪", "30", "弹匣 3", "AUTO", None)
slab(d, 20, 76, "M4 卡宾枪（长名字）", "100%", "弹匣 12", "BURST", None)
slab(d, 20, 132, "M249 班用机枪", "07", "弹匣 120", "AUTO", "/ 150")
img.save(os.path.join(OUT, "weapon-slab.png"))
print("ok")
