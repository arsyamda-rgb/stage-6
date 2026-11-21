package org.bukkit.plugin.java; import java.util.logging.Logger; import java.io.File;
public abstract class JavaPlugin { public Logger getLogger(){return null;} public org.bukkit.Server getServer(){return null;} public boolean isEnabled(){return true;}
 public File getDataFolder(){return null;} public void saveDefaultConfig(){} public void reloadConfig(){} public org.bukkit.configuration.file.FileConfiguration getConfig(){return null;} }
