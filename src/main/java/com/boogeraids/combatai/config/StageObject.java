package com.boogeraids.combatai.config;

import com.boogeraids.combatai.ability.Ability;
import com.boogeraids.combatai.ability.Slot;

import java.util.LinkedHashMap;
import java.util.Map;

/** 스테이지 위에 배치되는 기믹 오브젝트 정의. */
public final class StageObject {

    public enum Type { ABILITY, SWITCH, LASER, JUMPPAD, SIGN }

    public Type type;
    public Vec pos;
    public Vec min;
    public Vec max;
    public Vec target;
    public Ability ability;
    public Slot slot = Slot.AUTO;
    public double power = 1.0;
    public String text = "";
    public int period = 60;
    public int onTicks = 30;
    public int offset = 0;

    public static StageObject parse(Map<?, ?> map) {
        Object t = map.get("type");
        if (t == null) return null;
        StageObject o = new StageObject();
        try {
            o.type = Type.valueOf(String.valueOf(t).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
        o.pos = Vec.parse(map.get("pos"));
        o.min = Vec.parse(map.get("min"));
        o.max = Vec.parse(map.get("max"));
        o.target = Vec.parse(map.get("target"));
        if (o.min != null && o.max != null) {
            Vec a = Vec.min(o.min, o.max), b = Vec.max(o.min, o.max);
            o.min = a;
            o.max = b;
        }
        if (map.get("ability") != null) o.ability = Ability.parse(String.valueOf(map.get("ability")));
        if (map.get("slot") != null) o.slot = Slot.parse(String.valueOf(map.get("slot")));
        if (map.get("power") instanceof Number n) o.power = n.doubleValue();
        if (map.get("text") != null) o.text = String.valueOf(map.get("text"));
        if (map.get("period") instanceof Number n) o.period = Math.max(2, n.intValue());
        if (map.get("on") instanceof Number n) o.onTicks = Math.max(1, n.intValue());
        if (map.get("offset") instanceof Number n) o.offset = n.intValue();

        boolean ok = switch (o.type) {
            case ABILITY -> o.pos != null && o.ability != null;
            case SWITCH -> o.pos != null && o.target != null;
            case LASER -> o.min != null && o.max != null;
            case JUMPPAD, SIGN -> o.pos != null;
        };
        return ok ? o : null;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type.name().toLowerCase());
        switch (type) {
            case ABILITY -> {
                m.put("ability", ability.name());
                m.put("slot", slot.name());
                m.put("pos", pos.toList());
            }
            case SWITCH -> {
                m.put("pos", pos.toList());
                m.put("target", target.toList());
            }
            case LASER -> {
                m.put("min", min.toList());
                m.put("max", max.toList());
                m.put("period", period);
                m.put("on", onTicks);
                m.put("offset", offset);
            }
            case JUMPPAD -> {
                m.put("pos", pos.toList());
                m.put("power", power);
            }
            case SIGN -> {
                m.put("pos", pos.toList());
                m.put("text", text);
            }
        }
        return m;
    }
}
