/** Loads the patched Slayer class so -Xverify:all checks every stack map. */
public final class VerifySlayerLoad {
 public static void main(String[] args) throws Exception {
  Class.forName("owl.nh.SlayerController", false, VerifySlayerLoad.class.getClassLoader());
  System.out.println("SlayerController verified and loaded.");
 }
}
