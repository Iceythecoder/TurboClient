package owl.nh;
import com.google.gson.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Read-only inventory snapshots. No game input, recording or external requests. */
final class InventoryViewer extends JDialog {
 private final NHTrainingConnection connection;
 private final JLabel status=new JLabel("Waiting for a fight"),legend=new JLabel("Yellow highlights changed slots.");
 private final JLabel[] cells=new JLabel[28];
 private final int[] ids=new int[28],quantities=new int[28];
 private final long[] highlightUntil=new long[28];
 private final Map<Integer,ImageIcon> icons=new HashMap<>();
 private final Map<Integer,String> names=new HashMap<>();
 private final javax.swing.Timer refresh;
 private int episode=-1,lastTick=-1;
 InventoryViewer(Frame owner,NHTrainingConnection connection,File root) {
  super((Frame)null,"Agent inventory — local NH fight",false);this.connection=connection;
  setDefaultCloseOperation(DISPOSE_ON_CLOSE);PrototypePanel.registerWindow(owner,this);setResizable(false);
  loadIcons(root);
  JPanel content=new JPanel(new BorderLayout(6,6));content.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
  content.add(status,BorderLayout.NORTH);JPanel grid=new JPanel(new GridLayout(7,4,5,5));
  for(int i=0;i<28;i++){JLabel cell=new JLabel("",SwingConstants.CENTER);cell.setPreferredSize(new Dimension(54,48));
   cell.setOpaque(true);cell.setBackground(new Color(56,48,40));cell.setForeground(Color.WHITE);
   cell.setHorizontalTextPosition(SwingConstants.RIGHT);cell.setFont(new Font("SansSerif",Font.BOLD,10));
   cell.setBorder(BorderFactory.createLineBorder(new Color(91,78,61)));cells[i]=cell;grid.add(cell);}
  content.add(grid,BorderLayout.CENTER);JPanel bottom=new JPanel(new BorderLayout());bottom.add(legend,BorderLayout.NORTH);
  JCheckBox top=new JCheckBox("Keep on top",true);top.addActionListener(e->setAlwaysOnTop(top.isSelected()));bottom.add(top,BorderLayout.SOUTH);content.add(bottom,BorderLayout.SOUTH);
  setContentPane(content);pack();setAlwaysOnTop(true);
  Rectangle screen=owner.getGraphicsConfiguration().getBounds();
  setLocation(Math.max(screen.x,Math.min(owner.getX()+owner.getWidth()+8,screen.x+screen.width-getWidth())),
   Math.max(screen.y,Math.min(owner.getY(),screen.y+screen.height-getHeight())));
  refresh=new javax.swing.Timer(100,e->updateSnapshot());refresh.start();
  addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosed(java.awt.event.WindowEvent e){refresh.stop();}});
  updateSnapshot();
 }
 private void loadIcons(File root) {
  File assets=new File(root,"learning/nh-trainer/browser-source/fixtures/render/sprites");
  try {
   BufferedImage atlas=ImageIO.read(new File(assets,"item_sprites.png"));
   JsonObject definition;
   try(Reader reader=new InputStreamReader(new FileInputStream(new File(assets,"item_sprites.json")),StandardCharsets.UTF_8)){definition=new JsonParser().parse(reader).getAsJsonObject();}
   for(JsonElement element:definition.getAsJsonArray("sprites")){
    JsonObject sprite=element.getAsJsonObject();
    if(!sprite.get("variant").getAsString().equals("normal")||sprite.get("selected").getAsBoolean())continue;
    int id=sprite.get("itemId").getAsInt();
    icons.put(id,new ImageIcon(atlas.getSubimage(sprite.get("x").getAsInt(),sprite.get("y").getAsInt(),sprite.get("width").getAsInt(),sprite.get("height").getAsInt())));
    names.put(id,sprite.get("name").getAsString());
   }
  }catch(Exception error){legend.setText("Icons unavailable; showing actual item IDs.");}
 }
 void updateSnapshot() {
  FightFrame frame=connection.fightFrame;
  if(frame==null||frame.fighters==null||frame.fighters.length<1||frame.fighters[0].inventory==null){
   status.setText(frame==null?"Waiting for a fight":"Inventory unavailable for this setup");
   for(int i=0;i<28;i++){cells[i].setIcon(null);cells[i].setText("");cells[i].setToolTipText("No inventory snapshot");cells[i].setBackground(new Color(56,48,40));ids[i]=quantities[i]=0;highlightUntil[i]=0;}
   episode=lastTick=-1;return;
  }
  FightFrame.InventorySlot[] slots=frame.fighters[0].inventory;
  if(slots.length!=28){status.setText("Invalid inventory snapshot");return;}
  boolean reset=episode!=frame.episode||frame.tick<lastTick;
  long now=System.nanoTime();
  for(int i=0;i<28;i++){
   FightFrame.InventorySlot slot=slots[i];int id=slot==null?0:slot.itemId,quantity=slot==null?0:slot.quantity;
   if(!reset&&(id!=ids[i]||quantity!=quantities[i]))highlightUntil[i]=now+900000000L;
   if(reset)highlightUntil[i]=0;
   ids[i]=id;quantities[i]=quantity;
   ImageIcon icon=icons.get(id);cells[i].setIcon(icon);
   cells[i].setText(id==0?"":icon==null?"#"+id:quantity>1?Integer.toString(quantity):"");
   cells[i].setToolTipText("Slot "+(i+1)+": "+(id==0?"Empty":names.getOrDefault(id,"Item "+id)+" × "+quantity));
   cells[i].setBackground(now<highlightUntil[i]?new Color(137,103,29):new Color(56,48,40));
  }
  episode=frame.episode;lastTick=frame.tick;
  status.setText("Fight "+frame.episode+" · Tick "+frame.tick+(connection.busy()?" · Live":" · Last snapshot"));
 }
 static void open(Frame owner,NHTrainingConnection connection,File root,InventoryViewer[] existing) {
  if(existing[0]==null||!existing[0].isDisplayable())existing[0]=new InventoryViewer(owner,connection,root);
  existing[0].setVisible(true);existing[0].toFront();
 }
}
