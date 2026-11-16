package owl.nh;
import java.awt.*;
import java.io.File;
import java.net.URI;
import javax.swing.*;

final class NHTrainerPanel extends JPanel {
 private final javax.swing.Timer refresh;
 NHTrainerPanel(JFrame owner,CombatRuntime runtime) {
  setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
  add(new JLabel("NH Trainer - local training with the normal NH starter items."));
  add(new JLabel("Both fighters use the selected setup. NH starter excludes DMM gear and unowned switches."));
  JComboBox<String> profile=new JComboBox<>(new String[]{"NH starter (NH only)","DMM (legacy gear)"});JPanel setup=PrototypePanel.row();setup.add(new JLabel("Training setup"));setup.add(profile);add(setup);
  JTextArea status=new JTextArea(9,60);status.setText(runtime.nhTraining.status+"\n"+runtime.nhTraining.details);status.setEditable(false);status.setFont(new Font("Monospaced",Font.PLAIN,12));add(new JScrollPane(status));
  JPanel connection=PrototypePanel.row();PrototypePanel.button(connection,"Open nh-train.com",()->Desktop.getDesktop().browse(new URI(NHTrainingConnection.WEBSITE)));PrototypePanel.button(connection,"Check training connection",()->{runtime.armed=false;runtime.thread.invoke(()->runtime.stop("Checking NH Trainer connection"));runtime.nhTraining.check(profile.getSelectedIndex()==0?"nh-starter":"dmm");});add(connection);
  JComboBox<String> algorithm=new JComboBox<>(new String[]{"PPO","RecurrentPPO"});JComboBox<String> opponent=new JComboBox<>(new String[]{"fixed-ranged","fixed-magic","fixed-melee","seeded-rapid-switch"});JSpinner steps=PrototypePanel.number(8192,128,10000000),ticks=PrototypePanel.number(256,32,1200),fights=PrototypePanel.number(5,1,1000);
  JPanel choices=PrototypePanel.row();choices.add(new JLabel("Algorithm"));choices.add(algorithm);choices.add(new JLabel("Opponent"));choices.add(opponent);add(choices);
  JPanel limits=PrototypePanel.row();limits.add(new JLabel("Training steps"));limits.add(steps);limits.add(new JLabel("Max fight ticks"));limits.add(ticks);limits.add(new JLabel("Evaluation fights"));limits.add(fights);add(limits);
  JCheckBox watch=new JCheckBox("Watch current fight",true);JSpinner delay=PrototypePanel.number(600,0,2000);JComboBox<String> view=new JComboBox<>(new String[]{"Game view (browser)","2D diagnostics"});final FightViewer[] viewer={null};final InventoryViewer[] inventory={null};JPanel viewing=PrototypePanel.row();viewing.add(watch);viewing.add(view);viewing.add(new JLabel("Delay per tick (ms)"));viewing.add(delay);PrototypePanel.button(viewing,"Open fight viewer",()->openSelectedViewer(owner,runtime.nhTraining,viewer,view));PrototypePanel.button(viewing,"Agent inventory",()->InventoryViewer.open(owner,runtime.nhTraining,CombatRuntime.projectRoot(),inventory));add(viewing);add(new JLabel("600 ms = real-time ticks; 0 = fast training and skipped display frames."));
  JLabel fighterHeading=new JLabel("Play the scripted NH fighter (same fighter as Watch-NH-Fight.cmd)");add(fighterHeading);
  JComboBox<String> fightOpponent=new JComboBox<>(new String[]{"fixed-ranged","fixed-magic","fixed-melee","seeded-rapid-switch","seeded-random-prayer","mirror"});
  JSpinner watchFights=PrototypePanel.number(1,1,1000),watchTicks=PrototypePanel.number(256,32,1200);
  JPanel scripted=PrototypePanel.row();scripted.add(new JLabel("Fight opponent"));scripted.add(fightOpponent);scripted.add(new JLabel("Fights"));scripted.add(watchFights);scripted.add(new JLabel("Max fight ticks"));scripted.add(watchTicks);
  PrototypePanel.button(scripted,"Watch NH fighter",()->{
   if(runtime.nhTraining.busy())throw new IllegalStateException("Stop the current simulator job before starting another fight.");
   runtime.armed=false;runtime.thread.invoke(()->runtime.stop("Watching offline NH starter fighter"));
   openSelectedViewer(owner,runtime.nhTraining,viewer,view);
   runtime.nhTraining.watchScripted((String)fightOpponent.getSelectedItem(),(Integer)watchFights.getValue(),(Integer)watchTicks.getValue(),(Integer)delay.getValue());
   InventoryViewer.open(owner,runtime.nhTraining,CombatRuntime.projectRoot(),inventory);
  });PrototypePanel.button(scripted,"Stop fight",()->runtime.nhTraining.cancel());add(scripted);
  JLabel fighterNote=new JLabel("Includes DD and potion decisions. No checkpoint needed; watching does not train a model.");add(fighterNote);
  add(new JLabel("Model training below starts a separate, untrained policy. It does not use the scripted fighter above."));
  JPanel jobs=PrototypePanel.row();PrototypePanel.button(jobs,"Train a new model from scratch...",()->{
   JFileChooser chooser=new JFileChooser(new File(CombatRuntime.projectRoot(),"learning/nh-trainer/checkpoints"));chooser.setSelectedFile(new File((profile.getSelectedIndex()==0?"nh-starter-":"dmm-")+algorithm.getSelectedItem()+".zip"));
   if(chooser.showSaveDialog(owner)!=JFileChooser.APPROVE_OPTION)return;File output=chooser.getSelectedFile();if(!output.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".zip"))output=new File(output.getPath()+".zip");runtime.armed=false;runtime.thread.invoke(()->runtime.stop("Starting offline NH Trainer training"));if(watch.isSelected())openSelectedViewer(owner,runtime.nhTraining,viewer,view);runtime.nhTraining.train(profile.getSelectedIndex()==0?"nh-starter":"dmm",(String)algorithm.getSelectedItem(),(String)opponent.getSelectedItem(),(Integer)steps.getValue(),(Integer)ticks.getValue(),output,watch.isSelected(),(Integer)delay.getValue());
  });PrototypePanel.button(jobs,"Evaluate checkpoint...",()->{
   JFileChooser chooser=new JFileChooser(new File(CombatRuntime.projectRoot(),"learning/nh-trainer/checkpoints"));chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Trusted simulator checkpoint (*.zip)","zip"));if(chooser.showOpenDialog(owner)!=JFileChooser.APPROVE_OPTION)return;runtime.armed=false;runtime.thread.invoke(()->runtime.stop("Evaluating NH Trainer policy"));if(watch.isSelected())openSelectedViewer(owner,runtime.nhTraining,viewer,view);runtime.nhTraining.evaluate(profile.getSelectedIndex()==0?"nh-starter":"dmm",chooser.getSelectedFile(),(String)opponent.getSelectedItem(),(Integer)fights.getValue(),(Integer)ticks.getValue(),watch.isSelected(),(Integer)delay.getValue());
  });PrototypePanel.button(jobs,"Cancel job",()->runtime.nhTraining.cancel());add(jobs);
  JTextArea notes=new JTextArea("NH starter uses the pinned browser combat engine and its normal NH inventory for both fighters.\nMagic: Staff of the dead + Mystic. Ranged: Dragon crossbow + Karil's. Melee: Tentacle + defender. Special: AGS.\nNo Torva, Virtus, Masori, Zuriel's staff, Voidwaker, VLS, halberd or unowned Gmaul in this setup.\nCorrected NH v2 needs a fresh checkpoint. Legacy NH v1 comparison is available through the CLI; DMM stays separate.\nThe policy chooses attacks, prayers, movement, supplies, and full-style or weapon-only swaps. Rewards use terminal wins/losses.\nNo Orion gameplay is recorded or uploaded. Item replacements alone do not establish Orion compatibility.\nOnly load trusted checkpoints. Closing the companion terminates its training jobs.");notes.setLineWrap(true);notes.setWrapStyleWord(true);notes.setRows(9);notes.setEditable(false);notes.setBackground(getBackground());add(notes);
  setComponentZOrder(fighterHeading,2);setComponentZOrder(scripted,3);setComponentZOrder(viewing,4);setComponentZOrder(fighterNote,5);
  for(Component component:getComponents())if(component instanceof JComponent)((JComponent)component).setAlignmentX(Component.LEFT_ALIGNMENT);
  refresh=new javax.swing.Timer(200,e->{profile.setEnabled(!runtime.nhTraining.busy());fightOpponent.setEnabled(!runtime.nhTraining.busy());watchFights.setEnabled(!runtime.nhTraining.busy());status.setText("Installed: "+runtime.nhTraining.installed()+" / linked: "+runtime.nhTraining.connected+" / busy: "+runtime.nhTraining.busy()+"\n"+runtime.nhTraining.status+"\n"+runtime.nhTraining.details);});refresh.start();owner.addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosed(java.awt.event.WindowEvent event){refresh.stop();}});
 }
 static void openViewer(JFrame owner,NHTrainingConnection connection,FightViewer[] existing){if(existing[0]==null||!existing[0].isDisplayable())existing[0]=new FightViewer(owner,connection);existing[0].setVisible(true);existing[0].toFront();}
 static void openSelectedViewer(JFrame owner,NHTrainingConnection connection,FightViewer[] existing,JComboBox<String> view)throws Exception {if(view.getSelectedIndex()==0)Desktop.getDesktop().browse(connection.browserUri());else openViewer(owner,connection,existing);}
}
