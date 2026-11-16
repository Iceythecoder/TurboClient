package owl.nh;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.widgets.Widget;
/** Native bank/preset discovery without guessed widget IDs. */
final class OlmBankPresetView {
 final List<Widget> widgets;final Widget manager,cox;
 OlmBankPresetView(List<Widget> widgets){this.widgets=widgets;Widget m=null,c=null;for(Widget w:widgets){if(cox(w.getName(),w.getText())&&c==null)c=w;if(manager(w.getName(),w.getText(),w.getActions())&&m==null)m=w;}manager=m;cox=c;}
 static String clean(String v){return v==null?"":v.replaceAll("<[^>]*>","").replaceAll("[^a-zA-Z0-9 ]","").trim().toLowerCase(Locale.ROOT).replaceAll(" +"," ");}
 static boolean cox(String name,String text){return clean(name).equals("cox")||clean(text).equals("cox");}
 static boolean manager(String name,String text,String[] actions){if(clean(name).equals("preset manager")||clean(text).equals("preset manager"))return true;if(actions!=null)for(String a:actions){String c=clean(a);if(c.equals("preset manager")||c.equals("presets")||c.equals("open presets")||c.equals("manage presets"))return true;}return false;}
 static OlmBankPresetView read(Client client){List<Widget> visible=new ArrayList<>();Set<Widget> seen=Collections.newSetFromMap(new IdentityHashMap<>());ArrayDeque<Widget> q=new ArrayDeque<>();add(q,client.getWidgetRoots());while(!q.isEmpty()&&seen.size()<10000){Widget w=q.remove();if(!seen.add(w))continue;if(VisibleCombatUi.effectivelyVisible(w))visible.add(w);add(q,w.getChildren());add(q,w.getStaticChildren());add(q,w.getDynamicChildren());add(q,w.getNestedChildren());}return new OlmBankPresetView(visible);}
 private static void add(ArrayDeque<Widget> q,Widget[] widgets){if(widgets!=null)for(Widget w:widgets)if(w!=null)q.add(w);}
 Widget close(){Widget anchor=cox!=null?cox:manager;if(anchor==null)return null;for(Widget w:widgets){if((w.getId()>>>16)!=(anchor.getId()>>>16))continue;String[] actions=w.getActions();if(actions!=null)for(String a:actions)if("close".equals(clean(a)))return w;}return null;}
 boolean open(){return cox!=null||manager!=null;}
 static boolean loadEntry(MenuEntry e,int widgetId){return e!=null&&clean(e.getOption()).equals("load")&&clean(e.getTarget()).equals("cox")&&e.getParam1()==widgetId;}
 static boolean hasLoadEntry(MenuEntry[] entries,int widgetId){if(entries!=null)for(MenuEntry entry:entries)if(loadEntry(entry,widgetId))return true;return false;}
}
