package owl.nh;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import net.runelite.api.GameState;

/** Compact dashboard; advanced tools and learning have separate windows. */
final class ChambersTeachingWindow extends JDialog {
 private final CombatRuntime runtime;private final JFrame owner;
 private final JLabel state=new JLabel("Ready to run and learn.");
 private final JDialog stopControl;private ChambersLearningWindow learning,advanced;private final Timer refresh;
 ChambersTeachingWindow(JFrame owner,CombatRuntime runtime){
  super((Frame)null,"Chambers of Xeric",false);this.owner=owner;this.runtime=runtime;setDefaultCloseOperation(DISPOSE_ON_CLOSE);PrototypePanel.registerWindow(owner,this);
  JPanel main=new JPanel(new BorderLayout(12,12));main.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
  JPanel actions=new JPanel(new GridLayout(1,2,12,0));JButton learn=new JButton("Learn"),run=new JButton("Run bot");learn.setPreferredSize(new Dimension(150,62));actions.add(learn);actions.add(run);main.add(actions,BorderLayout.NORTH);
  JTextArea explanation=new JTextArea("Run bot learns automatically and resumes saved learning. Learn records your manual demonstration.\nAttempts save locally during play and after Stop, death, a stall, or a confirmed head kill. Nothing is uploaded.");explanation.setEditable(false);explanation.setLineWrap(true);explanation.setWrapStyleWord(true);explanation.setOpaque(false);explanation.setRows(4);main.add(explanation,BorderLayout.CENTER);
  JPanel bottom=new JPanel(new BorderLayout());bottom.add(state,BorderLayout.NORTH);JButton settings=new JButton("Advanced settings");bottom.add(settings,BorderLayout.WEST);main.add(bottom,BorderLayout.SOUTH);setContentPane(main);setSize(440,260);setLocationRelativeTo(owner);
  stopControl=createStopControl();
  JLabel savedState=new JLabel("No attempt saved in this session yet.");savedState.setFont(savedState.getFont().deriveFont(11f));JPanel savedControls=new JPanel(new GridLayout(4,1,4,4));JButton saveRun=new JButton("Confirm chest looted + save preset"),loadRun=new JButton("Load saved run...");savedControls.add(saveRun);savedControls.add(loadRun);JButton retrySave=new JButton("Retry save");retrySave.addActionListener(e->runtime.thread.invoke(()->{try{runtime.olm.retryAttemptSave();}catch(RuntimeException error){SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(this,error.getMessage(),"Retry save",JOptionPane.INFORMATION_MESSAGE));}}));savedControls.add(retrySave);savedControls.add(savedState);bottom.add(savedControls,BorderLayout.SOUTH);setSize(510,410);
  saveRun.addActionListener(e->{if(JOptionPane.showConfirmDialog(this,"Is the fight finished and have you collected the chest loot? This stops actions and saves the run's settings, experiences and learned model locally.","Confirm completed run",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;runtime.thread.invoke(()->{try{runtime.olm.confirmLootAndSave();}catch(RuntimeException error){SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(this,error.getMessage(),"Save run",JOptionPane.ERROR_MESSAGE));}});});
  loadRun.addActionListener(e->{JFileChooser chooser=new JFileChooser(new java.io.File(CombatRuntime.projectRoot(),"profiles/attempts"));if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;try{java.io.File f=chooser.getSelectedFile();if(f.length()>OlmPresetStore.MAX_BYTES)throw new IllegalArgumentException("Preset exceeds 4 MB");String json=new String(java.nio.file.Files.readAllBytes(f.toPath()),java.nio.charset.StandardCharsets.UTF_8);OlmPresetStore.parse(json);runtime.thread.invoke(()->{try{runtime.olm.loadPreset(json);}catch(RuntimeException error){SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(this,error.getMessage(),"Load run",JOptionPane.ERROR_MESSAGE));}});}catch(Exception error){JOptionPane.showMessageDialog(this,error.getMessage(),"Load run",JOptionPane.ERROR_MESSAGE);}});
  learn.addActionListener(e->{showStopControl();if(learning==null||!learning.isDisplayable())learning=new ChambersLearningWindow(owner,runtime,false);learning.setVisible(true);learning.toFront();});
  settings.addActionListener(e->{if(advanced==null||!advanced.isDisplayable())advanced=new ChambersLearningWindow(owner,runtime,true);advanced.setVisible(true);advanced.toFront();});
  run.addActionListener(e->{showStopControl();runtime.thread.invoke(()->{
   if(runtime.olm.running)return;runtime.stop("Starting Olm fighter");try{runtime.olm.startMain();SwingUtilities.invokeLater(()->{if(isDisplayable())state.setText("Olm baseline active. Observer running.");});}catch(RuntimeException error){String result=error.getMessage();SwingUtilities.invokeLater(()->{if(isDisplayable()){state.setText("Run not started.");JOptionPane.showMessageDialog(this,result,"Olm setup required",JOptionPane.INFORMATION_MESSAGE);}});}
  });});
  refresh=new Timer(200,e->{String saved=runtime.olm.presetStatus();savedState.setToolTipText(saved);savedState.setText(saved.startsWith("Saved attempt:")?"Saved: "+new java.io.File(saved.substring("Saved attempt: ".length())).getName():saved.startsWith("Saved preset:")?"Saved: "+new java.io.File(saved.substring("Saved preset: ".length())).getName():saved.length()>75?saved.substring(0,75)+"...":saved);if(runtime.demonstration.recording)state.setText("Learning: recording your manual run.");else if(runtime.olm.running){state.setText(runtime.olm.briefStatus());explanation.setText("Current action: "+runtime.olm.status+"\nMovement: "+runtime.olm.movementStatus());}else if(runtime.chambers.armed)state.setText("Projectile prayer switching active.");else state.setText(runtime.olm.briefStatus());state.setToolTipText(runtime.olm.status);});refresh.start();
  addComponentListener(new ComponentAdapter(){@Override public void componentShown(ComponentEvent e){showStopControl();}});
  addWindowListener(new WindowAdapter(){@Override public void windowClosed(WindowEvent e){refresh.stop();}});
 }
 private JDialog createStopControl(){
  JDialog floating=new JDialog((Frame)null,"Chambers stop",false);floating.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);floating.setAlwaysOnTop(true);floating.setFocusableWindowState(false);
  JPanel content=new JPanel(new BorderLayout(4,4));content.setBorder(BorderFactory.createEmptyBorder(5,6,6,6));JLabel handle=new JLabel("Drag to move",SwingConstants.CENTER);handle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
  MouseAdapter drag=new MouseAdapter(){private Point origin;@Override public void mousePressed(MouseEvent e){origin=e.getPoint();}@Override public void mouseDragged(MouseEvent e){Point screen=e.getLocationOnScreen();floating.setLocation(screen.x-origin.x,screen.y-origin.y-handle.getY()-floating.getInsets().top);}};handle.addMouseListener(drag);handle.addMouseMotionListener(drag);content.add(handle,BorderLayout.NORTH);
  JButton stop=new JButton("STOP  (F9)");stop.setForeground(new Color(150,20,20));stop.setFont(stop.getFont().deriveFont(Font.BOLD,15f));stop.addActionListener(e->stopAll());content.add(stop,BorderLayout.CENTER);floating.setContentPane(content);floating.setSize(180,105);floating.setResizable(false);
  Rectangle bounds=owner.getGraphicsConfiguration().getBounds();Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(owner.getGraphicsConfiguration());floating.setLocation(bounds.x+bounds.width-insets.right-floating.getWidth()-16,bounds.y+bounds.height-insets.bottom-floating.getHeight()-16);
  floating.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){floating.setVisible(false);}});PrototypePanel.registerWindow(owner,floating);return floating;
 }
 JDialog stopControl(){return stopControl;}
 private void showStopControl(){if(isDisplayable()&&!stopControl.isVisible())stopControl.setVisible(true);}
 private void stopAll(){runtime.armed=false;runtime.chambers.armed=false;runtime.olm.running=false;runtime.thread.invoke(()->runtime.stop("Chambers floating stop"));state.setText(runtime.olm.briefStatus());}
}
