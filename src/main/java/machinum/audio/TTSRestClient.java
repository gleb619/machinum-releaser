package machinum.audio;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jooby.StatusCode;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
public class TTSRestClient {

    private final String ttsServiceUrl;

    private final HttpClient httpClient;

    private final ObjectMapper objectMapper;

    public byte[] generate(@NonNull TTSRequest request) throws IOException, InterruptedException {
        log.debug("Generating audio from given text: {}", request);
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        addFormField(body, boundary, "text", request.getText());

        if (request.getVoice() != null && !request.getVoice().isEmpty()) {
            addFormField(body, boundary, "voice", request.getVoice());
        }

        if (request.getOutputFile() != null && !request.getOutputFile().isEmpty()) {
            addFormField(body, boundary, "output_file", request.getOutputFile());
        }

        if (request.getEnhance() != null) {
            addFormField(body, boundary, "enhance", request.getEnhance().toString());
            addFormField(body, boundary, "enhance_preset", "podcast");
        }

        if (request.getReturnZip() != null) {
            addFormField(body, boundary, "return_zip", request.getReturnZip().toString());
        }

        if (!Metadata.isEmpty(request.getMetadata())) {
            String metadataJson = objectMapper.writeValueAsString(request.getMetadata());
            addFormField(body, boundary, "metadata", metadataJson);
        }

        if(Objects.nonNull(request.getCoverArt()) && request.getCoverArt().length > 0) {
            addFilePart(body, boundary, "cover_art", "cover.jpg", request.getCoverArt(), "image/jpeg");
        }

        // Add the final boundary to signify the end of the request
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        URI uri = URI.create(ttsServiceUrl + "/api/tts");
        log.debug(">> POST {}", uri);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));

        HttpResponse<byte[]> response = null;
        try {
            response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } finally {
            if(Objects.nonNull(response)) {
                log.debug("<< POST {} {}", uri, response.statusCode());
            } else {
                log.debug("<< POST {} -1", uri);
            }
        }

        // Check for errors
        if (response.statusCode() != StatusCode.OK_CODE) {
            String errorBody = new String(response.body(), StandardCharsets.UTF_8);
            throw new IOException("TTS service failed with status %s: %s".formatted(response.statusCode(), errorBody));
        }

        log.debug("Generated audio for {}", request);

        return response.body();
    }

    @SneakyThrows
    public byte[] joinMp3Files(byte[] zipContent, String outputName, boolean enhance, byte[] coverArt,
                               Integer metadataFileIndex, Metadata metadata) {
        log.debug("Sending MP3 join request to TTS service");

        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        addFormField(body, boundary, "output_name", outputName);
        addFormField(body, boundary, "enhance", enhance + "");
        if(enhance) {
            addFormField(body, boundary, "enhance_preset", "podcast");
        }
        addFormField(body, boundary, "max_file_size", "100MB");
        addFormField(body, boundary, "add_silent_gaps", "true");
        addFormField(body, boundary, "metadata_file_index", String.valueOf(metadataFileIndex));

        if (!Metadata.isEmpty(metadata)) {
            String metadataJson = objectMapper.writeValueAsString(metadata);
            addFormField(body, boundary, "metadata", metadataJson);
        }

        if(Objects.nonNull(coverArt) && coverArt.length > 0) {
            addFilePart(body, boundary, "cover_art", "cover.jpg", coverArt, "image/jpeg");
        }
        if(Objects.nonNull(zipContent) && zipContent.length > 0) {
            addFilePart(body, boundary, "file", "audio.zip", zipContent, "application/zip");
        }

        // Add the final boundary to signify the end of the request
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        URI uri = URI.create(ttsServiceUrl + "/api/join");
        log.debug(">> POST {}", uri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpResponse<byte[]> response = null;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } finally {
            if(Objects.nonNull(response)) {
                log.debug("<< POST {} {}", uri, response.statusCode());
            } else {
                log.debug("<< POST {} -1", uri);
            }
        }

        if (response.statusCode() != StatusCode.OK_CODE) {
            String errorBody = new String(response.body(), StandardCharsets.UTF_8);
            throw new IOException("Failed to join MP3 files with status %s: %s".formatted(response.statusCode(), errorBody));
        }

        log.info("Successfully joined MP3 files");
        return response.body();
    }

    /* ============= */

    /**
     * Helper method to write a standard form field to the request body.
     * @param body The output stream for the request body.
     * @param boundary The multipart boundary string.
     * @param name The name of the form field.
     * @param value The value of the form field.
     */
    private void addFormField(ByteArrayOutputStream body, String boundary, String name, String value) throws IOException {
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Type: text/plain; charset=UTF-8\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(value.getBytes(StandardCharsets.UTF_8));
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Helper method to write a file part (e.g., an image) to the request body.
     * @param body The output stream for the request body.
     * @param boundary The multipart boundary string.
     * @param fieldName The form field name for the file.
     * @param fileName The name of the file.
     * @param fileBytes The raw byte data of the file.
     * @param contentType The MIME type of the file.
     */
    private void addFilePart(ByteArrayOutputStream body, String boundary, String fieldName, String fileName, byte[] fileBytes, String contentType) throws IOException {
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Type: " + contentType + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(fileBytes);
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @ToString(onlyExplicitlyIncluded = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class TTSRequest {

        private String text;
        private String voice;
        @ToString.Include
        private String outputFile;
        @ToString.Include
        private Boolean enhance;
        @ToString.Include
        private Boolean returnZip;
        public byte[] coverArt;
        private Metadata metadata;

    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @ToString(onlyExplicitlyIncluded = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class Metadata {

        @ToString.Include
        private String title;
        private String artist;
        private String album;
        private String year;
        private String genre;
        private String language;
        private String track;
        private String publisher;
        private String copyright;
        private String comments;

        public static boolean isEmpty(Metadata metadata) {
            return Objects.isNull(metadata) || Objects.isNull(metadata.getTitle()) || metadata.getTitle().isBlank();
        }

        public static Metadata createNew(Function<MetadataBuilder, MetadataBuilder> builderFn) {
            return builderFn.apply(builder()).build();
        }

    }

}
