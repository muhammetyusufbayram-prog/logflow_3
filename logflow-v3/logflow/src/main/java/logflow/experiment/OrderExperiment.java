package logflow.experiment;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import logflow.core.PipelineBuilder;
import logflow.core.Sink;
import logflow.core.Source;
import logflow.io.FileLineSource;
import logflow.io.FileSink;
import logflow.model.LogRecord;
import logflow.stage.LogPredicates;
import logflow.stage.ParserStage;
import logflow.stage.PredicateFilterStage;
import logflow.stage.ProjectionStage;

/**
 * Bileşilebilirlik deneyi: aynı veri, aşamalar farklı sırada. Çıktının bayt bayt aynı olduğunu
 * doğrular (değilse çıkış kodu 1), süreyi ve her aşamanın iş yükünü raporlar.
 * Kullanım: OrderExperiment [satırSayısı=1000000] [tekrar=7]
 */
public final class OrderExperiment {
    private record Result(String name, String hash, long millisMedian, long millisMin, long[] evals, long written) {}

    public static void main(String[] args) throws Exception {
        int lines = args.length > 0 ? Integer.parseInt(args[0]) : 1_000_000;
        int reps = args.length > 1 ? Integer.parseInt(args[1]) : 7;
        Path dir = Files.createTempDirectory("logflow-exp");
        Path input = dir.resolve("access.log");
        generate(input, lines, 42L);
        System.out.printf("Girdi: %,d satır, %,d bayt, Java %s, %d işlemci%n%n", lines, Files.size(input),
                System.getProperty("java.version"), Runtime.getRuntime().availableProcessors());

        boolean ok = true;
        List<LogRecord> parsed = new ArrayList<>();
        ParserStage pre = new ParserStage();
        for (String l : Files.readAllLines(input)) pre.process(l, parsed::add);
        Env file = new FileEnv(input), mem = new MemEnv(parsed);
        Pattern re = Pattern.compile("^/api/v\\d+/(users|orders|items)/\\d+(/.*)?$");
        Predicate<LogRecord> costly = r -> re.matcher(r.path()).matches();
        String[] keep = {"timestamp", "status", "path"};

        for (Env env : new Env[] {file, mem}) {
            String tag = env.name();
            ok &= compare(dir, reps, tag + " — Deney 1: status>=400 ve path^/api (ucuz koşullar)",
                    twoFilters(env, "status", LogPredicates.minStatus(400), "path", LogPredicates.pathPrefix("/api")));
            ok &= compare(dir, reps, tag + " — Deney 2: status>=400 ve path~regex (pahalı koşul)",
                    twoFilters(env, "status", LogPredicates.minStatus(400), "regex", costly));
            ok &= compare(dir, reps, tag + " — Deney 3: projeksiyon(timestamp,status,path) ve status>=400",
                    new Variant[] {
                        new Variant(env, "A: filtre -> projeksiyon", () -> {
                            var f = new PredicateFilterStage(LogPredicates.minStatus(400));
                            return new Run(env.base().via(f).via(new ProjectionStage(keep)), f);
                        }),
                        new Variant(env, "B: projeksiyon -> filtre", () -> {
                            var f = new PredicateFilterStage(LogPredicates.minStatus(400));
                            return new Run(env.base().via(new ProjectionStage(keep)).via(f), f);
                        })});
        }
        System.out.println(ok ? "SONUÇ: tüm sıralamalarda çıktılar bayt bayt aynı." : "SONUÇ: ÇIKTILAR FARKLI!");
        System.exit(ok ? 0 : 1);
    }

    private record Run(PipelineBuilder<LogRecord> builder, PredicateFilterStage... filters) {}
    private record Variant(Env env, String name, java.util.function.Supplier<Run> make) {}
    private record Output(Sink<LogRecord> sink, Callable<String> digest) {}

    /** Ölçüm ortamı: dosya (okuma + ayrıştırma + yazma dahil) ya da bellek (yalnızca aşamalar). */
    private interface Env {
        String name();
        PipelineBuilder<LogRecord> base();
        Output output(Path out);
    }

    private record FileEnv(Path input) implements Env {
        public String name() { return "DOSYA modu (oku+ayrıştır+yaz dahil)"; }
        public PipelineBuilder<LogRecord> base() {
            return PipelineBuilder.from(new FileLineSource(input)).via(new ParserStage());
        }
        public Output output(Path out) { return new Output(new FileSink(out), () -> sha256(out)); }
    }

