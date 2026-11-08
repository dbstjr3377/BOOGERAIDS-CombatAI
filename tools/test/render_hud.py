import json, math, sys
MODE = sys.argv[1] if len(sys.argv) > 1 else "stage"
from PIL import Image, ImageDraw, ImageFont
W, H = 1600, 900
PX = 0.025
RP = "/home/claude/proj/resourcepack/assets/boogeraids/"
els = json.load(open("/tmp/test/hud.json" if MODE == "stage" else "/tmp/test/hud_boss.json"))
uifont = json.load(open(RP + "font/ui.json"))["providers"]
glyphs = {p["chars"][0]: p for p in uifont}
TTF = {"boogeraids:lab": (RP + "font/sharetechmono.ttf", 9.0), "boogeraids:digital": (RP + "font/dseg7.ttf", 12.0)}
CJK = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
SYM = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

# 배경: 시뮬레이션용 연구소 톤 그라데이션 + 가짜 플랫폼 실루엣
img = Image.new("RGBA", (W, H))
d = ImageDraw.Draw(img)
for y in range(H):
    t = y / H
    d.line([(0, y), (W, y)], fill=(int(18 + 20 * t), int(26 + 24 * t), int(34 + 22 * t), 255))
for x in range(0, W, 80):
    d.line([(x, 0), (x, H)], fill=(255, 255, 255, 10))
for (x0, y0, x1) in [(0, 690, 520), (600, 600, 900), (980, 690, 1600), (420, 470, 560)]:
    d.rectangle([x0, y0, x1, H], fill=(52, 60, 70, 255), outline=(90, 104, 118, 255), width=3)
d.rectangle([760, 452, 800, 600], fill=(200, 160, 120, 255))  # 캐릭터 자리표시
if MODE == "boss":
    d.rectangle([1080, 300, 1300, 520], fill=(90, 90, 96, 255), outline=(255, 42, 74, 255), width=4)
    d.text((1190, 410), "BOSS (placeholder)", fill=(255, 200, 200, 200), anchor="mm", font=ImageFont.truetype(SYM, 16))
d.text((710, 425), "CHARACTER (placeholder)", fill=(220, 220, 220, 160), font=ImageFont.truetype(SYM, 14))

def to_px(sx, sy):
    return (sx + 1) / 2 * W, (1 - sy) / 2 * H

def tint(im, rgb):
    r, g, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    px = im.load()
    out = Image.new("RGBA", im.size)
    op = out.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            a, bb, c, al = px[x, y]
            op[x, y] = (a * r // 255, bb * g // 255, c * b // 255, al)
    return out

def font_for(fkey, ch, size_px):
    if fkey in TTF and (fkey != "boogeraids:lab" or ord(ch) < 0x7F) and (fkey != "boogeraids:digital" or ch in "0123456789:.-"):
        path, sz = TTF[fkey]
        return ImageFont.truetype(path, max(1, int(round(sz * size_px)))), sz
    if ord(ch) >= 0x1100:
        return ImageFont.truetype(CJK, max(1, int(round(8 * size_px)))), 8
    return ImageFont.truetype(SYM, max(1, int(round(8 * size_px)))), 8

def line_width(parts, k):
    w = 0
    for (t, c, f, b) in parts:
        for ch in t:
            fo, _ = font_for(f, ch, k)
            w += fo.getlength(ch)
    return w

order = ["frameOverlay", "vignette", "timerGhost", "timerLabel", "timerText", "subject", "stage", "title", "subtitle"]
els.sort(key=lambda e: -e["depth"])
for e in els:
    pixPerBlock = (H / 2) / e["hh"]
    ox, oy = to_px(e["sx"], e["sy"])
    k = PX * e["scaleY"] * pixPerBlock      # 화면 px / 텍스트 px
    kx = PX * e["scaleX"] * pixPerBlock
    if e["type"] == "ItemDisplay":
        icons = {"stage": ("FEATHER", "BLAZE_ROD"), "boss": ("BLAZE_ROD", "FIREWORK_ROCKET")}[MODE]
        name = icons[0] if e["name"].startswith("holder0") else icons[1]
        s = e["scaleY"] * pixPerBlock * 0.8
        col = {"FEATHER": (0, 229, 255, 255), "BLAZE_ROD": (255, 61, 110, 255), "FIREWORK_ROCKET": (124, 255, 79, 255)}[name]
        d.rounded_rectangle([ox - s / 2, oy - s / 2, ox + s / 2, oy + s / 2], radius=6, outline=col, width=2)
        d.text((ox, oy), name.replace("_", "\n"), fill=col, anchor="mm", font=ImageFont.truetype(SYM, 12), align="center")
        continue
    parts = [(p["t"], p["c"], p["f"], p["b"]) for p in e["parts"]]
    if not parts:
        continue
    # UI 글리프
    if len(parts) == 1 and len(parts[0][0]) == 1 and parts[0][0] in glyphs:
        g = glyphs[parts[0][0]]
        gim = Image.open(RP + "textures/ui/" + g["file"].split("/")[-1]).convert("RGBA")
        hpx = g["height"]
        wpx = gim.size[0] * hpx / gim.size[1]
        gw, gh = max(1, int(wpx * kx)), max(1, int(hpx * k))
        gim = tint(gim.resize((gw, gh), Image.BILINEAR), parts[0][1] if parts[0][1] >= 0 else 0xFFFFFF)
        cy = oy - 4.5 * k
        img.alpha_composite(gim, (int(ox - gw / 2), int(cy - gh / 2)))
        continue
    # 텍스트 블록
    lines = [[]]
    for p in parts:
        if p[0] == "\n":
            lines.append([])
        else:
            lines[-1].append(p)
    widths = [line_width(l, k) for l in lines]
    bw = max(widths) + 2 * k
    bh = len(lines) * 10 * k
    left, top = ox - bw / 2, oy - bh
    if e["bg"] and e["bg"][0] > 0:
        a, r, g, b = e["bg"]
        ov = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ImageDraw.Draw(ov).rectangle([left, top, left + bw, top + bh], fill=(r, g, b, a))
        img.alpha_composite(ov)
    d = ImageDraw.Draw(img)
    for i, l in enumerate(lines):
        if e["align"] == "LEFT":
            x = left + k
        elif e["align"] == "RIGHT":
            x = left + bw - k - widths[i]
        else:
            x = ox - widths[i] / 2
        base = top + (i * 10 + 8) * k
        for (t, c, f, b) in l:
            col = c if c >= 0 else 0xFFFFFF
            fill = ((col >> 16) & 255, (col >> 8) & 255, col & 255, 255)
            for ch in t:
                fo, _ = font_for(f, ch, k)
                d.text((x, base), ch, fill=fill, font=fo, anchor="ls", stroke_width=1 if b else 0, stroke_fill=fill)
                x += fo.getlength(ch)

final = Image.new("RGBA", (W, H + 34), (0, 0, 0, 255))
final.alpha_composite(img, (0, 0))
img = final
d = ImageDraw.Draw(img)
lab = ImageFont.truetype(CJK, 16)
d.text((12, H + 17), "HUD 레이아웃 시뮬레이션 — Hud.java 배치 계산 결과를 리소스팩 폰트/글리프로 그린 것 (실제 마인크래프트 렌더링 아님, FOV 70 · 16:9 가정)",
       fill=(255, 220, 120, 255), font=lab, anchor="lm")
img.convert("RGB").save("/tmp/test/hud_sim_" + MODE + ".png")
print("saved")
