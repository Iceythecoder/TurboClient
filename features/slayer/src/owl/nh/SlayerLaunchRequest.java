package owl.nh;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
final class SlayerLaunchRequest {
 static final String FILE="runtime/slayer-window.request";
 static boolean consume(Path root){Path p=root.resolve(FILE);try{if(!Files.isRegularFile(p)||Files.size(p)>128)return false;if(!Files.readString(p,StandardCharsets.UTF_8).trim().matches("SLAYER_V1(?: [a-f0-9]{32})?"))return false;Files.delete(p);return true;}catch(java.io.IOException error){throw new IllegalStateException("Cannot consume Slayer window request",error);}}
}

