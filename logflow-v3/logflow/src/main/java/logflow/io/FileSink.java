package logflow.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import logflow.core.Sink;
import logflow.model.LogRecord;

/** Kayıtları satır satır dosyaya yazar. close() arabelleği boşaltır; çağrılmazsa çıktı kaybolur. */
public final class FileSink implements Sink<LogRecord> {
    private final Path file;
    private BufferedWriter writer;

    public FileSink(Path file) { this.file = file; }

    @Override public void open() throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
    }

    @Override public void accept(LogRecord record) throws IOException {
        if (writer == null) throw new IllegalStateException("FileSink açılmadı: open() çağrılmalı");
        writer.write(record.render());
        writer.write('\n');
    }

    @Override public void close() throws IOException {
        if (writer != null) {          // tekrar çağrılması zararsız
            BufferedWriter w = writer;
            writer = null;
            w.close();                 // flush + dosyayı serbest bırak
        }
    }
}
