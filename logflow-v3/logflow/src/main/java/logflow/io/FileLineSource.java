package logflow.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import logflow.core.Source;

/** Bir metin dosyasını satır satır okur (UTF-8). */
public final class FileLineSource implements Source<String> {
    private final Path file;
    private BufferedReader reader;

    public FileLineSource(Path file) { this.file = file; }

    @Override public void open() throws IOException {
        reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
    }

    @Override public Optional<String> next() throws IOException {
        return Optional.ofNullable(reader.readLine());
    }

    @Override public void close() throws IOException {
        if (reader != null) { reader.close(); reader = null; }
    }
}
