# LogFlow — Mimari (v3: Bileşim)

Mimari odak: **modülerlik, bileşilebilirlik, aşama (stage) düzeyinde tek sorumluluk.**

## 1. Genel yapı

```
Source<T> ──► Stage<T,U> ──► Stage<U,V> ──► … ──► Sink<V>
FileLineSource  ParserStage   PredicateFilterStage     FileSink
(String)        (LogRecord)   ProjectionStage          (dosya)
```

| Parça | Sorumluluk | Dosya |
|---|---|---|
| `Source<T>` | öğe üretir (`open → next* → close`) | `core/Source.java` |
| `Stage<I,O>` | **bir** girdi için 0..n çıktı üretir | `core/Stage.java` |
| `Sink<T>` | öğeyi tüketir (`open → accept* → close`) | `core/Sink.java` |
| `Pipeline` | zinciri çalıştırır, yaşam döngüsünü yönetir | `core/Pipeline.java` |
| `PipelineBuilder<O>` | akıcı (fluent) birleştirici | `core/PipelineBuilder.java` |
| `PredicateFilterStage` | koşulu sağlamayan kaydı eler | `stage/` (32 satır) |
| `ProjectionStage` | kaydı seçilen alanlara indirger | `stage/` (34 satır) |
| `FileSink` | satırları dosyaya yazar | `io/` (37 satır) |

Her yeni aşama 40 satırın altındadır ve yalnızca tek bir şey yapar.

### Filtre ile dönüştürücü aynı mimari öğedir
`Stage.process(girdi, çıktıKanalı)` imzası 0 çıktı (eleme), 1 çıktı (dönüşüm) ya da çok çıktı
(açma/flatMap) üretmeye izin verir. `PredicateFilterStage` “çıktı kanalını çağırma”, `ProjectionStage`
“değiştirilmiş kopyayı ilet” der. Pipeline ikisini ayırt etmez; ayırt etmesi gerekmez.

Bunu mümkün kılan ikinci karar: **hem filtre hem projeksiyon `LogRecord → LogRecord`** üretir.
Projeksiyon sonrası kayıt, kaldırılan alanlar “yok” olan bir `LogRecord`tur; böylece aşamalar
her sırada ve her sayıda bileşebilir.

## 2. Tür güvenli bileşim

`PipelineBuilder<O>` zincirin o anki çıktı türünü taşır:

```java
Pipeline p = PipelineBuilder.from(new FileLineSource(in))        // PipelineBuilder<String>
    .via(new ParserStage())                                      // PipelineBuilder<LogRecord>
    .via(new PredicateFilterStage(r -> r.status() >= 400))       // PipelineBuilder<LogRecord>
    .via(new ProjectionStage("timestamp","clientIp","status","path"))
    .to(new FileSink(out));
```

**Ayrıştırmadan önce filtrelemek mümkün değildir** ve bu bir çalışma zamanı kuralı değil,
*derleme hatasıdır*. `from(FileLineSource).via(new PredicateFilterStage(...))` gerçekten derlenmez
(`javac` ile doğrulandı):

```
error: method via in class PipelineBuilder<O> cannot be applied to given types;
  required: Stage<? super String,N>
  found:    PredicateFilterStage
  reason: ... PredicateFilterStage cannot be converted to Stage<? super String,N>
```

## 3. Yaşam döngüsü (lifecycle) — kancalar süs değildir

`Pipeline.run()` sırası:

```
açılış :  sink.open → stage₁.open … stageₙ.open → source.open
kapanış:  source.close → stageₙ.close … stage₁.close → sink.close     (try/finally, ters sıra)
```

* Sink **ilk açılır, son kapanır**: yukarıdaki aşamalar kapanırken sink hâlâ yazılabilir durumdadır.
* Kapanış `finally` içindedir: bir aşama istisna fırlatsa bile tüm kaynaklar serbest bırakılır; ikinci
  hatalar `addSuppressed` ile ilk hataya eklenir, kaybolmaz. (Test: `PipelineLifecycleTest`.)
* `FileSink` `BufferedWriter` kullanır. **`close()` çağrılmazsa arabellekteki çıktı diske hiç ulaşmaz.**
  Bu, `FileSinkTest.withoutCloseBufferedOutputIsLost` testinde somut olarak gösterilir: `accept()`
  sonrası, `close()` öncesi dosya boyutu **0 bayttır**. `close()` ayrıca idempotenttir.

