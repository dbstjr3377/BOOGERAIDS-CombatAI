#!/usr/bin/env python3
"""
BOOGERAIDS 리소스팩 에셋 생성기.

  python3 tools/gen_pack.py

resourcepack/ 폴더 아래에 UI 글리프(PNG), 폰트 정의(JSON), pack.mcmeta, 언어 오버라이드를 생성한다.
폰트 TTF(DSEG7, Share Tech Mono)는 이미 resourcepack/assets/boogeraids/font/ 에 포함되어 있다 (OFL).
Gradle 빌드가 resourcepack/ 를 zip 으로 묶어 플러그인 jar 안에 넣는다.
"""
import json
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "resourcepack")
UI = os.path.join(ROOT, "assets", "boogeraids", "textures", "ui")
FONT = os.path.join(ROOT, "assets", "boogeraids", "font")
HUD_SPRITES = os.path.join(ROOT, "assets", "minecraft", "textures", "gui", "sprites", "hud")
LANG = os.path.join(ROOT, "assets", "minecraft", "lang")

CYAN = (0, 229, 255, 255)
CYAN_DIM = (0, 229, 255, 90)
PANEL = (8, 12, 18, 175)


def ensure(*dirs):
    for d in dirs:
        os.makedirs(d, exist_ok=True)


def holder():
    """스킬 홀더 프레임: 어두운 반투명 패널 + 시안 모서리 브래킷 + 컷 코너."""
    s = 64
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cut = 8
    poly = [(cut, 1), (s - 2, 1), (s - 2, s - cut - 1), (s - cut - 1, s - 2), (1, s - 2), (1, cut)]
    d.polygon(poly, fill=PANEL)
    # 내부 미세 그리드
    for i in range(8, s - 4, 8):
        d.line([(4, i), (s - 5, i)], fill=(0, 229, 255, 18))
        d.line([(i, 4), (i, s - 5)], fill=(0, 229, 255, 18))
    d.line(poly + [poly[0]], fill=CYAN_DIM, width=1)
    # 모서리 브래킷 (굵게)
    L = 14
    w = 3
    d.line([(cut, 1), (cut + L, 1)], fill=CYAN, width=w)
    d.line([(1, cut), (1, cut + L)], fill=CYAN, width=w)
    d.line([(1, cut), (cut, 1)], fill=CYAN, width=w)
    d.line([(s - 2 - L, s - 2), (s - cut - 1, s - 2)], fill=CYAN, width=w)
    d.line([(s - 2, s - cut - 1 - L), (s - 2, s - cut - 1)], fill=CYAN, width=w)
    d.line([(s - 2, s - cut - 1), (s - cut - 1, s - 2)], fill=CYAN, width=w)
    d.line([(s - 2 - L, 1), (s - 2, 1)], fill=CYAN, width=w)
    d.line([(s - 2, 1), (s - 2, 1 + L)], fill=CYAN, width=w)
    d.line([(1, s - 2 - L), (1, s - 2)], fill=CYAN, width=w)
    d.line([(1, s - 2), (1 + L, s - 2)], fill=CYAN, width=w)
    im.save(os.path.join(UI, "holder.png"))


def cooldown():
    """쿨타임 오버레이 (홀더 내부 크기와 일치하는 어두운 사각형)."""
    s = 64
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([4, 4, s - 5, s - 5], fill=(0, 0, 0, 185))
    # 대각 해칭
    for i in range(-s, s, 6):
        d.line([(i, s), (i + s, 0)], fill=(255, 40, 60, 40), width=1)
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rectangle([4, 4, s - 5, s - 5], fill=255)
    out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    out.paste(im, (0, 0), mask)
    out.save(os.path.join(UI, "cooldown.png"))


def white():
    Image.new("RGBA", (16, 16), (255, 255, 255, 255)).save(os.path.join(UI, "white.png"))


