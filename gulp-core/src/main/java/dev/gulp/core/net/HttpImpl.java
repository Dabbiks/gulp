package dev.gulp.core.net;

import dev.gulp.api.Logger;
import dev.gulp.api.net.Http;
import dev.gulp.api.net.HttpRequest;
import dev.gulp.api.net.HttpResponse;
import dev.gulp.api.net.WebSocket;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.api.scheduler.Scheduler;
import dev.gulp.core.scheduler.PromiseImpl;
import dev.gulp.platform.HttpResult;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformNet;
import dev.gulp.platform.PlatformWebSocket;
import dev.gulp.platform.WebSocketListener;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * {@link Http} over {@link PlatformNet}: callbacks already arrive on the main thread; the timeout is a real-time task
 * that fails the promise if the platform has not answered by then (a late answer is ignored).
 */
public final class HttpImpl implements Http {

    private final Supplier<@Nullable PlatformNet> net;
    private final Supplier<PromiseImpl<HttpResponse>> promises;
    private final Scheduler realtime;
    private final Logger logger;

    /**
     * Creates the HTTP access.
     *
     * @param net the platform network, looked up on first use
     * @param promises creates promises owned by the game
     * @param realtime schedules timeouts in real time
     * @param logger where socket errors without a handler go
     */
    public HttpImpl(
            Supplier<@Nullable PlatformNet> net,
            Supplier<PromiseImpl<HttpResponse>> promises,
            Scheduler realtime,
            Logger logger) {
        this.net = net;
        this.promises = promises;
        this.realtime = realtime;
        this.logger = logger;
    }

    private PlatformNet net() {
        PlatformNet current = net.get();
        if (current == null) {
            throw new UnsupportedOperationException("This platform has no network access");
        }
        return current;
    }

    @Override
    public Promise<HttpResponse> get(String url) {
        return request(HttpRequest.builder(url).build());
    }

    @Override
    public Promise<HttpResponse> post(String url, String body, String contentType) {
        return request(HttpRequest.builder(url).body(body, contentType).build());
    }

    @Override
    public Promise<HttpResponse> request(HttpRequest request) {
        PromiseImpl<HttpResponse> promise = promises.get();
        byte[] body = request.body();
        boolean[] done = {false};
        var timeout = realtime.later(request.timeout(), () -> {
            if (!done[0]) {
                done[0] = true;
                promise.fail(new java.io.IOException(
                        "HTTP " + request.method() + " " + request.url() + " timed out after " + request.timeout()));
            }
        });
        try {
            net().http(
                            request.method(),
                            request.url(),
                            request.headers(),
                            body == null ? null : ByteBuffer.wrap(body),
                            new PlatformCallback<>() {
                                @Override
                                public void success(HttpResult result) {
                                    if (done[0]) {
                                        return;
                                    }
                                    done[0] = true;
                                    timeout.cancel();
                                    promise.complete(convert(result));
                                }

                                @Override
                                public void failure(Throwable error) {
                                    if (done[0]) {
                                        return;
                                    }
                                    done[0] = true;
                                    timeout.cancel();
                                    promise.fail(error);
                                }
                            });
        } catch (RuntimeException error) {
            done[0] = true;
            timeout.cancel();
            promise.fail(error);
        }
        return promise;
    }

    private static HttpResponse convert(HttpResult result) {
        Map<String, List<String>> headers = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : result.headers().entrySet()) {
            headers.computeIfAbsent(entry.getKey().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                    .addAll(entry.getValue());
        }
        ByteBuffer buffer = result.body().duplicate();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new HttpResponse(result.status(), headers, bytes);
    }

    @Override
    public WebSocket connect(String url) {
        SocketImpl socket = new SocketImpl(url, logger);
        socket.platform = net().openWebSocket(url, socket);
        return socket;
    }

    /** A WebSocket whose events arrive on the main thread. */
    private static final class SocketImpl implements WebSocket, WebSocketListener {
        private final String url;
        private final Logger logger;
        private @Nullable PlatformWebSocket platform;
        private boolean open;
        private boolean closed;
        private final List<Object> queued = new ArrayList<>();
        private @Nullable Runnable onOpen;
        private @Nullable Consumer<String> onMessage;
        private @Nullable Consumer<byte[]> onBinary;
        private @Nullable CloseHandler onClose;
        private @Nullable Consumer<String> onError;

        SocketImpl(String url, Logger logger) {
            this.url = url;
            this.logger = logger;
        }

        @Override
        public void send(String text) {
            PlatformWebSocket target = platform;
            if (open && target != null) {
                target.send(text);
            } else if (!closed) {
                queued.add(text);
            }
        }

        @Override
        public void send(byte[] bytes) {
            PlatformWebSocket target = platform;
            if (open && target != null) {
                target.send(ByteBuffer.wrap(bytes.clone()));
            } else if (!closed) {
                queued.add(bytes.clone());
            }
        }

        @Override
        public void close() {
            PlatformWebSocket target = platform;
            if (!closed && target != null) {
                target.close(1000, "");
            }
            closed = true;
            open = false;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public WebSocket onOpen(Runnable handler) {
            this.onOpen = handler;
            return this;
        }

        @Override
        public WebSocket onMessage(Consumer<String> handler) {
            this.onMessage = handler;
            return this;
        }

        @Override
        public WebSocket onBinary(Consumer<byte[]> handler) {
            this.onBinary = handler;
            return this;
        }

        @Override
        public WebSocket onClose(CloseHandler handler) {
            this.onClose = handler;
            return this;
        }

        @Override
        public WebSocket onError(Consumer<String> handler) {
            this.onError = handler;
            return this;
        }

        @Override
        public void opened() {
            if (closed) {
                return;
            }
            open = true;
            PlatformWebSocket target = platform;
            if (target != null) {
                for (Object message : queued) {
                    if (message instanceof String text) {
                        target.send(text);
                    } else {
                        target.send(ByteBuffer.wrap((byte[]) message));
                    }
                }
            }
            queued.clear();
            Runnable handler = onOpen;
            if (handler != null) {
                handler.run();
            }
        }

        @Override
        public void textMessage(String text) {
            Consumer<String> handler = onMessage;
            if (handler != null) {
                handler.accept(text);
            }
        }

        @Override
        public void binaryMessage(ByteBuffer data) {
            Consumer<byte[]> handler = onBinary;
            if (handler != null) {
                ByteBuffer copy = data.duplicate();
                byte[] bytes = new byte[copy.remaining()];
                copy.get(bytes);
                handler.accept(bytes);
            }
        }

        @Override
        public void closed(int code, String reason) {
            open = false;
            closed = true;
            CloseHandler handler = onClose;
            if (handler != null) {
                handler.closed(code, reason);
            }
        }

        @Override
        public void failed(Throwable error) {
            open = false;
            Consumer<String> handler = onError;
            String message = error.getMessage() != null ? error.getMessage() : error.toString();
            if (handler != null) {
                handler.accept(message);
            } else {
                logger.warn("WebSocket " + url + " failed: " + message);
            }
        }
    }
}
