package dev.gulp.core.save;

import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonBoolean;
import dev.gulp.api.data.JsonNull;
import dev.gulp.api.data.JsonNumber;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonString;
import dev.gulp.api.data.JsonValue;
import dev.gulp.core.util.ByteSink;
import dev.gulp.core.util.Deflate;
import dev.gulp.core.util.Inflate;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tagged binary format of save files. Each value starts with a tag byte: null, false, true, integer (zigzag
 * varint), double (8 bytes), string (varint length and UTF-8), list, map (string keys), and integer array (a list of
 * integers stored as varints, used automatically for lists of 8 or more whole numbers, such as tile data). A file is
 * the magic {@code GULP}, a format version byte and the DEFLATE-compressed value.
 *
 * <pre>{@code
 * byte[] file = Tags.write(json);
 * JsonValue again = Tags.read(file);
 * }</pre>
 */
public final class Tags {

    /** The format version written now. */
    public static final int FORMAT = 1;

    private static final byte[] MAGIC = {'G', 'U', 'L', 'P'};
    private static final int NULL = 0;
    private static final int FALSE = 1;
    private static final int TRUE = 2;
    private static final int INTEGER = 3;
    private static final int DOUBLE = 4;
    private static final int STRING = 5;
    private static final int LIST = 6;
    private static final int MAP = 7;
    private static final int INT_ARRAY = 8;
    private static final int ARRAY_THRESHOLD = 8;

    private Tags() {}

    /**
     * Encodes and compresses a value into file bytes.
     *
     * @param value the value
     * @return the file
     */
    public static byte[] write(JsonValue value) {
        ByteSink raw = new ByteSink(1024);
        encode(raw, value);
        byte[] packed = Deflate.raw(raw.toArray());
        byte[] file = new byte[packed.length + 5];
        System.arraycopy(MAGIC, 0, file, 0, 4);
        file[4] = FORMAT;
        System.arraycopy(packed, 0, file, 5, packed.length);
        return file;
    }

    /**
     * Returns whether bytes start like a save file.
     *
     * @param file the bytes
     * @return {@code true} for the magic and a known version
     */
    public static boolean isTagFile(byte[] file) {
        return file.length >= 5
                && file[0] == MAGIC[0]
                && file[1] == MAGIC[1]
                && file[2] == MAGIC[2]
                && file[3] == MAGIC[3]
                && file[4] >= 1
                && file[4] <= FORMAT;
    }

    /**
     * Decompresses and decodes file bytes.
     *
     * @param file the file
     * @return the value
     * @throws IllegalArgumentException if the bytes are not a save file or are damaged
     */
    public static JsonValue read(byte[] file) {
        if (!isTagFile(file)) {
            throw new IllegalArgumentException("Not a Gulp save file");
        }
        byte[] packed = new byte[file.length - 5];
        System.arraycopy(file, 5, packed, 0, packed.length);
        byte[] raw;
        try {
            raw = Inflate.raw(packed);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Damaged save file: " + e.getMessage(), e);
        }
        Reader reader = new Reader(raw);
        JsonValue value = reader.value();
        if (reader.position != raw.length) {
            throw new IllegalArgumentException("Damaged save file: trailing data");
        }
        return value;
    }

    // ------------------------------------------------------------------ encoding

    private static void encode(ByteSink out, JsonValue value) {
        switch (value) {
            case JsonNull ignored -> out.write(NULL);
            case JsonBoolean bool -> out.write(bool.value() ? TRUE : FALSE);
            case JsonNumber number -> {
                if (number.isIntegral()) {
                    out.write(INTEGER);
                    varLong(out, zigzag(number.longValue()));
                } else {
                    out.write(DOUBLE);
                    long bits = Double.doubleToLongBits(number.doubleValue());
                    for (int i = 0; i < 8; i++) {
                        out.write((int) (bits >>> (i * 8)) & 0xFF);
                    }
                }
            }
            case JsonString text -> {
                out.write(STRING);
                string(out, text.value());
            }
            case JsonArray array -> {
                if (array.size() >= ARRAY_THRESHOLD && allIntegers(array)) {
                    out.write(INT_ARRAY);
                    varLong(out, array.size());
                    for (JsonValue element : array) {
                        varLong(out, zigzag(((JsonNumber) element).longValue()));
                    }
                } else {
                    out.write(LIST);
                    varLong(out, array.size());
                    for (JsonValue element : array) {
                        encode(out, element);
                    }
                }
            }
            case JsonObject object -> {
                out.write(MAP);
                varLong(out, object.size());
                for (String name : object.names()) {
                    string(out, name);
                    encode(out, object.getOrThrow(name));
                }
            }
        }
    }

