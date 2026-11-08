#!/usr/bin/env python3
"""
챕터 기믹 스프라이트 (아이템 모델) 생성: 톱니, 가시, 스프링, 레일.
ItemDisplay + item_model 컴포넌트(boogeraids:<name>)로 쓰이며 화면 평면에서 Z축 회전으로 애니메이션한다.
  python3 tools/gen_sprites.py
"""
import json, math, os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "resourcepack", "assets", "boogeraids")
TEX = os.path.join(ROOT, "textures", "item")
MODELS = os.path.join(ROOT, "models", "item")
ITEMS = os.path.join(ROOT, "items")
SS = 4  # 슈퍼샘플링 배율


def down(im, size):
    return im.resize((size, size), Image.LANCZOS)


def saw():
    """
    원형 톱날 (오리지널 디자인).
    - 크고 창백한 갈고리형 톱니 14개 (앞면은 거의 반지름 방향으로 곧게, 등은 휘어짐)
    - 톱니마다 등쪽 그림자로 입체감
    - 작은 어두운 몸체 + 동심원 홈 + 슬롯 6개 + 볼트 3개 허브
    """
    S = 64 * SS
    c = S / 2
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    R_out, R_root = S * 0.495, S * 0.36
    n = 16
    teeth = []
    for i in range(n):
        a0 = 2 * math.pi * i / n
        tip = 2 * math.pi * (i + 0.86) / n
        poly = [(c + math.cos(a0) * R_root, c + math.sin(a0) * R_root)]
        for k in range(1, 7):  # 휘어진 등
            t = k / 6
            a = a0 + (tip - a0) * t
            r = R_root + (R_out - R_root) * (t ** 1.7)
            poly.append((c + math.cos(a) * r, c + math.sin(a) * r))
        poly.append((c + math.cos(tip + 0.02) * R_root * 0.97, c + math.sin(tip + 0.02) * R_root * 0.97))
        teeth.append(poly)
    for poly in teeth:
        d.polygon(poly, fill=(232, 228, 216, 255), outline=(70, 70, 74, 255))
        # 등쪽 그림자 (안쪽 절반)
        inner = [poly[0]] + poly[1:4] + [((poly[3][0] + c) / 2 * 0.15 + poly[3][0] * 0.85, (poly[3][1] + c) / 2 * 0.15 + poly[3][1] * 0.85)]
        d.polygon(inner, fill=(176, 172, 164, 255))
    # 창백한 날 원판 + 날을 따라 도는 미세 홈
    d.ellipse([c - R_root, c - R_root, c + R_root, c + R_root], fill=(214, 210, 200, 255))
    for i in range(16):
        a = 2 * math.pi * i / 16
        d.line([(c + math.cos(a) * S * 0.25, c + math.sin(a) * S * 0.25), (c + math.cos(a + 0.12) * R_root * 0.98, c + math.sin(a + 0.12) * R_root * 0.98)],
               fill=(168, 164, 156, 255), width=SS)
    R_body = S * 0.24
    d.ellipse([c - R_body, c - R_body, c + R_body, c + R_body], fill=(52, 55, 62, 255), outline=(22, 23, 27, 255), width=SS * 2)
    for rr, col in ((0.84, (36, 38, 44, 255)), (0.6, (72, 76, 86, 255))):
        r = R_body * rr
        d.ellipse([c - r, c - r, c + r, c + r], outline=col, width=SS * 2)
    for i in range(6):
        a = 2 * math.pi * i / 6 + 0.3
        r1, r2 = R_body * 0.42, R_body * 0.74
        w = 0.2
        poly = [(c + math.cos(a - w) * r1, c + math.sin(a - w) * r1), (c + math.cos(a - w * 0.6) * r2, c + math.sin(a - w * 0.6) * r2),
                (c + math.cos(a + w * 0.6) * r2, c + math.sin(a + w * 0.6) * r2), (c + math.cos(a + w) * r1, c + math.sin(a + w) * r1)]
        d.polygon(poly, fill=(14, 15, 18, 255))
    rh = R_body * 0.30
    d.ellipse([c - rh, c - rh, c + rh, c + rh], fill=(22, 23, 27, 255), outline=(130, 134, 140, 255), width=SS * 2)
    for i in range(3):
        a = 2 * math.pi * i / 3 - math.pi / 2
        bx, by, br = c + math.cos(a) * rh * 0.55, c + math.sin(a) * rh * 0.55, rh * 0.2
        d.ellipse([bx - br, by - br, bx + br, by + br], fill=(160, 164, 170, 255))
    r = R_body * 0.92
    d.arc([c - r, c - r, c + r, c + r], 200, 255, fill=(200, 206, 214, 220), width=SS * 2)
    return down(im, 64)


