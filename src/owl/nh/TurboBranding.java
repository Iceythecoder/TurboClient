package owl.nh;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
final class TurboBranding {
 static BufferedImage logo(){try{return ImageIO.read(new File(CombatRuntime.projectRoot(),"assets/turbo-logo.png"));}catch(IOException e){return null;}}
 static void apply(Window window){BufferedImage logo=logo();if(logo!=null)window.setIconImages(java.util.Arrays.asList(logo.getScaledInstance(32,32,Image.SCALE_SMOOTH),logo.getScaledInstance(48,48,Image.SCALE_SMOOTH),logo.getScaledInstance(256,256,Image.SCALE_SMOOTH)));}
}
