#!/usr/bin/env python3
"""
챕터 1 레벨 생성기.
  python3 tools/gen_levels.py            → src/main/resources/levels/chapter1.yml
  python3 tools/gen_levels.py --preview  → build/level-preview/*.png 도 생성

좌표: x = 화면 오른쪽, y = 위 (y=0 이 맨 아래 줄). 바닥은 y=0..1, 발 높이 y=2 가 기본.
물리 기준 (jump-velocity 0.62): 점프 최고 2.5칸, 짧게 톡 1.2~1.6칸, 수평 최대 ~4칸.
스프링 * = 1.0 (최고 5.9칸), ! = 1.25 (최고 8.8칸), 옆 스프링 = 수평 ~6칸.
"""
import json
import math
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "levels", "chapter1.yml")
PREVIEW = os.path.join(ROOT, "build", "level-preview")


class Stage:
    def __init__(self, sid, name, w, h, time=60):
        self.id, self.name, self.w, self.h, self.time = sid, name, w, h, time
        self.g = [["." for _ in range(w)] for _ in range(h)]
        self.objects = []
        self.camera = None
        # 테두리: 천장 + 좌우 벽
        self.rect(0, h - 1, w - 1, h - 1)
        self.rect(0, 0, 0, h - 1)
        self.rect(w - 1, 0, w - 1, h - 1)

    def set(self, x, y, c):
        assert 0 <= x < self.w and 0 <= y < self.h, (self.id, x, y)
        self.g[y][x] = c

    def rect(self, x0, y0, x1, y1, c="#"):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for x in range(min(x0, x1), max(x0, x1) + 1):
                self.set(x, y, c)

    def floor(self, x0, x1, top=2, c="#"):
        self.rect(x0, 0, x1, top - 1, c)

    def pit(self, x0, x1):
        self.rect(x0, 0, x1, 1, ".")

    def row(self, x0, x1, y, c):
        self.rect(x0, y, x1, y, c)

    # --- 오브젝트 (그리드 좌표)
    def saw(self, x, y, size=1.6, **kw):
        self.objects.append({"type": "saw", "at": [x, y], "size": size, **kw})

    def saw_path(self, pts, speed, size=1.6, phase=0.0, loop=False, track=True):
        self.objects.append({"type": "saw", "path": [list(p) for p in pts], "speed": speed, "size": size,
                             "phase": phase, "loop": loop, "track": track})

    def saw_orbit(self, cx, cy, radius, orbit, size=1.4, phase=0.0, track=True):
        self.objects.append({"type": "saw", "at": [cx, cy], "radius": radius, "orbit": orbit, "size": size,
                             "phase": phase, "track": track})

    def sign(self, x, y, text):
        self.objects.append({"type": "sign", "at": [x, y], "text": text})

    def hint(self, x0, y0, x1, y1, text):
        self.objects.append({"type": "hint", "min": [x0, y0], "max": [x1, y1], "text": text})

    def rows(self):
        return ["".join(r) for r in reversed(self.g)]

    def to_yaml_obj(self):
        d = {"id": self.id, "name": self.name, "time": self.time, "rows": self.rows(), "objects": self.objects}
        if self.camera:
            d["camera"] = self.camera
        return d


# =====================================================================
#  STAGE 1-1  튜토리얼: 이동 → 점프 → 구덩이 → 길게 점프 → 가시 → 스프링
# =====================================================================
def stage1():
    s = Stage("s1", "STAGE 1-1  보행 반응 테스트", 76, 14, time=75)
    s.floor(0, 75)
    s.set(3, 2, "S")
    s.sign(5.5, 6.0, "[A] ◀  이동  ▶ [D]")
    s.hint(1, 2, 7, 5, "A / D 로 좌우 이동")
    # 1칸 턱
    s.rect(14, 2, 17, 2)
    s.sign(12.5, 6.0, "[SPACE] 점프")
    s.hint(9, 2, 12, 5, "SPACE 로 점프 - 1칸 턱은 가볍게 넘어요")
    # 2칸 턱
    s.rect(21, 2, 24, 3)
    s.hint(18, 2, 20, 5, "2칸 높이도 점프로 올라갈 수 있어요")
    # 구덩이 2칸
    s.pit(29, 30)
    s.sign(29.5, 6.0, "추락 주의")
    s.hint(25, 2, 27, 6, "구덩이에 빠지면 실험 실패! 다음 피험자에게 넘어갑니다")
    # 넓은 구덩이 3칸
    s.pit(36, 38)
    s.hint(32, 2, 34, 6, "SPACE 를 길게 누르면 더 높이, 더 멀리")
    # 가시
    s.row(44, 45, 2, "^")
    s.sign(44.5, 5.5, "▲ 가시 ▲")
    s.hint(40, 2, 42, 6, "가시에 닿아도 실패 - 뛰어넘으세요")
    s.row(50, 52, 2, "^")
    # 스프링 → 높은 단 (+5)
    s.set(58, 2, "*")
    s.rect(61, 2, 74, 6)
    s.sign(57.5, 9.5, "스프링 ▼")
    s.hint(54, 2, 56, 6, "스프링을 밟으면 높이 튀어올라요 - 공중에서 D 로 단 위에 착지")
    s.set(71, 7, "E")
    s.hint(64, 7, 68, 10, "초록 출구에 도달하면 스테이지 클리어!")
    return s


