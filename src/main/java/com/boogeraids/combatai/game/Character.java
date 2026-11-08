package com.boogeraids.combatai.game;

import com.boogeraids.combatai.ability.AbilitySlotState;
import com.boogeraids.combatai.config.MovementSettings;
import com.boogeraids.combatai.util.PlaneFrame;
import com.boogeraids.combatai.util.Tags;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Location;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * 통제권 플레이어의 스킨을 입은 Mannequin 캐릭터.
 * 중력/블록 충돌은 바닐라 물리를 그대로 쓰고, 수평 속도·점프만 매 틱 직접 제어한다.
 * 깊이(depth) 방향 이탈은 속도로 보정해 항상 스테이지 평면 위에 머무르게 한다.
 */
public final class Character {

    public static final double WIDTH = 0.6;
    public static final double HEIGHT = 1.8;

    private final MovementSettings mv;
    private final PlaneFrame frame;
    private Mannequin body;

    public final AbilitySlotState[] slots = {new AbilitySlotState(), new AbilitySlotState()};

    private double vu;            // 평면 수평 속도 (내부 상태)
    private double appliedVu;     // 지난 틱에 실제로 적용한 수평 속도 (벽 판정용)
    private int facing = 1;
    private int airTicks;
    private int jumpBuffer;
    private boolean jumpHeldLast;
    private boolean jumping;      // 점프로 상승 중 (가변 점프용)
    private boolean usedBoost;
    private double lastU, lastY;

    // 대시
    private int dashTicks;
    private int dashDir;
    private double dashSpeed;

    private boolean frozen;
    private int controlLock;      // 옆 스프링 발사 후 공중 제어 감소
    private boolean dead;

    public Character(PlaneFrame frame, MovementSettings mv) {
        this.frame = frame;
        this.mv = mv;
    }

    public void spawn(Location at, PlayerProfile profile) {
        remove();
        Location l = at.clone();
        l.setYaw(frame.yawFacing(1));
        l.setPitch(0);
        body = Tags.mark(at.getWorld().spawn(l, Mannequin.class, m -> {
            if (profile != null) m.setProfile(ResolvableProfile.resolvableProfile(profile));
            m.setDescription(null);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setCollidable(false);
            m.setRemoveWhenFarAway(false);
            m.setCanPickupItems(false);
        }));
        vu = 0;
        appliedVu = 0;
        facing = 1;
        dashTicks = 0;
        airTicks = 0;
        jumpBuffer = 0;
        usedBoost = false;
        lastU = frame.u(l);
        lastY = l.getY();
    }

    public void remove() {
        if (body != null && body.isValid()) body.remove();
        body = null;
    }

    public boolean isValid() {
        return body != null && body.isValid();
    }

    public Mannequin body() {
        return body;
    }

    public void setFrozen(boolean f) {
        frozen = f;
        if (body != null && f) body.setVelocity(new Vector(0, Math.min(0, body.getVelocity().getY()), 0));
    }

    // ------------------------------------------------------------------ queries

    public Location location() {
        return body.getLocation();
    }

    public double u() {
        return frame.u(body.getLocation());
    }

    public double y() {
        return body.getLocation().getY();
    }

    public double velU() {
        return vu;
    }

    public int facing() {
        return facing;
    }

    public boolean onGround() {
        return body.isOnGround();
    }

    public Location center() {
        return body.getLocation().add(0, HEIGHT / 2, 0);
    }

    public BoundingBox box() {
        return body.getBoundingBox();
    }

    public boolean isDashing() {
        return dashTicks > 0;
    }

    // ------------------------------------------------------------------ control

