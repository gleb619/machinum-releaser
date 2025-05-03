package machinum.book;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.cache.CacheService;
import machinum.chapter.Chapter;
import machinum.chapter.ChapterJsonlConverter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class BookRestClient {

    private final HttpClient httpClient;
    private final ChapterJsonlConverter chapterJsonlConverter;
    private final CacheService cache;
    private final String baseUrl;


    public Map<String, String> getAllBookTitlesCached() {
        return cache.get("titles", this::getAllBookTitles);
    }

    public Map<String, String> getAllBookTitles() {
        return getBookTitles(0, 10_000);
    }

    @SneakyThrows
    public Map<String, String> getBookTitles(int page, int size) {
        StringBuilder urlBuilder = new StringBuilder(baseUrl).append("/api/books/titles");

        if (page >= 0 && size >= 1) {
            urlBuilder.append("?page=").append(page).append("&size=").append(size);
        }

        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(urlBuilder.toString()))
                .header("Content-Type", "application/json")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 204) {
            return Collections.emptyMap();
        } else if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch book titles: " + response.statusCode());
        }

        String jsonList = response.body();

        return chapterJsonlConverter.getObjectMapper().readValue(jsonList, Map.class);
    }

    public List<Chapter> getReadyChaptersCached(String id, Integer from, Integer to) {
        return cache.get("chapters", () -> getReadyChapters(id, from, to));
    }

    @SneakyThrows
    public List<Chapter> getReadyChapters(String id, Integer from, Integer to) {
        StringBuilder urlBuilder = new StringBuilder(baseUrl).append("/api/books/").append(id).append("/chapters/ready");

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
            throw new IllegalStateException("Can't get book's chapters");
        }

        return chapterJsonlConverter.fromString(response.body());
    }

}