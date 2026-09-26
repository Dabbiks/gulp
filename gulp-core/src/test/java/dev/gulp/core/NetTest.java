package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.net.HttpRequest;
import dev.gulp.api.net.HttpResponse;
import dev.gulp.api.net.WebSocket;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.backend.headless.HeadlessNet;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import dev.gulp.platform.HttpResult;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NetTest {

    private @Nullable HeadlessRunner runner;

    @AfterEach
    void tearDown() {
        if (runner != null) {
            runner.stop();
        }
    }

    private TestGame start() {
        TestGame game = new TestGame();
        runner = Fixtures.started(game);
        return game;
    }

    private <T> List<Object> outcome(Promise<T> promise, int frames) {
        List<Object> outcome = new ArrayList<>();
        promise.thenSync(outcome::add).onFailure(outcome::add);
        runner.step(frames);
        return outcome;
    }

    @Test
    void requestsAnswerFailAndTimeOut() {
        TestGame game = start();
        HeadlessNet net = runner.backend().net();
        net.respond(
                "https://example.com/scores",
                new HttpResult(
                        200,
                        Map.of("Content-Type", List.of("application/json")),
                        ByteBuffer.wrap("{\"best\": 42}".getBytes(StandardCharsets.UTF_8))));
        List<Object> ok = outcome(game.http().get("https://example.com/scores"), 1);
        assertThat(ok).hasSize(1);
        HttpResponse response = (HttpResponse) ok.getFirst();
        assertThat(response.isOk()).isTrue();
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.header("content-type")).isEqualTo("application/json");
        assertThat(response.header("missing")).isNull();
        assertThat(response.json().asObject().getOrThrow("best").asInt()).isEqualTo(42);
        assertThat(response.bytes()).hasSize(12);

        HttpResponse missing =
                (HttpResponse) outcome(game.http().post("https://example.com/none", "x=1", "text/plain"), 1)
                        .getFirst();
        assertThat(missing.isOk()).isFalse();
        assertThat(missing.status()).isEqualTo(404);
        assertThat(net.requests()).containsExactly("GET https://example.com/scores", "POST https://example.com/none");

        net.fail("https://example.com/down", "Connection refused");
        List<Object> down = outcome(game.http().get("https://example.com/down"), 1);
        assertThat(down.getFirst()).isInstanceOf(Throwable.class);
        assertThat(((Throwable) down.getFirst())).hasMessage("Connection refused");

        net.hang("https://example.com/slow");
        HttpRequest slow = HttpRequest.builder("https://example.com/slow")
                .method("PUT")
                .header("X-Test", "1")
                .body(new byte[] {1, 2}, "application/octet-stream")
                .timeout(Duration.ofMillis(500))
                .build();
        assertThat(slow.headers()).containsEntry("X-Test", "1").containsKey("Content-Type");
        List<Object> late = outcome(game.http().request(slow), 10);
        assertThat(late).isEmpty();
        runner.step(30);
        assertThat(late).hasSize(1);
        assertThat(((Throwable) late.getFirst())).hasMessageContaining("timed out");
        assertThatThrownBy(() -> HttpRequest.builder("https://x").timeout(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void webSocketsQueueUntilOpenAndReportMessages() {
        TestGame game = start();
        HeadlessNet net = runner.backend().net();
        List<String> events = new ArrayList<>();
        WebSocket socket = game.http()
                .connect("wss://example.com/chat")
                .onOpen(() -> events.add("open"))
                .onMessage(text -> events.add("text " + text))
                .onBinary(bytes -> events.add("binary " + bytes.length))
                .onClose((code, reason) -> events.add("close " + code + " " + reason))
                .onError(message -> events.add("error"));
        assertThat(socket.isOpen()).isFalse();
        socket.send("early");
        socket.send(new byte[] {1});
        runner.step(1);
        assertThat(socket.isOpen()).isTrue();
        HeadlessNet.FakeWebSocket server = net.sockets().getFirst();
        assertThat(server.sentText()).containsExactly("early");
        assertThat(server.sentBinaryCount()).isEqualTo(1);
        socket.send("hello");
        assertThat(server.sentText()).containsExactly("early", "hello");
        server.receive("welcome");
        server.receive(new byte[] {1, 2, 3});
        runner.step(1);
        server.serverClose(1001, "going away");
        runner.step(1);
        assertThat(events).containsExactly("open", "text welcome", "binary 3", "close 1001 going away");
        assertThat(socket.isOpen()).isFalse();
        assertThat(server.isClosed()).isTrue();
        socket.close();

        net.refuseSocket("wss://example.com/refused");
        List<String> refused = new ArrayList<>();
        game.http().connect("wss://example.com/refused").onError(refused::add);
        runner.step(1);
        assertThat(refused).hasSize(1);
        assertThat(refused.getFirst()).contains("refused");
    }

    @Test
    void linksOpenOnlyForWebAddresses() {
        TestGame game = start();
        game.engine().platform().openUrl("https://gulp.dev/docs");
        assertThat(runner.backend().net().openedUrls()).containsExactly("https://gulp.dev/docs");
        assertThatThrownBy(() -> game.engine().platform().openUrl("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> game.engine().platform().openUrl("javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
