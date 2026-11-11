package com.boogeraids.combatai.hud;

import com.boogeraids.combatai.ability.Ability;
import com.boogeraids.combatai.ability.AbilitySlotState;
import com.boogeraids.combatai.config.HudSettings;
import com.boogeraids.combatai.util.Ease;
import com.boogeraids.combatai.util.Fonts;
import com.boogeraids.combatai.util.Tags;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 카메라 엔티티에 승객으로 붙는 화면 고정 HUD.
 * 모든 요소는 billboard CENTER 디스플레이이므로 translation 이 카메라 좌표계(+X 오른쪽, +Y 위, -Z 앞)로 해석된다.
 * 화면 좌표 (sx, sy) ∈ [-1, 1] → translation = (sx·halfW, sy·halfH, -d).
 *
 * <pre>
 *  ┌ SUBJECT ───────────  88:88.88  ─────────── CHAPTER ┐
 *  │ name                                        STAGE  │
 *  │                                                    │
 *  │                                         [L]  [R]   │
 *  └────────────────────────────────────────────────────┘
 * </pre>
 */
public final class Hud {

    private static final double PX = Fonts.PX;
    private static final int CYAN = 0x00E5FF;
    private static final int RED = 0xFF2A2A;

    private final HudSettings hs;
    private final ItemDisplay cam;
    private final List<Entity> all = new ArrayList<>();

    private final TextDisplay frameOverlay, vignette;
    private final TextDisplay timerLabel, timerGhost, timerText;
    private final TextDisplay subject, stage, usage;
    private final TextDisplay title, subtitle, hint;
    private int hintTicks;
    private final TextDisplay bossName, bossFrame, bossTrail, bossFill;
    private final Holder[] holders = new Holder[2];

    // 상태
    private int tick;
    private int vignetteRgb = RED;
    private double vignetteAlpha, vignetteDecay;
    private double alarm;          // 제한시간 임박 시 맥동 비네트 세기
    private int titleTicks, titleTotal, titleFade;
    private boolean bossVisible, timerVisible = true;
    private double bossTrailFrac = 1, bossFrac = 1;
    private int bossColor = 0xFF2A4A;

    private static final class Holder {
        TextDisplay frame, cooldown, cdText, key, name;
        ItemDisplay icon;
        Ability shown;
        boolean visible;
    }

    public Hud(ItemDisplay cam, HudSettings hs) {
        this.cam = cam;
        this.hs = hs;
        Location at = cam.getLocation();

        frameOverlay = text(at);
        vignette = text(at);
        timerLabel = text(at);
        timerGhost = text(at);
        timerText = text(at);
        subject = text(at);
        stage = text(at);
        usage = text(at);
        title = text(at);
        subtitle = text(at);
        hint = text(at);
        bossName = text(at);
        bossFrame = text(at);
        bossTrail = text(at);
        bossFill = text(at);
        for (int i = 0; i < 2; i++) {
            Holder h = new Holder();
            h.frame = text(at);
            h.icon = Tags.mark(at.getWorld().spawn(at, ItemDisplay.class, this::setupDisplay));
            h.icon.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            all.add(h.icon);
            h.cooldown = text(at);
            h.cdText = text(at);
            h.key = text(at);
            h.name = text(at);
            holders[i] = h;
        }
        for (Entity e : all) cam.addPassenger(e);

        layoutStatic();
        setHolder(0, null);
        setHolder(1, null);
        hideTitle();
        setBoss(false, null, 1);
    }

    // ------------------------------------------------------------------ spawn helpers

    private void setupDisplay(Display d) {
        d.setBillboard(Display.Billboard.CENTER);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(2f);
        d.setDisplayWidth(0f);
        d.setDisplayHeight(0f);
        d.setShadowRadius(0f);
        d.setInterpolationDuration(0);
        d.setTeleportDuration(0);
    }

