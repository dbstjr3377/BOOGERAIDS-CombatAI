package com.boogeraids.combatai.game;

import com.boogeraids.combatai.BoogerAIDS;
import com.boogeraids.combatai.ability.Ability;
import com.boogeraids.combatai.ability.AbilityController;
import com.boogeraids.combatai.ability.AbilitySlotState;
import com.boogeraids.combatai.ability.Slot;
import com.boogeraids.combatai.boss.BossRuntime;
import com.boogeraids.combatai.camera.CameraRig;
import com.boogeraids.combatai.config.ChapterDef;
import com.boogeraids.combatai.config.GameConfig;
import com.boogeraids.combatai.config.StageDef;
import com.boogeraids.combatai.hud.Hud;
import com.boogeraids.combatai.stage.StageRuntime;
import com.boogeraids.combatai.util.Fonts;
import com.boogeraids.combatai.util.Fx;
import com.boogeraids.combatai.util.PlaneFrame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 게임 전체 상태 머신.
 * <pre>
 * IDLE ─start→ INTRO ─→ PLAYING ─골 도달/보스 처치→ CLEARING ─→ (다음 스테이지 INTRO | VICTORY)
 *                         │
 *                         └─사망/시간초과→ DYING ─→ 큐의 다음 플레이어로 교대 후 같은 스테이지 INTRO
 * </pre>
 */
public final class GameManager {

    private final BoogerAIDS plugin;
    private final Fx fx;
    private final CameraRig camera;
    private final AbilityController abilities;
    private Hud hud;

    private GameState state = GameState.IDLE;
    private int stateTicks;
    private int globalTick;
    private BukkitTask task;

    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Deque<UUID> queue = new ArrayDeque<>();
    private final Map<UUID, SavedPlayer> saved = new HashMap<>();
    private final InputState input = new InputState();

    private World world;
    private int chapterIndex;
    private int stageIndex;
    private StageDef def;
    private PlaneFrame frame;
    private StageRuntime stage;
    private BossRuntime boss;
    private Character character;
    private int timerTicks;
    private int attempt;
    private int totalDeaths;
    private double carryBossHp = -1;
    private UUID deadController;
    private Location deathSpot;

    public GameManager(BoogerAIDS plugin) {
        this.plugin = plugin;
        this.fx = new Fx(this::audience);
        this.camera = new CameraRig(plugin);
        this.abilities = new AbilityController(this);
    }

    // ------------------------------------------------------------------ accessors

    public GameConfig config() {
        return plugin.gameConfig();
    }

    public Fx fx() {
        return fx;
    }

    public CameraRig camera() {
        return camera;
    }

    public Hud hud() {
        return hud;
    }

    public PlaneFrame frame() {
        return frame;
    }

    public StageRuntime stage() {
        return stage;
    }

    public BossRuntime boss() {
        return boss;
    }

    public Character character() {
        return character;
    }

    public GameState state() {
        return state;
    }

    public boolean isRunning() {
        return state != GameState.IDLE;
    }

    public boolean isParticipant(Player p) {
        return participants.contains(p.getUniqueId());
    }

    public boolean isController(Player p) {
        return isRunning() && camera.isController(p);
    }

