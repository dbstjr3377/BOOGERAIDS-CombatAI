package com.boogeraids.combatai.listener;

import com.boogeraids.combatai.BoogerAIDS;
import com.boogeraids.combatai.game.GameManager;
import com.boogeraids.combatai.util.Tags;
import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class GameListener implements Listener {

    private final BoogerAIDS plugin;

    public GameListener(BoogerAIDS plugin) {
        this.plugin = plugin;
    }

    private GameManager game() {
        return plugin.game();
    }

    // ------------------------------------------------------------------ join / quit

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (game().isRunning()) {
            game().join(p);
        } else {
            plugin.packService().send(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        game().leave(e.getPlayer());
        plugin.packService().setLoaded(e.getPlayer().getUniqueId(), false);
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent e) {
        if (!plugin.packService().isOurs(e.getID())) return;
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> plugin.packService().setLoaded(e.getPlayer().getUniqueId(), true);
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED ->
                    plugin.packService().setLoaded(e.getPlayer().getUniqueId(), false);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ input

    @EventHandler
    public void onInput(PlayerInputEvent e) {
        game().onInput(e.getPlayer(), e.getInput());
    }

    /** 좌클릭: 시선 앞 Interaction 공격. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        if (!game().isParticipant(e.getPlayer())) return;
        e.setCancelled(true);
        if (isClicker(e.getAttacked())) game().onClick(e.getPlayer(), true);
    }

    /** 우클릭: 시선 앞 Interaction 상호작용. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!game().isParticipant(e.getPlayer())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (isClicker(e.getRightClicked())) game().onClick(e.getPlayer(), false);
    }

    /** 허공 좌클릭 보조 판정 (Interaction 을 놓친 경우). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (!game().isParticipant(e.getPlayer())) return;
        e.setCancelled(true);
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            game().onClick(e.getPlayer(), true);
        }
    }

    private boolean isClicker(Entity e) {
        return game().camera().clicker() != null && game().camera().clicker().equals(e);
    }

    // ------------------------------------------------------------------ locks

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!game().isController(p)) return;
        if (game().camera().isDismountAllowed()) return;
        if (e.getDismounted().equals(game().camera().seat())) e.setCancelled(true);
    }

    @EventHandler
    public void onStopSpectating(PlayerStopSpectatingEntityEvent e) {
        if (game().isRunning() && game().camera().isViewer(e.getPlayer())
                && e.getSpectatorTarget().equals(game().camera().cam())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && game().isParticipant(e.getPlayer())
                && game().isRunning()) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        Entity en = e.getEntity();
        if (en.getScoreboardTags().contains(Tags.ENTITY)) {
            e.setCancelled(true);
            return;
        }
        if (en instanceof Player p && game().isParticipant(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && game().isParticipant(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (game().isParticipant(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (game().isParticipant(e.getPlayer())) e.setCancelled(true);
    }
}
