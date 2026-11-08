package com.boogeraids.combatai.ability;

/** 슬롯 하나(좌클릭/우클릭)에 장착된 능력과 쿨타임. */
public final class AbilitySlotState {
    public Ability ability;
    public int cooldown;      // 남은 틱
    public int flash;         // 사용/획득 시 HUD 강조 틱

    public boolean ready() {
        return ability != null && cooldown <= 0;
    }

    public double cooldownFraction() {
        if (ability == null || cooldown <= 0) return 0;
        return Math.min(1.0, cooldown / (double) ability.cooldownTicks);
    }

    public void clear() {
        ability = null;
        cooldown = 0;
        flash = 0;
    }
}
