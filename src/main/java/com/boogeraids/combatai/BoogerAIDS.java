package com.boogeraids.combatai;

import com.boogeraids.combatai.command.BcCommand;
import com.boogeraids.combatai.config.GameConfig;
import com.boogeraids.combatai.game.GameManager;
import com.boogeraids.combatai.listener.GameListener;
import com.boogeraids.combatai.pack.ResourcePackService;
import com.boogeraids.combatai.util.Tags;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BoogerAIDS extends JavaPlugin {

    private GameConfig gameConfig;
    private GameManager game;
    private ResourcePackService pack;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadGameConfig();
        int swept = Tags.sweep();
        if (swept > 0) getLogger().info("이전 세션에서 남은 엔티티 " + swept + "개 정리");

        pack = new ResourcePackService(this);
        pack.start();
        game = new GameManager(this);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        PluginCommand cmd = getCommand("boogeraids");
        if (cmd != null) {
            BcCommand bc = new BcCommand(this);
            cmd.setExecutor(bc);
            cmd.setTabCompleter(bc);
        }
        getLogger().info("BOOGERAIDS // CombatAI 활성화 - 챕터 " + gameConfig.chapters.size() + "개");
    }

    @Override
    public void onDisable() {
        if (game != null) game.shutdown();
        if (pack != null) pack.stop();
        Tags.sweep();
    }

    public void reloadGameConfig() {
        reloadConfig();
        gameConfig = GameConfig.load(getConfig(), getLogger());
    }

    public GameConfig gameConfig() {
        return gameConfig;
    }

    public GameManager game() {
        return game;
    }

    public ResourcePackService packService() {
        return pack;
    }
}
