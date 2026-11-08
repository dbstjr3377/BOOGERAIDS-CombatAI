package com.boogeraids.combatai.ability;

import com.boogeraids.combatai.boss.BossRuntime;
import com.boogeraids.combatai.game.Character;
import com.boogeraids.combatai.game.GameManager;
import com.boogeraids.combatai.stage.StageRuntime;
import com.boogeraids.combatai.util.PlaneFrame;
import com.boogeraids.combatai.util.Tags;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** 특수능력 발동과 투사체(플라즈마) 처리. */
public final class AbilityController {

    private final GameManager game;
    private final List<Shot> shots = new ArrayList<>();

    private static final class Shot {
        double u, y;
        int dir;
        int life;
        ItemDisplay e;
    }

    public AbilityController(GameManager game) {
        this.game = game;
    }

    /** 슬롯의 능력 사용 시도. */
    public void use(Character c, int slotIndex) {
        AbilitySlotState st = c.slots[slotIndex];
        if (st.ability == null) return;
        if (st.cooldown > 0) {
            game.fx().sound("block.note_block.bass", 0.5f, 0.5f);
            return;
        }
        boolean ok = switch (st.ability) {
            case DASH -> dash(c);
            case BOOST -> boost(c);
            case BLINK -> blink(c);
            case SHOCKWAVE -> shockwave(c);
            case PLASMA -> plasma(c);
        };
        if (ok) {
            st.cooldown = st.ability.cooldownTicks;
            st.flash = 0;
            game.hud().pulseHolder(slotIndex);
            c.swing();
        } else {
            game.fx().sound("block.note_block.bass", 0.5f, 0.5f);
        }
    }

    // ------------------------------------------------------------------ abilities

    private boolean dash(Character c) {
        c.dash(7, 0.95);
        game.fx().sound("entity.wind_charge.wind_burst", 0.9f, 1.5f);
        game.fx().sound("entity.breeze.shoot", 0.6f, 1.8f);
        game.camera().shake(0.12);
        return true;
    }

