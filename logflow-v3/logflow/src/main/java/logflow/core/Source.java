package logflow.core;

import java.io.IOException;
import java.util.Optional;

/** Pipeline'ın başlangıcı. Yaşam döngüsü: open() -> next()* -> close(). */
public interface Source<T> {
    default void open() throws IOException {}

    /** Sıradaki öğe; kaynak tükendiyse Optional.empty(). */
    Optional<T> next() throws IOException;

    default void close() throws IOException {}
}
