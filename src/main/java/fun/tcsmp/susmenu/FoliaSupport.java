package fun.tcsmp.susmenu;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Only loaded on Folia, so Spigot/Bukkit never touch the entity scheduler. */
final class FoliaSupport {
    private FoliaSupport() {}

    static void spectate(Plugin plugin, Player staff, Player target) {
        // read the target's location on the target's thread, then move staff on staff's own thread
        target.getScheduler().run(plugin, t -> {
            Location loc = target.getLocation();
            staff.getScheduler().run(plugin, t2 -> {
                staff.setGameMode(GameMode.SPECTATOR);
                staff.teleportAsync(loc);
            }, null);
        }, null);
    }
}
