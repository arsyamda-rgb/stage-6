package org.bukkit.command; import net.kyori.adventure.text.Component;
public interface CommandSender { boolean hasPermission(String p); void sendMessage(Component c); String getName(); }
