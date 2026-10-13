package logflow.stage;

import java.util.List;
import java.util.function.Consumer;
import logflow.core.Stage;
import logflow.model.LogRecord;

/**
 * Her kaydı seçilen alanların alt kümesine indirir. Veri minimizasyonu:
 * gereksiz (ör. clientIp gibi kişisel) alanlar çıktıya hiç ulaşmaz.
 */
public final class ProjectionStage implements Stage<LogRecord, LogRecord> {
    private final List<String> fields;

    public ProjectionStage(String... fields) {
        this(List.of(fields));
    }

    public ProjectionStage(List<String> fields) {
        if (fields.isEmpty()) throw new IllegalArgumentException("En az bir alan gerekli");
        for (String f : fields) {
            if (!LogRecord.ALL_FIELDS.contains(f)) {
                throw new IllegalArgumentException(
                        "Bilinmeyen alan: " + f + " (geçerli: " + LogRecord.ALL_FIELDS + ")");
            }
        }
        this.fields = List.copyOf(fields);
    }

    @Override
    public void process(LogRecord record, Consumer<? super LogRecord> out) {
        out.accept(record.project(fields));
    }
}
