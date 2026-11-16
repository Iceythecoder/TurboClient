package owl.nh;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

/** Per-session window navigation; no static registry survives companion reload. */
final class IndependentWindows {
 private final java.util.List<Window> windows=new ArrayList<>();private TrayIcon tray;
 void register(Window window){
  if(windows.contains(window))return;windows.add(window);TurboBranding.apply(window);
  JMenuBar bar=new JMenuBar();JMenu menu=new JMenu("Windows");bar.add(menu);
  menu.addMenuListener(new MenuListener(){public void menuCanceled(MenuEvent e){}public void menuDeselected(MenuEvent e){}public void menuSelected(MenuEvent e){menu.removeAll();for(Window w:new ArrayList<>(windows)){JMenuItem item=new JMenuItem(title(w));item.addActionListener(a->show(w));menu.add(item);}}});
  if(window instanceof JFrame){((JFrame)window).setJMenuBar(bar);((JFrame)window).setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);}
  if(window instanceof JDialog){((JDialog)window).setJMenuBar(bar);((JDialog)window).setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);}
  window.addWindowListener(new WindowAdapter(){@Override public void windowClosed(WindowEvent e){windows.remove(window);}});
 }
 static String title(Window w){return w instanceof Frame?((Frame)w).getTitle():w instanceof Dialog?((Dialog)w).getTitle():"Window";}
 static void show(Window w){if(w instanceof Frame)((Frame)w).setState(Frame.NORMAL);w.setVisible(true);w.toFront();}
 JPanel separate(JTabbedPane tabs){
  JPanel launchers=new JPanel(new GridLayout(0,2,10,10));launchers.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
  while(tabs.getTabCount()>0){String name=tabs.getTitleAt(0);Component content=tabs.getComponentAt(0);tabs.removeTabAt(0);JFrame frame=new JFrame(name);frame.add(content);frame.setSize(900,760);frame.setMinimumSize(new Dimension(650,480));frame.setLocationByPlatform(true);register(frame);JButton open=new JButton("Open "+name);open.addActionListener(e->show(frame));launchers.add(open);}
  return launchers;
 }
 java.util.List<Window> all(){return new ArrayList<>(windows);}
 void installTray(Window main){
  if(Boolean.getBoolean("owl.ui.smoke")||!SystemTray.isSupported())return;
  java.awt.image.BufferedImage icon=new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);Graphics2D g=icon.createGraphics();g.setColor(new Color(35,55,70));g.fillRoundRect(0,0,32,32,8,8);g.setColor(Color.WHITE);g.setFont(new Font("SansSerif",Font.BOLD,24));g.drawString("O",6,25);g.dispose();
  java.awt.image.BufferedImage logo=TurboBranding.logo();if(logo!=null)icon=logo;
  PopupMenu menu=new PopupMenu();MenuItem open=new MenuItem("Open Turbo Client windows");open.addActionListener(e->SwingUtilities.invokeLater(()->show(main)));menu.add(open);tray=new TrayIcon(icon,"Turbo Client - open windows",menu);tray.setImageAutoSize(true);tray.addActionListener(e->SwingUtilities.invokeLater(()->show(main)));try{SystemTray.getSystemTray().add(tray);}catch(AWTException|SecurityException e){tray=null;}
 }
 void dispose(){if(tray!=null){SystemTray.getSystemTray().remove(tray);tray=null;}for(Window w:new ArrayList<>(windows))if(!(w instanceof PrototypePanel))w.dispose();windows.clear();}
}
