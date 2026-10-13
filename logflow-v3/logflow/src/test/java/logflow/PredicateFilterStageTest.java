package logflow;

import static logflow.TestData.rec;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import logflow.model.LogRecord;
import logflow.stage.LogPredicates;
import logflow.stage.PredicateFilterStage;
import org.junit.Test;

public class PredicateFilterStageTest {
    private static List<LogRecord> run(Predicate<LogRecord> p, LogRecord... in) {
        PredicateFilterStage s = new PredicateFilterStage(p);
        List<LogRecord> out = new ArrayList<>();
        for (LogRecord r : in) s.process(r, out::add);
        return out;
    }

    @Test public void passesOnlyMatchingRecords() {
        LogRecord ok = rec("a", "/x", 200), bad = rec("b", "/x", 404);
        assertEquals(List.of(bad), run(r -> r.status() >= 400, ok, bad));
    }

    @Test public void dropsEverythingWhenNothingMatches() {
        assertTrue(run(r -> false, rec("a", "/x", 200)).isEmpty());
    }

    @Test public void minStatusIsInclusive() {
        List<LogRecord> out = run(LogPredicates.minStatus(400),
                rec("a", "/", 399), rec("b", "/", 400), rec("c", "/", 503));
        assertEquals(2, out.size());
    }

    @Test public void pathPrefixMatchesStartOnly() {
        List<LogRecord> out = run(LogPredicates.pathPrefix("/api"),
                rec("a", "/api/users", 200), rec("b", "/v1/api", 200), rec("c", "/apix", 200));
        assertEquals(List.of("a", "c"), out.stream().map(LogRecord::clientIp).toList());
    }

    @Test public void timeRangeIsHalfOpen() {
        Instant from = Instant.parse("2023-10-10T13:00:00Z"), to = Instant.parse("2023-10-10T14:00:00Z");
        LogRecord before = rec(from.minusSeconds(1)), atFrom = rec(from),
                inside = rec(from.plusSeconds(1800)), atTo = rec(to);
        assertEquals(List.of(atFrom, inside), run(LogPredicates.between(from, to), before, atFrom, inside, atTo));
    }

    @Test public void openEndedRangeAcceptsEverythingOnThatSide() {
        Instant from = Instant.parse("2023-10-10T13:00:00Z");
        assertEquals(1, run(LogPredicates.between(from, null), rec(from.plusSeconds(99999))).size());
        assertEquals(1, run(LogPredicates.between(null, from), rec(from.minusSeconds(99999))).size());
    }

    @Test public void countsEvaluatedAndPassed() {
        PredicateFilterStage s = new PredicateFilterStage(LogPredicates.minStatus(400));
        s.process(rec("a", "/", 200), x -> {});
        s.process(rec("b", "/", 500), x -> {});
        assertEquals(2, s.evaluated());
        assertEquals(1, s.passed());
    }

    @Test(expected = NullPointerException.class)
    public void rejectsNullPredicate() { new PredicateFilterStage(null); }
}
