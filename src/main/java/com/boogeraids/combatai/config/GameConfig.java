package com.boogeraids.combatai.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** config.yml 전체를 읽어 불변에 가까운 구조로 보관한다. */
public final class GameConfig {

    public String worldName = "world";
    public int introTicks = 70;
    public int deathTicks = 55;
    public int clearTicks = 70;
    public int victoryTicks = 220;
    public boolean bossResetOnDeath = true;

    public CameraSettings camera = new CameraSettings();
    public HudSettings hud = new HudSettings();
    public MovementSettings movement = new MovementSettings();
    public Set<Material> lethalBlocks = EnumSet.noneOf(Material.class);
    public Set<Material> breakableBlocks = EnumSet.noneOf(Material.class);

    public boolean packEnabled = true;
    public int packPort = 8163;
    public String packHost = "localhost";
    public String packExternalUrl = "";
    public String packExternalSha1 = "";
    public boolean packRequired = true;

    public final List<ChapterDef> chapters = new ArrayList<>();

    public static GameConfig load(FileConfiguration cfg, Logger log) {
        GameConfig g = new GameConfig();
        ConfigurationSection s = cfg.getConfigurationSection("settings");
        if (s != null) {
            g.worldName = s.getString("world", g.worldName);
            g.introTicks = Math.max(10, s.getInt("intro-ticks", g.introTicks));
            g.deathTicks = Math.max(10, s.getInt("death-ticks", g.deathTicks));
            g.clearTicks = Math.max(10, s.getInt("clear-ticks", g.clearTicks));
            g.victoryTicks = Math.max(20, s.getInt("victory-ticks", g.victoryTicks));
            g.bossResetOnDeath = s.getBoolean("boss-reset-on-death", true);
            g.camera = new CameraSettings().with(s.getConfigurationSection("camera"));
            g.hud = HudSettings.load(s.getConfigurationSection("hud"));
            g.movement = MovementSettings.load(s.getConfigurationSection("movement"));
            g.lethalBlocks = materials(s.getStringList("lethal-blocks"), log);
            g.breakableBlocks = materials(s.getStringList("breakable-blocks"), log);
            ConfigurationSection rp = s.getConfigurationSection("resource-pack");
            if (rp != null) {
                g.packEnabled = rp.getBoolean("enabled", true);
                g.packPort = rp.getInt("port", g.packPort);
                g.packHost = rp.getString("public-host", g.packHost);
                g.packExternalUrl = rp.getString("external-url", "");
                g.packExternalSha1 = rp.getString("external-sha1", "");
                g.packRequired = rp.getBoolean("required", true);
            }
        }

        ConfigurationSection chs = cfg.getConfigurationSection("chapters");
        if (chs != null) {
            for (String cid : chs.getKeys(false)) {
                ConfigurationSection c = chs.getConfigurationSection(cid);
                if (c == null) continue;
                ChapterDef ch = new ChapterDef();
                ch.id = cid;
                ch.name = c.getString("name", cid.toUpperCase());
                ch.stages = new ArrayList<>();
                ConfigurationSection sts = c.getConfigurationSection("stages");
                if (sts != null) {
                    for (String sid : sts.getKeys(false)) {
                        ConfigurationSection st = sts.getConfigurationSection(sid);
                        if (st != null) ch.stages.add(g.stage(cid, sid, st, false, log));
                    }
                }
                ConfigurationSection b = c.getConfigurationSection("boss");
                if (b != null) ch.boss = g.stage(cid, "boss", b, true, log);
                g.chapters.add(ch);
            }
        }
        return g;
    }

    private StageDef stage(String cid, String sid, ConfigurationSection s, boolean boss, Logger log) {
        StageDef d = new StageDef();
        d.chapterId = cid;
        d.id = sid;
        d.name = s.getString("name", boss ? "BOSS" : sid.toUpperCase());
        d.timeSeconds = boss ? 0 : s.getInt("time", 60);
        d.facing = (float) (Math.round(s.getDouble("facing", 180) / 90.0) * 90.0);
        d.spawn = Vec.parse(s.get("spawn"));
        Vec a = Vec.parse(s.get("goal-min"));
        Vec b = Vec.parse(s.get("goal-max"));
        if (a != null && b != null) {
            d.goalMin = Vec.min(a, b);
            d.goalMax = Vec.max(a, b);
        }
        d.killY = s.getDouble("kill-y", d.spawn != null ? d.spawn.y() - 25 : 0);
        d.camera = camera.with(s.getConfigurationSection("camera"));
        d.objects = new ArrayList<>();
        for (Map<?, ?> m : s.getMapList("objects")) {
            StageObject o = StageObject.parse(m);
            if (o != null) d.objects.add(o);
            else log.warning("[" + cid + "/" + sid + "] 잘못된 오브젝트 무시: " + m);
        }
        if (boss) {
            d.boss = BossSettings.load(s.getConfigurationSection("boss"));
            if (d.boss == null) d.boss = new BossSettings();
        }
        return d;
    }

    private static Set<Material> materials(List<String> names, Logger log) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String n : names) {
            Material m = Material.matchMaterial(n);
            if (m == null) log.warning("알 수 없는 블록: " + n);
            else set.add(m);
        }
        return set;
    }

    public ChapterDef chapter(String id) {
        for (ChapterDef c : chapters) if (c.id.equalsIgnoreCase(id)) return c;
        return null;
    }

    public int chapterIndex(String id) {
        for (int i = 0; i < chapters.size(); i++) if (chapters.get(i).id.equalsIgnoreCase(id)) return i;
        return -1;
    }
}