## 4. Veri minimizasyonu (projeksiyon)

`ProjectionStage("timestamp","status","path")` çıktıya `clientIp` gibi kişisel verinin **hiç
yazılmamasını** sağlar (boş bırakmaz, kaldırır). Kaldırılmış bir alana sonradan erişmek sessizce
yanlış sonuç vermez; `MissingFieldException` fırlatır (bkz. §5.3). Böylece “filtre için gereken alan
projeksiyonla atılmış” hatası yüksek sesle yakalanır.

## 5. Sıralama deneyi (bileşilebilirliğin deneysel doğrulaması)

Yeniden üretmek için: `./build.sh experiment [satır=1000000] [tekrar=7]`
(kod: `experiment/OrderExperiment.java`, ham çıktı: `docs/experiment-results.txt`).

**Düzen.** Sabit tohumlu (42) sentetik Common Log Format verisi: 1.000.000 satır, ≈ 85,7 MB.
Dağılım: durumların ~%80’i 200, ~%9,0’ı ≥ 400; yolların ~%55’i `/api/...`. Her varyant 3 ısınma
turu + 7 ölçüm turu; **medyan** raporlanır. İki ölçüm modu vardır:

* **DOSYA modu:** gerçek `FileLineSource → ParserStage → … → FileSink` (okuma, ayrıştırma, yazma dahil).
* **BELLEK modu:** kayıtlar önceden ayrıştırılmış ve bellekten verilir; yalnızca *aşamaların* maliyeti
  ölçülür. Ayrıştırma toplam sürenin büyük kısmını yuttuğu için sıralama etkisini DOSYA modunda
  gürültüden ayırmak zordur; BELLEK modu bu yüzden eklenmiştir.

Her karşılaştırmada iki sıranın çıktısının **bayt bayt (SHA-256) / özet olarak aynı** olduğu
program tarafından doğrulanır; farklıysa deney hata koduyla biter.

### 5.1 İki filtre: `status >= 400` ve `path` koşulu

Sonuç **aynı** (49.709 kayıt, aynı özet). İş yükü ve süre **farklı**:

| Mod / koşul | Sıra | 2. filtrenin değerlendirme sayısı | Medyan ms |
|---|---|---:|---:|
| Dosya, `path startsWith /api` | status → path | 90.294 | 2029 |
| Dosya, `path startsWith /api` | path → status | 549.316 | 2108 |
| Dosya, regex (pahalı) | status → regex | 90.294 | 1796 |
| Dosya, regex (pahalı) | regex → status | 549.316 | 2107 |
| Bellek, `startsWith` | status → path | 90.294 | 76 |
| Bellek, `startsWith` | path → status | 549.316 | 86 |
| Bellek, regex (pahalı) | status → regex | 90.294 | 102 |
| Bellek, regex (pahalı) | regex → status | 549.316 | 171 |

Yorum:

* **Değerlendirme sayıları deterministiktir** ve asıl kanıttır: seçici (eleme oranı yüksek) filtre
  önce gelirse ikinci filtre 6 kat daha az kayıt görür (90 bin ↔ 549 bin).
* Bu azalmanın zaman kazancına dönüşmesi **koşulun maliyetine bağlıdır**. Ucuz `startsWith` ile
  bellek modunda fark ≈ %13; pahalı regex ile ≈ %68 (1,68×). DOSYA modunda ayrıştırma süreyi
  domine ettiğinden fark %4–17’ye düşer.
* Kural: **ucuz ve seçici koşulu öne al.** Maliyet ve seçicilik biliniyorsa en iyi sıra hesaplanabilir
  (sıralama: maliyet / (1 − geçirme oranı) küçük olan önce). Bu, ileride yapılandırmadan/optimizerden
  otomatik yapılabilecek bir işlemdir; mimari bunu zaten mümkün kılar çünkü aşamalar sıra-bağımsız bileşir.

### 5.2 Projeksiyon filtreden önce mi, sonra mı? (filtrenin alanı korunuyorsa)

Çıktı aynı (90.294 kayıt). Süre:

| Mod | filtre → projeksiyon | projeksiyon → filtre | oran |
|---|---:|---:|---:|
| Dosya | 1846 ms | 2087 ms | 1,13× |
| Bellek | 98 ms | 196 ms | 2,00× |

