import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Adds Seers ring (i) normalisation to the active Olm class while preserving every other method byte-for-byte semantically. */
public final class PatchOlmSeersRingCall implements Opcodes {
 public static void main(String[] args)throws Exception{
  if(args.length!=2)throw new IllegalArgumentException("usage: input-jar output-class");byte[] original;
  try(ZipFile zip=new ZipFile(args[0])){ZipEntry entry=zip.getEntry("owl/nh/OlmFighter.class");if(entry==null)throw new IOException("OlmFighter.class missing");try(InputStream in=zip.getInputStream(entry)){original=in.readAllBytes();}}
  ClassNode type=new ClassNode();new ClassReader(original).accept(type,0);boolean changed=false;
  for(MethodNode method:type.methods)if(method.name.equals("normaliseRaidGear")&&method.desc.equals("(Lowl/nh/CombatCore$Style;Ljava/util/List;)V")){
   for(AbstractInsnNode node:method.instructions)if(node instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)node;if(call.owner.equals("owl/nh/OlmGearSwitch")&&call.name.equals("recogniseSeersRingI")){Files.write(Paths.get(args[1]),original);System.out.println("Olm Seers ring call already present.");return;}}
   AbstractInsnNode anchor=null;for(AbstractInsnNode node:method.instructions)if(node instanceof VarInsnNode&&node.getOpcode()==ASTORE&&((VarInsnNode)node).var==4){anchor=node;break;}
   if(anchor==null)throw new IllegalStateException("Could not locate Olm gear-name function anchor");LabelNode skip=new LabelNode();InsnList add=new InsnList();
   add.add(new VarInsnNode(ALOAD,1));add.add(new FieldInsnNode(GETSTATIC,"owl/nh/CombatCore$Style","MAGIC","Lowl/nh/CombatCore$Style;"));add.add(new JumpInsnNode(IF_ACMPNE,skip));
   add.add(new VarInsnNode(ALOAD,2));add.add(new VarInsnNode(ALOAD,3));add.add(new VarInsnNode(ALOAD,4));add.add(new MethodInsnNode(INVOKESTATIC,"owl/nh/OlmGearSwitch","recogniseSeersRingI","(Ljava/util/List;Ljava/util/Set;Ljava/util/function/IntFunction;)Z",false));add.add(new InsnNode(POP));add.add(skip);
   method.instructions.insert(anchor,add);changed=true;
  }
  if(!changed)throw new IllegalStateException("normaliseRaidGear was not found");ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES);type.accept(writer);Files.write(Paths.get(args[1]),writer.toByteArray());System.out.println("Patched Chambers magic-ring normalisation.");
 }
}
