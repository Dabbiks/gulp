package dev.gulp.examples.showcase;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Loads the web build of the showcase in headless Chromium, Firefox and WebKit, as Wasm GC and as JavaScript, and
 * checks that the game starts, draws, answers a console command and logs no errors.
 */
class WebSmokeTest {

    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "wasm", "application/wasm",
            "json", "application/json",
            "png", "image/png",
            "yml", "text/yaml; charset=utf-8");

    private static HttpServer server;
    private static Playwright playwright;
    private static String url;

    @BeforeAll
    static void serve() throws IOException {
        Path root = Path.of(System.getProperty("gulp.web.dir", "build/web"));
        assertThat(root.resolve("index.html")).exists();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                Path file = root.resolve(path.equals("/") ? "index.html" : path.substring(1))
                        .normalize();
                if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                String name = file.getFileName().toString();
                String extension = name.substring(name.lastIndexOf('.') + 1);
                byte[] body = Files.readAllBytes(file);
                exchange.getResponseHeaders()
                        .set("Content-Type", TYPES.getOrDefault(extension, "application/octet-stream"));
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        });
        server.start();
        url = "http://localhost:" + server.getAddress().getPort() + "/";
        playwright = Playwright.create();
    }

    @AfterAll
    static void stop() {
        if (playwright != null) {
            playwright.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    static Stream<Arguments> runs() {
        Set<String> browsers = new HashSet<>(
                Arrays.asList(System.getProperty("gulp.browsers", "chromium").split(",")));
        List<Arguments> runs = new ArrayList<>();
        for (String browser : List.of("chromium", "firefox", "webkit")) {
            if (browsers.contains(browser)) {
                runs.add(Arguments.of(browser, "wasm"));
                runs.add(Arguments.of(browser, "js"));
            }
        }
        return runs.stream();
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("runs")
    void showcaseStartsAndDraws(String browserName, String target) throws Exception {
        BrowserType type =
                switch (browserName) {
                    case "firefox" -> playwright.firefox();
                    case "webkit" -> playwright.webkit();
                    default -> playwright.chromium();
                };
        try (Browser browser = type.launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(800, 600));
            List<String> logs = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            page.onConsoleMessage(message -> {
                String text = message.type() + ": " + message.text();
                synchronized (logs) {
                    logs.add(text);
                }
                if (message.type().equals("error")) {
                    synchronized (errors) {
                        errors.add(text);
                    }
                }
            });
            page.onPageError(error -> {
                synchronized (errors) {
                    errors.add("page error: " + error);
                }
            });
            page.navigate(url + "?target=" + target);

            String wasm = String.valueOf(page.evaluate("typeof WebAssembly === 'object' && WebAssembly.validate("
                    + "new Uint8Array([0,97,115,109,1,0,0,0,1,5,1,95,1,120,0]))"));
            Assumptions.assumeTrue(
                    !target.equals("wasm") || wasm.equals("true"), browserName + " has no Wasm GC support");

            waitFor(page, logs, "Showcase started", 30_000);
            waitFor(page, logs, "sprites, ", 15_000);
            page.evaluate("gulp.command('/tps')");
            waitFor(page, logs, "TPS", 5_000);

            Object sized = page.evaluate("(() => { const c = document.getElementById('gulp-canvas');"
                    + " return c.width === Math.round(c.clientWidth * devicePixelRatio)"
                    + " && c.height === Math.round(c.clientHeight * devicePixelRatio); })()");
            assertThat(sized).as("canvas buffer follows its CSS size").isEqualTo(true);

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(page.screenshot()));
            Set<Integer> colors = new HashSet<>();
            for (int y = 0; y < image.getHeight(); y += 7) {
                for (int x = 0; x < image.getWidth(); x += 7) {
                    colors.add(image.getRGB(x, y));
                }
            }
            assertThat(colors).as("distinct colors on screen").hasSizeGreaterThan(5);
            synchronized (errors) {
                assertThat(errors)
                        .as("console errors in %s (%s)", browserName, target)
                        .isEmpty();
            }
        }
    }

    private static void waitFor(Page page, List<String> logs, String text, long timeoutMillis) {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < end) {
            synchronized (logs) {
                if (logs.stream().anyMatch(line -> line.contains(text))) {
                    return;
                }
            }
            page.waitForTimeout(100);
        }
        synchronized (logs) {
            throw new AssertionError("No log line containing '" + text + "' within " + timeoutMillis + " ms; got:\n"
                    + String.join("\n", logs));
        }
    }
}
