package owl.nh;
import javax.swing.*;
import java.awt.*;
import java.io.File;

/** Standalone offline viewer; never starts or attaches to Orion. */
public final class ScriptedFightPreview {
 public static void main(String[] args) {
  SwingUtilities.invokeLater(()->{
   NHTrainingConnection connection=new NHTrainingConnection(new File(System.getProperty("owl.project.root",".")));
   JFrame window=new JFrame("Watch Stage 3 NH fighter");
   final InventoryViewer[] inventory={null};
   window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
   JPanel panel=new JPanel();panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
   panel.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
   panel.add(new JLabel("Live local 3D fight — Stage 3 scripted fighter, no learning."));
   JComboBox<String> opponent=new JComboBox<>(new String[]{"fixed-ranged","fixed-magic","fixed-melee","seeded-rapid-switch","seeded-random-prayer","mirror"});
   JSpinner delay=new JSpinner(new SpinnerNumberModel(600,50,2000,50));
   JPanel choices=new JPanel();choices.add(new JLabel("Opponent"));choices.add(opponent);choices.add(new JLabel("Tick delay (ms)"));choices.add(delay);panel.add(choices);
   JTextArea status=new JTextArea(6,65);status.setEditable(false);panel.add(new JScrollPane(status));
   JPanel controls=new JPanel();JButton start=new JButton("Watch fight"),cancel=new JButton("Stop fight");
   controls.add(start);controls.add(cancel);JButton inventoryButton=new JButton("Agent inventory");controls.add(inventoryButton);inventoryButton.addActionListener(e->InventoryViewer.open(window,connection,new File(System.getProperty("owl.project.root",".")),inventory));panel.add(controls);
   start.addActionListener(e->{try{
    if(connection.busy())throw new IllegalStateException("Stop the current fight before starting another.");
    Desktop.getDesktop().browse(connection.browserUri());
    connection.watchScripted((String)opponent.getSelectedItem(),1,256,(Integer)delay.getValue());
    InventoryViewer.open(window,connection,new File(System.getProperty("owl.project.root",".")),inventory);
   }catch(Exception error){JOptionPane.showMessageDialog(window,error.getMessage(),"Cannot start fight",JOptionPane.ERROR_MESSAGE);}});
   cancel.addActionListener(e->connection.cancel());
   Timer refresh=new Timer(200,e->{status.setText(connection.status+"\n"+connection.phase);start.setEnabled(!connection.busy());});refresh.start();
   window.addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosed(java.awt.event.WindowEvent e){refresh.stop();connection.close();}});
   window.setContentPane(panel);window.pack();window.setLocationRelativeTo(null);window.setVisible(true);
  });
 }
}
