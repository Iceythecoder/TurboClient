package owl.nh;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.client.game.ItemManager;
import static owl.nh.CombatCore.*;

/** Explicit configuration panel. Presets are populated from real equipped item IDs. */
final class PrototypePanel extends JFrame {
 private final IndependentWindows separateWindows=new IndependentWindows();
 private final CombatRuntime runtime;private final JTextArea status=new JTextArea(),log=new JTextArea();
 private final JComboBox<Style> preset=new JComboBox<>(new Style[]{Style.MAGIC,Style.RANGED,Style.MELEE,Style.SPEC});
 private final DefaultListModel<String> itemModel=new DefaultListModel<>();private final JList<String> items=new JList<>(itemModel);
 private final JTextField ids=new JTextField();private final JCheckBox autoAttack=new JCheckBox("Automatic attack/cast"),offensive=new JCheckBox("Offensive prayers"),defensive=new JCheckBox("Defensive prediction"),spec=new JCheckBox("Special attacks"),freeze=new JCheckBox("Freeze estimates");
 private final EnumMap<Style,JCheckBox> enabled=new EnumMap<>(Style.class);private final EnumMap<Style,JComboBox<String>> prayers=new EnumMap<>(Style.class);private final EnumMap<Style,JSpinner> speeds=new EnumMap<>(Style.class);
 private final EnumMap<Protection,JComboBox<Style>> counters=new EnumMap<>(Protection.class);
 private final JSpinner windows=number(1,1,3),specHp=number(30,1,100),minSpec=number(50,1,100),refreeze=number(3,0,50);
 private final JComboBox<Style> specStyle=new JComboBox<>(new Style[]{Style.MELEE,Style.RANGED,Style.MAGIC});
 private final JTextArea enemyWeapons=new JTextArea(4,30),enemyAnimations=new JTextArea(4,30),attackAnimations=new JTextArea(4,30),freezeGraphics=new JTextArea(4,30);
 private final javax.swing.Timer refresh;private boolean updating;
 private final JCheckBox autoLock=new JCheckBox("Auto-lock on current target",true);
 PrototypePanel(CombatRuntime runtime) {
  super("Orion NH - client-integrated prototype");this.runtime=runtime;setSize(920,820);setMinimumSize(new Dimension(780,650));setLocationByPlatform(true);setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
  JPanel main=new JPanel(new BorderLayout(10,10));main.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));setContentPane(main);
  JLabel banner=new JLabel("Name masking: always on. F9 stops actions; name masking stays active.");banner.setFont(banner.getFont().deriveFont(Font.BOLD,14f));main.add(banner,BorderLayout.NORTH);
  JTabbedPane tabs=new JTabbedPane();main.add(tabs,BorderLayout.CENTER);
  JPanel observe=vertical();status.setEditable(false);status.setText(runtime.display);status.setFont(new Font("Monospaced",Font.PLAIN,12));status.setRows(12);observe.add(new JScrollPane(status));
  JPanel control=row();control.add(autoLock);autoLock.addActionListener(e->{if(!updating){boolean selected=autoLock.isSelected();runtime.armed=false;submit(()->runtime.setAutoLock(selected));}});button(control,"Lock once",()->submit(()->runtime.lockCurrent()));button(control,"Apply settings + arm",()->{Settings c=readSettings();runtime.armed=false;submit(()->{runtime.settings(c);runtime.arm();});});button(control,"STOP / observe (F9)",()->{runtime.armed=false;submit(()->runtime.stop("User stop"));});observe.add(control);
  log.setEditable(false);log.setFont(new Font("Monospaced",Font.PLAIN,11));log.setRows(16);observe.add(new JScrollPane(log));tabs.addTab("Observe / run",new JScrollPane(observe));
  JPanel gearPanel=vertical();gearPanel.add(new JLabel("Equip a setup in Orion, then capture its actual IDs. Remove pieces you do not want switched."));JPanel capture=row();capture.add(preset);button(capture,"Capture current equipment",()->{runtime.armed=false;Style selected=(Style)preset.getSelectedItem();submit(()->{runtime.stop("Capturing equipment");runtime.config.gear.put(selected,new ArrayList<>(runtime.adapter.container(InventoryID.EQUIPMENT)));SwingUtilities.invokeLater(()->showGear(selected));});});button(capture,"Remove selected item",()->{runtime.armed=false;Style selected=(Style)preset.getSelectedItem();int index=items.getSelectedIndex();if(index>=0)submit(()->{runtime.stop("Editing preset");runtime.config.gear.get(selected).remove(index);SwingUtilities.invokeLater(()->showGear(selected));});});gearPanel.add(capture);
  items.setVisibleRowCount(12);gearPanel.add(new JScrollPane(items));gearPanel.add(new JLabel("Optional ID editor (comma separated). Item names are shown from Orion; matching uses IDs."));gearPanel.add(ids);button(gearPanel,"Apply IDs to selected preset",()->{Style selected=(Style)preset.getSelectedItem();java.util.List<Integer> values=ConfigManager.ids(ids.getText());runtime.armed=false;submit(()->{runtime.stop("Editing IDs");runtime.config.gear.put(selected,values);SwingUtilities.invokeLater(()->showGear(selected));});});preset.addActionListener(e->showGear((Style)preset.getSelectedItem()));tabs.addTab("Equipment",new JScrollPane(gearPanel));
  JPanel rules=vertical();rules.add(new JLabel("Automation switches are off by default. Mappings and observations must be validated in Orion."));rules.add(autoAttack);rules.add(offensive);rules.add(defensive);rules.add(spec);rules.add(freeze);
  for(Style style:new Style[]{Style.MAGIC,Style.RANGED,Style.MELEE}) {
   JPanel line=row();JCheckBox on=new JCheckBox(style+" enabled",true);enabled.put(style,on);line.add(on);
   JComboBox<String> prayer=new JComboBox<>(new String[]{"NONE","AUGURY","RIGOUR","PIETY","MYSTIC_MIGHT","EAGLE_EYE"});prayer.setSelectedItem(runtime.config.offensive.get(style));prayers.put(style,prayer);line.add(new JLabel("Offensive prayer"));line.add(prayer);
   JSpinner speed=number(runtime.config.speeds.get(style),1,20);speeds.put(style,speed);line.add(new JLabel("Fallback speed (ticks)"));line.add(speed);rules.add(line);
  }
  JPanel special=row();special.add(new JLabel("Special attack style"));special.add(specStyle);JSpinner speed=number(4,1,20);speeds.put(Style.SPEC,speed);special.add(new JLabel("Spec cooldown"));special.add(speed);rules.add(special);
  for(Protection prayer:new Protection[]{Protection.RANGED,Protection.MAGIC,Protection.MELEE,Protection.NONE}){JPanel line=row();line.add(new JLabel("Opponent "+prayer+": preferred counter"));JComboBox<Style> choice=new JComboBox<>(new Style[]{Style.MAGIC,Style.RANGED,Style.MELEE});choice.setSelectedItem(runtime.config.counters.get(prayer));counters.put(prayer,choice);line.add(choice);rules.add(line);}
  JPanel limits=row();limits.add(new JLabel("Sequence deadline (600 ms tick windows)"));limits.add(windows);limits.add(new JLabel("Spec opponent HP % <="));limits.add(specHp);rules.add(limits);
  JPanel resources=row();resources.add(new JLabel("Minimum spec energy %"));resources.add(minSpec);resources.add(new JLabel("Refreeze within ticks"));resources.add(refreeze);rules.add(resources);
  rules.add(new JLabel("1 window attempts the current observed tick. 2-3 permit verification across ticks; not one-tick success."));rules.add(new JLabel("Weapon speeds are user settings. Actual cooldown confirmation uses verified animation mappings."));tabs.addTab("Rules",new JScrollPane(rules));
  JPanel mapping=vertical();mapping.add(new JLabel("No guessed Orion IDs. Each line: numeric ID = style, or numeric ID = duration/speed ticks."));
  area(mapping,"Enemy weapon ID = MAGIC / RANGED / MELEE",enemyWeapons);area(mapping,"Enemy attack animation ID = MAGIC / RANGED / MELEE",enemyAnimations);area(mapping,"Local attack animation ID = attack-speed ticks (required for automatic attacks)",attackAnimations);
  JPanel record=row();button(record,"Record current local attack animation",()->{Style selected=(Style)preset.getSelectedItem();int configured=(Integer)speeds.get(selected).getValue();submit(()->{Player player=runtime.client.getLocalPlayer();if(player==null||player.getAnimation()<0)throw new IllegalStateException("Manually attack, then record while that attack animation is visible. Do not record eating/movement.");int id=player.getAnimation();SwingUtilities.invokeLater(()->attackAnimations.append(id+"="+configured+"\n"));});});mapping.add(record);
  area(mapping,"Freeze graphic ID = duration ticks (estimated; requires Orion validation)",freezeGraphics);mapping.add(new JLabel("Enemy HP uses an approximate health-bar percentage, not exact hitpoints. Freeze does not imply path availability."));tabs.addTab("Signal mappings",new JScrollPane(mapping));
  tabs.addTab("Learning agent",new JScrollPane(new AgentPanel(this,runtime,c->populate(c))));
  tabs.addTab("NH Trainer",new JScrollPane(new NHTrainerPanel(this,runtime)));
  JPanel chambers=vertical();chambers.add(new JLabel("Chambers of Xeric: teach room cues and inspect Orion observations in a separate window."));
  final ChambersTeachingWindow[] chambersWindow={null};
  button(chambers,"Open Chambers teaching window",()->{
   
   if(chambersWindow[0]==null||!chambersWindow[0].isDisplayable())chambersWindow[0]=new ChambersTeachingWindow(this,runtime);
   chambersWindow[0].setVisible(true);chambersWindow[0].toFront();
  });chambers.add(new JLabel("Learn from your manual run. The movable Stop control stays above the client. Run bot starts the current Olm fighter."));tabs.addTab("Chambers",chambers);
  JPanel slayer=vertical();slayer.add(new JLabel("Fight a chosen nearby NPC type for a set number of kills using your current equipment."));
  button(slayer,"Open Slayer window",()->{JFrame window=runtime.slayer.window();window.setTitle("AI Slayer Fighter");registerWindow(this,window);window.setVisible(true);window.toFront();});
  slayer.add(new JLabel("Select an NPC in game or use your last killed NPC, enter a kill count, then Start. F9 stops actions."));tabs.addTab("Slayer",slayer);
  JPanel files=row();button(files,"Apply settings (observe)",()->{Settings c=readSettings();runtime.armed=false;submit(()->runtime.settings(c));});button(files,"Save preset...",()->{Settings c=readSettings();JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("orion-nh.properties"));if(chooser.showSaveDialog(this)==JFileChooser.APPROVE_OPTION)ConfigManager.save(chooser.getSelectedFile(),c);});button(files,"Load preset...",()->{JFileChooser chooser=new JFileChooser();if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;Settings c=ConfigManager.load(chooser.getSelectedFile());runtime.armed=false;submit(()->{runtime.settings(c);SwingUtilities.invokeLater(()->populate(c));});});main.add(files,BorderLayout.SOUTH);
  main.remove(tabs);main.remove(files);
  String clientMode=System.getProperty("owl.client.mode","all").trim().toLowerCase(Locale.ROOT);
  JPanel fighters=new JPanel(new GridLayout(clientMode.equals("all")?2:1,clientMode.equals("all")?2:1,12,12));fighters.setBorder(BorderFactory.createEmptyBorder(20,8,20,8));
  JButton chambersButton=null,slayerButton=null;
  for(JPanel feature:new JPanel[]{chambers,slayer})for(Component component:feature.getComponents())if(component instanceof JButton){JButton launcher=(JButton)component;if(feature==chambers){launcher.setText("AI Olm Fighter");chambersButton=launcher;}else{launcher.setText("AI Slayer Fighter");slayerButton=launcher;}}
  JButton toaButton=new JButton("AI TOA Fighter");toaButton.addActionListener(e->{try{Window window=runtime.toa.window(this);registerWindow(this,window);window.setVisible(true);window.toFront();}catch(RuntimeException error){JOptionPane.showMessageDialog(this,error.getMessage(),"AI TOA Fighter",JOptionPane.ERROR_MESSAGE);}});
  JButton tobButton=new JButton("TOB AI Fighter");tobButton.addActionListener(e->{try{Window window=runtime.tob.window(this);registerWindow(this,window);window.setVisible(true);window.toFront();}catch(RuntimeException error){JOptionPane.showMessageDialog(this,error.getMessage(),"TOB AI Fighter",JOptionPane.ERROR_MESSAGE);}});
  JButton gauntletButton=new JButton("Turbo Gauntlet");gauntletButton.addActionListener(e->{try{Window window=runtime.gauntlet.window(this);registerWindow(this,window);window.setVisible(true);window.toFront();}catch(RuntimeException error){JOptionPane.showMessageDialog(this,error.getMessage(),"Turbo Gauntlet",JOptionPane.ERROR_MESSAGE);}});
  if(clientMode.equals("chambers"))fighters.add(chambersButton);else if(clientMode.equals("slayer"))fighters.add(slayerButton);else if(clientMode.equals("toa"))fighters.add(toaButton);else if(clientMode.equals("gauntlet"))fighters.add(gauntletButton);else{fighters.add(chambersButton);fighters.add(slayerButton);fighters.add(toaButton);fighters.add(tobButton);fighters.add(gauntletButton);}
  main.add(fighters,BorderLayout.CENTER);separateWindows.register(this);getJMenuBar().add(runtime.usage.menu(this));
  if(clientMode.equals("all")||clientMode.equals("chambers")){JMenu learningFiles=new JMenu("Learning data");JMenuItem exportLearning=new JMenuItem("Export learned behaviour...");learningFiles.add(exportLearning);getJMenuBar().add(learningFiles);exportLearning.addActionListener(e->{JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("Export learned behaviour (no recordings or credentials)");chooser.setSelectedFile(new File("turbo-olm-learning.json"));if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;File destination=chooser.getSelectedFile();if(destination.exists()&&JOptionPane.showConfirmDialog(this,"Replace the selected learning export?","Export learning",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;runtime.thread.invoke(()->{try{String snapshot=OlmLearningExchange.sanitize(runtime.olm.profileText());java.util.concurrent.CompletableFuture.runAsync(()->{try{OlmLearningExchange.write(destination.toPath(),snapshot);SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(this,"Learning exported. Send this file manually; nothing was uploaded.","Export complete",JOptionPane.INFORMATION_MESSAGE));}catch(Exception failure){SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(this,failure.getMessage(),"Export failed",JOptionPane.ERROR_MESSAGE));}});}catch(RuntimeException failure){SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(this,"Load instance data before exporting. "+failure.getMessage(),"Export learning",JOptionPane.INFORMATION_MESSAGE));}});});}
  separateWindows.installTray(this);setTitle(clientMode.equals("chambers")?"Turbo Chambers":clientMode.equals("slayer")?"Turbo Slayer":clientMode.equals("toa")?"Turbo TOA":clientMode.equals("gauntlet")?"Turbo Gauntlet":"Turbo Client - windows");setMinimumSize(new Dimension(560,220));setSize(clientMode.equals("all")?new Dimension(820,500):new Dimension(620,260));
  refresh=new javax.swing.Timer(200,e->{banner.setText("Name masking: "+runtime.privacy.status+". F9 stops actions; privacy stays on.");status.setText(runtime.display);if(!log.getText().equals(runtime.log)){log.setText(runtime.log);log.setCaretPosition(log.getDocument().getLength());}});refresh.start();populate(runtime.config);
 }
 static void registerWindow(Frame owner,Window window){if(owner instanceof PrototypePanel)((PrototypePanel)owner).separateWindows.register(window);}
 java.util.List<Window> independentWindows(){return separateWindows.all();}
 @Override public void dispose(){if(refresh!=null)refresh.stop();separateWindows.dispose();super.dispose();}
 private void showGear(Style style) {
  java.util.List<Integer> values=new ArrayList<>(runtime.config.gear.get(style));ids.setText(ConfigManager.join(values));itemModel.clear();for(int id:values)itemModel.addElement("Item "+id);
  runtime.thread.invoke(()->{java.util.List<String> names=new ArrayList<>();for(int id:values){ItemComposition item=runtime.client.getItemDefinition(id);names.add(id+"  "+(item==null?"Unknown item":item.getName()));}SwingUtilities.invokeLater(()->{if(preset.getSelectedItem()==style){itemModel.clear();for(String name:names)itemModel.addElement(name);}});});
 }
 private Settings readSettings() {
  Settings c=new Settings();c.autoLock=autoLock.isSelected();for(Style s:new Style[]{Style.MAGIC,Style.RANGED,Style.MELEE,Style.SPEC}){c.gear.put(s,new ArrayList<>(runtime.config.gear.get(s)));c.speeds.put(s,(Integer)speeds.get(s).getValue());}
  c.enabled.clear();for(Style s:enabled.keySet()){if(enabled.get(s).isSelected())c.enabled.add(s);c.offensive.put(s,(String)prayers.get(s).getSelectedItem());}
  for(Protection prayer:counters.keySet())c.counters.put(prayer,(Style)counters.get(prayer).getSelectedItem());
  c.autoAttack=autoAttack.isSelected();c.offensiveEnabled=offensive.isSelected();c.defensiveEnabled=defensive.isSelected();c.specEnabled=spec.isSelected();c.freezeEnabled=freeze.isSelected();c.deadlineTicks=(Integer)windows.getValue();c.specHp=(Integer)specHp.getValue();c.minSpec=(Integer)minSpec.getValue();c.refreezeTicks=(Integer)refreeze.getValue();c.specStyle=(Style)specStyle.getSelectedItem();
  parseStyles(enemyWeapons.getText(),c.enemyWeapons);parseStyles(enemyAnimations.getText(),c.enemyAnimations);parseNumbers(attackAnimations.getText(),c.attackAnimations,20);parseNumbers(freezeGraphics.getText(),c.freezeGraphics,100);return c;
 }
 private void populate(Settings c) {
  updating=true;autoLock.setSelected(c.autoLock);autoAttack.setSelected(c.autoAttack);offensive.setSelected(c.offensiveEnabled);defensive.setSelected(c.defensiveEnabled);spec.setSelected(c.specEnabled);freeze.setSelected(c.freezeEnabled);
  for(Style s:enabled.keySet()){enabled.get(s).setSelected(c.enabled.contains(s));prayers.get(s).setSelectedItem(c.offensive.get(s));}for(Style s:speeds.keySet())speeds.get(s).setValue(c.speeds.get(s));for(Protection prayer:counters.keySet())counters.get(prayer).setSelectedItem(c.counters.get(prayer));windows.setValue(c.deadlineTicks);specHp.setValue(c.specHp);minSpec.setValue(c.minSpec);refreeze.setValue(c.refreezeTicks);specStyle.setSelectedItem(c.specStyle);
  enemyWeapons.setText(mapText(c.enemyWeapons));enemyAnimations.setText(mapText(c.enemyAnimations));attackAnimations.setText(mapText(c.attackAnimations));freezeGraphics.setText(mapText(c.freezeGraphics));showGear((Style)preset.getSelectedItem());updating=false;
 }
 private void submit(Runnable action){runtime.thread.invoke(()->{try{action.run();runtime.updateDisplay();}catch(RuntimeException e){runtime.stop("Configuration error");SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(this,e.getMessage(),"Orion NH",JOptionPane.ERROR_MESSAGE));}});}
 interface Checked {void run()throws Exception;}
 static void button(JPanel panel,String text,Checked action){JButton button=new JButton(text);button.addActionListener(e->{try{action.run();}catch(Exception ex){JOptionPane.showMessageDialog(panel,ex.getMessage(),"Orion NH",JOptionPane.ERROR_MESSAGE);}});panel.add(button);}
 static JPanel vertical(){JPanel p=new JPanel();p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));return p;}
 static JPanel row(){return new JPanel(new FlowLayout(FlowLayout.LEFT));}
 static JSpinner number(int value,int min,int max){return new JSpinner(new SpinnerNumberModel(value,min,max,1));}
 static void area(JPanel parent,String label,JTextArea area){parent.add(new JLabel(label));parent.add(new JScrollPane(area));}
 static String mapText(Map<Integer,?> map){StringBuilder b=new StringBuilder();for(Map.Entry<Integer,?> entry:map.entrySet())b.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');return b.toString();}
 static void parseStyles(String text,Map<Integer,Style> out){for(String line:text.split("\\R")){if(line.trim().isEmpty())continue;String[] parts=line.trim().split("\\s*=\\s*");if(parts.length!=2)throw new IllegalArgumentException("Expected ID=STYLE");int id=Integer.parseInt(parts[0]);Style style=Style.valueOf(parts[1].toUpperCase(Locale.ROOT));if(id<0||style==Style.UNKNOWN||style==Style.SPEC)throw new IllegalArgumentException("Invalid signal mapping");out.put(id,style);}}
 static void parseNumbers(String text,Map<Integer,Integer> out,int max){for(String line:text.split("\\R")){if(line.trim().isEmpty())continue;String[] parts=line.trim().split("\\s*=\\s*");if(parts.length!=2)throw new IllegalArgumentException("Expected ID=TICKS");int id=Integer.parseInt(parts[0]),ticks=Integer.parseInt(parts[1]);if(id<0||ticks<1||ticks>max)throw new IllegalArgumentException("Invalid signal duration");out.put(id,ticks);}}
}