    public Collection<? extends Player> audience() {
        List<Player> list = new ArrayList<>();
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) list.add(p);
        }
        return list;
    }

    public List<String> queueNames() {
        List<String> names = new ArrayList<>();
        for (UUID id : queue) {
            Player p = Bukkit.getPlayer(id);
            names.add(p == null ? "?" : p.getName());
        }
        return names;
    }

    public String describe() {
        if (!isRunning()) return "대기 중";
        ChapterDef ch = config().chapters.get(chapterIndex);
        return state + " | " + ch.id + "/" + def.id + " | 시도 " + attempt + " | 사망 " + totalDeaths;
    }

    // ------------------------------------------------------------------ start / stop

    public boolean start(CommandSender sender, String chapterId, String stageId) {
        if (isRunning()) {
            sender.sendMessage(Component.text("이미 게임이 진행 중입니다. /bc stop", NamedTextColor.RED));
            return false;
        }
        GameConfig cfg = config();
        if (cfg.chapters.isEmpty()) {
            sender.sendMessage(Component.text("config.yml 에 챕터가 없습니다.", NamedTextColor.RED));
            return false;
        }
        world = Bukkit.getWorld(cfg.worldName);
        if (world == null) {
            sender.sendMessage(Component.text("월드를 찾을 수 없습니다: " + cfg.worldName, NamedTextColor.RED));
            return false;
        }
        chapterIndex = 0;
        stageIndex = 0;
        if (chapterId != null) {
            chapterIndex = cfg.chapterIndex(chapterId);
            if (chapterIndex < 0) {
                sender.sendMessage(Component.text("챕터 없음: " + chapterId, NamedTextColor.RED));
                return false;
            }
            if (stageId != null) {
                List<StageDef> seq = cfg.chapters.get(chapterIndex).sequence();
                stageIndex = -1;
                for (int i = 0; i < seq.size(); i++) if (seq.get(i).id.equalsIgnoreCase(stageId)) stageIndex = i;
                if (stageIndex < 0) {
                    sender.sendMessage(Component.text("스테이지 없음: " + stageId, NamedTextColor.RED));
                    return false;
                }
            }
        }
        // 모든 스테이지 유효성 검사 (시작 지점 이후)
        for (int c = chapterIndex; c < cfg.chapters.size(); c++) {
            List<StageDef> seq = cfg.chapters.get(c).sequence();
            if (seq.isEmpty()) {
                sender.sendMessage(Component.text("챕터 " + cfg.chapters.get(c).id + " 에 스테이지가 없습니다.", NamedTextColor.RED));
                return false;
            }
            for (StageDef s : seq) {
                String err = s.validate();
                if (err != null) {
                    sender.sendMessage(Component.text("[" + s.key() + "] " + err, NamedTextColor.RED));
                    return false;
                }
            }
        }

        participants.clear();
        queue.clear();
        for (Player p : Bukkit.getOnlinePlayers()) join(p);
        if (queue.isEmpty()) {
            sender.sendMessage(Component.text("참가자가 없습니다.", NamedTextColor.RED));
            return false;
        }

        totalDeaths = 0;
        carryBossHp = -1;
        StageDef first = cfg.chapters.get(chapterIndex).sequence().get(stageIndex);
        Location at = first.spawn.toLocation(world);
        camera.create(world, at);
        hud = new Hud(camera.cam(), cfg.hud);
        state = GameState.INTRO;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        loadStage(true);
        return true;
    }

    public void stop(String reason) {
        if (!isRunning()) return;
        if (task != null) task.cancel();
        task = null;
        cleanupAttempt();
        if (hud != null) hud.destroy();
        hud = null;
        Player ctrl = camera.controller();
        if (ctrl != null) camera.releaseController(ctrl);
        for (UUID id : new ArrayList<>(participants)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                camera.removeViewer(p);
                restore(p);
                if (reason != null) p.sendMessage(Component.text("[BOOGERAIDS] 게임 종료: " + reason, NamedTextColor.GRAY));
            }
        }
        camera.destroy();
        participants.clear();
        queue.clear();
        state = GameState.IDLE;
    }

    // ------------------------------------------------------------------ participants

    /** 참가자로 등록 (큐 맨 뒤). 게임 중이면 관전 시점으로 전환. */
    public void join(Player p) {
        if (participants.contains(p.getUniqueId())) return;
        participants.add(p.getUniqueId());
        queue.addLast(p.getUniqueId());
        saved.put(p.getUniqueId(), SavedPlayer.capture(p));
        p.getInventory().clear();
        p.setHealth(Math.max(1, p.getHealth()));
        p.setFoodLevel(20);
        if (isRunning() && camera.isAlive()) {
            camera.addViewer(p);
            refreshSubject();
        }
        plugin.packService().send(p);
    }

    public void leave(Player p) {
        UUID id = p.getUniqueId();
        if (!participants.remove(id)) return;
        boolean wasController = id.equals(queue.peekFirst());
        queue.remove(id);
        if (wasController) camera.releaseController(p);
        camera.removeViewer(p);
        restore(p);
        if (!isRunning()) return;
        if (queue.isEmpty()) {
            stop("참가자가 없습니다");
            return;
        }
        if (wasController && (state == GameState.PLAYING || state == GameState.INTRO)) {
            deadController = null;
            die(DeathReason.ABANDONED);
        } else if (wasController && state == GameState.CLEARING) {
            // 클리어 연출 중 이탈: 다음 사람이 이어서 진행
            camera.setController(controllerPlayer());
        }
        refreshSubject();
    }

    private void restore(Player p) {
        SavedPlayer s = saved.remove(p.getUniqueId());
        if (s != null) s.restore(p);
        else if (p.getGameMode() == GameMode.SPECTATOR) p.setGameMode(GameMode.SURVIVAL);
    }

    public Player controllerPlayer() {
        UUID id = queue.peekFirst();
        return id == null ? null : Bukkit.getPlayer(id);
    }

    private String nextName() {
        if (queue.size() < 2) return null;
        java.util.Iterator<UUID> it = queue.iterator();
        it.next();
        Player p = Bukkit.getPlayer(it.next());
        return p == null ? null : p.getName();
    }

    private void refreshSubject() {
        if (hud == null) return;
        Player c = controllerPlayer();
        hud.setSubject(c == null ? "-" : c.getName(), nextName());
    }

    // ------------------------------------------------------------------ stage flow

    private ChapterDef chapter() {
        return config().chapters.get(chapterIndex);
    }

    /** 현재 스테이지를 새로 시작. firstAttempt 면 스테이지 타이틀 카드. */
    private void loadStage(boolean firstAttempt) {
        cleanupAttempt();
        Player ctrl = controllerPlayer();
        if (ctrl == null) {
            stop("참가자가 없습니다");
            return;
        }
        def = chapter().sequence().get(stageIndex);
        if (firstAttempt) attempt = 1;
        else attempt++;

        frame = new PlaneFrame(world, def.spawn.toVector(), def.facing);
        camera.configure(frame, def.camera);
        Location spawn = frame.at(0, def.spawn.y());
        camera.snap(0, def.spawn.y());

        stage = new StageRuntime(this, def, frame);
        stage.start();

        character = new Character(frame, config().movement);
        character.spawn(spawn, ctrl.getPlayerProfile());
        character.setFrozen(true);
        input.reset();

        // 시점 배치: 통제권 → 좌석, 나머지 → 관전
        camera.setController(ctrl);
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !p.getUniqueId().equals(ctrl.getUniqueId()) && !camera.isViewer(p)) camera.addViewer(p);
        }

        hud.setStage(chapter().name, def.name);
        refreshSubject();
        for (int i = 0; i < 2; i++) hud.setHolder(i, null);

        if (def.isBoss()) {
            boss = new BossRuntime(this, def.boss, frame, carryBossHp);
            boss.start();
            hud.setTimerVisible(false);
            hud.setBoss(true, boss.name(), boss.healthFraction());
            camera.zoom(1.15);
            camera.focusOverride(def.boss.pos.toVector(), 50);
            timerTicks = -1;
        } else {
            hud.setTimerVisible(true);
            hud.setBoss(false, null, 1);
            timerTicks = def.timeSeconds * 20;
            hud.setTimer(timerTicks, true);
        }

        state = GameState.INTRO;
        stateTicks = config().introTicks;
        if (firstAttempt) {
            hud.showTitle(Fonts.lab(def.isBoss() ? "⚠ WARNING ⚠" : def.name, def.isBoss() ? 0xFF2A4A : 0xFFFFFF).decorate(TextDecoration.BOLD),
                    Fonts.lab(def.isBoss() ? def.name : chapter().name, def.isBoss() ? 0xFFB3BD : 0x00E5FF),
                    stateTicks - 15, 15);
            fx.sound(def.isBoss() ? "entity.elder_guardian.curse" : "block.beacon.activate", 0.8f, def.isBoss() ? 0.7f : 1.3f);
            if (def.isBoss()) hud.flash(0xFF2A4A, 0.6, 30);
        } else {
            hud.showTitle(Fonts.lab("SUBJECT #" + String.format("%03d", totalDeaths + 1), 0xFFFFFF).decorate(TextDecoration.BOLD),
                    Fonts.lab(ctrl.getName() + " 투입", 0x00E5FF), stateTicks - 15, 15);
            fx.sound("block.respawn_anchor.charge", 0.7f, 1.4f);
        }
        hud.flash(0x00E5FF, 0.35, 15);
    }

    private void cleanupAttempt() {
        abilities.clear();
        if (stage != null) stage.cleanup();
        if (boss != null) boss.cleanup();
        if (character != null) character.remove();
        stage = null;
        boss = null;
        character = null;
    }

    private void beginPlay() {
        state = GameState.PLAYING;
        character.setFrozen(false);
        hud.showTitle(Fonts.lab("▶ START", 0x5CFF8A).decorate(TextDecoration.BOLD), null, 6, 10);
        fx.sound("block.note_block.bit", 1f, 2.0f);
        fx.sound("entity.experience_orb.pickup", 0.6f, 1.6f);
    }

    public void die(DeathReason reason) {
        if (state != GameState.PLAYING && state != GameState.INTRO) return;
        state = GameState.DYING;
        stateTicks = config().deathTicks;
        totalDeaths++;
        Player ctrl = controllerPlayer();
        if (reason != DeathReason.ABANDONED) deadController = ctrl == null ? null : ctrl.getUniqueId();
        abilities.clear();
        if (boss != null && !config().bossResetOnDeath) carryBossHp = boss.health();

        if (character != null && character.isValid()) {
            deathSpot = character.center();
            Vector knock = frame.velocity(-character.facing() * 0.25, 0.45);
            character.deathPose(knock);
            fx.dust(deathSpot, 0xFF2A2A, 1.6f, 40, 0.5);
            fx.particle(Particle.LARGE_SMOKE, deathSpot, 12, 0.3, 0.03);
            fx.particle(Particle.ELECTRIC_SPARK, deathSpot, 30, 0.4, 0.3);
        }
        camera.shake(0.85);
        hud.flash(0xFF0A1E, 1.0, 35);
        fx.sound("entity.player.hurt", 1f, 0.8f);
        fx.sound("block.beacon.deactivate", 1f, 0.6f);
        fx.sound("block.glass.break", 0.8f, 0.6f);

        String next = queue.size() > 1 ? nextName() : (ctrl == null ? "-" : ctrl.getName());
        hud.showTitle(Fonts.lab("✖ " + reason.code, 0xFF2A2A).decorate(TextDecoration.BOLD),
                Fonts.lab(reason.korean + "  //  다음 피험자: " + next, 0xFFB3B3), stateTicks - 12, 12);
        hud.setTimer(Math.max(0, timerTicks), true);
    }

    private void handOff() {
        if (deadController != null && deadController.equals(queue.peekFirst()) && queue.size() > 1) {
            queue.addLast(queue.pollFirst());
        }
        deadController = null;
        loadStage(false);
    }

    private void clear() {
        state = GameState.CLEARING;
        stateTicks = config().clearTicks;
        character.setFrozen(true);
        abilities.clear();
        String sub = def.isBoss() ? "감시체 무력화 완료" :
                String.format("잔여 시간 %02d.%02d", timerTicks / 20, (timerTicks % 20) * 5);
        hud.showTitle(Fonts.lab(def.isBoss() ? "SPECIMEN NEUTRALIZED" : "STAGE CLEAR", 0x5CFF8A).decorate(TextDecoration.BOLD),
                Fonts.lab(sub, 0xCFFFE0), stateTicks - 15, 15);
        hud.flash(0x5CFF8A, 0.55, 25);
        fx.sound("ui.toast.challenge_complete", 0.8f, 1.2f);
        fx.sound("block.beacon.power_select", 1f, 1.5f);
        fx.particle(Particle.TOTEM_OF_UNDYING, character.center(), 40, 0.4, 0.4);
        if (def.isBoss()) carryBossHp = -1;
    }

    private void advance() {
        List<StageDef> seq = chapter().sequence();
        stageIndex++;
        if (stageIndex >= seq.size()) {
            stageIndex = 0;
            chapterIndex++;
            carryBossHp = -1;
            if (chapterIndex >= config().chapters.size()) {
                victory();
                return;
            }
        }
        loadStage(true);
    }

    private void victory() {
        state = GameState.VICTORY;
        stateTicks = config().victoryTicks;
        if (character != null) character.setFrozen(true);
        hud.setTimerVisible(false);
        hud.setBoss(false, null, 1);
        hud.showTitle(Fonts.lab("ALL TESTS COMPLETE", 0x00E5FF).decorate(TextDecoration.BOLD),
                Fonts.lab("총 투입 피험자 " + (totalDeaths + 1) + "명  //  연구소 탈출 성공", 0xFFFFFF), stateTicks - 30, 30);
        fx.sound("ui.toast.challenge_complete", 1f, 1.0f);
        fx.sound("entity.player.levelup", 1f, 0.8f);
        hud.flash(0x00E5FF, 0.6, 40);
    }

    // ------------------------------------------------------------------ external events

    public void onInput(Player p, Input in) {
        if (!isController(p)) return;
        input.set(in.isLeft(), in.isRight(), in.isJump());
    }

    public void onClick(Player p, boolean left) {
        if (!isController(p) || state != GameState.PLAYING) return;
        input.click(left, globalTick);
    }

    public void onAbilityPickup(Ability ability, Slot slot, Location where) {
        AbilitySlotState[] slots = character.slots;
        int idx;
        if (slot == Slot.AUTO) {
            if (slots[0].ability == null) idx = 0;
            else if (slots[1].ability == null) idx = 1;
            else idx = 1;
        } else idx = slot.index();
        slots[idx].ability = ability;
        slots[idx].cooldown = 0;
        slots[idx].flash = 30;
        hud.updateSlots(slots);
        hud.pulseHolder(idx);
        hud.showTitle(Fonts.lab("+ " + ability.code, ability.rgb).decorate(TextDecoration.BOLD),
                Fonts.lab(ability.displayName + " — " + ability.description + "  [" + (idx == 0 ? "좌클릭" : "우클릭") + "]", 0xE6F7FF), 40, 12);
        hud.flash(ability.rgb, 0.4, 14);
        fx.sound("block.amethyst_block.resonate", 1f, 1.4f);
        fx.sound("entity.player.levelup", 0.6f, 1.8f);
        fx.particle(Particle.END_ROD, where, 25, 0.3, 0.12);
    }

    public void forceClear() {
        if (state == GameState.PLAYING || state == GameState.INTRO) {
            if (state == GameState.INTRO) beginPlay();
            clear();
        }
    }

    // ------------------------------------------------------------------ tick

    private void tick() {
        globalTick++;
        if (!isRunning()) return;
        if (!camera.isAlive()) {
            stop("카메라 엔티티 소실");
            return;
        }
        Player ctrl = controllerPlayer();
        if (ctrl != null && camera.isController(ctrl) && ctrl.isOnline()) {
            Input cur = ctrl.getCurrentInput();
            if (cur != null) input.set(cur.isLeft(), cur.isRight(), cur.isJump());
        }

        switch (state) {
            case INTRO -> tickIntro();
            case PLAYING -> tickPlaying();
            case DYING -> {
                if (character != null && character.isValid()) character.tick(input);
                if (--stateTicks <= 0) handOff();
            }
            case CLEARING -> {
                if (character != null && character.isValid()) character.tick(input);
                if (boss != null && !boss.isGone()) boss.tick(character);
                if (--stateTicks <= 0) advance();
            }
            case VICTORY -> {
                if (stateTicks % 12 == 0 && character != null && character.isValid()) firework(character.center().add(
                        (Math.random() - 0.5) * 8, 3 + Math.random() * 3, 0));
                if (--stateTicks <= 0) {
                    stop("모든 테스트 완료");
                    return;
                }
            }
            default -> { }
        }
        if (!isRunning()) return;

        // 카메라
        if (character != null && character.isValid()) {
            double tu = character.u(), ty = character.y();
            if (state == GameState.DYING && deathSpot != null) {
                tu = frame.u(deathSpot);
                ty = Math.max(deathSpot.getY() - 0.9, def.killY);
            }
            if (boss != null && !boss.isGone() && state != GameState.DYING) {
                tu = (tu * 2 + frame.u(boss.center())) / 3.0;
            }
            camera.tick(tu, ty, character.velU());
        }

        // HUD
        if (hud != null) {
            if (character != null) hud.updateSlots(character.slots);
            if (boss != null) hud.setBossHealth(boss.healthFraction(), boss.enraged());
            if (timerTicks >= 0) hud.setTimer(timerTicks, state != GameState.PLAYING);
            hud.tick();
        }
    }

    private void tickIntro() {
        if (character != null) character.tick(input);
        boolean bossReady = true;
        if (boss != null) {
            boss.tick(character);
            bossReady = boss.introDone();
        }
        if (--stateTicks <= 0 && bossReady) beginPlay();
    }

    private void tickPlaying() {
        if (character == null || !character.isValid()) {
            die(DeathReason.FALL);
            return;
        }
        character.tick(input);
        if (input.consumeLeft()) abilities.use(character, 0);
        if (input.consumeRight()) abilities.use(character, 1);
        abilities.tick(character);

        DeathReason r = stage.tick(character);
        if (r == null && boss != null) r = boss.tick(character);
        if (r != null) {
            die(r);
            return;
        }

        if (boss == null) {
            if (stage.reachedGoal(character)) {
                clear();
                return;
            }
            timerTicks--;
            if (timerTicks % 20 == 0 && timerTicks > 0 && timerTicks <= 200) {
                fx.sound("block.note_block.hat", 1f, timerTicks <= 60 ? 2.0f : 1.4f);
                if (timerTicks <= 60) camera.shake(0.08);
            }
            if (timerTicks <= 0) {
                timerTicks = 0;
                die(DeathReason.TIMEOUT);
            }
        } else if (boss.isGone()) {
            clear();
        }
    }

    private void firework(Location at) {
        Firework fw = at.getWorld().spawn(at, Firework.class, f -> {
            FireworkMeta m = f.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.fromRGB(0x00E5FF), Color.fromRGB(0x5CFF8A)).withFade(Color.WHITE).flicker(true).build());
            m.setPower(0);
            f.setFireworkMeta(m);
        });
        fw.detonate();
    }

    // ------------------------------------------------------------------ utility

    public void shutdown() {
        stop("플러그인 비활성화");
    }

    /** 플레이어 원래 상태 저장/복구. */
    static final class SavedPlayer {
        GameMode mode;
        Location location;
        ItemStack[] contents;
        int level;
        float exp;
        double health;
        int food;
        Collection<PotionEffect> effects;

        static SavedPlayer capture(Player p) {
            SavedPlayer s = new SavedPlayer();
            s.mode = p.getGameMode();
            s.location = p.getLocation();
            s.contents = p.getInventory().getContents().clone();
            s.level = p.getLevel();
            s.exp = p.getExp();
            s.health = p.getHealth();
            s.food = p.getFoodLevel();
            s.effects = new ArrayList<>(p.getActivePotionEffects());
            return s;
        }

        void restore(Player p) {
            if (p.getVehicle() != null) p.leaveVehicle();
            CameraRig.clearSpectate(p);
            for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
            p.addPotionEffects(effects);
            p.setGameMode(mode);
            p.teleport(location);
            p.getInventory().setContents(contents);
            p.setLevel(level);
            p.setExp(exp);
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            double cap = max == null ? 20.0 : max.getValue();
            p.setHealth(Math.max(1, Math.min(health, cap)));
            p.setFoodLevel(food);
            p.setInvulnerable(false);
            p.setCollidable(true);
        }
    }
}
