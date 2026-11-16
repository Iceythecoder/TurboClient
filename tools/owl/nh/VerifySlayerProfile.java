package owl.nh;

/** Confirms ID-independent Slayer stat lookup remains available for Orion variants. */
public final class VerifySlayerProfile {
 public static void main(String[] args) {
  SlayerNpcProfile.Result darkBeast=SlayerNpcProfile.chooseByName("Dark beast");
  SlayerNpcProfile.Result abyssalDemon=SlayerNpcProfile.chooseByName("Abyssal demon");
  if(darkBeast==null||abyssalDemon==null)throw new AssertionError("Named Slayer profiles were not loaded");
  System.out.println("Named Slayer profiles loaded: "+darkBeast.attackStyle+" / "+darkBeast.protectionPrayer+"; "+abyssalDemon.attackStyle+" / "+abyssalDemon.protectionPrayer);
 }
}
