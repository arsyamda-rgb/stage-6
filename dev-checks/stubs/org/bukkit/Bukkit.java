package org.bukkit; import java.util.*; import org.bukkit.entity.Player;
public final class Bukkit { public static OfflinePlayer getOfflinePlayer(UUID u){return null;} public static Player getPlayerExact(String n){return null;} public static Player getPlayer(UUID u){return null;}
 public static OfflinePlayer getOfflinePlayerIfCached(String n){return null;} public static Collection<? extends Player> getOnlinePlayers(){return List.of();} public static boolean isPrimaryThread(){return true;} }
