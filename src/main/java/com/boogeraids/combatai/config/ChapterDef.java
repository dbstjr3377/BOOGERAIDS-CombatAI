package com.boogeraids.combatai.config;

import java.util.List;

public final class ChapterDef {
    public String id;
    public String name;
    public List<StageDef> stages;
    public StageDef boss;

    /** 보스 스테이지를 마지막에 포함한 전체 순서. */
    public List<StageDef> sequence() {
        if (boss == null) return stages;
        java.util.ArrayList<StageDef> l = new java.util.ArrayList<>(stages);
        l.add(boss);
        return l;
    }
}
