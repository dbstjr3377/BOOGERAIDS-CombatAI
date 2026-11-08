package com.boogeraids.combatai.stage;

import com.boogeraids.combatai.ability.Ability;
import com.boogeraids.combatai.config.StageDef;
import com.boogeraids.combatai.config.StageObject;
import com.boogeraids.combatai.config.Vec;
import com.boogeraids.combatai.game.Character;
import com.boogeraids.combatai.game.DeathReason;
import com.boogeraids.combatai.game.GameManager;
import com.boogeraids.combatai.util.Fonts;
import com.boogeraids.combatai.util.PlaneFrame;
import com.boogeraids.combatai.util.Sprites;
import com.boogeraids.combatai.util.Tags;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 번의 스테이지 시도 동안 존재하는 런타임 상태.
 * 사망 → 재시작 때마다 새로 만들어지며, cleanup() 에서 부순 블록·스위치를 원상 복구한다.
 */
public final class StageRuntime {

    private final GameManager game;
    public final StageDef def;
    public final PlaneFrame frame;
    private final World world;

    private final List<Entity> entities = new ArrayList<>();
    private final Map<Location, BlockData> restore = new LinkedHashMap<>();
    private final List<Pickup> pickups = new ArrayList<>();
    private final List<Switch> switches = new ArrayList<>();
    private final List<Laser> lasers = new ArrayList<>();
    private final List<Pad> pads = new ArrayList<>();
    private final List<Saw> saws = new ArrayList<>();
    private final List<Spikes> spikes = new ArrayList<>();
    private final List<Spring> springs = new ArrayList<>();
    private final List<Hint> hints = new ArrayList<>();
    private BoundingBox goal;
    private TextDisplay goalLabel;
    private int tick;

    private static final class Pickup {
        StageObject def;
        ItemDisplay icon, shell;
        TextDisplay label;
        BoundingBox box;
        boolean taken;
        float angle;
    }

    private static final class Switch {
        StageObject def;
        BoundingBox box;
        TextDisplay label;
        boolean active;
    }

    private static final class Laser {
        StageObject def;
        BoundingBox box;
        BlockDisplay beam;
        boolean on;
    }

    private static final class Pad {
        StageObject def;
        BoundingBox box;
    }

    /** 회전 톱날. 고정 / 경로 왕복·순환 / 공전. */
    private static final class Saw {
        StageObject def;
        ItemDisplay e;
        double[][] pts;      // (u, y) 경로점
        double[] cum;        // 누적 길이
        double total;        // 열린 경로 길이 (순환이면 닫는 구간 포함)
        double cu, cy;       // 공전 중심 또는 고정 위치
        double u, y;         // 현재 위치
        float angle;
        boolean moving;
    }

    private static final class Spikes {
        StageObject def;
        double u0, u1, y0, y1;   // 판정 박스 (평면 좌표)
    }

    private static final class Spring {
        StageObject def;
        ItemDisplay e;
        double cu, y0;
        int pressed;
        int cooldown;
    }

    private static final class Hint {
        StageObject def;
        BoundingBox box;
        boolean shown;
    }

    public StageRuntime(GameManager game, StageDef def, PlaneFrame frame) {
        this.game = game;
        this.def = def;
        this.frame = frame;
        this.world = frame.world;
    }

    // ------------------------------------------------------------------ setup

