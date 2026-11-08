package com.boogeraids.combatai.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/** 리소스팩 폰트 키와 대략적인 글자 폭 계산. (리소스팩 폰트 정의: tools/gen_pack.py) */
public final class Fonts {
    private Fonts() {}

    public static final Key DIGITAL = Key.key("boogeraids", "digital");
    public static final Key LAB = Key.key("boogeraids", "lab");
    public static final Key UI = Key.key("boogeraids", "ui");

    // UI 글리프 (boogeraids:ui)
    public static final String HOLDER = "";
    public static final String COOLDOWN = "";
    public static final String WHITE = "";
    public static final String VIGNETTE = "";
    public static final String FRAME = "";
    public static final String BOSS_FRAME = "";
    public static final String MOUSE_L = "";
    public static final String MOUSE_R = "";
    public static final String WARN = "";

    /** 텍스트 디스플레이 1픽셀 = 0.025 블록 (scale 1 기준). */
    public static final double PX = 0.025;

    // 폰트 메트릭 (px). Share Tech Mono: 0.54em * 9px, DSEG7: 숫자 0.816em * 12px, 콜론 0.2em, 마침표 0
    private static final double LAB_ADV = 0.54 * 9.0;
    private static final double DIGIT_ADV = 0.816 * 12.0;
    private static final double DIGIT_COLON = 0.2 * 12.0;
    private static final double DEFAULT_WIDE = 9.0;   // 한글 등 기본 폰트 폴백 글자
    private static final double DEFAULT_ASCII = 6.0;

    public static Component lab(String s, int rgb) {
        return Component.text(s, TextColor.color(rgb)).font(LAB);
    }

    public static Component digital(String s, int rgb) {
        return Component.text(s, TextColor.color(rgb)).font(DIGITAL);
    }

    public static Component ui(String glyph, int rgb) {
        return Component.text(glyph, TextColor.color(rgb)).font(UI);
    }

    /** lab 폰트 문자열의 대략적인 폭 (px). 한글은 기본 폰트 폴백 폭 사용. */
    public static double labWidth(String s) {
        double w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c < 0x7F) w += LAB_ADV;
            else if (Character.isHighSurrogate(c)) { w += DEFAULT_WIDE; i++; }
            else w += c < 0x2000 ? DEFAULT_ASCII : DEFAULT_WIDE;
        }
        return w;
    }

    public static double digitalWidth(String s) {
        double w = 0;
        for (char c : s.toCharArray()) {
            if (c == ':') w += DIGIT_COLON;
            else if (c == '.') w += 0;
            else w += DIGIT_ADV;
        }
        return w;
    }
}
