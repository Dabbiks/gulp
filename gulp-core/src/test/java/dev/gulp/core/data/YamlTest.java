package dev.gulp.core.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.gulp.api.data.Json;
import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonNull;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonParseException;
import dev.gulp.api.data.JsonString;
import dev.gulp.api.data.JsonValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class YamlTest {

    @Test
    void parsesMappingsSequencesAndScalars() {
        String yaml = """
                # Game settings
                title: Coin Hunter   # trailing comment
                lives: 3
                speed: 7.5
                ratio: -.5
                big: +12
                debug: false
                missing: ~
                empty:
                quoted: "a: b # not a comment"
                single: 'it''s'
                hash: color#1
                player:
                  name: Mikołaj
                  inventory:
                    - sword
                    - shield
                  stats:
                    hp: 20
                levels:
                - name: first
                  size: 10
                - name: second
                  size: 20
                matrix:
                  - - 1
                    - 2
                  - [3, 4]
                flow: {x: 1, y: "two", z: [a, b]}
                """;

        JsonObject root = YamlReader.parse(yaml).asObject();

        assertThat(root.getOrThrow("title").asString()).isEqualTo("Coin Hunter");
        assertThat(root.getOrThrow("lives").asInt()).isEqualTo(3);
        assertThat(root.getOrThrow("speed").asDouble()).isEqualTo(7.5);
        assertThat(root.getOrThrow("ratio").asDouble()).isEqualTo(-0.5);
        assertThat(root.getOrThrow("big").asInt()).isEqualTo(12);
        assertThat(root.getOrThrow("debug").asBoolean()).isFalse();
        assertThat(root.getOrThrow("missing")).isEqualTo(JsonNull.INSTANCE);
        assertThat(root.getOrThrow("empty")).isEqualTo(JsonNull.INSTANCE);
        assertThat(root.getOrThrow("quoted").asString()).isEqualTo("a: b # not a comment");
        assertThat(root.getOrThrow("single").asString()).isEqualTo("it's");
        assertThat(root.getOrThrow("hash").asString()).isEqualTo("color#1");
        JsonObject player = root.getOrThrow("player").asObject();
        assertThat(player.getOrThrow("name").asString()).isEqualTo("Mikołaj");
        assertThat(player.getOrThrow("inventory").asArray().values())
                .containsExactly(new JsonString("sword"), new JsonString("shield"));
        assertThat(player.getOrThrow("stats").asObject().getOrThrow("hp").asInt())
                .isEqualTo(20);
        JsonArray levels = root.getOrThrow("levels").asArray();
        assertThat(levels.size()).isEqualTo(2);
        assertThat(levels.get(1).asObject().getOrThrow("size").asInt()).isEqualTo(20);
        assertThat(Json.write(root.getOrThrow("matrix"))).isEqualTo("[[1,2],[3,4]]");
        assertThat(Json.write(root.getOrThrow("flow"))).isEqualTo("{\"x\":1,\"y\":\"two\",\"z\":[\"a\",\"b\"]}");
    }

    @Test
    void parsesBlockScalars() {
        String yaml = """
                literal: |
                  line one
                    indented # kept

                  line three
                folded: >
                  one
                  two

                  three
                strip: |-
                  no newline
                keep: |+
                  kept

                after: done
                """;

        JsonObject root = YamlReader.parse(yaml).asObject();

        assertThat(root.getOrThrow("literal").asString()).isEqualTo("line one\n  indented # kept\n\nline three\n");
        assertThat(root.getOrThrow("folded").asString()).isEqualTo("one two\nthree\n");
        assertThat(root.getOrThrow("strip").asString()).isEqualTo("no newline");
        assertThat(root.getOrThrow("keep").asString()).isEqualTo("kept\n\n");
        assertThat(root.getOrThrow("after").asString()).isEqualTo("done");
    }

    @Test
    void parsesQuotedEscapesAndKeys() {
        JsonObject root = YamlReader.parse("\"key with: colon\": \"tab\\there \\u0041\\\\\"\n'x': [\"a, b\", 'c']\n")
                .asObject();

        assertThat(root.getOrThrow("key with: colon").asString()).isEqualTo("tab\there A\\");
        assertThat(Json.write(root.getOrThrow("x"))).isEqualTo("[\"a, b\",\"c\"]");
    }

    @Test
    void emptyDocumentIsEmptyObject() {
        assertThat(YamlReader.parse("")).isEqualTo(JsonObject.EMPTY);
        assertThat(YamlReader.parse("# only a comment\n\n")).isEqualTo(JsonObject.EMPTY);
        assertThat(YamlReader.parse("---\na: 1\n").asObject().getOrThrow("a").asInt())
                .isEqualTo(1);
        assertThat(YamlReader.parse("- a\n- b").asArray().size()).isEqualTo(2);
        assertThat(YamlReader.parse("plain")).isEqualTo(new JsonString("plain"));
    }

    @Test
    void resolvesScalarsLikeYaml12() {
        assertThat(YamlReader.resolvePlain("True").asBoolean()).isTrue();
        assertThat(YamlReader.resolvePlain("NULL")).isEqualTo(JsonNull.INSTANCE);
        assertThat(YamlReader.resolvePlain("1e3").asDouble()).isEqualTo(1000.0);
        assertThat(YamlReader.resolvePlain("12345678901234567890")).isEqualTo(new JsonString("12345678901234567890"));
        assertThat(YamlReader.resolvePlain("1.2.3")).isEqualTo(new JsonString("1.2.3"));
        assertThat(YamlReader.resolvePlain("-")).isEqualTo(new JsonString("-"));
        assertThat(YamlReader.resolvePlain("1e")).isEqualTo(new JsonString("1e"));
        assertThat(YamlReader.resolvePlain(".")).isEqualTo(new JsonString("."));
    }

    @Test
    void reportsErrorsWithPosition() {
        assertThatThrownBy(() -> YamlReader.parse("a: 1\n  b: 2\n"))
                .isInstanceOfSatisfying(
                        JsonParseException.class, e -> assertThat(e.line()).isEqualTo(2));
        for (String bad : List.of(
                "  indented: document",
                "a: 1\na: 2",
                "a: 1\njust text",
                "a: \"unterminated",
                "a: [1, 2",
                "a: {x: 1",
                "a: {x 1}",
                "a: \"bad \\q\"",
                "a: \"\\u12\"",
                "a: |x\n  text",
                "a:\n\t- tab",
                "- a\nb: c",
                ": value")) {
            assertThat(catchThrowable(() -> YamlReader.parse(bad)))
                    .as("'%s'", bad)
                    .isInstanceOf(JsonParseException.class);
        }
    }

    @Test
    void writerRoundTrips() {
        JsonObject original = JsonObject.builder()
                .put("title", "Coin Hunter")
                .put("text", "needs: quotes # here")
                .put("number_like", "42")
                .put("bool_like", "true")
                .put("empty", "")
                .put("multi", "a\nb\t\"c\"\\\u0001")
                .put("spaces", " padded ")
                .put("polish", "zażółć gęślą jaźń")
                .put("n", 3)
                .put("x", 1.5)
                .put("flag", true)
                .put("none", JsonNull.INSTANCE)
                .put("list", JsonArray.builder().add("a").add(1).build())
                .put(
                        "objects",
                        JsonArray.builder()
                                .add(JsonObject.builder().put("k", "v").build())
                                .add(JsonArray.builder().add(2).build())
                                .build())
                .put(
                        "nested",
                        JsonObject.builder()
                                .put(
                                        "deep",
                                        JsonObject.builder().put("value", 7).build())
                                .build())
                .put("empty_list", JsonArray.EMPTY)
                .put("empty_map", JsonObject.EMPTY)
                .put("weird key: #", "v")
                .build();

        String yaml = YamlWriter.write(original);

        assertThat(yaml).contains("title: Coin Hunter\n").contains("text: \"needs: quotes # here\"\n");
        assertThat(YamlReader.parse(yaml)).isEqualTo(original);
        assertThat(YamlWriter.write(JsonArray.builder().add(1).build())).isEqualTo("- 1\n");
        assertThat(YamlWriter.write(new JsonString("x"))).isEqualTo("x\n");
        assertThat(YamlReader.parse(YamlWriter.write(JsonObject.EMPTY))).isEqualTo(JsonObject.EMPTY);
    }

    @Test
    void writerQuotesAmbiguousStrings() {
        for (String text : List.of("null", "123", "-5", "1.5", "~", "a: b", "#x", "- item", "x ", "[a]")) {
            assertThat(YamlWriter.isPlainSafe(text)).as(text).isFalse();
            JsonValue parsed = YamlReader.parse(
                            YamlWriter.write(JsonObject.builder().put("v", text).build()))
                    .asObject()
                    .getOrThrow("v");
            assertThat(parsed.asString()).isEqualTo(text);
        }
        assertThat(YamlWriter.isPlainSafe("Coin Hunter (v2)")).isTrue();
    }
}
