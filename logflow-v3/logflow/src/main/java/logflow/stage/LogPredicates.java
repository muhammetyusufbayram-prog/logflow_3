package logflow.stage;

import java.time.Instant;
import java.util.function.Predicate;
import logflow.model.LogRecord;

/** PredicateFilterStage ile kullanılan hazır koşullar. */
public final class LogPredicates {
    private LogPredicates() {}

    /** status >= min */
    public static Predicate<LogRecord> minStatus(int min) {
        return r -> r.status() >= min;
    }

    /** path, verilen önekle başlar */
    public static Predicate<LogRecord> pathPrefix(String prefix) {
        return r -> r.path().startsWith(prefix);
    }

    /** from <= zaman damgası < to (sınırlardan biri null ise o taraf açık) */
    public static Predicate<LogRecord> between(Instant from, Instant to) {
        return r -> {
            Instant t = r.timestamp();
            return (from == null || !t.isBefore(from)) && (to == null || t.isBefore(to));
        };
    }
}
