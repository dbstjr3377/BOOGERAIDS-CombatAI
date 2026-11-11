package com.boogeraids.combatai.config;

import org.bukkit.configuration.ConfigurationSection;

/** 카메라 설정. 스테이지별로 일부 값만 덮어쓸 수 있다. */
public final class CameraSettings {
    public double distance = 13.0;
    public double height = 1.6;
    public double pitch = 6.0;
    public double yawOffset = 0.0;
    public double lookahead = 2.6;
    public double followSpeed = 0.14;
    public double verticalFollowSpeed = 0.09;
    public double deadzoneY = 1.2;
    public double lookaheadSpeed = 0.06;

    public CameraSettings copy() {
        CameraSettings c = new CameraSettings();
        c.distance = distance;
        c.height = height;
        c.pitch = pitch;
        c.yawOffset = yawOffset;
        c.lookahead = lookahead;
        c.followSpeed = followSpeed;
        c.verticalFollowSpeed = verticalFollowSpeed;
        c.deadzoneY = deadzoneY;
        c.lookaheadSpeed = lookaheadSpeed;
        return c;
    }

    /** 섹션에 있는 키만 덮어쓴 복사본을 반환. */
    public CameraSettings with(ConfigurationSection s) {
        CameraSettings c = copy();
        if (s == null) return c;
        c.distance = s.getDouble("distance", c.distance);
        c.height = s.getDouble("height", c.height);
        c.pitch = s.getDouble("pitch", c.pitch);
        c.yawOffset = s.getDouble("yaw-offset", c.yawOffset);
        c.lookahead = s.getDouble("lookahead", c.lookahead);
        c.followSpeed = clamp01(s.getDouble("follow-speed", c.followSpeed));
        c.verticalFollowSpeed = clamp01(s.getDouble("vertical-follow-speed", c.verticalFollowSpeed));
        c.deadzoneY = s.getDouble("deadzone-y", c.deadzoneY);
        c.lookaheadSpeed = clamp01(s.getDouble("lookahead-speed", c.lookaheadSpeed));
        return c;
    }

    private static double clamp01(double v) {
        return Math.max(0.001, Math.min(1.0, v));
    }
}
