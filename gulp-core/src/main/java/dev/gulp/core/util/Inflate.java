package dev.gulp.core.util;

/**
 * DEFLATE decoder (RFC 1951) with the zlib (RFC 1950) and gzip (RFC 1952) wrappers, written for map data: plain Java
 * without {@code java.util.zip}, which TeaVM does not provide. Checksums are not verified.
 *
 * <pre>{@code
 * byte[] cells = Inflate.zlib(compressed);
 * }</pre>
 */
public final class Inflate {

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
    /** Order in which the code length code lengths are stored in a dynamic block header. */
    private static final int[] CODE_LENGTH_ORDER = {16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15};

    private final byte[] in;
    private int position;
    private int bitBuffer;
    private int bitCount;
    private final ByteSink out = new ByteSink(1024);

    private Inflate(byte[] in, int start) {
        this.in = in;
        this.position = start;
    }

    /**
     * Decodes a zlib stream: a two-byte header, DEFLATE data and an Adler-32 checksum.
     *
     * @param data the compressed bytes
     * @return the decompressed bytes
     * @throws IllegalArgumentException if the data is not a valid zlib stream
     */
    public static byte[] zlib(byte[] data) {
        if (data.length < 2
                || (data[0] & 0x0F) != 8
                || ((data[0] & 0xFF) * 256 + (data[1] & 0xFF)) % 31 != 0
                || (data[1] & 0x20) != 0) {
            throw new IllegalArgumentException("Not a zlib stream (or one with a preset dictionary)");
        }
        return new Inflate(data, 2).run();
    }

    /**
     * Decodes a gzip member: a header with optional fields, DEFLATE data and a CRC-32 trailer.
     *
     * @param data the compressed bytes
     * @return the decompressed bytes
     * @throws IllegalArgumentException if the data is not a valid gzip stream
     */
    public static byte[] gzip(byte[] data) {
        if (data.length < 18 || (data[0] & 0xFF) != 0x1F || (data[1] & 0xFF) != 0x8B || data[2] != 8) {
            throw new IllegalArgumentException("Not a gzip stream");
        }
        int flags = data[3] & 0xFF;
        int at = 10;
        if ((flags & 0x04) != 0) {
            at += 2 + ((data[at] & 0xFF) | (data[at + 1] & 0xFF) << 8);
        }
        if ((flags & 0x08) != 0) {
            while (data[at++] != 0) {
                // file name
            }
        }
        if ((flags & 0x10) != 0) {
            while (data[at++] != 0) {
                // comment
            }
        }
        if ((flags & 0x02) != 0) {
            at += 2;
        }
        return new Inflate(data, at).run();
    }

    /**
     * Decodes raw DEFLATE data without a wrapper.
     *
     * @param data the compressed bytes
     * @return the decompressed bytes
     * @throws IllegalArgumentException if the data is not valid DEFLATE data
     */
    public static byte[] raw(byte[] data) {
        return new Inflate(data, 0).run();
    }

    private byte[] run() {
        boolean last;
        do {
            last = bits(1) == 1;
            switch (bits(2)) {
                case 0 -> stored();
                case 1 -> codes(Huffman.FIXED_LITERALS, Huffman.FIXED_DISTANCES);
                case 2 -> dynamic();
                default -> throw new IllegalArgumentException("Invalid DEFLATE block type");
            }
        } while (!last);
        return out.toArray();
    }

    private int bits(int count) {
        while (bitCount < count) {
            if (position >= in.length) {
                throw new IllegalArgumentException("DEFLATE data ends early");
            }
            bitBuffer |= (in[position++] & 0xFF) << bitCount;
            bitCount += 8;
        }
        int value = bitBuffer & ((1 << count) - 1);
        bitBuffer >>>= count;
        bitCount -= count;
        return value;
    }

    private void stored() {
        bitBuffer = 0;
        bitCount = 0;
        if (position + 4 > in.length) {
            throw new IllegalArgumentException("DEFLATE data ends early");
        }
        int length = (in[position] & 0xFF) | (in[position + 1] & 0xFF) << 8;
        int check = (in[position + 2] & 0xFF) | (in[position + 3] & 0xFF) << 8;
        position += 4;
        if ((length ^ 0xFFFF) != check || position + length > in.length) {
            throw new IllegalArgumentException("Invalid stored DEFLATE block");
        }
        out.write(in, position, length);
        position += length;
    }

