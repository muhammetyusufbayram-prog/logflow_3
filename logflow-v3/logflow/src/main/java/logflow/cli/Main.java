package logflow.cli;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import logflow.core.Pipeline;
import logflow.core.PipelineBuilder;
import logflow.core.RunStats;
import logflow.io.FileLineSource;
import logflow.io.FileSink;
import logflow.model.LogRecord;
import logflow.stage.LogPredicates;
import logflow.stage.ParserStage;
import logflow.stage.PredicateFilterStage;
import logflow.stage.ProjectionStage;

/** Yapılandırılabilir komut satırı log filtresi. Çıkış kodları: 0 başarı, 1 G/Ç hatası, 2 kullanım hatası. */
public final class Main {
    public static void main(String[] args) {
        // Konsol kodlamasından bağımsız olarak Türkçe karakterlerin doğru görünmesi için açıkça UTF-8
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        System.exit(run(args, out, err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        CliOptions o;
        try {
            o = CliOptions.parse(args);
            if (o.help()) { out.print(CliOptions.USAGE); return 0; }
            if (o.fields() != null) new ProjectionStage(o.fields());   // alan adlarını erkenden doğrula
            if (Files.exists(o.output()) && Files.isSameFile(o.input(), o.output())) {
                throw new IllegalArgumentException("Girdi ve çıktı aynı dosya olamaz");
            }
        } catch (IllegalArgumentException e) {
            err.println("Hata: " + e.getMessage());
            err.print(CliOptions.USAGE);
            return 2;
        } catch (IOException e) {
            err.println("Hata: " + e);
            return 1;
        }
        try {
            ParserStage parser = new ParserStage();
            RunStats s = build(o, parser).run();
            out.printf("Okunan satır: %d, yazılan kayıt: %d, bozuk satır: %d, süre: %.1f ms%n",
                    s.recordsRead(), s.recordsWritten(), parser.malformedCount(), s.elapsedMillis());
            return 0;
        } catch (IOException e) {
            err.println("G/Ç hatası: " + e);
            return 1;
        }
    }

    /** Argümanlara göre pipeline kurar: parse -> [status] -> [path] -> [zaman] -> [projeksiyon] -> yaz. */
    static Pipeline build(CliOptions o, ParserStage parser) {
        PipelineBuilder<LogRecord> b = PipelineBuilder.from(new FileLineSource(o.input())).via(parser);
        if (o.minStatus() != null) b = b.via(new PredicateFilterStage(LogPredicates.minStatus(o.minStatus())));
        if (o.pathPrefix() != null) b = b.via(new PredicateFilterStage(LogPredicates.pathPrefix(o.pathPrefix())));
        if (o.since() != null || o.until() != null) {
            b = b.via(new PredicateFilterStage(LogPredicates.between(o.since(), o.until())));
        }
        if (o.fields() != null) b = b.via(new ProjectionStage(o.fields()));
        return b.to(new FileSink(o.output()));
    }
}