# =====================================================================
#  STAGE 1-2  가시 회랑: 가시 웅덩이 / 천장 가시 터널(짧은 점프) / 가시 바닥 위 발판
# =====================================================================
def stage2():
    s = Stage("s2", "STAGE 1-2  가시 회랑", 90, 14, time=60)
    s.floor(0, 89)
    s.set(2, 2, "S")
    s.row(8, 9, 2, "^")
    s.row(15, 17, 2, "^")
    # 가시를 얹은 계단
    s.rect(21, 2, 23, 2)
    s.rect(24, 2, 26, 3)
    s.set(25, 4, "^")
    s.rect(27, 2, 29, 2)
    s.hint(19, 2, 20, 5, "가시 사이 빈 칸을 골라 밟으세요")
    # 천장 가시 터널 (천장 y>=6, 가시 y=5): 서 있으면 안전, 크게 뛰면 위험
    s.rect(34, 6, 52, 12)
    s.row(35, 51, 5, "v")
    s.pit(43, 43)
    s.hint(30, 2, 33, 5, "천장 가시! 구멍은 SPACE 를 짧게 톡 눌러 넘으세요")
    # 가시 바닥 + 발판
    s.rect(57, 0, 75, 1, ".")
    s.row(57, 75, 0, "#")
    s.row(57, 75, 1, "^")
    for x0 in (60, 66, 72):
        s.rect(x0, 1, x0 + 2, 1, "=")
    s.hint(53, 2, 56, 6, "가시 바닥 - 금속 발판만 밟을 것")
    # 마무리 단 (+2)
    s.rect(80, 2, 88, 3)
    s.set(85, 4, "E")
    return s


# =====================================================================
#  STAGE 1-3  스프링 샤프트: 위로 올라가는 세로 스테이지 (떨어져도 아래 층으로 돌아갈 뿐)
# =====================================================================
def stage3():
    s = Stage("s3", "STAGE 1-3  스프링 샤프트", 32, 40, time=70)
    s.floor(0, 31)
    s.set(3, 2, "S")
    s.row(22, 30, 2, "^")                      # 오른쪽 바닥 가시
    s.set(12, 2, "*")                          # → L1 (+5)
    s.hint(5, 2, 9, 5, "스프링을 연달아 밟아 위로 올라가세요")
    s.rect(14, 6, 21, 6)                       # L1 top 7
    s.set(20, 7, "!")                          # → L2 (+7)
    s.rect(6, 13, 16, 13)                      # L2 top 14
    s.row(6, 12, 12, "v")                      # L2 아래 가시
    s.set(8, 14, "*")                          # → L3 (+5)
    s.rect(1, 18, 5, 18)                       # L3 top 19
    s.rect(1, 19, 1, 19)                       # 옆 스프링이 붙을 벽
    s.set(2, 19, "}")                          # 옆 스프링: 왼쪽으로 걸어 들어가면 오른쪽으로 발사
    s.hint(2, 19, 5, 22, "옆 스프링 - 왼쪽으로 걸어 들어가면 반대로 튕겨나가요")
    s.rect(12, 18, 20, 18)                     # L4 top 19 (L3 와 6칸 간격 → 옆 스프링으로 건넘)
    s.set(19, 19, "!")                         # → L5 (+7)
    s.rect(22, 25, 30, 25)                     # L5 top 26
    for y in range(8, 14):
        s.set(30, y, "<")                      # 오른쪽 벽 가시
    for y in range(20, 26):
        s.set(1, y, ">")                       # 왼쪽 벽 가시
    s.set(27, 26, "E")
    s.camera = {"deadzone-y": 0.8, "vertical-follow-speed": 0.12}
    return s


