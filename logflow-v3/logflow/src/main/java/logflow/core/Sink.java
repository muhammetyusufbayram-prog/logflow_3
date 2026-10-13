package logflow.core;

import java.io.IOException;

/** Pipeline'ın sonu. close() çağrılmazsa arabelleğe alınmış çıktı kaybolabilir. */
public interface Sink<T> {
    default void open() throws IOException {}

    void accept(T item) throws IOException;

    default void close() throws IOException {}
}
