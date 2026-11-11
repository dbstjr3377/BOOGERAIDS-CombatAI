package com.boogeraids.combatai.camera;

import com.boogeraids.combatai.BoogerAIDS;
import com.boogeraids.combatai.config.CameraSettings;
import com.boogeraids.combatai.util.Ease;
import com.boogeraids.combatai.util.PlaneFrame;
import com.boogeraids.combatai.util.Tags;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 횡스크롤 카메라.
 * <ul>
 *   <li>cam  : 관전자 전원이 spectate 하는 보이지 않는 ItemDisplay. HUD 엔티티들이 승객으로 붙는다.</li>
 *   <li>seat : 통제권 플레이어(어드벤처 모드)가 탑승하는 좌석. 눈 높이가 cam 과 일치하도록 자동 보정.</li>
 *   <li>clicker : 통제권 플레이어 시선 앞에 붙어 다니는 Interaction. 좌/우클릭을 받는다.</li>
 * </ul>
 * 이동은 매 틱 지수 이징(ease-out) + 진행방향 lookahead + 수직 데드존을 적용하고,
 * ItemDisplay 의 teleport interpolation 으로 클라이언트에서 프레임 단위로 부드럽게 보간된다.
 */
public final class CameraRig {

    private static final int TELEPORT_INTERP = 2;

    private final BoogerAIDS plugin;
    private ItemDisplay cam;
    private ItemDisplay seat;
    private Interaction clicker;

    private PlaneFrame frame;
    private CameraSettings settings;

    private double focusU, focusY, lookU;
    private double trauma;
    private int ticks;
    private float yaw, pitch;
    private double eyeOffset = 1.62;

    // 연출용 오버라이드 (보스 등장 시 특정 지점을 보여주기 등)
    private Vector overrideFocus;
    private int overrideTicks;
    private double zoom = 1.0, zoomTarget = 1.0;

    private UUID controller;
    private boolean allowDismount;
    private final Set<UUID> viewers = new HashSet<>();