    private TextDisplay text(Location at) {
        TextDisplay t = Tags.mark(at.getWorld().spawn(at, TextDisplay.class, d -> {
            setupDisplay(d);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setDefaultBackground(false);
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setLineWidth(2000);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.text(Component.empty());
        }));
        all.add(t);
        return t;
    }

    public void destroy() {
        for (Entity e : all) {
            if (e.isValid()) e.remove();
        }
        all.clear();
    }

    // ------------------------------------------------------------------ geometry

    private double d(double depthMul) {
        return hs.distance * depthMul;
    }

    /** 한 줄이 화면 높이의 lineFrac 만큼 차지하도록 하는 텍스트 스케일. */
    private double lineScale(double lineFrac, double depth) {
        return lineFrac * 2 * hs.halfHeight(depth) / (10 * PX);
    }

    private static Transformation tf(double x, double y, double z, double sx, double sy) {
        return new Transformation(new Vector3f((float) x, (float) y, (float) z), new Quaternionf(),
                new Vector3f((float) sx, (float) sy, (float) Math.max(sx, sy)), new Quaternionf());
    }

    /**
     * 텍스트 블록 배치.
     * @param hAlign -1 왼쪽 기준, 0 가운데, 1 오른쪽 기준
     * @param vAlign 1 위 기준, 0 가운데, -1 아래 기준
     */
    private void anchorText(TextDisplay e, double sx, double sy, int hAlign, int vAlign,
                            double widthPx, int lines, double lineFrac, double depthMul) {
        double depth = d(depthMul);
        double s = lineScale(lineFrac, depth);
        double w = widthPx * PX * s;
        double h = lines * 10 * PX * s;
        double ax = sx * hs.halfWidth(depth);
        double ay = sy * hs.halfHeight(depth);
        double cx = ax + (hAlign < 0 ? w / 2 : hAlign > 0 ? -w / 2 : 0);
        double oy = vAlign > 0 ? ay - h : vAlign == 0 ? ay - h / 2 : ay;
        e.setTransformation(tf(cx, oy, -depth, s, s));
    }

    /** 비트맵 글리프 하나를 (cx, cy) 블록 위치 중심에 배치. 글리프 중심은 원점에서 약 4.5px 위. */
    private void placeGlyph(Display e, double cx, double cy, double depth, double sx, double sy) {
        e.setTransformation(tf(cx, cy - 4.5 * PX * sy, -depth, sx, sy));
    }

    // ------------------------------------------------------------------ static layout

    private void layoutStatic() {
        // 화면 전체 프레임 (모서리 마커 + 스캔라인). 화면을 살짝 넘치게 덮는다.
        double dOv = d(1.12);
        double sCover = 2 * hs.halfHeight(dOv) / (144 * PX) * 1.02;
        double sCoverX = sCover * Math.max(1.0, hs.aspect / (256.0 / 144.0));
        frameOverlay.text(Fonts.ui(Fonts.FRAME, 0xFFFFFF));
        placeGlyph(frameOverlay, 0, 0, dOv, sCoverX, sCover);

        double dV = d(1.10);
        double sV = 2 * hs.halfHeight(dV) / (144 * PX) * 1.04;
        placeGlyph(vignette, 0, 0, dV, sV * Math.max(1.0, hs.aspect / (256.0 / 144.0)), sV);

        // 타이머
        timerLabel.text(Fonts.lab("◢ TIME LIMIT ◣", 0xFF5A5A));
        anchorText(timerLabel, 0, 0.97, 0, 1, Fonts.labWidth("◢ TIME LIMIT ◣"), 1, 0.024, 1.0);
        timerGhost.text(Fonts.digital("88:88.88", 0x3A0808));
        timerGhost.setBackgroundColor(Color.fromARGB(150, 12, 2, 4));
        anchorText(timerGhost, 0, 0.885, 0, 1, Fonts.digitalWidth("88:88.88"), 1, 0.05, 1.004);
        anchorText(timerText, 0, 0.885, 0, 1, Fonts.digitalWidth("88:88.88"), 1, 0.05, 1.0);

        // 스킬 홀더 위치 (우측 하단, 왼쪽=L 슬롯, 오른쪽=R 슬롯)
        for (int i = 0; i < 2; i++) layoutHolder(i, holders[i], 1.0);
    }

