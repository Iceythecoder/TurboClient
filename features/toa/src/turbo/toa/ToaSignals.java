package turbo.toa;

/** Verified OSRS/Orion Warden signal identities observed in the local run trace. */
final class ToaSignals {
 static boolean spinningLight(int id){return id==2197||id==2198;}
 static boolean bombProjectile(int id){return id==2225||id==2226;}
 static String protection(int id){
  if(id==2204)return "MELEE";
  if(id==2206)return "RANGED";
  if(id==2208)return "MAGIC";
  // Visually verified against the matching 2026-10-31 Orion screenshots and
  // runtime cycles: 2224 is the red skull; 2241 is the grey projectile.
  if(id==2224)return "MAGIC";
  if(id==2241)return "RANGED";
  return null;
 }
 private ToaSignals(){}
}
