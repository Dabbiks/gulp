package dev.gulp.core.util;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Zstandard decoder (RFC 8878) for map data, in plain Java. Handles every block and literal type, FSE and Huffman
 * tables, repeat offsets and concatenated or skippable frames; dictionaries are not supported and checksums are not
 * verified.
 *
 * <pre>{@code
 * byte[] cells = Zstd.decompress(compressed);
 * }</pre>
 */
public final class Zstd {

    private static final int MAGIC = 0xFD2FB528;

    private static final int[] LITERAL_LENGTH_BASE = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 18, 20, 22, 24, 28, 32, 40, 48, 64, 128, 256, 512,
        1024, 2048, 4096, 8192, 16384, 32768, 65536
    };
    private static final int[] LITERAL_LENGTH_BITS = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 3, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        16
    };
    private static final int[] MATCH_LENGTH_BASE = {
        3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32,
        33, 34, 35, 37, 39, 41, 43, 47, 51, 59, 67, 83, 99, 131, 259, 515, 1027, 2051, 4099, 8195, 16387, 32771, 65539
    };
    private static final int[] MATCH_LENGTH_BITS = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2,
        2, 3, 3, 4, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
    };

    private static final Fse DEFAULT_LITERAL_LENGTHS = new Fse(6, new int[] {
        4, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 2, 1, 1, 1, 1, 1, -1, -1, -1, -1
    });
    private static final Fse DEFAULT_MATCH_LENGTHS = new Fse(6, new int[] {
        1, 4, 3, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 1, 1, 1, 1, 1, -1, -1, -1, -1, -1, -1, -1
    });
    private static final Fse DEFAULT_OFFSETS = new Fse(
            5, new int[] {1, 1, 1, 1, 1, 1, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, -1, -1, -1, -1, -1});

    private final byte[] in;
    private int position;
    private final ByteSink out = new ByteSink(1024);

    // State kept between the blocks of one frame.
    private final int[] repeats = new int[3];
    private @Nullable Huffman literalTable;
    private @Nullable Fse literalLengthTable;
    private @Nullable Fse offsetTable;
    private @Nullable Fse matchLengthTable;
    private byte[] literals = new byte[0];

    private Zstd(byte[] in) {
        this.in = in;
    }

    /**
     * Decodes all frames in {@code data}.
     *
     * @param data the compressed bytes
     * @return the decompressed bytes
     * @throws IllegalArgumentException if the data is not valid Zstandard data
     */
    public static byte[] decompress(byte[] data) {
        Zstd decoder = new Zstd(data);
        while (decoder.position < data.length) {
            decoder.frame();
        }
        return decoder.out.toArray();
    }

    private void frame() {
        int magic = readInt(4);
        if ((magic & 0xFFFFFFF0) == 0x184D2A50) {
            int skipped = readInt(4); // read first: "position += readInt(4)" would add to the old position
            position += skipped;
            return;
        }
        if (magic != MAGIC) {
            throw new IllegalArgumentException("Not a Zstandard frame");
        }
        int descriptor = readInt(1);
        int sizeFlag = descriptor >>> 6;
        boolean singleSegment = (descriptor & 0x20) != 0;
        boolean checksum = (descriptor & 0x04) != 0;
        int dictionaryFlag = descriptor & 3;
        if ((descriptor & 0x08) != 0) {
            throw new IllegalArgumentException("Reserved Zstandard frame header bit set");
        }
        if (!singleSegment) {
            position++; // window descriptor: the whole output stays in memory
        }
        int dictionarySize = dictionaryFlag == 3 ? 4 : dictionaryFlag;
        if (dictionarySize > 0 && readInt(dictionarySize) != 0) {
            throw new IllegalArgumentException("Zstandard dictionaries are not supported");
        }
        position += sizeFlag == 0 ? (singleSegment ? 1 : 0) : 1 << sizeFlag;
        repeats[0] = 1;
        repeats[1] = 4;
        repeats[2] = 8;
        literalTable = null;
        literalLengthTable = null;
        offsetTable = null;
        matchLengthTable = null;
        boolean last;
        do {
            int header = readInt(3);
            last = (header & 1) != 0;
            int type = (header >>> 1) & 3;
            int size = header >>> 3;
            switch (type) {
                case 0 -> {
                    require(size);
                    out.write(in, position, size);
                    position += size;
                }
                case 1 -> {
                    int value = readInt(1);
                    for (int i = 0; i < size; i++) {
                        out.write(value);
                    }
                }
                case 2 -> {
                    require(size);
                    int end = position + size;
                    compressedBlock(end);
                    position = end;
                }
                default -> throw new IllegalArgumentException("Reserved Zstandard block type");
            }
        } while (!last);
        if (checksum) {
            position += 4;
        }
    }

    private void require(int count) {
        if (position + count > in.length) {
            throw new IllegalArgumentException("Zstandard data ends early");
        }
    }

    private int readInt(int bytes) {
        require(bytes);
        int value = 0;
        for (int i = 0; i < bytes; i++) {
            value |= (in[position++] & 0xFF) << (8 * i);
        }
        return value;
    }

    // ------------------------------------------------------------------ literals

    private void compressedBlock(int end) {
        int literalCount = literalsSection();
        sequencesSection(end, literalCount);
    }

    /** Decodes the literals section into {@link #literals}; returns the number of literals. */
    private int literalsSection() {
        int first = readInt(1);
        int type = first & 3;
        int sizeFormat = (first >>> 2) & 3;
        if (type <= 1) {
            int size;
            switch (sizeFormat) {
                case 0, 2 -> size = first >>> 3;
                case 1 -> size = (first >>> 4) | readInt(1) << 4;
                default -> size = (first >>> 4) | readInt(2) << 4;
            }
            ensureLiterals(size);
            if (type == 0) {
                require(size);
                System.arraycopy(in, position, literals, 0, size);
                position += size;
            } else {
                byte value = (byte) readInt(1);
                Arrays.fill(literals, 0, size, value);
            }
            return size;
        }
        int streams = sizeFormat == 0 ? 1 : 4;
        int sizeBits = sizeFormat <= 1 ? 10 : sizeFormat == 2 ? 14 : 18;
        int headerBytes = sizeFormat <= 1 ? 2 : sizeFormat == 2 ? 3 : 4;
        long header = (first >>> 4) | ((readInt(headerBytes) & 0xFFFFFFFFL) << 4);
        int regenerated = (int) (header & ((1 << sizeBits) - 1));
        int compressed = (int) (header >>> sizeBits);
        require(compressed);
        int end = position + compressed;
        Huffman table;
        if (type == 2) {
            table = Huffman.read(this, end);
            literalTable = table;
        } else {
            table = literalTable;
            if (table == null) {
                throw new IllegalArgumentException("Treeless Zstandard literals without a previous table");
            }
        }
        ensureLiterals(regenerated);
        if (streams == 1) {
            table.decode(in, position, end, literals, 0, regenerated);
        } else {
            int size1 = readInt(2);
            int size2 = readInt(2);
            int size3 = readInt(2);
            int start1 = position;
            int start2 = start1 + size1;
            int start3 = start2 + size2;
            int start4 = start3 + size3;
            if (start4 > end) {
                throw new IllegalArgumentException("Invalid Zstandard literal stream sizes");
            }
            int each = (regenerated + 3) / 4;
            table.decode(in, start1, start2, literals, 0, each);
            table.decode(in, start2, start3, literals, each, each);
            table.decode(in, start3, start4, literals, 2 * each, each);
            table.decode(in, start4, end, literals, 3 * each, regenerated - 3 * each);
        }
        position = end;
        return regenerated;
    }

    private void ensureLiterals(int size) {
        if (literals.length < size) {
            literals = new byte[Math.max(size, literals.length * 2)];
        }
    }

    // ------------------------------------------------------------------ sequences

    private void sequencesSection(int end, int literalCount) {
        int count = position < end ? readInt(1) : 0;
        if (count >= 128) {
            count = count == 255 ? readInt(2) + 0x7F00 : ((count - 128) << 8) + readInt(1);
        }
        if (count == 0) {
            out.write(literals, 0, literalCount);
            return;
        }
        int modes = readInt(1);
        Fse literalLengths = table(modes >>> 6, DEFAULT_LITERAL_LENGTHS, literalLengthTable, 35, 9);
        literalLengthTable = literalLengths;
        Fse offsets = table((modes >>> 4) & 3, DEFAULT_OFFSETS, offsetTable, 31, 8);
        offsetTable = offsets;
        Fse matchLengths = table((modes >>> 2) & 3, DEFAULT_MATCH_LENGTHS, matchLengthTable, 52, 9);
        matchLengthTable = matchLengths;

        BackwardBits bits = new BackwardBits(in, position, end);
        int literalLengthState = literalLengths.initial(bits);
        int offsetState = offsets.initial(bits);
        int matchLengthState = matchLengths.initial(bits);
        int literalAt = 0;
        for (int s = 0; s < count; s++) {
            int offsetCode = offsets.symbol(offsetState);
            int matchCode = matchLengths.symbol(matchLengthState);
            int literalCode = literalLengths.symbol(literalLengthState);
            if (offsetCode > 31 || matchCode > 52 || literalCode > 35) {
                throw new IllegalArgumentException("Invalid Zstandard sequence code");
            }
            int offsetValue = (1 << offsetCode) + (int) bits.read(offsetCode);
            int matchLength = MATCH_LENGTH_BASE[matchCode] + (int) bits.read(MATCH_LENGTH_BITS[matchCode]);
            int literalLength = LITERAL_LENGTH_BASE[literalCode] + (int) bits.read(LITERAL_LENGTH_BITS[literalCode]);
            if (s + 1 < count) {
                literalLengthState = literalLengths.next(literalLengthState, bits);
                matchLengthState = matchLengths.next(matchLengthState, bits);
                offsetState = offsets.next(offsetState, bits);
            }
            int offset = resolveOffset(offsetValue, literalLength);
            if (literalAt + literalLength > literalCount) {
                throw new IllegalArgumentException("Zstandard sequence uses more literals than decoded");
            }
            out.write(literals, literalAt, literalLength);
            literalAt += literalLength;
            out.copyMatch(offset, matchLength);
        }
        out.write(literals, literalAt, literalCount - literalAt);
    }

    private int resolveOffset(int offsetValue, int literalLength) {
        if (offsetValue > 3) {
            int offset = offsetValue - 3;
            repeats[2] = repeats[1];
            repeats[1] = repeats[0];
            repeats[0] = offset;
            return offset;
        }
        // Repeat offsets; with no literals before the match the choices shift by one.
        int index = offsetValue - 1 + (literalLength == 0 ? 1 : 0);
        if (index == 0) {
            return repeats[0];
        }
        int offset = index == 3 ? repeats[0] - 1 : repeats[index];
        if (index > 1) {
            repeats[2] = repeats[1];
        }
        repeats[1] = repeats[0];
        repeats[0] = offset;
        return offset;
    }

    private Fse table(int mode, Fse predefined, @Nullable Fse previous, int maxSymbol, int maxLog) {
        return switch (mode) {
            case 0 -> predefined;
            case 1 -> Fse.rle(readInt(1));
            case 2 -> {
                ForwardBits bits = new ForwardBits(in, position);
                Fse table = Fse.read(bits, maxSymbol, maxLog);
                position = bits.alignedPosition();
                yield table;
            }
            default -> {
                if (previous == null) {
                    throw new IllegalArgumentException("Repeated Zstandard table without a previous one");
                }
                yield previous;
            }
        };
    }

    // ------------------------------------------------------------------ bit streams

    /** Little-endian bits read forwards, as in FSE table descriptions. */
    private static final class ForwardBits {
        private final byte[] data;
        private final int start;
        private long bit;

        ForwardBits(byte[] data, int start) {
            this.data = data;
            this.start = start;
        }

        int peek(int count) {
            int value = 0;
            for (int i = 0; i < count; i++) {
                long at = bit + i;
                int index = start + (int) (at >>> 3);
                if (index >= data.length) {
                    throw new IllegalArgumentException("Zstandard table description ends early");
                }
                value |= ((data[index] >>> (at & 7)) & 1) << i;
            }
            return value;
        }

        void skip(int count) {
            bit += count;
        }

        int read(int count) {
            int value = peek(count);
            bit += count;
            return value;
        }

        int alignedPosition() {
            return start + (int) ((bit + 7) >>> 3);
        }
    }

    /**
     * Bits read from the end of a stream towards its start, as in Huffman and sequence streams. The last byte holds a
     * marker bit above the data; bits before the start read as zero, and {@link #overflowed()} tells when that happened.
     */
    static final class BackwardBits {
        private final byte[] data;
        private final int start;
        private final int end;
        /** Number of unread bits; negative after reading past the start. */
        private int remaining;

        BackwardBits(byte[] data, int start, int end) {
            if (end <= start || data[end - 1] == 0) {
                throw new IllegalArgumentException("Invalid Zstandard bit stream");
            }
            this.data = data;
            this.start = start;
            this.end = end;
            remaining = (end - start - 1) * 8 + (31 - Integer.numberOfLeadingZeros(data[end - 1] & 0xFF));
        }

        long peek(int count) {
            if (count == 0) {
                return 0;
            }
            int low = remaining - count;
            if (low < 0) {
                int available = count + low;
                return available <= 0 ? 0 : bitsAt(0, available) << -low;
            }
            return bitsAt(low, count);
        }

        void skip(int count) {
            remaining -= count;
        }

        long read(int count) {
            long value = peek(count);
            remaining -= count;
            return value;
        }

        boolean overflowed() {
            return remaining < 0;
        }

        private long bitsAt(int low, int count) {
            int first = start + (low >>> 3);
            long word = 0;
            for (int i = 0; i < 8 && first + i < end; i++) {
                word |= (long) (data[first + i] & 0xFF) << (8 * i);
            }
            return (word >>> (low & 7)) & ((1L << count) - 1);
        }
    }

    // ------------------------------------------------------------------ FSE

    /** A finite state entropy decoding table: per state, a symbol, bits to read and the base of the next state. */
    private static final class Fse {
        final int accuracyLog;
        final int[] symbols;
        final int[] bitCounts;
        final int[] bases;

        private Fse(int accuracyLog, int[] symbols, int[] bitCounts, int[] bases) {
            this.accuracyLog = accuracyLog;
            this.symbols = symbols;
            this.bitCounts = bitCounts;
            this.bases = bases;
        }

        /** Builds the table from normalised counts; -1 marks a "less than one" probability. */
        Fse(int accuracyLog, int[] counts) {
            this(accuracyLog, new int[1 << accuracyLog], new int[1 << accuracyLog], new int[1 << accuracyLog]);
            int size = 1 << accuracyLog;
            int high = size - 1;
            int[] next = new int[counts.length];
            for (int s = 0; s < counts.length; s++) {
                if (counts[s] == -1) {
                    symbols[high--] = s;
                    next[s] = 1;
                } else {
                    next[s] = counts[s];
                }
            }
            int step = (size >>> 1) + (size >>> 3) + 3;
            int mask = size - 1;
            int at = 0;
            for (int s = 0; s < counts.length; s++) {
                for (int i = 0; i < counts[s]; i++) {
                    symbols[at] = s;
                    do {
                        at = (at + step) & mask;
                    } while (at > high);
                }
            }
            if (at != 0) {
                throw new IllegalArgumentException("Invalid Zstandard FSE distribution");
            }
            for (int state = 0; state < size; state++) {
                int s = symbols[state];
                int value = next[s]++;
                int bits = accuracyLog - (31 - Integer.numberOfLeadingZeros(value));
                bitCounts[state] = bits;
                bases[state] = (value << bits) - size;
            }
        }

        static Fse rle(int symbol) {
            return new Fse(0, new int[] {symbol}, new int[] {0}, new int[] {0});
        }

        static Fse read(ForwardBits bits, int maxSymbol, int maxLog) {
            int accuracyLog = bits.read(4) + 5;
            if (accuracyLog > maxLog) {
                throw new IllegalArgumentException("Zstandard FSE accuracy too high");
            }
            int[] counts = new int[maxSymbol + 1];
            int remaining = (1 << accuracyLog) + 1;
            int threshold = 1 << accuracyLog;
            int width = accuracyLog + 1;
            int symbol = 0;
            while (remaining > 1 && symbol <= maxSymbol) {
                int max = 2 * threshold - 1 - remaining;
                int value = bits.peek(width);
                int low = value & (threshold - 1);
                if (low < max) {
                    value = low;
                    bits.skip(width - 1);
                } else {
                    if (value >= threshold) {
                        value -= max;
                    }
                    bits.skip(width);
                }
                int count = value - 1;
                remaining -= count < 0 ? -count : count;
                counts[symbol++] = count;
                if (count == 0) {
                    int repeat;
                    do {
                        repeat = bits.read(2);
                        for (int i = 0; i < repeat && symbol <= maxSymbol; i++) {
                            counts[symbol++] = 0;
                        }
                    } while (repeat == 3);
                }
                while (remaining < threshold) {
                    width--;
                    threshold >>>= 1;
                }
            }
            if (remaining != 1) {
                throw new IllegalArgumentException("Invalid Zstandard FSE table description");
            }
            return new Fse(accuracyLog, Arrays.copyOf(counts, symbol));
        }

        int initial(BackwardBits bits) {
            return (int) bits.read(accuracyLog);
        }

        int symbol(int state) {
            return symbols[state];
        }

        int next(int state, BackwardBits bits) {
            return bases[state] + (int) bits.read(bitCounts[state]);
        }
    }

    // ------------------------------------------------------------------ Huffman

    /** Literal prefix code: a lookup table indexed by the next {@code maxBits} bits. */
    private static final class Huffman {
        final int maxBits;
        final byte[] symbols;
        final byte[] lengths;

        private Huffman(int maxBits, byte[] symbols, byte[] lengths) {
            this.maxBits = maxBits;
            this.symbols = symbols;
            this.lengths = lengths;
        }

        /** Reads a tree description at the decoder's position, advancing it. */
        static Huffman read(Zstd decoder, int limit) {
            int header = decoder.readInt(1);
            int[] weights = new int[256];
            int count;
            if (header < 128) {
                int end = decoder.position + header;
                if (end > limit) {
                    throw new IllegalArgumentException("Zstandard Huffman description too long");
                }
                ForwardBits description = new ForwardBits(decoder.in, decoder.position);
                Fse table = Fse.read(description, 255, 6);
                BackwardBits bits = new BackwardBits(decoder.in, description.alignedPosition(), end);
                int state1 = table.initial(bits);
                int state2 = table.initial(bits);
                count = 0;
                while (true) {
                    weights[count++] = table.symbol(state1);
                    state1 = table.next(state1, bits);
                    if (bits.overflowed()) {
                        weights[count++] = table.symbol(state2);
                        break;
                    }
                    weights[count++] = table.symbol(state2);
                    state2 = table.next(state2, bits);
                    if (bits.overflowed()) {
                        weights[count++] = table.symbol(state1);
                        break;
                    }
                    if (count > 253) {
                        throw new IllegalArgumentException("Too many Zstandard Huffman weights");
                    }
                }
                decoder.position = end;
            } else {
                count = header - 127;
                for (int i = 0; i < count; i += 2) {
                    int value = decoder.readInt(1);
                    weights[i] = value >>> 4;
                    weights[i + 1] = value & 15;
                }
            }
            return build(weights, count);
        }

        private static Huffman build(int[] weights, int count) {
            int total = 0;
            for (int i = 0; i < count; i++) {
                if (weights[i] > 0) {
                    total += 1 << (weights[i] - 1);
                }
            }
            if (total == 0) {
                throw new IllegalArgumentException("Empty Zstandard Huffman table");
            }
            int maxBits = 32 - Integer.numberOfLeadingZeros(total);
            int left = (1 << maxBits) - total;
            if (Integer.bitCount(left) != 1) {
                throw new IllegalArgumentException("Invalid Zstandard Huffman weights");
            }
            weights[count] = 32 - Integer.numberOfLeadingZeros(left);
            int symbolsTotal = count + 1;
            int size = 1 << maxBits;
            byte[] symbols = new byte[size];
            byte[] lengths = new byte[size];
            int at = 0;
            for (int weight = 1; weight <= maxBits; weight++) {
                for (int s = 0; s < symbolsTotal; s++) {
                    if (weights[s] == weight) {
                        int span = 1 << (weight - 1);
                        for (int i = 0; i < span; i++) {
                            symbols[at] = (byte) s;
                            lengths[at] = (byte) (maxBits + 1 - weight);
                            at++;
                        }
                    }
                }
            }
            if (at != size) {
                throw new IllegalArgumentException("Invalid Zstandard Huffman table");
            }
            return new Huffman(maxBits, symbols, lengths);
        }

        void decode(byte[] data, int start, int end, byte[] target, int offset, int count) {
            BackwardBits bits = new BackwardBits(data, start, end);
            for (int i = 0; i < count; i++) {
                int index = (int) bits.peek(maxBits);
                target[offset + i] = symbols[index];
                bits.skip(lengths[index]);
            }
            if (bits.overflowed()) {
                throw new IllegalArgumentException("Zstandard literal stream ends early");
            }
        }
    }
}
