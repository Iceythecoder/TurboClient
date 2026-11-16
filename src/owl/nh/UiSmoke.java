package owl.nh;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;

/** Offline GUI rendering with a mock API: no client startup, network or input. */
public final class UiSmoke {
 static JButton findAppButton(PrototypePanel panel,String text){for(Window window:panel.independentWindows())if(window instanceof RootPaneContainer){JButton found=findButton(((RootPaneContainer)window).getContentPane(),text);if(found!=null)return found;}return null;}
 public static void main(String[] args)throws Exception {System.setProperty("owl.ui.smoke","true");
  Client client=(Client)Proxy.newProxyInstance(Client.class.getClassLoader(),new Class<?>[]{Client.class},(proxy,method,values)->{
   if(method.getName().equals("isClientThread"))return true;
   if(method.getName().equals("getGameState"))return GameState.LOGIN_SCREEN;
   if(method.getName().equals("getPlayers"))return java.util.Collections.emptyList();
   if(method.getName().equals("getPlayerOptions"))return new String[0];
   Class<?> type=method.getReturnType();if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;return null;
  });
  Constructor<ClientThread> constructor=ClientThread.class.getDeclaredConstructor(Client.class);constructor.setAccessible(true);ClientThread thread=constructor.newInstance(client);
  CombatRuntime runtime=new CombatRuntime(client,thread,new EventBus(),null);
  runtime.nhTraining.check();awaitJob(runtime.nhTraining);
  runtime.nhTraining.evaluate("nh-starter",new File(CombatRuntime.projectRoot(),"build/stage1-checks/models/nh-starter-ppo.zip"),"fixed-ranged",3,32,true,0);awaitJob(runtime.nhTraining);
  CoreTests.check(runtime.nhTraining.fightFrame!=null,"actual simulator viewer snapshot");
  CoreTests.check(runtime.nhTraining.episodeReturns().size()==3,"viewer completed fight history");
  SwingUtilities.invokeAndWait(()->{
   try {
    PrototypePanel panel=new PrototypePanel(runtime);panel.setLocation(-10000,-10000);panel.setVisible(true);panel.validate();
    int i=0;for(Window window:panel.independentWindows()){if(window==panel)continue;window.setLocation(-10000,-10000);window.setVisible(true);window.validate();BufferedImage image=new BufferedImage(window.getWidth(),window.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();window.paint(graphics);graphics.dispose();ImageIO.write(image,"png",new File("ui-tab-"+(i++)+".png"));}
    CoreTests.check(findAppButton(panel,"Watch NH fighter")!=null,"separate trainer retains fighter control");
    CoreTests.check(findAppButton(panel,"Agent inventory")!=null,"separate trainer retains inventory control");
    CoreTests.check(findAppButton(panel,"Open Chambers teaching window")!=null,"separate Chambers entry point");
    java.util.List<Window> sections=panel.independentWindows();Window section=sections.get(0),sibling=sections.get(1);section.dispatchEvent(new java.awt.event.WindowEvent(section,java.awt.event.WindowEvent.WINDOW_CLOSING));CoreTests.check(!section.isVisible()&&sibling.isVisible(),"closing one section leaves its sibling visible");IndependentWindows.show(section);CoreTests.check(section.isVisible(),"closed section reopens with retained controls");panel.dispatchEvent(new java.awt.event.WindowEvent(panel,java.awt.event.WindowEvent.WINDOW_CLOSING));CoreTests.check(!panel.isVisible()&&sibling.isVisible(),"closing navigation leaves independent tools visible");IndependentWindows.show(panel);
    ChambersTeachingWindow dashboard=new ChambersTeachingWindow(panel,runtime);dashboard.setLocation(-10000,-10000);dashboard.setVisible(true);dashboard.validate();
    CoreTests.check(findButton(dashboard.getContentPane(),"Learn")!=null&&findButton(dashboard.getContentPane(),"Run bot")!=null,"simple Chambers dashboard");
    CoreTests.check(findButton(dashboard.getContentPane(),"Start basic test")==null&&findButton(dashboard.getContentPane(),"Retry save")!=null,"single main runner and recoverable local saves");
    JDialog floatingStop=dashboard.stopControl();floatingStop.setLocation(-10000,-10000);floatingStop.setVisible(true);floatingStop.validate();
    dashboard.dispatchEvent(new java.awt.event.WindowEvent(dashboard,java.awt.event.WindowEvent.WINDOW_CLOSING));CoreTests.check(!dashboard.isVisible()&&floatingStop.isVisible(),"closing Chambers leaves floating Stop available");IndependentWindows.show(dashboard);
    JLabel dragHandle=null;for(Component component:((Container)floatingStop.getContentPane()).getComponents())if(component instanceof JLabel)dragHandle=(JLabel)component;
    CoreTests.check(dragHandle!=null&&floatingStop.isAlwaysOnTop(),"movable always-on-top stop control");
    java.awt.Point beforeDrag=floatingStop.getLocation();
    java.awt.event.MouseEvent press=new java.awt.event.MouseEvent(dragHandle,java.awt.event.MouseEvent.MOUSE_PRESSED,0,0,4,4,100,100,1,false,java.awt.event.MouseEvent.BUTTON1);
    for(java.awt.event.MouseListener listener:dragHandle.getMouseListeners())listener.mousePressed(press);
    java.awt.event.MouseEvent move=new java.awt.event.MouseEvent(dragHandle,java.awt.event.MouseEvent.MOUSE_DRAGGED,0,0,30,40,-9900,-9800,0,false,java.awt.event.MouseEvent.NOBUTTON);
    for(java.awt.event.MouseMotionListener listener:dragHandle.getMouseMotionListeners())listener.mouseDragged(move);
    CoreTests.check(!floatingStop.getLocation().equals(beforeDrag),"stop palette moves when dragged");
    runtime.armed=true;runtime.chambers.armed=true;runtime.demonstration.recording=true;runtime.olm.running=true;findButton(floatingStop.getContentPane(),"STOP  (F9)").doClick();
    CoreTests.check(!runtime.armed&&!runtime.chambers.armed&&!runtime.demonstration.recording&&!runtime.olm.running,"floating stop disables inputs and recording");
    BufferedImage stopImage=new BufferedImage(floatingStop.getWidth(),floatingStop.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D stopGraphics=stopImage.createGraphics();floatingStop.paint(stopGraphics);stopGraphics.dispose();ImageIO.write(stopImage,"png",new File("ui-chambers-stop.png"));
    BufferedImage dashboardImage=new BufferedImage(dashboard.getWidth(),dashboard.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D dashboardGraphics=dashboardImage.createGraphics();dashboard.paint(dashboardGraphics);dashboardGraphics.dispose();ImageIO.write(dashboardImage,"png",new File("ui-chambers-dashboard.png"));dashboard.dispose();
    CoreTests.check(findButton(dashboard.getContentPane(),"Confirm chest looted + save preset")!=null&&findButton(dashboard.getContentPane(),"Load saved run...")!=null,"confirmed run save and reload controls");ChambersLearningWindow chambers=new ChambersLearningWindow(panel,runtime,true);chambers.setLocation(-10000,-10000);chambers.setVisible(true);chambers.validate();
    JTabbedPane chamberTabs=findTabs(chambers.getContentPane());chamberTabs.setSelectedIndex(1);chambers.validate();
    CoreTests.check(findButton(chambers.getContentPane(),"Apply + observe")!=null&&findButton(chambers.getContentPane(),"Arm prayer switching")!=null,"explicit Chambers observe and arm controls");
    CoreTests.check(findButton(chambers.getContentPane(),"Save prayer timing...")!=null&&findButton(chambers.getContentPane(),"Load prayer timing...")!=null,"prayer timing persistence controls");
    CoreTests.check(findButton(chambers.getContentPane(),"Start demonstration")!=null&&findButton(chambers.getContentPane(),"Learn from demonstration")!=null,"demonstration recorder and learner controls");
    BufferedImage chamberImage=new BufferedImage(chambers.getWidth(),chambers.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D chamberGraphics=chamberImage.createGraphics();chambers.paint(chamberGraphics);chamberGraphics.dispose();ImageIO.write(chamberImage,"png",new File("ui-chambers-prayers.png"));
    chamberTabs.setSelectedIndex(2);chambers.validate();chamberGraphics=chamberImage.createGraphics();chambers.paint(chamberGraphics);chamberGraphics.dispose();ImageIO.write(chamberImage,"png",new File("ui-chambers-demonstration.png"));
    chamberTabs.setSelectedIndex(3);chambers.validate();CoreTests.check(findButton(chambers.getContentPane(),"Save Olm profile")!=null&&findButton(chambers.getContentPane(),"Load saved Olm profile")!=null&&findButton(chambers.getContentPane(),"Apply Olm profile")!=null&&findButton(chambers.getContentPane(),"Teach target correction")!=null,"Olm baseline configuration and feedback");findButton(chambers.getContentPane(),"Load observed candidates").doClick();java.util.List<JTextArea> loadedAreas=new java.util.ArrayList<>();collectAreas(chambers.getContentPane(),loadedAreas);CoreTests.check(loadedAreas.stream().anyMatch(a->a.isEditable()&&a.getText().contains("observedGear")&&a.getText().contains("7553")&&a.getCaretPosition()==0),"candidate button loads actual observed profile and resets caret");chambers.validate();chamberGraphics=chamberImage.createGraphics();chambers.paint(chamberGraphics);chamberGraphics.dispose();ImageIO.write(chamberImage,"png",new File("ui-olm-baseline.png"));chambers.dispose();
    CoreTests.check(runtime.nhTraining.fightFrame.fighters[0].inventory!=null&&runtime.nhTraining.fightFrame.fighters[0].inventory.length==28,"actual 28-slot inventory decoded");
    InventoryViewer inventory=new InventoryViewer(panel,runtime.nhTraining,CombatRuntime.projectRoot());inventory.setLocation(-10000,-10000);inventory.setVisible(true);inventory.validate();inventory.updateSnapshot();
    BufferedImage inventoryImage=new BufferedImage(inventory.getWidth(),inventory.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D inventoryGraphics=inventoryImage.createGraphics();inventory.paint(inventoryGraphics);inventoryGraphics.dispose();ImageIO.write(inventoryImage,"png",new File("ui-agent-inventory.png"));inventory.dispose();
    FightViewer viewer=new FightViewer(panel,runtime.nhTraining);viewer.setLocation(-10000,-10000);viewer.setVisible(true);viewer.validate();BufferedImage fightImage=new BufferedImage(viewer.getWidth(),viewer.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D fightGraphics=fightImage.createGraphics();viewer.paint(fightGraphics);fightGraphics.dispose();ImageIO.write(fightImage,"png",new File("ui-fight-viewer.png"));viewer.dispose();
    PromptInfoDialog prompt=new PromptInfoDialog(panel,runtime,c->{});prompt.setModal(false);prompt.setLocation(-10000,-10000);prompt.setVisible(true);prompt.validate();
    java.util.List<JTextArea> areas=new java.util.ArrayList<>();collectAreas(prompt.getContentPane(),areas);for(JTextArea area:areas)if(area.isEditable())area.setText(KnowledgePrompt.EXAMPLE);
    JButton preview=findButton(prompt.getContentPane(),"Preview interpretation");preview.doClick();prompt.validate();BufferedImage promptImage=new BufferedImage(prompt.getWidth(),prompt.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D promptGraphics=promptImage.createGraphics();prompt.paint(promptGraphics);promptGraphics.dispose();ImageIO.write(promptImage,"png",new File("ui-prompt.png"));prompt.dispose();runtime.agent.close();runtime.nhTraining.close();panel.dispose();
   }catch(Exception error){throw new RuntimeException(error);}
  });
  System.out.println("Offline UI smoke render passed.");System.exit(0);
 }
 static void awaitJob(NHTrainingConnection c)throws Exception {long deadline=System.nanoTime()+45000000000L;while(c.busy()&&System.nanoTime()<deadline)Thread.sleep(25);CoreTests.check(!c.busy(),"viewer job completed: "+c.status);}
 static JTabbedPane findTabs(Container container){for(Component component:container.getComponents()){if(component instanceof JTabbedPane)return (JTabbedPane)component;if(component instanceof Container){JTabbedPane nested=findTabs((Container)component);if(nested!=null)return nested;}}return null;}
 static void collectAreas(Container container,java.util.List<JTextArea> areas){for(Component component:container.getComponents()){if(component instanceof JTextArea)areas.add((JTextArea)component);else if(component instanceof Container)collectAreas((Container)component,areas);}}
 static JButton findButton(Container container,String text){for(Component component:container.getComponents()){if(component instanceof JButton&&((JButton)component).getText().equals(text))return (JButton)component;if(component instanceof Container){JButton found=findButton((Container)component,text);if(found!=null)return found;}}return null;}
}
