package com.boogeraids.combatai.config;

import com.boogeraids.combatai.ability.Ability;
import com.boogeraids.combatai.ability.Slot;

import java.util.LinkedHashMap;
import java.util.Map;

/** 스테이지 위에 배치되는 기믹 오브젝트 정의. */
public final class StageObject {

    public enum Type { ABILITY, SWITCH, LASER, JUMPPAD, SIGN, SAW, SPIKES, SPRING, HINT }

    /** 가시/스프링 방향. */
    public enum Dir { UP, DOWN, LEFT, RIGHT }

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

    // SAW
    public double size = 1.6;
    public java.util.List<Vec> path = new java.util.ArrayList<>();
    public double speed = 0.1;          // 경로 이동 속도 (블록/틱)
    public boolean loop = false;        // false = 왕복, true = 순환
    public double radius = 0;           // > 0 이면 pos 를 중심으로 공전
    public double orbit = 3.0;          // 공전 각속도 (도/틱, 음수면 반대 방향)
    public double phase = 0;            // 경로: 0~1 진행 비율 / 공전: 시작 각도(도)
    public boolean track = true;        // 레일 표시
    // SPIKES / SPRING
    public int length = 1;
    public Dir dir = Dir.UP;

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
        if (map.get("size") instanceof Number n) o.size = Math.max(0.3, n.doubleValue());
        if (map.get("path") instanceof java.util.List<?> pl) {
            for (Object pt : pl) {
                Vec v = Vec.parse(pt);
                if (v != null) o.path.add(v);
            }
        }
        if (map.get("speed") instanceof Number n) o.speed = n.doubleValue();
        if (map.get("loop") instanceof Boolean b) o.loop = b;
        if (map.get("radius") instanceof Number n) o.radius = n.doubleValue();
        if (map.get("orbit") instanceof Number n) o.orbit = n.doubleValue();
        if (map.get("phase") instanceof Number n) o.phase = n.doubleValue();
        if (map.get("track") instanceof Boolean b) o.track = b;
        if (map.get("length") instanceof Number n) o.length = Math.max(1, n.intValue());
        if (map.get("dir") != null) {
            try {
                o.dir = Dir.valueOf(String.valueOf(map.get("dir")).trim().toUpperCase());
            } catch (IllegalArgumentException ignored) { }
        }
        if (o.type == Type.SPRING && !(map.get("power") instanceof Number)) o.power = 0.95;

        boolean ok = switch (o.type) {
            case ABILITY -> o.pos != null && o.ability != null;
            case SWITCH -> o.pos != null && o.target != null;
            case LASER -> o.min != null && o.max != null;
            case JUMPPAD, SIGN, SPIKES, SPRING -> o.pos != null;
            case SAW -> o.pos != null || o.path.size() >= 2;
            case HINT -> o.min != null && o.max != null;
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
            case SAW -> {
                if (pos != null) m.put("pos", pos.toList());
                if (path.size() >= 2) {
                    java.util.List<Object> l = new java.util.ArrayList<>();
                    for (Vec v : path) l.add(v.toList());
                    m.put("path", l);
                    m.put("speed", speed);
                    m.put("loop", loop);
                }
                if (radius > 0) {
                    m.put("radius", radius);
                    m.put("orbit", orbit);
                }
                m.put("phase", phase);
                m.put("size", size);
                m.put("track", track);
            }
            case SPIKES -> {
                m.put("pos", pos.toList());
                m.put("length", length);
                m.put("dir", dir.name());
            }
            case SPRING -> {
                m.put("pos", pos.toList());
                m.put("power", power);
                m.put("dir", dir.name());
            }
            case HINT -> {
                m.put("min", min.toList());
                m.put("max", max.toList());
                m.put("text", text);
            }
        }
        return m;
    }
}
