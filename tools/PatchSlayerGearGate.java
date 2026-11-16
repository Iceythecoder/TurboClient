import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Makes the restored Slayer binary wait for its available auto-profile gear. */
public final class PatchSlayerGearGate implements Opcodes {
 public static void main(String[] args)throws Exception{
  if(args.length!=2)throw new IllegalArgumentException("usage: input-jar output-class");
  byte[] original;
  try(ZipFile zip=new ZipFile(args[0])){ZipEntry entry=zip.getEntry("owl/nh/SlayerController.class");if(entry==null)throw new IOException("SlayerController.class missing");try(InputStream in=zip.getInputStream(entry)){original=in.readAllBytes();}}
  ClassNode type=new ClassNode();new ClassReader(original).accept(type,0);boolean gate=false,filter=false;
  for(MethodNode method:type.methods){
   for(AbstractInsnNode node=method.instructions.getFirst();node!=null;node=node.getNext())if(node instanceof MethodInsnNode){
    MethodInsnNode call=(MethodInsnNode)node;
    if(call.owner.equals("owl/nh/SlayerController")&&call.name.equals("prepareAutomaticGear")&&call.desc.equals("()Z")){
     AbstractInsnNode next=call.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();
     if(next!=null&&next.getOpcode()==POP){
      AbstractInsnNode continuation=next.getNext();while(continuation!=null&&continuation.getOpcode()==NOP)continuation=continuation.getNext();
      LabelNode ready=new LabelNode();method.instructions.insertBefore(continuation,ready);
      InsnList check=new InsnList();check.add(new JumpInsnNode(IFNE,ready));check.add(new InsnNode(RETURN));method.instructions.insertBefore(next,check);method.instructions.remove(next);gate=true;
     }
     break;
    }
   }
   if(method.name.equals("prepareAutomaticGear")&&method.desc.equals("()Z")){
    for(AbstractInsnNode node=method.instructions.getFirst();node!=null;node=node.getNext())if(node instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)node;if(call.owner.equals("owl/nh/OlmGearSwitch")&&call.name.equals("keepAvailable")){filter=true;break;}}
    if(filter)continue;
    for(AbstractInsnNode node=method.instructions.getFirst();node!=null;node=node.getNext())if(node.getOpcode()==ASTORE&&((VarInsnNode)node).var==5){
     AbstractInsnNode prior=node.getPrevious();while(prior!=null&&prior.getOpcode()<0)prior=prior.getPrevious();
     if(prior instanceof MethodInsnNode&&((MethodInsnNode)prior).owner.equals("java/util/ArrayList")){
      InsnList add=new InsnList();add.add(new VarInsnNode(ALOAD,5));add.add(new VarInsnNode(ALOAD,3));add.add(new MethodInsnNode(INVOKESTATIC,"owl/nh/OlmGearSwitch","keepAvailable","(Ljava/util/List;Ljava/util/Set;)V",false));method.instructions.insert(node,add);filter=true;break;
     }
    }
   }
  }
  if(!gate||!filter)throw new IllegalStateException("patch anchors not found: gate="+gate+", filter="+filter);
  // This patch adds a new conditional-return basic block. Recomputing only
  // max stack/locals leaves the original StackMapTable one block short and
  // Java rejects pulseImpl at load time. Rebuild both frames and maxima.
  ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);type.accept(writer);Files.write(Paths.get(args[1]),writer.toByteArray());System.out.println("Patched Slayer gear gate, available-item filter, and verifier frames.");
 }
}
