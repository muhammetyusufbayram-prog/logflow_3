package logflow.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Source -> Stage* -> Sink zincirini çalıştırır ve yaşam döngüsünü yönetir. */
public final class Pipeline {
    private final Source<Object> source;
    private final List<Stage<Object, Object>> stages;
    private final Sink<Object> sink;

    @SuppressWarnings("unchecked")
    Pipeline(Source<?> source, List<Stage<?, ?>> stages, Sink<?> sink) {
        this.source = (Source<Object>) source;
        this.stages = new ArrayList<>();
        for (Stage<?, ?> s : stages) this.stages.add((Stage<Object, Object>) s);
        this.sink = (Sink<Object>) sink;
    }

    public RunStats run() throws IOException {
        long start = System.nanoTime();
        long[] written = {0};
        Consumer<Object> chain = item -> {
            try {
                sink.accept(item);
                written[0]++;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        for (int i = stages.size() - 1; i >= 0; i--) {
            Stage<Object, Object> stage = stages.get(i);
            Consumer<Object> next = chain;
            chain = item -> stage.process(item, next);
        }

        List<AutoCloseable> opened = new ArrayList<>();   // close() için ters sırada
        Throwable failure = null;
        long read = 0;
        try {
            sink.open();
            opened.add(sink::close);
            for (Stage<Object, Object> s : stages) {
                s.open();
                opened.add(s::close);
            }
            source.open();
            opened.add(source::close);
            var item = source.next();
            while (item.isPresent()) {
                read++;
                chain.accept(item.get());
                item = source.next();
            }
        } catch (UncheckedIOException e) {
            failure = e.getCause();
        } catch (IOException | RuntimeException e) {
            failure = e;
        } finally {
            for (int i = opened.size() - 1; i >= 0; i--) {   // source, aşamalar, en son sink
                try {
                    opened.get(i).close();
                } catch (Exception e) {
                    if (failure == null) failure = e; else failure.addSuppressed(e);
                }
            }
        }
        if (failure instanceof IOException io) throw io;
        if (failure instanceof RuntimeException re) throw re;
        if (failure != null) throw new IOException(failure);
        return new RunStats(read, written[0], System.nanoTime() - start);
    }
}
