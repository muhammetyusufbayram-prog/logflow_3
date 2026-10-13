package logflow;

import java.time.Instant;
import logflow.model.LogRecord;

final class TestData {
    private TestData() {}

    static LogRecord rec(String ip, String path, int status) {
        return new LogRecord(Instant.parse("2023-10-10T13:55:36Z"), ip, "GET", path, status, 100);
    }

    static LogRecord rec(Instant ts) {
        return new LogRecord(ts, "10.0.0.1", "GET", "/x", 200, 1);
    }
}
