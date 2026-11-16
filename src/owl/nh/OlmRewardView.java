package owl.nh;
import java.util.*;
import java.awt.Rectangle;
import net.runelite.api.widgets.*;

/** Read legacy item containers and individual dynamic reward widgets. */
final class OlmRewardView {
 static final class Slot {final int id,quantity;final Rectangle bounds;Slot(int id,int quantity,Rectangle bounds){this.id=id;this.quantity=quantity;this.bounds=bounds;}}
 final List<Slot> items=new ArrayList<>();boolean visible,readable;int scannedWidgets;
 static OlmRewardView read(Widget container){
  OlmRewardView result=new OlmRewardView();if(!VisibleCombatUi.effectivelyVisible(container))return result;result.visible=true;
  Collection<WidgetItem> legacy=container.getWidgetItems();if(legacy!=null){result.readable=true;for(WidgetItem item:legacy)if(item.getId()>0&&item.getQuantity()>0)result.items.add(new Slot(item.getId(),item.getQuantity(),item.getCanvasBounds()));}
  Set<Widget> seen=Collections.newSetFromMap(new IdentityHashMap<>());ArrayDeque<Widget> todo=new ArrayDeque<>();todo.add(container);
  while(!todo.isEmpty()&&seen.size()<1024){Widget w=todo.removeFirst();if(!seen.add(w))continue;result.scannedWidgets++;if(VisibleCombatUi.effectivelyVisible(w)&&w.getItemId()>0&&w.getItemQuantity()>0){result.readable=true;Slot slot=new Slot(w.getItemId(),w.getItemQuantity(),w.getBounds());if(result.items.stream().noneMatch(old->old.id==slot.id&&Objects.equals(old.bounds,slot.bounds)))result.items.add(slot);}
   for(Widget[] children:new Widget[][]{w.getChildren(),w.getDynamicChildren(),w.getStaticChildren(),w.getNestedChildren()})if(children!=null){result.readable=true;for(Widget child:children)if(child!=null)todo.add(child);}
  }return result;
 }
 Map<String,Object> diagnostics(){Map<String,Object> out=new LinkedHashMap<>();out.put("containerVisible",visible);out.put("layoutReadable",readable);out.put("widgetsScanned",scannedWidgets);out.put("remainingSlots",items.size());return out;}
}
