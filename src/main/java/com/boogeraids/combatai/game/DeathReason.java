package com.boogeraids.combatai.game;

public enum DeathReason {
    TIMEOUT("TIME OUT", "제한시간 초과"),
    FALL("SIGNAL LOST", "추락"),
    HAZARD("HAZARD CONTACT", "위험물 접촉"),
    LASER("LASER GRID", "레이저에 소각됨"),
    BOSS("TERMINATED", "감시체에게 제거됨"),
    ABANDONED("CONNECTION LOST", "피험자 이탈"),
    FORCED("ADMIN OVERRIDE", "관리자에 의해 종료");

    public final String code;
    public final String korean;

    DeathReason(String code, String korean) {
        this.code = code;
        this.korean = korean;
    }
}
