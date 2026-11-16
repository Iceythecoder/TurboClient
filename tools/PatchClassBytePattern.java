import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

/** Replaces one unique, length-preserving byte pattern in a compiled class. */
public final class PatchClassBytePattern {
    private PatchClassBytePattern() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                "usage: PatchClassBytePattern <class> <from-hex> <to-hex> <expected-count>");
        }
        Path path = Path.of(args[0]);
        byte[] from = HexFormat.of().parseHex(args[1]);
        byte[] to = HexFormat.of().parseHex(args[2]);
        int expected = Integer.parseInt(args[3]);
        if (from.length != to.length) {
            throw new IllegalArgumentException("replacement must preserve class-file length");
        }

        byte[] bytes = Files.readAllBytes(path);
        int count = 0;
        for (int i = 0; i <= bytes.length - from.length; i++) {
            boolean match = true;
            for (int j = 0; j < from.length; j++) {
                if (bytes[i + j] != from[j]) {
                    match = false;
                    break;
                }
            }
            if (!match) continue;
            System.arraycopy(to, 0, bytes, i, to.length);
            count++;
            i += from.length - 1;
        }
        if (count != expected) {
            throw new IllegalStateException("matched " + count + " patterns; expected " + expected);
        }
        Files.write(path, bytes);
        System.out.println("Patched " + count + " pattern in " + path);
    }
}
