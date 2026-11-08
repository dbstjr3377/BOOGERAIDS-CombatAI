package com.boogeraids.combatai.config;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.List;

/** 설정 파일용 불변 좌표. */
public record Vec(double x, double y, double z) {

    public static Vec parse(Object raw) {
        if (raw instanceof List<?> list && list.size() >= 3) {
            return new Vec(num(list.get(0)), num(list.get(1)), num(list.get(2)));
        }
        if (raw instanceof String s) {
            String[] p = s.replace("[", "").replace("]", "").split("[,\\s]+");
            if (p.length >= 3) {
                return new Vec(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
            }
        }
        return null;
    }

    private static double num(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }

    public static Vec of(Location l) {
        return new Vec(l.getX(), l.getY(), l.getZ());
    }

    public static Vec blockCenter(Location l) {
        return new Vec(l.getBlockX() + 0.5, l.getBlockY(), l.getBlockZ() + 0.5);
    }

    public Location toLocation(World w) {
        return new Location(w, x, y, z);
    }

    public Vector toVector() {
        return new Vector(x, y, z);
    }

    public List<Double> toList() {
        return List.of(round(x), round(y), round(z));
    }

    private static double round(double d) {
        return Math.round(d * 100.0) / 100.0;
    }

    public static Vec min(Vec a, Vec b) {
        return new Vec(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z));
    }

    public static Vec max(Vec a, Vec b) {
        return new Vec(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
    }
}
