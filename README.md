# BOOGERAIDS-CombatAI

마인크래프트 **26.3 Paper** 서버용 2.5D 연구소 플랫포머 미니게임 플러그인.
캐릭터는 하나, 피험자는 여럿. 통제권을 가진 플레이어가 죽거나 시간이 다 되면 대기열의 다음 플레이어에게 바로 통제권이 넘어간다.

---

## 빌드

```bash
./gradlew build          # JDK 25 필요 (Gradle 툴체인이 자동으로 받아옴)
# 결과물: build/libs/BOOGERAIDS-CombatAI-1.0.0.jar
```

- 의존성: `io.papermc.paper:paper-api:26.3.+` (compileOnly)
- `resourcepack/` 폴더가 자동으로 zip 으로 묶여 jar 안에 들어간다.
- 리소스팩 에셋(글리프 PNG, 폰트 JSON)을 수정하려면 `python3 tools/gen_pack.py` (Pillow 필요) 후 다시 빌드.

## 설치

1. jar 를 `plugins/` 에 넣고 서버 실행 → `plugins/BOOGERAIDS-CombatAI/config.yml` 생성
2. `settings.resource-pack.public-host` 를 **플레이어가 접속하는 서버 주소**로 변경, 포트(기본 8163) 개방
   - 직접 호스팅하려면 `external-url` / `external-sha1` 사용
3. `settings.world` 를 맵이 있는 월드로 설정
4. 스테이지 좌표 설정 (아래 "맵 만들기") → `/bc reload` → `/bc start`

---

## 게임 구조

| 요소 | 동작 |
|---|---|
| 시점 | 모든 플레이어는 하나의 횡스크롤 카메라를 공유. 관전자는 관전 모드로 카메라 엔티티에 고정, 통제권자는 어드벤처 모드로 카메라 위치의 좌석에 탑승(투명) |
| 카메라 | 지수 이징(ease-out) 추적 + 진행 방향 lookahead + 수직 데드존 + 트라우마 기반 화면 흔들림. 스테이지별 `yaw-offset` 으로 2.5D 시점 |
| 캐릭터 | 통제권자 스킨을 입힌 **Mannequin** 엔티티. 중력/블록 충돌은 바닐라, 수평 가감속·코요테 타임·점프 버퍼·가변 점프는 직접 처리. 점프 최고 2.5칸 / 짧게 톡 1.2~1.6칸 / 수평 ~4칸 |
| 조작 | `A`/`D` 이동, `Space` 점프, 좌클릭/우클릭 특수능력 (처음엔 비어 있음) |
| 루프 | 챕터 = 일반 스테이지 N개 + 보스 스테이지 1개. 일반 스테이지는 제한시간 안에 EXIT 영역 도달, 보스 스테이지는 시간제한 없이 보스 처치 |
| 사망 | 시간 초과 / 낙사(kill-y) / 즉사 블록 / 레이저 / 보스 공격 → 연출 후 대기열 다음 사람이 같은 스테이지 처음부터 |

### HUD (리소스팩 필요)
- **중앙 상단**: 빨간 7세그먼트 디지털 시계 (DSEG7 폰트, 뒤에 꺼진 세그먼트 `88:88.88` 고스트). 10초 이하 깜빡임 + 비프음 + 붉은 비네트 맥동
- **좌측 상단**: 현재 피험자 이름 + 다음 대기자 (Share Tech Mono 연구소 폰트)
- **우측 상단**: 챕터 이름 / 스테이지 이름
- **우측 하단**: 능력 홀더 (좌클릭 슬롯, 우클릭 슬롯). 아이콘 중앙, 입력키(마우스 아이콘) 좌하단, 쿨타임은 아래에서 위로 줄어드는 오버레이 + 중앙 숫자
- 보스 스테이지: 타이머 대신 보스 체력바 (피격 잔상 바 포함), 50% 이하 OVERDRIVE
- 화면 프레임(모서리 마커 + 스캔라인), 사망/획득/클리어 플래시, 타이틀 카드 팝 애니메이션
- 통제권자의 바닐라 HUD(핫바, 하트, 허기, 경험치, 크로스헤어, "Shift로 내리기")는 리소스팩으로 숨김

HUD는 카메라 엔티티에 승객으로 붙은 TextDisplay/ItemDisplay 로 그려진다. 화면 위치 계산은 **FOV 70(보통), 16:9** 기준이므로 FOV 를 크게 바꾸면 모서리 요소가 화면 밖/안쪽으로 밀릴 수 있다 (`settings.hud` 에서 조정).

### 특수능력