    private static boolean allIntegers(JsonArray array) {
        for (JsonValue element : array) {
            if (!(element instanceof JsonNumber number) || !number.isIntegral()) {
                return false;
            }
        }
        return true;
    }

    private static long zigzag(long value) {
        return (value << 1) ^ (value >> 63);
    }

    private static void varLong(ByteSink out, long value) {
        long rest = value;
        while ((rest & ~0x7FL) != 0) {
            out.write((int) ((rest & 0x7F) | 0x80));
            rest >>>= 7;
        }
        out.write((int) rest);
    }

    private static void string(ByteSink out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        varLong(out, bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    // ------------------------------------------------------------------ decoding

    /** Reads values from decompressed bytes. */
    private static final class Reader {
        private final byte[] in;
        int position;

        Reader(byte[] in) {
            this.in = in;
        }

        JsonValue value() {
            int tag = next();
            return switch (tag) {
                case NULL -> JsonNull.INSTANCE;
                case FALSE -> JsonBoolean.of(false);
                case TRUE -> JsonBoolean.of(true);
                case INTEGER -> JsonNumber.of(unzigzag(varLong()));
                case DOUBLE -> {
                    long bits = 0;
                    for (int i = 0; i < 8; i++) {
                        bits |= (long) next() << (i * 8);
                    }
                    yield JsonNumber.of(Double.longBitsToDouble(bits));
                }
                case STRING -> new JsonString(string());
                case LIST -> {
                    int size = size();
                    List<JsonValue> values = new ArrayList<>(Math.min(size, 1024));
                    for (int i = 0; i < size; i++) {
                        values.add(value());
                    }
                    yield new JsonArray(values);
                }
                case MAP -> {
                    int size = size();
                    Map<String, JsonValue> members = new LinkedHashMap<>();
                    for (int i = 0; i < size; i++) {
                        String name = string();
                        members.put(name, value());
                    }
                    yield new JsonObject(members);
                }
                case INT_ARRAY -> {
                    int size = size();
                    List<JsonValue> values = new ArrayList<>(Math.min(size, 4096));
                    for (int i = 0; i < size; i++) {
                        values.add(JsonNumber.of(unzigzag(varLong())));
                    }
                    yield new JsonArray(values);
                }
                default -> throw new IllegalArgumentException("Damaged save file: unknown tag " + tag);
            };
        }

        private int next() {
            if (position >= in.length) {
                throw new IllegalArgumentException("Damaged save file: data ends early");
            }
            return in[position++] & 0xFF;
        }

        private long varLong() {
            long value = 0;
            int shift = 0;
            while (true) {
                int b = next();
                value |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return value;
                }
                shift += 7;
                if (shift > 63) {
                    throw new IllegalArgumentException("Damaged save file: varint too long");
                }
            }
        }

        private int size() {
            long size = varLong();
            if (size < 0 || size > in.length - position + 1L && size > 16_777_216L) {
                throw new IllegalArgumentException("Damaged save file: bad size " + size);
            }
            return (int) size;
        }

        private String string() {
            int length = size();
            if (position + length > in.length) {
                throw new IllegalArgumentException("Damaged save file: string ends early");
            }
            String text = new String(in, position, length, StandardCharsets.UTF_8);
            position += length;
            return text;
        }

        private static long unzigzag(long value) {
            return (value >>> 1) ^ -(value & 1);
        }
    }
}
