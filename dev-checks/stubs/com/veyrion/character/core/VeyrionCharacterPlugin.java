package com.veyrion.character.core;
import java.util.ArrayDeque; import java.util.logging.Logger; import org.bukkit.plugin.java.JavaPlugin;
/** COMPILE/CHECK STUB ONLY. Runs scheduled tasks from a queue so checks can step "ticks" by hand. */
public class VeyrionCharacterPlugin extends JavaPlugin {
    public final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    public int ticks;
    private final Logger logger = Logger.getLogger("check");
    public java.util.List<String> warnings = new java.util.ArrayList<>();
    @Override public Logger getLogger() { return logger; }
    @Override public org.bukkit.Server getServer() {
        return () -> new org.bukkit.scheduler.BukkitScheduler() {
            public Object runTask(JavaPlugin p, Runnable r) { tasks.add(r); return null; }
            public Object runTaskLater(JavaPlugin p, Runnable r, long d) { tasks.add(r); return null; }
            public Object runTaskTimer(JavaPlugin p, Runnable r, long d, long per) { return null; }
        };
    }
    /** Runs one tick: every task queued so far (tasks queued meanwhile wait for the next tick). */
    public void tick() { ticks++; int n = tasks.size(); for (int i = 0; i < n; i++) tasks.poll().run(); }
    public com.veyrion.character.config.Messages messages(){return null;} public com.veyrion.character.config.ConfigManager configManager(){return null;}
    public void reloadEconomy(){} public com.veyrion.character.economy.VyrPayouts vyrPayouts(){return null;}
    public void announceExp(java.util.UUID u, com.veyrion.character.character.ExpResult r,long g){}
}