| 능력 | 효과 | 쿨타임 |
|---|---|---|
| `DASH` | 진행 방향 순간 가속 (중력 무시 7틱). 기절한 보스에 들이받으면 피해 | 1.8s |
| `BOOST` | 공중 추가 점프 (착지 전 1회) | 1.5s |
| `BLINK` | 최대 5블록 앞 순간이동, **얇은 벽 통과 가능** | 3.5s |
| `SHOCKWAVE` | 반경 3 내 `breakable-blocks` 파괴, 근처 보스 피해 5 | 4.5s |
| `PLASMA` | 전방 투사체. 부술 수 있는 블록 파괴, 보스 피해 2.5 | 0.7s |

### 보스 "SENTINEL"
- 패턴: **LASER** (낮은 레이저 → 점프로 회피 / 높은 레이저 → 점프하면 사망), **DEBRIS** (경고 표시 후 낙하물), **SLAM** (플레이어 위로 이동 → 내려찍기 → 바닥 충격파, 점프로 회피 → 기절, 받는 피해 1.5배)
- 피해: 위에서 **밟기**(3), PLASMA, SHOCKWAVE, 기절 중 DASH 충돌
- 체력 50% 이하에서 OVERDRIVE (패턴 간격 단축, 낙하물 증가)
- `boss-reset-on-death: false` 면 교대 후에도 보스 체력 유지

---

## 챕터 1 (내장 레벨)

빈 월드(보이드/평지 권장)에서 원하는 지점에 서서:
```
/bc build chapter1      # 스테이지 6개 + 보스 아레나 건설, config.yml 의 chapters.c1 자동 등록
/bc start c1
```
스테이지는 서 있는 블록을 1-1 의 왼쪽 아래로 해서 -Z 방향으로 52블록 간격으로 지어진다 (주변과 카메라 공간은 비워짐).

| 스테이지 | 내용 | 제한 |
|---|---|---|
| 1-1 보행 반응 테스트 | 튜토리얼: 이동 → 1·2칸 턱 → 구덩이 → 길게 점프 → 가시 → 스프링 | 75s |
| 1-2 가시 회랑 | 가시 웅덩이, 가시 계단, **천장 가시 터널(짧게 톡 점프)**, 가시 바닥 위 금속 발판 | 60s |
| 1-3 스프링 샤프트 | 세로 스테이지. 스프링/강한 스프링 연쇄, 옆 스프링, 벽 가시 | 70s |
| 1-4 회전 톱날 | 바닥에 묻힌 톱날, 오르내리는 톱날 3연속, 구덩이 위 톱날(높이 뛰면 위험), 굴러오는 톱날 | 60s |
| 1-5 레일 회로 | 발판을 공전하는 톱날, 머리 위 왕복 레일 톱날, 사각 순환 레일, 마주 도는 쌍톱날 | 70s |
| 1-6 통증 내성 시험 | 종합: 천장 가시+바닥 톱날 복도, 스프링 기둥 건너기, 위상차 톱날 파도 | 80s |
| BOSS SPECIMEN-00 | 아레나 + PLASMA/BOOST 캡슐, 보스 체력 40 | - |

- 레벨 원본: `tools/gen_levels.py` → `src/main/resources/levels/chapter1.yml` (ASCII 타일맵)
- 미리보기: `python3 tools/gen_levels.py --preview` → `build/level-preview/*.png`
- 클리어 가능성 검증 봇: `python3 tools/test/playtest.py` (플러그인과 같은 물리로 BFS 탐색, `--timed` 는 움직이는 톱날 타이밍 포함)
- 톱날 스프라이트는 Path of Pain 의 분위기(창백한 갈고리 톱니, 어두운 중심, 레일, 빠른 회전)를 참고한 **오리지널 디자인** (`tools/gen_sprites.py`)

### 기믹 오브젝트
| 타입 | 설명 |
|---|---|
| `saw` | 회전 톱날. 고정(`pos`) / 경로(`path` + `speed`, `loop`, 음수 speed = 역방향) / 공전(`pos` + `radius` + `orbit`). `size`, `phase`, `track`(레일 표시) |
| `spikes` | 삼각 가시. `pos`(첫 칸), `length`, `dir`(UP/DOWN/LEFT/RIGHT) |
| `spring` | 스프링. `power`(1.0 ≈ 6칸, 1.25 ≈ 9칸), `dir`(UP / LEFT / RIGHT = 옆 스프링) |
| `hint` | 영역에 처음 들어가면 화면 하단 안내 문구 |

## 맵 만들기

### 원칙
- 캐릭터는 **스폰 좌표를 지나는 평면** 위에서만 움직인다. `facing: 180` (기본) 이면 카메라가 북쪽(-Z)을 바라보고 **+X 가 화면 오른쪽**.
- 맵은 그 평면(스폰 z 블록 한 줄)을 기준으로 블록으로 짓는다. 앞뒤로 장식 블록을 두면 2.5D 느낌이 난다.
- **카메라 쪽(스폰 기준 남쪽 약 13블록)은 비워둘 것.** 카메라와 HUD가 그 공간에 있다.
- 레드스톤, 압력판, 슬라임 블록 등 바닐라 기믹도 캐릭터(Mannequin)에 반응한다.