Projeksiyon her kaydı yeniden oluşturur (yeni nesne). Önce filtrelenirse projeksiyon yalnızca
geçen ~%9 kayıt için çalışır; önce projeksiyon yapılırsa 1.000.000 kayıt için çalışır. **Önce filtrele,
sonra projeksiyon** hem daha hızlıdır hem de daha güvenlidir (§5.3).

### 5.3 Ödünleşim: projeksiyon önce gelirse doğruluk riski

Projeksiyon filtrenin ihtiyaç duyduğu alanı atarsa filtre çalışamaz. Örnek:
`ProjectionStage("path")` ardından `status >= 500` filtresi. Bu mimaride sonuç sessizce boş değil,
**yüksek sesli hatadır** (`MissingFieldException: Alan projeksiyonla kaldırılmış: status`; test:
`PipelineIntegrationTest.projectionBeforeFilterFailsLoudlyWhenFilterFieldIsDropped`). Filtre alanı
korunuyorsa (`ProjectionStage("status","path")`) iki sıra aynı çıktıyı verir
(`projectionBeforeFilterWorksWhenFilterFieldIsKept`).

### 5.4 Sonuç

| Soru | Cevap |
|---|---|
| Aşamalar her sırada/sayıda bileşir mi? | Evet; tür sistemi izin verdiği her sıra çalışır (`sameStageInstancesComposeInAnyOrderAndCount`). |
| Filtre sırası sonucu değiştirir mi? | Hayır, yan etkisiz saf koşullar için çıktı özdeştir (3 deney × 2 mod). |
| Performansı değiştirir mi? | Evet: kazanç = elenen kayıt oranı × sonraki aşamanın maliyeti. |
| Ayrıştırmadan önce filtre? | Mümkün değil: derleme hatası (§2). |
| Projeksiyon önce mi sonra mı? | Gerçek ödünleşim: hız için sonra, ama filtre alanları atılmamalı; önce yapılırsa hem yavaş hem riskli. |

**Ölçüm uyarıları (dürüstlük notu).** Tek makinede (2 işlemci, Java 21, JIT ısınması sonrası medyan)
tek koşu; mutlak süreler makineye göre değişir, oranlar ve özellikle değerlendirme sayıları
taşınabilir sonuçlardır. DOSYA modundaki küçük farklar (%4–8) koşular arası gürültü içinde kalabilir;
bu yüzden iddia, gürültüden bağımsız olan değerlendirme sayıları ve BELLEK modu üzerine kuruludur.
`Pipeline` tek iş parçacıklıdır; koşullar durumsuz (saf) olmalıdır — yan etkili koşullarda sıra
sonucu değiştirebilir.

## 6. Komut satırı

```
logflow --input access.log --output out.tsv --min-status 400 --path-prefix /api --fields timestamp,clientIp,status
```

`Main.build()` argümanlardan pipeline’ı kurar: *parse → [status] → [path] → [zaman] → [projeksiyon] → yaz*
(filtre önce, projeksiyon sonra: §5.2’deki en hızlı ve güvenli sıra). 4. Hafta’da bu sabit kurulumun
yerini yapılandırma dosyası alacaktır; `PipelineBuilder` aynı kalır, yalnızca onu çağıran kod değişir.
Çıkış kodları: 0 başarı, 1 G/Ç hatası, 2 kullanım hatası. Girdi ve çıktı aynı dosya ise çalışmayı reddeder
(aksi halde girdi `FileSink.open()` ile sıfırlanırdı).

## 7. Bilinen sınırlar ve varsayımlar

* 1. ve 2. artımın kodu bu depoya verilmediği için `LogRecord`, `Source/Stage/Sink`, `ParserStage`,
  `FileLineSource` ve `Pipeline` bu artımın ihtiyacına göre yeniden yazılmıştır. Log biçimi
  **Apache/Nginx Common Log Format** olarak varsayılmıştır. Farklıysa yalnızca `ParserStage` değişir.
* Bozuk satırlar atlanır ve sayılır (`ParserStage.malformedCount()`), çalışmayı durdurmaz.
* Çıktı biçimi: UTF-8, sekmeyle ayrılmış (TSV), başlık satırı yok, zaman damgası UTC ISO-8601.
