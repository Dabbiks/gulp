package dev.gulp.core.world;

import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonBoolean;
import dev.gulp.api.data.JsonNumber;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonString;
import dev.gulp.api.data.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Reads Tiled's XML formats ({@code .tmx} maps, {@code .tsx} tile sets) into the same JSON tree as their {@code .tmj}
 * and {@code .tsj} counterparts, so one loader serves both. Contains a small XML parser: elements, attributes, text,
 * comments, CDATA, the declaration and character references; no DTDs or namespaces, which Tiled does not write.
 */
final class TiledXml {

    private static final Set<String> NUMBERS = Set.of(
            "width",
            "height",
            "tilewidth",
            "tileheight",
            "x",
            "y",
            "id",
            "gid",
            "firstgid",
            "tilecount",
            "columns",
            "margin",
            "spacing",
            "opacity",
            "offsetx",
            "offsety",
            "parallaxx",
            "parallaxy",
            "imagewidth",
            "imageheight",
            "tileid",
            "duration",
            "rotation",
            "hexsidelength",
            "nextlayerid",
            "nextobjectid");
    private static final Set<String> FLAGS = Set.of("visible", "repeatx", "repeaty", "infinite", "locked");

    private TiledXml() {}

    /** Returns true when the text is XML rather than JSON. */
    static boolean isXml(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && c != 0xFEFF /* byte order mark */) {
                return c == '<';
            }
        }
        return false;
    }

    /**
     * Converts a {@code .tmx} document.
     *
     * @throws IllegalArgumentException if the text is not a Tiled map
     */
    static JsonObject map(String text) {
        Element root = parse(text);
        if (!root.name.equals("map")) {
            throw new IllegalArgumentException("Expected a Tiled <map>, found <" + root.name + ">");
        }
        Map<String, JsonValue> map = attributes(root);
        JsonArray.Builder tilesets = JsonArray.builder();
        for (Element set : root.children("tileset")) {
            tilesets.add(set.attributes.containsKey("source") ? new JsonObject(attributes(set)) : tileset(set));
        }
        map.put("tilesets", tilesets.build());
        map.put("layers", layers(root));
        putProperties(map, root);
        return new JsonObject(map);
    }

    /**
     * Converts a {@code .tsx} document.
     *
     * @throws IllegalArgumentException if the text is not a Tiled tile set
     */
    static JsonObject tileset(String text) {
        Element root = parse(text);
        if (!root.name.equals("tileset")) {
            throw new IllegalArgumentException("Expected a Tiled <tileset>, found <" + root.name + ">");
        }
        return tileset(root);
    }

    // ------------------------------------------------------------------ conversion

    private static JsonObject tileset(Element set) {
        Map<String, JsonValue> result = attributes(set);
        putImage(result, set.child("image"));
        JsonArray.Builder tiles = JsonArray.builder();
        for (Element tile : set.children("tile")) {
            Map<String, JsonValue> entry = attributes(tile);
            putImage(entry, tile.child("image"));
            Element animation = tile.child("animation");
            if (animation != null) {
                JsonArray.Builder frames = JsonArray.builder();
                for (Element frame : animation.children("frame")) {
                    frames.add(new JsonObject(attributes(frame)));
                }
                entry.put("animation", frames.build());
            }
            putProperties(entry, tile);
            tiles.add(new JsonObject(entry));
        }
        result.put("tiles", tiles.build());
        putProperties(result, set);
        return new JsonObject(result);
    }

    private static JsonArray layers(Element parent) {
        JsonArray.Builder layers = JsonArray.builder();
        for (Element child : parent.children) {
            Map<String, JsonValue> layer = attributes(child);
            switch (child.name) {
                case "layer" -> {
                    layer.put("type", new JsonString("tilelayer"));
                    Element data = child.child("data");
                    if (data != null) {
                        String encoding = data.attributes.getOrDefault("encoding", "");
                        layer.put("encoding", new JsonString(encoding.isEmpty() ? "xml" : encoding));
                        String compression = data.attributes.get("compression");
                        if (compression != null) {
                            layer.put("compression", new JsonString(compression));
                        }
                        List<Element> chunks = data.children("chunk");
                        if (chunks.isEmpty()) {
                            layer.put("data", data(data, encoding));
                        } else {
                            JsonArray.Builder list = JsonArray.builder();
                            for (Element chunk : chunks) {
                                Map<String, JsonValue> entry = attributes(chunk);
                                entry.put("data", data(chunk, encoding));
                                list.add(new JsonObject(entry));
                            }
                            layer.put("chunks", list.build());
                        }
                    }
                }
                case "objectgroup" -> {
                    layer.put("type", new JsonString("objectgroup"));
                    JsonArray.Builder objects = JsonArray.builder();
                    for (Element object : child.children("object")) {
                        Map<String, JsonValue> entry = attributes(object);
                        putProperties(entry, object);
                        objects.add(new JsonObject(entry));
                    }
                    layer.put("objects", objects.build());
                }
                case "imagelayer" -> {
                    layer.put("type", new JsonString("imagelayer"));
                    Element image = child.child("image");
                    if (image != null && image.attributes.containsKey("source")) {
                        layer.put("image", new JsonString(image.attributes.get("source")));
                    }
                }
                case "group" -> {
                    layer.put("type", new JsonString("group"));
                    layer.put("layers", layers(child));
                }
                default -> {
                    continue;
                }
            }
            putProperties(layer, child);
            layers.add(new JsonObject(layer));
        }
        return layers.build();
    }

    /** Layer data: the Base64 text as is, CSV or {@code <tile gid>} elements as a number array. */
    private static JsonValue data(Element data, String encoding) {
        if (encoding.equals("base64")) {
            return new JsonString(data.text.toString().trim());
        }
        JsonArray.Builder cells = JsonArray.builder();
        if (encoding.equals("csv")) {
            for (String cell : data.text.toString().split(",")) {
                String trimmed = cell.trim();
                if (!trimmed.isEmpty()) {
                    cells.add(Long.parseLong(trimmed));
                }
            }
        } else if (encoding.isEmpty()) {
            for (Element tile : data.children("tile")) {
                cells.add(Long.parseLong(tile.attributes.getOrDefault("gid", "0")));
            }
        } else {
            throw new IllegalArgumentException("Unsupported Tiled layer encoding: " + encoding);
        }
        return cells.build();
    }

    private static void putImage(Map<String, JsonValue> target, @Nullable Element image) {
        if (image == null) {
            return;
        }
        String source = image.attributes.get("source");
        if (source != null) {
            target.put("image", new JsonString(source));
        }
        String width = image.attributes.get("width");
        String height = image.attributes.get("height");
        if (width != null) {
            target.put("imagewidth", number(width));
        }
        if (height != null) {
            target.put("imageheight", number(height));
        }
    }

    private static void putProperties(Map<String, JsonValue> target, Element owner) {
        Element properties = owner.child("properties");
        if (properties == null) {
            return;
        }
        JsonArray.Builder list = JsonArray.builder();
        for (Element property : properties.children("property")) {
            String value = property.attributes.get("value");
            list.add(JsonObject.builder()
                    .put("name", property.attributes.getOrDefault("name", ""))
                    .put("type", property.attributes.getOrDefault("type", "string"))
                    // Multi-line string properties keep their value as element text.
                    .put("value", value != null ? value : property.text.toString())
                    .build());
        }
        target.put("properties", list.build());
    }

    private static Map<String, JsonValue> attributes(Element element) {
        Map<String, JsonValue> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> attribute : element.attributes.entrySet()) {
            String name = attribute.getKey();
            String value = attribute.getValue();
            if (NUMBERS.contains(name)) {
                result.put(name, number(value));
            } else if (FLAGS.contains(name)) {
                result.put(name, JsonBoolean.of(value.equals("1") || value.equals("true")));
            } else {
                result.put(name, new JsonString(value));
            }
        }
        return result;
    }

    private static JsonValue number(String value) {
        try {
            return value.contains(".")
                    ? JsonNumber.of(Double.parseDouble(value))
                    : JsonNumber.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a number in Tiled XML, found '" + value + "'", e);
        }
    }

    // ------------------------------------------------------------------ XML

    /** One XML element with its attributes, child elements and concatenated text. */
    static final class Element {
        final String name;
        final Map<String, String> attributes = new LinkedHashMap<>();
        final List<Element> children = new ArrayList<>();
        final StringBuilder text = new StringBuilder();

        Element(String name) {
            this.name = name;
        }

        @Nullable Element child(String childName) {
            for (Element child : children) {
                if (child.name.equals(childName)) {
                    return child;
                }
            }
            return null;
        }

        List<Element> children(String childName) {
            List<Element> result = new ArrayList<>();
            for (Element child : children) {
                if (child.name.equals(childName)) {
                    result.add(child);
                }
            }
            return result;
        }
    }

    /**
     * Parses a document and returns its root element.
     *
     * @throws IllegalArgumentException on malformed XML
     */
    static Element parse(String text) {
        Parser parser = new Parser(text);
        Element root = parser.document();
        if (root == null) {
            throw new IllegalArgumentException("XML document has no root element");
        }
        return root;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        @Nullable Element document() {
            Element root = null;
            while (i < s.length()) {
                if (s.startsWith("<?", i)) {
                    skipPast("?>");
                } else if (s.startsWith("<!--", i)) {
                    skipPast("-->");
                } else if (s.startsWith("<!", i)) {
                    skipPast(">");
                } else if (s.charAt(i) == '<') {
                    if (root != null) {
                        throw error("more than one root element");
                    }
                    root = element();
                } else {
                    i++;
                }
            }
            return root;
        }

        private Element element() {
            i++; // '<'
            Element element = new Element(name());
            while (true) {
                skipSpace();
                if (i >= s.length()) {
                    throw error("unterminated tag <" + element.name + ">");
                }
                char c = s.charAt(i);
                if (c == '/') {
                    expect("/>");
                    return element;
                }
                if (c == '>') {
                    i++;
                    break;
                }
                String attribute = name();
                skipSpace();
                expect("=");
                skipSpace();
                char quote = i < s.length() ? s.charAt(i) : 0;
                if (quote != '"' && quote != '\'') {
                    throw error("attribute " + attribute + " without a quoted value");
                }
                int end = s.indexOf(quote, i + 1);
                if (end < 0) {
                    throw error("unterminated attribute " + attribute);
                }
                element.attributes.put(attribute, decode(s.substring(i + 1, end)));
                i = end + 1;
            }
            while (true) {
                int next = s.indexOf('<', i);
                if (next < 0) {
                    throw error("missing </" + element.name + ">");
                }
                element.text.append(decode(s.substring(i, next)));
                i = next;
                if (s.startsWith("</", i)) {
                    i += 2;
                    String closing = name();
                    if (!closing.equals(element.name)) {
                        throw error("</" + closing + "> closes <" + element.name + ">");
                    }
                    skipSpace();
                    expect(">");
                    return element;
                } else if (s.startsWith("<!--", i)) {
                    skipPast("-->");
                } else if (s.startsWith("<![CDATA[", i)) {
                    int end = s.indexOf("]]>", i);
                    if (end < 0) {
                        throw error("unterminated CDATA");
                    }
                    element.text.append(s, i + 9, end);
                    i = end + 3;
                } else if (s.startsWith("<?", i)) {
                    skipPast("?>");
                } else {
                    element.children.add(element());
                }
            }
        }

        private String name() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (Character.isWhitespace(c) || c == '=' || c == '>' || c == '/') {
                    break;
                }
                i++;
            }
            if (start == i) {
                throw error("expected a name");
            }
            return s.substring(start, i);
        }

        private void skipSpace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private void skipPast(String end) {
            int at = s.indexOf(end, i);
            if (at < 0) {
                throw error("missing " + end);
            }
            i = at + end.length();
        }

        private void expect(String token) {
            if (!s.startsWith(token, i)) {
                throw error("expected '" + token + "'");
            }
            i += token.length();
        }

        private IllegalArgumentException error(String message) {
            int line = 1;
            for (int k = 0; k < Math.min(i, s.length()); k++) {
                if (s.charAt(k) == '\n') {
                    line++;
                }
            }
            return new IllegalArgumentException("Malformed XML at line " + line + ": " + message);
        }

        private static String decode(String raw) {
            if (raw.indexOf('&') < 0) {
                return raw;
            }
            StringBuilder out = new StringBuilder(raw.length());
            for (int k = 0; k < raw.length(); k++) {
                char c = raw.charAt(k);
                int end = c == '&' ? raw.indexOf(';', k) : -1;
                if (end < 0) {
                    out.append(c);
                    continue;
                }
                String entity = raw.substring(k + 1, end);
                switch (entity) {
                    case "lt" -> out.append('<');
                    case "gt" -> out.append('>');
                    case "amp" -> out.append('&');
                    case "quot" -> out.append('"');
                    case "apos" -> out.append('\'');
                    default -> {
                        if (entity.startsWith("#x") || entity.startsWith("#X")) {
                            out.appendCodePoint(Integer.parseInt(entity.substring(2), 16));
                        } else if (entity.startsWith("#")) {
                            out.appendCodePoint(Integer.parseInt(entity.substring(1)));
                        } else {
                            out.append('&').append(entity).append(';');
                        }
                    }
                }
                k = end;
            }
            return out.toString();
        }
    }
}
