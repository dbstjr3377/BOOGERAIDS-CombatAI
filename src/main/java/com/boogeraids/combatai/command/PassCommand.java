package com.boogeraids.combatai.command;

import com.boogeraids.combatai.BoogerAIDS;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /너해봐 &lt;플레이어&gt; (별칭 /pass) - 통제권을 가진 플레이어가 다른 참가자에게 조작을 넘긴다. */
public final class PassCommand implements TabExecutor {

    private final BoogerAIDS plugin;

    public PassCommand(BoogerAIDS plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (a.length < 1) {
            p.sendMessage(Component.text("사용법: /너해봐 <플레이어>", NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayerExact(a[0]);
        String err = plugin.game().passControl(p, target);
        if (err != null) {
            p.sendMessage(Component.text("[BC] ", NamedTextColor.AQUA).append(Component.text(err, NamedTextColor.RED)));
        } else {
            target.sendMessage(Component.text("[BC] ", NamedTextColor.AQUA)
                    .append(Component.text(p.getName() + " 님이 통제권을 넘겼습니다. 너가 해봐!", NamedTextColor.WHITE)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length != 1 || !(s instanceof Player self) || !plugin.game().isRunning()) return out;
        for (Player p : plugin.game().audience()) {
            if (p.getUniqueId().equals(self.getUniqueId())) continue;
            if (p.getName().toLowerCase().startsWith(a[0].toLowerCase())) out.add(p.getName());
        }
        return out;
    }
}
