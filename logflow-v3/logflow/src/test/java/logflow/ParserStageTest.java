package logflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import logflow.model.LogRecord;
import logflow.stage.ParserStage;
import org.junit.Test;

public class ParserStageTest {
    private static List<LogRecord> parse(ParserStage p, String... lines) {
        List<LogRecord> out = new ArrayList<>();
        for (String l : lines) p.process(l, out::add);
        return out;
    }

    @Test public void parsesCommonLogFormat() {
        LogRecord r = parse(new ParserStage(),
                "10.0.0.1 - - [10/Oct/2023:13:55:36 +0000] \"GET /api/users HTTP/1.1\" 200 512").get(0);
        assertEquals(Instant.parse("2023-10-10T13:55:36Z"), r.timestamp());
        assertEquals("10.0.0.1", r.clientIp());
        assertEquals("GET", r.method());
        assertEquals("/api/users", r.path());
        assertEquals(200, r.status());
        assertEquals(512, r.bytes());
    }

    @Test public void convertsTimezoneOffsetToUtc() {
        LogRecord r = parse(new ParserStage(),
                "1.1.1.1 - - [10/Oct/2023:13:55:36 +0300] \"GET / HTTP/1.1\" 200 1").get(0);
        assertEquals(Instant.parse("2023-10-10T10:55:36Z"), r.timestamp());
    }

    @Test public void dashBytesMeansZero() {
        assertEquals(0, parse(new ParserStage(),
                "1.1.1.1 - - [10/Oct/2023:13:55:36 +0000] \"GET / HTTP/1.1\" 304 -").get(0).bytes());
    }

    @Test public void skipsAndCountsMalformedLines() {
        ParserStage p = new ParserStage();
        List<LogRecord> out = parse(p, "garbage", "", "1.1.1.1 - - [99/Foo/2023:99:99:99 +0000] \"GET / HTTP/1.1\" 200 1");
        assertTrue(out.isEmpty());
        assertEquals(3, p.malformedCount());
    }
}
