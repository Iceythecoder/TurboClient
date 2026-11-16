import java.io.IOException;
import java.util.*;
import javax.tools.*;
/** Handles the sandbox-specific javac ZipFS close failure after successful compilation. */
public final class CompileDriver {
 public static void main(String[] args) throws Exception {
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new IllegalStateException("Run this build with a JDK");
  StandardJavaFileManager files=compiler.getStandardFileManager(null,null,null);boolean success=false;
  try {success=Boolean.TRUE.equals(compiler.getTask(null,files,null,Arrays.asList("--release","11","-proc:none","-classpath",args[0],"-d",args[1]),null,files.getJavaFileObjectsFromStrings(Arrays.asList(args).subList(2,args.length))).call());}
  finally {try{files.close();}catch(IOException restrictedCleanup){if(!success)throw restrictedCleanup;System.out.println("Compilation passed; restricted archive cleanup handled.");}}
  if(!success)System.exit(1);
 }
}
