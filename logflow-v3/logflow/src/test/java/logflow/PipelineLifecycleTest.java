package logflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import logflow.core.PipelineBuilder;
import logflow.core.RunStats;
import logflow.core.Sink;
import logflow.core.Source;
import logflow.core.Stage;
import org.junit.Test;

public class PipelineLifecycleTest {
    private final List<String> events = new ArrayList<>();

    private Source<Integer> source(Integer... xs) {
        return new Source<>() {
            Iterator<Integer> it;
            public void open() { events.add("source.open"); it = List.of(xs).iterator(); }
            public Optional<Integer> next() { return it.hasNext() ? Optional.of(it.next()) : Optional.empty(); }
            public void close() { events.add("source.close"); }
        };
    }

    private Stage<Integer, Integer> stage(String name, Integer failOn) {
        return new Stage<>() {
            public void open() { events.add(name + ".open"); }
            public void process(Integer x, Consumer<? super Integer> out) {
                if (x.equals(failOn)) throw new IllegalStateException("boom");
                out.accept(x);
            }
            public void close() { events.add(name + ".close"); }
        };
    }

    private Sink<Integer> sink(List<Integer> got) {
        return new Sink<>() {
            public void open() { events.add("sink.open"); }
            public void accept(Integer x) { got.add(x); }
            public void close() { events.add("sink.close"); }
        };
    }

    @Test public void opensSinkFirstAndClosesItLast() throws IOException {
        List<Integer> got = new ArrayList<>();
        RunStats s = PipelineBuilder.from(source(1, 2)).via(stage("a", null)).via(stage("b", null))
                .to(sink(got)).run();
        assertEquals(List.of("sink.open", "a.open", "b.open", "source.open",
                "source.close", "b.close", "a.close", "sink.close"), events);
        assertEquals(List.of(1, 2), got);
        assertEquals(2, s.recordsRead());
        assertEquals(2, s.recordsWritten());
    }

    @Test public void closesEverythingEvenWhenAStageFails() throws IOException {
        List<Integer> got = new ArrayList<>();
        try {
            PipelineBuilder.from(source(1, 2, 3)).via(stage("a", 2)).to(sink(got)).run();
            fail("RuntimeException bekleniyordu");
        } catch (IllegalStateException e) {
            assertEquals("boom", e.getMessage());
        }
        assertTrue(events.containsAll(List.of("source.close", "a.close", "sink.close")));
        assertEquals(List.of(1), got);
    }

    @Test public void zeroStagesIsAValidPipeline() throws IOException {
        List<Integer> got = new ArrayList<>();
        PipelineBuilder.from(source(7)).to(sink(got)).run();
        assertEquals(List.of(7), got);
    }

    @Test public void sameStageInstancesComposeInAnyOrderAndCount() throws IOException {
        Stage<Integer, Integer> dropOdd = new Stage<>() {
            public void process(Integer x, Consumer<? super Integer> out) { if (x % 2 == 0) out.accept(x); }
        };
        Stage<Integer, Integer> dropSmall = new Stage<>() {
            public void process(Integer x, Consumer<? super Integer> out) { if (x > 2) out.accept(x); }
        };
        List<Integer> ab = new ArrayList<>(), ba = new ArrayList<>(), aaa = new ArrayList<>();
        PipelineBuilder.from(source(1, 2, 3, 4, 5, 6)).via(dropOdd).via(dropSmall).to(sink(ab)).run();
        PipelineBuilder.from(source(1, 2, 3, 4, 5, 6)).via(dropSmall).via(dropOdd).to(sink(ba)).run();
        PipelineBuilder.from(source(1, 2, 3, 4, 5, 6)).via(dropOdd).via(dropOdd).via(dropOdd).to(sink(aaa)).run();
        assertEquals(List.of(4, 6), ab);
        assertEquals(ab, ba);
        assertEquals(List.of(2, 4, 6), aaa);
    }
}
