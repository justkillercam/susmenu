package fun.tcsmp.susmenu;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SusCommand implements CommandExecutor, TabCompleter {
    private final SusMenu plugin;
    private final SusGui gui;

    SusCommand(SusMenu plugin, SusGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("susmenu.reload")) {
                sender.sendMessage(plugin.msg("no-permission", ""));
                return true;
            }
            plugin.reloadAll();
            sender.sendMessage(plugin.msg("reloaded", ""));
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.msg("players-only", ""));
            return true;
        }
        if (!p.hasPermission("susmenu.use")) {
            p.sendMessage(plugin.msg("no-permission", ""));
            return true;
        }
        gui.open(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("susmenu.reload")
                && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            out.add("reload");
        }
        return out;
    }
}
