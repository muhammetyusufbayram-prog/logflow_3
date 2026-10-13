package logflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.List;
import logflow.cli.Main;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MainTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream(), errBuf = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(args, new PrintStream(outBuf, true, StandardCharsets.UTF_8),
                new PrintStream(errBuf, true, StandardCharsets.UTF_8));
    }

    private String in() throws Exception {
        return Paths.get(getClass().getResource("/access-small.log").toURI()).toString();
    }

    @Test public void documentExampleArguments() throws Exception {
        Path out = tmp.getRoot().toPath().resolve("o.tsv");
        int code = run("--input", in(), "--output", out.toString(),
                "--min-status", "400", "--path-prefix", "/api", "--fields", "timestamp,clientIp,status");
        assertEquals(0, code);
        assertEquals(List.of("2023-10-10T13:57:02Z\t10.0.0.3\t500",
                "2023-10-10T14:10:00Z\t10.0.0.4\t404",
                "2023-10-10T15:00:00Z\t10.0.0.6\t403"), Files.readAllLines(out));
        assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("yazılan kayıt: 3"));
    }

    @Test public void noFiltersCopiesAllParsedRecords() throws Exception {
        Path out = tmp.getRoot().toPath().resolve("all.tsv");
        assertEquals(0, run("-i", in(), "-o", out.toString()));
        assertEquals(6, Files.readAllLines(out).size());
    }

    @Test public void timeRangeFilter() throws Exception {
        Path out = tmp.getRoot().toPath().resolve("t.tsv");
        assertEquals(0, run("-i", in(), "-o", out.toString(), "--since", "2023-10-10T14:00:00Z",
                "--until", "2023-10-10T15:00:00Z", "--fields", "clientIp"));
        assertEquals(List.of("10.0.0.4", "10.0.0.5"), Files.readAllLines(out));
    }

    @Test public void helpExitsZero() {
        assertEquals(0, run("--help"));
        assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("--min-status"));
    }

    @Test public void usageErrorsExitTwoAndWriteNothing() throws Exception {
        Path out = tmp.getRoot().toPath().resolve("never.tsv");
        assertEquals(2, run("-i", in(), "-o", out.toString(), "--min-status", "abc"));
        assertEquals(2, run("-i", in(), "-o", out.toString(), "--fields", "nope"));
        assertEquals(2, run("-i", in()));
        assertEquals(2, run("-i", in(), "-o", out.toString(), "--bogus", "1"));
        assertEquals(2, run("-i", in(), "-o", out.toString(), "--min-status"));
        assertFalse(Files.exists(out));
        assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("Hata:"));
    }

    @Test public void refusesToOverwriteItsOwnInput() throws Exception {
        Path copy = tmp.newFile("in.log").toPath();
        Files.copy(Paths.get(in()), copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        long before = Files.size(copy);
        assertEquals(2, run("-i", copy.toString(), "-o", copy.toString()));
        assertEquals(before, Files.size(copy));
    }

    @Test public void missingInputExitsOne() {
        assertEquals(1, run("-i", tmp.getRoot().toPath().resolve("yok").toString(),
                "-o", tmp.getRoot().toPath().resolve("o").toString()));
        assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("G/Ç hatası"));
    }
}
