package machinum.novel;

import lombok.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.net.URLEncoder.encode;
import static machinum.util.CheckedSupplier.checked;

@Slf4j
@RequiredArgsConstructor
@Deprecated(forRemoval = true)
public class NovelScraper {

    private final ScraperConfig config;
    private final Parser<Novel> parser;
    private final RateLimiter rateLimiter;
    private final ScraperCache scraperCache;

    public static NovelScraper forBook(ScraperConfig config) {
        return new NovelScraper(config, new NovelParser(),
                RateLimiter.of(1, 1000), //1req in 1sec
                ScraperCache.of(Duration.ofMinutes(10).toMillis()));
    }

    public static NovelScraper forBook(Function<ScraperConfig.ScraperConfigBuilder, ScraperConfig.ScraperConfigBuilder> creator) {
        return forBook(creator.apply(ScraperConfig.builder()).build());
    }

    public static NovelScraper forBook(String bookId) {
        return forUrl("https://www.novelupdates.com/series/%s/".formatted(bookId));
    }

    public static NovelScraper forUrl(String url) {
        return forBook(ScraperConfig.builder()
                .novelUrl(url)
                .build());
    }

    public Optional<Novel> scrape() {
        try {
            return Optional.of(performScrape());
        } catch (ScrapeException e) {
            if(log.isDebugEnabled()) {
                log.error("Can't scrape site: ", e);
            } else {
                log.error("Can't scrape site: {}", e.getMessage());
            }
            return Optional.empty();
        }
    }

    public CompletableFuture<Optional<Novel>> scrapeAsync() {
        return CompletableFuture.supplyAsync(this::scrape);
    }

    @SneakyThrows
    public Novel performScrape() throws ScrapeException {
        log.debug("Prepare to scape external site");
        int attempts = 0;
        Throwable throwable = null;

        while (attempts < config.getMaxRetries()) {
            try {
                if(config.getDelayMs() > 0) {
                    Thread.sleep(config.getDelayMs());
                }

                log.debug("Connecting to {}", config.getNovelUrl());

                var cookies = prepareCookies();
                var doc = makeRequesst(cookies).get();

                log.debug("Got result, preparing to parse page");

                var novel = parseNovel(config.getNovelUrl(), doc);

                log.debug("Success parsing from {}", config.getNovelUrl());
                return novel;
            } catch (Exception e) {
                if(Objects.isNull(throwable)) {
                    throwable = e;
                }
                attempts++;
            }
        }

        throw new ScrapeException("Failed to scrape after %d attempts".formatted(config.getMaxRetries()), throwable);
    }

    private Map<String, String> prepareCookies() throws IOException {
        if(config.isAbsorbCookie()) {
            return makeRequesst(Map.of()).execute().cookies();
        } else {
            return new HashMap<>();
        }
    }

    private Connection makeRequesst(Map<String, String> cookies) throws IOException {
        return Jsoup.connect(config.getNovelUrl())
                .timeout(config.getTimeout())
                .method(Connection.Method.GET)
                .followRedirects(true)
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .cookies(cookies);
    }

    private Novel parseNovel(String key, Document doc) throws ScrapeException {
        try {
            return scraperCache.get(key, checked(() -> {
                rateLimiter.acquire();
                var novel = parser.parse(doc);

                log.debug("Parsed document into dto: {}", novel);
                return novel;
            }));
        } catch (Exception e) {
            throw new ScrapeException("Failed to parse novel data", e);
        }
    }

    // Search functionality
    public static List<SearchResult> search(String query) throws ScrapeException {
        return search(query, ScraperConfig.builder().build());
    }

    public static List<SearchResult> search(String query, ScraperConfig config) throws ScrapeException {
        try {
            var searchUrl = "https://www.novelupdates.com/?s=%s&post_type=seriesplans".formatted(
                    encode(query, StandardCharsets.UTF_8));

            var doc = Jsoup.connect(searchUrl)
                    .timeout(config.getTimeout())
                    .get();

            return doc.select(".search_main_box_nu")
                    .stream()
                    .map(NovelScraper::parseSearchResult)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new ScrapeException("Search failed", e);
        }
    }