    private double holderSize(double depth) {
        return 0.17 * 2 * hs.halfHeight(depth);
    }

    private void layoutHolder(int slot, Holder h, double pop) {
        double depth = d(1.0);
        double size = holderSize(depth);
        double hw = hs.halfWidth(depth), hh = hs.halfHeight(depth);
        double gap = size * 0.22;
        double margin = 0.045 * hw;
        double cxRight = hw - margin - size / 2;
        double cx = slot == 1 ? cxRight : cxRight - size - gap;
        double cy = -hh + margin * 1.4 + size / 2;

        double sFrame = size / (32 * PX) * pop;
        placeGlyph(h.frame, cx, cy, depth, sFrame, sFrame);

        double iconScale = size * 0.58 * pop;
        h.icon.setTransformation(tf(cx, cy, -depth * 0.995, iconScale, iconScale));

        // 쿨타임 텍스트 (중앙)
        double sCd = lineScale(0.05, depth);
        h.cdText.setTransformation(tf(cx, cy - 5 * PX * sCd, -depth * 0.985, sCd, sCd));

        // 입력키 (좌하단 모서리 마우스 아이콘)
        double sKey = size * 0.36 / (12 * PX);
        placeGlyph(h.key, cx - size / 2 + size * 0.10, cy - size / 2 + size * 0.16, depth * 0.98, sKey, sKey);

        // 능력 이름 (홀더 위)
        double sName = lineScale(0.026, depth);
        h.name.setTransformation(tf(cx, cy + size / 2 + size * 0.06, -depth, sName, sName));
    }

    private void layoutCooldown(int slot, Holder h, double frac) {
        double depth = d(1.0);
        double size = holderSize(depth);
        double hw = hs.halfWidth(depth), hh = hs.halfHeight(depth);
        double gap = size * 0.22;
        double margin = 0.045 * hw;
        double cxRight = hw - margin - size / 2;
        double cx = slot == 1 ? cxRight : cxRight - size - gap;
        double cy = -hh + margin * 1.4 + size / 2;
        double s = size / (32 * PX);
        double k = Math.max(0.0001, s * frac);
        double bottom = cy - size / 2;
        // 글리프는 원점 위 4.5px 를 중심으로 ±16px → 아래쪽 끝을 홀더 바닥에 고정
        double ty = bottom + (16 - 4.5) * PX * k;
        h.cooldown.setTransformation(tf(cx, ty, -depth * 0.99, s, k));
    }

    // ------------------------------------------------------------------ public API

    public void setTimerVisible(boolean v) {
        timerVisible = v;
        if (!v) {
            timerText.text(Component.empty());
            timerGhost.text(Component.empty());
            timerGhost.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            timerLabel.text(Component.empty());
        } else {
            timerGhost.text(Fonts.digital("88:88.88", 0x3A0808));
            timerGhost.setBackgroundColor(Color.fromARGB(150, 12, 2, 4));
            timerLabel.text(Fonts.lab("◢ TIME LIMIT ◣", 0xFF5A5A));
        }
    }

    /** @param ticksLeft 남은 틱. 음수면 "--:--.--" */
    public void setTimer(int ticksLeft, boolean frozen) {
        if (!timerVisible) return;
        String s;
        if (ticksLeft < 0) {
            s = "--:--.--";
        } else {
            int centis = ticksLeft * 5;
            int secs = centis / 100;
            s = String.format("%02d:%02d.%02d", secs / 60, secs % 60, centis % 100);
        }
        int color = RED;
        if (ticksLeft >= 0 && ticksLeft <= 200 && !frozen) {
            // 10초 이하: 깜빡임
            color = ((tick / 4) % 2 == 0) ? 0xFF1010 : 0xFF8A8A;
        }
        Component c = Component.text(s, TextColor.color(color)).font(Fonts.DIGITAL);
        timerText.text(c);
        alarm = (ticksLeft >= 0 && ticksLeft <= 100 && !frozen) ? 1.0 - ticksLeft / 100.0 : 0;
    }

