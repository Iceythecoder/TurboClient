import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Replaces one exact CONSTANT_Utf8 entry without altering any other class data. */
public final class PatchClassUtf8 {
    private PatchClassUtf8() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            throw new IllegalArgumentException("usage: input.class output.class oldUtf8 newUtf8");
        }
        byte[] source = Files.readAllBytes(Path.of(args[0]));
        byte[] oldValue = args[2].getBytes(StandardCharsets.UTF_8);
        byte[] newValue = args[3].getBytes(StandardCharsets.UTF_8);
        if (source.length < 10 || readU4(source, 0) != 0xCAFEBABEL) {
            throw new IllegalArgumentException("not a class file");
        }

        int constantPoolCount = readU2(source, 8);
        int offset = 10;
        int matchOffset = -1;
        int matchLength = -1;
        int matches = 0;
        for (int index = 1; index < constantPoolCount; index++) {
            int tag = source[offset++] & 0xff;
            switch (tag) {
                case 1 -> {
                    int length = readU2(source, offset);
                    offset += 2;
                    if (length == oldValue.length && equalsAt(source, offset, oldValue)) {
                        matchOffset = offset;
                        matchLength = length;
                        matches++;
                    }
                    offset += length;
                }
                case 3, 4, 9, 10, 11, 12, 17, 18 -> offset += 4;
                case 5, 6 -> { offset += 8; index++; }
                case 7, 8, 16, 19, 20 -> offset += 2;
                case 15 -> offset += 3;
                default -> throw new IllegalArgumentException("unsupported constant-pool tag " + tag);
            }
        }
        if (matches != 1) {
            throw new IllegalStateException("expected one exact UTF-8 match, found " + matches);
        }

        int lengthOffset = matchOffset - 2;
        int difference = newValue.length - matchLength;
        byte[] result = new byte[source.length + difference];
        System.arraycopy(source, 0, result, 0, lengthOffset);
        result[lengthOffset] = (byte) (newValue.length >>> 8);
        result[lengthOffset + 1] = (byte) newValue.length;
        System.arraycopy(newValue, 0, result, matchOffset, newValue.length);
        System.arraycopy(source, matchOffset + matchLength, result, matchOffset + newValue.length,
                source.length - matchOffset - matchLength);
        Files.write(Path.of(args[1]), result);
    }

    private static boolean equalsAt(byte[] source, int offset, byte[] expected) {
        for (int i = 0; i < expected.length; i++) {
            if (source[offset + i] != expected[i]) return false;
        }
        return true;
    }

    private static int readU2(byte[] value, int offset) {
        return ((value[offset] & 0xff) << 8) | (value[offset + 1] & 0xff);
    }

    private static long readU4(byte[] value, int offset) {
        return ((long) (value[offset] & 0xff) << 24)
                | ((long) (value[offset + 1] & 0xff) << 16)
                | ((long) (value[offset + 2] & 0xff) << 8)
                | (value[offset + 3] & 0xffL);
    }
}
