package logflow.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Akıcı (fluent) birleştirici. Tür parametresi O, zincirin o anki çıktı türüdür;
 * böylece uyumsuz aşamalar (ör. ayrıştırmadan önce LogRecord filtresi)
 * derleme zamanında reddedilir.
 */
public final class PipelineBuilder<O> {
    private final Source<?> source;
    private final List<Stage<?, ?>> stages;

    private PipelineBuilder(Source<?> source, List<Stage<?, ?>> stages) {
        this.source = source;
        this.stages = stages;
    }

    public static <T> PipelineBuilder<T> from(Source<T> source) {
        return new PipelineBuilder<>(source, new ArrayList<>());
    }

    public <N> PipelineBuilder<N> via(Stage<? super O, N> stage) {
        List<Stage<?, ?>> next = new ArrayList<>(stages);
        next.add(stage);
        return new PipelineBuilder<>(source, next);
    }

    public Pipeline to(Sink<? super O> sink) {
        return new Pipeline(source, List.copyOf(stages), sink);
    }
}
