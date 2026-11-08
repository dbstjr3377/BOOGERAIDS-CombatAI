package com.boogeraids.combatai.boss;

import com.boogeraids.combatai.config.BossSettings;
import com.boogeraids.combatai.game.Character;
import com.boogeraids.combatai.game.DeathReason;
import com.boogeraids.combatai.game.GameManager;
import com.boogeraids.combatai.util.Ease;
import com.boogeraids.combatai.util.Fonts;
import com.boogeraids.combatai.util.PlaneFrame;
import com.boogeraids.combatai.util.Tags;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 보스 "감시체". 아레나 위를 떠다니며 3가지 패턴을 사용한다.
 * <ul>
 *   <li>LASER  : 아레나 전체를 가로지르는 레이저. 낮은 레이저는 점프로 피하고, 높은 레이저는 점프하면 맞는다.</li>
 *   <li>DEBRIS : 경고 표시 후 천장에서 잔해 낙하.</li>
 *   <li>SLAM   : 플레이어 위로 이동 → 내려찍기 → 바닥을 따라 퍼지는 충격파 (점프로 회피). 이후 잠시 기절(추가 피해).</li>
 * </ul>
 * 피해 수단: 위에서 밟기, PLASMA, SHOCKWAVE.
 */
public final class BossRuntime {

    private enum Phase { ENTER, IDLE, LASER_TELE, LASER_FIRE, DEBRIS_TELE, DEBRIS_FALL, SLAM_MOVE, SLAM_TELE, SLAM_DROP, SLAM_WAVE, STUN, DEAD, GONE }

    private final GameManager game;
    private final BossSettings cfg;
    private final PlaneFrame frame;
    private final World world;

    private final double uMin, uMax, floorY, ceilY;
    private final double half;

    private ItemDisplay body;
    private final List<Entity> temp = new ArrayList<>();

    private double hp;
    private boolean enraged;
    private Phase phase = Phase.ENTER;
    private int phaseTicks;
    private int life;
    private int hurtCooldown;
    private int hitFlash;

    // 위치 (평면 좌표, 중심)
    private double bu, by;
    private double vy;

    // 패턴 상태
    private double laserY;
    private boolean laserLow;
    private BlockDisplay laserBeam;
    private final List<Debris> debris = new ArrayList<>();
    private double waveOrigin;
    private int lastPattern = -1;

    private static final class Debris {
        double u, y, v;
        BlockDisplay e;
        TextDisplay warn;
        int landed = -1;
    }

