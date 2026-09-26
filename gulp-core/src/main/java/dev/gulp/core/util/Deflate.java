package dev.gulp.core.util;

/**
 * DEFLATE encoder (RFC 1951) for save files: LZ77 with hash chains over a 32 KiB window and the fixed Huffman codes,
 * in one block. Plain Java because TeaVM has no {@code java.util.zip}; {@link Inflate#raw} reads the output.
 *
 * <pre>{@code
 * byte[] packed = Deflate.raw(bytes);
 * byte[] again = Inflate.raw(packed);
 * }</pre>
 */
public final class Deflate {

    private static final int WINDOW = 1 << 15;
    private static final int WINDOW_MASK = WINDOW - 1;
    private static final int HASH_BITS = 15;
    private static final int HASH_SIZE = 1 << HASH_BITS;
    private static final int MIN_MATCH = 3;
    private static final int MAX_MATCH = 258;
    private static final int MAX_CHAIN = 64;

    private static final int[] LENGTH_BASE = {
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227,
        258
    };
    private static final int[] LENGTH_EXTRA = {
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
    };
    private static final int[] DISTANCE_BASE = {
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097,
        6145, 8193, 12289, 16385, 24577
    };
    private static final int[] DISTANCE_EXTRA = {
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13
    };

    private final ByteSink out;
    private int bitBuffer;
    private int bitCount;

    private Deflate(int capacity) {
        this.out = new ByteSink(Math.max(64, capacity));
    }

    /**
     * Compresses bytes.
     *
     * @param data the input
     * @return raw DEFLATE data
     */
    public static byte[] raw(byte[] data) {
        Deflate encoder = new Deflate(data.length / 2 + 64);
        encoder.encode(data);
        return encoder.out.toArray();
    }

    private void encode(byte[] data) {
        writeBits(1, 1); // last block
        writeBits(1, 2); // fixed Huffman codes
        int[] head = new int[HASH_SIZE];
        java.util.Arrays.fill(head, -1);
        int[] previous = new int[WINDOW];
        int position = 0;
        int length = data.length;
        while (position < length) {
            int bestLength = 0;
            int bestDistance = 0;
            if (position + MIN_MATCH <= length) {
                int hash = hash(data, position);
                int candidate = head[hash];
                int chain = MAX_CHAIN;
                int limit = Math.min(MAX_MATCH, length - position);
                while (candidate >= 0 && position - candidate <= WINDOW && chain-- > 0) {
                    if (data[candidate + bestLength] == data[position + bestLength]) {
                        int matched = 0;
                        while (matched < limit && data[candidate + matched] == data[position + matched]) {
                            matched++;
                        }
                        if (matched > bestLength) {
                            bestLength = matched;
                            bestDistance = position - candidate;
                            if (matched == limit) {
                                break;
                            }
                        }
                    }
                    int next = previous[candidate & WINDOW_MASK];
                    if (next >= candidate) {
                        break;
                    }
                    candidate = next;
                }
            }
            if (bestLength >= MIN_MATCH) {
                writeLength(bestLength);
                writeDistance(bestDistance);
                int end = position + bestLength;
                while (position < end) {
                    insert(data, position, head, previous);
                    position++;
                }
            } else {
                writeLiteral(data[position] & 0xFF);
                insert(data, position, head, previous);
                position++;
            }
        }
        writeLiteral(256);
        if (bitCount > 0) {
            out.write(bitBuffer & 0xFF);
        }
    }

    private static int hash(byte[] data, int position) {
        int value = (data[position] & 0xFF) << 16 | (data[position + 1] & 0xFF) << 8 | (data[position + 2] & 0xFF);
        return (value * 0x9E3779B1) >>> (32 - HASH_BITS);
    }

    private static void insert(byte[] data, int position, int[] head, int[] previous) {
        if (position + MIN_MATCH <= data.length) {
            int hash = hash(data, position);
            previous[position & WINDOW_MASK] = head[hash];
            head[hash] = position;
        }
    }

    private void writeLiteral(int symbol) {
        if (symbol < 144) {
            writeCode(0x30 + symbol, 8);
        } else if (symbol < 256) {
            writeCode(0x190 + symbol - 144, 9);
        } else if (symbol < 280) {
            writeCode(symbol - 256, 7);
        } else {
            writeCode(0xC0 + symbol - 280, 8);
        }
    }

    private void writeLength(int length) {
        int index = LENGTH_BASE.length - 1;
        while (LENGTH_BASE[index] > length) {
            index--;
        }
        writeLiteral(257 + index);
        writeBits(length - LENGTH_BASE[index], LENGTH_EXTRA[index]);
    }

    private void writeDistance(int distance) {
        int index = DISTANCE_BASE.length - 1;
        while (DISTANCE_BASE[index] > distance) {
            index--;
        }
        writeCode(index, 5);
        writeBits(distance - DISTANCE_BASE[index], DISTANCE_EXTRA[index]);
    }

    /** Writes a Huffman code, most significant bit first. */
    private void writeCode(int code, int length) {
        int reversed = 0;
        for (int i = 0; i < length; i++) {
            reversed = reversed << 1 | (code >>> i & 1);
        }
        writeBits(reversed, length);
    }

    /** Writes plain bits, least significant bit first. */
    private void writeBits(int value, int count) {
        if (count == 0) {
            return;
        }
        bitBuffer |= value << bitCount;
        bitCount += count;
        while (bitCount >= 8) {
            out.write(bitBuffer & 0xFF);
            bitBuffer >>>= 8;
            bitCount -= 8;
        }
    }
}
