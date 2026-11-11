package com.boogeraids.combatai.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 리소스팩 아이템 모델(boogeraids:&lt;name&gt;)을 화면 평면에 붙인 2D 스프라이트.
 * 엔티티 yaw = facing + 180 으로 두면 로컬 +X = 화면 오른쪽, +Y = 위, +Z = 카메라 쪽이 되어
 * Z축 회전이 곧 화면 위 회전이 된다.
 */
public final class Sprites {
    private Sprites() {}

    public static ItemStack item(String name) {
        ItemStack s = new ItemStack(Material.PAPER);
        ItemMeta m = s.getItemMeta();
        m.setItemModel(NamespacedKey.fromString("boogeraids:" + name));
        s.setItemMeta(m);
        return s;
    }

    /** 평면 좌표 (u, y) 에 스프라이트 생성. depth < 0 이면 카메라 쪽으로 당겨진다. */
    public static ItemDisplay spawn(PlaneFrame f, double u, double y, double depth, String name, Transformation tf) {
        Location at = place(f, u, y, depth);
        return Tags.mark(f.world.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(item(name));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(2f);
            d.setShadowRadius(0f);
            d.setTransformation(tf);
        }));
    }

    public static Location place(PlaneFrame f, double u, double y, double depth) {
        Location l = f.at(u, y).add(f.forward.clone().multiply(depth));
        l.setYaw(f.facing + 180f);
        l.setPitch(0f);
        return l;
    }

    /** 로컬 평면에서: (tx, ty) 이동 → Z축 회전 → 스케일 (sx, sy). */
    public static Transformation tf(double tx, double ty, double angleRad, double sx, double sy) {
        return new Transformation(new Vector3f((float) tx, (float) ty, 0f),
                new Quaternionf().rotateZ((float) angleRad),
                new Vector3f((float) sx, (float) sy, (float) Math.max(sx, sy)), new Quaternionf());
    }
}
