package owl.nh;
import java.util.*;
import javax.swing.*;
import net.runelite.client.RuneLite;

/** Starts a separate Orion process and attaches a companion to its existing event bus.
 * No installation patch, plugin allowlist change, agent injection or running-process attachment.
 */
public final class Bootstrap {
 public static void main(String[] args) {
  boolean probe=Arrays.asList(args).contains("--startup-check");StartupDiagnostics diagnostics=null;
  try {
   if(Arrays.asList(args).contains("--self-test")){CoreTests.main(args);KioskPrivacyTests.main(args);IntegrationTests.main(args);ChambersTests.main(args);OlmPrayerLearningTests.main(args);OlmOffensivePrayerTests.main(args);ChambersDemonstrationTests.main(args);OlmStrategyTests.main(args);OlmSpecialsTests.main(args);OlmOverlapTests.main(args);OlmRaidFinishTests.main(args);OlmLoopTests.main(args);OlmPoolTests.main(args);OlmHeadCombatTests.main(args);OlmTravelTests.main(args);OlmLearningTests.main(args);OlmPriorityTests.main(args);OlmFoundationTests.main(args);OlmEncounterFlowTests.main(args);OlmUiHazardTests.main(args);LearningTests.main(args);NHTrainingTests.main(args);ReloadTests.main(args);return;}
   if(Arrays.asList(args).contains("--diagnostics")){IntegrationTests.main(args);return;}
   if(probe){Thread timeout=new Thread(()->{try{Thread.sleep(60000);System.err.println("STARTUP_TIMEOUT");System.exit(2);}catch(InterruptedException ignored){}},"startup-probe-timeout");timeout.setDaemon(true);timeout.start();}
   diagnostics=new StartupDiagnostics();diagnostics.begin();
   String[] clientArgs=Arrays.stream(args).filter(a->!a.equals("--startup-check")).toArray(String[]::new);RuneLite.main(clientArgs);
   if(Arrays.asList(args).contains("--help"))return;
   if(RuneLite.getInjector()==null)throw new IllegalStateException("Orion did not initialize its injector",diagnostics.failure);
   if(RuneLite.getInjector().getInstance(net.runelite.api.Client.class)==null)throw new IllegalStateException("Orion did not load its RuneLite game API. The game client may have failed to download or loaded in vanilla mode.",diagnostics.failure);
   diagnostics.end();
   CompanionHost.start(RuneLite.getInjector(),()->{if(probe){javax.swing.Timer stableCheck=new javax.swing.Timer(5000,event->{System.out.println("STARTUP_OK reload host and companion initialized");System.exit(0);});stableCheck.setRepeats(false);stableCheck.start();}},error->{if(probe){System.err.println(StartupDiagnostics.classes(error));System.exit(1);}report(error);});
  }catch(Throwable error){if(diagnostics!=null)diagnostics.end();if(probe){System.err.println(StartupDiagnostics.classes(error));System.exit(1);}if(Arrays.asList(args).contains("--self-test")||Arrays.asList(args).contains("--diagnostics")){System.err.println("Checks failed: "+error.getClass().getSimpleName()+" / "+error.getMessage());System.exit(1);}report(error);}
 }
 static void report(Throwable error){SwingUtilities.invokeLater(()->{JTextArea detail=new JTextArea("Prototype could not start:\n"+StartupDiagnostics.detail(error),16,75);detail.setEditable(false);detail.setLineWrap(true);detail.setWrapStyleWord(true);JOptionPane.showMessageDialog(null,new JScrollPane(detail),"Orion NH startup",JOptionPane.ERROR_MESSAGE);});}
}