# =====================================================================
#  STAGE 1-4  회전 톱날 입문: 묻힌 톱날 / 오르내리는 톱날 / 구덩이 위 톱날 / 굴러오는 톱날
# =====================================================================
def stage4():
    s = Stage("s4", "STAGE 1-4  회전 톱날", 96, 14, time=60)
    s.floor(0, 95)
    s.set(2, 2, "S")
    s.hint(4, 2, 8, 5, "회전 톱날 - 스치기만 해도 절단됩니다")
    s.saw(14.5, 2.0, size=2.0)
    s.saw(24.5, 2.0, size=2.0)
    s.saw(31.5, 2.0, size=2.0)
    # 오르내리는 톱날
    s.hint(35, 2, 37, 5, "톱날이 올라간 틈을 노리세요")
    s.saw_path([(42.5, 2.0), (42.5, 8.5)], speed=0.13, size=1.8)
    s.saw_path([(50.5, 2.0), (50.5, 8.5)], speed=0.13, size=1.8, phase=0.5)
    s.saw_path([(56.5, 2.0), (56.5, 8.5)], speed=0.13, size=1.8, phase=0.25)
    # 구덩이 위 고정 톱날: 크게 뛰면 닿음
    s.pit(63, 64)
    s.saw(64.0, 6.6, size=1.6)
    s.hint(59, 2, 61, 5, "위에 톱날! 점프를 너무 높이 하지 마세요")
    # 바닥을 굴러오는 톱날
    s.saw_path([(70.5, 2.0), (81.5, 2.0)], speed=0.11, size=2.0)
    s.hint(67, 2, 68, 5, "굴러오는 톱날은 타이밍 맞춰 뛰어넘기")
    s.rect(86, 2, 94, 2)
    s.set(91, 3, "E")
    return s


# =====================================================================
#  STAGE 1-5  레일 회로: 공전 톱날 / 레일 위 왕복 톱날 / 순환 레일 / 스프링 + 톱날
# =====================================================================
def stage5():
    s = Stage("s5", "STAGE 1-5  레일 회로", 104, 16, time=70)
    s.floor(0, 103)
    s.set(2, 2, "S")
    # 공전 톱날이 도는 발판
    s.rect(10, 2, 11, 2)
    s.rect(12, 4, 16, 4, "=")
    s.saw_orbit(14.5, 5.5, 3.2, 2.6, size=1.4)
    s.hint(5, 2, 8, 5, "발판 가운데는 안전 지대 - 톱날이 지나가면 이동")
    # 가시 바닥 위 발판 + 위를 왕복하는 레일 톱날
    s.row(20, 46, 2, "^")
    for x0 in (21, 27, 33, 39):
        s.rect(x0, 3, x0 + 2, 3, "=")
    s.rect(45, 2, 46, 2, ".")
    s.row(45, 46, 2, "^")
    s.saw_path([(19.0, 8.0), (47.0, 8.0)], speed=0.16, size=1.7)
    s.hint(17, 2, 19, 6, "머리 위 톱날이 멀어졌을 때 점프")
    # 순환 레일 (사각 루프 2개 톱날)
    # 바닥 구간에서는 플레이어 쪽으로 다가오도록 역방향(speed < 0) → 정면으로 뛰어넘기
    s.saw_path([(52.0, 2.0), (64.0, 2.0), (64.0, 7.0), (52.0, 7.0)], speed=-0.15, size=1.8, loop=True)
    s.saw_path([(52.0, 2.0), (64.0, 2.0), (64.0, 7.0), (52.0, 7.0)], speed=-0.15, size=1.8, loop=True, phase=0.5)
    # 스프링 → 높은 단, 위쪽 고정 톱날 피해서 오른쪽으로
    s.set(70, 2, "*")
    s.saw(70.0, 10.0, size=1.6)
    s.rect(72, 2, 102, 6)
    s.hint(66, 2, 68, 5, "스프링을 밟자마자 오른쪽으로!")
    # 단 위: 두 개가 마주 도는 공전 톱날
    s.saw_orbit(84.5, 9.0, 2.4, 3.2, size=1.3, phase=0)
    s.saw_orbit(84.5, 9.0, 2.4, 3.2, size=1.3, phase=180, track=False)
    s.saw_path([(92.5, 7.0), (92.5, 12.0)], speed=0.14, size=1.6)
    s.set(99, 7, "E")
    return s


