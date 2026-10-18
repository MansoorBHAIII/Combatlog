
package com.combatlog;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CombatLog extends JavaPlugin implements Listener {

    private static final long DURATION = 12_000L;

    private final Map<UUID, Long> expiry = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::updateBars, 1L, 5L);
        getLogger().info("CombatLog enabled!");
    }

    @Override
    public void onDisable() {
        bars.values().forEach(BossBar::removeAll);
        bars.clear();
        expiry.clear();
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof Player victim)
                || attacker.equals(victim)
                || event.isCancelled()) {
            return;
        }

        tag(attacker);
        tag(victim);
    }

    private void tag(Player player) {
        UUID id = player.getUniqueId();
        expiry.put(id, System.currentTimeMillis() + DURATION);

        BossBar bar = bars.computeIfAbsent(id, key ->
                Bukkit.createBossBar(
                        ChatColor.RED + "Combat " + ChatColor.WHITE + "12s",
                        BarColor.RED,
                        BarStyle.SOLID
                ));

        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }

        bar.setVisible(true);
        bar.setProgress(1.0);
        bar.setTitle(ChatColor.RED + "Combat "
                + ChatColor.WHITE + "12s");
    }

    private void updateBars() {
        long now = System.currentTimeMillis();

        for (UUID id : new java.util.HashSet<>(expiry.keySet())) {
            long remaining = expiry.get(id) - now;

            if (remaining <= 0) {
                expiry.remove(id);
                BossBar bar = bars.remove(id);
                if (bar != null) bar.removeAll();
                continue;
            }

            Player player = Bukkit.getPlayer(id);
            BossBar bar = bars.get(id);

            if (player != null && bar != null) {
                long seconds = (remaining + 999) / 1000;
                bar.setTitle(ChatColor.RED + "Combat "
                        + ChatColor.WHITE + seconds + "s");
                bar.setProgress(Math.max(0.0,
                        Math.min(1.0, remaining / (double) DURATION)));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        if (!expiry.containsKey(id)) return;

        Location location = player.getLocation().clone();

        // Drop every carried item, including armor and offhand.
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                location.getWorld().dropItemNaturally(location, item.clone());
            }
        }

        // Clear inventory to avoid item duplication.
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);

        expiry.remove(id);
        BossBar bar = bars.remove(id);
        if (bar != null) bar.removeAll();

        Bukkit.broadcastMessage(ChatColor.RED + player.getName()
                + " logged out during combat and dropped their items!");
    }
}
