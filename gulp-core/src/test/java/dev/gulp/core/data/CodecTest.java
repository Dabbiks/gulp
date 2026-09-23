package dev.gulp.core.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.data.Codec;
import dev.gulp.api.data.CodecException;
import dev.gulp.api.data.DataContainer;
import dev.gulp.api.data.DataType;
import dev.gulp.api.data.Json;
import dev.gulp.api.data.JsonNull;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonString;
import dev.gulp.api.data.JsonValue;
import dev.gulp.api.data.Serializable;
import dev.gulp.api.registry.Key;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class CodecTest {

    enum Rarity {
        COMMON,
        RARE
    }

    @Serializable
    record Stats(int hp, long xp, float speed, double luck, boolean alive) {}

    @Serializable
    record Upgrade(
            String name,
            Integer level,
            Key id,
            Rarity rarity,
            List<String> tags,
            Map<String, Integer> costs,
            Stats stats,
            JsonValue extra,
            @Nullable String icon,
            Boolean boxedFlag,
            Long boxedLong,
            Float boxedFloat,
            Double boxedDouble) {}

    private static Upgrade sample(@Nullable String icon) {
        return new Upgrade(
                "dash",
                2,
                Key.parse("test:dash"),
                Rarity.RARE,
                List.of("move", "fast"),
                Map.of("gold", 10),
                new Stats(20, 5_000_000_000L, 1.5f, 0.25, true),
                JsonObject.builder().put("any", "thing").build(),
                icon,
                true,
                7L,
                2.5f,
                0.5);
    }

    @Test
    void generatedRecordCodecRoundTrips() {
        Codec<Upgrade> codec = Codec.of(Upgrade.class);
        Upgrade upgrade = sample("dash.png");

        JsonValue json = codec.encode(upgrade);

        assertThat(Json.write(json))
                .contains("\"name\":\"dash\"", "\"id\":\"test:dash\"", "\"rarity\":\"RARE\"", "\"xp\":5000000000");
        assertThat(codec.decode(Json.parse(Json.write(json)))).isEqualTo(upgrade);
        assertThat(codec.decode(codec.encode(sample(null))).icon()).isNull();
    }

    @Test
    void generatedCodecAcceptsMissingNullableMembersAndReportsPaths() {
        Codec<Upgrade> codec = Codec.of(Upgrade.class);
        JsonObject json = codec.encode(sample("x")).asObject();

        assertThat(codec.decode(json.without("icon")).icon()).isNull();
        assertThatThrownBy(() -> codec.decode(json.without("name")))
                .isInstanceOf(CodecException.class)
                .hasMessageContaining(".name")
                .hasMessageContaining("Missing member 'name'");
        JsonObject badStats =
                json.with("stats", json.getOrThrow("stats").asObject().with("hp", new JsonString("many")));
        assertThatThrownBy(() -> codec.decode(badStats))
                .isInstanceOfSatisfying(
                        CodecException.class, e -> assertThat(e.path()).isEqualTo(".stats.hp"));
        assertThatThrownBy(() -> codec.decode(json.with("tags", Json.parse("[\"a\", 1]"))))
                .isInstanceOfSatisfying(
                        CodecException.class, e -> assertThat(e.path()).isEqualTo(".tags[1]"));
        assertThatThrownBy(() -> codec.decode(new JsonString("nope")))
                .isInstanceOf(CodecException.class)
                .hasMessageContaining("Expected a JSON object for Upgrade");
    }

    @Test
    void codecOfUnknownClassFails() {
        assertThatThrownBy(() -> Codec.of(String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Serializable");
    }

    @Test
    void builtInCodecs() {
        assertThat(Codec.BOOLEAN.decode(Codec.BOOLEAN.encode(true))).isTrue();
        assertThat(Codec.INT.decode(Json.parse("42"))).isEqualTo(42);
        assertThat(Codec.LONG.decode(Codec.LONG.encode(1L << 40))).isEqualTo(1L << 40);
        assertThat(Codec.FLOAT.decode(Codec.FLOAT.encode(1.5f))).isEqualTo(1.5f);
        assertThat(Codec.DOUBLE.decode(Codec.DOUBLE.encode(0.1))).isEqualTo(0.1);
        assertThat(Codec.STRING.decode(Codec.STRING.encode("ż"))).isEqualTo("ż");
        assertThat(Codec.KEY.decode(new JsonString("a:b"))).isEqualTo(Key.of("a", "b"));
        assertThat(Codec.JSON.decode(JsonNull.INSTANCE)).isEqualTo(JsonNull.INSTANCE);
        assertThat(Codec.enumOf(Rarity.values()).decode(new JsonString("rare"))).isEqualTo(Rarity.RARE);
        assertThat(Codec.mapOf(Codec.INT).decode(Json.parse("{\"a\":1}"))).containsEntry("a", 1);
        Codec<@Nullable String> nullable = Codec.STRING.nullable();
        assertThat(nullable.encode(null)).isEqualTo(JsonNull.INSTANCE);
        assertThat(nullable.decode(JsonNull.INSTANCE)).isNull();
        assertThat(nullable.decode(new JsonString("x"))).isEqualTo("x");

        assertThatThrownBy(() -> Codec.INT.decode(new JsonString("x")))
                .isInstanceOf(CodecException.class)
                .hasMessageContaining("Expected a JSON number");
        assertThatThrownBy(() -> Codec.KEY.decode(new JsonString("no colon"))).isInstanceOf(CodecException.class);
        assertThatThrownBy(() -> Codec.enumOf(Rarity.values()).decode(new JsonString("epic")))
                .isInstanceOf(CodecException.class)
                .hasMessageContaining("epic");
        assertThatThrownBy(() -> Codec.mapOf(Codec.INT).decode(Json.parse("{\"a\":\"x\"}")))
                .isInstanceOfSatisfying(
                        CodecException.class, e -> assertThat(e.path()).isEqualTo(".a"));
        Codec<Integer> fromString = Codec.of(value -> new JsonString(value.toString()), json -> {
            throw new IllegalStateException();
        });
        assertThatThrownBy(() -> fromString.decode(JsonNull.INSTANCE)).isInstanceOf(CodecException.class);
        Codec<Integer> xmapped = Codec.STRING.xmap(Integer::parseInt, String::valueOf);
        assertThat(xmapped.decode(xmapped.encode(5))).isEqualTo(5);
        assertThatThrownBy(() -> xmapped.decode(new JsonString("five"))).isInstanceOf(CodecException.class);
    }

    @Test
    void dataContainerStoresTypedValuesAndConverts() {
        DataContainer data = DataContainer.create();
        Key kills = Key.parse("test:kills");
        Key visited = Key.parse("test:visited");
        Key nested = Key.parse("test:nested");
        Key upgrade = Key.parse("test:upgrade");
        DataContainer inner = DataContainer.create();
        inner.set(kills, DataType.INT, 1);

        data.set(kills, DataType.INT, 3);
        data.set(visited, DataType.LIST(DataType.KEY), List.of(Key.parse("test:level1")));
        data.set(Key.parse("test:scores"), DataType.MAP(DataType.DOUBLE), Map.of("best", 9.5));
        data.set(nested, DataType.CONTAINER, inner);
        data.set(upgrade, DataType.of("upgrade", Codec.of(Upgrade.class)), sample(null));
        data.set(Key.parse("test:flag"), DataType.BOOLEAN, true);
        data.set(Key.parse("test:name"), DataType.STRING, "x");
        data.set(Key.parse("test:big"), DataType.LONG, 1L);
        data.set(Key.parse("test:f"), DataType.FLOAT, 1f);

        assertThat(data.get(kills, DataType.INT)).isEqualTo(3);
        assertThat(data.get(kills, DataType.LONG)).as("converted through JSON").isEqualTo(3L);
        assertThat(data.getOrDefault(Key.parse("test:none"), DataType.INT, 7)).isEqualTo(7);
        assertThat(data.get(Key.parse("test:none"), DataType.INT)).isNull();
        assertThat(data.has(visited)).isTrue();
        assertThat(data.keys()).hasSize(9);
        assertThat(data.isEmpty()).isFalse();
        assertThatThrownBy(() -> data.get(visited, DataType.INT))
                .isInstanceOf(CodecException.class)
                .hasMessageContaining("test:visited");

        JsonObject json = data.toJson();
        DataContainer restored =
                DataContainer.fromJson(Json.parse(Json.write(json)).asObject());
        assertThat(restored.get(visited, DataType.LIST(DataType.KEY))).containsExactly(Key.parse("test:level1"));
        assertThat(restored.get(nested, DataType.CONTAINER).get(kills, DataType.INT))
                .isEqualTo(1);
        assertThat(restored.get(upgrade, DataType.of("upgrade", Codec.of(Upgrade.class))))
                .isEqualTo(sample(null));
        assertThat(restored.toString()).contains("test:kills");

        assertThat(data.remove(kills)).isTrue();
        assertThat(data.remove(kills)).isFalse();
        data.clear();
        assertThat(data.isEmpty()).isTrue();
        assertThat(DataType.INT.name()).isEqualTo("int");
        assertThat(DataType.LIST(DataType.KEY).toString()).isEqualTo("list<key>");
        assertThat(DataType.MAP(DataType.INT).codec()).isNotNull();
        assertThatThrownBy(() -> DataContainer.fromJson(
                        JsonObject.builder().put("bad key", 1).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
