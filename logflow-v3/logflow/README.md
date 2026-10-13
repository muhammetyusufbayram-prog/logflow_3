# LogFlow Pipeline — v3 (Bileşim)

Komut satırı log filtresi: **oku → ayrıştır → filtrele → projeksiyon → yaz.**
Mimari ve sıralama deneyi: [ARCHITECTURE.md](ARCHITECTURE.md).

## Gereksinim
Java 21+. Maven **ya da** yalnızca JDK (bkz. `build.sh`; JUnit jar’ları `lib/` içinde).

## Derle ve test et
```
./build.sh            # derle + 44+ testi çalıştır
mvn test              # Maven ile (JUnit 4.13.2, internet gerekir)
./build.sh jar        # target/logflow.jar
./build.sh experiment # sıralama deneyi (1M satır)
```

## Kullan
```
java -jar target/logflow.jar --input access.log --output out.tsv \
     --min-status 400 --path-prefix /api --fields timestamp,clientIp,status
java -jar target/logflow.jar --help
```
Seçenekler: `--min-status`, `--path-prefix`, `--fields`, ayrıca `--since/--until` (ISO-8601).
Alanlar: `timestamp, clientIp, method, path, status, bytes`.

## Kod olarak
```java
Pipeline p = PipelineBuilder.from(new FileLineSource(in))
    .via(new ParserStage())
    .via(new PredicateFilterStage(r -> r.status() >= 400))
    .via(new ProjectionStage("timestamp","clientIp","status","path"))
    .to(new FileSink(out));
p.run();
```
