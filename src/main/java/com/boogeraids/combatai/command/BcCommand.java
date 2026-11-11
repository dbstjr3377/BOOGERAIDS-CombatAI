package com.boogeraids.combatai.command;

import com.boogeraids.combatai.BoogerAIDS;
import com.boogeraids.combatai.ability.Ability;
import com.boogeraids.combatai.ability.Slot;
import com.boogeraids.combatai.config.ChapterDef;
import com.boogeraids.combatai.config.StageDef;
import com.boogeraids.combatai.config.StageObject;
import com.boogeraids.combatai.config.Vec;
import com.boogeraids.combatai.game.DeathReason;
import com.boogeraids.combatai.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /bc (boogeraids) 명령어. 게임 진행 + 인게임 스테이지 편집.
 */
public final class BcCommand implements TabExecutor {

    private final BoogerAIDS plugin;
    private final Map<UUID, Location[]> selections = new HashMap<>();

    public BcCommand(BoogerAIDS plugin) {
        this.plugin = plugin;
    }

    private GameManager game() {
        return plugin.game();
    }

    private static void ok(CommandSender s, String msg) {
        s.sendMessage(Component.text("[BC] ", NamedTextColor.AQUA).append(Component.text(msg, NamedTextColor.WHITE)));
    }

    private static void err(CommandSender s, String msg) {
        s.sendMessage(Component.text("[BC] ", NamedTextColor.AQUA).append(Component.text(msg, NamedTextColor.RED)));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            help(s);
            return true;
        }
        String sub = a[0].toLowerCase();

        // 일반 플레이어용
        switch (sub) {
            case "join" -> {
                if (!(s instanceof Player p)) return true;
                if (!game().isRunning()) { err(s, "진행 중인 게임이 없습니다."); return true; }
                game().join(p);
                ok(s, "대기열에 참가했습니다.");
                return true;
            }
            case "leave" -> {
                if (!(s instanceof Player p)) return true;
                game().leave(p);
                ok(s, "게임에서 나왔습니다.");
                return true;
            }
            case "queue", "status" -> {
                ok(s, game().describe());
                if (game().isRunning()) ok(s, "대기열: " + String.join(" → ", game().queueNames()));
                return true;
            }
            case "pack" -> {
                if (s instanceof Player p) plugin.packService().send(p, true);
                return true;
            }
            default -> { }
        }

        if (!s.hasPermission("boogeraids.admin")) {
            err(s, "권한이 없습니다.");
            return true;
        }

