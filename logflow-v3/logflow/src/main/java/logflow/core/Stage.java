package logflow.core;

import java.util.function.Consumer;

/**
 * Tek sorumluluklu dönüşüm/eleme birimi. Bir girdi için sıfır (eleme),
 * bir (dönüşüm) ya da daha fazla çıktı üretebilir. Filtre ile dönüştürücü
 * aynı arayüzdür: fark yalnızca kaç çıktı ürettiğidir.
 */
public interface Stage<I, O> {
    default void open() {}

    void process(I input, Consumer<? super O> out);

    default void close() {}
}
