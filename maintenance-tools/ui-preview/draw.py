"""Static re-implementation of UiDraw/PanelScreen chrome, for layout discussion only."""
from PIL import Image, ImageDraw, ImageFont
import math, os

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(REPO, "src/main/resources/assets/hostile_humans_unified")
OUT = os.path.join(REPO, "docs/images/ui-preview")  # referenced by docs/UI_FRAME.md
S = 2  # pixel scale

def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    ar, ag, ab = (a >> 16) & 255, (a >> 8) & 255, a & 255
    br, bg, bb = (b >> 16) & 255, (b >> 8) & 255, b & 255
    return (round(ar + (br - ar) * t) << 16) | (round(ag + (bg - ag) * t) << 8) | round(ab + (bb - ab) * t)

def rgba(c, a):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, max(0, min(255, round(a * 255))))

class Canvas:
    def __init__(self, w, h, bg=(15, 20, 23)):
        self.img = Image.new("RGB", (w, h), bg)
        self.d = ImageDraw.Draw(self.img, "RGBA")
    def rect(self, x, y, w, h, color, alpha=1.0):
        if w <= 0 or h <= 0: return
        self.d.rectangle([x, y, x + w - 1, y + h - 1], fill=rgba(color, alpha))
    def grad_v(self, x, y, w, h, c1, c2):
        for i in range(h):
            self.rect(x, y + i, w, 1, mix(c1, c2, i / max(1, h - 1)))
    def outline(self, x, y, w, h, color, alpha=1.0):
        self.rect(x, y, w, 1, color, alpha); self.rect(x, y + h - 1, w, 1, color, alpha)
        self.rect(x, y, 1, h, color, alpha); self.rect(x + w - 1, y, 1, h, color, alpha)

BASE_HEAD, BASE_TOP, BASE_MID, BASE_BOTTOM = 0x1A2733, 0x15212A, 0x0E171E, 0x090F13
INSET, SURFACE, EDGE, TEXT, MUTED, DISABLED = 0x0B141A, 0x1D252B, 0x41525F, 0xE6EBEF, 0x929FA9, 0x5F707C
ACCENT = 0xF2C66D

def frame(c, x, y, w, h):
    # soft shadow
    for i in range(5, 0, -1):
        a = (10 + (6 - i) * 9) / 255
        ox, oy = x - 3 - i, y - 3 - i + i // 2
        ow, oh = w + 6 + 2 * i, h + 6 + 2 * i
        c.rect(ox, oy, ow, 1, 0, a); c.rect(ox, oy + oh - 1, ow, 1, 0, a)
        c.rect(ox, oy, 1, oh, 0, a); c.rect(ox + ow - 1, oy, 1, oh, 0, a)
    # 3px ring + 1px outline
    c.rect(x - 3, y - 3, w + 6, 3, EDGE, .42); c.rect(x - 3, y + h, w + 6, 3, EDGE, .42)
    c.rect(x - 3, y, 3, h, EDGE, .42); c.rect(x + w, y, 3, h, EDGE, .42)
    c.outline(x - 1, y - 1, w + 2, h + 2, 0x05080B)
    head = mix(BASE_HEAD, ACCENT, .09)
    a = round(h * .18); b = round(h * .58); cut = 3
    c.rect(x + 2, y, w - 2, 1, head)
    c.rect(x + 1, y + 1, w - 1, 1, mix(head, BASE_TOP, .5))
    c.rect(x, y + 2, w, 1, BASE_TOP)
    c.grad_v(x, y + cut, w, a - cut, head, BASE_TOP)
    c.grad_v(x, y + a, w, b - a, BASE_TOP, BASE_MID)
    c.grad_v(x, y + b, w, h - cut - b, BASE_MID, BASE_BOTTOM)
    c.rect(x, y + h - 3, w - 1, 1, BASE_BOTTOM); c.rect(x, y + h - 2, w - 1, 1, BASE_BOTTOM)
    c.rect(x, y + h - 1, w - 2, 1, BASE_BOTTOM)
    # spine
    top, mid, low = mix(head, ACCENT, .8), mix(BASE_MID, ACCENT, .8), mix(BASE_BOTTOM, ACCENT, .8)
    c.grad_v(x, y + 2, 3, a - 2, top, mid)
    c.grad_v(x, y + a, 3, b - a, mid, mid)
    c.grad_v(x, y + b, 3, h - 1 - b, mid, low)
    c.rect(x + 2, y, 1, 2, top); c.rect(x + 1, y + 1, 2, 1, top)
    c.rect(x + 3, y + 2, 1, h - 3, mix(BASE_BOTTOM, ACCENT, .35))
    c.rect(x + 3, y + 1, w - 4, 1, 0xFFFFFF, .16)
    c.rect(x + 2, y + h - 2, w - 4, 1, 0, .45)
    grain(c, x, y, w, h)

