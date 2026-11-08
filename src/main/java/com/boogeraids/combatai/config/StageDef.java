package com.boogeraids.combatai.config;

import java.util.List;

/** 스테이지(또는 보스 스테이지) 정의. */
public final class StageDef {
    public String chapterId;
    public String id;          // 보스 스테이지는 "boss"
    public String name;
    public int timeSeconds;    // 보스 스테이지는 0 (제한시간 없음)
    public float facing = 180f;
    public Vec spawn;
    public Vec goalMin;
    public Vec goalMax;
    public double killY;
    public CameraSettings camera;
    public List<StageObject> objects;
    public BossSettings boss;

    public boolean isBoss() {
        return boss != null;
    }

    /** 시작 가능한 상태인지 검사, 문제가 있으면 사유 반환. */
    public String validate() {
        if (spawn == null) return "spawn 이 설정되지 않음";
        if (isBoss()) {
            if (!boss.isValid()) return "boss.pos / arena-min / arena-max 가 필요함";
        } else {
            if (goalMin == null || goalMax == null) return "goal-min / goal-max 가 설정되지 않음";
            if (timeSeconds <= 0) return "time 이 0 이하";
        }
        return null;
    }

    public String key() {
        return chapterId + "/" + id;
    }
}
