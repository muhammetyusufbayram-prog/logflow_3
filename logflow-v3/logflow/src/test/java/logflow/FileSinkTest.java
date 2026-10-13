package logflow;

import static logflow.TestData.rec;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import logflow.io.FileSink;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileSinkTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void writesOneLinePerRecordAfterClose() throws IOException {
        Path f = tmp.getRoot().toPath().resolve("out.tsv");
        FileSink sink = new FileSink(f);
        sink.open();
        sink.accept(rec("1.1.1.1", "/a", 200));
        sink.accept(rec("2.2.2.2", "/b", 500));
        sink.close();
        assertEquals(List.of("2023-10-10T13:55:36Z\t1.1.1.1\tGET\t/a\t200\t100",
                "2023-10-10T13:55:36Z\t2.2.2.2\tGET\t/b\t500\t100"), Files.readAllLines(f));
    }

    /** Hatanın kendisi: close() çağrılmazsa arabellekteki çıktı diske hiç ulaşmaz. */
    @Test public void withoutCloseBufferedOutputIsLost() throws IOException {
        Path f = tmp.getRoot().toPath().resolve("lost.tsv");
        FileSink sink = new FileSink(f);
        sink.open();
        sink.accept(rec("1.1.1.1", "/a", 200));
        assertEquals("close() olmadan dosya boş kalır", 0, Files.size(f));
        sink.close();
        assertEquals(1, Files.readAllLines(f).size());
    }

    @Test public void closeIsIdempotent() throws IOException {
        FileSink sink = new FileSink(tmp.getRoot().toPath().resolve("o.tsv"));
        sink.open();
        sink.close();
        sink.close();
    }

    @Test public void closeWithoutOpenIsHarmless() throws IOException {
        new FileSink(tmp.getRoot().toPath().resolve("never.tsv")).close();
    }

    @Test(expected = IllegalStateException.class)
    public void acceptBeforeOpenFails() throws IOException {
        new FileSink(tmp.getRoot().toPath().resolve("x.tsv")).accept(rec("a", "/", 200));
    }

    @Test public void createsMissingParentDirectories() throws IOException {
        Path f = tmp.getRoot().toPath().resolve("a/b/c/out.tsv");
        FileSink sink = new FileSink(f);
        sink.open();
        sink.close();
        assertEquals(0, Files.size(f));
    }

    @Test public void reopeningTruncatesPreviousContent() throws IOException {
        Path f = tmp.getRoot().toPath().resolve("t.tsv");
        for (int i = 0; i < 2; i++) {
            FileSink s = new FileSink(f);
            s.open();
            s.accept(rec("a", "/", 200));
            s.close();
        }
        assertEquals(1, Files.readAllLines(f).size());
    }
}
