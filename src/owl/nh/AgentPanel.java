package owl.nh;
import java.awt.*;
import java.io.File;
import javax.swing.*;
import static owl.nh.CombatCore.*;

final class AgentPanel extends JPanel {
 private final javax.swing.Timer refresh;
 AgentPanel(JFrame owner,CombatRuntime runtime,java.util.function.Consumer<Settings> informationApplied) {
  setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
  add(new JLabel("Local PPO / Recurrent PPO - trained policies are separate from the installed algorithms."));
  add(new JLabel("Included demo policies select styles in a toy simulator. They have no Orion PvP training."));
  JTextArea status=new JTextArea(6,60);status.setText("Runtime installed: "+runtime.agent.installed()+"\n"+runtime.agent.status+"\nMode: "+runtime.agent.mode+"\nNo prediction yet\n"+runtime.agent.trainingStatus);status.setEditable(false);status.setFont(new Font("Monospaced",Font.PLAIN,12));add(new JScrollPane(status));
  JPanel loading=PrototypePanel.row();PrototypePanel.button(loading,"Load trusted local policy...",()->{
   JFileChooser chooser=new JFileChooser(new File(CombatRuntime.projectRoot(),"learning/models"));chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SB3 policy checkpoint (*.zip)","zip"));
   if(chooser.showOpenDialog(owner)==JFileChooser.APPROVE_OPTION){runtime.armed=false;runtime.thread.invoke(()->runtime.stop("Loading learning policy"));runtime.agent.load(chooser.getSelectedFile());}
  });
  PrototypePanel.button(loading,"Activate recommendations",()->{runtime.armed=false;runtime.thread.invoke(()->{runtime.stop("Agent recommendations selected");try{runtime.agent.activate(LearningAgent.Mode.SHADOW);}catch(RuntimeException e){showError(owner,e);}});});
  PrototypePanel.button(loading,"Deactivate",()->{runtime.armed=false;runtime.agent.deactivate();runtime.thread.invoke(()->runtime.stop("Learning agent deactivated"));});add(loading);
  JPanel control=PrototypePanel.row();PrototypePanel.button(control,"Use Orion-trained policy for decisions",()->{
   runtime.armed=false;runtime.thread.invoke(()->{runtime.stop("Selecting policy control");try{runtime.agent.activate(LearningAgent.Mode.CONTROL);}catch(RuntimeException e){showError(owner,e);}});
  });PrototypePanel.button(control,"Test one offline policy sample",()->{
   if(!runtime.agent.ready||runtime.agent.mode==LearningAgent.Mode.DISABLED)throw new IllegalStateException("Load a policy and activate recommendations first");
   if(runtime.armed||runtime.agent.mode==LearningAgent.Mode.CONTROL)throw new IllegalStateException("Offline samples are available in recommendation mode while disarmed");
   runtime.thread.invoke(()->{State sample=new State();sample.generation=-999;sample.tick=1;sample.valid=true;sample.protection=Protection.RANGED;sample.distance=4;sample.localHp=80;sample.prayerPoints=60;sample.opponentHpPercent=70;sample.canBarrage=true;Settings config=new Settings();config.gear.get(Style.MAGIC).add(1);config.gear.get(Style.RANGED).add(2);config.gear.get(Style.MELEE).add(3);sample.inventory.addAll(java.util.Arrays.asList(1,2,3));runtime.agent.request(sample,config,new AttackManager());});
  });add(control);
  add(new JLabel("Control requires Arm in Observe/run. Gear, target and deadline checks still apply."));
  add(new JLabel("Demo policies are limited to recommendations. No live learning or automatic eating is implemented."));
  JPanel info=PrototypePanel.row();PrototypePanel.button(info,"Open prompt / information window",()->new PromptInfoDialog(owner,runtime,informationApplied).setVisible(true));add(info);
  add(new JLabel("Prompt entries update app rules; PPO does not interpret general chat text."));
  JPanel trainer=PrototypePanel.row();JComboBox<String> algorithm=new JComboBox<>(new String[]{"PPO","RecurrentPPO"});trainer.add(algorithm);JSpinner steps=PrototypePanel.number(8192,256,1000000);trainer.add(new JLabel("Demo steps"));trainer.add(steps);
  PrototypePanel.button(trainer,"Train toy demo...",()->{
   JFileChooser chooser=new JFileChooser(new File(CombatRuntime.projectRoot(),"learning/models"));chooser.setSelectedFile(new File(algorithm.getSelectedItem()+"-demo.zip"));
   if(chooser.showSaveDialog(owner)==JFileChooser.APPROVE_OPTION){File output=chooser.getSelectedFile();if(!output.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".zip"))output=new File(output.getPath()+".zip");runtime.agent.trainDemo((String)algorithm.getSelectedItem(),(Integer)steps.getValue(),output);}
  });PrototypePanel.button(trainer,"Cancel training",()->runtime.agent.cancelTraining());add(trainer);
  JTextArea notes=new JTextArea("Model files can contain executable pickle objects; load only your own trusted local checkpoints.\nThe worker runs locally over stdin/stdout. Game observations are not sent to an external service or saved.\nA .zip policy needs adjacent .json metadata with the exact observation/action schema.\nInformation changes disarm the app and reset recurrent context; they do not alter learned weights.");notes.setEditable(false);notes.setLineWrap(true);notes.setWrapStyleWord(true);notes.setRows(5);notes.setBackground(getBackground());add(notes);
  for(Component component:getComponents())if(component instanceof JComponent)((JComponent)component).setAlignmentX(Component.LEFT_ALIGNMENT);
  refresh=new javax.swing.Timer(200,e->{LearningAgent.Prediction p=runtime.agent.prediction;status.setText("Runtime installed: "+runtime.agent.installed()+"\n"+runtime.agent.status+"\nMode: "+runtime.agent.mode+"\n"+(p==null?"No prediction yet":("Last suggestion: "+(p.style==Style.UNKNOWN?"WAIT":p.style)+" / sampled tick "+p.tick+" (live validity checked before use)"))+"\n"+runtime.agent.trainingStatus);});refresh.start();owner.addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosed(java.awt.event.WindowEvent event){refresh.stop();}});
 }
 static void showError(JFrame owner,Exception error){SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(owner,error.getMessage(),"Agent not activated",JOptionPane.WARNING_MESSAGE));}
}
