package owl.nh;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.ToIntFunction;
import net.runelite.client.ui.overlay.*;
/** Read-only adapter for the installed DPS panel, whose rendered children are cleared. */
final class KioskDpsMask {
 private static Object field(Object target,String name)throws ReflectiveOperationException{for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 static java.util.List<Rectangle> rectangles(Overlay overlay,Collection<String> protectedNames,ToIntFunction<String> width){
  if(!overlay.getClass().getName().equals("net.runelite.client.plugins.dpscounter.DpsOverlay")||!(overlay instanceof OverlayPanel))return Collections.emptyList();
  try{Object plugin=field(overlay,"dpsCounterPlugin");Map<?,?> members=(Map<?,?>)field(plugin,"members");Object total=field(plugin,"total");java.util.List<String> names=new ArrayList<>();int ownDamage=-1;for(Object member:members.values()){String name=(String)field(member,"name");names.add(name);if(!KioskText.spans(KioskText.plain(name),protectedNames).isEmpty())ownDamage=((Number)field(member,"damage")).intValue();}if(ownDamage<0)return Collections.emptyList();Object panel=((OverlayPanel)overlay).getPanelComponent();Rectangle border=(Rectangle)field(panel,"border");Point gap=(Point)field(panel,"gap");boolean totalRow=((Number)field(total,"damage")).intValue()>ownDamage;return rows(overlay.getBounds(),border,gap.y,names,totalRow,protectedNames,width);}catch(ReflectiveOperationException|RuntimeException unsupported){return Collections.emptyList();}
 }
 static java.util.List<Rectangle> rows(Rectangle bounds,Rectangle border,int gap,java.util.List<String> members,boolean total,Collection<String> names,ToIntFunction<String> width){java.util.List<Rectangle> out=new ArrayList<>();int count=1+members.size()+(total?1:0);if(bounds==null||bounds.width<=0||bounds.height<=0||count<=1)return out;int rowHeight=Math.max(1,(bounds.height-border.y-border.height-gap*(count-1))/count);for(int row=0;row<members.size();row++){String text=KioskText.plain(members.get(row));int top=bounds.y+border.y+(row+1)*(rowHeight+gap);for(int[] span:KioskText.spans(text,names)){int left=bounds.x+border.x+width.applyAsInt(text.substring(0,span[0]));int length=width.applyAsInt(text.substring(span[0],span[1]));Rectangle r=new Rectangle(left-2,top-2,length+8,rowHeight+4).intersection(bounds);if(r.width>0&&r.height>0)out.add(r);}}return out;}
}
