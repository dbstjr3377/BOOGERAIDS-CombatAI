package com.boogeraids.combatai.stage;

import com.boogeraids.combatai.BoogerAIDS;
import com.boogeraids.combatai.config.Vec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * levels/*.yml 의 ASCII 타일맵을 월드에 짓고 config.yml 에 챕터를 등록한다.
 *
 * <pre>
 * 범례   #  벽/바닥        =  금속 발판       L  조명 블록       x  부술 수 있는 블록
 *        S  스폰(발 위치)   E  출구(3칸 높이)
 *        ^ v &lt; &gt;  가시 (뾰족한 방향)
 *        *  스프링(보통)    !  스프링(강)      {  왼쪽으로 튕기는 스프링   }  오른쪽으로 튕기는 스프링
 *        . 또는 공백  빈칸
 * </pre>
 * 좌표: 그리드 x = 화면 오른쪽(+X), y = 위. rows 는 위에서 아래 순서.
 * 스테이지는 기준점에서 -Z 방향으로 spacing 만큼 떨어져 차례로 지어진다 (facing 180, 카메라는 +Z 쪽).
 */
public final class LevelBuilder {

    private static final int OPS_PER_TICK = 24000;

    // 연구소 팔레트
    private static final Material SOLID = Material.LIGHT_GRAY_CONCRETE;
    private static final Material SOLID_TOP = Material.WHITE_CONCRETE;
    private static final Material PLATFORM = Material.SMOOTH_STONE;
    private static final Material LIGHT = Material.SEA_LANTERN;
    private static final Material BREAKABLE = Material.CRACKED_STONE_BRICKS;
    private static final Material WALL = Material.GRAY_CONCRETE;
    private static final Material WALL_STRIPE = Material.LIGHT_GRAY_CONCRETE;
    private static final Material WALL_ACCENT = Material.CYAN_TERRACOTTA;
    private static final Material WALL_BACK = Material.BLACK_CONCRETE;
    private static final Material GATE = Material.CYAN_CONCRETE;

    private final BoogerAIDS plugin;

    public LevelBuilder(BoogerAIDS plugin) {
        this.plugin = plugin;
    }

    private record Op(int x, int y, int z, Material m) { }

    private static final class Grid {
        final String[] rows;
        final int w, h;

        Grid(List<String> raw) {
            int width = 0;
            for (String r : raw) width = Math.max(width, r.length());
            this.w = width;
            this.h = raw.size();
            this.rows = new String[h];
            for (int i = 0; i < h; i++) {
                StringBuilder sb = new StringBuilder(raw.get(i));
                while (sb.length() < w) sb.append('.');
                rows[i] = sb.toString();
            }
        }

        /** y = 0 이 맨 아래 줄. 범위 밖은 빈칸. */
        char at(int x, int y) {
            if (x < 0 || y < 0 || x >= w || y >= h) return '.';
            return rows[h - 1 - y].charAt(x);
        }

        boolean solid(int x, int y) {
            char c = at(x, y);
            return c == '#' || c == '=' || c == 'L' || c == 'x';
        }
    }

