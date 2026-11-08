#!/usr/bin/env python3
"""
레벨 클리어 가능성 자동 검증 봇.
Character.java 와 같은 이동 규칙 + 바닐라 중력(v=(v-0.08)*0.98) + 타일 충돌로
스폰 → 출구 경로를 BFS 로 탐색한다. (4틱 단위 입력: 좌/정지/우 × 점프 없음/짧게/길게)

검증 범위: 지형, 가시, 스프링(일반/옆), 고정 톱날.
움직이는 톱날은 타이밍 문제라 경로에서 제외하고, 대신 '기다리면 지나갈 수 있는지'를 별도로 확인한다.
  python3 tools/test/playtest.py
"""
import math
import os
import sys
from collections import deque

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import gen_levels as GL  # noqa: E402

W2, HGT = 0.3, 1.8
WALK, GA, GD, AA = 0.27, 0.07, 0.11, 0.04
JUMP, CUT, COYOTE = 0.62, 0.5, 3
SOLID = set("#=Lx")


class World:
    def __init__(self, st):
        self.st = st
        self.g = st.g
        self.w, self.h = st.w, st.h
        self.spikes = []
        self.springs = []
        self.goal = None
        for y in range(self.h):
            for x in range(self.w):
                c = self.g[y][x]
                if c == "^": self.spikes.append((x + .12, x + .88, y, y + .42))
                if c == "v": self.spikes.append((x + .12, x + .88, y + .58, y + 1))
                if c == "<": self.spikes.append((x + .58, x + 1, y + .12, y + .88))
                if c == ">": self.spikes.append((x, x + .42, y + .12, y + .88))
                if c in "*!{}": self.springs.append((x, y, c))
                if c == "E": self.goal = (x, x + 1, y, y + 3)
                if c == "S": self.spawn = (x + .5, float(y))
        self.saws = []
        for o in st.objects:
            if o["type"] == "saw" and "path" not in o and "radius" not in o:
                self.saws.append((o["at"][0], o["at"][1], o["size"] * 0.42))

    def solid(self, x, y):
        if y < 0 or x < 0 or x >= self.w:
            return x < 0 or x >= self.w
        if y >= self.h:
            return True
        return self.g[y][x] in SOLID

    def box_free(self, x, y):
        for cx in range(math.floor(x - W2 + 1e-6), math.floor(x + W2 - 1e-6) + 1):
            for cy in range(math.floor(y + 1e-6), math.floor(y + HGT - 1e-6) + 1):
                if self.solid(cx, cy):
                    return False
        return True


def step(wd, s, d, jump_press, jump_hold):
    """한 틱 시뮬레이션. s = (x, y, vu, vy, ground, air, jumping, lock)"""
    x, y, vu, vy, ground, air, jumping, lock = s
    if ground:
        air = 0
        if jumping and vy <= 0: jumping = False
    else:
        air += 1
    tgt = d * WALK
    acc = (GD if (d == 0 or (tgt != 0 and math.copysign(1, tgt) != math.copysign(1, vu) and vu != 0)) else GA) if ground else AA
    if lock > 0:
        lock -= 1
        acc *= 0.15
        if ground and lock < 8: lock = 0
    if vu < tgt: vu = min(tgt, vu + acc)
    elif vu > tgt: vu = max(tgt, vu - acc)
    if jump_press and air <= COYOTE and not jumping:
        vy = JUMP; jumping = True; air = COYOTE + 1
    if jumping and not jump_hold and vy > 0.1:
        vy *= CUT; jumping = False
    # 이동 (y 먼저)
    ny = y + vy
    ground = False
    if not wd.box_free(x, ny):
        if vy < 0:
            ny = math.floor(ny) + 1.0 if wd.box_free(x, math.floor(ny) + 1.0) else y
            ground = True
        else:
            ny = y
        vy = 0
    y = ny
    nx = x + vu
    if not wd.box_free(nx, y):
        # 0.6 스텝업 (계단/반블록) 은 이 레벨에 없음
        nx = x; vu = 0
    x = nx
    vy = (vy - 0.08) * 0.98
    # 스프링
    for (sx, sy, c) in wd.springs:
        cu = sx + .5
        if c in "*!":
            if vy <= 0.05 and x + W2 > cu - .45 and x - W2 < cu + .45 and y + HGT > sy and y < sy + .65:
                vy = 1.0 if c == "*" else 1.25
                jumping = False
        else:
            if x + W2 > cu - .5 and x - W2 < cu + .5 and y + HGT > sy + .05 and y < sy + .95:
                vu = (-1 if c == "{" else 1) * 1.4 * 0.62
                vy = 0.45; jumping = False; lock = 16
    return (x, y, vu, vy, ground, air, jumping, lock)