GRAIN = Image.open(os.path.join(ASSETS, "textures/gui/panel/surface.png")).convert("RGBA")
def grain(c, x, y, w, h, ox=None, oy=None):
    ox = x if ox is None else ox
    oy = y if oy is None else oy
    tile = GRAIN
    px, py = (x - ox) % 64, (y - oy) % 64
    for ty in range(-py, h, 64):
        for tx in range(-px, w, 64):
            sx, sy = max(0, -tx), max(0, -ty)
            tw = min(64 - sx, w - max(0, tx)); th = min(64 - sy, h - max(0, ty))
            if tw <= 0 or th <= 0: continue
            region = tile.crop((sx, sy, sx + tw, sy + th)).copy()
            region.putalpha(region.getchannel("A").point(lambda v: int(v * .30)))
            c.img.paste(region, (x + max(0, tx) + sx, y + max(0, ty) + sy), region)

FONT = ImageFont.truetype("/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc", 9 * S)

def draw(c, w=600, h=480, band=True, faction="灰鸦", rows=4):
    x, y = 30, 30
    frame(c, x * S, y * S, w * S, h * S)
    # header wash
    head_w = w - 2
    fade = max(1, head_w * 78 // 100)
    steps = min(26, max(1, fade // 4))
    for i in range(steps):
        x0, x1 = fade * i // steps, fade * (i + 1) // steps
        pos = (i + 1) / steps
        a = .22 + (.06 - .22) * (pos / .54) if pos < .54 else .06 * (1 - (pos - .54) / .46)
        c.rect((x + 1 + x0) * S, y * S, (x1 - x0) * S, 28 * S, ACCENT, a)
    c.d.text(((x + 12) * S, (y + 10) * S), "雇佣士兵名单", font=FONT, fill=rgba(TEXT, 1))
    c.d.text(((x + w - 12 - 32) * S, (y + 10) * S), "1 / 1", font=FONT, fill=rgba(MUTED, 1))
    # band
    by = (y + 32) * S
    c.rect((x + 12) * S, by, (w - 24) * S, 24 * S, mix(BASE_TOP, 0xFFFFFF, .035))
    for i, label in enumerate(["全部", "0级", "1级", "2级", "3级", "小队管理"]):
        bw = 44 if i < 5 else 96
        bx = (x + 18 + sum([48, 48, 48, 48, 48][:i])) * S
        sel = i == 0
        c.rect(bx, by + 2 * S, bw * S, 20 * S, mix(INSET, ACCENT, .55) if sel else mix(SURFACE, 0xFFFFFF, .12))
        c.rect(bx + S, by + 3 * S, (bw - 2) * S, 18 * S, mix(INSET, ACCENT, .14) if sel else SURFACE)
        if sel: c.rect(bx, by + 2 * S, 2 * S, 20 * S, ACCENT)
        c.d.text((bx + 7 * S, (by + 2 * S) + 6 * S), label, font=FONT, fill=rgba(ACCENT if sel else TEXT, 1))
    # rule
    c.rect((x + 12) * S, (y + 58) * S, (w - 24) * S, S, 0x070D12)
    c.rect((x + 12) * S, (y + 59) * S, (w - 24) * S, S, 0xFFFFFF, .045)
    # shelf
    bx_, by_ = (x + 12) * S, (y + 60) * S
    bw_, bh_ = (w - 26) * S, (h - 60 - 40) * S
    c.rect(bx_, by_, bw_, bh_, INSET)
    c.rect(bx_, by_, bw_, S, 0, .55)
    c.outline(bx_, by_, bw_, bh_, 0, .22)
    # divider
    c.rect(bx_ + 280 * S, by_, S, bh_, 0, .55)
    # rows
    for i in range(rows):
        ry = by_ + (6 + i * 32) * S
        c.rect(bx_ + 8 * S, ry, 264 * S, 32 * S, INSET)
        c.rect(bx_ + 8 * S, ry + 31 * S, 264 * S, S, 0xFFFFFF, .04)
        c.rect(bx_ + 18 * S, ry + 9 * S, 4 * S, 4 * S, 0x8FC7A6)
        c.d.text((bx_ + 26 * S, ry + 5 * S), ["沃罗宁", "米哈伊尔", "卡佳", "阿列克谢"][i], font=FONT, fill=rgba(TEXT, 1))
        # health: 5 cells of 8 ticks, proportional
        cur, mx = [(42, 52), (15, 48), (60, 60), (33, 40)][i]
        cells = min(8, max(1, math.ceil(mx / 25)))
        per, tick, inner, gap = 8, 1, 1, 3
        cellw = tick * per + inner * (per - 1)
        natural = cells * cellw + (cells - 1) * gap
        target = 112
        hx = bx_ + (8 + 8 + 68 + 6) * S
        filled = cur / mx * cells * per
        col = 0x7FC08C if cur / mx > .5 else 0xDBB25F if cur / mx >= .2 else 0xDC7F6F
        for t in range(cells * per):
            start = t // per * (cellw + gap) + (t % per) * (tick + inner)
            l = hx + round(start * target / natural) * S
            wpx = max(1, round((start + tick) * target / natural) - round(start * target / natural))
            c.rect(l, ry + 6 * S, max(1, wpx * S), 8 * S, 0x21303A)
            f = max(0, min(1, filled - t))
            if f > 0: c.rect(l, ry + 6 * S, max(1, round(wpx * f * S)), 8 * S, col)
        num = f"{cur}/{mx}"
        c.d.text((bx_ + (264 - 6 - 36) * S, ry + 6 * S), num, font=FONT, fill=rgba(MUTED, 1))
        c.d.text((bx_ + 18 * S, ry + 18 * S), "三阶 · [0] · 已加载", font=FONT, fill=rgba(DISABLED, 1))
    c.d.text((bx_ + 300 * S, by_ + 10 * S), "选择左侧士兵查看详情；Shift 点击可多选", font=FONT, fill=rgba(MUTED, 1))
    # fade + action bar
    fy = by_ + bh_ - 20 * S
    for i in range(20 * S):
        c.rect(bx_, fy + i, bw_, 1, INSET, .85 * (i + 1) / (20 * S))
    ax, ay = (x + 12) * S, (h - 30) * S
    for i in range(14 * S):
        c.rect(ax, ay - 14 * S + i, (w - 26) * S, 1, 0, .34 * (i + 1) / (14 * S))
    c.rect(ax, ay, (w - 26) * S, 20 * S, mix(BASE_BOTTOM, 0, .45), .86)
    c.rect(ax, ay, (w - 26) * S, S, 0xFFFFFF, .05)
    grain(c, ax, ay - 14 * S, (w - 26) * S, 34 * S, x * S, y * S)
    c.d.text((ax + 7 * S, ay + 6 * S), "小队管理", font=FONT, fill=rgba(TEXT, 1))
    for i, (label, danger) in enumerate([("刷新", False), ("全部召回", True)]):
        bw2 = (76 if not danger else 88)
        bx2 = ax + (w - 26 - (76 + 88 + 6)) * S + (0 if i == 0 else (76 + 6) * S)
        c.rect(bx2, ay, bw2 * S, 20 * S, mix(INSET, 0xE49B92, .42) if danger else mix(SURFACE, 0xFFFFFF, .12))
        c.rect(bx2 + S, ay + S, (bw2 - 2) * S, 18 * S, mix(INSET, 0xE49B92, .10) if danger else SURFACE)
        c.d.text((bx2 + 7 * S, ay + 6 * S), label, font=FONT, fill=rgba(0xE49B92 if danger else TEXT, 1))
    return c

W, H = (600 + 90) * S, (480 + 70) * S
c = Canvas(W, H)
draw(c, 600, 480)
c.img.save(os.path.join(OUT, "roster-chrome.png"))
print("ok", c.img.size)
