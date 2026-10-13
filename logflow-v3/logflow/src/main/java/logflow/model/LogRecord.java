package logflow.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ayrıştırılmış tek bir erişim günlüğü kaydı. Projeksiyon sonrası bazı alanlar
 * "yok" olabilir; yok alana erişmek MissingFieldException fırlatır.
 * Filtre ve projeksiyon aynı türü ürettiği için aşamalar her sırada birleşebilir.
 */
public final class LogRecord {
    public static final List<String> ALL_FIELDS =
            List.of("timestamp", "clientIp", "method", "path", "status", "bytes");

    private final Instant timestamp;
    private final String clientIp, method, path;
    private final Integer status;
    private final Long bytes;
    private final List<String> columns;   // render() için sıra; yalnızca mevcut alanlar

    public LogRecord(Instant timestamp, String clientIp, String method, String path, int status, long bytes) {
        this(timestamp, clientIp, method, path, status, bytes, ALL_FIELDS);
    }

    private LogRecord(Instant ts, String ip, String m, String p, Integer st, Long b, List<String> cols) {
        this.timestamp = ts; this.clientIp = ip; this.method = m;
        this.path = p; this.status = st; this.bytes = b; this.columns = cols;
    }

    public Instant timestamp() { return require("timestamp", timestamp); }
    public String clientIp()   { return require("clientIp", clientIp); }
    public String method()     { return require("method", method); }
    public String path()       { return require("path", path); }
    public int status()        { return require("status", status); }
    public long bytes()        { return require("bytes", bytes); }

    public boolean has(String field) { return columns.contains(field); }

    /** Yalnızca verilen alanları (verilen sırada) tutan yeni kayıt döndürür; diğerleri bırakılır. */
    public LogRecord project(List<String> fields) {
        List<String> kept = new ArrayList<>();
        for (String f : fields) {
            if (!ALL_FIELDS.contains(f)) throw new IllegalArgumentException("Bilinmeyen alan: " + f);
            if (has(f) && !kept.contains(f)) kept.add(f);
        }
        return new LogRecord(kept.contains("timestamp") ? timestamp : null,
                kept.contains("clientIp") ? clientIp : null,
                kept.contains("method") ? method : null,
                kept.contains("path") ? path : null,
                kept.contains("status") ? status : null,
                kept.contains("bytes") ? bytes : null,
                List.copyOf(kept));
    }

    /** Mevcut alanları sekmeyle ayrılmış tek satır olarak yazar. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        for (String c : columns) {
            if (sb.length() > 0) sb.append('\t');
            sb.append(switch (c) {
                case "timestamp" -> timestamp.toString();
                case "clientIp" -> clientIp;
                case "method" -> method;
                case "path" -> path;
                case "status" -> status.toString();
                default -> bytes.toString();
            });
        }
        return sb.toString();
    }

    private <T> T require(String field, T value) {
        if (value == null) throw new MissingFieldException(field);
        return value;
    }

    @Override public boolean equals(Object o) {
        return o instanceof LogRecord r && columns.equals(r.columns) && render().equals(r.render());
    }
    @Override public int hashCode() { return Objects.hash(columns, render()); }
    @Override public String toString() { return "LogRecord[" + render().replace('\t', ' ') + "]"; }
}