def spike():
    """한 칸(1블록) 아래쪽 절반을 채우는 삼각 가시 2개 (위쪽을 향함)."""
    S = 32 * SS
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    base_y, tip_y = S - SS, S * 0.5
    for i in range(2):
        x0, x1 = i * S / 2 + SS, (i + 1) * S / 2 - SS
        xm = (x0 + x1) / 2
        d.polygon([(x0, base_y), (xm, tip_y), (x1, base_y)], fill=(206, 210, 214, 255), outline=(40, 42, 48, 255))
        d.polygon([(xm, tip_y), (x1, base_y), (xm + (x1 - xm) * 0.15, base_y)], fill=(140, 146, 152, 255))
        d.polygon([(xm - SS * 1.5, tip_y + SS * 3), (xm, tip_y), (xm + SS * 1.5, tip_y + SS * 3)], fill=(255, 70, 80, 255))
    d.rectangle([0, S - SS * 3, S, S], fill=(50, 54, 60, 255))
    return down(im, 32)


def spring(compressed):
    """노란/검정 경고 줄무늬 상판 + 코일 + 바닥판. 칸의 아래쪽에 붙는다."""
    S = 32 * SS
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    base_h = SS * 3
    top_h = SS * 4
    coil_h = S * (0.18 if compressed else 0.38)
    base_y = S - base_h
    top_y = base_y - coil_h - top_h
    d.rectangle([SS * 3, base_y, S - SS * 3, S - 1], fill=(60, 64, 72, 255), outline=(25, 27, 31, 255), width=SS)
    turns = 2 if compressed else 3
    xs = [S * 0.26, S * 0.74]
    prev = None
    for k in range(turns * 2 + 1):
        y = base_y - coil_h * k / (turns * 2)
        x = xs[k % 2]
        if prev:
            d.line([prev, (x, y)], fill=(40, 42, 48, 255), width=SS * 5)
            d.line([prev, (x, y)], fill=(196, 202, 210, 255), width=SS * 3)
        prev = (x, y)
    d.rectangle([SS, top_y, S - SS, top_y + top_h], fill=(255, 196, 0, 255), outline=(25, 27, 31, 255), width=SS)
    for i in range(-2, 8):
        x = i * SS * 5
        d.polygon([(x, top_y + top_h), (x + SS * 2.5, top_y), (x + SS * 5, top_y), (x + SS * 2.5, top_y + top_h)], fill=(30, 30, 30, 255))
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rectangle([SS, 0, S - SS, S], fill=255)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(im, (0, 0), mask)
    return down(out, 32)


def rail():
    """톱니가 지나가는 레일. 가로로 늘려 쓴다."""
    S = 16 * SS
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    m = S / 2
    d.rectangle([0, m - SS * 1.5, S, m + SS * 1.5], fill=(28, 30, 34, 255))
    d.line([(0, m - SS * 1.5), (S, m - SS * 1.5)], fill=(110, 116, 124, 255), width=SS // 2)
    return down(im, 16)


def hub():
    S = 16 * SS
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([SS * 2, SS * 2, S - SS * 2, S - SS * 2], fill=(34, 36, 40, 255), outline=(120, 126, 134, 255), width=SS)
    d.ellipse([S / 2 - SS * 2, S / 2 - SS * 2, S / 2 + SS * 2, S / 2 + SS * 2], fill=(150, 156, 164, 255))
    return down(im, 16)


def write_model(name, img):
    img.save(os.path.join(TEX, name + ".png"))
    with open(os.path.join(MODELS, name + ".json"), "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "boogeraids:item/" + name}}, f, indent=2)
    with open(os.path.join(ITEMS, name + ".json"), "w") as f:
        json.dump({"model": {"type": "minecraft:model", "model": "boogeraids:item/" + name}}, f, indent=2)


if __name__ == "__main__":
    for p in (TEX, MODELS, ITEMS):
        os.makedirs(p, exist_ok=True)
    write_model("saw", saw())
    write_model("spike", spike())
    write_model("spring", spring(False))
    write_model("spring_pressed", spring(True))
    write_model("rail", rail())
    write_model("hub", hub())
    print("sprites generated")
