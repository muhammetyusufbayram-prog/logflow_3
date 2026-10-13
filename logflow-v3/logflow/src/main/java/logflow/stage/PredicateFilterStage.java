package logflow.stage;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import logflow.core.Stage;
import logflow.model.LogRecord;

/** Kaydı yalnızca koşul sağlanıyorsa iletir; aksi halde eler. */
public final class PredicateFilterStage implements Stage<LogRecord, LogRecord> {
    private final Predicate<LogRecord> predicate;
    private long evaluated;
    private long passed;

    public PredicateFilterStage(Predicate<LogRecord> predicate) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
    }

    @Override
    public void process(LogRecord record, Consumer<? super LogRecord> out) {
        evaluated++;
        if (predicate.test(record)) {
            passed++;
            out.accept(record);
        }
    }

    /** Koşulun kaç kez değerlendirildiği (sıralama deneyinde iş yükünü ölçmek için). */
    public long evaluated() { return evaluated; }

    public long passed() { return passed; }
}
