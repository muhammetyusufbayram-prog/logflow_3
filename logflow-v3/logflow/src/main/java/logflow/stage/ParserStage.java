package logflow.stage;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import logflow.core.Stage;
import logflow.model.LogRecord;

/** Common Log Format satırını LogRecord'a çevirir. Bozuk satırları atlar ve sayar. */
public final class ParserStage implements Stage<String, LogRecord> {
    private static final Pattern CLF = Pattern.compile(
            "^(\\S+) \\S+ \\S+ \\[([^\\]]+)\\] \"(\\S+) (\\S+)[^\"]*\" (\\d{3}) (\\d+|-).*$");
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

    private long malformed;

    @Override
    public void process(String line, Consumer<? super LogRecord> out) {
        Matcher m = CLF.matcher(line);
        if (!m.matches()) { malformed++; return; }
        try {
            Instant ts = OffsetDateTime.parse(m.group(2), TS).toInstant();
            long bytes = m.group(6).equals("-") ? 0 : Long.parseLong(m.group(6));
            out.accept(new LogRecord(ts, m.group(1), m.group(3), m.group(4),
                    Integer.parseInt(m.group(5)), bytes));
        } catch (DateTimeParseException | NumberFormatException e) {
            malformed++;
        }
    }

    public long malformedCount() { return malformed; }
}
