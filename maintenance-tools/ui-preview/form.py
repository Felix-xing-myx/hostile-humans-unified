import os
HERE = os.path.dirname(os.path.abspath(__file__))
exec(open(os.path.join(HERE, 'draw.py'), encoding='utf-8').read().split('W, H =')[0])
font = FONT

def control(c, x, y, w, h, face, edge, text, label, ratio_ok=True):
    c.rect(x, y, w, h, edge)
    c.rect(x + S, y + S, w - 2 * S, h - 2 * S, face)
    c.d.text((x + 7 * S, y + 6 * S), label, font=FONT, fill=rgba(text, 1))

def form(c, ox, oy, pw=600, ph=480):
    frame(c, ox, oy, pw, ph)
    bw = pw - 2
    fade = max(1, bw * 78 // 100); steps = min(26, max(1, fade // 4))
    for i in range(steps):
        x0, x1 = fade * i // steps, fade * (i + 1) // steps
        pos = (i + 1) / steps
        a = .22 + (.06 - .22) * (pos / .54) if pos < .54 else .06 * (1 - (pos - .54) / .46)
        c.rect((ox + 1 + x0) * S, oy * S, (x1 - x0) * S, 28 * S, ACCENT, a)
    c.d.text(((ox + 12) * S, (oy + 10) * S), "据点信息", font=FONT, fill=rgba(TEXT, 1))
    # band with 5 tabs
    by = (oy + 32) * S
    c.rect((ox + 12) * S, by, (pw - 24) * S, 24 * S, mix(BASE_TOP, 0xFFFFFF, .035))
    labels = ["模板", "区域", "设施", "转移", "管理"]
    bx = ox + 18
    for i, label in enumerate(labels):
        w = font.getlength(label) // S + 14
        c.rect(bx * S, by + 2 * S, w * S, 20 * S, mix(INSET, ACCENT, .55) if i == 0 else mix(SURFACE, 0xFFFFFF, .12))
        c.rect((bx + 1) * S, by + 3 * S, (w - 2) * S, 18 * S, mix(INSET, ACCENT, .14) if i == 0 else SURFACE)
        c.d.text((bx * S + 7 * S, by + 2 * S + 6 * S), label, font=FONT, fill=rgba(ACCENT if i == 0 else TEXT, 1))
        bx += w + 4
    c.rect((ox + 12) * S, (oy + 58) * S, (pw - 24) * S, S, 0x070D12)
    c.rect((ox + 12) * S, (oy + 59) * S, (pw - 24) * S, S, 0xFFFFFF, .045)
    bx0, by0 = (ox + 12) * S, (oy + 60) * S
    c.rect(bx0, by0, (pw - 26) * S, (ph - 100) * S, INSET)
    # field: caption + 20px edit box
    def field(x, y, label, value, valid=True):
        c.d.text((x * S, y * S), label, font=FONT, fill=rgba(MUTED, 1))
        ey = y + 10
        c.rect(x * S, ey * S, 180 * S, 20 * S, mix(SURFACE, 0xFFFFFF, .12))
        c.rect((x + 1) * S, (ey + 1) * S, 178 * S, 18 * S, SURFACE)
        # inner 8px editor with vanilla scissor clip at +8
        c.d.text(((x + 6) * S, (ey + 4) * S), value, font=FONT, fill=rgba(TEXT if valid else 0xE49B92, 1))
        c.rect((x + 6) * S, (ey + 4 + 12) * S, 168 * S, S, 0, .5)  # editor box bottom edge
    field(ox + 20, oy + 70, "阵营资源 ID", "hostile_humans_unified:crimson")
    # preset chips under the field
    cy = oy + 103
    cx = ox + 20
    for i, label in enumerate(["赤獠", "靛磐", "苍隼", "灰鸦"]):
        w = font.getlength(label) // S + 10
        sel = i == 0
        c.rect(cx * S, cy * S, w * S, 16 * S, mix(INSET, ACCENT, .55) if sel else mix(SURFACE, 0xFFFFFF, .12))
        c.rect((cx + 1) * S, (cy + 1) * S, (w - 2) * S, 14 * S, mix(INSET, ACCENT, .14) if sel else SURFACE)
        if sel: c.rect(cx * S, cy * S, 2 * S, 16 * S, ACCENT)
        c.d.text((cx * S + 5 * S, cy * S + 4 * S), label, font=FONT, fill=rgba(ACCENT if sel else TEXT, 1))
        cx += w + 4
    field(ox + 20, oy + 130, "模板 ID", "fortress_core")
    # option rows: active vs disabled
    y = oy + 168
    c.d.text(((ox + 20) * S, y * S), "模块类型（禁用态，服务端未授权编辑）", font=FONT, fill=rgba(MUTED, 1))
    y += 12
    for i, label in enumerate(["保护根模块", "附件模块", "普通模块"]):
        w = 120
        x = ox + 20 + i * (w + 4)
        control(c, x * S, y * S, w * S, 20 * S, mix(INSET, 0xFFFFFF, .05), mix(INSET, 0xFFFFFF, .10), mix(DISABLED, MUTED, .55), label)
    y += 28
    c.d.text(((ox + 20) * S, y * S), "模块类型（可编辑）", font=FONT, fill=rgba(MUTED, 1))
    y += 12
    for i, label in enumerate(["保护根模块", "附件模块", "普通模块"]):
        w = 120
        x = ox + 20 + i * (w + 4)
        sel = i == 0
        control(c, x * S, y * S, w * S, 20 * S,
                mix(INSET, ACCENT, .14) if sel else SURFACE,
                mix(INSET, ACCENT, .55) if sel else mix(SURFACE, 0xFFFFFF, .12),
                ACCENT if sel else TEXT, label)

c = Canvas((600 + 80) * S, (480 + 60) * S)
form(c, 30, 30)
c.img.save(os.path.join(OUT, "settlement-form.png"))
print("ok")
