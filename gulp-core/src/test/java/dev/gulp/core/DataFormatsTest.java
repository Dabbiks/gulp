package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonNull;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonValue;
import dev.gulp.core.i18n.MessageFormat;
import dev.gulp.core.i18n.PluralRules;
import dev.gulp.core.save.Tags;
import dev.gulp.core.util.Deflate;
import dev.gulp.core.util.Inflate;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The save file format, its compressor and the message formatter. */
class DataFormatsTest {

    private static byte[] sample(int kind, int size) {
        Random random = new Random(kind * 17L + size);
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = switch (kind) {
                case 0 -> (byte) random.nextInt(256);
                case 1 -> (byte) "save slot region chunk ".charAt(random.nextInt(5) + i % 17);
                default -> (byte) (i % 7 == 0 ? 3 : 0);
            };
        }
        return data;
    }

    private static byte[] jdkInflate(byte[] packed) throws DataFormatException {
        Inflater inflater = new Inflater(true);
        inflater.setInput(packed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!inflater.finished()) {
            int n = inflater.inflate(buffer);
            if (n == 0 && inflater.needsInput()) {
                break;
            }
            out.write(buffer, 0, n);
        }
        inflater.end();
        return out.toByteArray();
    }

    @ParameterizedTest
    @CsvSource({"0,0", "0,1", "0,70000", "1,100", "1,200000", "2,5", "2,300000"})
    void deflateRoundTripsThroughBothInflaters(int kind, int size) throws DataFormatException {
        byte[] data = sample(kind, size);
        byte[] packed = Deflate.raw(data);
        assertThat(Inflate.raw(packed)).isEqualTo(data);
        assertThat(jdkInflate(packed)).isEqualTo(data);
        if (kind == 2 && size > 1000) {
            assertThat(packed.length).as("repetitive data compresses").isLessThan(size / 20);
        }
    }

    @Test
    void tagsKeepEveryValueKind() {
        JsonArray.Builder tiles = JsonArray.builder();
        for (int i = 0; i < 1024; i++) {
            tiles.add(i % 3 == 0 ? -i : (long) i * 100_000L);
        }
        JsonObject value = JsonObject.builder()
                .put("null", JsonNull.INSTANCE)
                .put("yes", true)
                .put("no", false)
                .put("small", 3)
                .put("negative", -12_345_678_901L)
                .put("max", Long.MAX_VALUE)
                .put("min", Long.MIN_VALUE)
                .put("half", 0.5)
                .put("text", "Zażółć gęślą jaźń 🎮")
                .put("empty", "")
                .put("mixed", JsonArray.builder().add(1).add("two").add(3.5).build())
                .put("short", JsonArray.builder().add(1).add(2).build())
                .put("tiles", tiles.build())
                .put("nested", JsonObject.builder().put("a", JsonObject.EMPTY).build())
                .build();
        byte[] file = Tags.write(value);
        assertThat(Tags.isTagFile(file)).isTrue();
        JsonValue again = Tags.read(file);
        assertThat(again).isEqualTo(value);
        assertThat(file.length).as("int arrays and compression").isLessThan(4500);
    }

    @Test
    void damagedFilesAreRejected() {
        byte[] file = Tags.write(JsonObject.builder().put("a", 1).build());
        assertThat(Tags.isTagFile(new byte[] {1, 2})).isFalse();
        assertThatThrownBy(() -> Tags.read("{\"a\":1}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a Gulp save");
        byte[] newer = file.clone();
        newer[4] = 9;
        assertThat(Tags.isTagFile(newer)).isFalse();
        byte[] cut = new byte[file.length - 3];
        System.arraycopy(file, 0, cut, 0, cut.length);
        assertThatThrownBy(() -> Tags.read(cut)).isInstanceOf(IllegalArgumentException.class);
        byte[] garbage = {'G', 'U', 'L', 'P', 1, 0x03, 0x00};
        assertThatThrownBy(() -> Tags.read(garbage)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "pl_pl, 1, one",
        "pl_pl, 3, few",
        "pl_pl, 22, few",
        "pl_pl, 12, many",
        "pl_pl, 25, many",
        "pl_pl, 1.5, other",
        "ru_ru, 21, one",
        "ru_ru, 11, many",
        "uk_ua, 4, few",
        "cs_cz, 3, few",
        "cs_cz, 7, other",
        "cs_cz, 1.5, many",
        "fr_fr, 0, one",
        "fr_fr, 1.5, one",
        "fr_fr, 2, other",
        "ja_jp, 1, other",
        "en_us, 1, one",
        "en_us, 0, other"
    })
    void pluralCategoriesFollowCldr(String locale, double number, String category) {
        assertThat(PluralRules.category(locale, number)).isEqualTo(category);
    }

    @Test
    void messagesFormatArgumentsPluralsAndSelects() {
        String coins = "{0, plural, one {# moneta} few {# monety} many {# monet} other {# monety}}";
        assertThat(MessageFormat.format(coins, "pl_pl", 1)).isEqualTo("1 moneta");
        assertThat(MessageFormat.format(coins, "pl_pl", 4)).isEqualTo("4 monety");
        assertThat(MessageFormat.format(coins, "pl_pl", 12_345)).isEqualTo("12\u00a0345 monet");
        assertThat(MessageFormat.format("{0, plural, =0 {none} one {one} other {# items}}", "en_us", 0))
                .isEqualTo("none");
        assertThat(MessageFormat.format("Hi {name}, {count, number}!", "en_us", Map.of("name", "Ola", "count", 1234.5)))
                .isEqualTo("Hi Ola, 1,234.5!");
        assertThat(MessageFormat.format("{0, select, male {he} female {she} other {they}}", "en", "female"))
                .isEqualTo("she");
        assertThat(MessageFormat.format("{0, select, male {he} other {they}}", "en", "x"))
                .isEqualTo("they");
        assertThat(MessageFormat.format("{0} and {1}", "en", "a")).isEqualTo("a and {1}");
        assertThat(MessageFormat.format("'{0}' is {0}", "en", 5)).contains("5");
        assertThat(MessageFormat.format("broken {0", "en", 1)).isEqualTo("broken {0");
        assertThat(MessageFormat.format("{0, date}", "pl_pl", 0L)).isEqualTo("01.01.1970");
    }

    @Test
    void numbersAndDatesUseTheLanguageConventions() {
        assertThat(MessageFormat.formatNumber(1234.5, "en_us")).isEqualTo("1,234.5");
        assertThat(MessageFormat.formatNumber(1234.5, "pl_pl")).isEqualTo("1234,5");
        assertThat(MessageFormat.formatNumber(1_234_567, "pl_pl")).isEqualTo("1\u00a0234\u00a0567");
        assertThat(MessageFormat.formatNumber(1234.567, "de_de")).isEqualTo("1.234,57");
        assertThat(MessageFormat.formatNumber(-0.001, "en")).isEqualTo("0");
        assertThat(MessageFormat.formatNumber(-3, "en")).isEqualTo("-3");
        assertThat(MessageFormat.formatNumber(Double.NaN, "en")).isEqualTo("NaN");
        long day = 1_758_758_400_000L; // 2025-09-25
        assertThat(MessageFormat.formatDate(day, "pl_pl")).isEqualTo("25.09.2025");
        assertThat(MessageFormat.formatDate(day, "en_us")).isEqualTo("9/25/2025");
        assertThat(MessageFormat.formatDate(day, "en_gb")).isEqualTo("25/09/2025");
        assertThat(MessageFormat.formatDate(day, "fr_fr")).isEqualTo("25/09/2025");
        assertThat(MessageFormat.formatDate(day, "ja_jp")).isEqualTo("2025-09-25");
        assertThat(MessageFormat.formatDate(-86_400_000L, "ja")).isEqualTo("1969-12-31");
    }
}