    public CameraRig(BoogerAIDS plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ lifecycle

    public void create(World world, Location at) {
        destroy();
        Location l = at.clone();
        cam = Tags.mark(world.spawn(l, ItemDisplay.class, d -> {
            d.setTeleportDuration(TELEPORT_INTERP);
            d.setViewRange(4f);
            d.setDisplayWidth(0f);
            d.setDisplayHeight(0f);
        }));
        seat = Tags.mark(world.spawn(l, ItemDisplay.class, d -> {
            d.setTeleportDuration(TELEPORT_INTERP);
            d.setDisplayWidth(0f);
            d.setDisplayHeight(0f);
        }));
        clicker = Tags.mark(world.spawn(l, Interaction.class, i -> {
            i.setInteractionWidth(2.6f);
            i.setInteractionHeight(2.6f);
            i.setResponsive(false);
        }));
    }

    public void destroy() {
        if (seat != null) {
            allowDismount = true;
            for (Entity p : seat.getPassengers()) seat.removePassenger(p);
            allowDismount = false;
            seat.remove();
        }
        if (cam != null) {
            for (Entity p : cam.getPassengers()) {
                cam.removePassenger(p);
            }
            cam.remove();
        }
        if (clicker != null) clicker.remove();
        cam = null;
        seat = null;
        clicker = null;
    }

    public boolean isAlive() {
        return cam != null && cam.isValid();
    }

    public ItemDisplay cam() {
        return cam;
    }

    public ItemDisplay seat() {
        return seat;
    }

    public Interaction clicker() {
        return clicker;
    }

    public boolean isDismountAllowed() {
        return allowDismount;
    }

    // ------------------------------------------------------------------ config

    public void configure(PlaneFrame frame, CameraSettings settings) {
        this.frame = frame;
        this.settings = settings;
        this.yaw = (float) (frame.facing + settings.yawOffset);
        this.pitch = (float) settings.pitch;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    /** 즉시 해당 위치로 이동 (스테이지 시작). */
    public void snap(double u, double y) {
        focusU = u;
        focusY = y;
        lookU = 0;
        trauma = 0;
        overrideFocus = null;
        zoom = zoomTarget = 1.0;
        if (cam != null) {
            cam.setTeleportDuration(0);
            seat.setTeleportDuration(0);
            apply();
            later(() -> {
                if (cam != null) cam.setTeleportDuration(TELEPORT_INTERP);
                if (seat != null) seat.setTeleportDuration(TELEPORT_INTERP);
            });
        }
    }

    private void later(Runnable r) {
        plugin.getServer().getScheduler().runTaskLater(plugin, r, 2L);
    }

    /** 화면 흔들림 추가 (0~1). */
    public void shake(double amount) {
        trauma = Math.min(1.0, trauma + amount);
    }

    /** 일정 시간 동안 특정 지점을 바라보는 연출. */
    public void focusOverride(Vector worldPoint, int ticks) {
        this.overrideFocus = worldPoint.clone();
        this.overrideTicks = ticks;
    }

    public void zoom(double factor) {
        this.zoomTarget = factor;
    }

    // ------------------------------------------------------------------ tick

    /**
     * @param targetU   캐릭터 u
     * @param targetY   캐릭터 발 y
     * @param velU      캐릭터 수평 속도 (lookahead 용)
     */
    public void tick(double targetU, double targetY, double velU) {
        if (cam == null || frame == null) return;
        ticks++;

        double fu = targetU, fy = targetY;
        if (overrideFocus != null) {
            fu = frame.u(overrideFocus);
            fy = overrideFocus.getY();
            if (--overrideTicks <= 0) overrideFocus = null;
        }

        // 수평: lookahead 를 별도로 이징 → 방향 전환 시 화면이 부드럽게 미끄러짐
        double lookTarget = Math.abs(velU) > 0.05 ? Math.signum(velU) * settings.lookahead : lookU * 0.98;
        if (overrideFocus != null) lookTarget = 0;
        lookU = Ease.approach(lookU, lookTarget, settings.lookaheadSpeed);
        focusU = Ease.approach(focusU, fu, settings.followSpeed);

        // 수직: 데드존 밖으로 나갔을 때만 따라감
        double dy = fy - focusY;
        if (Math.abs(dy) > settings.deadzoneY || overrideFocus != null) {
            double target = overrideFocus != null ? fy : fy - Math.signum(dy) * settings.deadzoneY;
            focusY = Ease.approach(focusY, target, settings.verticalFollowSpeed);
        } else if (Math.abs(dy) > 0.01) {
            focusY = Ease.approach(focusY, fy, settings.verticalFollowSpeed * 0.25);
        }

        zoom = Ease.approach(zoom, zoomTarget, 0.08);
        trauma = Math.max(0, trauma - 0.035);
        apply();
    }

    private void apply() {
        double r = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        Vector look = new Vector(-Math.sin(r) * Math.cos(p), -Math.sin(p), Math.cos(r) * Math.cos(p));
        Vector focus = frame.vec(focusU + lookU, focusY + settings.height);
        Vector pos = focus.clone().subtract(look.clone().multiply(settings.distance * zoom));

        // 트라우마 기반 흔들림 (t² 로 강도 감쇠, 서로 다른 주파수의 사인 합성으로 노이즈 근사)
        if (trauma > 0.001) {
            double s = trauma * trauma;
            double t = ticks;
            double ox = (Math.sin(t * 1.7) * 0.6 + Math.sin(t * 3.9 + 1.3) * 0.4) * 0.35 * s;
            double oy = (Math.sin(t * 2.3 + 0.7) * 0.6 + Math.sin(t * 4.7 + 2.1) * 0.4) * 0.30 * s;
            Vector right = new Vector(-Math.cos(r), 0, -Math.sin(r));
            pos.add(right.multiply(ox)).add(new Vector(0, oy, 0));
        }

        Location camLoc = pos.toLocation(frame.world, yaw, pitch);
        cam.teleport(camLoc, TeleportFlag.EntityState.RETAIN_PASSENGERS);

        Location seatLoc = camLoc.clone().subtract(0, eyeOffset, 0);
        seat.teleport(seatLoc, TeleportFlag.EntityState.RETAIN_PASSENGERS);

        Player c = controller();
        if (c != null) {
            // 좌석 위 플레이어 눈 높이를 측정해 cam 과 일치하도록 보정
            if (c.getVehicle() == seat) {
                double measured = c.getEyeLocation().getY() - seat.getLocation().getY();
                if (measured > 0.2 && measured < 3.5) eyeOffset = Ease.approach(eyeOffset, measured, 0.5);
            }
            // 시점 고정 (시점이 많이 벗어났을 때만 보정해 떨림 방지)
            Location el = c.getLocation();
            if (Math.abs(wrap(el.getYaw() - yaw)) > 4f || Math.abs(el.getPitch() - pitch) > 4f) {
                c.setRotation(yaw, pitch);
            }
            // 클릭 수신용 Interaction 을 시선 앞에 배치
            Location eye = c.getEyeLocation();
            Vector dir = eye.getDirection();
            Location cl = eye.clone().add(dir.multiply(1.4)).subtract(0, clicker.getInteractionHeight() / 2.0, 0);
            clicker.teleport(cl);
        } else if (clicker != null) {
            clicker.teleport(camLoc.clone().subtract(0, 40, 0));
        }

        // 관전자 시점 유지
        if (ticks % 10 == 0) {
            for (UUID id : viewers) {
                Player v = plugin.getServer().getPlayer(id);
                if (v == null || !v.isOnline()) continue;
                if (v.getGameMode() != GameMode.SPECTATOR) v.setGameMode(GameMode.SPECTATOR);
                if (v.getSpectatorTarget() != cam) {
                    if (v.getWorld() != cam.getWorld() || v.getLocation().distanceSquared(camLoc) > 64) {
                        v.teleport(camLoc);
                    }
                    v.setSpectatorTarget(cam);
                }
            }
            if (c != null && c.getVehicle() != seat) mountController(c);
        }
    }

    private static float wrap(float a) {
        a %= 360f;
        if (a >= 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }

    public Location cameraLocation() {
        return cam == null ? null : cam.getLocation();
    }

    // ------------------------------------------------------------------ players

    public Player controller() {
        return controller == null ? null : plugin.getServer().getPlayer(controller);
    }

    public void setController(Player p) {
        Player old = controller();
        if (old != null && (p == null || !old.getUniqueId().equals(p.getUniqueId()))) {
            releaseController(old);
        }
        controller = p == null ? null : p.getUniqueId();
        if (p != null) {
            viewers.remove(p.getUniqueId());
            clearSpectate(p);
            p.setGameMode(GameMode.ADVENTURE);
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            p.setCollidable(false);
            p.setInvulnerable(true);
            mountController(p);
        }
    }

    private void mountController(Player p) {
        if (seat == null) return;
        if (p.getVehicle() != null && p.getVehicle() != seat) {
            allowDismount = true;
            p.leaveVehicle();
            allowDismount = false;
        }
        if (p.getVehicle() == seat) return;
        p.teleport(seat.getLocation());
        seat.addPassenger(p);
        p.setRotation(yaw, pitch);
    }

    /** 통제권 해제: 좌석에서 내리고 관전자로 전환. */
    public void releaseController(Player p) {
        allowDismount = true;
        if (p.getVehicle() != null) p.leaveVehicle();
        allowDismount = false;
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        p.removePotionEffect(PotionEffectType.SATURATION);
        p.setInvulnerable(false);
        p.setCollidable(true);
        if (controller != null && controller.equals(p.getUniqueId())) controller = null;
    }

    public boolean isController(Player p) {
        return controller != null && controller.equals(p.getUniqueId());
    }

    public void addViewer(Player p) {
        if (isController(p)) return;
        viewers.add(p.getUniqueId());
        p.setGameMode(GameMode.SPECTATOR);
        if (cam != null) {
            p.teleport(cam.getLocation());
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (cam != null && p.isOnline() && viewers.contains(p.getUniqueId()) && p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(cam);
            }, 3L);
        }
    }

    public void removeViewer(Player p) {
        if (viewers.remove(p.getUniqueId()) && p.isOnline()) {
            clearSpectate(p);
        }
    }

    /** setSpectatorTarget 은 관전 모드가 아니면 예외를 던지므로 확인 후 호출. */
    public static void clearSpectate(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR && p.getSpectatorTarget() != null) p.setSpectatorTarget(null);
    }

    public boolean isViewer(Player p) {
        return viewers.contains(p.getUniqueId());
    }

    public Set<UUID> viewers() {
        return viewers;
    }
}
