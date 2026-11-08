package com.boogeraids.combatai.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public final class BossSettings {
    public String name = "SENTINEL-00";
    public double health = 60;
    public Material item = Material.OBSERVER;
    public double scale = 3.0;
    public Vec pos;
    public Vec arenaMin;
    public Vec arenaMax;
    public int idleTicks = 50;

    public static BossSettings load(ConfigurationSection s) {
        if (s == null) return null;
        BossSettings b = new BossSettings();
        b.name = s.getString("name", b.name);
        b.health = Math.max(1, s.getDouble("health", b.health));
        Material m = Material.matchMaterial(s.getString("item", "OBSERVER"));
        if (m != null && m.isItem()) b.item = m;
        b.scale = Math.max(0.5, s.getDouble("scale", b.scale));
        b.pos = Vec.parse(s.get("pos"));
        Vec a = Vec.parse(s.get("arena-min"));
        Vec c = Vec.parse(s.get("arena-max"));
        if (a != null && c != null) {
            b.arenaMin = Vec.min(a, c);
            b.arenaMax = Vec.max(a, c);
        }
        b.idleTicks = Math.max(10, s.getInt("idle-ticks", b.idleTicks));
        return b;
    }

    public boolean isValid() {
        return pos != null && arenaMin != null && arenaMax != null;
    }
}
