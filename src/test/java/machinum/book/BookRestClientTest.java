package machinum.book;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import machinum.cache.CacheService;
import machinum.chapter.Chapter;
import machinum.chapter.ChapterJsonlConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.http.HttpClient;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookRestClientTest {

    @RegisterExtension
    public static WireMockExtension wireMockExtension = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    ChapterJsonlConverter converter;

    BookRestClient client;

    CacheService cacheService;

    @BeforeEach
    public void setup() {
        converter = new ChapterJsonlConverter(new ObjectMapper().findAndRegisterModules());
        cacheService = new CacheService(Duration.ofMillis(1));

        // Initialize the REST client with the base URL of WireMock server
        String baseUrl = "http://localhost:" + wireMockExtension.getPort() + "/api/books";
        client = new BookRestClient(HttpClient.newBuilder().build(), converter, cacheService, baseUrl);

        // Define expected response from the server
        String jsonResponse = """
                {"number": 1, "translatedTitle":"Chapter 1","translatedText":"Body 1"}
                {"number": 2, "translatedTitle":"Chapter 2","translatedText":"Body 2"}
                """;

        // Stub WireMock to return this response when requested
        wireMockExtension.stubFor(WireMock.get(WireMock.urlEqualTo("/api/books/someBookId/ready?from=1&to=5"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));
    }

    @Test
    void testMain() throws Exception {
        // Example usage
        String id = "someBookId";
        Integer from = 1;
        Integer to = 5;

        var response = client.getReadyChapters(id, from, to);

        assertThat(response)
                .isNotNull()
                .isNotEmpty()
                .containsExactly(
                        Chapter.builder()
                                .number(1)
                                .translatedTitle("Chapter 1")
                                .translatedText("Body 1")
                                .build(),
                        Chapter.builder()
                                .number(2)
                                .translatedTitle("Chapter 2")
                                .translatedText("Body 2")
                                .build()
                );
    }

    @Test
    void testFail() throws Exception {
        // Example usage
        String id = "wrongBookId";
        Integer from = 1;
        Integer to = null;

        assertThatThrownBy(() -> {
            client.getReadyChapters(id, from, to);
        })
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Can't get book's chapters");
    }

}