    public void setSubject(String name, String next) {
        List<String> raw = new ArrayList<>();
        raw.add("▣ TEST SUBJECT");
        raw.add(name == null ? "-" : name);
        if (next != null) raw.add("NEXT ▸ " + next);
        Component c = Component.empty()
                .append(Fonts.lab(raw.get(0), CYAN)).append(Component.newline())
                .append(Fonts.lab(raw.get(1), 0xFFFFFF).decorate(TextDecoration.BOLD));
        if (next != null) c = c.append(Component.newline()).append(Fonts.lab(raw.get(2), 0x7F95A3));
        subject.text(c);
        subject.setAlignment(TextDisplay.TextAlignment.LEFT);
        subject.setBackgroundColor(Color.fromARGB(140, 6, 10, 16));
        double w = 0;
        for (String r : raw) w = Math.max(w, Fonts.labWidth(r) * (r == raw.get(1) ? 1.1 : 1.0));
        anchorText(subject, -0.965, 0.95, -1, 1, w + 4, raw.size(), 0.034, 1.0);

        // 좌측 상단 UI 바로 아래: 통제권 넘기기 사용법
        String cmd = "/너해봐 <플레이어>";
        String desc = "  통제권 넘기기";
        usage.text(Component.empty()
                .append(Fonts.lab("⌨ ", 0x00E5FF))
                .append(Fonts.lab(cmd, 0xFFFFFF))
                .append(Fonts.lab(desc, 0x7F95A3)));
        usage.setAlignment(TextDisplay.TextAlignment.LEFT);
        usage.setBackgroundColor(Color.fromARGB(110, 6, 10, 16));
        double top = 0.95 - raw.size() * 0.034 * 2 - 0.018;   // 한 줄 = 화면 높이의 lineFrac = sy 로 2배
        anchorText(usage, -0.965, top, -1, 1, Fonts.labWidth("⌨ " + cmd + desc) + 4, 1, 0.026, 1.0);
    }

    public void setStage(String chapter, String stageName) {
        Component c = Component.empty()
                .append(Fonts.lab(chapter, CYAN)).append(Component.newline())
                .append(Fonts.lab(stageName, 0xFFFFFF).decorate(TextDecoration.BOLD));
        stage.text(c);
        stage.setAlignment(TextDisplay.TextAlignment.RIGHT);
        stage.setBackgroundColor(Color.fromARGB(140, 6, 10, 16));
        double w = Math.max(Fonts.labWidth(chapter), Fonts.labWidth(stageName) * 1.1);
        anchorText(stage, 0.965, 0.95, 1, 1, w + 4, 2, 0.034, 1.0);
    }

    /** 능력 슬롯 표시. ability 가 null 이면 숨김. */
    public void setHolder(int slot, Ability ability) {
        Holder h = holders[slot];
        h.shown = ability;
        h.visible = ability != null;
        if (ability == null) {
            h.frame.text(Component.empty());
            h.icon.setItemStack(new ItemStack(Material.AIR));
            h.cooldown.text(Component.empty());
            h.cdText.text(Component.empty());
            h.key.text(Component.empty());
            h.name.text(Component.empty());
            return;
        }
        h.frame.text(Fonts.ui(Fonts.HOLDER, 0xFFFFFF));
        h.icon.setItemStack(new ItemStack(ability.icon));
        h.key.text(Fonts.ui(slot == 0 ? Fonts.MOUSE_L : Fonts.MOUSE_R, 0xFFFFFF));
        h.name.text(Fonts.lab(ability.code, ability.rgb));
        h.cooldown.text(Fonts.ui(Fonts.COOLDOWN, 0xFFFFFF));
        layoutCooldown(slot, h, 0);
    }

