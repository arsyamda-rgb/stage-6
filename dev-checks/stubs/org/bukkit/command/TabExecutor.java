package org.bukkit.command; import java.util.List;
public interface TabExecutor { boolean onCommand(CommandSender s, Command c, String l, String[] a); List<String> onTabComplete(CommandSender s, Command c, String l, String[] a); }