def vignette():
    """플래시/경고용 비네트 (흰색, 텍스트 색으로 틴트)."""
    w, h = 256, 144
    im = Image.new("RGBA", (w, h))
    px = im.load()
    for y in range(h):
        for x in range(w):
            nx = (x + 0.5) / w * 2 - 1
            ny = (y + 0.5) / h * 2 - 1
            r = math.sqrt((nx * 0.92) ** 2 + (ny * 1.05) ** 2)
            a = max(0.0, min(1.0, (r - 0.45) / 0.75))
            a = a ** 1.6
            px[x, y] = (255, 255, 255, int(235 * a))
    im.save(os.path.join(UI, "vignette.png"))


def hudframe():
    """항상 켜진 연구소 카메라 프레임: 모서리 마커 + 아주 옅은 스캔라인."""
    w, h = 512, 288
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for y in range(0, h, 3):
        d.line([(0, y), (w, y)], fill=(255, 255, 255, 7))
    m, L, t = 10, 34, 2
    c = (255, 255, 255, 120)
    for (x0, y0, sx, sy) in [(m, m, 1, 1), (w - 1 - m, m, -1, 1), (m, h - 1 - m, 1, -1), (w - 1 - m, h - 1 - m, -1, -1)]:
        d.line([(x0, y0), (x0 + sx * L, y0)], fill=c, width=t)
        d.line([(x0, y0), (x0, y0 + sy * L)], fill=c, width=t)
    # 중앙 십자 눈금 (아주 옅게)
    cx, cy = w // 2, h // 2
    d.line([(cx - 6, cy), (cx - 2, cy)], fill=(255, 255, 255, 40))
    d.line([(cx + 2, cy), (cx + 6, cy)], fill=(255, 255, 255, 40))
    d.line([(cx, cy - 6), (cx, cy - 2)], fill=(255, 255, 255, 40))
    d.line([(cx, cy + 2), (cx, cy + 6)], fill=(255, 255, 255, 40))
    im.save(os.path.join(UI, "hudframe.png"))


def bossframe():
    w, h = 256, 16
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, w - 1, h - 1], outline=(255, 60, 70, 255), width=2)
    for x in range(16, w - 8, 16):
        d.line([(x, h - 5), (x, h - 3)], fill=(255, 60, 70, 160))
    d.polygon([(0, 0), (6, 0), (0, 6)], fill=(255, 60, 70, 255))
    d.polygon([(w - 1, h - 1), (w - 7, h - 1), (w - 1, h - 7)], fill=(255, 60, 70, 255))
    im.save(os.path.join(UI, "bossframe.png"))


