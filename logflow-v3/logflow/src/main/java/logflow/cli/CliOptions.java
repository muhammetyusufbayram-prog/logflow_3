package logflow.cli;

import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/** Komut satırı argümanlarını ayrıştırır. Hatalı kullanımda IllegalArgumentException fırlatır. */
public record CliOptions(Path input, Path output, Integer minStatus, String pathPrefix,
                         List<String> fields, Instant since, Instant until, boolean help) {

    public static final String USAGE = """
            Kullanım: logflow --input <dosya> --output <dosya> [seçenekler]

              -i, --input <dosya>       Okunacak access log (Common Log Format)   [zorunlu]
              -o, --output <dosya>      Yazılacak çıktı dosyası (TSV)              [zorunlu]
                  --min-status <n>      Yalnızca status >= n olan kayıtlar
                  --path-prefix <önek>  Yalnızca path'i önekle başlayan kayıtlar
                  --fields <a,b,...>    Çıktıda tutulacak alanlar (sıra korunur):
                                        timestamp,clientIp,method,path,status,bytes
                  --since <an>          Yalnızca zaman damgası >= an (ISO-8601, ör. 2023-10-10T13:00:00Z)
                  --until <an>          Yalnızca zaman damgası <  an (ISO-8601)
              -h, --help                Bu yardımı göster
            """;

    public static CliOptions parse(String[] args) {
        Path in = null, out = null;
        Integer minStatus = null;
        String prefix = null;
        List<String> fields = null;
        Instant since = null, until = null;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("-h") || a.equals("--help")) {
                return new CliOptions(null, null, null, null, null, null, null, true);
            }
            String v = value(args, i, a);
            i++;
            switch (a) {
                case "-i", "--input" -> in = Path.of(v);
                case "-o", "--output" -> out = Path.of(v);
                case "--min-status" -> minStatus = parseStatus(v);
                case "--path-prefix" -> prefix = v;
                case "--fields" -> fields = Arrays.stream(v.split(",")).map(String::trim).toList();
                case "--since" -> since = parseInstant(a, v);
                case "--until" -> until = parseInstant(a, v);
                default -> throw new IllegalArgumentException("Bilinmeyen seçenek: " + a);
            }
        }
        if (in == null) throw new IllegalArgumentException("--input zorunlu");
        if (out == null) throw new IllegalArgumentException("--output zorunlu");
        return new CliOptions(in, out, minStatus, prefix, fields, since, until, false);
    }

    private static String value(String[] args, int i, String opt) {
        if (!opt.startsWith("-")) throw new IllegalArgumentException("Beklenmeyen argüman: " + opt);
        if (i + 1 >= args.length) throw new IllegalArgumentException(opt + " için değer eksik");
        return args[i + 1];
    }

    private static int parseStatus(String v) {
        try {
            int n = Integer.parseInt(v);
            if (n < 100 || n > 599) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--min-status 100-599 arası bir sayı olmalı: " + v);
        }
    }

    private static Instant parseInstant(String opt, String v) {
        try {
            return Instant.parse(v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(opt + " ISO-8601 anı olmalı (ör. 2023-10-10T13:00:00Z): " + v);
        }
    }
}
