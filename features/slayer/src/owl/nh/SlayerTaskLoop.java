package owl.nh;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** In-memory state for the Nieve assignment loop. Chat text is parsed and then discarded. */
final class SlayerTaskLoop {
 enum Stage { IDLE, ASSIGNMENT, WAIT_ASSIGNMENT, GLIDER, WAIT_TELEPORTS, SEARCH, WAIT_SEARCH_PROMPT, WAIT_RESULTS, WAIT_RESULT_MENU, WAIT_DESTINATION, HUNT, HOME, WAIT_HOME }
 static final Pattern ASSIGNMENT=Pattern.compile("(?i).*you(?:(?:'|\\u2019)re| are) assigned to kill\\s+(.+?)\\s*;\\s*only\\s+([0-9,]+)\\s+more to go\\.?.*");
 static final Pattern CURRENT_ASSIGNMENT=Pattern.compile("(?i).*you(?:(?:'|\\u2019)re| are) still hunting\\s+(.+?)\\s*;\\s*you have\\s+([0-9,]+)\\s+(?:more\\s+)?to go\\.?.*");
 static final Pattern ASSIGNMENT_NAME=Pattern.compile("(?i).*(?:assigned to kill|assignment is(?: to)? kill|task is(?: to)? kill)\\s+(.+?)(?:[.;!]|$).*");
 static final class Task {final String display,target,search;final int remaining;Task(String display,String target,String search,int remaining){this.display=display;this.target=target;this.search=search;this.remaining=remaining;}}
 boolean active,selectedOnly;Stage stage=Stage.IDLE;Task task;int requestedAt=-1000,attempts,observedAt=-1;String homeOrigin="",pendingTarget;

 void start(){active=true;selectedOnly=false;stage=Stage.ASSIGNMENT;task=null;pendingTarget=null;requestedAt=-1000;attempts=0;observedAt=-1;homeOrigin="";}
 void startSelected(){start();selectedOnly=true;}
 void stop(){active=false;selectedOnly=false;stage=Stage.IDLE;task=null;pendingTarget=null;requestedAt=-1000;attempts=0;observedAt=-1;homeOrigin="";}
 boolean running(){return active;}
 void stage(Stage next,int tick){stage=next;requestedAt=tick;attempts=0;observedAt=-1;}
 void requested(int tick){requestedAt=tick;attempts++;}
 boolean due(int tick,int delay){return tick-requestedAt>=delay;}
 void resetObservation(){observedAt=-1;}
 boolean stable(boolean visible,int tick,int ticks){if(!visible){observedAt=-1;return false;}if(observedAt<0)observedAt=tick;return tick-observedAt>=ticks;}
 boolean accept(String raw){Task parsed=parse(raw);if(!active||parsed==null)return false;task=parsed;stage=Stage.GLIDER;requestedAt=-1000;attempts=0;return true;}
 boolean observeTarget(String raw){if(!active||raw==null)return false;String plain=raw.replaceAll("<[^>]*>","").replace('\u00a0',' ').trim();Matcher match=ASSIGNMENT_NAME.matcher(plain);if(!match.matches())return false;String display=clean(match.group(1));if(display.isEmpty())return false;pendingTarget=singular(display);return !pendingTarget.isEmpty();}
 boolean acceptObservedCount(int remaining){if(!active||pendingTarget==null||pendingTarget.isEmpty()||remaining<1||remaining>10000)return false;task=new Task(pendingTarget,pendingTarget,pendingTarget,remaining);pendingTarget=null;stage=Stage.GLIDER;requestedAt=-1000;attempts=0;return true;}
 void acceptKnown(String display,String target,int remaining,Stage next,int tick){if(!active||remaining<1||remaining>10000)throw new IllegalArgumentException("Invalid Slayer task count");String cleanTarget=singular(target),cleanDisplay=clean(display);task=new Task(cleanDisplay.isEmpty()?cleanTarget:cleanDisplay,cleanTarget,cleanTarget,remaining);pendingTarget=null;stage(next,tick);}

 static Task parse(String raw){
  if(raw==null)return null;String plain=raw.replaceAll("<[^>]*>","").replace('\u00a0',' ').trim();Matcher match=ASSIGNMENT.matcher(plain);if(!match.matches()){match=CURRENT_ASSIGNMENT.matcher(plain);if(!match.matches())return null;}
  int remaining;try{remaining=Integer.parseInt(match.group(2).replace(",",""));}catch(NumberFormatException bad){return null;}if(remaining<1||remaining>10000)return null;
  String display=clean(match.group(1));if(display.isEmpty())return null;String target=singular(display);return new Task(display,target,target,remaining);
 }
 static boolean noTask(String raw){if(raw==null)return false;String text=clean(raw).toLowerCase(Locale.ROOT);return text.contains("no slayer task")||text.contains("no slayer assignment")||text.contains("don't have a slayer task")||text.contains("do not have a slayer task")||text.contains("not currently assigned")||text.contains("need a slayer task")||text.contains("need an assignment");}
 static String singular(String value){
  String text=clean(value);String lower=text.toLowerCase(Locale.ROOT);
  if(lower.endsWith("ies")&&text.length()>3)return text.substring(0,text.length()-3)+"y";
  if(lower.endsWith("sses")||lower.endsWith("shes")||lower.endsWith("ches")||lower.endsWith("xes")||lower.endsWith("zes"))return text.substring(0,text.length()-2);
  if(lower.endsWith("s")&&!lower.endsWith("ss")&&text.length()>1)return text.substring(0,text.length()-1);
  return text;
 }
 static String clean(String value){return value==null?"":value.replaceAll("<[^>]*>","").trim().replaceAll("\\s+"," ");}
}
