package com.boogeraids.combatai.util;

/** 이징 함수 모음. */
public final class Ease {
    private Ease() {}

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** 틱 단위 지수 감쇠 이징 (ease-out). speed 0~1. */
    public static double approach(double current, double target, double speed) {
        return current + (target - current) * speed;
    }

    public static double outCubic(double t) {
        t = clamp(t, 0, 1);
        double u = 1 - t;
        return 1 - u * u * u;
    }

    public static double inOutSine(double t) {
        t = clamp(t, 0, 1);
        return -(Math.cos(Math.PI * t) - 1) / 2;
    }

    public static double outBack(double t) {
        t = clamp(t, 0, 1);
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }

    public static double inQuad(double t) {
        t = clamp(t, 0, 1);
        return t * t;
    }
}
