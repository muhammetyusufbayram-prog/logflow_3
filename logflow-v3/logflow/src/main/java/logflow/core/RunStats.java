package logflow.core;

/** Bir pipeline çalışmasının özeti. */
public record RunStats(long recordsRead, long recordsWritten, long elapsedNanos) {
    public double elapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }
}
