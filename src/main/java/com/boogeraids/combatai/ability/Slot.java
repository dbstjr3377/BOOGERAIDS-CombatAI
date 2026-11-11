package com.boogeraids.combatai.ability;

public enum Slot {
    LEFT, RIGHT, AUTO;

    public static Slot parse(String s) {
        if (s == null) return AUTO;
        return switch (s.trim().toUpperCase()) {
            case "LEFT", "L", "왼쪽", "좌클릭" -> LEFT;
            case "RIGHT", "R", "오른쪽", "우클릭" -> RIGHT;
            default -> AUTO;
        };
    }

    public int index() {
        return this == RIGHT ? 1 : 0;
    }
}
