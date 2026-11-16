package owl.nh;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import javax.swing.*;

/** A 2D view of real simulator snapshots, not a recording of the public browser. */
final class FightViewer extends JDialog {
 private final javax.swing.Timer refresh;
 FightViewer(JFrame owner,NHTrainingConnection connection) {
  super((Frame)null,"NH Trainer - live fight and learning progress",false);setSize(980,830);setMinimumSize(new Dimension(800,740));setLocationRelativeTo(owner);setDefaultCloseOperation(DISPOSE_ON_CLOSE);PrototypePanel.registerWindow(owner,this);
  setContentPane(new Canvas(connection));refresh=new javax.swing.Timer(50,e->repaint());refresh.start();addWindowListener(new WindowAdapter(){@Override public void windowClosed(WindowEvent e){refresh.stop();}});
 }
 static final class Canvas extends JPanel {
  final NHTrainingConnection connection;static final Color BACK=new Color(17,24,39),TEXT=new Color(226,232,240),MUTED=new Color(148,163,184),AGENT=new Color(56,189,248),ENEMY=new Color(251,146,60);
  Canvas(NHTrainingConnection c){connection=c;setBackground(BACK);}
  @Override protected void paintComponent(Graphics graphics) {
   super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();try {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setFont(new Font("Segoe UI",Font.PLAIN,13));
    FightFrame frame=connection.fightFrame;
    text(g,"LIVE SIMULATOR VIEW  /  2D",20,28,AGENT,16,true);
    if(frame==null){text(g,"Enable Watch current fight, then start training or evaluate a checkpoint.",20,65,TEXT,14,false);text(g,connection.status,20,93,MUTED,13,false);return;}
    text(g,"Fight "+frame.episode+"   |   Tick "+frame.tick+"   |   Total decisions "+frame.steps+"   |   "+(frame.mode.equals("training")?"Training with exploration":"Evaluation - weights fixed"),20,54,TEXT,14,false);
    text(g,connection.busy()?connection.phase:"Job ended - showing its last snapshot",20,78,MUTED,13,false);
    int chartHeight=120,cardHeight=208,arenaHeight=Math.max(170,getHeight()-chartHeight-cardHeight-180);
    arena(g,frame,20,96,getWidth()-40,arenaHeight);
    int cardY=108+arenaHeight,cardWidth=(getWidth()-52)/2;
    card(g,frame.fighters[0],20,cardY,cardWidth,cardHeight,AGENT,frame.mode.equals("training")?"Learning agent":"Saved checkpoint");
    card(g,frame.fighters[1],32+cardWidth,cardY,cardWidth,cardHeight,ENEMY,"Opponent");
    int graphY=cardY+cardHeight+12;plot(g,connection.episodeReturns(),20,graphY,getWidth()-40,chartHeight);
    text(g,"Completed fights: "+(frame.wins+frame.losses+frame.draws)+"   |   Wins "+frame.wins+"   Losses "+frame.losses+"   Draws "+frame.draws,20,graphY+chartHeight+24,TEXT,13,false);
    text(g,"Compare checkpoints on the same test opponents. Training wins alone do not prove improvement.",20,graphY+chartHeight+45,MUTED,12,false);
   }finally{g.dispose();}
  }
  void arena(Graphics2D g,FightFrame f,int x,int y,int width,int height) {
   g.setColor(new Color(30,41,59));g.fillRoundRect(x,y,width,height,14,14);
   int cols=f.arena.maxX-f.arena.minX+1,rows=f.arena.maxY-f.arena.minY+1;double cell=Math.min((width-36.0)/cols,(height-34.0)/rows);
   int left=x+(int)((width-cols*cell)/2),top=y+(int)((height-rows*cell)/2);
   g.setColor(new Color(51,65,85));for(int i=0;i<=cols;i++)g.drawLine(left+(int)(i*cell),top,left+(int)(i*cell),top+(int)(rows*cell));for(int i=0;i<=rows;i++)g.drawLine(left,top+(int)(i*cell),left+(int)(cols*cell),top+(int)(i*cell));
   boolean overlap=f.fighters[0].x==f.fighters[1].x&&f.fighters[0].y==f.fighters[1].y;
   for(int i=0;i<2;i++) {
    FightFrame.Fighter fighter=f.fighters[i];int px=left+(int)((fighter.x-f.arena.minX+.5)*cell),py=top+(int)((f.arena.maxY-fighter.y+.5)*cell);if(overlap)px+=i==0?-8:8;
    int radius=(int)Math.max(8,Math.min(17,cell*.33));Color color=i==0?AGENT:ENEMY;g.setColor(color);g.fillOval(px-radius,py-radius,radius*2,radius*2);
    if(fighter.freezeTicks>0){g.setStroke(new BasicStroke(3));g.setColor(new Color(186,230,253));g.drawOval(px-radius-4,py-radius-4,radius*2+8,radius*2+8);g.setStroke(new BasicStroke(1));}
    text(g,i==0?"A":"B",px-4,py+5,BACK,13,true);String prayer=prayer(fighter.overhead);int labelWidth=g.getFontMetrics().stringWidth(prayer);text(g,prayer,px-labelWidth/2,py-radius-9,TEXT,11,false);
   }
   text(g,overlap?"Both fighters occupy the same tile":"Grid shows actual simulator tiles; scenery and collision are not rendered",x+12,y+height-8,MUTED,11,false);
  }
  void card(Graphics2D g,FightFrame.Fighter f,int x,int y,int width,int height,Color color,String label) {
   g.setColor(new Color(30,41,59));g.fillRoundRect(x,y,width,height,12,12);text(g,label,x+12,y+23,color,15,true);
   int barWidth=width-24;g.setColor(new Color(51,65,85));g.fillRoundRect(x+12,y+35,barWidth,17,5,5);g.setColor(f.hp>50?new Color(34,197,94):f.hp>25?new Color(250,204,21):new Color(248,113,113));g.fillRoundRect(x+12,y+35,(int)(barWidth*Math.max(0,Math.min(1,f.hp/(double)f.maxHp))),17,5,5);text(g,"HP "+Math.max(0,f.hp),x+17,y+49,BACK,12,true);
   text(g,"Prayer "+f.prayer+"   Food "+f.food+"   Spec "+(int)f.spec+"%",x+12,y+75,TEXT,13,false);
   text(g,fit(g,"Weapon: "+f.weaponName,width-24),x+12,y+99,TEXT,13,false);
   text(g,"Last style: "+f.style+"   Overhead: "+prayer(f.overhead),x+12,y+122,TEXT,12,false);
   text(g,"Frozen: "+f.freezeTicks+" ticks   Attack cooldown: "+f.cooldown,x+12,y+145,MUTED,12,false);
   text(g,"Damage dealt: "+(int)f.damage+"   Tile: "+f.x+", "+f.y,x+12,y+167,MUTED,12,false);
   text(g,fit(g,"Intent: "+f.lastIntent.replace('_',' '),width-24),x+12,y+190,TEXT,11,false);
  }
  void plot(Graphics2D g,List<Double> values,int x,int y,int width,int height) {
   g.setColor(new Color(30,41,59));g.fillRoundRect(x,y,width,height,12,12);text(g,"Recent mean fight return (rolling 20) - not a win-rate estimate",x+12,y+21,TEXT,12,false);
   if(values.isEmpty()){text(g,"Finish a fight to add a point. Draws can produce a flat line.",x+12,y+57,MUTED,12,false);return;}
   double[] mean=new double[values.size()];double lo=-1,hi=1;for(int i=0;i<mean.length;i++){double total=0;int from=Math.max(0,i-19);for(int j=from;j<=i;j++)total+=values.get(j);mean[i]=total/(i-from+1);lo=Math.min(lo,mean[i]);hi=Math.max(hi,mean[i]);}
   int left=x+48,top=y+34,w=width-62,h=height-46;g.setColor(new Color(71,85,105));int zero=top+(int)(hi/(hi-lo)*h);g.drawLine(left,zero,left+w,zero);g.setColor(AGENT);g.setStroke(new BasicStroke(2));int previousX=left,previousY=top+(int)((hi-mean[0])/(hi-lo)*h);
   for(int i=0;i<mean.length;i++){int px=left+(int)(i*w/(double)Math.max(1,mean.length-1)),py=top+(int)((hi-mean[i])/(hi-lo)*h);g.drawLine(previousX,previousY,px,py);g.fillOval(px-2,py-2,4,4);previousX=px;previousY=py;}g.setStroke(new BasicStroke(1));text(g,String.format(java.util.Locale.ROOT,"%.1f",hi),x+8,top+10,MUTED,11,false);text(g,String.format(java.util.Locale.ROOT,"%.1f",lo),x+8,top+h,MUTED,11,false);
  }
  static String prayer(String raw){if(raw==null)return "None";if(raw.equals("PROTECT_FROM_MISSILES"))return "Ranged";if(raw.equals("PROTECT_FROM_MAGIC"))return "Magic";if(raw.equals("PROTECT_FROM_MELEE"))return "Melee";return raw.equals("NONE")?"None":raw.charAt(0)+raw.substring(1).toLowerCase(java.util.Locale.ROOT);}
  static void text(Graphics2D g,String value,int x,int y,Color color,int size,boolean bold){g.setColor(color);g.setFont(new Font("Segoe UI",bold?Font.BOLD:Font.PLAIN,size));g.drawString(value==null?"":value,x,y);}
  static String fit(Graphics2D g,String value,int width){if(value==null)return "";while(value.length()>1&&g.getFontMetrics().stringWidth(value)>width)value=value.substring(0,value.length()-2)+"…";return value;}
 }
}
