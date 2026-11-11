package com.boogeraids.combatai.util;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/** 플러그인이 생성한 엔티티 표시용 스코어보드 태그. */
public final class Tags {
    private Tags() {}

    public static final String ENTITY = "boogeraids";

    public static <T extends Entity> T mark(T e) {
        e.addScoreboardTag(ENTITY);
        e.setPersistent(false);
        return e;
    }

    /** 서버 크래시 등으로 남은 엔티티 정리. */
    public static int sweep() {
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getScoreboardTags().contains(ENTITY)) {
                    e.remove();
                    n++;
                }
            }
        }
        return n;
    }
}
