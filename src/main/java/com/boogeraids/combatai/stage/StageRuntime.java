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

        // 즉사 블록
        if (touchesLethal(cb)) return DeathReason.HAZARD;
        if (c.y() < def.killY) return DeathReason.FALL;
        return null;
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
