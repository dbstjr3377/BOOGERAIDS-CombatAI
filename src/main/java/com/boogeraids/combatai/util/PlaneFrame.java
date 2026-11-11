package com.boogeraids.combatai.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * 스테이지의 2D 평면 좌표계.
 * u = 화면 오른쪽 방향 좌표, y = 월드 높이, depth = 화면 안쪽 방향 (평면에서 0 유지).
 * facing 은 카메라가 바라보는 yaw (90의 배수).
 */
public final class PlaneFrame {
    public final World world;
    public final float facing;
    public final Vector origin;   // 평면 위 기준점 (스폰)
    public final Vector right;    // 화면 오른쪽
    public final Vector forward;  // 카메라가 바라보는 방향 (평면 안쪽)

    public PlaneFrame(World world, Vector origin, float facing) {
        this.world = world;
        this.facing = facing;
        this.origin = origin.clone();
        double r = Math.toRadians(facing);
        this.forward = new Vector(-Math.sin(r), 0, Math.cos(r));
        this.right = new Vector(-Math.cos(r), 0, -Math.sin(r));
        snap(forward);
        snap(right);
    }

    private static void snap(Vector v) {
        v.setX(Math.round(v.getX() * 1e6) / 1e6);
        v.setZ(Math.round(v.getZ() * 1e6) / 1e6);
    }

    public double u(Location l) {
        return (l.getX() - origin.getX()) * right.getX() + (l.getZ() - origin.getZ()) * right.getZ();
    }

    public double u(Vector v) {
        return (v.getX() - origin.getX()) * right.getX() + (v.getZ() - origin.getZ()) * right.getZ();
    }

    public double depth(Location l) {
        return (l.getX() - origin.getX()) * forward.getX() + (l.getZ() - origin.getZ()) * forward.getZ();
    }

    public Location at(double u, double y) {
        return new Location(world, origin.getX() + right.getX() * u, y, origin.getZ() + right.getZ() * u);
    }

    public Vector vec(double u, double y) {
        return new Vector(origin.getX() + right.getX() * u, y, origin.getZ() + right.getZ() * u);
    }

    /** 평면 방향 속도(u, y) → 월드 속도. */
    public Vector velocity(double vu, double vy) {
        return right.clone().multiply(vu).setY(vy);
    }

    /** 오른쪽(+u)을 바라보는 yaw. */
    public float yawFacing(int dir) {
        return dir >= 0 ? facing + 90f : facing - 90f;
    }
}