    //TODO redo to Parser<SearchResult>
    private static SearchResult parseSearchResult(Element element) {
        try {
            var title = element.select(".search_title a").text();
            var url = element.select(".search_title a").attr("href");
            var description = element.select(".search_body_nu").text();

            return SearchResult.builder()
                    .title(title)
                    .url(url)
                    .description(description)
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @ToString(onlyExplicitlyIncluded = true)
    public static class Novel {

        @ToString.Include
        String title;
        String altTitle;
        String novelType;
        String author;
        Double rating;
        String status;
        String description;
        List<String> genres;
        List<String> tags;
        @EqualsAndHashCode.Exclude
        List<NovelChapter> novelChapters;
        String year;
        Integer chaptersCount;
        String coverUrl;
        
    }

    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    public static class NovelChapter {

        String title;
        String url;
        LocalDate releaseDate;
        String groupName;

    }

    @Value
    @With
    @AllArgsConstructor
    @Builder(toBuilder = true)
    public static class ScraperConfig {

        String novelUrl;
        @Builder.Default
        int timeout = 5000;
        @Builder.Default
        int maxRetries = 3;
        @Builder.Default
        long delayMs = 1000;
        //Will start another jsoup session, load cookies from old, and set into new one
        @Builder.Default
        boolean absorbCookie = Boolean.TRUE;

    }

    public static class ScrapeException extends Exception {

        public ScrapeException(String message, Throwable cause) {
            super(message, cause);
        }

        public ScrapeException(String message) {
            super(message);
        }

    }

    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    public static class SearchResult {

        String title;
        String url;
        String description;

    }

    @RequiredArgsConstructor
    public static class RateLimiter {

        private final Semaphore semaphore;
        private final long intervalMs;

        public static RateLimiter of(int permits, long intervalMs) {
            return new RateLimiter(new Semaphore(permits), intervalMs);
        }

        public void acquire() throws InterruptedException {
            try {
                semaphore.acquire();
                Thread.sleep(intervalMs);
            } finally {
                semaphore.release();
            }
        }

    }

    @FunctionalInterface
    public interface Parser<T> {

        T parse(Document document) throws ScrapeException;

    }

    public static class NovelParser implements Parser<Novel> {

        @Override
        public Novel parse(Document doc) {
            log.debug("Prepare to parse document into dto: {}", doc.title());
            var title = doc.select(".seriestitlenu").first().text();
            var author = doc.select("#showauthors a").first().text();
            var description = doc.select("#editdescription p").text();
            var status = doc.select("#editstatus").text();
            var coverUrl = doc.select(".seriesimg img").attr("src");
            var originNames = doc.select("#editassociated");
            var associatedName = originNames.stream().filter((el) -> el.text().matches("[!A-z0-9-_]")).findFirst()
                    .orElse(originNames.first()).text();
            var novelType = doc.select("#showtype > a").text();
            var year = doc.select("#edityear").text();
            int chapters = Arrays.stream(doc.select("#editstatus").text().replaceAll("[^\\d+]", "").trim().split("\s"))
                    .mapToInt(Integer::parseInt)
                    .sum();

            var rating = parseRating(doc);
            var genres = parseGenres(doc);
            var tags = parseTags(doc);
            var novelChapters = parseChapters(doc);

            return Novel.builder()
                    .title(title)
                    .altTitle(associatedName)
                    .novelType(novelType)
                    .author(author)
                    .description(description)
                    .status(status)
                    .rating(rating)
                    .genres(genres)
                    .tags(tags)
                    .novelChapters(novelChapters)
                    .year(year)
                    .chaptersCount(chapters)
                    .coverUrl(coverUrl)
                    .build();
        }

        private Double parseRating(Document doc) {
            try {
                var ratingText = doc.select(".uvotes").text();
                return Double.parseDouble(ratingText.replaceAll("[^0-9.]", ""));
            } catch (Exception e) {
                return null;
            }
        }

        private List<String> parseGenres(Document doc) {
            return doc.select("#seriesgenre a")
                    .stream()
                    .map(Element::text)
                    .collect(Collectors.toList());
        }

        private List<String> parseTags(Document doc) {
            return doc.select("#showtags a")
                    .stream()
                    .map(Element::text)
                    .collect(Collectors.toList());
        }

        private List<NovelChapter> parseChapters(Document doc) {
            return doc.select("table#myTable tr")
                    .stream()
                    .skip(1) // Skip header
                    .map(this::parseChapter)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        private NovelChapter parseChapter(Element row) {
            try {
                var cells = row.select("td");
                if (cells.size() < 3) return null;

                var chapterTitle = cells.get(0).select("a").text();
                var chapterUrl = cells.get(0).select("a").attr("href");
                var groupName = cells.get(1).text();
                var dateText = cells.get(2).text();
                var releaseDate = parseDate(dateText);

                return NovelChapter.builder()
                        .title(chapterTitle)
                        .url(chapterUrl)
                        .groupName(groupName)
                        .releaseDate(releaseDate)
                        .build();
            } catch (Exception e) {
                return null;
            }
        }

        private LocalDate parseDate(String dateText) {
            try {
                var formatter = DateTimeFormatter.ofPattern("MM/dd/yy");
                return LocalDate.parse(dateText, formatter);
            } catch (Exception e) {
                return null;
            }
        }

    }

    @RequiredArgsConstructor
    public static class ScraperCache implements AutoCloseable {

        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        private final Map<String, Novel> cache;

        public static ScraperCache of(long ttlMs) {
            return new ScraperCache(new ConcurrentHashMap<>())
                    .scheduleCleanup(ttlMs, TimeUnit.MILLISECONDS);
        }

        public Novel get(String key, Supplier<Novel> supplier) {
            return cache.computeIfAbsent(key, k -> supplier.get());
        }

        public void invalidate(String key) {
            cache.remove(key);
        }

        public ScraperCache scheduleCleanup(long period, TimeUnit timeUnit) {
            scheduler.scheduleAtFixedRate(() -> cache.forEach((s, novel) -> invalidate(s)), period, period, timeUnit);
            return this;
        }

        @Override
        public void close() {
            scheduler.shutdown();
        }

    }

    @RequiredArgsConstructor
    public static class ImageDownloader {

        private final HttpClient httpClient;

        public static ImageDownloader defaultOne() {
            return of(HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build());
        }

        public static ImageDownloader of(HttpClient httpClient) {
            return new ImageDownloader(httpClient);
        }

        public Optional<ImageData> fetch(String imageUrl) {
            try {
                return Optional.ofNullable(fetchImage(imageUrl));
            } catch (Exception e) {
                log.error("IMAGE ERROR: ", e);
                return Optional.empty();
            }
        }

        public ImageData fetchImage(String imageUrl) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            byte[] imageBytes = response.body();

            return new ImageData(imageBytes, contentType);
        }

    }

    @Value
    public static class ImageData {

        byte[] bytes;

        String contentType;

        public String toBase64() {
            String base64Image = Base64.getEncoder().encodeToString(bytes);
            return "data:%s;base64,%s".formatted(contentType, base64Image);
        }

    }


}
