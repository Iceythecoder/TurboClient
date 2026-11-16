package owl.nh;
import java.awt.Rectangle;
import java.util.*;
import java.util.function.ToIntFunction;
import java.util.regex.*;
/** Pure visual name spans. No widget/game text is modified. */
final class KioskText {
 static String plain(String s){return s==null?"":s.replaceAll("<[^>]*>","").replace('\u00a0',' ');}
 static List<int[]> spans(String text,Collection<String> names){List<int[]> found=new ArrayList<>();for(String name:names){String n=plain(name);if(n.isBlank())continue;Matcher m=Pattern.compile("(?<![\\p{L}\\p{N}_])"+Pattern.quote(n)+"(?![\\p{L}\\p{N}_])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(text);while(m.find())found.add(new int[]{m.start(),m.end()});}return found;}
 static String title(String text,Collection<String> names){String masked=text;for(String name:names){String n=plain(name);if(!n.isBlank())masked=Pattern.compile(Pattern.quote(n),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(masked).replaceAll("\u2588\u2588\u2588\u2588\u2588");}return masked;}
 static String rawPrefix(String raw,int count){int chars=0,i=0;while(i<raw.length()){if(raw.charAt(i)=='<'){int end=raw.indexOf('>',i);if(end>=0){i=end+1;continue;}}if(chars>=count)break;chars++;i++;}return raw.substring(0,i);}
 static List<Rectangle> masks(String raw,Rectangle bounds,int xAlign,int yAlign,int height,ToIntFunction<String> width,Collection<String> names){List<Rectangle> masks=new ArrayList<>();if(raw==null||bounds==null||bounds.width<=0||bounds.height<=0)return masks;List<String> lines=new ArrayList<>();for(String segment:raw.split("(?i)<br>|\\r?\\n",-1)){String line="";for(String word:segment.split("(?<= )")){if(!line.isEmpty()&&width.applyAsInt(line+word)>bounds.width){lines.add(line.stripTrailing());line="";}line+=word;}lines.add(line);}int lineHeight=height>0?height:14;int top=bounds.y+(yAlign==1?Math.max(0,(bounds.height-lines.size()*lineHeight)/2):yAlign==2?Math.max(0,bounds.height-lines.size()*lineHeight):0);for(int row=0;row<lines.size();row++){String line=lines.get(row),text=plain(line);int full=width.applyAsInt(line),left=bounds.x+(xAlign==1?(bounds.width-full)/2:xAlign==2?bounds.width-full:0);for(int[] span:spans(text,names)){int before=width.applyAsInt(rawPrefix(line,span[0]));int length=width.applyAsInt(text.substring(span[0],span[1]));Rectangle r=new Rectangle(left+before-2,top+row*lineHeight-1,Math.max(4,length+4),lineHeight+3);Rectangle visible=r.intersection(bounds);if(visible.width>0&&visible.height>0)masks.add(visible);}}return masks;}
}
