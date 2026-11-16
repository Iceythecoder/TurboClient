import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Adds style glove normalization to the restored live classes without rebuilding stale feature sources. */
public final class PatchStyleGlovesCall {
 public static void main(String[] args)throws Exception{
  if(args.length!=3)throw new IllegalArgumentException("usage: <jar> <class-entry> <output-class>");
  byte[] bytes;try(JarFile jar=new JarFile(args[0])){try(java.io.InputStream in=jar.getInputStream(jar.getJarEntry(args[1]))){bytes=in.readAllBytes();}}
  ClassNode type=new ClassNode();new ClassReader(bytes).accept(type,0);boolean changed=false;
  for(MethodNode method:type.methods){
   boolean target=(method.name.equals("normaliseRaidGear")&&method.desc.equals("(Lowl/nh/CombatCore$Style;Ljava/util/List;)V"))||(method.name.equals("prepareAutomaticGear")&&method.desc.equals("()Z"));
   if(!target)continue;
   for(AbstractInsnNode node=method.instructions.getFirst();node!=null;node=node.getNext()){
    if(node instanceof MethodInsnNode&&((MethodInsnNode)node).owner.equals("owl/nh/OlmGearSwitch")&&((MethodInsnNode)node).name.equals("recogniseStyleGloves")){target=false;break;}
   }
   if(!target)continue;
   for(AbstractInsnNode node=method.instructions.getFirst();node!=null;node=node.getNext())if(node.getOpcode()==Opcodes.ASTORE&&((VarInsnNode)node).var==4){
    InsnList add=new InsnList();add.add(new VarInsnNode(Opcodes.ALOAD,1));add.add(new VarInsnNode(Opcodes.ALOAD,2));add.add(new VarInsnNode(Opcodes.ALOAD,3));add.add(new VarInsnNode(Opcodes.ALOAD,4));add.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"owl/nh/OlmGearSwitch","recogniseStyleGloves","(Lowl/nh/CombatCore$Style;Ljava/util/List;Ljava/util/Set;Ljava/util/function/IntFunction;)Z",false));add.add(new InsnNode(Opcodes.POP));method.instructions.insert(node,add);changed=true;break;
   }
  }
  if(!changed)throw new IllegalStateException("No unpatched target method found in "+args[1]);ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);type.accept(writer);Path output=Paths.get(args[2]);Files.createDirectories(output.getParent());Files.write(output,writer.toByteArray());System.out.println("Patched style gloves into "+args[1]);
 }
}
