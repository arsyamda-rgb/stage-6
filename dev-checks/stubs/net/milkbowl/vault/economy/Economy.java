package net.milkbowl.vault.economy; import org.bukkit.OfflinePlayer;
public interface Economy { boolean isEnabled(); String getName(); double getBalance(OfflinePlayer p); boolean has(OfflinePlayer p,double a); EconomyResponse depositPlayer(OfflinePlayer p,double a); EconomyResponse withdrawPlayer(OfflinePlayer p,double a); }
