package com.boogeraids.combatai.ability;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;

/** 맵 기믹으로 얻을 수 있는 특수능력. */
public enum Ability {
    DASH("DASH", "위상 대시", "진행 방향으로 순간 가속", Material.FEATHER, 36, 0x00E5FF),
    BOOST("BOOST", "부스트 점프", "공중에서도 한 번 더 도약", Material.FIREWORK_ROCKET, 30, 0x7CFF4F),
    BLINK("BLINK", "블링크", "얇은 벽을 통과하는 단거리 순간이동", Material.ENDER_PEARL, 70, 0xB45CFF),
    SHOCKWAVE("SHOCKWAVE", "충격파", "주변의 금 간 블록 파괴 / 보스 피해", Material.HEAVY_CORE, 90, 0xFFB300),
    PLASMA("PLASMA", "플라즈마 탄", "전방으로 에너지탄 발사 (보스 피해)", Material.BLAZE_ROD, 14, 0xFF3D6E);

    public final String code;
    public final String displayName;
    public final String description;
    public final Material icon;
    public final int cooldownTicks;
    public final int rgb;

    Ability(String code, String displayName, String description, Material icon, int cooldownTicks, int rgb) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.cooldownTicks = cooldownTicks;
        this.rgb = rgb;
    }

    public TextColor textColor() {
        return TextColor.color(rgb);
    }

    public Color color() {
        return Color.fromRGB(rgb);
    }

    public static Ability parse(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
