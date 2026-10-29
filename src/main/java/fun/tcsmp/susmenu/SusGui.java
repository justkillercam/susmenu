package fun.tcsmp.susmenu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SusGui implements Listener {
    static final class Holder implements InventoryHolder {
        final Map<Integer, UUID> slots = new HashMap<>();
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final SusMenu plugin;
    private final FlagStore store;

    SusGui(SusMenu plugin, FlagStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @SuppressWarnings("deprecation")
    void open(Player staff) {
        int min = Math.max(1, plugin.getConfig().getInt("min-flags", 1));
        long expire = Math.max(0, plugin.getConfig().getLong("flag-expire-minutes", 60)) * 60_000L;
        int maxReasons = Math.max(1, plugin.getConfig().getInt("max-reasons", 5));

        // only players who are online can be spectated
        List<FlagStore.Suspect> list = new ArrayList<>();
        for (FlagStore.Suspect s : store.snapshot(min, expire)) {
            if (Bukkit.getPlayer(s.id()) != null) list.add(s);
        }
        if (list.isEmpty()) {
            staff.sendMessage(plugin.msg("nobody", ""));
            return;
        }

        int shown = Math.min(54, list.size());
        int rows = (shown + 8) / 9;
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9,
                Text.color(plugin.getConfig().getString("menu-title", "<red>Sus Menu")));
        holder.inventory = inv;

        for (int i = 0; i < shown; i++) {
            FlagStore.Suspect s = list.get(i);
            Player online = Bukkit.getPlayer(s.id());
            if (online == null) continue;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(online);
                meta.setDisplayName(Text.color("<red><bold>" + online.getName()));
                meta.setLore(lore(s, maxReasons));
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
            holder.slots.put(i, s.id());
        }
        staff.openInventory(inv);
    }

    private List<String> lore(FlagStore.Suspect s, int maxReasons) {
        List<String> lore = new ArrayList<>();
        lore.add(Text.color("<gray>Flags: <red><bold>" + s.flags().size()));

        Map<String, Integer> counts = new HashMap<>();
        for (FlagStore.Flag f : s.flags()) counts.merge(f.check(), 1, Integer::sum);
        List<Map.Entry<String, Integer>> reasons = new ArrayList<>(counts.entrySet());
        reasons.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        lore.add(Text.color("<gray>Why:"));
        for (int i = 0; i < reasons.size() && i < maxReasons; i++) {
            Map.Entry<String, Integer> r = reasons.get(i);
            lore.add(Text.color("<gray> - <white>") + r.getKey() + Text.color(" <gray>x" + r.getValue()));
        }
        if (reasons.size() > maxReasons) {
            lore.add(Text.color("<dark_gray>   +" + (reasons.size() - maxReasons) + " more"));
        }

        FlagStore.Flag last = s.flags().get(s.flags().size() - 1);
        lore.add(Text.color("<gray>Last: <white>") + last.check());
        if (last.info() != null) {
            String info = last.info().replaceAll("§.", "").replace('\n', ' ').trim();
            if (info.length() > 40) info = info.substring(0, 40) + "...";
            if (!info.isEmpty()) lore.add(Text.color("<dark_gray>") + info);
        }

        lore.add("");
        lore.add(Text.color("<yellow>Click to spectate"));
        return lore;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory()) return;
        if (!(e.getWhoClicked() instanceof Player staff)) return;

        UUID id = h.slots.get(e.getSlot());
        if (id == null) return;
        staff.closeInventory();
        if (!staff.hasPermission("susmenu.use")) return;
        plugin.spectate(staff, id);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