    private void dynamic() {
        int literalCount = bits(5) + 257;
        int distanceCount = bits(5) + 1;
        int codeLengthCount = bits(4) + 4;
        int[] codeLengths = new int[19];
        for (int i = 0; i < codeLengthCount; i++) {
            codeLengths[CODE_LENGTH_ORDER[i]] = bits(3);
        }
        Huffman lengthCode = new Huffman(codeLengths, 19);
        int[] lengths = new int[literalCount + distanceCount];
        int i = 0;
        while (i < lengths.length) {
            int symbol = lengthCode.decode(this);
            if (symbol < 16) {
                lengths[i++] = symbol;
                continue;
            }
            int value = 0;
            int repeat;
            if (symbol == 16) {
                if (i == 0) {
                    throw new IllegalArgumentException("DEFLATE length repeat without a previous length");
                }
                value = lengths[i - 1];
                repeat = 3 + bits(2);
            } else if (symbol == 17) {
                repeat = 3 + bits(3);
            } else {
                repeat = 11 + bits(7);
            }
            if (i + repeat > lengths.length) {
                throw new IllegalArgumentException("Too many DEFLATE code lengths");
            }
            while (repeat-- > 0) {
                lengths[i++] = value;
            }
        }
        int[] distanceLengths = new int[distanceCount];
        System.arraycopy(lengths, literalCount, distanceLengths, 0, distanceCount);
        codes(new Huffman(lengths, literalCount), new Huffman(distanceLengths, distanceCount));
    }

    private void codes(Huffman literals, Huffman distances) {
        while (true) {
            int symbol = literals.decode(this);
            if (symbol < 256) {
                out.write(symbol);
            } else if (symbol == 256) {
                return;
            } else {
                symbol -= 257;
                if (symbol >= LENGTH_BASE.length) {
                    throw new IllegalArgumentException("Invalid DEFLATE length code");
                }
                int length = LENGTH_BASE[symbol] + bits(LENGTH_EXTRA[symbol]);
                int code = distances.decode(this);
                if (code >= DISTANCE_BASE.length) {
                    throw new IllegalArgumentException("Invalid DEFLATE distance code");
                }
                int distance = DISTANCE_BASE[code] + bits(DISTANCE_EXTRA[code]);
                out.copyMatch(distance, length);
            }
        }
    }

    /**
     * Canonical Huffman code: the number of codes of each length and the symbols ordered by code. Decoding walks the
     * lengths bit by bit, comparing the code read so far with the first code of that length.
     */
    private static final class Huffman {
        static final Huffman FIXED_LITERALS = fixedLiterals();
        static final Huffman FIXED_DISTANCES = new Huffman(filled(30, 5), 30);

        private final int[] counts = new int[16];
        private final int[] symbols;

        Huffman(int[] lengths, int n) {
            symbols = new int[n];
            for (int s = 0; s < n; s++) {
                counts[lengths[s]]++;
            }
            counts[0] = 0;
            int[] offsets = new int[16];
            for (int len = 1; len < 16; len++) {
                offsets[len] = offsets[len - 1] + counts[len - 1];
            }
            for (int s = 0; s < n; s++) {
                if (lengths[s] != 0) {
                    symbols[offsets[lengths[s]]++] = s;
                }
            }
        }

        int decode(Inflate stream) {
            int code = 0;
            int first = 0;
            int index = 0;
            for (int len = 1; len < 16; len++) {
                code |= stream.bits(1);
                int count = counts[len];
                if (code - first < count) {
                    return symbols[index + code - first];
                }
                index += count;
                first = (first + count) << 1;
                code <<= 1;
            }
            throw new IllegalArgumentException("Invalid DEFLATE Huffman code");
        }

        private static Huffman fixedLiterals() {
            int[] lengths = new int[288];
            for (int s = 0; s < 288; s++) {
                lengths[s] = s < 144 ? 8 : s < 256 ? 9 : s < 280 ? 7 : 8;
            }
            return new Huffman(lengths, 288);
        }

        private static int[] filled(int n, int value) {
            int[] result = new int[n];
            java.util.Arrays.fill(result, value);
            return result;
        }
    }
}
