package com.boogeraids.combatai.config;

import org.bukkit.configuration.ConfigurationSection;

public final class HudSettings {
    public double distance = 1.0;
    public double fov = 70.0;
    public double aspect = 16.0 / 9.0;

    public static HudSettings load(ConfigurationSection s) {
        HudSettings h = new HudSettings();
        if (s == null) return h;
        h.distance = Math.max(0.3, s.getDouble("distance", h.distance));
        h.fov = s.getDouble("fov", h.fov);
        h.aspect = s.getDouble("aspect", h.aspect);
        return h;
    }

    /** 거리 d 에서 화면 높이의 절반 (블록). */
    public double halfHeight(double d) {
        return d * Math.tan(Math.toRadians(fov / 2.0));
    }

    public double halfWidth(double d) {
        return halfHeight(d) * aspect;
    }
}
