package logflow;

import static logflow.TestData.rec;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import logflow.model.LogRecord;
import logflow.model.MissingFieldException;
import logflow.stage.ProjectionStage;
import org.junit.Test;

public class ProjectionStageTest {
    private static LogRecord project(LogRecord in, String... fields) {
        List<LogRecord> out = new ArrayList<>();
        new ProjectionStage(fields).process(in, out::add);
        assertEquals(1, out.size());
        return out.get(0);
    }

    @Test public void keepsOnlySelectedFieldsInRequestedOrder() {
        LogRecord r = project(rec("1.2.3.4", "/api", 500), "status", "timestamp");
        assertEquals("500\t2023-10-10T13:55:36Z", r.render());
    }

    @Test public void droppedFieldsAreGoneNotBlank() {
        LogRecord r = project(rec("1.2.3.4", "/api", 500), "path");
        assertTrue(r.has("path"));
        assertFalse(r.has("clientIp"));
        assertEquals("/api", r.render());   // kişisel veri (clientIp) çıktıda yok
    }

    @Test(expected = MissingFieldException.class)
    public void accessingDroppedFieldFailsLoudly() {
        project(rec("1.2.3.4", "/api", 500), "path").status();
    }

    @Test public void duplicateFieldsAreCollapsed() {
        assertEquals("/api", project(rec("1.2.3.4", "/api", 500), "path", "path").render());
    }

    @Test public void projectionIsIdempotentForSubset() {
        LogRecord once = project(rec("1.2.3.4", "/api", 500), "path", "status");
        assertEquals("/api\t500", project(once, "path", "status").render());
    }

    @Test public void projectingAnAlreadyRemovedFieldDoesNotResurrectIt() {
        LogRecord once = project(rec("1.2.3.4", "/api", 500), "path");
        assertEquals("/api", project(once, "path", "status").render());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownField() { new ProjectionStage("nope"); }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyFieldList() { new ProjectionStage(List.of()); }
}
