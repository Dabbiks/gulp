package dev.gulp.core.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.data.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

class TiledXmlTest {

    @Test
    void recognisesXmlAndReadsEntities() {
        assertThat(TiledXml.isXml("﻿  <map/>")).isTrue();
        assertThat(TiledXml.isXml("{}")).isFalse();
        assertThat(TiledXml.isXml("   ")).isFalse();
        JsonObject map = TiledXml.map("""
                <?xml version="1.0"?><!DOCTYPE map><map class='&lt;&#65;&#x42;&unknown;&gt;' infinite="1">
                 <?tiled hint?><!-- comment --><imagelayer name="empty"/><unknownlayer/>
                </map>""");
        assertThat(map.get("class").asString()).isEqualTo("<AB&unknown;>");
        assertThat(map.get("infinite").asBoolean()).isTrue();
        assertThat(map.get("layers").asArray().size()).isEqualTo(1);
        assertThat(TiledXml.tileset("<tileset name='a'><tile id='0'><image source='a.png'/></tile></tileset>")
                        .get("tiles")
                        .toString())
                .contains("a.png");
    }

    @Test
    void rejectsBrokenFiles() {
        for (String broken : List.of(
                "<map><layer></map>",
                "<map a=1/>",
                "<map a=\"1/>",
                "<map a/>",
                "<tileset/>",
                "<map/><map/>",
                "<map><layer name=\"x\"><data encoding=\"hex\">00</data></layer></map>",
                "<map width=\"wide\"/>",
                "<map",
                "<map>text",
                "<map><![CDATA[x</map>",
                "<map><!-- x</map>",
                "< />",
                "text only")) {
            assertThatThrownBy(() -> TiledXml.map(broken)).as(broken).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> TiledXml.tileset("<map/>")).isInstanceOf(IllegalArgumentException.class);
    }
}
