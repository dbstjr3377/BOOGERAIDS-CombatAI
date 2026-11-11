package sim;

import com.boogeraids.combatai.ability.*;
import com.boogeraids.combatai.config.HudSettings;
import com.boogeraids.combatai.hud.Hud;
import com.boogeraids.combatai.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.*;
import java.lang.reflect.*;
import java.util.*;
import org.bukkit.util.Vector;

public class Harness {
    static final Map<Object, Map<String, Object>> state = new LinkedHashMap<>();
    static int fails = 0;

    static Object proxy(Class<?> c, Location at) {
        Map<String, Object> st = new HashMap<>();
        st.put("type", c.getSimpleName());
        Object[] self = new Object[1];
        self[0] = Proxy.newProxyInstance(Harness.class.getClassLoader(), new Class[]{c}, (p, m, a) -> {
            String n = m.getName();
            switch (n) {
                case "getLocation": return at.clone();
                case "isValid": return true;
                case "addPassenger": case "teleport": return true;
                case "hashCode": return System.identityHashCode(p);
                case "equals": return p == a[0];
                case "toString": return c.getSimpleName();
                case "getScoreboardTags": return new HashSet<String>();
            }
            if (a != null && a.length == 1 && (n.startsWith("set") || n.equals("text"))) st.put(n, a[0]);
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r.isPrimitive() && r != void.class) return 0;
            return null;
        });
        state.put(self[0], st);
        return self[0];
    }

    static World world() {
        return (World) Proxy.newProxyInstance(Harness.class.getClassLoader(), new Class[]{World.class}, (p, m, a) -> {
            if (m.getName().equals("spawn")) {
                Object e = proxy((Class<?>) a[1], (Location) a[0]);
                @SuppressWarnings("unchecked") java.util.function.Consumer<Object> f = (java.util.function.Consumer<Object>) a[2];
                f.accept(e);
                return e;
            }
            return null;
        });
    }

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  PASS " : "  FAIL ") + name + (detail.isEmpty() ? "" : "  — " + detail));
        if (!ok) fails++;
    }

    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-6; }

    static String flat(Component c) {
        if (c == null) return "";
        StringBuilder sb = new StringBuilder(c.text);
        for (Component ch : c.children) sb.append(flat(ch));
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        World w = world();

        System.out.println("[1] PlaneFrame 좌표계");
        int[][] expect = {{180, 1, 0, 0, 0, 0, -1}, {0, -1, 0, 0, 0, 0, 1}, {90, 0, 0, -1, -1, 0, 0}, {270, 0, 0, 1, 1, 0, 0}};
        for (int[] e : expect) {
            PlaneFrame f = new PlaneFrame(w, new Vector(10, 64, 20), e[0]);
            check("facing " + e[0] + " right/forward", near(f.right.x, e[1]) && near(f.right.z, e[3]) && near(f.forward.x, e[4]) && near(f.forward.z, e[6]),
                    "right=" + f.right + " forward=" + f.forward);
            // 오른쪽을 보는 yaw 의 방향벡터가 right 와 같아야 함
            Location l = new Location(w, 0, 0, 0, f.yawFacing(1), 0);
            Vector d = l.getDirection();
            check("facing " + e[0] + " 캐릭터가 오른쪽을 볼 때 방향", near(d.x, f.right.x) && near(d.z, f.right.z), "dir=" + d);
            // u 왕복
            Location p = f.at(3.5, 70);
            check("facing " + e[0] + " u(at(3.5)) == 3.5", near(f.u(p), 3.5) && near(f.depth(p), 0), "u=" + f.u(p));
            // 카메라는 평면 앞(-forward 쪽)에 있어야 함
            double yaw = Math.toRadians(e[0]);
            Vector look = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
            check("facing " + e[0] + " 카메라 시선 == forward", near(look.x, f.forward.x) && near(look.z, f.forward.z), "look=" + look);
        }

        System.out.println("[2] Ease");
        check("outCubic(0)=0, (1)=1", near(Ease.outCubic(0), 0) && near(Ease.outCubic(1), 1), "");
        double v = 0; for (int i = 0; i < 60; i++) v = Ease.approach(v, 10, 0.14);
        check("approach 60틱 후 목표의 99% 이상", v > 9.9, String.format("v=%.4f", v));

        System.out.println("[3] HUD 레이아웃 (FOV 70, 16:9, 거리 1.0)");
        HudSettings hs = new HudSettings();
        ItemDisplay cam = (ItemDisplay) proxy(ItemDisplay.class, new Location(w, 0, 100, 0));
        Hud hud = new Hud(cam, hs);
        hud.setSubject("dbstjr3377", "Steve_Lab");
        hud.setStage("CHAPTER 01 // 격리 구역", "STAGE 1-1  초기 반응 테스트");
        boolean bossMode = args.length > 0 && args[0].equals("boss");
        if (bossMode) {
            hud.setTimerVisible(false);
            hud.setBoss(true, "SENTINEL-00", 1.0);
            hud.setBossHealth(0.62, false);
            for (int i = 0; i < 30; i++) hud.tick();
        } else hud.setTimer(20 * 47 + 13, false);
        AbilitySlotState[] slots = {new AbilitySlotState(), new AbilitySlotState()};
        slots[0].ability = Ability.DASH;
        slots[1].ability = Ability.PLASMA; slots[1].cooldown = 6;
        if (args.length > 0 && args[0].equals("boss")) { slots[0].ability = Ability.PLASMA; slots[1].ability = Ability.BOOST; slots[1].cooldown = 0; slots[0].cooldown = 6; }
        hud.updateSlots(slots);
        if (bossMode) {
            hud.setStage("CHAPTER 01 // 격리 구역", "SPECIMEN-00 // 관리 AI 감시체");
            hud.showTitle(Fonts.lab("+ BOOST", 0x7CFF4F), Fonts.lab("부스트 점프 — 공중에서도 한 번 더 도약  [우클릭]", 0xE6F7FF), 40, 12);
        } else hud.showTitle(Fonts.lab("STAGE 1-1  초기 반응 테스트", 0xFFFFFF), Fonts.lab("CHAPTER 01 // 격리 구역", 0x00E5FF), 40, 15);
        for (int i = 0; i < 4; i++) hud.tick();   // 타이틀 팝 보간 완료

        Map<Object, String> names = new HashMap<>();
        for (Field fd : Hud.class.getDeclaredFields()) {
            fd.setAccessible(true);
            Object val = fd.get(hud);
            if (val != null && state.containsKey(val)) names.put(val, fd.getName());
            if (val instanceof Object[] arr) {
                for (int i = 0; i < arr.length; i++) {
                    if (arr[i] == null) continue;
                    for (Field hf : arr[i].getClass().getDeclaredFields()) {
                        hf.setAccessible(true);
                        Object hv = hf.get(arr[i]);
                        if (hv != null && state.containsKey(hv)) names.put(hv, "holder" + i + "." + hf.getName());
                    }
                }
            }
        }
        StringBuilder json = new StringBuilder("[\n");
        boolean first = true;
        for (var en : state.entrySet()) {
            Map<String, Object> st = en.getValue();
            String name = names.get(en.getKey());
            if (name == null || !st.containsKey("setTransformation")) continue;
            Transformation t = (Transformation) st.get("setTransformation");
            double depth = -t.t.z;
            double hw = hs.halfWidth(depth), hh = hs.halfHeight(depth);
            Component c = (Component) st.get("text");
            String txt = flat(c);
            String font = "";
            Integer color = null;
            Deque<Component> q = new ArrayDeque<>(); if (c != null) q.add(c);
            List<String> parts = new ArrayList<>();
            while (!q.isEmpty()) { Component x = q.poll(); if (x.font != null && font.isEmpty()) font = x.font; if (color == null && x.color != null) color = x.color;
                if (!x.text.isEmpty()) parts.add("{\"t\":" + js(x.text) + ",\"c\":" + (x.color == null ? -1 : x.color) + ",\"f\":" + js(x.font == null ? "" : x.font) + ",\"b\":" + x.bold + "}");
                q.addAll(x.children); }
            Color bg = (Color) st.get("setBackgroundColor");
            Object align = st.get("setAlignment");
            Object item = st.get("setItemStack");
            json.append(first ? "" : ",\n").append(String.format(Locale.ROOT,
                    "{\"name\":\"%s\",\"type\":\"%s\",\"sx\":%.4f,\"sy\":%.4f,\"depth\":%.4f,\"scaleX\":%.5f,\"scaleY\":%.5f,\"hh\":%.5f,\"parts\":[%s],\"bg\":%s,\"align\":\"%s\",\"item\":%s}",
                    name, st.get("type"), t.t.x / hw, t.t.y / hh, depth, t.s.x, t.s.y, hh, String.join(",", parts),
                    bg == null ? "null" : "[" + bg.a + "," + bg.r + "," + bg.g + "," + bg.b + "]", align == null ? "CENTER" : align,
                    item == null ? "null" : js(String.valueOf(((org.bukkit.inventory.ItemStack) item) == null ? "" : itemName(st)))));
            first = false;
            if (!txt.isEmpty() || item != null) {
                boolean inside = Math.abs(t.t.x / hw) <= 1.0 && Math.abs(t.t.y / hh) <= 1.0;
                check("HUD 요소 '" + name + "' 원점이 화면 안", inside || name.equals("frameOverlay") || name.equals("vignette"),
                        String.format(Locale.ROOT, "sx=%.3f sy=%.3f", t.t.x / hw, t.t.y / hh));
            }
        }
        json.append("\n]");
        java.nio.file.Files.writeString(java.nio.file.Path.of(args.length > 0 ? "/tmp/test/hud_" + args[0] + ".json" : "/tmp/test/hud.json"), json.toString());
        System.out.println("\n결과: " + (fails == 0 ? "전체 통과" : fails + "개 실패"));
    }

    static String itemName(Map<String, Object> st) { return "item"; }

    static String js(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
