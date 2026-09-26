package dev.gulp.backend.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gulp.platform.PlatformLog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Log rotation and crash reports of section 20.2. */
class DesktopLogTest {

    @TempDir
    Path data;

    @Test
    void latestLogIsArchivedAndOldArchivesAreDropped() throws IOException {
        Path logs = data.resolve("logs");
        Files.createDirectories(logs);
        for (int i = 0; i < 12; i++) {
            Path old = logs.resolve("2020-01-0" + (i % 9 + 1) + "-" + (i + 1) + ".log.gz");
            Files.write(old, new byte[] {1});
            Files.setLastModifiedTime(old, FileTime.fromMillis(1_000L * i));
        }
        Files.writeString(logs.resolve("latest.log"), "previous run\n");

        DesktopLog log = new DesktopLog(logs);
        log.write(PlatformLog.INFO, "test", "hello", null);
        log.close();

        List<Path> archives;
        try (var files = Files.list(logs)) {
            archives = files.filter(f -> f.toString().endsWith(".log.gz")).toList();
        }
        assertThat(archives).hasSize(DesktopLog.KEEP_ARCHIVES);
        Path newest = archives.stream()
                .filter(f -> !f.getFileName().toString().startsWith("2020-"))
                .findFirst()
                .orElseThrow();
        try (var in = new GZIPInputStream(Files.newInputStream(newest))) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("previous run\n");
        }
        assertThat(Files.readString(logs.resolve("latest.log"))).contains("[INFO] [test] hello");
    }

    @Test
    void crashReportsGoNextToTheLogs() throws IOException {
        DesktopLog log = new DesktopLog(data.resolve("logs"));
        String where = log.crash("crash-2026-09-25_14-03-07.txt", "---- Gulp crash report ----");
        log.close();
        Path report = data.resolve("crash-reports").resolve("crash-2026-09-25_14-03-07.txt");
        assertThat(where).isEqualTo(report.toAbsolutePath().toString());
        assertThat(Files.readString(report)).isEqualTo("---- Gulp crash report ----");
    }
}
