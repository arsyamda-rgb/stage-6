package org.bukkit.scheduler; import org.bukkit.plugin.java.JavaPlugin;
public interface BukkitScheduler { Object runTask(JavaPlugin p, Runnable r); Object runTaskLater(JavaPlugin p, Runnable r, long d); Object runTaskTimer(JavaPlugin p, Runnable r, long d, long per); }
