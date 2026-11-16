package owl.nh;

/** Actor-attached crystal markers are separate from scene GraphicsObjects. */
final class OlmCrystalMarker {
 private OlmSpecials.Cell marked;private int markedTick=-1000,lastSeen=-1000;private boolean active;
 void reset(){marked=null;markedTick=lastSeen=-1000;active=false;}
 void observe(boolean crystalSignal,OlmSpecials.Cell player,int tick){
  observe(crystalSignal,player,tick,false);
 }
 void observe(boolean crystalSignal,OlmSpecials.Cell player,int tick,boolean newMarker){if(crystalSignal){lastSeen=tick;if(!active||newMarker){marked=player;markedTick=tick;}}active=crystalSignal;}
 void add(OlmSpecials.View view,int radius){if(marked!=null&&view.tick-lastSeen<=2&&view.tick>=lastSeen)view.hazards.add(new OlmSpecials.Hazard(marked,OlmSpecials.HazardKind.FALLING,radius));}
}