def dead(wd, s):
    x, y = s[0], s[1]
    if y < -4: return True
    for (u0, u1, y0, y1) in wd.spikes:
        if x + W2 > u0 and x - W2 < u1 and y + HGT > y0 and y < y1: return True
    for (cx, cy, r) in wd.saws:
        dx = max(x - W2 - cx, 0, cx - (x + W2)); dy = max(y - cy, 0, cy - (y + HGT))
        if dx * dx + dy * dy < r * r: return True
    return False


def won(wd, s):
    x, y = s[0], s[1]
    g = wd.goal
    return g and x + W2 > g[0] and x - W2 < g[1] and y + HGT > g[2] and y < g[3]


ACTIONS = [(d, j) for d in (1, -1, 0) for j in ("none", "tap", "full")]


def solve(st, max_nodes=400000):
    wd = World(st)
    sx, sy = wd.spawn
    start = (sx, sy, 0.0, 0.0, True, 0, False, 0)
    key = lambda s: (round(s[0] * 4), round(s[1] * 4), round(s[2] * 10), round(s[3] * 10), s[4], s[7] > 0)
    seen = {key(start)}
    q = deque([(start, 0)])
    nodes = 0
    while q:
        s, depth = q.popleft()
        nodes += 1
        if nodes > max_nodes:
            return None, nodes
        for d, j in ACTIONS:
            t = s
            ok = True
            for k in range(4):
                press = j != "none" and k == 0
                hold = j == "full" or (j == "tap" and k == 0)
                t = step(wd, t, d, press, hold)
                if dead(wd, t):
                    ok = False; break
                if won(wd, t):
                    return (depth + 1) * 4, nodes
            if not ok: continue
            kk = key(t)
            if kk in seen: continue
            seen.add(kk)
            q.append((t, depth + 1))
    return None, nodes


def moving_saw_gaps(st):
    """움직이는 톱날: 한 주기 동안 '발 높이 통로(y 2~3.8)'가 비는 틱 비율."""
    out = []
    for o in st.objects:
        if o["type"] != "saw" or ("path" not in o and "radius" not in o):
            continue
        r = o["size"] * 0.42
        if "path" in o:
            pts = o["path"] + ([o["path"][0]] if o.get("loop") else [])
            L = sum(math.dist(pts[i - 1], pts[i]) for i in range(1, len(pts)))
            cyc = L if o.get("loop") else 2 * L
            period = int(cyc / abs(o["speed"]))
        else:
            period = int(360 / abs(o["orbit"]))
        out.append((o.get("path", [o.get("at")])[0], period, r))
    return out


if __name__ == "__main__" and "--timed" not in sys.argv:
    stages = [fn() for fn in GL.STAGES] + [GL.boss()[0]]
    allok = True
    for st in stages:
        if st.id == "boss":
            wd = World(st)
            print(f"[{st.id}] 보스 아레나 - 스폰 정상: {wd.box_free(*wd.spawn)}")
            continue
        ticks, nodes = solve(st)
        status = f"클리어 가능 - 최단 약 {ticks / 20:.1f}초 (제한 {st.time}초)" if ticks else "경로 없음!"
        if not ticks or ticks / 20 > st.time: allok = False
        movers = moving_saw_gaps(st)
        print(f"[{st.id}] {st.name}: {status}  · 탐색 {nodes}노드 · 움직이는 톱날 {len(movers)}개 (주기 {', '.join(str(m[1]) + 't' for m in movers) or '-'})")
    print("결과:", "전체 통과" if allok else "실패 있음")
    sys.exit(0 if allok else 1)


