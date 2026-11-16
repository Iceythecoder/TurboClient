package owl.nh;
import java.awt.*;
import javax.swing.*;
import static owl.nh.CombatCore.*;

final class PromptInfoDialog extends JDialog {
 PromptInfoDialog(JFrame owner,CombatRuntime runtime,java.util.function.Consumer<Settings> applied) {
  super((Frame)null,"Agent information / prompt",false);setSize(800,680);setLocationRelativeTo(owner);
  JPanel main=new JPanel(new BorderLayout(8,8));main.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));setContentPane(main);
  JTextArea description=new JTextArea("PPO is not a chat model. This window imports verified weapon/animation info and preferences into the app's rules.\nIt does not retrain a policy or give it general language understanding. Notes stay in memory; imported rules can be saved using Save preset.");description.setEditable(false);description.setBackground(main.getBackground());description.setLineWrap(true);description.setWrapStyleWord(true);description.setRows(3);main.add(description,BorderLayout.NORTH);
  JTextArea input=new JTextArea(runtime.agentPrompt),review=new JTextArea();input.setFont(new Font("Monospaced",Font.PLAIN,12));review.setEditable(false);review.setFont(new Font("Monospaced",Font.PLAIN,12));
  JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,new JScrollPane(input),new JScrollPane(review));split.setResizeWeight(.65);main.add(split,BorderLayout.CENTER);
  JCheckBox partial=new JCheckBox("Apply recognized lines even if other lines are unrecognized");JPanel bottom=new JPanel(new BorderLayout());bottom.add(partial,BorderLayout.NORTH);JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));bottom.add(buttons,BorderLayout.SOUTH);main.add(bottom,BorderLayout.SOUTH);
  JButton example=new JButton("Insert format examples"),preview=new JButton("Preview interpretation"),apply=new JButton("Apply information"),cancel=new JButton("Close");buttons.add(example);buttons.add(preview);buttons.add(apply);buttons.add(cancel);
  example.addActionListener(e->input.setText(KnowledgePrompt.EXAMPLE));
  preview.addActionListener(e->{try{review.setText(KnowledgePrompt.parse(input.getText(),runtime.config).summary());}catch(RuntimeException error){review.setText(error.getMessage());}});
  apply.addActionListener(e->{try {
   KnowledgePrompt.Preview result=KnowledgePrompt.parse(input.getText(),runtime.config);review.setText(result.summary());
   if(!result.unrecognized.isEmpty()&&!partial.isSelected())throw new IllegalArgumentException("Resolve unrecognized lines, or explicitly choose to apply only recognized lines.");
   if(result.applied.isEmpty()&&result.notes.isEmpty())throw new IllegalArgumentException("No supported information found.");
   runtime.armed=false;String text=input.getText();runtime.thread.invoke(()->{runtime.settings(result.settings);runtime.agentPrompt=text;runtime.agentNotes=String.join("\n",result.notes);runtime.agent.resetContext();SwingUtilities.invokeLater(()->{applied.accept(result.settings);dispose();});});
  }catch(RuntimeException error){JOptionPane.showMessageDialog(this,error.getMessage(),"Information not applied",JOptionPane.WARNING_MESSAGE);}});
  cancel.addActionListener(e->dispose());setDefaultCloseOperation(DISPOSE_ON_CLOSE);PrototypePanel.registerWindow(owner,this);
 }
}