# =====================================================================
#  STAGE 1-6  통증 내성 시험: 종합 (천장 가시 + 바닥 톱날, 스프링 기둥, 톱날 파도)
# =====================================================================
def stage6():
    s = Stage("s6", "STAGE 1-6  통증 내성 시험", 116, 18, time=80)
    s.floor(0, 115)
    s.set(2, 2, "S")
    # A. 천장 가시 복도 + 바닥을 오가는 톱날
    s.rect(7, 7, 31, 16)
    s.row(8, 30, 6, "v")
    s.saw_path([(9.0, 2.0), (17.0, 2.0)], speed=0.12, size=1.8)
    s.saw_path([(20.0, 2.0), (29.0, 2.0)], speed=0.14, size=1.8, phase=0.5)
    s.hint(3, 2, 6, 5, "최종 점검 - 지금까지 배운 것을 모두 사용하세요")
    # B. 스프링 기둥 건너기 (아래는 가시 바닥)
    s.rect(35, 0, 60, 1, ".")
    s.row(35, 60, 0, "#")
    s.row(35, 60, 1, "^")
    for x in (37, 43, 49, 55):
        s.rect(x, 1, x, 1, "#")
        s.set(x, 2, "*")
    # 스프링 연쇄 중에는 타이밍을 고를 수 없으므로 위협 대신 가시 바닥에 묻힌 톱날로 긴장감만
    for x in (40.0, 46.0, 52.0, 58.0):
        s.saw(x, 1.0, size=1.6)
    s.hint(32, 2, 34, 5, "스프링 기둥을 따라 오른쪽으로 - 공중에서 방향 조절")
    # C. 톱날 파도 (위상차 상하 톱날) + 바닥 가시
    for i, x in enumerate((68.5, 74.5, 80.5, 86.5, 92.5)):
        s.saw_path([(x, 2.0), (x, 8.0)], speed=0.15, size=1.7, phase=i * 0.2)
    s.row(71, 71, 2, "^")
    s.row(83, 83, 2, "^")
    s.hint(64, 2, 66, 5, "톱날 파도 - 리듬을 타세요")
    # D. 마지막 2칸 단 + 출구
    s.rect(100, 2, 114, 3)
    s.saw(104.5, 4.0, size=1.8)
    s.set(111, 4, "E")
    return s


# =====================================================================
#  BOSS
# =====================================================================
def boss():
    s = Stage("boss", "BOSS  SPECIMEN-00 // 관리 AI 감시체", 46, 18)
    s.floor(0, 45)
    s.set(4, 2, "S")
    s.rect(1, 6, 3, 6, "=")
    s.rect(42, 6, 44, 6, "=")
    s.objects.append({"type": "ability", "ability": "PLASMA", "slot": "LEFT", "at": [9.5, 2.0]})
    s.objects.append({"type": "ability", "ability": "BOOST", "slot": "RIGHT", "at": [36.5, 2.0]})
    s.hint(1, 2, 8, 5, "캡슐을 얻어 공격 / 보스를 위에서 밟아도 피해")
    d = s.to_yaml_obj()
    d.pop("time")
    d["boss"] = {"name": "SENTINEL-00", "health": 40, "item": "OBSERVER", "scale": 3.0,
                 "at": [23.0, 9.0], "arena": [1, 2, 45, 16], "idle-ticks": 64}
    return s, d


STAGES = [stage1, stage2, stage3, stage4, stage5, stage6]


# ---------------------------------------------------------------- yaml
def ystr(v):
    return json.dumps(v, ensure_ascii=False)


def dump(stages, bossd):
    L = ["# 자동 생성: tools/gen_levels.py  (직접 수정해도 되지만 생성기로 다시 만들면 덮어써짐)",
         "# 범례: # 벽  = 발판  L 조명  x 부술수있음  S 스폰  E 출구  ^ v < > 가시  * ! 스프링  { } 옆 스프링",
         "id: c1", 'name: "CHAPTER 01 // 격리 구역"', "spacing: 52", "stages:"]

    def body(d, ind):
        out = []
        for k in ("id", "name", "time"):
            if k in d:
                out.append(f"{ind}{k}: {ystr(d[k])}")
        if d.get("camera"):
            out.append(f"{ind}camera: {ystr(d['camera'])}")
        if d.get("boss"):
            out.append(f"{ind}boss: {ystr(d['boss'])}")
        out.append(f"{ind}rows:")
        for r in d["rows"]:
            out.append(f"{ind}  - {ystr(r)}")
        out.append(f"{ind}objects:")
        for o in d["objects"]:
            out.append(f"{ind}  - {ystr(o)}")
        if not d["objects"]:
            out[-1] += " []"
        return out

    for d in stages:
        b = body(d, "    ")
        b[0] = "  - " + b[0].strip()
        L += b
    L.append("boss:")
    L += body(bossd, "  ")
    return "\n".join(L) + "\n"


