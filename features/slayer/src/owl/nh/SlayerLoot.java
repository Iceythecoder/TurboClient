package owl.nh;

import java.awt.Color;
import net.runelite.api.TileItem;
import net.runelite.client.plugins.grounditems.config.ValueCalculationMode;
import net.runelite.client.plugins.lootfilters.DisplayConfig;
import net.runelite.client.plugins.lootfilters.FilterRule;
import net.runelite.client.plugins.lootfilters.LootFiltersPlugin;
import net.runelite.client.plugins.lootfilters.model.PluginTileItem;

/** Resolves Orion's live Loot Filters colour and its high-value fallback tiers. */
final class SlayerLoot {
 private static final Color FILTER_PURPLE=new Color(0xAA00FF),FILTER_MAGENTA=new Color(0xFF00FF),FILTER_ORANGE=new Color(0xAF6025),VALUE_ORANGE=new Color(0xFF9600),VALUE_PINK=new Color(0xFF66B2),TOP_RED=new Color(0xFF0000);
 static Color priceColor(int ge,int ha,ValueCalculationMode mode,int lowAt,Color low,int mediumAt,Color medium,int highAt,Color high,int insaneAt,Color insane){
  int value=mode==ValueCalculationMode.GE?ge:mode==ValueCalculationMode.HA?ha:Math.max(ge,ha);
  if(value>insaneAt)return insane;
  if(value>highAt)return high;
  if(value>mediumAt)return medium;
  if(value>lowAt)return low;
  return null;
 }
 static boolean matchesHighOrAbove(int ge,int ha,net.runelite.client.plugins.grounditems.GroundItemsConfig config){
  if(config==null)return false;Color actual=priceColor(ge,ha,config.valueCalculationMode(),config.lowValuePrice(),config.lowValueColor(),config.mediumValuePrice(),config.mediumValueColor(),config.highValuePrice(),config.highValueColor(),config.insaneValuePrice(),config.insaneValueColor());return config.highValueColor().equals(actual)||config.insaneValueColor().equals(actual);
 }
 static DisplayConfig resolvedFilterDisplay(LootFiltersPlugin plugin,TileItem item){
  if(plugin==null||item==null||item.getTile()==null)return null;
  try{
   PluginTileItem wrapped=new PluginTileItem(plugin,item.getTile(),item);DisplayConfig display=plugin.getDisplayIndex()==null?null:plugin.getDisplayIndex().get(wrapped);
   if(display==null&&plugin.getActiveFilter()!=null)display=plugin.getActiveFilter().findMatch(plugin,wrapped);
   return display==null||display.isHidden()?null:display;
  }catch(RuntimeException ignored){return null;}
 }
 static Color resolvedFilterColor(LootFiltersPlugin plugin,TileItem item){DisplayConfig display=resolvedFilterDisplay(plugin,item);return display==null?null:display.getTextColor();}
 static boolean matchesPurpleOrAbove(LootFiltersPlugin plugin,TileItem item){
  if(plugin==null||item==null||item.getTile()==null)return false;
  try{
   PluginTileItem wrapped=new PluginTileItem(plugin,item.getTile(),item);
   if(plugin.getConfig()!=null&&FilterRule.highlight(plugin.getConfig()).getCond().test(plugin,wrapped))return true;
  }catch(RuntimeException ignored){}
  DisplayConfig display=resolvedFilterDisplay(plugin,item);if(display==null)return false;Color actual=display.getTextColor(),configured=plugin.getConfig()==null?null:plugin.getConfig().highlightColor();return purpleOrHigher(actual,configured,FILTER_PURPLE,FILTER_MAGENTA,FILTER_ORANGE,VALUE_ORANGE,VALUE_PINK,TOP_RED)||purpleTier(actual);
 }
 static boolean same(Color first,Color second){return first!=null&&second!=null&&first.getRGB()==second.getRGB();}
 static boolean purpleOrHigher(Color actual,Color... accepted){if(actual==null||accepted==null)return false;for(Color color:accepted)if(same(actual,color))return true;return false;}
 /** Loot-filter packs are allowed to shade their tiers, so do not require one exact RGB value. */
 static boolean purpleTier(Color color){
  if(color==null)return false;float[] hsb=Color.RGBtoHSB(color.getRed(),color.getGreen(),color.getBlue(),null);float hue=hsb[0],saturation=hsb[1],brightness=hsb[2];
  if(brightness<0.35f||saturation<0.45f)return false;
  boolean purple=hue>=0.69f&&hue<=0.94f;
  boolean pinkOrRed=hue>=0.94f||hue<=0.04f;
  boolean orange=hue>=0.04f&&hue<=0.12f&&color.getRed()>color.getGreen()*1.25f;
  return purple||pinkOrRed||orange;
 }
}
