package com.veyrion.character.race;
import java.util.*;
public class RaceChanceCheck {
  static int fails=0;
  static void check(boolean ok,String m){ if(!ok){fails++;System.out.println("FAIL "+m);} }
  static double sum(List<RaceChanceCalculator.Chance> l){double s=0;for(var c:l)s+=c.chance();return s;}
  public static void main(String[] a){
    var e=List.of(new RaceChanceCalculator.Entry("human",35),new RaceChanceCalculator.Entry("elf",30),new RaceChanceCalculator.Entry("beast",25),new RaceChanceCalculator.Entry("dragon",10));
    var p=new RaceChanceCalculator.Params(true,5,1.0,0.5,2.0,0.05);
    // 1 empty db -> base weights
    var r=RaceChanceCalculator.compute(e,Map.of(),p);
    check(Math.abs(sum(r)-1)<1e-9,"sum empty");
    check(Math.abs(r.get(0).chance()-0.35)<1e-9 && Math.abs(r.get(3).chance()-0.10)<1e-9,"empty = base");
    // 2 dragon rare -> more chance; human overpopulated -> less
    var r2=RaceChanceCalculator.compute(e,Map.of("human",300L,"elf",30L,"beast",25L,"dragon",1L),p);
    check(r2.get(3).chance()>0.10,"dragon up"); check(r2.get(0).chance()<0.35,"human down");
    check(Math.abs(sum(r2)-1)<1e-9,"sum r2");
    for(var c:r2){check(c.multiplier()>=0.5&&c.multiplier()<=2.0,"mult bound "+c.id());check(c.chance()>=0.05-1e-9&&c.chance()<=1,"floor/ceil "+c.id());}
    // 3 extreme: one race huge, floor must hold, no negative/NaN
    var r3=RaceChanceCalculator.compute(e,Map.of("human",1_000_000_000L),p);
    for(var c:r3){check(!Double.isNaN(c.chance())&&c.chance()>=0.05-1e-9,"extreme "+c.id()+" "+c.chance());}
    check(Math.abs(sum(r3)-1)<1e-9,"sum r3");
    // 4 floor too big -> capped to uniform
    var r4=RaceChanceCalculator.compute(e,Map.of(),new RaceChanceCalculator.Params(true,5,1,0.5,2,0.9));
    for(var c:r4)check(Math.abs(c.chance()-0.25)<1e-9,"uniform");
    // 5 single race
    var r5=RaceChanceCalculator.compute(List.of(new RaceChanceCalculator.Entry("x",1)),Map.of("x",9L),p);
    check(Math.abs(r5.get(0).chance()-1)<1e-12,"single");
    // 6 dynamic off
    var r6=RaceChanceCalculator.compute(e,Map.of("human",999L),new RaceChanceCalculator.Params(false,5,1,0.5,2,0));
    check(Math.abs(r6.get(0).chance()-0.35)<1e-9,"dynamic off");
    // 7 alpha huge no NaN
    var r7=RaceChanceCalculator.compute(e,Map.of("human",50L),new RaceChanceCalculator.Params(true,1e-9,1e6,1e-6,1e6,0.01));
    for(var c:r7)check(Double.isFinite(c.chance()),"alpha huge");
    check(Math.abs(sum(r7)-1)<1e-9,"sum r7");
    // 8 randomized fuzz
    Random rnd=new Random(1);
    for(int t=0;t<20000;t++){
      int n=1+rnd.nextInt(8); var l=new ArrayList<RaceChanceCalculator.Entry>(); var pop=new HashMap<String,Long>();
      for(int i=0;i<n;i++){l.add(new RaceChanceCalculator.Entry("r"+i,0.01+rnd.nextDouble()*100)); pop.put("r"+i,(long)(rnd.nextDouble()*rnd.nextInt(100000)));}
      double mn=rnd.nextDouble()*(1.0/n)*1.2; var pr=new RaceChanceCalculator.Params(rnd.nextBoolean(),0.1+rnd.nextDouble()*50,rnd.nextDouble()*4,0.1+rnd.nextDouble()*0.9,1+rnd.nextDouble()*9,Math.min(1,mn));
      var res=RaceChanceCalculator.compute(l,pop,pr);
      check(Math.abs(sum(res)-1)<1e-9,"fuzz sum");
      double fl=Math.min(pr.minChance(),1.0/n);
      for(var c:res){check(c.chance()>=fl-1e-9&&c.chance()<=1+1e-12&&!Double.isNaN(c.chance()),"fuzz bounds");}
      var pk=RaceChanceCalculator.pick(res,0.999999999999); check(pk!=null,"pick");
    }
    // 9 pick distribution
    var rr=new java.util.random.RandomGenerator[]{new Random(7)}[0]; Map<String,Integer> cnt=new HashMap<>();
    for(int i=0;i<200000;i++)cnt.merge(RaceChanceCalculator.pick(r2,rr).id(),1,Integer::sum);
    for(var c:r2)check(Math.abs(cnt.getOrDefault(c.id(),0)/200000.0-c.chance())<0.01,"dist "+c.id());
    // 10 invalid params rejected
    try{new RaceChanceCalculator.Params(true,0,1,.5,2,0);check(false,"k=0 accepted");}catch(IllegalArgumentException ok){}
    r2.forEach(c->System.out.printf(Locale.ROOT,"%s pop=%d exp=%.1f x%.2f -> %.2f%%%n",c.id(),c.population(),c.expected(),c.multiplier(),c.chance()*100));
    System.out.println(fails==0?"ALL CHECKS PASSED":"FAILED: "+fails);
    System.exit(fails==0?0:1);
  }
}
