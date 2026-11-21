package org.bukkit.configuration.file; import java.util.*;
public class FileConfiguration { public String getString(String p,String d){return d;} public String getString(String p){return null;} public boolean getBoolean(String p,boolean d){return d;}
 public int getInt(String p,int d){return d;} public long getLong(String p,long d){return d;} public double getDouble(String p,double d){return d;} public List<Map<?,?>> getMapList(String p){return List.of();} }
