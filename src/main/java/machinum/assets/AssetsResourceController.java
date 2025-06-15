package machinum.assets;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jooby.*;
import io.jooby.annotation.GET;
import io.jooby.annotation.PathParam;
import io.jooby.annotation.QueryParam;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppException;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@io.jooby.annotation.Path("/assets")
@RequiredArgsConstructor
public class AssetsResourceController {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final ConcurrentHashMap<String, String> cachedResourceMetadata;
    private final Path cachePath;
    private final Path metadataFilePath;


    public static AssetsResourceController_ assetsController(Jooby jooby) {
        String cacheDirectory = jooby.getConfig().getString("assets.cache.folder");
        String metadataFile = jooby.getConfig().getString("assets.cache.metadata-file");

        var cachePath = Paths.get(cacheDirectory).toAbsolutePath();
        var metadataFilePath = Paths.get(metadataFile).toAbsolutePath();
        var httpClient = jooby.require(ServiceKey.key(HttpClient.class, "assets"));

        try {
            Files.createDirectories(cachePath);
            log.debug("Dynamic cache directory created/ensured at: {}", cachePath);
            var metadata = loadMetadata(metadataFilePath);
            return new AssetsResourceController_(httpClient, metadata, cachePath, metadataFilePath);
        } catch (IOException e) {
            log.error("Could not create cache directory or load metadata: {}", cacheDirectory, e);
            throw new AppException("Could not initialize cache", e);
        }
    }

    @GET("/{type}/{filename}")
    public Context getCachedResource(@PathParam("type") String type,
                                  @PathParam("filename") String filename,
                                  @QueryParam("url") String url,
                                  Context ctx) {
        String decodedUrl;
        try {
            decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decode URL: {}", url, e);
            return ctx.send(StatusCode.BAD_REQUEST);
        }

        Path typeDir = cachePath.resolve(type);
        try {
            Files.createDirectories(typeDir);
        } catch (IOException e) {
            log.error("Could not create type directory in cache: {}", typeDir, e);
            return ctx.send(StatusCode.SERVER_ERROR);
        }

        Path localFilePath = typeDir.resolve(filename);
        String metadataKey = type + "/" + filename;

        if (!Files.exists(localFilePath)) {
            log.debug("Cache miss for: {}. Attempting to download from: {}", localFilePath, decodedUrl);
            boolean downloaded = downloadAndCacheResource(decodedUrl, type, filename);
            if (!downloaded) {
                return ctx.send(StatusCode.NOT_FOUND);
            }
        } else {
            log.trace("Cache hit for: {}", localFilePath);
        }

        // Always ensure metadata contains the URL for refresh purposes
        if (!decodedUrl.equals(cachedResourceMetadata.get(metadataKey))) {
            cachedResourceMetadata.put(metadataKey, decodedUrl);
            saveMetadata();
        }

        try {
            byte[] fileContent = Files.readAllBytes(localFilePath);
            String contentType = determineContentType(filename);

            return ctx.setResponseHeader("Content-Type", contentType)
                    .setResponseHeader("Cache-Control", "public, max-age=604800") // 7 days
                    .send(fileContent);
        } catch (IOException e) {
            log.error("Failed to read cached file: {}", localFilePath, e);
            return ctx.send(StatusCode.SERVER_ERROR);
        }
    }

    private boolean downloadAndCacheResource(String remoteUrl, String type, String filename) {
        try {
            log.debug("Downloading: {} to cache as {}/{}", remoteUrl, type, filename);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(remoteUrl))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                byte[] fileContent = response.body();
                if (fileContent != null) {
                    Path typePath = cachePath.resolve(type);
                    Files.createDirectories(typePath);
                    Path filePath = typePath.resolve(filename);
                    Files.write(filePath, fileContent,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.SYNC);
                    log.debug("Successfully downloaded and cached: {} to {}", remoteUrl, filePath);

                    String metadataKey = type + "/" + filename;
                    cachedResourceMetadata.put(metadataKey, remoteUrl);
                    saveMetadata();
                    return true;
                } else {
                    log.warn("Failed to download {}: content was null", remoteUrl);
                    return false;
                }
            } else {
                log.warn("Failed to download {}: HTTP status {}", remoteUrl, response.statusCode());
                return false;
            }
        } catch (Exception e) {
            String message = "Error downloading resource %s for %s/%s: %s"
                    .formatted(remoteUrl, type, filename, e.getMessage());
            if (log.isTraceEnabled()) {
                log.error(message, e);
            } else {
                log.error(message);
            }
            return false;
        }
    }

    protected static ConcurrentHashMap<String, String> loadMetadata(Path metadataFilePath) {
        ObjectMapper objectMapper = new ObjectMapper();
        var cachedResourceMetadata = new ConcurrentHashMap<String, String>();
        if (Files.exists(metadataFilePath)) {
            try {
                var loadedMap = objectMapper.readValue(
                        metadataFilePath.toFile(),
                        new TypeReference<Map<String, String>>() {});
                cachedResourceMetadata.putAll(loadedMap);
                log.debug("Loaded {} entries from cache metadata file: {}",
                        cachedResourceMetadata.size(), metadataFilePath);
                return cachedResourceMetadata;
            } catch (IOException e) {
                log.error("Failed to load cache metadata from {}: {}", metadataFilePath, e.getMessage());
            }
        } else {
            log.debug("Cache metadata file not found, starting with empty metadata: {}", metadataFilePath);
        }

        return cachedResourceMetadata;
    }

    @Synchronized
    private void saveMetadata() {
        try {
            Files.createDirectories(metadataFilePath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(metadataFilePath.toFile(), new TreeMap<>(cachedResourceMetadata));
            log.trace("Cache metadata saved to: {}", metadataFilePath);
        } catch (IOException e) {
            log.error("Failed to save cache metadata to {}: {}", metadataFilePath, e.getMessage());
        }
    }

    private String determineContentType(String filename) {
        if (filename.endsWith(".css")) {
            return MediaType.css.getValue();
        } else if (filename.endsWith(".js") || filename.endsWith(".mjs")) {
            return MediaType.js.getValue();
        } else if (filename.endsWith(".json")) {
            return MediaType.json.getValue();
        } else if (filename.endsWith(".png")) {
            return MediaType.byFileExtension("png").getValue();
        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return MediaType.byFileExtension("jpg").getValue();
        } else if (filename.endsWith(".gif")) {
            return MediaType.byFileExtension("gif").getValue();
        }
        return MediaType.octetStream.getValue();
    }
}