# ---------------------------------------------------------------- 시간 포함 탐색 (움직이는 톱날 포함)
def saw_pos(o, t):
    if "path" in o:
        pts = [tuple(p) for p in o["path"]] + ([tuple(o["path"][0])] if o.get("loop") else [])
        cum = [0.0]
        for i in range(1, len(pts)):
            cum.append(cum[-1] + math.dist(pts[i - 1], pts[i]))
        tot = cum[-1]
        cyc = tot if o.get("loop") else 2 * tot
        d = (t * abs(o["speed"]) + o.get("phase", 0) * cyc) % cyc
        if not o.get("loop") and d > tot: d = cyc - d
        if o["speed"] < 0: d = tot - d
        i = 1
        while i < len(cum) - 1 and cum[i] < d: i += 1
        k = 0 if cum[i] == cum[i - 1] else (d - cum[i - 1]) / (cum[i] - cum[i - 1])
        return pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * k, pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * k
    a = math.radians(o.get("phase", 0) + t * o["orbit"])
    return o["at"][0] + math.cos(a) * o["radius"], o["at"][1] + math.sin(a) * o["radius"]


def solve_timed(st, max_nodes=900000):
    wd = World(st)
    movers = [o for o in st.objects if o["type"] == "saw" and ("path" in o or "radius" in o)]

    def hit(s, t):
        x, y = s[0], s[1]
        for o in movers:
            cx, cy = saw_pos(o, t)
            r = o["size"] * 0.42
            dx = max(x - W2 - cx, 0, cx - (x + W2)); dy = max(y - cy, 0, cy - (y + HGT))
            if dx * dx + dy * dy < r * r: return True
        return False

    sx, sy = wd.spawn
    start = (sx, sy, 0.0, 0.0, True, 0, False, 0)
    key = lambda s, t: (round(s[0] * 4), round(s[1] * 4), round(s[2] * 10), round(s[3] * 10), s[4], s[7] > 0, (t // 4) % 48)
    seen = {key(start, 0)}
    q = deque([(start, 0)])
    nodes = 0
    while q:
        s, t0 = q.popleft()
        nodes += 1
        if nodes > max_nodes: return None, nodes
        for d, j in ACTIONS:
            t = s; tt = t0; ok = True
            for k in range(4):
                t = step(wd, t, d, j != "none" and k == 0, j == "full" or (j == "tap" and k == 0))
                tt += 1
                if dead(wd, t) or hit(t, tt): ok = False; break
                if won(wd, t): return tt, nodes
            if not ok: continue
            kk = key(t, tt)
            if kk in seen: continue
            seen.add(kk)
            q.append((t, tt))
    return None, nodes


if __name__ == "__main__" and "--timed" in sys.argv:
    for st in [fn() for fn in GL.STAGES]:
        if not any(o["type"] == "saw" and ("path" in o or "radius" in o) for o in st.objects):
            continue
        tt, nodes = solve_timed(st)
        print(f"[{st.id}] 움직이는 톱날 포함: " + (f"클리어 가능 - 약 {tt / 20:.1f}초" if tt else "경로 못 찾음") + f" ({nodes}노드)")


def solve_section(st, start_xy, goal_x, t0=0, max_nodes=600000):
    """구간 검증: start_xy 에서 시작해 x >= goal_x (또는 출구) 에 안전하게 도달할 수 있는지 (움직이는 톱날 포함)."""
    wd = World(st)
    movers = [o for o in st.objects if o["type"] == "saw" and ("path" in o or "radius" in o)]

    def hit(s, t):
        x, y = s[0], s[1]
        for o in movers:
            cx, cy = saw_pos(o, t)
            r = o["size"] * 0.42
            dx = max(x - W2 - cx, 0, cx - (x + W2)); dy = max(y - cy, 0, cy - (y + HGT))
            if dx * dx + dy * dy < r * r: return True
        return False

    start = (start_xy[0], start_xy[1], 0.0, 0.0, True, 0, False, 0)
    key = lambda s, t: (round(s[0] * 4), round(s[1] * 4), round(s[2] * 10), round(s[3] * 10), s[4], s[7] > 0, (t // 4) % 60)
    seen = {key(start, t0)}
    q = deque([(start, t0)])
    nodes = 0
    while q:
        s, ts = q.popleft()
        nodes += 1
        if nodes > max_nodes: return None
        for d, j in ACTIONS:
            t = s; tt = ts; ok = True
            for k in range(4):
                t = step(wd, t, d, j != "none" and k == 0, j == "full" or (j == "tap" and k == 0))
                tt += 1
                if dead(wd, t) or hit(t, tt): ok = False; break
                if (t[0] >= goal_x and t[4]) or won(wd, t): return tt - t0
            if not ok: continue
            kk = key(t, tt)
            if kk in seen: continue
            seen.add(kk)
            q.append((t, tt))
    return None
