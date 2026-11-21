package org.bukkit; import java.util.UUID; import org.bukkit.entity.Player;
public interface OfflinePlayer { UUID getUniqueId(); String getName(); boolean hasPlayedBefore(); Player getPlayer(); boolean isOnline(); }
