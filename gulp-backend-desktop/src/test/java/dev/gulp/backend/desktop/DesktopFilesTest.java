package dev.gulp.backend.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gulp.core.MainQueue;
import dev.gulp.platform.PlatformCallback;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DesktopFilesTest {

    @Test
    void aGameWithoutAnAssetsFolderStillStarts(@TempDir Path data) {
        DesktopExecutor executor = new DesktopExecutor();
        DesktopFiles files = new DesktopFiles(data.resolve("assets"), data, executor, new MainQueue());
        files.watchAssets(path -> {});
        files.watchAssets(null);
        executor.shutdown();
    }

    @Test
    void theLastRequestedWriteOfAFileWins(@TempDir Path data) throws Exception {
        DesktopExecutor executor = new DesktopExecutor();
        DesktopFiles files = new DesktopFiles(null, data, executor, new MainQueue());
        for (int i = 0; i < 200; i++) {
            files.writeUserData(
                    "preferences.json",
                    ByteBuffer.wrap(("value " + i).getBytes(StandardCharsets.UTF_8)),
                    new PlatformCallback<>() {
                        @Override
                        public void success(@Nullable Void value) {}

                        @Override
                        public void failure(Throwable error) {
                            throw new AssertionError(error);
                        }
                    });
        }
        executor.shutdown();
        assertThat(Files.readString(data.resolve("preferences.json"))).isEqualTo("value 199");
        assertThat(data.resolve("preferences.json.tmp")).doesNotExist();
    }
}
