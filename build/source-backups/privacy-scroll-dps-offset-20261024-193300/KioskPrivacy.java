package owl.nh;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.*;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;
/** Always-on presentation masking. It owns no mouse/keyboard hooks or game mutations. */
final class KioskPrivacy {
 private final Client client;private OverlayManager manager;volatile java.util.List<String> names=Collections.emptyList();private Frame frame;private String originalTitle;private PropertyChangeListener titleListener;private javax.swing.Timer timer;volatile String status="Starting";
 final Overlay overlay;
 KioskPrivacy(Client client){this.client=client;overlay=new MaskOverlay();}
 void observe(){LinkedHashSet<String> seen=new LinkedHashSet<>(names);Player self=client.getLocalPlayer();if(self!=null&&self.getName()!=null&&!self.getName().isBlank())seen.add(KioskText.plain(self.getName()));String account=client.getUsername();if(account!=null&&!account.isBlank())seen.add(KioskText.plain(account));while(seen.size()>4)seen.remove(seen.iterator().next());names=Collections.unmodifiableList(new ArrayList<>(seen));}
 void attach(OverlayManager manager){this.manager=manager;manager.add(overlay);timer=new javax.swing.Timer(100,e->maskTitle());timer.start();maskTitle();status="Always on";}
 private void maskTitle(){if(client.getCanvas()==null)return;Window owner=SwingUtilities.getWindowAncestor(client.getCanvas());if(!(owner instanceof Frame))return;if(frame!=owner){if(frame!=null&&titleListener!=null)frame.removePropertyChangeListener("title",titleListener);frame=(Frame)owner;originalTitle=frame.getTitle();titleListener=e->maskTitle();frame.addPropertyChangeListener("title",titleListener);}String current=frame.getTitle(),masked=KioskText.title(current,names);if(!masked.equals(current)){originalTitle=current;frame.setTitle(masked);}}
 void close(OverlayManager manager){if(timer!=null)timer.stop();if(frame!=null&&titleListener!=null){frame.removePropertyChangeListener("title",titleListener);if(originalTitle!=null)frame.setTitle(originalTitle);}if(manager!=null)manager.remove(overlay);this.manager=null;names=Collections.emptyList();status="Detached";}
 private final class MaskOverlay extends Overlay {
  MaskOverlay(){setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ALWAYS_ON_TOP);setPriority(OverlayPriority.HIGHEST);}
  @Override public Dimension render(Graphics2D graphics){observe();if(names.isEmpty())return null;Graphics2D g=(Graphics2D)graphics.create();try{g.setColor(Color.BLACK);g.setFont(FontManager.getRunescapeSmallFont());widgetMasks(g);Player self=client.getLocalPlayer();if(self!=null&&self.getName()!=null){net.runelite.api.Point p=self.getCanvasTextLocation(g,KioskText.plain(self.getName()),self.getLogicalHeight()+40);if(p!=null){FontMetrics fm=g.getFontMetrics();g.fillRect(p.getX()-3,p.getY()-fm.getAscent()-2,fm.stringWidth(KioskText.plain(self.getName()))+6,fm.getHeight()+3);}}menuMasks(g);dpsMasks(g);}finally{g.dispose();}return null;}
  private void widgetMasks(Graphics2D g){Set<Widget> seen=Collections.newSetFromMap(new IdentityHashMap<>());ArrayDeque<Widget> queue=new ArrayDeque<>();add(queue,client.getWidgetRoots());while(!queue.isEmpty()&&seen.size()<10000){Widget w=queue.remove();if(!seen.add(w)||!VisibleCombatUi.effectivelyVisible(w))continue;String text=w.getText();if(text!=null&&!text.isEmpty()){FontTypeFace font=w.getFont();java.util.function.ToIntFunction<String> width=font==null?s->g.getFontMetrics().stringWidth(KioskText.plain(s)):font::getTextWidth;for(Rectangle mask:KioskText.masks(text,w.getBounds(),w.getXTextAlignment(),w.getYTextAlignment(),w.getLineHeight(),width,names))g.fillRect(mask.x,mask.y,mask.width,mask.height);}add(queue,w.getChildren());add(queue,w.getStaticChildren());add(queue,w.getDynamicChildren());add(queue,w.getNestedChildren());}}
  private void dpsMasks(Graphics2D g){if(manager==null)return;g.setFont(FontManager.getRunescapeFont());for(Overlay source:manager.getOverlays()){if(source==overlay)continue;for(Rectangle r:KioskDpsMask.rectangles(source,names,s->g.getFontMetrics().stringWidth(s)))g.fillRect(r.x,r.y,r.width,r.height);}}  private void menuMasks(Graphics2D g){if(!client.isMenuOpen())return;MenuEntry[] entries=client.getMenuEntries();if(entries==null)return;g.setFont(FontManager.getRunescapeFont());for(int i=0;i<entries.length;i++){MenuEntry e=entries[i];String raw=e.getOption()+" "+e.getTarget(),text=KioskText.plain(raw);int baseline=client.getMenuY()+31+(entries.length-1-i)*15;for(int[] span:KioskText.spans(text,names)){int x=client.getMenuX()+3+g.getFontMetrics().stringWidth(text.substring(0,span[0]));int icons=0;String prefix=KioskText.rawPrefix(raw,span[0]);for(int at=prefix.indexOf("<img=");at>=0;at=prefix.indexOf("<img=",at+1))icons++;x+=icons*13;g.fillRect(x-2,baseline-13,g.getFontMetrics().stringWidth(text.substring(span[0],span[1]))+5,16);}}}
  private void add(ArrayDeque<Widget> q,Widget[] widgets){if(widgets!=null)for(Widget w:widgets)if(w!=null)q.add(w);}
 }
}