    private boolean boost(Character c) {
        if (!c.boost(0.62)) return false;
        Location at = c.location();
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            game.fx().dust(at.clone().add(Math.cos(a) * 0.6, 0.05, Math.sin(a) * 0.6), Ability.BOOST.rgb, 1.0f, 1, 0);
        }
        game.fx().particle(Particle.CLOUD, at, 6, 0.2, 0.02);
        game.fx().sound("entity.breeze.jump", 1f, 1.4f);
        return true;
    }

    private boolean blink(Character c) {
        PlaneFrame f = game.frame();
        double u0 = c.u(), y = c.y();
        double best = Double.NaN;
        for (double d = 5.0; d >= 1.0; d -= 0.25) {
            double u = u0 + c.facing() * d;
            if (isFree(f, u, y)) {
                best = u;
                break;
            }
        }
        if (Double.isNaN(best)) return false;
        Location from = c.center();
        Location to = f.at(best, y);
        c.teleport(to);
        game.fx().particle(Particle.REVERSE_PORTAL, from, 40, 0.3, 0.05);
        game.fx().particle(Particle.PORTAL, c.center(), 40, 0.3, 0.6);
        game.fx().line(from, c.center(), Ability.BLINK.rgb, 0.8f, 0.3);
        game.fx().sound("entity.enderman.teleport", 1f, 1.3f);
        game.hud().flash(Ability.BLINK.rgb, 0.25, 8);
        return true;
    }

    private boolean isFree(PlaneFrame f, double u, double y) {
        Location base = f.at(u, y);
        BoundingBox box = BoundingBox.of(base.clone().add(0, Character.HEIGHT / 2, 0),
                Character.WIDTH / 2 - 0.02, Character.HEIGHT / 2 - 0.02, Character.WIDTH / 2 - 0.02);
        World w = f.world;
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++)
            for (int yy = (int) Math.floor(box.getMinY()); yy <= (int) Math.floor(box.getMaxY()); yy++)
                for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
                    Block b = w.getBlockAt(x, yy, z);
                    if (!b.isPassable()) return false;
                    if (game.config().lethalBlocks.contains(b.getType())) return false;
                }
        return true;
    }

    private boolean shockwave(Character c) {
        Location ctr = c.center();
        StageRuntime stage = game.stage();
        int r = 3;
        int broken = 0;
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > 12) continue;
                    Block b = ctr.getBlock().getRelative(x, y, z);
                    if (stage != null && stage.breakBlock(b)) broken++;
                }
        BossRuntime boss = game.boss();
        if (boss != null && boss.center().distance(ctr) < 5.5) boss.damage(5.0, boss.center());

        for (int ring = 0; ring < 3; ring++) {
            double rad = 1.0 + ring * 1.1;
            for (int i = 0; i < 20; i++) {
                double a = i * Math.PI / 10;
                Location p = game.frame().at(c.u() + Math.cos(a) * rad, ctr.getY() + Math.sin(a) * rad);
                game.fx().dust(p, Ability.SHOCKWAVE.rgb, 1.3f - ring * 0.3f, 1, 0);
            }
        }
        game.fx().particle(Particle.EXPLOSION, ctr, 1, 0, 0);
        game.fx().sound("item.mace.smash_ground_heavy", 1f, 0.9f);
        game.fx().sound("entity.generic.explode", 0.5f, 1.6f);
        game.camera().shake(0.45 + Math.min(0.3, broken * 0.05));
        game.hud().flash(Ability.SHOCKWAVE.rgb, 0.3, 10);
        return true;
    }

    private boolean plasma(Character c) {
        PlaneFrame f = game.frame();
        Shot s = new Shot();
        s.dir = c.facing();
        s.u = c.u() + s.dir * 0.6;
        s.y = c.y() + 1.15;
        Location at = f.at(s.u, s.y);
        s.e = Tags.mark(at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.FIRE_CHARGE));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setGlowing(true);
            d.setGlowColorOverride(Ability.PLASMA.color());
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.55f, 0.55f, 0.55f), new Quaternionf()));
        }));
        shots.add(s);
        game.fx().sound("entity.blaze.shoot", 0.8f, 1.7f);
        game.fx().sound("block.beacon.power_select", 0.4f, 2.0f);
        return true;
    }

    // ------------------------------------------------------------------ tick

    public void tick(Character c) {
        // 대시 잔상
        if (c.isValid() && c.isDashing()) {
            game.fx().dust(c.center(), Ability.DASH.rgb, 1.4f, 3, 0.25);
            game.fx().particle(Particle.ELECTRIC_SPARK, c.center(), 2, 0.2, 0.02);
        }
        if (shots.isEmpty()) return;
        PlaneFrame f = game.frame();
        BossRuntime boss = game.boss();
        StageRuntime stage = game.stage();
        for (Iterator<Shot> it = shots.iterator(); it.hasNext(); ) {
            Shot s = it.next();
            boolean dead = ++s.life > 40;
            for (int step = 0; step < 2 && !dead; step++) {
                s.u += s.dir * 0.45;
                Location p = f.at(s.u, s.y);
                Block b = p.getBlock();
                if (!b.isPassable()) {
                    if (stage != null) stage.breakBlock(b);
                    dead = true;
                } else if (boss != null && !boss.isDefeated() && boss.hitbox().expand(0.3).contains(p.toVector())) {
                    boss.damage(2.5, p);
                    dead = true;
                }
            }
            Location p = f.at(s.u, s.y);
            if (dead) {
                game.fx().particle(Particle.FLAME, p, 12, 0.15, 0.05);
                game.fx().particle(Particle.SMOKE, p, 6, 0.1, 0.02);
                game.fx().sound("entity.generic.extinguish_fire", 0.5f, 1.6f);
                if (s.e.isValid()) s.e.remove();
                it.remove();
            } else {
                s.e.teleport(p);
                game.fx().dust(p, Ability.PLASMA.rgb, 0.9f, 2, 0.05);
            }
        }
    }

    public void clear() {
        for (Shot s : shots) if (s.e != null && s.e.isValid()) s.e.remove();
        shots.clear();
    }
}
