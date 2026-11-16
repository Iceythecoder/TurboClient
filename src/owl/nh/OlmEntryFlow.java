package owl.nh;

/** Gate -> finish visible dialogue -> entry attempt only when needed -> finish dialogue -> crossing. */
final class OlmEntryFlow {
 enum Stage { FIRST_GATE, WAIT_BEGIN, WAIT_DISMISS, SECOND_GATE, WAIT_INSIDE, INSIDE }
 enum Action { WAIT, CLICK_GATE, CLICK_BEGIN, CONTINUE, CHOOSE_OPTION }
 private Stage stage=Stage.FIRST_GATE;private int at=-1000,gateRequests,beginRequests,lastDialogue=-1000,clearSince=-1;
 void reset(){stage=Stage.FIRST_GATE;at=lastDialogue=-1000;gateRequests=beginRequests=0;clearSince=-1;}Stage stage(){return stage;}
 boolean keyboardConfirmation(){return beginRequests>0;}
 String attempts(){return "gate requests "+gateRequests+", confirmation requests "+beginRequests;}
 boolean insideEvidence(boolean recordedInside,boolean crossed){return stage==Stage.INSIDE||crossed||stage==Stage.FIRST_GATE&&recordedInside;}
 void confirmInside(){stage=Stage.INSIDE;}
 static boolean crossedFromOutside(OlmSpecials.Cell player,OlmSpecials.Cell gate,OlmSpecials.Cell outside){if(player==null||gate==null||outside==null||player.plane!=gate.plane||outside.plane!=gate.plane)return false;int dx=outside.x-gate.x,dy=outside.y-gate.y;if(dx==0&&dy==0)return false;return Math.abs(dx)>Math.abs(dy)?(player.x-gate.x)*Integer.signum(dx)<=-1:(player.y-gate.y)*Integer.signum(dy)<=-1;}
 boolean insideDialogueDue(int cycle){return stage==Stage.INSIDE&&cycle-lastDialogue>=15;}
 static boolean pastGate(OlmSpecials.Cell player,OlmSpecials.Cell gate,OlmSpecials.Cell boss){if(player==null||gate==null||boss==null||player.plane!=gate.plane||player.plane!=boss.plane)return false;int dx=boss.x-gate.x,dy=boss.y-gate.y;if(Math.max(Math.abs(dx),Math.abs(dy))<2)return false;return Math.abs(dx)>Math.abs(dy)?(player.x-gate.x)*Integer.signum(dx)>=1:(player.y-gate.y)*Integer.signum(dy)>=1;}
 Action next(int cycle,boolean gate,boolean begin,boolean otherDialogue,boolean inside){return next(cycle,gate,begin,false,otherDialogue,inside);}
 Action next(int cycle,boolean gate,boolean begin,boolean canContinue,boolean otherDialogue,boolean inside){
  if(stage==Stage.INSIDE)return Action.WAIT;
  if(otherDialogue||canContinue||begin)clearSince=-1;else if(clearSince<0)clearSince=cycle;
  if(inside&&!otherDialogue&&!canContinue&&!begin){stage=Stage.INSIDE;return Action.WAIT;}
  if(canContinue)return cycle-lastDialogue>=15?Action.CONTINUE:Action.WAIT;
  if(begin)return cycle-lastDialogue>=(beginRequests>0?35:1)?Action.CLICK_BEGIN:Action.WAIT;
  if(otherDialogue&&stage!=Stage.FIRST_GATE&&cycle-lastDialogue>=15)return Action.CHOOSE_OPTION;
  if((stage==Stage.WAIT_DISMISS||stage==Stage.WAIT_BEGIN&&lastDialogue>=0)&&!otherDialogue&&clearSince>=0&&cycle-clearSince>=35)stage=Stage.SECOND_GATE;
  if(stage==Stage.WAIT_BEGIN&&!otherDialogue&&cycle-at>=100)stage=Stage.FIRST_GATE;
  if(stage==Stage.WAIT_INSIDE&&!otherDialogue&&cycle-at>=60)stage=Stage.SECOND_GATE;
  if(otherDialogue)return Action.WAIT;
  return gate&&(stage==Stage.FIRST_GATE||stage==Stage.SECOND_GATE)?Action.CLICK_GATE:Action.WAIT;
 }
 void sentGate(int cycle){stage=stage==Stage.SECOND_GATE?Stage.WAIT_INSIDE:Stage.WAIT_BEGIN;at=cycle;gateRequests++;}
 void sentBegin(int cycle){if(stage!=Stage.WAIT_INSIDE)stage=Stage.WAIT_DISMISS;at=lastDialogue=cycle;beginRequests++;}
 void sentContinue(int cycle){at=lastDialogue=cycle;}
 void sentOption(int cycle){sentBegin(cycle);}
 static String clean(String text){return text==null?"":text.replaceAll("<[^>]*>","").replace('\u00a0',' ').trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+"," ");}
 static int entryOption(java.util.List<String> options){int begin=beginOption(options);if(begin>0)return begin;for(int i=0;i<options.size();i++){String text=clean(options.get(i));if(!text.isEmpty()&&!text.matches("^(?:[1-9][.):] )?(no|leave|exit|cancel|abandon|restart)\\b.*"))return i+1;}return -1;}
 static int beginOption(java.util.List<String> options){int found=-1;for(int i=0;i<options.size();i++)if(beginText(options.get(i))){if(found!=-1)return -1;found=i+1;}return found;}
 static boolean beginText(String text){String clean=clean(text).replaceFirst("^[1-9][.):]\\s*","").replaceAll("[.!]+$","").trim();return clean.equals("begin the raid")||clean.equals("begin raid");}
}