    private record MemEnv(List<LogRecord> records) implements Env {
        public String name() { return "BELLEK modu (yalnızca aşamalar)"; }
        public PipelineBuilder<LogRecord> base() {
            return PipelineBuilder.from(new Source<LogRecord>() {
                java.util.Iterator<LogRecord> it;
                public void open() { it = records.iterator(); }
                public Optional<LogRecord> next() { return it.hasNext() ? Optional.of(it.next()) : Optional.empty(); }
            });
        }
        public Output output(Path out) {
            long[] h = {17, 0};
            Sink<LogRecord> sink = r -> { h[0] = h[0] * 31 + r.render().hashCode(); h[1]++; };
            return new Output(sink, () -> Long.toHexString(h[0]) + "/" + h[1]);
        }
    }

    private static Variant[] twoFilters(Env env, String n1, Predicate<LogRecord> p1, String n2, Predicate<LogRecord> p2) {
        return new Variant[] {
            new Variant(env, "A: " + n1 + " -> " + n2, () -> {
                var f1 = new PredicateFilterStage(p1); var f2 = new PredicateFilterStage(p2);
                return new Run(env.base().via(f1).via(f2), f1, f2);
            }),
            new Variant(env, "B: " + n2 + " -> " + n1, () -> {
                var f1 = new PredicateFilterStage(p2); var f2 = new PredicateFilterStage(p1);
                return new Run(env.base().via(f1).via(f2), f1, f2);
            })};
    }

    private static boolean compare(Path dir, int reps, String title, Variant[] v) throws Exception {
        System.out.println("### " + title);
        Result ra = measure(dir, v[0], reps), rb = measure(dir, v[1], reps);
        System.out.println("| Sıra | medyan ms | en iyi ms | filtre değerlendirmeleri (1., 2., ...) | yazılan | çıktı özeti |");
        System.out.println("|---|---:|---:|---|---:|---|");
        for (Result r : new Result[] {ra, rb}) {
            System.out.printf("| %s | %d | %d | %s | %,d | %s |%n", r.name, r.millisMedian, r.millisMin,
                    Arrays.toString(r.evals), r.written, r.hash.length() > 12 ? r.hash.substring(0, 12) : r.hash);
        }
        boolean same = ra.hash.equals(rb.hash);
        System.out.println(same ? "→ çıktılar AYNI" : "→ çıktılar FARKLI");
        System.out.printf("→ B/A süre oranı (medyan): %.2f%n%n", rb.millisMedian / (double) ra.millisMedian);
        return same;
    }

    private static Result measure(Path dir, Variant v, int reps) throws Exception {
        long[] times = new long[reps];
        String hash = null;
        long[] evals = null;
        long written = 0;
        for (int i = -3; i < reps; i++) {                       // 3 ısınma turu
            Path out = dir.resolve("out-" + Math.abs(v.name.hashCode()) + ".tsv");
            Run run = v.make.get();
            Output o = v.env.output(out);
            long t0 = System.nanoTime();
            written = run.builder.to(o.sink).run().recordsWritten();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            if (i >= 0) times[i] = ms;
            if (i == reps - 1) {
                hash = o.digest.call();
                evals = Arrays.stream(run.filters).mapToLong(PredicateFilterStage::evaluated).toArray();
            }
        }
        Arrays.sort(times);
        return new Result(v.name, hash, times[reps / 2], times[0], evals, written);
    }

    private static String sha256(Path p) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }

    /** Gerçekçi dağılımlı sentetik Common Log Format verisi (sabit tohum: tekrarlanabilir). */
    static void generate(Path file, int n, long seed) throws IOException {
        Random rnd = new Random(seed);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);
        Instant t = Instant.parse("2023-10-10T00:00:00Z");
        String[] resources = {"users", "orders", "items"};
        String[] statics = {"/index.html", "/static/app.js", "/static/site.css", "/img/logo.png", "/favicon.ico"};
        Function<Random, Integer> status = r -> {
            int x = r.nextInt(100);
            return x < 80 ? 200 : x < 88 ? 304 : x < 91 ? 301 : x < 95 ? 404 : x < 97 ? 403 : 500;
        };
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (int i = 0; i < n; i++) {
                t = t.plusMillis(rnd.nextInt(80));
                String path = rnd.nextInt(100) < 55
                        ? "/api/v" + (1 + rnd.nextInt(3)) + "/" + resources[rnd.nextInt(3)] + "/" + rnd.nextInt(10000)
                        : statics[rnd.nextInt(statics.length)];
                String method = rnd.nextInt(100) < 80 ? "GET" : rnd.nextInt(2) == 0 ? "POST" : "DELETE";
                w.write("10.0." + rnd.nextInt(8) + "." + rnd.nextInt(256) + " - - ["
                        + fmt.format(t.atOffset(ZoneOffset.UTC)) + "] \"" + method + " " + path
                        + " HTTP/1.1\" " + status.apply(rnd) + " " + rnd.nextInt(50000));
                w.newLine();
            }
        }
    }
}