    public void start() {
        if (!def.isBoss() && def.goalMin != null) {
            goal = new BoundingBox(def.goalMin.x(), def.goalMin.y(), def.goalMin.z(),
                    def.goalMax.x() + 1, def.goalMax.y() + 1, def.goalMax.z() + 1);
            Location top = new Location(world, goal.getCenterX(), goal.getMaxY() + 0.4, goal.getCenterZ());
            goalLabel = text(top, Component.empty()
                    .append(Fonts.lab("▼ EXIT ▼", 0x5CFF8A).decorate(TextDecoration.BOLD)), 1.6f, true);
        }
        for (StageObject o : def.objects) {
            switch (o.type) {
                case ABILITY -> spawnPickup(o);
                case SWITCH -> spawnSwitch(o);
                case LASER -> spawnLaser(o);
                case JUMPPAD -> spawnPad(o);
                case SIGN -> text(o.pos.toLocation(world), Fonts.lab(o.text, 0xD8F6FF), 1.0f, true);
                case SAW -> spawnSaw(o);
                case SPIKES -> spawnSpikes(o);
                case SPRING -> spawnSpring(o);
                case HINT -> {
                    Hint h = new Hint();
                    h.def = o;
                    h.box = new BoundingBox(o.min.x(), o.min.y(), o.min.z(), o.max.x(), o.max.y(), o.max.z());
                    hints.add(h);
                }
            }
        }
    }

