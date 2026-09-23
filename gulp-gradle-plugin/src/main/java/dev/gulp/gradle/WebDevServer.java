package dev.gulp.gradle;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Static file server for {@code runWeb}. It lives in the Gradle daemon so that {@code ./gradlew runWeb --continuous}
 * keeps one server while Gradle rebuilds; {@code /__gulp/stamp} changes after each rebuild and the page reloads.
 */
final class WebDevServer {

    /** Running servers by port. */
    private static final Map<Integer, WebDevServer> SERVERS = new ConcurrentHashMap<>();

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("mjs", "text/javascript; charset=utf-8"),
            Map.entry("wasm", "application/wasm"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("webp", "image/webp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("ogg", "audio/ogg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("otf", "font/otf"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("yml", "text/yaml; charset=utf-8"));

    private final HttpServer server;
    private volatile Path root;

    private WebDevServer(int port, Path root) throws IOException {
        this.root = root;
        this.server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "gulp-web-server");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
    }

    /**
     * Starts a server for a directory, or points the running one on that port at it.
     *
     * @param port the port
     * @param root the directory to serve
     * @return whether a new server was started
     * @throws IOException if the port is taken by another program
     */
    static boolean serve(int port, Path root) throws IOException {
        WebDevServer running = SERVERS.get(port);
        if (running != null) {
            running.root = root;
            return false;
        }
        SERVERS.put(port, new WebDevServer(port, root));
        return true;
    }

    /**
     * Stops the server on a port.
     *
     * @param port the port
     * @return whether a server was running
     */
    static boolean stop(int port) {
        WebDevServer running = SERVERS.remove(port);
        if (running == null) {
            return false;
        }
        running.server.stop(0);
        return true;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8);
            if (path.equals("/__gulp/stamp")) {
                send(exchange, 200, "text/plain", Long.toString(stamp()).getBytes(StandardCharsets.UTF_8));
                return;
            }
            Path file = resolve(path);
            if (file == null) {
                send(exchange, 404, "text/plain", ("Not found: " + path).getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] body = Files.readAllBytes(file);
            if (file.getFileName().toString().equals("index.html")) {
                // Only pages served here reload themselves; the built site stays free of development code.
                String page = new String(body, StandardCharsets.UTF_8);
                body = page.replace("</body>", GulpGamePlugin.DEV_RELOAD + "\n</body>")
                        .getBytes(StandardCharsets.UTF_8);
            }
            send(exchange, 200, type(file), body);
        }
    }

    private @Nullable Path resolve(String path) {
        Path base = root.toAbsolutePath().normalize();
        Path file =
                base.resolve(path.startsWith("/") ? path.substring(1) : path).normalize();
        if (!file.startsWith(base)) {
            return null;
        }
        if (Files.isDirectory(file)) {
            file = file.resolve("index.html");
        }
        return Files.isRegularFile(file) ? file : null;
    }

    /** Newest modification time in the served folder. */
    private long stamp() throws IOException {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .mapToLong(file -> file.toFile().lastModified())
                    .max()
                    .orElse(0);
        }
    }

    private static String type(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return TYPES.getOrDefault(extension, "application/octet-stream");
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /** The folder served, for messages. */
    static String describe(File root) {
        return root.getAbsolutePath();
    }
}
