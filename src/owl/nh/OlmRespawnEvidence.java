package owl.nh;
/** A lobby position alone is never evidence of a death or a return from combat. */
final class OlmRespawnEvidence {
 private OlmSpecials.Cell fight;private int first=-1;
 void reset(){fight=null;first=-1;}
 void combat(OlmSpecials.Cell position){if(position!=null)fight=position;}
 boolean returned(int tick,boolean entered,OlmSpecials.Cell position,boolean preRoom){
  if(!entered||fight==null||position==null||!preRoom||fight.distance(position)<6){first=-1;return false;}
  if(first<0||tick<first){first=tick;return false;}return tick>first;
 }
}