        switch (sub) {
            case "start" -> {
                if (game().start(s, a.length > 1 ? a[1] : null, a.length > 2 ? a[2] : null)) ok(s, "게임 시작!");
            }
            case "stop" -> {
                game().stop("관리자 종료");
                ok(s, "게임을 종료했습니다.");
            }
            case "skip" -> {
                game().forceClear();
                ok(s, "현재 스테이지를 클리어 처리했습니다.");
            }
            case "kill" -> game().die(DeathReason.FORCED);
            case "reload" -> {
                if (game().isRunning()) { err(s, "게임 중에는 리로드할 수 없습니다."); return true; }
                plugin.reloadGameConfig();
                plugin.packService().start();
                ok(s, "설정을 다시 불러왔습니다. 챕터 " + plugin.gameConfig().chapters.size() + "개");
            }
            case "list" -> list(s);
            case "pos1", "pos2" -> {
                if (!(s instanceof Player p)) return true;
                Location[] sel = selections.computeIfAbsent(p.getUniqueId(), k -> new Location[2]);
                Block target = p.getTargetBlockExact(8);
                Location l = target != null ? target.getLocation() : p.getLocation().getBlock().getLocation();
                sel[sub.equals("pos1") ? 0 : 1] = l;
                ok(s, sub + " = " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + " (바라보는 블록)");
            }
            case "tp" -> {
                if (!(s instanceof Player p) || a.length < 3) { err(s, "/bc tp <챕터> <스테이지|boss>"); return true; }
                StageDef d = find(a[1], a[2]);
                if (d == null || d.spawn == null) { err(s, "스테이지 또는 스폰이 없습니다."); return true; }
                p.teleport(d.spawn.toLocation(p.getWorld()));
            }
            case "build" -> {
                if (!(s instanceof Player p) || a.length < 2) { err(s, "/bc build <레벨파일> (예: chapter1) - 지금 서 있는 블록이 1번 스테이지 왼쪽 아래 기준점"); return true; }
                if (game().isRunning()) { err(s, "게임 중에는 건설할 수 없습니다."); return true; }
                new com.boogeraids.combatai.stage.LevelBuilder(plugin).build(s, a[1], p.getLocation().getBlock().getLocation());
            }
            case "chapter" -> chapterCmd(s, a);
            case "stage" -> stageCmd(s, a);
            case "set" -> setCmd(s, a);
            case "obj" -> objCmd(s, a);
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        String[] lines = {
                "§b§lBOOGERAIDS // CombatAI",
                "§f/bc start [챕터] [스테이지] §7- 게임 시작 (접속자 전원 참가)",
                "§f/bc stop | skip | kill | status | reload",
                "§f/bc join | leave | pack",
                "§3── 편집 ──",
                "§f/bc build <레벨> §7- 내장 레벨(chapter1)을 현재 위치에 건설하고 config 에 등록 (빈 월드 권장)",
                "§f/bc list §7- 챕터/스테이지 목록",
                "§f/bc chapter add <id> <이름>",
                "§f/bc stage add <챕터> <id> [시간(초)] [이름]   §7보스: §f/bc stage boss <챕터> [이름]",
                "§f/bc pos1 | pos2 §7- 바라보는 블록으로 영역 선택",
                "§f/bc set <챕터> <스테이지|boss> <spawn|goal|facing|killy|time|name|arena|bosspos|health|bossname|item|camera>",
                "§f/bc obj <챕터> <스테이지|boss> add <ability|sign|jumppad|switch|laser> ...",
                "§f/bc obj <챕터> <스테이지|boss> list | remove <번호>",
                "§f/bc tp <챕터> <스테이지|boss>",
        };
        for (String l : lines) s.sendMessage(l);
    }

    private void list(CommandSender s) {
        for (ChapterDef c : plugin.gameConfig().chapters) {
            s.sendMessage("§b" + c.id + " §f" + c.name);
            for (StageDef d : c.sequence()) {
                String err = d.validate();
                s.sendMessage("  §7- §f" + d.id + " §7" + d.name + (err == null ? " §a✔" : " §c✖ " + err));
            }
        }
    }

    // ------------------------------------------------------------------ editing

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private void save(CommandSender s, String msg) {
        plugin.saveConfig();
        plugin.reloadGameConfig();
        ok(s, msg + (game().isRunning() ? " §7(다음 시도부터 적용)" : ""));
    }

    private static String stagePath(String chapter, String stage) {
        return stage.equalsIgnoreCase("boss") ? "chapters." + chapter + ".boss" : "chapters." + chapter + ".stages." + stage;
    }

    private StageDef find(String chapter, String stage) {
        ChapterDef c = plugin.gameConfig().chapter(chapter);
        if (c == null) return null;
        for (StageDef d : c.sequence()) if (d.id.equalsIgnoreCase(stage)) return d;
        return null;
    }

    private static String join(String[] a, int from) {
        return a.length > from ? String.join(" ", Arrays.copyOfRange(a, from, a.length)) : "";
    }

    private void chapterCmd(CommandSender s, String[] a) {
        if (a.length < 3 || !a[1].equalsIgnoreCase("add")) { err(s, "/bc chapter add <id> <이름>"); return; }
        String id = a[2];
        if (cfg().isConfigurationSection("chapters." + id)) { err(s, "이미 있는 챕터입니다."); return; }
        cfg().set("chapters." + id + ".name", a.length > 3 ? join(a, 3) : id.toUpperCase());
        save(s, "챕터 " + id + " 생성");
    }