### 인게임 편집 명령어 (권한 `boogeraids.admin`)

```
/bc chapter add c2 CHAPTER 02 // 생체 실험동
/bc stage add c2 s1 45 STAGE 2-1  산성 수조
/bc set c2 s1 spawn            # 지금 서 있는 위치 = 스폰 (이동 평면 기준)
/bc set c2 s1 facing           # 지금 바라보는 방향 = 카메라 방향 (90° 단위)
/bc pos1 / /bc pos2            # 바라보는 블록으로 영역 선택
/bc set c2 s1 goal             # 선택 영역 = EXIT
/bc set c2 s1 killy 60
/bc set c2 s1 camera yaw-offset 12   # 2.5D 시점

/bc obj c2 s1 add ability BLINK RIGHT  # 현재 위치에 능력 캡슐
/bc obj c2 s1 add jumppad 1.1
/bc obj c2 s1 add sign 벽 너머로 블링크!
/bc obj c2 s1 add switch               # 현재 위치 = 스위치, 바라보는 블록 = 레드스톤 블록 설치 위치
/bc obj c2 s1 add laser 60 30 0        # 선택 영역 = 주기형 레이저 (주기, 켜짐, 오프셋 틱)
/bc obj c2 s1 list | remove <번호>

/bc stage boss c2 VAT-07 // 배양조 감시체
/bc set c2 boss spawn
/bc set c2 boss arena          # 선택 영역 = 보스 아레나 (아래 y = 바닥)
/bc set c2 boss bosspos        # 보스 대기 위치
/bc set c2 boss health 80
/bc set c2 boss item           # 손에 든 아이템 = 보스 외형

/bc list                       # 설정 검증 (✔/✖)
/bc tp c2 s1
```

### 진행 명령어
```
/bc start [챕터] [스테이지]   # 접속자 전원 참가 (원래 상태/인벤토리는 종료 시 복구)
/bc stop | skip | kill | status | reload
/bc join | leave | pack       # 일반 플레이어
/너해봐 <플레이어>              # (별칭 /pass) 통제권을 가진 플레이어가 다른 참가자에게 조작을 넘김
```
`/너해봐` 는 플레이 중(타이틀 카드 포함)에만 쓸 수 있고, 캐릭터의 위치·속도·능력·남은 시간은 그대로 유지된 채 조작자와 스킨만 바뀐다.
받는 사람은 대기열 맨 앞, 넘긴 사람은 맨 뒤로 이동하며, 연속 사용은 2초 간격으로 제한된다. 사용법은 좌측 상단 피험자 UI 바로 아래에 항상 표시된다.

---

## 프로젝트 구조

```
src/main/java/com/boogeraids/combatai/
├─ BoogerAIDS.java              메인 클래스
├─ game/GameManager.java        상태 머신 (INTRO → PLAYING → DYING/CLEARING → …), 대기열, 교대
├─ game/Character.java          Mannequin 캐릭터 + 플랫포머 물리
├─ camera/CameraRig.java        카메라/좌석/클릭 수신 Interaction, 이징·흔들림
├─ hud/Hud.java                 카메라 승객 디스플레이로 그리는 화면 고정 HUD
├─ ability/                     능력 정의, 쿨타임, 발동/투사체
├─ stage/StageRuntime.java      스테이지 오브젝트, 즉사 블록, 골 판정, 블록 복구
├─ boss/BossRuntime.java        보스 패턴
├─ listener/GameListener.java   입력/클릭/잠금 이벤트
├─ command/BcCommand.java       /bc 명령어
└─ pack/ResourcePackService.java 내장 HTTP 리소스팩 배포
resourcepack/                   폰트(DSEG7, Share Tech Mono - OFL), UI 글리프, 바닐라 HUD 숨김
tools/gen_pack.py               리소스팩 에셋 생성기
```

## 알려진 제약
- 통제권자의 시점은 서버가 주기적으로 되돌려 고정한다. 클라이언트에서 마우스를 크게 움직이면 순간적으로 시점이 흔들릴 수 있다.
- HUD 위치는 FOV 70 / 16:9 가정. 다른 비율에서는 모서리 요소가 약간 안쪽에 그려진다.
- 리소스팩 `pack.mcmeta` 는 `min_format: 84 ~ max_format: 999` 로 26.x 전체를 대상으로 한다.

## 라이선스 (리소스팩 폰트)
- DSEG7 Classic © keshikan — SIL Open Font License 1.1 (`resourcepack/licenses/DSEG-OFL.txt`)
- Share Tech Mono © Carrois Type Design — SIL Open Font License 1.1 (`resourcepack/licenses/ShareTechMono-OFL.txt`)
