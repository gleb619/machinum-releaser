package machinum;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;

import static java.net.http.HttpClient.newHttpClient;
import static java.net.http.HttpRequest.newBuilder;

public abstract class BaseTest {

    protected static HttpResponse<String> get(String uri) throws IOException, InterruptedException {
        var request = newBuilder()
                .uri(URI.create(uri))
                .GET()
                .setHeader("Accept", "*/*")
                .setHeader("Content-Type", "application/json")
                .build();

        return newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    /* Used in reflection. Do not touch */
    @SuppressWarnings({"unused"})
    public App createMockApplication() {
//        wireMock.start();
//        Awaitility.await().atMost(Duration.ofSeconds(60)).until(postgreSQL::isRunning);
//        Awaitility.await().atMost(Duration.ofSeconds(60)).until(wireMock::isRunning);

//        String jdbcUrl = postgreSQL.getJdbcUrl();
//        System.setProperty("db.url", jdbcUrl);
//        System.setProperty("clients", "test:http://localhost:%s/api".formatted(wireMock.getServerPort()));
//        System.setProperty("service.base-url", "http://localhost:%s".formatted(wireMock.getServerPort()));

        return new App();
    }

}
