package org.bukkit;
/** Check stub. Functional so a lambda can supply the scheduler. */
@FunctionalInterface public interface Server { org.bukkit.scheduler.BukkitScheduler getScheduler(); default org.bukkit.plugin.ServicesManager getServicesManager(){return null;} }
