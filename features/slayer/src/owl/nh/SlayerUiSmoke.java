package owl.nh;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.api.*;
public final class SlayerUiSmoke {
 public static void main(String[] args)throws Exception{Map<String,Object> values=new HashMap<>();values.put("getGameState",GameState.LOGIN_SCREEN);values.put("getPlayers",Collections.emptyList());values.put("getNpcs",Collections.emptyList());Client client=ChambersTests.fake(Client.class,values);java.lang.reflect.Constructor<net.runelite.client.callback.ClientThread> ctor=net.runelite.client.callback.ClientThread.class.getDeclaredConstructor(Client.class);ctor.setAccessible(true);CombatRuntime runtime=new CombatRuntime(client,ctor.newInstance(client),new net.runelite.client.eventbus.EventBus(),null);try{SwingUtilities.invokeAndWait(()->{JFrame window=runtime.slayer.window();try{window.addNotify();window.validate();BufferedImage image=new BufferedImage(window.getWidth(),window.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,image.getWidth(),image.getHeight());window.getRootPane().printAll(g);g.dispose();if(!ImageIO.write(image,"png",new java.io.File(args[0])))throw new IllegalStateException("PNG writer unavailable");}catch(Exception error){throw new RuntimeException(error);}finally{window.dispose();}});System.out.println("Rendered mock Slayer window; no game login or input.");}finally{runtime.olm.close();}}
}

