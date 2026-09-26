package dev.gulp.backend.desktop;

import dev.gulp.core.MainQueue;
import dev.gulp.platform.HttpResult;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformNet;
import dev.gulp.platform.PlatformWebSocket;
import dev.gulp.platform.WebSocketListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;

/**
 * Desktop networking on {@link HttpClient}: requests and WebSockets run on virtual threads and report on the main
 * thread; links open in the system browser through {@code rundll32}, {@code open} or {@code xdg-open}. Timeouts are
 * the engine's job (it fails the promise and drops the late answer), so the client sets only a connect timeout.
 */
final class DesktopNet implements PlatformNet {

    private final MainQueue mainQueue;
    private final DesktopLog log;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private @Nullable HttpClient client;

    DesktopNet(MainQueue mainQueue, DesktopLog log) {
        this.mainQueue = mainQueue;
        this.log = log;
    }

    private synchronized HttpClient client() {
        HttpClient current = client;
        if (current == null) {
            current = HttpClient.newBuilder()
                    .executor(executor)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
            client = current;
        }
        return current;
    }

    @Override
    public void http(
            String method,
            String url,
            Map<String, String> headers,
            @Nullable ByteBuffer body,
            PlatformCallback<HttpResult> callback) {
        HttpRequest request;
        try {
            byte[] bytes = new byte[body == null ? 0 : body.remaining()];
            if (body != null) {
                body.duplicate().get(bytes);
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .method(
                            method,
                            body == null
                                    ? HttpRequest.BodyPublishers.noBody()
                                    : HttpRequest.BodyPublishers.ofByteArray(bytes));
            for (Map.Entry<String, String> header : headers.entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
            request = builder.build();
        } catch (RuntimeException error) {
            mainQueue.post(() -> callback.failure(error));
            return;
        }
        client().sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                mainQueue.post(() -> callback.failure(cause));
            } else {
                HttpResult result = new HttpResult(
                        response.statusCode(), response.headers().map(), ByteBuffer.wrap(response.body()));
                mainQueue.post(() -> callback.success(result));
            }
        });
    }

    @Override
    public PlatformWebSocket openWebSocket(String url, WebSocketListener listener) {
        DesktopSocket socket = new DesktopSocket(listener);
        CompletableFuture<WebSocket> opening;
        try {
            opening = client().newWebSocketBuilder().buildAsync(URI.create(url), socket);
        } catch (RuntimeException error) {
            opening = CompletableFuture.failedFuture(error);
        }
        socket.tail = opening.whenComplete((ws, error) -> {
            if (error != null) {
                socket.closed = true;
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                mainQueue.post(() -> listener.failed(cause));
            }
        });
        return socket;
    }

    @Override
    public void openUrl(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> command = os.contains("win")
                ? List.of("rundll32", "url.dll,FileProtocolHandler", url)
                : os.contains("mac") ? List.of("open", url) : List.of("xdg-open", url);
        executor.execute(() -> {
            try {
                new ProcessBuilder(command).inheritIO().start();
            } catch (IOException error) {
                log.write(DesktopLog.WARN, "gulp", "Could not open " + url + " in the browser", error);
            }
        });
    }

    void shutdown() {
        executor.shutdownNow();
    }

    /**
     * A WebSocket that gathers message parts and reports whole messages on the main thread. Sends are chained on one
     * future, so they never overlap and the main thread never waits.
     */
    private final class DesktopSocket implements PlatformWebSocket, WebSocket.Listener {

        private final WebSocketListener listener;
        private final StringBuilder text = new StringBuilder();
        private final ByteArrayOutputStream binary = new ByteArrayOutputStream();
        CompletableFuture<WebSocket> tail = new CompletableFuture<>();
        volatile boolean closed;

        DesktopSocket(WebSocketListener listener) {
            this.listener = listener;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            mainQueue.post(listener::opened);
            webSocket.request(1);
        }

        @Override
        public @Nullable CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                String message = text.toString();
                text.setLength(0);
                mainQueue.post(() -> listener.textMessage(message));
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public @Nullable CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] part = new byte[data.remaining()];
            data.get(part);
            binary.write(part, 0, part.length);
            if (last) {
                ByteBuffer message = ByteBuffer.wrap(binary.toByteArray());
                binary.reset();
                mainQueue.post(() -> listener.binaryMessage(message));
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public @Nullable CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed = true;
            mainQueue.post(() -> listener.closed(statusCode, reason));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closed = true;
            mainQueue.post(() -> listener.failed(error));
        }

        @Override
        public void send(String message) {
            then(ws -> ws.sendText(message, true));
        }

        @Override
        public void send(ByteBuffer data) {
            byte[] copy = new byte[data.remaining()];
            data.duplicate().get(copy);
            then(ws -> ws.sendBinary(ByteBuffer.wrap(copy), true));
        }

        @Override
        public void close(int code, String reason) {
            then(ws -> ws.sendClose(code, reason));
        }

        private synchronized void then(java.util.function.Function<WebSocket, CompletableFuture<WebSocket>> action) {
            if (closed) {
                throw new IllegalStateException("WebSocket is closed");
            }
            tail = tail.thenCompose(action);
        }
    }
}
