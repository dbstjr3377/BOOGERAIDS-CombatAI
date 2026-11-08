package com.boogeraids.combatai.config;

import org.bukkit.configuration.ConfigurationSection;

public final class MovementSettings {
    public double walkSpeed = 0.27;
    public double groundAccel = 0.07;
    public double groundDecel = 0.11;
    public double airAccel = 0.04;
    public double jumpVelocity = 0.62;
    public double jumpCut = 0.5;
    public int coyoteTicks = 3;
    public int jumpBufferTicks = 4;

    public static MovementSettings load(ConfigurationSection s) {
        MovementSettings m = new MovementSettings();
        if (s == null) return m;
        m.walkSpeed = s.getDouble("walk-speed", m.walkSpeed);
        m.groundAccel = s.getDouble("ground-accel", m.groundAccel);
        m.groundDecel = s.getDouble("ground-decel", m.groundDecel);
        m.airAccel = s.getDouble("air-accel", m.airAccel);
        m.jumpVelocity = s.getDouble("jump-velocity", m.jumpVelocity);
        m.jumpCut = s.getDouble("jump-cut", m.jumpCut);
        m.coyoteTicks = s.getInt("coyote-ticks", m.coyoteTicks);
        m.jumpBufferTicks = s.getInt("jump-buffer-ticks", m.jumpBufferTicks);
        return m;
    }
}
