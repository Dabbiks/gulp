package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.core.util.ByteSink;
import dev.gulp.core.util.Inflate;
import dev.gulp.core.util.Zstd;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The decoders behind compressed Tiled layers, checked against the JDK and the reference Zstandard encoder. */
class CompressionTest {

    private static byte[] sample(int kind, int size) {
        Random random = new Random(kind * 31L + size);
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = switch (kind) {
                case 0 -> (byte) random.nextInt(256);
                case 1 -> (byte) "gulp tile map chunk ".charAt(random.nextInt(4) + i % 16);
                default -> (byte) (i % 4 == 0 ? 1 + (i / 64) % 5 : 0);
            };
        }
        return data;
    }

    private static byte[] deflate(byte[] data, int level, int strategy, boolean wrap) {
        Deflater deflater = new Deflater(level, !wrap);
        deflater.setStrategy(strategy);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    @Test
    void inflateMatchesTheJdkForEveryLevelAndStrategy() {
        for (int kind = 0; kind < 3; kind++) {
            for (int size : new int[] {0, 1, 100, 70_000}) {
                byte[] data = sample(kind, size);
                for (int level = 0; level <= 9; level += 3) {
                    for (int strategy :
                            new int[] {Deflater.DEFAULT_STRATEGY, Deflater.FILTERED, Deflater.HUFFMAN_ONLY}) {
                        assertThat(Inflate.zlib(deflate(data, level, strategy, true)))
                                .as("kind %d size %d level %d", kind, size, level)
                                .isEqualTo(data);
                        assertThat(Inflate.raw(deflate(data, level, strategy, false)))
                                .isEqualTo(data);
                    }
                }
            }
        }
    }

    @Test
    void gzipSkipsOptionalHeaderFields() throws IOException {
        byte[] data = sample(1, 5000);
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(plain)) {
            gzip.write(data);
        }
        assertThat(Inflate.gzip(plain.toByteArray())).isEqualTo(data);

        // Extra field, file name, comment and header CRC, as some tools write them.
        ByteArrayOutputStream full = new ByteArrayOutputStream();
        full.write(new byte[] {0x1F, (byte) 0x8B, 8, 0x1E, 0, 0, 0, 0, 0, 3});
        full.write(new byte[] {3, 0, 'a', 'b', 'c'});
        full.write("map.bin\0comment\0".getBytes(StandardCharsets.US_ASCII));
        full.write(new byte[] {0, 0});
        full.write(deflate(data, 6, Deflater.DEFAULT_STRATEGY, false));
        full.write(new byte[8]);
        assertThat(Inflate.gzip(full.toByteArray())).isEqualTo(data);
    }

    @Test
    void inflateRejectsBrokenData() {
        assertThatThrownBy(() -> Inflate.zlib(new byte[] {1, 2})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Inflate.gzip(new byte[20])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Inflate.raw(new byte[] {(byte) 0x07})).isInstanceOf(IllegalArgumentException.class);
        byte[] cut = deflate(sample(1, 1000), 9, Deflater.DEFAULT_STRATEGY, true);
        assertThatThrownBy(() -> Inflate.zlib(java.util.Arrays.copyOf(cut, cut.length / 2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Inflate.raw(new byte[] {0, 5, 0, 0, 0})).isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = CompressionTest.class.getResourceAsStream("/compression/" + name)) {
            assertThat(in).as(name).isNotNull();
            return in.readAllBytes();
        }
    }

    /** Files written by Python's {@code compression.zstd} at several levels; the expected CRC-32 comes from there. */
    @ParameterizedTest
    @CsvSource({
        "tiles, 57600, a90f03ae",
        "text, 400006, 4ebc6bb4",
        "random, 20000, 3e2e8304",
        "same, 200000, 98bd9e80",
        "tiny, 3, 352441c2",
        "empty, 0, 00000000"
    })
    void zstdDecodesReferenceFrames(String name, int length, String crc) throws IOException {
        for (int level : new int[] {-5, 1, 3, 19}) {
            byte[] data = Zstd.decompress(resource(name + "_" + level + ".zst"));
            CRC32 check = new CRC32();
            check.update(data);
            assertThat(data).as("%s at level %d", name, level).hasSize(length);
            assertThat(Long.toHexString(check.getValue())).isEqualTo(Long.parseLong(crc, 16) == 0 ? "0" : crc);
        }
    }

    @Test
    void zstdHandlesConcatenatedAndSkippableFrames() throws IOException {
        byte[] tiny = resource("tiny_3.zst");
        byte[] skippable = {0x50, 0x2A, 0x4D, 0x18, 2, 0, 0, 0, 9, 9};
        byte[] joined = new byte[tiny.length * 2 + skippable.length];
        System.arraycopy(tiny, 0, joined, 0, tiny.length);
        System.arraycopy(skippable, 0, joined, tiny.length, skippable.length);
        System.arraycopy(tiny, 0, joined, tiny.length + skippable.length, tiny.length);
        assertThat(new String(Zstd.decompress(joined), StandardCharsets.US_ASCII))
                .isEqualTo("abcabc");
    }

    @Test
    void zstdRejectsBrokenData() throws IOException {
        assertThatThrownBy(() -> Zstd.decompress(new byte[] {1, 2, 3, 4})).isInstanceOf(IllegalArgumentException.class);
        byte[] text = resource("text_3.zst");
        assertThatThrownBy(() -> Zstd.decompress(java.util.Arrays.copyOf(text, text.length / 2)))
                .isInstanceOf(IllegalArgumentException.class);
        byte[] dictionary = {0x28, (byte) 0xB5, 0x2F, (byte) 0xFD, 0x21, 5, 0};
        assertThatThrownBy(() -> Zstd.decompress(dictionary)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void byteSinkCopiesOverlappingMatches() {
        ByteSink sink = new ByteSink(1);
        sink.write('a');
        sink.write('b');
        sink.copyMatch(2, 5);
        assertThat(new String(sink.toArray(), StandardCharsets.US_ASCII)).isEqualTo("abababa");
        assertThat(sink.size()).isEqualTo(7);
        assertThatThrownBy(() -> sink.copyMatch(8, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