    /** 능력 획득/사용 시 홀더 팝 애니메이션. */
    public void pulseHolder(int slot) {
        Holder h = holders[slot];
        if (!h.visible) return;
        h.frame.setInterpolationDelay(0);
        h.frame.setInterpolationDuration(0);
        h.icon.setInterpolationDuration(0);
        layoutHolder(slot, h, 1.18);
        h.frame.setInterpolationDuration(5);
        h.icon.setInterpolationDuration(5);
        pendingRelayout[slot] = 2;
    }

    private final int[] pendingRelayout = new int[2];

    public void updateSlots(AbilitySlotState[] slots) {
        for (int i = 0; i < 2; i++) {
            Holder h = holders[i];
            AbilitySlotState st = slots[i];
            if (st.ability != h.shown) setHolder(i, st.ability);
            if (!h.visible) continue;
            double frac = st.cooldownFraction();
            layoutCooldown(i, h, frac);
            if (st.cooldown > 0) {
                h.cdText.text(Fonts.lab(String.format("%.1f", st.cooldown / 20.0), 0xFFFFFF).decorate(TextDecoration.BOLD));
                h.frame.text(Fonts.ui(Fonts.HOLDER, 0x8A9AA6));
            } else {
                h.cdText.text(Component.empty());
                int c = st.flash > 0 && (tick / 2) % 2 == 0 ? st.ability.rgb : 0xFFFFFF;
                h.frame.text(Fonts.ui(Fonts.HOLDER, c));
            }
        }
    }

    public void showTitle(Component main, Component sub, int stayTicks, int fadeTicks) {
        titleTotal = stayTicks + fadeTicks;
        titleTicks = titleTotal;
        titleFade = Math.max(1, fadeTicks);
        title.text(main);
        subtitle.text(sub == null ? Component.empty() : sub);
        title.setTextOpacity((byte) -1);
        subtitle.setTextOpacity((byte) -1);
        title.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        // 팝 인: 크게 시작 → 보간으로 원래 크기
        title.setInterpolationDuration(0);
        subtitle.setInterpolationDuration(0);
        anchorText(title, 0, 0.22, 0, 0, 0, 1, 0.07 * 1.3, 0.99);
        anchorText(subtitle, 0, 0.10, 0, 0, 0, 1, 0.034, 0.99);
        titlePopPending = 2;
    }

    private int titlePopPending;

    /** 화면 하단 중앙 안내 문구 (튜토리얼 등). */
    public void showHint(Component c, int ticks) {
        hintTicks = ticks;
        hint.text(Component.empty().append(Fonts.lab("▶ ", 0x00E5FF)).append(c));
        hint.setTextOpacity((byte) -1);
        hint.setBackgroundColor(Color.fromARGB(170, 6, 10, 16));
        hint.setInterpolationDuration(0);
        anchorText(hint, 0, -0.66, 0, 0, 0, 1, 0.04, 0.99);
    }

    public void hideTitle() {
        titleTicks = 0;
        title.text(Component.empty());
        subtitle.text(Component.empty());
    }

    /** 화면 가장자리 플래시. */
    public void flash(int rgb, double alpha, int fadeTicks) {
        vignetteRgb = rgb;
        vignetteAlpha = Math.max(vignetteAlpha, Ease.clamp(alpha, 0, 1));
        vignetteDecay = vignetteAlpha / Math.max(1, fadeTicks);
    }

    public void setBoss(boolean visible, String name, double frac) {
        bossVisible = visible;
        if (!visible) {
            bossName.text(Component.empty());
            bossFrame.text(Component.empty());
            bossFill.text(Component.empty());
            bossTrail.text(Component.empty());
            return;
        }
        bossName.text(Fonts.lab("◆ " + name + " ◆", 0xFF5A6E));
        anchorText(bossName, 0, 0.975, 0, 1, Fonts.labWidth("◆ " + name + " ◆"), 1, 0.028, 1.0);
        bossFrame.text(Fonts.ui(Fonts.BOSS_FRAME, 0xFFFFFF));
        bossFill.text(Fonts.ui(Fonts.WHITE, bossColor));
        bossTrail.text(Fonts.ui(Fonts.WHITE, 0xFFFFFF));
        bossFrac = bossTrailFrac = Ease.clamp(frac, 0, 1);
        layoutBoss();
    }