def mouse(left):
    w, h = 24, 32
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([1, 1, w - 2, h - 2], radius=10, fill=(10, 14, 20, 220), outline=(220, 240, 255, 255), width=2)
    d.line([(w // 2, 2), (w // 2, 13)], fill=(220, 240, 255, 255), width=2)
    d.line([(2, 13), (w - 3, 13)], fill=(220, 240, 255, 255), width=2)
    if left:
        d.rounded_rectangle([3, 3, w // 2 - 2, 11], radius=4, fill=CYAN)
    else:
        d.rounded_rectangle([w // 2 + 2, 3, w - 4, 11], radius=4, fill=CYAN)
    im.save(os.path.join(UI, "mouse_l.png" if left else "mouse_r.png"))


def warn():
    s = 32
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(s // 2, 2), (s - 2, s - 3), (2, s - 3)], fill=(255, 200, 0, 255), outline=(30, 20, 0, 255))
    d.rectangle([s // 2 - 2, 11, s // 2 + 1, 21], fill=(30, 20, 0, 255))
    d.rectangle([s // 2 - 2, 24, s // 2 + 1, 26], fill=(30, 20, 0, 255))
    im.save(os.path.join(UI, "warn.png"))


def transparent(path, w, h):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.new("RGBA", (w, h), (0, 0, 0, 0)).save(path)


def hide_vanilla_hud():
    """통제권 플레이어(어드벤처 모드)의 바닐라 HUD를 숨긴다."""
    t = transparent
    t(os.path.join(HUD_SPRITES, "hotbar.png"), 182, 22)
    t(os.path.join(HUD_SPRITES, "hotbar_selection.png"), 24, 23)
    t(os.path.join(HUD_SPRITES, "hotbar_offhand_left.png"), 29, 24)
    t(os.path.join(HUD_SPRITES, "hotbar_offhand_right.png"), 29, 24)
    t(os.path.join(HUD_SPRITES, "crosshair.png"), 15, 15)
    t(os.path.join(HUD_SPRITES, "experience_bar_background.png"), 182, 5)
    t(os.path.join(HUD_SPRITES, "experience_bar_progress.png"), 182, 5)
    for name in ["container", "container_blinking", "full", "half", "full_blinking", "half_blinking",
                 "hardcore_full", "hardcore_half", "hardcore_full_blinking", "hardcore_half_blinking",
                 "absorbing_full", "absorbing_half", "absorbing_full_blinking", "absorbing_half_blinking",
                 "container_hardcore", "container_hardcore_blinking",
                 "poisoned_full", "poisoned_half", "withered_full", "withered_half", "frozen_full", "frozen_half"]:
        t(os.path.join(HUD_SPRITES, "heart", name + ".png"), 9, 9)
    for name in ["food_empty", "food_full", "food_half", "food_empty_hunger", "food_full_hunger", "food_half_hunger",
                 "air", "air_bursting", "air_empty", "armor_empty", "armor_full", "armor_half"]:
        t(os.path.join(HUD_SPRITES, name + ".png"), 9, 9)


def bitmap(file, ch, height, ascent=None):
    if ascent is None:
        # 글리프 중심이 기본 줄(9px)의 중앙에 오도록: ascent = H/2 + 2.5
        ascent = min(height, int(round(height / 2 + 2.5)))
    return {"type": "bitmap", "file": "boogeraids:ui/" + file, "ascent": ascent, "height": height, "chars": [ch]}


def fonts():
    ui = {"providers": [
        bitmap("holder.png", "", 32),
        bitmap("cooldown.png", "", 32),
        bitmap("white.png", "", 8),
        bitmap("vignette.png", "", 144),
        bitmap("hudframe.png", "", 144),
        bitmap("bossframe.png", "", 8),
        bitmap("mouse_l.png", "", 12),
        bitmap("mouse_r.png", "", 12),
        bitmap("warn.png", "", 16),
    ]}
    digital = {"providers": [
        {"type": "ttf", "file": "boogeraids:dseg7.ttf", "size": 12.0, "oversample": 6.0, "shift": [0.0, 1.0], "skip": []},
        {"type": "reference", "id": "minecraft:default"},
    ]}
    lab = {"providers": [
        {"type": "ttf", "file": "boogeraids:sharetechmono.ttf", "size": 9.0, "oversample": 6.0, "shift": [0.0, 0.5], "skip": []},
        {"type": "reference", "id": "minecraft:default"},
    ]}
    for name, data in (("ui", ui), ("digital", digital), ("lab", lab)):
        with open(os.path.join(FONT, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=True, indent=2)


def meta():
    data = {"pack": {
        "description": "BOOGERAIDS // CombatAI Lab HUD",
        "min_format": 84,
        "max_format": 999,
    }}
    with open(os.path.join(ROOT, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)


def lang():
    # 탑승 중 "Shift를 눌러 내리기" 안내 문구 제거
    for code in ("en_us", "ko_kr"):
        with open(os.path.join(LANG, code + ".json"), "w", encoding="utf-8") as f:
            json.dump({"mount.onboard": ""}, f, indent=2)


if __name__ == "__main__":
    ensure(UI, FONT, HUD_SPRITES, LANG)
    holder(); cooldown(); white(); vignette(); hudframe(); bossframe(); mouse(True); mouse(False); warn()
    hide_vanilla_hud(); fonts(); meta(); lang()
    print("resourcepack generated at", os.path.normpath(ROOT))
