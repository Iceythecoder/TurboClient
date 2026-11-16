package owl.nh;
import java.util.*;

/** Recorded approach tiles for the two observed mirrored mage spawns; no fabricated arena coordinates. */
final class OlmArenaPlan {
 static String key(int x,int y,int plane){return x+":"+y+":"+plane;}
 static Map<String,int[]> learn(ChambersDemonstration.Saved demo,OlmFighter.Profile profile){return learnRole(demo,profile.mageHands);}
 static Map<String,int[]> learnMelee(ChambersDemonstration.Saved demo,OlmFighter.Profile profile){return learnRole(demo,profile.meleeHands);}
 private static Map<String,int[]> learnRole(ChambersDemonstration.Saved demo,Set<Integer> roles){
  Map<String,Map<String,Integer>> counts=new TreeMap<>();Map<String,int[]> tiles=new HashMap<>();
  for(ChambersDemonstration.Action action:demo.actions){if(action.before==null||action.option==null||!action.option.equalsIgnoreCase("Attack")||!roles.contains(action.targetNpcId))continue;ChambersDemonstration.Frame f=action.before;
   for(int[] npc:f.npcs){if(npc.length<5||npc[0]!=action.targetNpcId||npc[4]==0)continue;int distance=Math.max(Math.abs(f.x-npc[1]),Math.abs(f.y-npc[2]));if(distance<1||distance>10)continue;String spawn=key(npc[1],npc[2],f.plane),tile=key(f.x,f.y,f.plane);counts.computeIfAbsent(spawn,k->new TreeMap<>()).merge(tile,1,Integer::sum);tiles.put(tile,new int[]{f.x,f.y,f.plane});}
  }
  Map<String,int[]> out=new TreeMap<>();for(Map.Entry<String,Map<String,Integer>> spawn:counts.entrySet()){String best=null;int n=0;for(Map.Entry<String,Integer> tile:spawn.getValue().entrySet())if(tile.getValue()>n){best=tile.getKey();n=tile.getValue();}if(best!=null&&n>=2&&out.size()<8)out.put(spawn.getKey(),tiles.get(best));}return out;
 }
 static int[] opposite(Map<String,int[]> map,String previous){if(previous==null||map.size()!=2||!map.containsKey(previous))return null;for(Map.Entry<String,int[]> e:map.entrySet())if(!e.getKey().equals(previous))return e.getValue().clone();return null;}
 static Map<String,List<int[]>> firingCamps(ChambersDemonstration.Saved demo,OlmFighter.Profile profile){
  Map<String,Map<OlmSpecials.Cell,Integer>> counts=new TreeMap<>();if(demo==null||demo.actions==null)return Collections.emptyMap();
  for(ChambersDemonstration.Action action:demo.actions){if(action.before==null||!"Attack".equalsIgnoreCase(action.option)||!profile.heads.contains(action.targetNpcId)&&!profile.mageHands.contains(action.targetNpcId))continue;ChambersDemonstration.Frame f=action.before;for(int[] npc:f.npcs){if(npc.length<5||npc[0]!=action.targetNpcId||npc[4]==0)continue;OlmSpecials.Cell tile=new OlmSpecials.Cell(f.x,f.y,f.plane);if(tile.distance(new OlmSpecials.Cell(npc[1],npc[2],f.plane))>16)continue;counts.computeIfAbsent(action.targetNpcId+":"+key(npc[1],npc[2],f.plane),k->new HashMap<>()).merge(tile,1,Integer::sum);}}
  Map<String,List<int[]>> result=new TreeMap<>();for(Map.Entry<String,Map<OlmSpecials.Cell,Integer>> spawn:counts.entrySet()){if(result.size()>=16)break;List<OlmSpecials.Cell> tiles=new ArrayList<>(spawn.getValue().keySet());tiles.sort(Comparator.<OlmSpecials.Cell>comparingInt(c->-spawn.getValue().get(c)).thenComparingInt(c->c.x).thenComparingInt(c->c.y));List<int[]> selected=new ArrayList<>();OlmSpecials.Cell anchor=tiles.get(0);for(OlmSpecials.Cell tile:tiles)if(selected.size()<4&&spawn.getValue().get(tile)>=2&&tile.distance(anchor)<=4)selected.add(new int[]{tile.x,tile.y,tile.plane});if(!selected.isEmpty())result.put(spawn.getKey(),selected);}return result;
 }
 static void validate(Map<String,int[]> map){if(map==null||map.size()>8)throw new IllegalArgumentException("Invalid mage approach map");for(Map.Entry<String,int[]> e:map.entrySet()){int[] p=e.getValue();if(!e.getKey().matches("[0-9]{1,5}:[0-9]{1,5}:[0-3]")||p==null||p.length!=3||p[0]<0||p[0]>16383||p[1]<0||p[1]>16383||p[2]<0||p[2]>3)throw new IllegalArgumentException("Invalid recorded mage approach tile");}}
}
