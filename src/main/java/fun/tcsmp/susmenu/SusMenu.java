package fun.tcsmp.susmenu;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class SusMenu extends JavaPlugin {
    private static final String DEFAULT_PREFIX = "<gray>[<red>SUS<gray>]";

    private final FlagStore store = new FlagStore();
    private AntiCheatHook hook;
    private boolean folia;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        folia = classExists("io.papermc.paper.threadedregions.RegionizedServer");

        hook = new AntiCheatHook(this, store);
        SusGui gui = new SusGui(this, store);
        getServer().getPluginManager().registerEvents(gui, this);

        SusCommand cmd = new SusCommand(this, gui);
        PluginCommand pc = getCommand("sus");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        connect();
    }

    private void connect() {
        hook.hook(getConfig().getString("anti-cheat", "grim"), getConfig().getString("event-class", ""));
    }

    /** /sus reload */
    void reloadAll() {
        saveDefaultConfig();
        reloadConfig();
        HandlerList.unregisterAll(hook);
        connect();
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Puts the staff member in spectator mode and teleports them to the target. */
    void spectate(Player staff, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            staff.sendMessage(msg("target-offline", ""));
            return;
        }
        if (folia) {
            FoliaSupport.spectate(this, staff, target);
        } else {
            staff.setGameMode(GameMode.SPECTATOR);
            staff.teleport(target.getLocation());
        }
        staff.sendMessage(msg("spectating", target.getName()));
    }

    String prefix() {
        String p = getConfig().getString("prefix", "");
        return Text.color(p == null || p.isBlank() ? DEFAULT_PREFIX : p);
    }

    String msg(String key, String target) {
        String m = getConfig().getString("messages." + key, key);
        return prefix() + " " + Text.color(m.replace("{target}", target));
    }
}