    public void setBossHealth(double frac, boolean enraged) {
        bossFrac = Ease.clamp(frac, 0, 1);
        int c = enraged ? 0xD02CFF : 0xFF2A4A;
        if (c != bossColor) {
            bossColor = c;
            if (bossVisible) bossFill.text(Fonts.ui(Fonts.WHITE, bossColor));
        }
    }

    private void layoutBoss() {
        double depth = d(1.0);
        double hw = hs.halfWidth(depth), hh = hs.halfHeight(depth);
        double width = 0.62 * hw;
        double cy = 0.85 * hh;
        double s = width / (128 * PX);
        placeGlyph(bossFrame, 0, cy, depth * 1.002, s, s);
        double innerW = width * (1 - 6.0 / 128);
        double innerH = 8 * PX * s * 0.55;
        double left = -innerW / 2;
        double sy = innerH / (8 * PX);
        double fw = Math.max(0.0001, innerW * bossFrac);
        placeGlyph(bossFill, left + fw / 2, cy, depth * 0.998, fw / (8 * PX), sy);
        double tw = Math.max(0.0001, innerW * bossTrailFrac);
        placeGlyph(bossTrail, left + tw / 2, cy, depth, tw / (8 * PX), sy);
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        tick++;
        if (!cam.isValid()) return;

        // 비네트: 플래시 + 시간 임박 경보 맥동
        vignetteAlpha = Math.max(0, vignetteAlpha - vignetteDecay);
        double pulse = alarm > 0 ? alarm * (0.35 + 0.25 * Math.sin(tick * 0.45)) : 0;
        double a = Math.max(vignetteAlpha, pulse);
        int rgb = vignetteAlpha >= pulse ? vignetteRgb : RED;
        if (a < 0.04) {
            vignette.text(Component.empty());
        } else {
            vignette.text(Fonts.ui(Fonts.VIGNETTE, rgb));
            vignette.setTextOpacity((byte) (int) Math.round(Ease.clamp(a, 0.12, 1.0) * 255));
        }

        // 타이틀 페이드
        if (titlePopPending > 0 && --titlePopPending == 0) {
            title.setInterpolationDelay(0);
            title.setInterpolationDuration(7);
            anchorText(title, 0, 0.22, 0, 0, 0, 1, 0.07, 0.99);
        }
        if (titleTicks > 0) {
            titleTicks--;
            if (titleTicks < titleFade) {
                double f = titleTicks / (double) titleFade;
                if (f < 0.12) {
                    title.text(Component.empty());
                    subtitle.text(Component.empty());
                } else {
                    byte op = (byte) (int) Math.round(f * 255);
                    title.setTextOpacity(op);
                    subtitle.setTextOpacity(op);
                }
            }
        }

        // 안내 문구 페이드
        if (hintTicks > 0) {
            hintTicks--;
            if (hintTicks < 12) {
                if (hintTicks < 2) {
                    hint.text(Component.empty());
                    hint.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                } else {
                    hint.setTextOpacity((byte) (int) Math.round(hintTicks / 12.0 * 255));
                    hint.setBackgroundColor(Color.fromARGB((int) (170 * hintTicks / 12.0), 6, 10, 16));
                }
            }
        }

        // 홀더 팝 복귀
        for (int i = 0; i < 2; i++) {
            if (pendingRelayout[i] > 0 && --pendingRelayout[i] == 0) layoutHolder(i, holders[i], 1.0);
        }

        // 보스 체력 잔상
        if (bossVisible) {
            if (bossTrailFrac > bossFrac) bossTrailFrac = Math.max(bossFrac, bossTrailFrac - 0.008);
            else bossTrailFrac = bossFrac;
            layoutBoss();
        }
    }
}
