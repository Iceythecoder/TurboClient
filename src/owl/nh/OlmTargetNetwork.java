package owl.nh;
import java.util.*;

/** Small supervised target network. Never trains on its own guesses as labels. */
final class OlmTargetNetwork {
 private final double[][] w=new double[12][8],v=new double[3][12];
 private final double[] b=new double[12],c=new double[3];
 private int examples,corrections,onlineUpdates;private double rewardBaseline;
 OlmTargetNetwork(){Random r=new Random(51);for(double[] row:w)for(int i=0;i<row.length;i++)row[i]=r.nextGaussian()*.15;for(double[] row:v)for(int i=0;i<row.length;i++)row[i]=r.nextGaussian()*.15;}
 static double[] encode(OlmStrategy.View s){return new double[]{Math.min(150,s.hpPercent)/100.0,Math.min(150,s.prayerPercent)/100.0,s.magicAlive?1:0,s.meleeAlive?1:0,s.headAlive?1:0,s.magicHp/100.0,s.meleeHp/100.0,s.phase==OlmStrategy.Phase.FINAL_HANDS?1:0};}
 private double[] hidden(double[] x){double[] h=new double[12];for(int j=0;j<12;j++){double z=b[j];for(int i=0;i<8;i++)z+=w[j][i]*x[i];h[j]=Math.tanh(z);}return h;}
 private double[] probabilities(double[] h){double[] p=c.clone();for(int k=0;k<3;k++)for(int j=0;j<12;j++)p[k]+=v[k][j]*h[j];double max=Math.max(p[0],Math.max(p[1],p[2])),sum=0;for(int k=0;k<3;k++){p[k]=Math.exp(p[k]-max);sum+=p[k];}for(int k=0;k<3;k++)p[k]/=sum;return p;}
 synchronized void teach(double[] x,int label){double[] h=hidden(x),p=probabilities(h),dh=new double[12];p[label]-=1;for(int j=0;j<12;j++){for(int k=0;k<3;k++)dh[j]+=p[k]*v[k][j];dh[j]*=1-h[j]*h[j];}for(int k=0;k<3;k++){c[k]-=.025*p[k];for(int j=0;j<12;j++)v[k][j]-=.025*p[k]*h[j];}for(int j=0;j<12;j++){b[j]-=.025*dh[j];for(int i=0;i<8;i++)w[j][i]-=.025*dh[j]*x[i];}}
 synchronized String predict(OlmStrategy.View s){if(examples+onlineUpdates<5)return null;double[] p=probabilities(hidden(encode(s)));boolean[] alive={s.magicAlive,s.meleeAlive,s.headAlive};int best=-1;for(int i=0;i<3;i++)if(alive[i]&&(best<0||p[i]>p[best]))best=i;return best<0||p[best]<.65?null:new String[]{"magic","melee","head"}[best];}
 synchronized void correct(double[] state,String target){int label=target.equals("magic")?0:target.equals("melee")?1:2;for(int i=0;i<20;i++)teach(state,label);corrections++;}
 synchronized void outcome(double[] x,int action,double reward){
  if(x==null||x.length!=8||action<0||action>2||!Double.isFinite(reward))throw new IllegalArgumentException("Invalid online outcome");
  double advantage=Math.max(-1,Math.min(1,reward-rewardBaseline));rewardBaseline=.95*rewardBaseline+.05*reward;
  double[] h=hidden(x),p=probabilities(h),dh=new double[12];p[action]-=1;
  for(int j=0;j<12;j++){for(int k=0;k<3;k++)dh[j]+=p[k]*v[k][j];dh[j]*=1-h[j]*h[j];}
  double rate=.005*advantage;
  for(int k=0;k<3;k++){c[k]-=rate*p[k];for(int j=0;j<12;j++)v[k][j]-=rate*p[k]*h[j];}
  for(int j=0;j<12;j++){b[j]-=rate*dh[j];for(int i=0;i<8;i++)w[j][i]-=rate*dh[j]*x[i];}onlineUpdates++;
 }
 static final class Checkpoint {String schema="olm-target-network-v1";double[][] w,v;double[] b,c;int examples,corrections,onlineUpdates;double rewardBaseline;}
 synchronized Checkpoint checkpoint(){Checkpoint p=new Checkpoint();p.w=copy(w);p.v=copy(v);p.b=b.clone();p.c=c.clone();p.examples=examples;p.corrections=corrections;p.onlineUpdates=onlineUpdates;p.rewardBaseline=rewardBaseline;return p;}
 static OlmTargetNetwork restore(Checkpoint p){
  if(p==null||!"olm-target-network-v1".equals(p.schema)||p.examples<0||p.examples>16000||p.corrections<0||p.onlineUpdates<0||!Double.isFinite(p.rewardBaseline))throw new IllegalArgumentException("Invalid neural checkpoint");
  matrix(p.w,12,8);matrix(p.v,3,12);vector(p.b,12);vector(p.c,3);OlmTargetNetwork n=new OlmTargetNetwork();
  for(int i=0;i<12;i++)System.arraycopy(p.w[i],0,n.w[i],0,8);for(int i=0;i<3;i++)System.arraycopy(p.v[i],0,n.v[i],0,12);System.arraycopy(p.b,0,n.b,0,12);System.arraycopy(p.c,0,n.c,0,3);n.examples=p.examples;n.corrections=p.corrections;n.onlineUpdates=p.onlineUpdates;n.rewardBaseline=p.rewardBaseline;return n;
 }
 private static double[][] copy(double[][] a){double[][] out=new double[a.length][];for(int i=0;i<a.length;i++)out[i]=a[i].clone();return out;}
 private static void matrix(double[][] a,int rows,int cols){if(a==null||a.length!=rows)throw new IllegalArgumentException("Invalid neural matrix");for(double[] row:a)vector(row,cols);}
 private static void vector(double[] a,int count){if(a==null||a.length!=count)throw new IllegalArgumentException("Invalid neural vector");for(double d:a)if(!Double.isFinite(d)||Math.abs(d)>1000)throw new IllegalArgumentException("Invalid neural weight");}
 synchronized int seed(ChambersDemonstration.Saved demo,OlmFighter.Profile profile){
  if(demo==null||!"orion-chambers-demonstration-v1".equals(demo.schema)||demo.actions==null||demo.actions.size()>16000)throw new IllegalArgumentException("Invalid demonstration for target learning");
  List<double[]> xs=new ArrayList<>();List<Integer> ys=new ArrayList<>();OlmPhaseTracker phase=new OlmPhaseTracker();
  for(ChambersDemonstration.Action a:demo.actions){if(a.before==null||a.after==null||!"attack".equalsIgnoreCase(a.option))continue;int label=profile.mageHands.contains(a.targetNpcId)?0:profile.meleeHands.contains(a.targetNpcId)?1:profile.heads.contains(a.targetNpcId)?2:-1;if(label<0)continue;
   OlmStrategy.View s=new OlmStrategy.View();s.hpPercent=a.before.hp;s.prayerPercent=a.before.prayer;
   for(int[] n:a.before.npcs){if(n==null||n.length<7)continue;boolean mage=profile.mageHands.contains(n[0]),melee=profile.meleeHands.contains(n[0]);if(mage||melee)phase.observe(mage,n[6],n[1],n[2],n[4]>0,n[4]==0);boolean living=n[4]!=0;if(mage){s.magicAlive=living;s.magicHp=n[5]>0&&n[4]>=0?n[4]*100/n[5]:-1;}if(melee){s.meleeAlive=living;s.meleeHp=n[5]>0&&n[4]>=0?n[4]*100/n[5]:-1;}if(profile.heads.contains(n[0]))s.headAlive=living;}
   s.phase=a.before.phase==1?OlmStrategy.Phase.FINAL_HANDS:phase.phase(s.magicAlive||s.meleeAlive);xs.add(encode(s));ys.add(label);
  }
  if(xs.size()<5)throw new IllegalArgumentException("Need five demonstrated attacks with outcome frames");
  for(int epoch=0;epoch<80;epoch++)for(int i=0;i<xs.size();i++)teach(xs.get(i),ys.get(i));examples=xs.size();return examples;
 }
 synchronized String status(){return "Neural targets: "+examples+" demonstration examples, "+corrections+" corrections, "+onlineUpdates+" reward updates. Approximate health-bar rewards; not causal proof or validated skill.";}
 synchronized int updateCount(){return onlineUpdates;}
}