    private TextDisplay text(Location at, Component c, float scale, boolean bg) {
        TextDisplay t = Tags.mark(world.spawn(at, TextDisplay.class, d -> {
            d.text(c);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setDefaultBackground(false);
            d.setBackgroundColor(bg ? Color.fromARGB(150, 6, 10, 16) : Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(!bg);
            d.setLineWidth(400);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
        }));
        entities.add(t);
        return t;
    }

    private void spawnPickup(StageObject o) {
        Pickup p = new Pickup();
        p.def = o;
        Location at = o.pos.toLocation(world).add(0, 0.6, 0);
        Ability ab = o.ability;
        p.icon = Tags.mark(world.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(ab.icon));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setGlowColorOverride(ab.color());
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(0.65f, 0.65f, 0.65f), new Quaternionf()));
        }));
        p.shell = Tags.mark(world.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.GLASS));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(1.05f, 1.05f, 1.05f), new Quaternionf()));
        }));
        p.label = text(at.clone().add(0, 1.0, 0), Component.empty()
                .append(Fonts.lab("◇ " + ab.code + " ◇", ab.rgb).decorate(TextDecoration.BOLD))
                .append(Component.newline())
                .append(Fonts.lab(ab.displayName, 0xCFE3EC)), 0.75f, true);
        entities.add(p.icon);
        entities.add(p.shell);
        p.box = BoundingBox.of(at, 0.55, 0.7, 0.55);
        pickups.add(p);
    }

    private void spawnSwitch(StageObject o) {
        Switch s = new Switch();
        s.def = o;
        Location at = o.pos.toLocation(world);
        s.box = BoundingBox.of(at.clone().add(0, 0.8, 0), 0.6, 1.0, 0.6);
        s.label = text(at.clone().add(0, 2.1, 0), Fonts.lab("◉ SWITCH", 0xFFB300), 0.8f, true);
        ItemDisplay icon = Tags.mark(world.spawn(at.clone().add(0, 1.0, 0), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.LEVER));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setBillboard(Display.Billboard.VERTICAL);
        }));
        entities.add(icon);
        switches.add(s);
    }

    private void spawnLaser(StageObject o) {
        Laser l = new Laser();
        l.def = o;
        l.box = new BoundingBox(o.min.x(), o.min.y(), o.min.z(), o.max.x(), o.max.y(), o.max.z());
        Location at = new Location(world, o.min.x(), o.min.y(), o.min.z());
        float sx = (float) Math.max(0.05, l.box.getWidthX());
        float sy = (float) Math.max(0.05, l.box.getHeight());
        float sz = (float) Math.max(0.05, l.box.getWidthZ());
        l.beam = Tags.mark(world.spawn(at, BlockDisplay.class, d -> {
            d.setBlock(Material.AIR.createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(0xFF1E3C));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(sx, sy, sz), new Quaternionf()));
        }));
        entities.add(l.beam);
        lasers.add(l);
    }

    private void spawnPad(StageObject o) {
        Pad p = new Pad();
        p.def = o;
        Location at = o.pos.toLocation(world);
        p.box = new BoundingBox(at.getX() - 0.6, at.getY() - 0.2, at.getZ() - 0.6, at.getX() + 0.6, at.getY() + 0.5, at.getZ() + 0.6);
        text(at.clone().add(0, 0.6, 0), Fonts.lab("⇧", 0x7CFF4F).decorate(TextDecoration.BOLD), 1.4f, false);
        pads.add(p);
    }

    // ------------------------------------------------------------------ tick

    /** @return 이번 틱에 캐릭터가 죽었다면 사유, 아니면 null */
    public DeathReason tick(Character c) {
        tick++;
        BoundingBox cb = c.box();

        // 캡슐 애니메이션 + 획득
        for (Pickup p : pickups) {
            if (p.taken) continue;
            if (tick % 10 == 1) {
                p.angle += (float) (Math.PI / 2);
                float bob = (float) (Math.sin(tick * 0.1) * 0.12);
                Transformation t = new Transformation(new Vector3f(0, bob, 0),
                        new Quaternionf().rotateY(p.angle), new Vector3f(0.65f, 0.65f, 0.65f), new Quaternionf());
                p.icon.setInterpolationDelay(0);
                p.icon.setInterpolationDuration(10);
                p.icon.setTransformation(t);
            }
            if (tick % 6 == 0) {
                game.fx().dust(p.icon.getLocation(), p.def.ability.rgb, 0.8f, 2, 0.45);
            }
            if (cb.overlaps(p.box)) {
                p.taken = true;
                Location where = p.icon.getLocation();
                p.icon.remove();
                p.shell.remove();
                p.label.remove();
                game.onAbilityPickup(p.def.ability, p.def.slot, where);
            }
        }

        // 스위치
        for (Switch s : switches) {
            if (s.active) continue;
            if (cb.overlaps(s.box)) {
                s.active = true;
                Block b = s.def.target.toLocation(world).getBlock();
                record(b);
                b.setType(Material.REDSTONE_BLOCK, true);
                s.label.text(Fonts.lab("◉ ACTIVE", 0x5CFF8A));
                game.fx().sound("block.lever.click", 1f, 0.7f);
                game.fx().sound("block.beacon.activate", 0.6f, 1.8f);
                game.fx().particle(Particle.ELECTRIC_SPARK, s.box.getCenter().toLocation(world), 20, 0.4, 0.2);
                game.camera().shake(0.15);
            }
        }

        // 점프 패드
        for (Pad p : pads) {
            if (tick % 4 == 0) {
                game.fx().dust(p.def.pos.toLocation(world).add(0, 0.2, 0), 0x7CFF4F, 1.0f, 3, 0.3);
            }
            if (cb.overlaps(p.box) && c.body().getVelocity().getY() <= 0.05) {
                c.bounce(p.def.power);
                game.fx().sound("block.slime_block.fall", 1f, 0.6f);
                game.fx().sound("entity.breeze.jump", 1f, 1.2f);
                game.fx().particle(Particle.CLOUD, p.def.pos.toLocation(world).add(0, 0.3, 0), 10, 0.3, 0.05);
            }
        }

        // 레이저
        for (Laser l : lasers) {
            int phase = Math.floorMod(tick + l.def.offset, l.def.period);
            boolean on = phase < l.def.onTicks;
            int untilOn = l.def.period - phase;
            if (on != l.on) {
                l.on = on;
                l.beam.setBlock(on ? Material.RED_STAINED_GLASS.createBlockData() : Material.AIR.createBlockData());
                if (on) game.fx().sound("block.beacon.power_select", 0.5f, 2.0f);
            }
            if (!on && untilOn <= 14 && tick % 2 == 0) {
                // 켜지기 직전 경고 파티클
                Location a = new Location(world, l.box.getCenterX(), l.box.getMinY(), l.box.getCenterZ());
                Location b = new Location(world, l.box.getCenterX(), l.box.getMaxY(), l.box.getCenterZ());
                game.fx().line(a, b, 0xFF1E3C, 0.6f, 0.4);
            }
            if (on && cb.overlaps(l.box)) return DeathReason.LASER;
        }

        // 골 연출
        if (goal != null && tick % 3 == 0) {
            double x = goal.getMinX() + Math.random() * goal.getWidthX();
            double z = goal.getMinZ() + Math.random() * goal.getWidthZ();
            game.fx().particle(Particle.END_ROD, new Location(world, x, goal.getMinY() + 0.1, z), 1, 0.0, 0.03);
        }
        if (goalLabel != null && tick % 20 == 0) {
            goalLabel.setInterpolationDelay(0);
            goalLabel.setInterpolationDuration(20);
            float s = (tick / 20) % 2 == 0 ? 1.75f : 1.6f;
            goalLabel.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(s, s, s), new Quaternionf()));
        }

        DeathReason mech = tickMechanisms(c);
        if (mech != null) return mech;

        // 즉사 블록
        if (touchesLethal(cb)) return DeathReason.HAZARD;
        if (c.y() < def.killY) return DeathReason.FALL;
        return null;
    }


    // ------------------------------------------------------------------ 톱날 / 가시 / 스프링

    private static final double SPRITE_DEPTH = -0.22;   // 카메라 쪽으로 살짝 (캐릭터 앞에 그려짐)
    private static final double RAIL_DEPTH = 0.18;      // 레일은 뒤쪽

    private void spawnSaw(StageObject o) {
        Saw sw = new Saw();
        sw.def = o;
        if (o.path.size() >= 2) {
            int n = o.path.size() + (o.loop ? 1 : 0);
            sw.pts = new double[n][2];
            for (int i = 0; i < o.path.size(); i++) {
                Vec v = o.path.get(i);
                sw.pts[i][0] = frame.u(v.toVector());
                sw.pts[i][1] = v.y();
            }
            if (o.loop) sw.pts[n - 1] = sw.pts[0].clone();
            sw.cum = new double[n];
            for (int i = 1; i < n; i++) {
                sw.cum[i] = sw.cum[i - 1] + Math.hypot(sw.pts[i][0] - sw.pts[i - 1][0], sw.pts[i][1] - sw.pts[i - 1][1]);
            }
            sw.total = sw.cum[n - 1];
            sw.moving = sw.total > 0.01 && o.speed != 0;
            if (o.track) {
                for (int i = 1; i < n; i++) rail(sw.pts[i - 1][0], sw.pts[i - 1][1], sw.pts[i][0], sw.pts[i][1]);
                for (int i = 0; i < o.path.size(); i++) entities.add(Sprites.spawn(frame, sw.pts[i][0], sw.pts[i][1], RAIL_DEPTH - 0.02, "hub",
                        Sprites.tf(0, 0, 0, 0.45, 0.45)));
            }
        } else {
            sw.cu = frame.u(o.pos.toVector());
            sw.cy = o.pos.y();
            sw.moving = o.radius > 0;
            if (o.radius > 0 && o.track) {
                int seg = Math.max(12, (int) (o.radius * 6));
                for (int i = 0; i < seg; i++) {
                    double a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
                    rail(sw.cu + Math.cos(a0) * o.radius, sw.cy + Math.sin(a0) * o.radius,
                            sw.cu + Math.cos(a1) * o.radius, sw.cy + Math.sin(a1) * o.radius);
                }
                entities.add(Sprites.spawn(frame, sw.cu, sw.cy, RAIL_DEPTH - 0.02, "hub", Sprites.tf(0, 0, 0, 0.8, 0.8)));
            }
        }
        position(sw, 0);
        sw.e = Sprites.spawn(frame, sw.u, sw.y, SPRITE_DEPTH, "saw", Sprites.tf(0, 0, 0, o.size, o.size));
        sw.e.setTeleportDuration(1);
        entities.add(sw.e);
        saws.add(sw);
    }

    private void rail(double u0, double y0, double u1, double y1) {
        double len = Math.hypot(u1 - u0, y1 - y0);
        if (len < 0.01) return;
        double ang = Math.atan2(y1 - y0, u1 - u0);
        entities.add(Sprites.spawn(frame, (u0 + u1) / 2, (y0 + y1) / 2, RAIL_DEPTH, "rail",
                Sprites.tf(0, 0, ang, len + 0.12, 1.0)));
    }

    /** t 틱 시점의 톱날 위치 계산. */
    private void position(Saw sw, int t) {
        StageObject o = sw.def;
        if (sw.pts != null) {
            if (sw.total <= 0.01) {
                sw.u = sw.pts[0][0];
                sw.y = sw.pts[0][1];
                return;
            }
            double cycle = o.loop ? sw.total : sw.total * 2;
            double d = t * Math.abs(o.speed) + o.phase * cycle;
            d = ((d % cycle) + cycle) % cycle;
            if (!o.loop && d > sw.total) d = cycle - d;
            if (o.speed < 0) d = sw.total - d;
            int i = 1;
            while (i < sw.cum.length - 1 && sw.cum[i] < d) i++;
            double segLen = sw.cum[i] - sw.cum[i - 1];
            double k = segLen <= 0 ? 0 : (d - sw.cum[i - 1]) / segLen;
            sw.u = sw.pts[i - 1][0] + (sw.pts[i][0] - sw.pts[i - 1][0]) * k;
            sw.y = sw.pts[i - 1][1] + (sw.pts[i][1] - sw.pts[i - 1][1]) * k;
        } else if (o.radius > 0) {
            double a = Math.toRadians(o.phase + t * o.orbit);
            sw.u = sw.cu + Math.cos(a) * o.radius;
            sw.y = sw.cy + Math.sin(a) * o.radius;
        } else {
            sw.u = sw.cu;
            sw.y = sw.cy;
        }
    }

    private void spawnSpikes(StageObject o) {
        Spikes sp = new Spikes();
        sp.def = o;
        // pos = 첫 칸의 블록 좌표(모서리). 칸 중심 기준으로 길이만큼 이어진다.
        Vec c = new Vec(Math.floor(o.pos.x()) + 0.5, Math.floor(o.pos.y()), Math.floor(o.pos.z()) + 0.5);
        double cu = frame.u(c.toVector());
        double y0 = c.y();
        boolean horizontal = o.dir == StageObject.Dir.UP || o.dir == StageObject.Dir.DOWN;
        double angle = switch (o.dir) {
            case UP -> 0;
            case DOWN -> Math.PI;
            case LEFT -> Math.PI / 2;
            case RIGHT -> -Math.PI / 2;
        };
        for (int i = 0; i < o.length; i++) {
            double u = horizontal ? cu + i : cu;
            double y = horizontal ? y0 + 0.5 : y0 + 0.5 + i;
            entities.add(Sprites.spawn(frame, u, y, SPRITE_DEPTH, "spike", Sprites.tf(0, 0, angle, 1.0, 1.0)));
        }
        double last = o.length - 1;
        switch (o.dir) {
            case UP -> { sp.u0 = cu - 0.38; sp.u1 = cu + last + 0.38; sp.y0 = y0; sp.y1 = y0 + 0.42; }
            case DOWN -> { sp.u0 = cu - 0.38; sp.u1 = cu + last + 0.38; sp.y0 = y0 + 0.58; sp.y1 = y0 + 1.0; }
            case LEFT -> { sp.u0 = cu + 0.08; sp.u1 = cu + 0.5; sp.y0 = y0 + 0.12; sp.y1 = y0 + last + 0.88; }
            case RIGHT -> { sp.u0 = cu - 0.5; sp.u1 = cu - 0.08; sp.y0 = y0 + 0.12; sp.y1 = y0 + last + 0.88; }
        }
        spikes.add(sp);
    }

    private void spawnSpring(StageObject o) {
        Spring sp = new Spring();
        sp.def = o;
        Vec c = new Vec(Math.floor(o.pos.x()) + 0.5, Math.floor(o.pos.y()), Math.floor(o.pos.z()) + 0.5);
        sp.cu = frame.u(c.toVector());
        sp.y0 = c.y();
        sp.e = Sprites.spawn(frame, sp.cu, sp.y0 + 0.5, SPRITE_DEPTH, "spring", springTf(o.dir, 1.0));
        entities.add(sp.e);
        springs.add(sp);
    }

    /** 스프링 스프라이트 변환. squash < 1 이면 바닥(붙은 면) 기준으로 눌림. */
    private static org.bukkit.util.Transformation springTf(StageObject.Dir dir, double squash) {
        double off = -(1 - squash) * 0.5;
        return switch (dir) {
            case LEFT -> Sprites.tf(-off, 0, Math.PI / 2, 1.0, squash);
            case RIGHT -> Sprites.tf(off, 0, -Math.PI / 2, 1.0, squash);
            default -> Sprites.tf(0, off, 0, 1.0, squash);
        };
    }

    /** 평면 좌표에서 캐릭터 박스와 원의 충돌. */
    private static boolean circleHits(double cu, double cy, double charU, double charY, double r) {
        double dx = Math.max(Math.max(charU - Character.WIDTH / 2 - cu, 0), cu - (charU + Character.WIDTH / 2));
        double dy = Math.max(Math.max(charY - cy, 0), cy - (charY + Character.HEIGHT));
        return dx * dx + dy * dy < r * r;
    }

    private static boolean boxHits(double u0, double u1, double y0, double y1, double charU, double charY) {
        return charU + Character.WIDTH / 2 > u0 && charU - Character.WIDTH / 2 < u1
                && charY + Character.HEIGHT > y0 && charY < y1;
    }

    private DeathReason tickMechanisms(Character c) {
        double cu = c.u(), cy = c.y();
        DeathReason kill = null;

        // 톱날
        double nearest = Double.MAX_VALUE;
        for (Saw sw : saws) {
            if (sw.moving) {
                position(sw, tick);
                sw.e.teleport(Sprites.place(frame, sw.u, sw.y, SPRITE_DEPTH));
            }
            if (tick % 2 == 0) {
                sw.angle -= (float) Math.toRadians(52);
                sw.e.setInterpolationDelay(0);
                sw.e.setInterpolationDuration(2);
                sw.e.setTransformation(Sprites.tf(0, 0, sw.angle, sw.def.size, sw.def.size));
            }
            if (kill == null && circleHits(sw.u, sw.y, cu, cy, sw.def.size * 0.42)) kill = DeathReason.SAW;
            nearest = Math.min(nearest, Math.hypot(sw.u - cu, sw.y - (cy + 0.9)));
        }
        if (nearest < 6 && tick % 8 == 0) {
            game.fx().sound("block.grindstone.use", (float) (0.35 * (1 - nearest / 6)), 1.7f);
        }

        // 가시
        for (Spikes sp : spikes) {
            if (kill == null && boxHits(sp.u0, sp.u1, sp.y0, sp.y1, cu, cy)) kill = DeathReason.SPIKE;
        }

        // 스프링
        for (Spring sp : springs) {
            if (sp.cooldown > 0) sp.cooldown--;
            if (sp.pressed > 0 && --sp.pressed == 0) {
                sp.e.setItemStack(Sprites.item("spring"));
                sp.e.setInterpolationDelay(0);
                sp.e.setInterpolationDuration(4);
                sp.e.setTransformation(springTf(sp.def.dir, 1.0));
            }
            if (sp.cooldown > 0 || kill != null) continue;
            double vy = c.body().getVelocity().getY();
            boolean hit = switch (sp.def.dir) {
                case LEFT, RIGHT -> boxHits(sp.cu - 0.5, sp.cu + 0.5, sp.y0 + 0.05, sp.y0 + 0.95, cu, cy);
                default -> vy <= 0.05 && boxHits(sp.cu - 0.45, sp.cu + 0.45, sp.y0, sp.y0 + 0.65, cu, cy);
            };
            if (!hit) continue;
            switch (sp.def.dir) {
                case LEFT -> c.launch(-sp.def.power * 0.62, 0.45, 16);
                case RIGHT -> c.launch(sp.def.power * 0.62, 0.45, 16);
                default -> c.bounce(sp.def.power);
            }
            sp.cooldown = 8;
            sp.pressed = 3;
            sp.e.setItemStack(Sprites.item("spring_pressed"));
            sp.e.setInterpolationDuration(0);
            sp.e.setTransformation(springTf(sp.def.dir, 0.7));
            game.fx().sound("entity.slime.jump", 0.9f, 1.5f);
            game.fx().sound("block.piston.extend", 0.5f, 1.8f);
            game.fx().particle(Particle.CLOUD, frame.at(sp.cu, sp.y0 + 0.3), 5, 0.2, 0.03);
            game.camera().shake(0.06);
        }

        // 안내 힌트
        for (Hint h : hints) {
            if (!h.shown && c.box().overlaps(h.box)) {
                h.shown = true;
                game.hud().showHint(Fonts.lab(h.def.text, 0xE6F7FF), 90);
                game.fx().sound("block.note_block.chime", 0.6f, 1.6f);
            }
        }
        return kill;
    }

    private boolean touchesLethal(BoundingBox b) {
        int x0 = (int) Math.floor(b.getMinX() + 0.05), x1 = (int) Math.floor(b.getMaxX() - 0.05);
        int y0 = (int) Math.floor(b.getMinY() - 0.05), y1 = (int) Math.floor(b.getMaxY() - 0.05);
        int z0 = (int) Math.floor(b.getMinZ() + 0.05), z1 = (int) Math.floor(b.getMaxZ() - 0.05);
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    if (game.config().lethalBlocks.contains(world.getBlockAt(x, y, z).getType())) return true;
        return false;
    }

    public boolean reachedGoal(Character c) {
        return goal != null && c.box().overlaps(goal);
    }

    public Vec goalCenter() {
        return goal == null ? null : new Vec(goal.getCenterX(), goal.getMinY(), goal.getCenterZ());
    }

    // ------------------------------------------------------------------ blocks

    public boolean isBreakable(Block b) {
        return game.config().breakableBlocks.contains(b.getType());
    }

    /** 부술 수 있는 블록이면 부수고 true. 스테이지 재시작 시 복구된다. */
    public boolean breakBlock(Block b) {
        if (!isBreakable(b)) return false;
        BlockData data = b.getBlockData();
        record(b);
        b.setType(Material.AIR, false);
        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        world.spawnParticle(Particle.BLOCK, c, 24, 0.3, 0.3, 0.3, 0.1, data, true);
        game.fx().sound("block.glass.break", 0.8f, 0.8f);
        return true;
    }

    private void record(Block b) {
        Location key = b.getLocation();
        restore.putIfAbsent(key, b.getBlockData().clone());
    }

    public void cleanup() {
        for (Entity e : entities) if (e.isValid()) e.remove();
        entities.clear();
        List<Map.Entry<Location, BlockData>> list = new ArrayList<>(restore.entrySet());
        for (int i = list.size() - 1; i >= 0; i--) {
            Map.Entry<Location, BlockData> en = list.get(i);
            en.getKey().getBlock().setBlockData(en.getValue(), true);
        }
        restore.clear();
    }

    public World world() {
        return world;
    }
}
