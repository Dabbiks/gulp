package dev.gulp.core.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.gulp.api.data.Json;
import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonBoolean;
import dev.gulp.api.data.JsonNull;
import dev.gulp.api.data.JsonNumber;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonParseException;
import dev.gulp.api.data.JsonString;
import dev.gulp.api.data.JsonValue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void parsesAllValueTypes() {
        JsonValue value = Json.parse(
                "{\"a\": 1, \"b\": -2.5e1, \"c\": \"x\\ny\\u0041\", \"d\": [true, false, null], \"e\": {}, \"f\": []}");

        JsonObject object = value.asObject();
        assertThat(object.names()).containsExactly("a", "b", "c", "d", "e", "f");
        assertThat(object.getOrThrow("a").asInt()).isEqualTo(1);
        assertThat(object.getOrThrow("b").asDouble()).isEqualTo(-25.0);
        assertThat(object.getOrThrow("c").asString()).isEqualTo("x\nyA");
        assertThat(object.getOrThrow("d").asArray().values())
                .containsExactly(JsonBoolean.TRUE, JsonBoolean.FALSE, JsonNull.INSTANCE);
        assertThat(object.getOrThrow("e")).isEqualTo(JsonObject.EMPTY);
        assertThat(object.getOrThrow("f")).isEqualTo(JsonArray.EMPTY);
    }

    @Test
    void keepsLongPrecision() {
        JsonNumber big = Json.parse("9007199254740993").asNumber();

        assertThat(big.isIntegral()).isTrue();
        assertThat(big.longValue()).isEqualTo(9_007_199_254_740_993L);
        assertThat(Json.parse("12345678901234567890").asNumber().isIntegral()).isFalse();
    }

    @Test
    void handlesEscapes() {
        String text = "\"\\\"\\\\\\/\\b\\f\\n\\r\\t\"";

        assertThat(Json.parse(text).asString()).isEqualTo("\"\\/\b\f\n\r\t");
    }

    @Test
    void writesCompactAndPretty() {
        JsonObject object = JsonObject.builder()
                .put("name", "Slime \"blue\"")
                .put("hp", 20)
                .put("speed", 1.5)
                .put("alive", true)
                .put(
                        "tags",
                        JsonArray.builder().add("a").add(2).add(false).add(0.5).build())
                .put("none", JsonNull.INSTANCE)
                .put("control", "\u0001\t")
                .build();

        String compact = Json.write(object);
        assertThat(compact)
                .isEqualTo("{\"name\":\"Slime \\\"blue\\\"\",\"hp\":20,\"speed\":1.5,\"alive\":true,"
                        + "\"tags\":[\"a\",2,false,0.5],\"none\":null,\"control\":\"\\u0001\\t\"}");
        assertThat(Json.parse(compact)).isEqualTo(object);

        String pretty = Json.writePretty(object);
        assertThat(pretty).startsWith("{\n  \"name\": ").contains("\n  \"tags\": [\n    \"a\",");
        assertThat(Json.parse(pretty)).isEqualTo(object);
        assertThat(Json.writePretty(JsonObject.EMPTY)).isEqualTo("{}");
        assertThat(object.toString()).isEqualTo(compact);
    }

    @Test
    void reportsLineAndColumnOfErrors() {
        assertThatThrownBy(() -> Json.parse("{\n  \"a\": tru\n}"))
                .isInstanceOfSatisfying(JsonParseException.class, e -> {
                    assertThat(e.line()).isEqualTo(2);
                    assertThat(e.column()).isEqualTo(8);
                });
        for (String bad : List.of(
                "",
                "{",
                "[1,]x",
                "[1 2]",
                "{\"a\" 1}",
                "{a: 1}",
                "{\"a\":1 \"b\":2}",
                "\"unterminated",
                "\"bad \\q escape\"",
                "\"\\u12\"",
                "\"\\uZZZZ\"",
                "\"tab\there\"",
                "01x",
                "-",
                "1.",
                "1e",
                "nul",
                "@",
                "1 2")) {
            assertThat(catchThrowable(() -> Json.parse(bad))).as("'%s'", bad).isInstanceOf(JsonParseException.class);
        }
    }

    @Test
    void rejectsExcessiveNesting() {
        assertThatThrownBy(() -> Json.parse("[".repeat(600) + "]".repeat(600)))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("nested");
    }

    @Test
    void jsonValueConvertsPlainJava() {
        JsonValue value = JsonValue.of(Map.of("list", List.of(1, 2L, (short) 3, (byte) 4, 1.5f, "s", true)));

        assertThat(Json.write(value)).isEqualTo("{\"list\":[1,2,3,4,1.5,\"s\",true]}");
        assertThat(JsonValue.of(null)).isEqualTo(JsonNull.INSTANCE);
        assertThat(JsonValue.of(new JsonString("x"))).isEqualTo(new JsonString("x"));
        assertThatThrownBy(() -> JsonValue.of(new Object())).isInstanceOf(IllegalArgumentException.class);
    }
}