    /** 매 틱 호출. */
    public void tick(InputState in) {
        if (body == null || !body.isValid() || dead) return;
        for (AbilitySlotState s : slots) {
            if (s.cooldown > 0) s.cooldown--;
            if (s.flash > 0) s.flash--;
        }

        Location loc = body.getLocation();
        double u = frame.u(loc);
        double movedU = u - lastU;
        Vector vel = body.getVelocity();
        double vy = vel.getY();
        boolean ground = body.isOnGround();

        if (frozen) {
            vu = 0;
            appliedVu = 0;
            body.setVelocity(frame.velocity(0, Math.min(vy, 0)).add(depthCorrection(loc)));
            lastU = u;
            lastY = loc.getY();
            return;
        }

        // 벽에 막혔으면 내부 속도도 끊는다
        if (Math.abs(appliedVu) > 0.05 && Math.abs(movedU) < Math.abs(appliedVu) * 0.25 && dashTicks == 0) vu = movedU;

        int dir = (in.right ? 1 : 0) - (in.left ? 1 : 0);
        if (dir != 0 && dashTicks == 0) facing = dir;

        // 점프 입력 버퍼
        boolean jumpPressed = in.consumeJumpPress() || (in.jump && !jumpHeldLast);
        jumpHeldLast = in.jump;
        if (jumpPressed) jumpBuffer = mv.jumpBufferTicks + 1;
        if (jumpBuffer > 0) jumpBuffer--;

        if (ground) {
            airTicks = 0;
            usedBoost = false;
            if (jumping && vy <= 0) jumping = false;
        } else {
            airTicks++;
        }

        if (dashTicks > 0) {
            dashTicks--;
            vu = dashDir * dashSpeed;
            vy = 0;
            if (dashTicks == 0) {
                body.setGravity(true);
                vu = dashDir * mv.walkSpeed;
            }
        } else {
            double target = dir * mv.walkSpeed;
            double accel;
            if (ground) accel = dir == 0 || Math.signum(target) != Math.signum(vu) ? mv.groundDecel : mv.groundAccel;
            else accel = mv.airAccel;
            if (controlLock > 0) {
                controlLock--;
                accel *= 0.15;
                if (ground && controlLock < 8) controlLock = 0;
            }
            if (vu < target) vu = Math.min(target, vu + accel);
            else if (vu > target) vu = Math.max(target, vu - accel);

            // 점프 (코요테 타임 포함)
            if (jumpBuffer > 0 && airTicks <= mv.coyoteTicks && !jumping) {
                vy = mv.jumpVelocity;
                jumping = true;
                jumpBuffer = 0;
                airTicks = mv.coyoteTicks + 1;
            }
            // 가변 점프: 키를 떼면 상승 감속
            if (jumping && !in.jump && vy > 0.1) {
                vy *= mv.jumpCut;
                jumping = false;
            }
        }

        body.setVelocity(frame.velocity(vu, vy).add(depthCorrection(loc)));
        appliedVu = vu;

        // 바라보는 방향
        float yaw = frame.yawFacing(facing);
        if (Math.abs(loc.getYaw() - yaw) > 1f) {
            body.setRotation(yaw, 0f);
            body.setBodyYaw(yaw);
        }
        lastU = u;
        lastY = loc.getY();
    }

    private Vector depthCorrection(Location loc) {
        double dep = frame.depth(loc);
        if (Math.abs(dep) < 0.02) return new Vector();
        return frame.forward.clone().multiply(-dep * 0.8);
    }

    // ------------------------------------------------------------------ ability primitives

    public void dash(int ticks, double speed) {
        dashTicks = ticks;
        dashDir = facing;
        dashSpeed = speed;
        body.setGravity(false);
    }

    public boolean boost(double power) {
        if (usedBoost && !onGround()) return false;
        usedBoost = !onGround();
        jumping = false;
        Vector v = body.getVelocity();
        body.setVelocity(new Vector(v.getX(), power, v.getZ()));
        return true;
    }

    public void bounce(double power) {
        jumping = false;
        usedBoost = false;
        Vector v = body.getVelocity();
        body.setVelocity(new Vector(v.getX(), power, v.getZ()));
    }

    /** 옆 스프링: 수평 속도 vu, 수직 vy 로 발사하고 lock 틱 동안 공중 제어를 약하게. */
    public void launch(double vu, double vy, int lock) {
        stopDash();
        this.vu = vu;
        this.facing = vu >= 0 ? 1 : -1;
        jumping = false;
        usedBoost = false;
        controlLock = lock;
        appliedVu = vu;
        body.setVelocity(frame.velocity(vu, vy));
    }

    public void teleport(Location l) {
        l.setYaw(frame.yawFacing(facing));
        l.setPitch(0);
        body.teleport(l);
        lastU = frame.u(l);
        lastY = l.getY();
    }

    public void stopDash() {
        if (dashTicks > 0) {
            dashTicks = 0;
            body.setGravity(true);
        }
    }

    public void swing() {
        if (body != null) body.swingMainHand();
    }

    public void deathPose(Vector knock) {
        stopDash();
        frozen = true;
        dead = true;
        body.setGravity(true);
        body.setVelocity(knock);
        try {
            body.setPose(Pose.SLEEPING);
        } catch (IllegalArgumentException ignored) {
            // 일부 버전에서 허용되지 않는 포즈면 무시
        }
    }

    public void resetAbilities() {
        for (AbilitySlotState s : slots) s.clear();
    }
}