# ---------------------------------------------------------------- 검증
def validate(st):
    rows = st.rows()
    flat = "".join(rows)
    assert flat.count("S") == 1, f"{st.id}: 스폰은 1개"
    if st.id != "boss":
        assert "E" in flat, f"{st.id}: 출구 없음"
    # 출구 위 3칸 비어 있어야 함
    for y in range(st.h):
        for x in range(st.w):
            if st.g[y][x] == "E":
                for dy in (1, 2):
                    assert st.g[y + dy][x] in ".E", f"{st.id}: 출구 위가 막힘 ({x},{y + dy})"
            if st.g[y][x] in "S":
                assert st.g[y - 1][x] in "#=", f"{st.id}: 스폰 발밑이 비어 있음"
                assert st.g[y + 1][x] == "." and st.g[y + 2][x] == ".", f"{st.id}: 스폰 머리 위가 막힘"
            if st.g[y][x] in "*!":
                assert st.g[y - 1][x] in "#=", f"{st.id}: 스프링 ({x},{y}) 받침 없음"


# ---------------------------------------------------------------- preview
def preview(st, objects):
    from PIL import Image, ImageDraw, ImageFont
    T = 18
    W, H = st.w * T, st.h * T
    pad_top = 34
    img = Image.new("RGBA", (W, H + pad_top), (14, 16, 20, 255))
    d = ImageDraw.Draw(img)
    tex = os.path.join(ROOT, "resourcepack", "assets", "boogeraids", "textures", "item")
    saw = Image.open(os.path.join(tex, "saw.png")).convert("RGBA")
    spike = Image.open(os.path.join(tex, "spike.png")).convert("RGBA")
    spring = Image.open(os.path.join(tex, "spring.png")).convert("RGBA")
    spring_hi = spring
    font = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
    f12 = ImageFont.truetype(font, 11)
    f14 = ImageFont.truetype(font, 15)

    def P(x, y):  # 그리드 점 → 픽셀
        return x * T, pad_top + (st.h - y) * T

    # 뒷벽
    for y in range(st.h):
        for x in range(st.w):
            px, py = P(x, y + 1)
            col = (62, 66, 72) if x % 6 else (82, 86, 92)
            if y in (3, st.h - 4):
                col = (60, 96, 100)
            d.rectangle([px, py, px + T - 1, py + T - 1], fill=col + (255,))
    # 타일
    for y in range(st.h):
        for x in range(st.w):
            c = st.g[y][x]
            px, py = P(x, y + 1)
            if c == "#":
                top = y + 1 >= st.h or st.g[y + 1][x] not in "#=Lx"
                d.rectangle([px, py, px + T - 1, py + T - 1], fill=(236, 238, 240, 255) if top else (160, 164, 168, 255))
                d.rectangle([px, py, px + T - 1, py + T - 1], outline=(120, 124, 130, 255))
            elif c == "=":
                d.rectangle([px, py, px + T - 1, py + T - 1], fill=(170, 172, 176, 255), outline=(90, 92, 96, 255))
            elif c == "E":
                d.rectangle([px - T, py - 2 * T, px + 2 * T - 1, py + T - 1], outline=(0, 229, 255, 255), width=2)
                d.rectangle([px, py - 2 * T, px + T - 1, py + T - 1], fill=(20, 60, 40, 255))
            elif c == "S":
                d.rectangle([px + 3, py - T * 0.8, px + T - 4, py + T - 1], fill=(220, 170, 120, 255))
            elif c in "^v<>":
                ang = {"^": 0, "v": 180, "<": 90, ">": -90}[c]
                im = spike.resize((T, T), Image.NEAREST).rotate(ang)
                img.alpha_composite(im, (int(px), int(py)))
            elif c in "*!{}":
                ang = {"*": 0, "!": 0, "{": 90, "}": -90}[c]
                im = spring.resize((T, T), Image.NEAREST).rotate(ang)
                img.alpha_composite(im, (int(px), int(py)))
                if c == "!":
                    d.text((px + T / 2, py - 2), "!!", fill=(255, 200, 0, 255), font=f12, anchor="mb")
    # 오브젝트
    for o in objects:
        t = o["type"]
        if t == "saw":
            size = o.get("size", 1.6)
            if "path" in o:
                pts = o["path"] + ([o["path"][0]] if o.get("loop") else [])
                d.line([P(*p) for p in pts], fill=(20, 22, 26, 255), width=4)
                d.line([P(*p) for p in pts], fill=(110, 116, 124, 255), width=1)
                n = len(o["path"])
                # 위상 위치에 톱날
                cum = [0]
                for i in range(1, len(pts)):
                    cum.append(cum[-1] + math.dist(pts[i - 1], pts[i]))
                tot = cum[-1]
                dist = o.get("phase", 0) * (tot if o.get("loop") else 2 * tot)
                if not o.get("loop") and dist > tot:
                    dist = 2 * tot - dist
                i = 1
                while i < len(cum) - 1 and cum[i] < dist:
                    i += 1
                k = 0 if cum[i] == cum[i - 1] else (dist - cum[i - 1]) / (cum[i] - cum[i - 1])
                cx = pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * k
                cy = pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * k
                # 이동 범위 고스트
                for p in o["path"]:
                    gx, gy = P(*p)
                    r = size / 2 * T
                    d.ellipse([gx - r, gy - r, gx + r, gy + r], outline=(232, 228, 216, 90), width=1)
            elif "radius" in o:
                ccx, ccy = o["at"]
                r = o["radius"] * T
                gx, gy = P(ccx, ccy)
                d.ellipse([gx - r, gy - r, gx + r, gy + r], outline=(20, 22, 26, 255), width=4)
                d.ellipse([gx - r, gy - r, gx + r, gy + r], outline=(110, 116, 124, 255), width=1)
                a = math.radians(o.get("phase", 0))
                cx, cy = ccx + math.cos(a) * o["radius"], ccy + math.sin(a) * o["radius"]
            else:
                cx, cy = o["at"]
            gx, gy = P(cx, cy)
            s_px = int(size * T)
            im = saw.resize((s_px, s_px), Image.LANCZOS)
            img.alpha_composite(im, (int(gx - s_px / 2), int(gy - s_px / 2)))
        elif t == "sign":
            gx, gy = P(*o["at"])
            tw = d.textlength(o["text"], font=f12)
            d.rectangle([gx - tw / 2 - 3, gy - 9, gx + tw / 2 + 3, gy + 7], fill=(6, 10, 16, 200))
            d.text((gx, gy), o["text"], fill=(216, 246, 255, 255), font=f12, anchor="mm")
        elif t == "hint":
            x0, y0 = o["min"]
            x1, y1 = o["max"]
            a, b = P(x0, y1 + 1), P(x1 + 1, y0)
            d.rectangle([a[0], a[1], b[0], b[1]], outline=(0, 229, 255, 120), width=1)
        elif t == "ability":
            gx, gy = P(o["at"][0], o["at"][1] + 1.1)
            d.ellipse([gx - 7, gy - 7, gx + 7, gy + 7], outline=(255, 61, 110, 255), width=2)
            d.text((gx, gy - 12), o["ability"], fill=(255, 255, 255, 255), font=f12, anchor="mb")
    title = f"{st.name}" + (f"   ⏱ {st.time}s" if st.id != "boss" else "") + f"   {st.w}×{st.h}"
    d.rectangle([0, 0, W, pad_top - 4], fill=(6, 10, 16, 255))
    d.text((8, (pad_top - 4) / 2), title, fill=(0, 229, 255, 255), font=f14, anchor="lm")
    return img


def main():
    built = []
    for fn in STAGES:
        st = fn()
        validate(st)
        built.append(st)
    bst, bd = boss()
    validate(bst)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(dump([s.to_yaml_obj() for s in built], bd))
    print("wrote", os.path.relpath(OUT, ROOT))
    if "--preview" in sys.argv:
        os.makedirs(PREVIEW, exist_ok=True)
        for st in built + [bst]:
            p = os.path.join(PREVIEW, f"{st.id}.png")
            preview(st, st.objects).convert("RGB").save(p)
            print("preview", os.path.relpath(p, ROOT))


if __name__ == "__main__":
    main()
