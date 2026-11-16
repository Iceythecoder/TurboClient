package owl.nh;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import javax.swing.*;
import net.runelite.api.*;

/** Raid teaching and explicitly armed controls with local, user-chosen learning exports. */
final class ChambersLearningWindow extends JDialog {
 private final JTextField room=new JTextField(24);
 private final JTextArea instructions=new JTextArea(12,60),observation=new JTextArea(9,60);
 private final CombatRuntime runtime;
 private final JComboBox<String> neckCue=new JComboBox<>(new String[]{"Unknown / neck not clearly visible","Visible green neck glow","Neck clearly visible without green glow"});
 private final javax.swing.Timer liveRefresh;
 ChambersLearningWindow(JFrame owner,CombatRuntime runtime,boolean advanced) {
  super((Frame)null,"Chambers of Xeric - teaching workspace",false);this.runtime=runtime;setTitle(advanced?"Chambers advanced settings":"Chambers learning");
  setDefaultCloseOperation(DISPOSE_ON_CLOSE);PrototypePanel.registerWindow(owner,this);
  JPanel content=new JPanel(new BorderLayout(8,8));content.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
  JPanel top=new JPanel();top.setLayout(new BoxLayout(top,BoxLayout.Y_AXIS));
  top.add(new JLabel("Olm baseline requires a verified Orion profile. Demonstration learning remains separate."));
  JPanel location=PrototypePanel.row();location.add(new JLabel("Room / encounter (set manually)"));location.add(room);top.add(location);
  JPanel phaseRow=PrototypePanel.row();phaseRow.add(new JLabel("Neck cue (manual observation)"));phaseRow.add(neckCue);top.add(phaseRow);
  JLabel phasePlan=new JLabel(phaseGuidance(0));top.add(phasePlan);neckCue.addActionListener(e->{phasePlan.setText(phaseGuidance(neckCue.getSelectedIndex()));int phase=neckCue.getSelectedIndex();runtime.thread.invoke(()->{runtime.demonstration.phase(phase);runtime.olm.manualPhase(phase);});});
  top.add(new JLabel("Teach: visible cue -> desired action -> success condition. Describe Orion-specific differences."));
  content.add(top,BorderLayout.NORTH);
  instructions.setLineWrap(true);instructions.setWrapStyleWord(true);
  instructions.setText("USER-SUPPLIED OLM PLAN (baseline requires a verified profile):\nValidate Chambers -> Overload(+)(4) -> Prayer enhance(+)(4) -> four Xeric's aid sips toward full HP -> Revitalisation sip -> enter Olm.\nPhases 1 and 2: mage hand -> special attack melee hand -> finish melee hand.\nPhase 3: damage melee hand to very low HP without killing it; damage mage hand, then coordinate both hand kills within the server's allowed window -> finish remaining hand -> head.\nExact HP stop threshold, kill window, phase signals, supplies and IDs require Orion validation. Never change game rules.\nImage reference: blue/white projectile = MAGIC; green projectile = RANGED. Assign IDs only after observing them in Orion.");
  instructions.append("\nUser phase cue: confirmed green neck glow -> phase 3. Neck clearly visible without green glow -> regular phase: mage hand first, then melee. Hidden, obscured or uncertain neck -> phase unknown; do not assume regular phase. Visual recognition is not implemented; the cue is manually labeled for now.");
  observation.setEditable(false);observation.setText("Click Capture observation while logged into Orion. Room recognition is not implemented.");
  JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,section("Your room instructions",instructions),section("Current client observation (manual snapshot)",observation));split.setResizeWeight(.6);
  JTabbedPane tabs=new JTabbedPane();tabs.addTab("Plan / observations",split);
  JPanel prayerPanel=PrototypePanel.vertical();
  JTextField regions=new JTextField(35),npcs=new JTextField(35);JTextArea mappings=new JTextArea(4,45),live=new JTextArea(12,60);live.setEditable(false);live.setLineWrap(true);live.setWrapStyleWord(true);
  JPanel regionRow=PrototypePanel.row();regionRow.add(new JLabel("Verified template region IDs"));regionRow.add(regions);prayerPanel.add(regionRow);
  JPanel npcRow=PrototypePanel.row();npcRow.add(new JLabel("Verified Olm NPC IDs"));npcRow.add(npcs);prayerPanel.add(npcRow);
  PrototypePanel.area(prayerPanel,"Projectile ID=MAGIC / RANGED / MELEE (one per line; no guessed IDs)",mappings);
  JPanel locationCapture=PrototypePanel.row();
  PrototypePanel.button(locationCapture,"Use current Chambers location",()->gameTask(()->{
   runtime.stop("Teaching manually confirmed Chambers location");String result=runtime.chambers.teachCurrentLocation();
   Player self=runtime.client.getLocalPlayer();int region=net.runelite.api.coords.WorldPoint.fromLocalInstance(runtime.client,self.getLocalLocation()).getRegionID();
   SwingUtilities.invokeLater(()->{if(isDisplayable()){regions.setText(Integer.toString(region));observation.setText(result);JOptionPane.showMessageDialog(this,result,"Chambers location captured",JOptionPane.INFORMATION_MESSAGE);}});
  }));prayerPanel.add(locationCapture);
  JSpinner margin=PrototypePanel.number(5,1,50);JPanel timing=PrototypePanel.row();timing.add(new JLabel("Safety margin (client cycles; ~20 ms each, uncalibrated)"));timing.add(margin);prayerPanel.add(timing);
  JPanel actions=PrototypePanel.row();
  PrototypePanel.button(actions,"Apply + observe",()->{
   ChambersController.Config config=ChambersController.parse(regions.getText(),npcs.getText(),mappings.getText(),(Integer)margin.getValue());
   runtime.armed=false;runtime.thread.invoke(()->{runtime.stop("Starting Chambers observations");runtime.chambers.configure(config);runtime.chambers.start();});
  });
  PrototypePanel.button(actions,"Arm prayer switching",()->runtime.thread.invoke(()->{try{if(runtime.demonstration.recording)throw new IllegalStateException("Stop demonstration recording first");runtime.chambers.arm();}catch(RuntimeException error){runtime.chambers.stop("Arm rejected");SwingUtilities.invokeLater(()->{if(isDisplayable())JOptionPane.showMessageDialog(this,error.getMessage(),"Chambers",JOptionPane.ERROR_MESSAGE);});}}));
  PrototypePanel.button(actions,"STOP prayers (F9)",()->{runtime.chambers.armed=false;runtime.thread.invoke(()->runtime.chambers.stop("User stop"));});prayerPanel.add(actions);
  JPanel timingFiles=PrototypePanel.row();PrototypePanel.button(timingFiles,"Save prayer timing...",this::savePrayerTiming);PrototypePanel.button(timingFiles,"Load prayer timing...",this::loadPrayerTiming);prayerPanel.add(timingFiles);
  prayerPanel.add(new JLabel("Only projectiles targeting your actor are handled. AoE, tile hazards and melee animations are not covered."));
  prayerPanel.add(new JLabel("Unknown IDs stop prayer output. Region and nearby NPC must both match. No automatic potting or attacks."));
  prayerPanel.add(new JScrollPane(live));tabs.addTab("Projectile prayers",new JScrollPane(prayerPanel));content.add(tabs,BorderLayout.CENTER);
  JPanel demonstrations=PrototypePanel.vertical();
  JTextArea guide=new JTextArea("Record your own run in Orion. Client state and permitted menu actions are captured, not video or chat.\nStart inside Chambers: raid membership is your manual assertion for this demonstration.\nRecording disables this application's automatic actions. Play normally; label phase changes with the neck selector.\nStop after the run, then Learn from demonstration. The local exemplar learner suggests similar recorded actions; it does not control the game.\nSave explicitly to retain the demonstration. Closing this window hides it. Use Stop to end recording; data remains in memory until discarded or the application closes.");guide.setEditable(false);guide.setLineWrap(true);guide.setWrapStyleWord(true);guide.setRows(7);demonstrations.add(guide);
  JTextArea demoStatus=new JTextArea(7,60);demoStatus.setEditable(false);demoStatus.setLineWrap(true);demoStatus.setWrapStyleWord(true);
  JPanel recordControls=PrototypePanel.row();
  PrototypePanel.button(recordControls,"Start demonstration",()->gameTask(()->{runtime.stop("Manual Chambers demonstration");runtime.chambers.armed=false;runtime.demonstration.start();}));
  PrototypePanel.button(recordControls,"Stop recording",()->gameTask(()->runtime.demonstration.stop("User stopped")));
  PrototypePanel.button(recordControls,"Learn from demonstration",()->gameTask(()->runtime.demonstration.learn()));demonstrations.add(recordControls);
  JTextField marker=new JTextField(28);JPanel markers=PrototypePanel.row();markers.add(new JLabel("Teaching marker"));markers.add(marker);PrototypePanel.button(markers,"Add marker",()->{String note=marker.getText();gameTask(()->runtime.demonstration.mark(note));});demonstrations.add(markers);
  JPanel demoFiles=PrototypePanel.row();PrototypePanel.button(demoFiles,"Save demonstration...",this::saveDemonstration);
  PrototypePanel.button(demoFiles,"Load demonstration...",this::loadDemonstration);
  PrototypePanel.button(demoFiles,"Discard demonstration + model",()->{if(JOptionPane.showConfirmDialog(this,"Discard the in-memory demonstration and learned examples?","Discard",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)gameTask(()->runtime.demonstration.clear());});demonstrations.add(demoFiles);
  demonstrations.add(new JScrollPane(demoStatus));tabs.addTab("Demonstration learning",new JScrollPane(demonstrations));
  JPanel baseline=PrototypePanel.vertical();JTextArea profileJson=new JTextArea(runtime.olm.profileText(),17,60),baselineStatus=new JTextArea(10,60);baselineStatus.setEditable(false);baselineStatus.setLineWrap(true);baselineStatus.setWrapStyleWord(true);
  JTextArea baselineGuide=new JTextArea("Configure Orion's actual IDs below. Equipment uses your main Equipment presets.\nOrb rules: purple=magic, red=melee, green=ranged. Map IDs to colours in mechanics.orbProjectiles.\nSafe attack positions are held; mapped hazards trigger native dodges. Wall gaps require a verified Lunar/click interaction.\nApply text edits first. Save Olm profile stores the applied setup, current gear and learned model. Runs save updated learning automatically. After head death: collect chest items, use nearby steps, then stop.");baselineGuide.setLineWrap(true);baselineGuide.setWrapStyleWord(true);baselineGuide.setEditable(false);baselineGuide.setRows(6);baseline.add(baselineGuide);baseline.add(new JScrollPane(profileJson));
  JPanel baselineActions=PrototypePanel.row();PrototypePanel.button(baselineActions,"Apply Olm profile",()->{String json=profileJson.getText();gameTask(()->runtime.olm.applyProfileOrPreset(json));});PrototypePanel.button(baselineActions,"Observe Olm only",()->gameTask(()->{runtime.stop("Olm observation only");runtime.olm.observe();}));PrototypePanel.button(baselineActions,"Save Olm profile",()->gameTask(()->runtime.olm.saveProfile()));PrototypePanel.button(baselineActions,"Load saved Olm profile",()->gameTask(()->{runtime.olm.loadSavedProfile();String json=runtime.olm.profileText();SwingUtilities.invokeLater(()->{profileJson.setText(json);profileJson.setCaretPosition(0);});}));baseline.add(baselineActions,0);
  JLabel candidateStatus=new JLabel("Candidates have not been loaded in this window.");candidateStatus.setName("olmCandidateStatus");baseline.add(candidateStatus); JPanel candidates=PrototypePanel.row();PrototypePanel.button(candidates,"Load observed candidates",()->{File source=new File(CombatRuntime.projectRoot(),"profiles/olm-demo2-candidate.json");if(!source.isFile())throw new IOException("Candidate profile not found: "+source.getAbsolutePath());String text=new String(java.nio.file.Files.readAllBytes(source.toPath()),StandardCharsets.UTF_8);new com.google.gson.JsonParser().parse(text).getAsJsonObject();profileJson.setText(text);profileJson.setCaretPosition(0);candidateStatus.setText("Loaded observed candidates. Review the text, then Apply Olm profile. Not armed.");SwingUtilities.invokeLater(()->profileJson.scrollRectToVisible(new Rectangle(0,0,1,1)));});PrototypePanel.button(candidates,"Use observed gear presets",()->{String json=profileJson.getText();gameTask(()->runtime.olm.applyObservedGear(json));});baseline.add(candidates);
  JComboBox<String> baselinePhase=new JComboBox<>(new String[]{"Unknown: retain last strategy","Green neck: final hands","Regular: mage then melee","Head only"});JPanel phaseControls=PrototypePanel.row();phaseControls.add(new JLabel("Manual phase fallback"));phaseControls.add(baselinePhase);baselinePhase.addActionListener(e->{int selected=baselinePhase.getSelectedIndex();runtime.thread.invoke(()->runtime.olm.manualPhase(selected));});baseline.add(phaseControls);
  JComboBox<String> correction=new JComboBox<>(new String[]{"magic","melee","head"});JPanel feedback=PrototypePanel.row();feedback.add(new JLabel("Correct the last target decision to"));feedback.add(correction);PrototypePanel.button(feedback,"Teach target correction",()->{String chosen=(String)correction.getSelectedItem();gameTask(()->runtime.olm.correctTarget(chosen));});baseline.add(feedback);baseline.add(new JScrollPane(baselineStatus));tabs.addTab("Olm baseline",new JScrollPane(baseline));
  JPanel movementFeedback=PrototypePanel.row();PrototypePanel.button(movementFeedback,"Last move was good",()->gameTask(()->runtime.olm.specialFeedback(true)));PrototypePanel.button(movementFeedback,"Last move was bad",()->gameTask(()->runtime.olm.specialFeedback(false)));PrototypePanel.button(movementFeedback,"Save movement learning...",this::saveMovementLearning);PrototypePanel.button(movementFeedback,"Load movement learning...",this::loadMovementLearning);baseline.add(movementFeedback,baseline.getComponentCount()-1);
  javax.swing.Timer baselineRefresh=new javax.swing.Timer(200,e->baselineStatus.setText(runtime.olm.status+"\n"+runtime.olm.specialStatus()+"\n\n"+runtime.olm.trace));baselineRefresh.start();
  JPanel controls=PrototypePanel.row();PrototypePanel.button(controls,"Capture observation",this::capture);
  PrototypePanel.button(controls,"Save teaching notes...",this::save);
  PrototypePanel.button(controls,"Clear",()->{room.setText("");neckCue.setSelectedIndex(0);instructions.setText("");observation.setText("");});
  controls.add(new JLabel("Local memory; save explicitly. Demonstration data has its own discard button."));content.add(controls,BorderLayout.SOUTH);
  if(!advanced){
   JPanel learningTop=new JPanel(new BorderLayout(4,4));learningTop.add(new JLabel("Play Chambers yourself and record your demonstration."),BorderLayout.NORTH);learningTop.add(phaseRow,BorderLayout.CENTER);content.add(learningTop,BorderLayout.NORTH);
   content.add(new JScrollPane(demonstrations),BorderLayout.CENTER);content.remove(controls);setTitle("Chambers - Learning");
  }
  setContentPane(content);setSize(advanced?850:720,advanced?650:540);setLocationRelativeTo(owner);
  liveRefresh=new javax.swing.Timer(150,e->{live.setText(runtime.chambers.display+"\n\nIn-memory events:\n"+runtime.chambers.history);demoStatus.setText(runtime.demonstration.status+"\n\n"+runtime.demonstration.prediction);});liveRefresh.start();
  addWindowListener(new WindowAdapter(){@Override public void windowClosed(WindowEvent e){liveRefresh.stop();baselineRefresh.stop();if(!runtime.olm.running){runtime.chambers.armed=false;runtime.chambers.enabled=false;}runtime.thread.invoke(()->{if(!runtime.olm.running)runtime.chambers.close();runtime.demonstration.stop("Teaching window closed");});room.setText("");instructions.setText("");observation.setText("");regions.setText("");npcs.setText("");mappings.setText("");live.setText("");marker.setText("");profileJson.setText("");}});
 }
 private static JPanel section(String title,JTextArea text){JPanel panel=new JPanel(new BorderLayout());panel.setBorder(BorderFactory.createTitledBorder(title));panel.add(new JScrollPane(text));return panel;}
  private void savePrayerTiming(){
   JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("olm-prayer-timing.json"));if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;File target=chooser.getSelectedFile();if(target.exists()){JOptionPane.showMessageDialog(this,"Choose a new filename to preserve the existing export.");return;}
   gameTask(()->{com.google.gson.JsonObject root=new com.google.gson.JsonObject();com.google.gson.Gson gson=new com.google.gson.GsonBuilder().setPrettyPrinting().create();root.addProperty("schema","orion-olm-prayer-timing-v1");root.add("timing",gson.toJsonTree(runtime.chambers.learning.checkpoint()));root.add("outcomes",gson.toJsonTree(runtime.chambers.learning.outcomes()));final String json=gson.toJson(root);java.util.concurrent.CompletableFuture.runAsync(()->{try{OlmPrayerLearning.saveNew(target.toPath(),json);}catch(IOException error){SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(this,"Prayer timing could not be saved: "+error.getMessage()));}});});
  }
  private void loadPrayerTiming()throws IOException{
   JFileChooser chooser=new JFileChooser();if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;File source=chooser.getSelectedFile();if(source.length()>256*1024)throw new IOException("Prayer timing export exceeds 256 KB");String text=new String(java.nio.file.Files.readAllBytes(source.toPath()),StandardCharsets.UTF_8);com.google.gson.JsonObject root=new com.google.gson.JsonParser().parse(text).getAsJsonObject();if(!root.has("schema")||!"orion-olm-prayer-timing-v1".equals(root.get("schema").getAsString()))throw new IOException("Not a prayer timing export");OlmPrayerLearning.Checkpoint next=new com.google.gson.Gson().fromJson(root.get("timing"),OlmPrayerLearning.Checkpoint.class);new OlmPrayerLearning().restore(next);gameTask(()->{runtime.chambers.stop("Loading prayer timing; re-arm explicitly");runtime.chambers.learning.restore(next);});
  }
 private void gameTask(Runnable task){runtime.thread.invoke(()->{try{task.run();}catch(RuntimeException error){SwingUtilities.invokeLater(()->{if(isDisplayable())JOptionPane.showMessageDialog(this,error.getMessage(),"Chambers demonstration",JOptionPane.ERROR_MESSAGE);});}});}
 private void saveDemonstration()throws IOException{
  if(runtime.demonstration.recording)throw new IllegalStateException("Stop recording before saving");
  if(!runtime.demonstration.hasData())throw new IllegalStateException("No demonstration to save");
  JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("orion-chambers-demonstration.json"));if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
  File file=chooser.getSelectedFile();if(file.exists()&&JOptionPane.showConfirmDialog(this,"Replace this demonstration file?","Save",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
  try(Writer writer=new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8)){writer.write(runtime.demonstration.export());}
 }
 private void saveMovementLearning()throws IOException{
  JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("olm-movement-learning.json"));if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;File file=chooser.getSelectedFile();if(file.exists()&&JOptionPane.showConfirmDialog(this,"Replace this movement learning file?","Save",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
  try(Writer writer=new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8)){writer.write(runtime.olm.exportSpecialLearning());}
 }
 private void loadMovementLearning()throws IOException{
  JFileChooser chooser=new JFileChooser();if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;File file=chooser.getSelectedFile();if(file.length()>65536)throw new IllegalArgumentException("Movement learning exceeds 64 KB");StringBuilder json=new StringBuilder();try(Reader reader=new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8)){char[] buffer=new char[4096];int count;while((count=reader.read(buffer))!=-1){if(json.length()+count>65536)throw new IllegalArgumentException("Movement learning too large");json.append(buffer,0,count);}}String text=json.toString();gameTask(()->runtime.olm.restoreSpecialLearning(text));
 }
 private void loadDemonstration()throws IOException{
  if(runtime.demonstration.hasData()||runtime.demonstration.recording)throw new IllegalStateException("Discard the current demonstration before loading another");
  JFileChooser chooser=new JFileChooser();if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;File file=chooser.getSelectedFile();
  if(file.length()>128L*1024*1024)throw new IllegalArgumentException("Demonstration exceeds 128 MB limit");
  StringBuilder text=new StringBuilder();try(Reader reader=new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8)){char[] buffer=new char[8192];int count;while((count=reader.read(buffer))!=-1){if(text.length()+count>128*1024*1024)throw new IllegalArgumentException("Demonstration too large");text.append(buffer,0,count);}}
  runtime.demonstration.restore(text.toString());
 }
 private static String phaseGuidance(int cue){switch(cue){case 1:return "User-labeled phase 3: lower melee hand HP, then coordinate both hand kills.";case 2:return "User-labeled regular phase: mage hand first, then special attack and finish melee hand.";default:return "Neck cue unknown. The baseline can also infer phases from confirmed hand kills.";}}
 private void capture(){
  runtime.thread.invoke(()->{
   String value;
   try{
    if(runtime.client.getGameState()!=GameState.LOGGED_IN||runtime.client.getLocalPlayer()==null)value="Not logged in. No observation captured.";
    else{
     Player self=runtime.client.getLocalPlayer();StringBuilder b=new StringBuilder();
     b.append("Tick: ").append(runtime.client.getTickCount()).append("\nTile: ").append(self.getWorldLocation()).append("\nHP: ").append(runtime.client.getBoostedSkillLevel(Skill.HITPOINTS)).append("\nPrayer: ").append(runtime.client.getBoostedSkillLevel(Skill.PRAYER)).append("\nLocal animation: ").append(self.getAnimation()).append("\nLoaded NPCs (not automatic room identification):\n");
     int count=0;for(NPC npc:runtime.client.getNpcs()){
      if(count++>=30){b.append("Additional NPCs omitted.\n");break;}
      b.append("ID ").append(npc.getId()).append(" / ").append(npc.getName()).append(" / animation ").append(npc.getAnimation()).append(" / tile ").append(npc.getWorldLocation()).append('\n');
     }
     value=b.toString();
    }
   }catch(RuntimeException error){value="Observation unavailable. No raid actions were issued.";}
   final String snapshot=value;SwingUtilities.invokeLater(()->{if(isDisplayable())observation.setText(snapshot);});
  });
 }
 private void save()throws IOException{
  JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("orion-chambers-notes.txt"));
  if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
  File destination=chooser.getSelectedFile();
  if(destination.exists()&&JOptionPane.showConfirmDialog(this,"Replace this existing notes file?","Save teaching notes",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
  try(Writer writer=new OutputStreamWriter(new FileOutputStream(destination),StandardCharsets.UTF_8)){
   writer.write("Orion Chambers teaching notes\nRoom / encounter: "+room.getText()+"\nManual neck cue: "+neckCue.getSelectedItem()+"\n"+phaseGuidance(neckCue.getSelectedIndex())+"\n\nUser instructions (unverified; not a trained policy):\n"+instructions.getText()+"\n\nManual client observation:\n"+observation.getText());
  }
 }
}
