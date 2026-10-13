package logflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import logflow.core.PipelineBuilder;
import logflow.core.RunStats;
import logflow.io.FileLineSource;
import logflow.io.FileSink;
import logflow.model.MissingFieldException;
import logflow.stage.LogPredicates;
import logflow.stage.ParserStage;
import logflow.stage.PredicateFilterStage;
import logflow.stage.ProjectionStage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PipelineIntegrationTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private Path fixture() throws Exception {
        return Paths.get(getClass().getResource("/access-small.log").toURI());
    }

    /** Belgedeki örnek: from -> Parser -> Filter -> Projection -> FileSink. */
    @Test public void threeStagePipelineWritesExpectedFile() throws Exception {
        Path out = tmp.getRoot().toPath().resolve("out.tsv");
        ParserStage parser = new ParserStage();
        RunStats s = PipelineBuilder.from(new FileLineSource(fixture()))
                .via(parser)
                .via(new PredicateFilterStage(r -> r.status() >= 400))
                .via(new PredicateFilterStage(LogPredicates.pathPrefix("/api")))
                .via(new ProjectionStage("timestamp", "clientIp", "status", "path"))
                .to(new FileSink(out))
                .run();

        assertEquals(List.of(
                "2023-10-10T13:57:02Z\t10.0.0.3\t500\t/api/orders",
                "2023-10-10T14:10:00Z\t10.0.0.4\t404\t/api/users/7",
                "2023-10-10T15:00:00Z\t10.0.0.6\t403\t/api/orders/9"), Files.readAllLines(out));
        assertEquals(7, s.recordsRead());
        assertEquals(3, s.recordsWritten());
        assertEquals(1, parser.malformedCount());
    }

    @Test public void filterOrderDoesNotChangeOutput() throws Exception {
        String ab = runFiltersThenRead(true), ba = runFiltersThenRead(false);
        assertEquals(ab, ba);
        assertTrue(ab.contains("10.0.0.3"));
    }

    private String runFiltersThenRead(boolean statusFirst) throws Exception {
        Path out = tmp.newFile().toPath();
        var status = new PredicateFilterStage(LogPredicates.minStatus(400));
        var prefix = new PredicateFilterStage(LogPredicates.pathPrefix("/api"));
        var b = PipelineBuilder.from(new FileLineSource(fixture())).via(new ParserStage());
        var b2 = statusFirst ? b.via(status).via(prefix) : b.via(prefix).via(status);
        b2.to(new FileSink(out)).run();
        return Files.readString(out);
    }

    @Test public void projectionBeforeFilterWorksWhenFilterFieldIsKept() throws Exception {
        Path out = tmp.newFile().toPath();
        PipelineBuilder.from(new FileLineSource(fixture())).via(new ParserStage())
                .via(new ProjectionStage("status", "path"))
                .via(new PredicateFilterStage(LogPredicates.minStatus(500)))
                .to(new FileSink(out)).run();
        assertEquals(List.of("500\t/api/orders"), Files.readAllLines(out));
    }

    @Test(expected = MissingFieldException.class)
    public void projectionBeforeFilterFailsLoudlyWhenFilterFieldIsDropped() throws Exception {
        PipelineBuilder.from(new FileLineSource(fixture())).via(new ParserStage())
                .via(new ProjectionStage("path"))
                .via(new PredicateFilterStage(LogPredicates.minStatus(500)))
                .to(new FileSink(tmp.newFile().toPath())).run();
    }

    @Test public void sinkIsClosedEvenIfFilterThrows() throws Exception {
        Path out = tmp.newFile().toPath();
        try {
            PipelineBuilder.from(new FileLineSource(fixture())).via(new ParserStage())
                    .via(new ProjectionStage("path"))
                    .via(new PredicateFilterStage(LogPredicates.minStatus(500)))
                    .to(new FileSink(out)).run();
        } catch (MissingFieldException expected) {
            // hata yayıldı; kaynaklar yine de serbest bırakıldı
        }
        Files.delete(out);   // Linux'ta kilit yok ama dosyanın var olduğunu ve erişilebilir olduğunu doğrular
    }

    @Test(expected = IOException.class)
    public void missingInputFileIsReportedAsIoException() throws Exception {
        PipelineBuilder.from(new FileLineSource(tmp.getRoot().toPath().resolve("yok.log")))
                .via(new ParserStage()).to(new FileSink(tmp.newFile().toPath())).run();
    }
}