    public FileConfiguration loadLevel(String name) {
        java.io.File f = new java.io.File(plugin.getDataFolder(), "levels/" + name + ".yml");
        try {
            if (f.isFile()) return YamlConfiguration.loadConfiguration(f);
            var in = plugin.getResource("levels/" + name + ".yml");
            if (in == null) return null;
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(r);
            }
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** origin = 첫 스테이지 그리드 (0,0) 칸의 블록 좌표. */
    public boolean build(CommandSender sender, String levelName, Location origin) {
        FileConfiguration lv = loadLevel(levelName);
        if (lv == null) {
            sender.sendMessage(Component.text("레벨 파일 없음: levels/" + levelName + ".yml", NamedTextColor.RED));
            return false;
        }
        World world = origin.getWorld();
        String chapterId = lv.getString("id", levelName);
        int spacing = lv.getInt("spacing", 48);
        int ox = origin.getBlockX(), oy = origin.getBlockY(), oz = origin.getBlockZ();

        List<Op> ops = new ArrayList<>();
        Map<String, Object> stagesOut = new LinkedHashMap<>();
        Map<String, Object> bossOut = null;

        List<Map<?, ?>> stages = lv.getMapList("stages");
        int index = 0;
        for (Map<?, ?> st : stages) {
            int z = oz - index * spacing;
            Map<String, Object> out = buildStage(st, ox, oy, z, ops, false);
            if (out == null) {
                sender.sendMessage(Component.text("스테이지 " + st.get("id") + " 에 rows 가 없습니다.", NamedTextColor.RED));
                return false;
            }
            stagesOut.put(String.valueOf(st.get("id")), out);
            index++;
        }
        ConfigurationSection boss = lv.getConfigurationSection("boss");
        if (boss != null) {
            int z = oz - index * spacing;
            Map<String, Object> raw = new LinkedHashMap<>();
            for (String k : boss.getKeys(false)) {
                Object v = boss.get(k);
                raw.put(k, v instanceof ConfigurationSection cs ? cs.getValues(true) : v);
            }
            bossOut = buildStage(raw, ox, oy, z, ops, true);
        }

        int minY = world.getMinHeight(), maxY = world.getMaxHeight();
        for (Op op : ops) {
            if (op.y < minY || op.y >= maxY) {
                sender.sendMessage(Component.text("월드 높이 범위를 벗어납니다. 더 낮은/높은 곳에서 실행하세요.", NamedTextColor.RED));
                return false;
            }
        }

        final Map<String, Object> bossFinal = bossOut;
        final String chName = lv.getString("name", chapterId.toUpperCase());
        sender.sendMessage(Component.text("[BC] " + ops.size() + "개 블록 배치 시작 (" + stagesOut.size() + " 스테이지"
                + (bossOut != null ? " + 보스" : "") + ")", NamedTextColor.AQUA));

        final int[] cursor = {0};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            int end = Math.min(ops.size(), cursor[0] + OPS_PER_TICK);
            for (int i = cursor[0]; i < end; i++) {
                Op op = ops.get(i);
                world.getBlockAt(op.x, op.y, op.z).setType(op.m, false);
            }
            cursor[0] = end;
            if (end % (OPS_PER_TICK * 10) < OPS_PER_TICK || end >= ops.size()) {
                sender.sendMessage(Component.text("[BC] 배치 " + (end * 100 / Math.max(1, ops.size())) + "%", NamedTextColor.GRAY));
            }
            if (end >= ops.size()) {
                task[0].cancel();
                writeConfig(chapterId, chName, world.getName(), stagesOut, bossFinal);
                sender.sendMessage(Component.text("[BC] 챕터 " + chapterId + " 건설 완료 → /bc start " + chapterId, NamedTextColor.GREEN));
            }
        }, 1L, 1L);
        return true;
    }

    private void writeConfig(String id, String name, String worldName, Map<String, Object> stages, Map<String, Object> boss) {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("settings.world", worldName);
        cfg.set("chapters." + id, null);
        cfg.set("chapters." + id + ".name", name);
        for (Map.Entry<String, Object> e : stages.entrySet()) {
            cfg.set("chapters." + id + ".stages." + e.getKey(), e.getValue());
        }
        if (boss != null) cfg.set("chapters." + id + ".boss", boss);
        plugin.saveConfig();
        plugin.reloadGameConfig();
    }

    // ------------------------------------------------------------------ stage

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildStage(Map<?, ?> st, int ox, int oy, int oz, List<Op> ops, boolean isBoss) {
        Object rowsRaw = st.get("rows");
        if (!(rowsRaw instanceof List<?> rl) || rl.isEmpty()) return null;
        List<String> rows = new ArrayList<>();
        for (Object o : rl) rows.add(String.valueOf(o));
        Grid g = new Grid(rows);

        // 1) 정리: 스테이지 앞(카메라 공간)과 주변을 비운다
        for (int x = -6; x < g.w + 6; x++)
            for (int y = -10; y < g.h + 8; y++)
                for (int z = -3; z <= 18; z++)
                    ops.add(new Op(ox + x, oy + y, oz + z, Material.AIR));

        // 2) 뒷벽 (z-1: 패턴 벽, z-2: 검은 벽)
        for (int x = -1; x <= g.w; x++) {
            for (int y = -1; y <= g.h; y++) {
                Material m = WALL;
                if (Math.floorMod(x, 6) == 0) m = WALL_STRIPE;
                if (y == 3 || y == g.h - 4) m = WALL_ACCENT;
                if (y == g.h - 3 && Math.floorMod(x, 8) == 4) m = LIGHT;
                ops.add(new Op(ox + x, oy + y, oz - 1, m));
                ops.add(new Op(ox + x, oy + y, oz - 2, WALL_BACK));
            }
        }

        // 3) 타일
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> objects = new ArrayList<>();
        Vec spawn = null;
        int ex0 = Integer.MAX_VALUE, ey0 = Integer.MAX_VALUE, ex1 = Integer.MIN_VALUE, ey1 = Integer.MIN_VALUE;

        for (int y = 0; y < g.h; y++) {
            for (int x = 0; x < g.w; x++) {
                char c = g.at(x, y);
                int wx = ox + x, wy = oy + y;
                switch (c) {
                    case '#' -> {
                        Material m = g.solid(x, y + 1) ? SOLID : SOLID_TOP;
                        ops.add(new Op(wx, wy, oz, m));
                        ops.add(new Op(wx, wy, oz + 1, m));
                    }
                    case '=' -> {
                        ops.add(new Op(wx, wy, oz, PLATFORM));
                        ops.add(new Op(wx, wy, oz + 1, PLATFORM));
                    }
                    case 'L' -> {
                        ops.add(new Op(wx, wy, oz, LIGHT));
                        ops.add(new Op(wx, wy, oz + 1, LIGHT));
                    }
                    case 'x' -> ops.add(new Op(wx, wy, oz, BREAKABLE));
                    case 'S' -> spawn = new Vec(wx + 0.5, wy, oz + 0.5);
                    case 'E' -> {
                        ex0 = Math.min(ex0, x);
                        ey0 = Math.min(ey0, y);
                        ex1 = Math.max(ex1, x);
                        ey1 = Math.max(ey1, y);
                    }
                    case '*', '!', '{', '}' -> {
                        Map<String, Object> o = new LinkedHashMap<>();
                        o.put("type", "spring");
                        o.put("pos", List.of((double) wx, (double) wy, (double) oz));
                        o.put("power", c == '!' ? 1.25 : (c == '*' ? 1.0 : 1.4));
                        o.put("dir", c == '{' ? "LEFT" : c == '}' ? "RIGHT" : "UP");
                        objects.add(o);
                    }
                    default -> { }
                }
            }
        }

        // 가시: 가로(^ v)는 행 단위, 세로(< >)는 열 단위로 묶는다
        for (int y = 0; y < g.h; y++) {
            for (int x = 0; x < g.w; x++) {
                char c = g.at(x, y);
                if (c != '^' && c != 'v') continue;
                if (g.at(x - 1, y) == c) continue;
                int len = 1;
                while (g.at(x + len, y) == c) len++;
                objects.add(spikes(ox + x, oy + y, oz, len, c == '^' ? "UP" : "DOWN"));
            }
        }
        for (int x = 0; x < g.w; x++) {
            for (int y = 0; y < g.h; y++) {
                char c = g.at(x, y);
                if (c != '<' && c != '>') continue;
                if (g.at(x, y - 1) == c) continue;
                int len = 1;
                while (g.at(x, y + len) == c) len++;
                objects.add(spikes(ox + x, oy + y, oz, len, c == '<' ? "LEFT" : "RIGHT"));
            }
        }

        // 출구 게이트 장식
        if (ex0 != Integer.MAX_VALUE) {
            int top = ey0 + 3;
            for (int x = ex0 - 1; x <= ex1 + 1; x++) ops.add(new Op(ox + x, oy + top, oz - 1, GATE));
            for (int y = ey0; y < top; y++) {
                ops.add(new Op(ox + ex0 - 1, oy + y, oz - 1, GATE));
                ops.add(new Op(ox + ex1 + 1, oy + y, oz - 1, GATE));
                for (int x = ex0; x <= ex1; x++) ops.add(new Op(ox + x, oy + y, oz - 1, Material.BLACK_STAINED_GLASS));
            }
            ops.add(new Op(ox + ex0 - 1, oy + top + 1, oz - 1, LIGHT));
            ops.add(new Op(ox + ex1 + 1, oy + top + 1, oz - 1, LIGHT));
        }

        // 4) yaml 오브젝트 (그리드 좌표 → 월드)
        Object objRaw = st.get("objects");
        if (objRaw instanceof List<?> ol) {
            for (Object item : ol) {
                if (!(item instanceof Map<?, ?> m)) continue;
                Map<String, Object> o = new LinkedHashMap<>();
                for (Map.Entry<?, ?> en : m.entrySet()) {
                    String k = String.valueOf(en.getKey());
                    Object v = en.getValue();
                    switch (k) {
                        case "at" -> o.put("pos", point(v, ox, oy, oz));
                        case "path" -> {
                            List<Object> pl = new ArrayList<>();
                            for (Object p : (List<?>) v) pl.add(point(p, ox, oy, oz));
                            o.put("path", pl);
                        }
                        case "min" -> o.put("min", cell(v, ox, oy, oz, 0, -1));
                        case "max" -> o.put("max", cell(v, ox, oy, oz, 1, 2));
                        default -> o.put(k, v);
                    }
                }
                objects.add(o);
            }
        }

        out.put("name", st.get("name") != null ? String.valueOf(st.get("name")) : "STAGE");
        out.put("facing", 180);
        if (spawn != null) out.put("spawn", spawn.toList());
        out.put("kill-y", (double) (oy - 4));
        if (!isBoss) {
            out.put("time", st.get("time") instanceof Number n ? n.intValue() : 60);
            if (ex0 != Integer.MAX_VALUE) {
                out.put("goal-min", List.of((double) ox + ex0, (double) oy + ey0, (double) oz));
                out.put("goal-max", List.of((double) ox + ex1, (double) oy + Math.max(ey1, ey0 + 2), (double) oz));
            }
        }
        if (st.get("camera") instanceof Map<?, ?> cam) out.put("camera", new LinkedHashMap<>((Map<String, Object>) cam));
        out.put("objects", objects);

        if (isBoss && st.get("boss") instanceof Map<?, ?> b) {
            Map<String, Object> bo = new LinkedHashMap<>();
            for (Map.Entry<?, ?> en : b.entrySet()) {
                String k = String.valueOf(en.getKey());
                Object v = en.getValue();
                switch (k) {
                    case "at" -> bo.put("pos", point(v, ox, oy, oz));
                    case "arena" -> {
                        List<?> a = (List<?>) v;
                        bo.put("arena-min", List.of((double) ox + num(a.get(0)), (double) oy + num(a.get(1)), (double) oz));
                        bo.put("arena-max", List.of((double) ox + num(a.get(2)), (double) oy + num(a.get(3)), (double) oz + 1));
                    }
                    default -> bo.put(k, v);
                }
            }
            out.put("boss", bo);
        }
        return out;
    }

    private static Map<String, Object> spikes(int wx, int wy, int oz, int len, String dir) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("type", "spikes");
        o.put("pos", List.of((double) wx, (double) wy, (double) oz));
        o.put("length", len);
        o.put("dir", dir);
        return o;
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(o));
    }

    /** 그리드 점 (x, y) → 월드 좌표 (평면 중심 z). */
    private static List<Double> point(Object v, int ox, int oy, int oz) {
        List<?> l = (List<?>) v;
        return List.of(ox + num(l.get(0)), oy + num(l.get(1)), oz + 0.5);
    }

    /** 그리드 칸 → 월드 박스 모서리. add=1 이면 칸의 반대쪽 모서리. */
    private static List<Double> cell(Object v, int ox, int oy, int oz, int add, int dz) {
        List<?> l = (List<?>) v;
        return List.of(ox + num(l.get(0)) + add, oy + num(l.get(1)) + add, (double) oz + dz);
    }
}