    private void stageCmd(CommandSender s, String[] a) {
        if (a.length >= 3 && a[1].equalsIgnoreCase("boss")) {
            String path = "chapters." + a[2] + ".boss";
            if (!cfg().isConfigurationSection("chapters." + a[2])) { err(s, "챕터가 없습니다."); return; }
            cfg().set(path + ".name", a.length > 3 ? join(a, 3) : "BOSS");
            cfg().set(path + ".facing", 180);
            cfg().set(path + ".boss.name", "SENTINEL");
            cfg().set(path + ".boss.health", 60);
            cfg().set(path + ".boss.item", "OBSERVER");
            cfg().set(path + ".boss.scale", 3.0);
            save(s, "보스 스테이지 생성 - spawn / arena / bosspos 를 설정하세요");
            return;
        }
        if (a.length < 4 || !a[1].equalsIgnoreCase("add")) { err(s, "/bc stage add <챕터> <id> [시간] [이름]"); return; }
        if (!cfg().isConfigurationSection("chapters." + a[2])) { err(s, "챕터가 없습니다."); return; }
        String path = stagePath(a[2], a[3]);
        int time = 60;
        int nameFrom = 4;
        if (a.length > 4) {
            try {
                time = Integer.parseInt(a[4]);
                nameFrom = 5;
            } catch (NumberFormatException ignored) { }
        }
        cfg().set(path + ".name", a.length > nameFrom ? join(a, nameFrom) : a[3].toUpperCase());
        cfg().set(path + ".time", time);
        cfg().set(path + ".facing", 180);
        save(s, "스테이지 " + a[3] + " 생성 - spawn / goal 을 설정하세요");
    }

    private void setCmd(CommandSender s, String[] a) {
        if (!(s instanceof Player p) || a.length < 4) { err(s, "/bc set <챕터> <스테이지|boss> <키> [값]"); return; }
        String path = stagePath(a[1], a[2]);
        if (!cfg().isConfigurationSection(path)) { err(s, "스테이지가 없습니다: " + path); return; }
        String key = a[3].toLowerCase();
        Location loc = p.getLocation();
        Location[] sel = selections.get(p.getUniqueId());
        switch (key) {
            case "spawn" -> {
                Vec v = new Vec(loc.getBlockX() + 0.5, Math.floor(loc.getY() * 2) / 2.0, loc.getBlockZ() + 0.5);
                cfg().set(path + ".spawn", v.toList());
                if (!cfg().isSet(path + ".kill-y")) cfg().set(path + ".kill-y", Math.floor(loc.getY()) - 20);
                save(s, "spawn = " + v.toList() + "  §7(캐릭터 이동 평면도 이 좌표 기준)");
            }
            case "goal" -> {
                if (sel == null || sel[0] == null || sel[1] == null) { err(s, "/bc pos1, /bc pos2 로 영역을 먼저 선택하세요."); return; }
                cfg().set(path + ".goal-min", Vec.min(Vec.of(sel[0]), Vec.of(sel[1])).toList());
                cfg().set(path + ".goal-max", Vec.max(Vec.of(sel[0]), Vec.of(sel[1])).toList());
                save(s, "골 영역 설정");
            }
            case "facing" -> {
                int yaw = (int) (Math.round(loc.getYaw() / 90.0) * 90) % 360;
                if (yaw < 0) yaw += 360;
                cfg().set(path + ".facing", yaw);
                save(s, "facing = " + yaw + " (지금 바라보는 방향이 카메라 방향)");
            }
            case "killy" -> {
                double y = a.length > 4 ? Double.parseDouble(a[4]) : Math.floor(loc.getY());
                cfg().set(path + ".kill-y", y);
                save(s, "kill-y = " + y);
            }
            case "time" -> {
                if (a.length < 5) { err(s, "/bc set ... time <초>"); return; }
                cfg().set(path + ".time", Integer.parseInt(a[4]));
                save(s, "time = " + a[4]);
            }
            case "name" -> {
                cfg().set(path + ".name", join(a, 4));
                save(s, "name = " + join(a, 4));
            }
            case "arena" -> {
                if (sel == null || sel[0] == null || sel[1] == null) { err(s, "/bc pos1, /bc pos2 로 아레나 영역을 선택하세요."); return; }
                cfg().set(path + ".boss.arena-min", Vec.min(Vec.of(sel[0]), Vec.of(sel[1])).toList());
                cfg().set(path + ".boss.arena-max", Vec.max(Vec.of(sel[0]), Vec.of(sel[1])).toList());
                save(s, "보스 아레나 설정 (바닥 높이 = 아래쪽 y)");
            }
            case "bosspos" -> {
                cfg().set(path + ".boss.pos", Vec.of(loc).toList());
                save(s, "보스 위치 = 현재 위치");
            }
            case "health" -> {
                cfg().set(path + ".boss.health", Double.parseDouble(a[4]));
                save(s, "보스 체력 = " + a[4]);
            }
            case "bossname" -> {
                cfg().set(path + ".boss.name", join(a, 4));
                save(s, "보스 이름 = " + join(a, 4));
            }
            case "item" -> {
                Material m = a.length > 4 ? Material.matchMaterial(a[4]) : p.getInventory().getItemInMainHand().getType();
                if (m == null || m.isAir()) { err(s, "아이템을 지정하거나 손에 드세요."); return; }
                cfg().set(path + ".boss.item", m.name());
                save(s, "보스 외형 = " + m.name());
            }
            case "camera" -> {
                if (a.length < 6) { err(s, "/bc set ... camera <distance|height|pitch|yaw-offset|lookahead|follow-speed> <값>"); return; }
                cfg().set(path + ".camera." + a[4], Double.parseDouble(a[5]));
                save(s, "camera." + a[4] + " = " + a[5]);
            }
            default -> err(s, "알 수 없는 키: " + key);
        }
    }

