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
PW, PH = 128, 52
FONT = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", 9 * S)

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

def fit_ellipsis(text, limit):
    if width(text) <= limit:
        return text
    ell = 9
    cut, used = "", 0
    for c in text:
        a = adv(c)
        if used + a > limit - ell:
            break
        cut += c
        used += a
    return cut + "…" if limit > ell else clip(text, limit)

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
    # gun art 54x30
    d.rectangle([(x + 2) * S, (y + 4) * S, (x + 56) * S, (y + 34) * S], outline=rgba(EDGE, .8))
    d.text(((x + 16) * S, (y + 13) * S), "枪械图", font=FONT, fill=rgba(MUTED, 1))
    # column: ammo + capacity, then reserve
    col = x + 58
    col_right = x + PW - 4
    sc = min(2.0, max(24, col_right - col - (width(capacity) + 3 if capacity else 0)) / max(1, width(amount)))
    amount_w = round(width(amount) * sc)
    big = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", int(9 * S * sc))
    d.text((col * S, (y + 4) * S), amount, font=big, fill=rgba(TEXT, 1))
    if capacity:
        d.text(((col + amount_w + 3) * S, (y + 13) * S), capacity, font=FONT, fill=rgba(MUTED, 1))
    small_res = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", int(9 * S * .85))
    shown_reserve = clip_tail(reserve, int((col_right - col) / .85))
    d.text(((col_right) * S - int(width(shown_reserve) * .85) * S, (y + 24) * S), shown_reserve, font=small_res, fill=rgba(MUTED, 1))
    # hairline
    d.rectangle([(x + 4) * S, (y + 36) * S, (x + PW - 4) * S, (y + 37) * S], fill=rgba(EDGE, 1))
    # caption row: native mode art (18x12 box here), then the name
    # Fire mode is a localised word in a fixed 36px slot; the name column never moves.
    MODE_SLOT = 36
    mode_words = {"AUTO": "全自动", "SEMI": "单点", "BURST": "连发", "RELOAD": "装填中"}
    mode_text = mode_words.get(mode_label.upper(), mode_label)
    d.text(((x + 3) * S, (y + 39) * S), clip(mode_text, MODE_SLOT), font=FONT, fill=rgba(MUTED, 1))
    name_x = x + 3 + MODE_SLOT + 4
    room = max(20, col_right - name_x)
    small = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", int(9 * S * .85))
    d.text((name_x * S, (y + 39) * S), fit_ellipsis(name, int(room / .85)), font=small, fill=rgba(MUTED, 1))

img = Image.new("RGB", (330 * S, 262 * S), (15, 20, 23))
d = ImageDraw.Draw(img, "RGBA")
d.text((10 * S, 4 * S), "枪械板 128x52 · 第三版：弹匣已移入上带（按 Java 常数量出的实况）", font=ImageFont.truetype(
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", 8 * S), fill=rgba(MUTED, 1))
slab(d, 20, 18, "AK-47 突击步枪", "30", "弹匣 3", "AUTO", "/ 30")
ann = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", 8 * S)
note = lambda ty, text: d.text((158 * S, ty * S), text, font=ann, fill=rgba(ACCENT, 1))
note(26, "上带：枪图 54x30 ｜ 18 / 30 ｜ 弹匣(右下)")
note(52, "灰线 y36")
note(62, "下带：射击模式文字（固定列）+ 名称")
slab(d, 20, 78, "M4 卡宾枪（长名字）", "100%", "弹匣 12", "BURST", "")
slab(d, 20, 138, "M249 班用机枪", "07", "弹匣 120", "SEMI", "/ 150")
slab(d, 20, 198, "M4A1 卡宾枪（消音器改装型）", "30", "弹匣 3", "AUTO", "/ 30")
img.save(os.path.join(OUT, "weapon-slab-128x52.png"))
print("ok")