    public BossRuntime(GameManager game, BossSettings cfg, PlaneFrame frame, double startHp) {
        this.game = game;
        this.cfg = cfg;
        this.frame = frame;
        this.world = frame.world;
        double a = frame.u(cfg.arenaMin.toVector());
        double b = frame.u(new Vector(cfg.arenaMax.x(), cfg.arenaMin.y(), cfg.arenaMax.z()));
        double c = frame.u(new Vector(cfg.arenaMin.x(), cfg.arenaMin.y(), cfg.arenaMax.z()));
        double d = frame.u(new Vector(cfg.arenaMax.x(), cfg.arenaMin.y(), cfg.arenaMin.z()));
        this.uMin = Math.min(Math.min(a, b), Math.min(c, d));
        this.uMax = Math.max(Math.max(a, b), Math.max(c, d));
        this.floorY = cfg.arenaMin.y();
        this.ceilY = cfg.arenaMax.y();
        this.half = cfg.scale / 2.0;
        this.hp = startHp > 0 ? Math.min(startHp, cfg.health) : cfg.health;
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        bu = frame.u(cfg.pos.toVector());
        by = cfg.pos.y() + 9;   // 위에서 내려오며 등장
        Location at = frame.at(bu, by);
        body = Tags.mark(world.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(cfg.item));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(3);
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(0xFF2A4A));
            d.setViewRange(3f);
            d.setTransformation(scaleTf(0, 0));
        }));
        phase = Phase.ENTER;
        phaseTicks = 0;
    }

    public void cleanup() {
        if (body != null && body.isValid()) body.remove();
        for (Entity e : temp) if (e.isValid()) e.remove();
        temp.clear();
        debris.clear();
        laserBeam = null;
    }

    public boolean isDefeated() {
        return phase == Phase.DEAD || phase == Phase.GONE;
    }

    /** 사망 연출까지 끝났는지. */
    public boolean isGone() {
        return phase == Phase.GONE;
    }

    public double health() {
        return hp;
    }

    public double healthFraction() {
        return hp / cfg.health;
    }

    public boolean enraged() {
        return enraged;
    }

    public String name() {
        return cfg.name;
    }

    public Location center() {
        return frame.at(bu, by);
    }

    public boolean introDone() {
        return phase != Phase.ENTER;
    }

    // ------------------------------------------------------------------ damage

    public BoundingBox hitbox() {
        Location c = center();
        double h = half * 0.92;
        return new BoundingBox(c.getX() - h, c.getY() - h, c.getZ() - h, c.getX() + h, c.getY() + h, c.getZ() + h);
    }

    /** @return 실제로 피해를 입었는지 */
    public boolean damage(double amount, Location from) {
        if (isDefeated() || phase == Phase.ENTER || hurtCooldown > 0) return false;
        if (phase == Phase.STUN) amount *= 1.5;
        hp = Math.max(0, hp - amount);
        hurtCooldown = 6;
        hitFlash = 4;
        game.fx().sound("entity.iron_golem.hurt", 1f, 0.6f);
        game.fx().sound("block.amethyst_block.hit", 1f, 0.5f);
        game.fx().particle(Particle.ELECTRIC_SPARK, from != null ? from : center(), 25, 0.4, 0.3);
        game.camera().shake(0.25);
        if (!enraged && hp <= cfg.health * 0.5 && hp > 0) {
            enraged = true;
            body.setGlowColorOverride(Color.fromRGB(0xD02CFF));
            game.fx().sound("entity.warden.roar", 1f, 1.3f);
            game.camera().shake(0.6);
            game.hud().flash(0xD02CFF, 0.6, 20);
            game.hud().showTitle(Fonts.lab("⚠ OVERDRIVE ⚠", 0xD02CFF), Fonts.lab("감시체 출력 제한 해제", 0xE8C8FF), 30, 15);
        }
        if (hp <= 0) {
            enterDead();
        }
        return true;
    }

    private void enterDead() {
        cancelAttacks();
        phase = Phase.DEAD;
        phaseTicks = 0;
        game.fx().sound("entity.wither.death", 0.8f, 1.6f);
        game.hud().flash(0xFFFFFF, 0.9, 30);
    }

    // ------------------------------------------------------------------ tick

    /** @return 캐릭터를 죽였다면 사유 */
    public DeathReason tick(Character c) {
        life++;
        phaseTicks++;
        if (hurtCooldown > 0) hurtCooldown--;
        DeathReason kill = null;

        switch (phase) {
            case ENTER -> {
                double target = idleY();
                by = Ease.approach(by, target, 0.07);
                if (phaseTicks == 1) game.fx().sound("block.beacon.activate", 1f, 0.5f);
                if (phaseTicks % 6 == 0) game.camera().shake(0.12);
                if (phaseTicks == 30) {
                    game.fx().sound("entity.warden.sonic_charge", 1f, 0.8f);
                }
                if (phaseTicks >= 45) {
                    game.fx().sound("entity.warden.roar", 1f, 1.0f);
                    game.camera().shake(0.7);
                    setPhase(Phase.IDLE);
                }
            }
            case IDLE -> {
                hover(c.u() + (bu > c.u() ? 4.5 : -4.5), idleY(), 0.04);
                int idle = enraged ? (int) (cfg.idleTicks * 0.6) : cfg.idleTicks;
                if (phaseTicks >= idle) choosePattern(c);
            }
            case LASER_TELE -> {
                hover(bu, idleY(), 0.05);
                int dur = enraged ? 22 : 30;
                if (phaseTicks % 2 == 0) {
                    game.fx().line(frame.at(uMin, laserY), frame.at(uMax, laserY), laserLow ? 0xFF3030 : 0xFF9A30,
                            phaseTicks > dur - 8 ? 1.0f : 0.5f, 0.8);
                }
                if (phaseTicks % 10 == 0 || phaseTicks == dur - 3) {
                    game.fx().sound("block.note_block.bit", 1f, phaseTicks >= dur - 3 ? 2.0f : 1.4f);
                }
                game.fx().particle(Particle.ELECTRIC_SPARK, center(), 3, half * 0.6, 0.05);
                if (phaseTicks >= dur) fireLaser();
            }
            case LASER_FIRE -> {
                if (laserBeam != null && c.box().overlaps(laserBox())) kill = DeathReason.BOSS;
                if (phaseTicks % 2 == 0) game.camera().shake(0.08);
                if (phaseTicks >= 6) {
                    if (laserBeam != null) {
                        laserBeam.remove();
                        laserBeam = null;
                    }
                    setPhase(Phase.IDLE);
                }
            }
            case DEBRIS_TELE -> {
                hover(bu, idleY() + 1.0, 0.05);
                for (Debris d : debris) {
                    if (phaseTicks % 3 == 0) {
                        for (int i = 0; i < 8; i++) {
                            double a = i * Math.PI / 4 + life * 0.1;
                            Location p = frame.at(d.u + Math.cos(a) * 0.7, floorY + 0.1).add(frame.forward.clone().multiply(Math.sin(a) * 0.4));
                            game.fx().dust(p, 0xFFB300, 0.7f, 1, 0);
                        }
                    }
                }
                if (phaseTicks % 8 == 0) game.fx().sound("block.note_block.pling", 0.8f, 0.6f);
                if (phaseTicks >= (enraged ? 18 : 26)) dropDebris();
            }
            case DEBRIS_FALL -> {
                boolean any = false;
                for (Debris d : debris) {
                    if (d.landed >= 0) {
                        if (++d.landed > 10 && d.e.isValid()) d.e.remove();
                        continue;
                    }
                    any = true;
                    d.v = Math.min(1.2, d.v + 0.07);
                    d.y -= d.v;
                    if (d.y <= floorY) {
                        d.y = floorY;
                        d.landed = 0;
                        Location p = frame.at(d.u, floorY + 0.2);
                        game.fx().particle(Particle.EXPLOSION, p, 1, 0, 0);
                        game.fx().particle(Particle.LARGE_SMOKE, p, 8, 0.4, 0.02);
                        game.fx().sound("entity.generic.explode", 0.6f, 1.4f);
                        game.camera().shake(0.22);
                        if (d.warn != null) d.warn.remove();
                    }
                    d.e.teleport(frame.at(d.u, d.y).subtract(0.45, 0, 0.45));
                    BoundingBox bb = BoundingBox.of(frame.at(d.u, d.y + 0.45), 0.45, 0.45, 0.45);
                    if (c.box().overlaps(depthExpand(bb, 0.5))) kill = DeathReason.BOSS;
                }
                if (!any && phaseTicks > 30) {
                    for (Debris d : debris) if (d.e.isValid()) d.e.remove();
                    debris.clear();
                    setPhase(Phase.IDLE);
                }
            }
            case SLAM_MOVE -> {
                hover(c.u(), floorY + 7.5 + half, 0.16);
                if (phaseTicks >= (enraged ? 14 : 20)) setPhase(Phase.SLAM_TELE);
            }
            case SLAM_TELE -> {
                hover(bu, floorY + 7.8 + half, 0.2);
                if (phaseTicks % 2 == 0) {
                    game.fx().dust(frame.at(bu, floorY + 0.1), 0xFF3030, 1.2f, 6, 0.6);
                }
                if (phaseTicks == 1) game.fx().sound("entity.warden.sonic_charge", 1f, 1.6f);
                if (phaseTicks >= 14) {
                    vy = 0;
                    setPhase(Phase.SLAM_DROP);
                }
            }
            case SLAM_DROP -> {
                vy = Math.min(1.6, vy + 0.16);
                by -= vy;
                if (by - half <= floorY) {
                    by = floorY + half;
                    waveOrigin = bu;
                    game.fx().sound("entity.generic.explode", 1f, 0.6f);
                    game.fx().sound("entity.warden.sonic_boom", 0.7f, 1.4f);
                    game.fx().particle(Particle.EXPLOSION, frame.at(bu, floorY + 0.3), 3, 1.0, 0);
                    game.camera().shake(0.9);
                    game.hud().flash(0xFFB300, 0.35, 10);
                    setPhase(Phase.SLAM_WAVE);
                }
                if (c.box().overlaps(hitbox())) kill = DeathReason.BOSS;
            }
            case SLAM_WAVE -> {
                double front = phaseTicks * 0.6;
                for (int side = -1; side <= 1; side += 2) {
                    double wu = waveOrigin + side * (half + front);
                    if (wu < uMin || wu > uMax) continue;
                    Location p = frame.at(wu, floorY + 0.15);
                    game.fx().particle(Particle.SWEEP_ATTACK, p, 1, 0.05, 0);
                    game.fx().dust(p.clone().add(0, 0.2, 0), 0xFFB300, 1.4f, 3, 0.15);
                    if (Math.abs(c.u() - wu) < 0.6 && c.y() < floorY + 0.55) kill = DeathReason.BOSS;
                }
                if (phaseTicks >= 26) setPhase(Phase.STUN);
            }
            case STUN -> {
                if (phaseTicks % 6 == 0) game.fx().particle(Particle.CRIT, center().add(0, half, 0), 6, 0.4, 0.1);
                if (phaseTicks >= (enraged ? 34 : 50)) setPhase(Phase.IDLE);
            }
            case DEAD -> {
                if (phaseTicks % 4 == 0) {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    Location p = center().add(r.nextDouble(-half, half), r.nextDouble(-half, half), r.nextDouble(-0.3, 0.3));
                    game.fx().particle(Particle.EXPLOSION, p, 1, 0, 0);
                    game.fx().sound("entity.generic.explode", 0.7f, 1.0f + r.nextFloat() * 0.6f);
                    game.camera().shake(0.3);
                    body.setGlowColorOverride(Color.fromRGB((phaseTicks / 4) % 2 == 0 ? 0xFFFFFF : 0xFF2A4A));
                }
                by -= 0.02;
                if (phaseTicks >= 70) {
                    game.fx().particle(Particle.EXPLOSION_EMITTER, center(), 1, 0, 0);
                    game.fx().sound("entity.generic.explode", 1f, 0.5f);
                    game.camera().shake(1.0);
                    body.remove();
                    phase = Phase.GONE;
                }
            }
            case GONE -> { }
        }

        // 본체 접촉 / 밟기 판정
        if (kill == null && phase != Phase.ENTER && !isDefeated()) {
            BoundingBox hb = depthExpand(hitbox(), 0.6);
            BoundingBox cb = c.box();
            if (cb.overlaps(hb)) {
                double vyc = c.body().getVelocity().getY();
                boolean fromAbove = vyc < 0.05 && cb.getMinY() >= hb.getMaxY() - 0.7;
                if (fromAbove) {
                    damage(3.0, c.location());
                    c.bounce(0.75);
                    game.fx().sound("entity.player.attack.crit", 1f, 0.8f);
                } else if (c.isDashing() && phase == Phase.STUN) {
                    // 기절한 보스에게 대시로 들이받기
                    damage(2.0, c.center());
                } else {
                    kill = DeathReason.BOSS;
                }
            }
        }

        if (body != null && body.isValid()) render();
        return kill;
    }

    private double idleY() {
        return floorY + 4.6 + half;
    }

    private void hover(double targetU, double targetY, double speed) {
        double minU = uMin + half + 0.5, maxU = uMax - half - 0.5;
        targetU = Ease.clamp(targetU, minU, Math.max(minU, maxU));
        targetY = Math.min(targetY, ceilY - half - 0.2);
        bu = Ease.approach(bu, targetU, speed);
        by = Ease.approach(by, targetY + Math.sin(life * 0.08) * 0.25, speed * 1.5);
    }

    private void setPhase(Phase p) {
        phase = p;
        phaseTicks = 0;
    }

    private void choosePattern(Character c) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int pick;
        do {
            pick = r.nextInt(3);
        } while (pick == lastPattern && r.nextInt(3) != 0);
        lastPattern = pick;
        switch (pick) {
            case 0 -> {
                laserLow = r.nextBoolean();
                laserY = floorY + (laserLow ? 0.35 : 2.35);
                game.fx().sound("block.beacon.power_select", 1f, 0.6f);
                setPhase(Phase.LASER_TELE);
            }
            case 1 -> {
                debris.clear();
                int n = enraged ? 6 : 4;
                addDebris(c.u());
                for (int i = 1; i < n; i++) addDebris(r.nextDouble(uMin + 1, uMax - 1));
                game.fx().sound("entity.warden.sonic_charge", 1f, 1.2f);
                setPhase(Phase.DEBRIS_TELE);
            }
            default -> setPhase(Phase.SLAM_MOVE);
        }
    }

    private void addDebris(double u) {
        Debris d = new Debris();
        d.u = Ease.clamp(u, uMin + 0.6, uMax - 0.6);
        d.y = ceilY - 1;
        d.warn = Tags.mark(world.spawn(frame.at(d.u, floorY + 0.4), TextDisplay.class, t -> {
            t.text(Fonts.ui(Fonts.WARN, 0xFFFFFF));
            t.setBillboard(Display.Billboard.CENTER);
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setBrightness(new Display.Brightness(15, 15));
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.2f, 1.2f, 1.2f), new Quaternionf()));
        }));
        temp.add(d.warn);
        debris.add(d);
    }

    private void dropDebris() {
        BlockData bd = Material.IRON_BLOCK.createBlockData();
        for (Debris d : debris) {
            Location l = frame.at(d.u, d.y).subtract(0.45, 0, 0.45);
            d.e = Tags.mark(world.spawn(l, BlockDisplay.class, b -> {
                b.setBlock(bd);
                b.setTeleportDuration(1);
                b.setBrightness(new Display.Brightness(15, 15));
                b.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.9f, 0.9f, 0.9f), new Quaternionf()));
                b.setGlowing(true);
                b.setGlowColorOverride(Color.fromRGB(0xFFB300));
            }));
            temp.add(d.e);
        }
        setPhase(Phase.DEBRIS_FALL);
    }

    private BoundingBox laserBox() {
        Location a = frame.at(uMin, laserY - 0.2);
        Location b = frame.at(uMax, laserY + 0.2);
        return depthExpand(BoundingBox.of(a, b), 0.6);
    }

    /** 화면 안쪽(깊이) 방향으로만 양쪽 확장. */
    private BoundingBox depthExpand(BoundingBox bb, double amount) {
        return bb.clone().expand(Math.abs(frame.forward.getX()) * amount, 0, Math.abs(frame.forward.getZ()) * amount);
    }

    private void fireLaser() {
        BoundingBox bb = laserBox();
        Location at = new Location(world, bb.getMinX(), bb.getMinY(), bb.getMinZ());
        float sx = (float) bb.getWidthX(), sy = (float) bb.getHeight(), sz = (float) bb.getWidthZ();
        laserBeam = Tags.mark(world.spawn(at, BlockDisplay.class, b -> {
            b.setBlock(Material.RED_STAINED_GLASS.createBlockData());
            b.setBrightness(new Display.Brightness(15, 15));
            b.setGlowing(true);
            b.setGlowColorOverride(Color.fromRGB(laserLow ? 0xFF1E3C : 0xFF8A1E));
            b.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(sx, sy, sz), new Quaternionf()));
        }));
        temp.add(laserBeam);
        game.fx().sound("entity.warden.sonic_boom", 0.9f, 1.8f);
        game.fx().sound("block.beacon.deactivate", 1f, 2.0f);
        game.hud().flash(0xFF1E3C, 0.4, 8);
        game.camera().shake(0.35);
        setPhase(Phase.LASER_FIRE);
    }

    private void cancelAttacks() {
        if (laserBeam != null) {
            laserBeam.remove();
            laserBeam = null;
        }
        for (Iterator<Debris> it = debris.iterator(); it.hasNext(); ) {
            Debris d = it.next();
            if (d.e != null) d.e.remove();
            if (d.warn != null) d.warn.remove();
            it.remove();
        }
    }

    private Transformation scaleTf(float wobble, float tilt) {
        float s = (float) cfg.scale;
        Quaternionf q = new Quaternionf().rotateY(wobble).rotateZ(tilt);
        return new Transformation(new Vector3f(), q, new Vector3f(s, s, s), new Quaternionf());
    }

    private void render() {
        body.teleport(frame.at(bu, by));
        if (life % 5 == 0) {
            float wob = (float) Math.sin(life * 0.05) * 0.35f;
            float tilt = phase == Phase.STUN ? 0.35f : (float) Math.sin(life * 0.04) * 0.08f;
            body.setInterpolationDelay(0);
            body.setInterpolationDuration(5);
            body.setTransformation(scaleTf(wob, tilt));
        }
        if (hitFlash > 0) {
            hitFlash--;
            body.setGlowColorOverride(Color.fromRGB(hitFlash > 0 ? 0xFFFFFF : (enraged ? 0xD02CFF : 0xFF2A4A)));
        }
    }
}