    private void objCmd(CommandSender s, String[] a) {
        if (a.length < 4) { err(s, "/bc obj <챕터> <스테이지|boss> <add|list|remove> ..."); return; }
        String path = stagePath(a[1], a[2]);
        if (!cfg().isConfigurationSection(path)) { err(s, "스테이지가 없습니다: " + path); return; }
        List<Map<?, ?>> raw = new ArrayList<>(cfg().getMapList(path + ".objects"));
        String op = a[3].toLowerCase();

        if (op.equals("list")) {
            if (raw.isEmpty()) ok(s, "오브젝트 없음");
            for (int i = 0; i < raw.size(); i++) s.sendMessage("§7[" + i + "] §f" + raw.get(i));
            return;
        }
        if (op.equals("remove")) {
            if (a.length < 5) { err(s, "/bc obj ... remove <번호>"); return; }
            int idx = Integer.parseInt(a[4]);
            if (idx < 0 || idx >= raw.size()) { err(s, "번호 범위 밖"); return; }
            raw.remove(idx);
            cfg().set(path + ".objects", raw);
            save(s, "오브젝트 #" + idx + " 삭제");
            return;
        }
        if (!op.equals("add") || a.length < 5 || !(s instanceof Player p)) {
            err(s, "/bc obj ... add <ability|sign|jumppad|switch|laser>");
            return;
        }
        Location loc = p.getLocation();
        Vec here = new Vec(loc.getBlockX() + 0.5, Math.floor(loc.getY() * 2) / 2.0, loc.getBlockZ() + 0.5);
        StageObject o = new StageObject();
        switch (a[4].toLowerCase()) {
            case "ability" -> {
                if (a.length < 6) { err(s, "능력: " + Arrays.stream(Ability.values()).map(Enum::name).collect(Collectors.joining(", "))); return; }
                o.type = StageObject.Type.ABILITY;
                o.ability = Ability.parse(a[5]);
                if (o.ability == null) { err(s, "알 수 없는 능력"); return; }
                o.slot = a.length > 6 ? Slot.parse(a[6]) : Slot.AUTO;
                o.pos = here;
            }
            case "sign" -> {
                o.type = StageObject.Type.SIGN;
                o.pos = new Vec(here.x(), loc.getY() + 1.8, here.z());
                o.text = join(a, 5);
            }
            case "jumppad" -> {
                o.type = StageObject.Type.JUMPPAD;
                o.pos = here;
                o.power = a.length > 5 ? Double.parseDouble(a[5]) : 1.0;
            }
            case "switch" -> {
                Block target = p.getTargetBlockExact(10);
                if (target == null) { err(s, "레드스톤 블록을 놓을 위치(블록)를 바라보세요."); return; }
                o.type = StageObject.Type.SWITCH;
                o.pos = here;
                o.target = Vec.of(target.getLocation());
            }
            case "laser" -> {
                Location[] sel = selections.get(p.getUniqueId());
                if (sel == null || sel[0] == null || sel[1] == null) { err(s, "/bc pos1, /bc pos2 로 레이저 영역을 선택하세요."); return; }
                o.type = StageObject.Type.LASER;
                Vec mn = Vec.min(Vec.of(sel[0]), Vec.of(sel[1]));
                Vec mx = Vec.max(Vec.of(sel[0]), Vec.of(sel[1]));
                o.min = mn;
                o.max = new Vec(mx.x() + 1, mx.y() + 1, mx.z() + 1);
                o.period = a.length > 5 ? Integer.parseInt(a[5]) : 60;
                o.onTicks = a.length > 6 ? Integer.parseInt(a[6]) : 30;
                o.offset = a.length > 7 ? Integer.parseInt(a[7]) : 0;
            }
            case "saw" -> {
                o.type = StageObject.Type.SAW;
                o.pos = new Vec(here.x(), loc.getY() + 1.0, here.z());
                o.size = a.length > 5 ? Double.parseDouble(a[5]) : 1.6;
                if (a.length > 7) {          // saw <크기> <반지름> <각속도>  → 공전
                    o.radius = Double.parseDouble(a[6]);
                    o.orbit = Double.parseDouble(a[7]);
                }
            }
            case "spikes" -> {
                o.type = StageObject.Type.SPIKES;
                o.pos = new Vec(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                o.dir = a.length > 5 ? StageObject.Dir.valueOf(a[5].toUpperCase()) : StageObject.Dir.UP;
                o.length = a.length > 6 ? Integer.parseInt(a[6]) : 1;
            }
            case "spring" -> {
                o.type = StageObject.Type.SPRING;
                o.pos = new Vec(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                o.power = a.length > 5 ? Double.parseDouble(a[5]) : 1.0;
                o.dir = a.length > 6 ? StageObject.Dir.valueOf(a[6].toUpperCase()) : StageObject.Dir.UP;
            }
            default -> {
                err(s, "알 수 없는 타입");
                return;
            }
        }
        raw.add(new LinkedHashMap<>(o.serialize()));
        cfg().set(path + ".objects", raw);
        save(s, a[4] + " 오브젝트 추가 (#" + (raw.size() - 1) + ")");
    }

    // ------------------------------------------------------------------ tab

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("help", "join", "leave", "status", "queue", "pack"));
            if (s.hasPermission("boogeraids.admin")) {
                out.addAll(List.of("start", "stop", "skip", "kill", "reload", "list", "build", "pos1", "pos2", "tp", "chapter", "stage", "set", "obj"));
            }
        } else if (s.hasPermission("boogeraids.admin")) {
            String sub = a[0].toLowerCase();
            List<String> chapters = plugin.gameConfig().chapters.stream().map(c -> c.id).toList();
            if (a.length == 2 && List.of("start", "tp", "set", "obj").contains(sub)) out.addAll(chapters);
            else if (a.length == 2 && sub.equals("chapter")) out.add("add");
            else if (a.length == 2 && sub.equals("build")) out.add("chapter1");
            else if (a.length == 2 && sub.equals("stage")) out.addAll(List.of("add", "boss"));
            else if (a.length == 3 && sub.equals("stage")) out.addAll(chapters);
            else if (a.length == 3 && List.of("start", "tp", "set", "obj").contains(sub)) {
                ChapterDef c = plugin.gameConfig().chapter(a[1]);
                if (c != null) for (StageDef d : c.sequence()) out.add(d.id);
            } else if (a.length == 4 && sub.equals("set")) {
                out.addAll(List.of("spawn", "goal", "facing", "killy", "time", "name", "arena", "bosspos", "health", "bossname", "item", "camera"));
            } else if (a.length == 5 && sub.equals("set") && a[3].equalsIgnoreCase("camera")) {
                out.addAll(List.of("distance", "height", "pitch", "yaw-offset", "lookahead", "follow-speed", "vertical-follow-speed", "deadzone-y"));
            } else if (a.length == 4 && sub.equals("obj")) {
                out.addAll(List.of("add", "list", "remove"));
            } else if (a.length == 5 && sub.equals("obj") && a[3].equalsIgnoreCase("add")) {
                out.addAll(List.of("ability", "sign", "jumppad", "switch", "laser", "saw", "spikes", "spring"));
            } else if (a.length == 6 && sub.equals("obj") && a[4].equalsIgnoreCase("ability")) {
                for (Ability ab : Ability.values()) out.add(ab.name());
            } else if (a.length == 7 && sub.equals("obj") && a[4].equalsIgnoreCase("ability")) {
                out.addAll(List.of("LEFT", "RIGHT", "AUTO"));
            }
        }
        String last = a[a.length - 1].toLowerCase();
        return out.stream().filter(x -> x.toLowerCase().startsWith(last)).toList();
    }
}
