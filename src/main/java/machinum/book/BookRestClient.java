package machinum.book;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.cache.CacheService;
import machinum.chapter.Chapter;
import machinum.chapter.ChapterJsonlConverter;
import machinum.exception.AppException;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class BookRestClient {

    private final HttpClient httpClient;
    private final ChapterJsonlConverter chapterJsonlConverter;
    private final CacheService inMemoryCache;
    private final String baseUrl;


    public List<BookExportResult> getAllBookTitlesCached() {
        return inMemoryCache.get("titles", this::getAllBookTitles);
    }

    public List<BookExportResult> getAllBookTitles() {
        return getBookTitles(0, 10_000);
    }

    @SneakyThrows
    public List<BookExportResult> getBookTitles(int page, int size) {
        var urlBuilder = new StringBuilder(baseUrl).append("/api/books/titles");

        if (page >= 0 && size >= 1) {
            urlBuilder.append("?page=").append(page).append("&size=").append(size);
        }

        var targetUrl = urlBuilder.toString();

        log.info(">> {}", targetUrl);

        try {
            var response = httpClient.send(HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString());


            if (response.statusCode() == 204) {
                log.info("<< {} {}", targetUrl, response.statusCode());
                return Collections.emptyList();
            } else if (response.statusCode() != 200) {
                throw new AppException("Failed to fetch book titles: " + response.statusCode(), Map.of(
                        "response", response,
                        "status", response.statusCode()
                ));
            }

            var jsonList = response.body();

            log.info("<< {} {}", targetUrl, response.statusCode());

            return List.of(chapterJsonlConverter.getObjectMapper().readValue(jsonList, BookExportResult[].class));
        } catch (Exception e) {
            if (e instanceof ConnectException ce) {
                log.error("<X Server is not reachable: {}", ce.getMessage());
            } else if (e instanceof AppException ae) {
                log.error("<< %s %s: ".formatted(targetUrl, ae.getMetadata().getOrDefault("status", 500)), e);
            } else {
                log.error("<< %s %s: ".formatted(targetUrl, -1), e);
            }

            return Collections.emptyList();
        }
    }

    public List<Chapter> getReadyChaptersCached(String id, Integer from, Integer to) {
        return inMemoryCache.get("chapters_%s_%s_%s".formatted(id, from, to), () -> getReadyChapters(id, from, to));
    }

    public byte[] getAudioCached(String id, Integer from, Integer to, byte[] coverArt) {
        return inMemoryCache.get("audio_%s_%s_%s".formatted(id, from, to), () -> getAudio(id, from, to, coverArt));
    }

    @SneakyThrows
    public List<Chapter> getReadyChapters(String id, Integer from, Integer to) {
        var urlBuilder = new StringBuilder(baseUrl).append("/api/books/").append(id).append("/chapters/ready");

        if (from != null && to != null) {
            urlBuilder.append("?from=").append(from).append("&to=").append(to);
        }

        log.debug("Send request to get chapters: bookId={}", id);

        var response = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(urlBuilder.toString()))
                .header("Content-Type", "application/json")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Can't get book's chapters: bookId={}\n{}", id, response.body());
            throw new AppException("Can't get book's chapters");
        }

        return chapterJsonlConverter.fromString(response.body());
    }

    @SneakyThrows
    public byte[] getAudio(String bookId, Integer from, Integer to, byte[] coverArt) {
        var urlBuilder = "%s/api/books/%s/audio?from=%d&to=%d".formatted(baseUrl, bookId, from, to);

        log.debug("Send request to get audio: bookId={}", bookId);

        var response = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(urlBuilder))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(coverArt))
                .build(), HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            log.error("Can't get book's audio: bookId={}\n{}", bookId, new String(response.body(), StandardCharsets.UTF_8));
            throw new AppException("Can't get book's audio");
        }

        return response.body();
    }

    public record BookExportResult(String id, String title, Integer chaptersCount) {
    }

}