package machinum.pandoc;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.cache.CacheService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * A REST client for the Markdown to EPUB Converter API
 * Uses Java 11+ HTTP Client (JEP 321)
 */
@Slf4j
@RequiredArgsConstructor
public class PandocRestClient {

    private final HttpClient httpClient;
    private final CacheService cache;
    private final String baseUrl;


    @SneakyThrows
    public byte[] convertToEpubCached(PandocRequest pandocRequest) {
        return cache.get("epub-" + pandocRequest.getOutputFilename(), () ->
                convertToEpub(pandocRequest.getStartIndex(), pandocRequest.getMarkdownFiles(),
                        pandocRequest.getTitle(), pandocRequest.getAuthor(),
                        pandocRequest.getCoverImage(), pandocRequest.getCoverContentType(),
                        pandocRequest.getTocDepth(), pandocRequest.getOutputFilename()));
    }

    /**
     * Converts markdown files to EPUB format
     *
     * @param startIndex     Chapter number
     * @param markdownFiles  List of markdown file paths to convert
     * @param title          Optional title for the EPUB
     * @param author         Optional author for the EPUB
     * @param coverImage     Optional path to cover image
     * @param tocDepth       Table of contents depth (default is 2)
     * @param outputFilename Desired filename for the output EPUB
     * @return byte array if conversion was successful, empty array otherwise
     * @throws IOException          If an I/O error occurs
     * @throws InterruptedException If the operation is interrupted
     */
    @SneakyThrows
    public byte[] convertToEpub(
            Integer startIndex,
            List<byte[]> markdownFiles,
            String title,
            String author,
            byte[] coverImage,
            String coverContentType,
            int tocDepth,
            String outputFilename) {

        // Validate input
        if (markdownFiles == null || markdownFiles.isEmpty()) {
            throw new IllegalArgumentException("No markdown files provided");
        }

        if (coverImage == null || coverImage.length <= 0) {
            throw new IllegalArgumentException("Cover image does not exist");
        }

        // Create a unique boundary for multipart form data
        String boundary = UUID.randomUUID().toString();

        // Build the multipart request body
        byte[] requestBody = buildMultipartBody(
                boundary,
                startIndex,
                markdownFiles,
                coverImage,
                coverContentType,
                title,
                author,
                tocDepth,
                outputFilename);

        // Create the HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/transform"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();

        // Send the request and get the response
        HttpResponse<byte[]> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray());

        // Check if the request was successful
        byte[] result = response.body();
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return result;
        } else {
            log.error("Error: [{}] {}", response.statusCode(), new String(result, StandardCharsets.UTF_8));
            return new byte[0];
        }
    }

    /**
     * Build the multipart/form-data request body
     */
    private byte[] buildMultipartBody(
            String boundary,
            Integer startIndex,
            List<byte[]> markdownFiles,
            byte[] coverImage,
            String coverContentType,
            String title,
            String author,
            int tocDepth,
            String outputFilename) throws IOException {

        StringBuilder builder = new StringBuilder();
        List<byte[]> dataParts = new ArrayList<>();
        int paddingLength = String.valueOf(startIndex + markdownFiles.size()).length();

        // Add markdown files
        for (int i = 0; i < markdownFiles.size(); i++) {
            var markdownBytes = markdownFiles.get(i);
            var filename = String.format("chapter_%0" + paddingLength + "d.md", (startIndex + i));

            builder.append("--").append(boundary).append("\r\n");
            builder.append("Content-Disposition: form-data; name=\"markdown_files\"; filename=\"")
                    .append(filename).append("\"\r\n");
            builder.append("Content-Type: text/markdown\r\n\r\n");

            dataParts.add(builder.toString().getBytes(StandardCharsets.UTF_8));
            dataParts.add(markdownBytes);
            dataParts.add("\r\n".getBytes(StandardCharsets.UTF_8));

            builder.setLength(0);
        }

        // Add cover image if provided
        if (coverImage != null) {
            builder.append("--").append(boundary).append("\r\n");
            builder.append("Content-Disposition: form-data; name=\"cover_image\"; filename=\"")
                    .append(getCoverImageFilename(coverContentType)).append("\"\r\n");
            builder.append("Content-Type: ").append(coverContentType).append("\r\n\r\n");

            dataParts.add(builder.toString().getBytes(StandardCharsets.UTF_8));
            dataParts.add(coverImage);
            dataParts.add("\r\n".getBytes(StandardCharsets.UTF_8));

            builder.setLength(0);
        }

        // Add title if provided
        if (title != null && !title.isEmpty()) {
            builder.append("--").append(boundary).append("\r\n");
            builder.append("Content-Disposition: form-data; name=\"title\"\r\n\r\n");
            builder.append(title).append("\r\n");
            dataParts.add(builder.toString().getBytes(StandardCharsets.UTF_8));
            builder.setLength(0);
        }

        // Add author if provided
        if (author != null && !author.isEmpty()) {
            builder.append("--").append(boundary).append("\r\n");
            builder.append("Content-Disposition: form-data; name=\"author\"\r\n\r\n");
            builder.append(author).append("\r\n");
            dataParts.add(builder.toString().getBytes(StandardCharsets.UTF_8));
            builder.setLength(0);
        }

        // Add output filename
        builder.append("--").append(boundary).append("\r\n");
        builder.append("Content-Disposition: form-data; name=\"output_filename\"\r\n\r\n");
        builder.append(outputFilename).append("\r\n");
        dataParts.add(builder.toString().getBytes(StandardCharsets.UTF_8));
        builder.setLength(0);

        // Add toc depth
        builder.append("--").append(boundary).append("\r\n");
        builder.append("Content-Disposition: form-data; name=\"toc_depth\"\r\n\r\n");
        builder.append(tocDepth).append("\r\n");
        dataParts.add(builder.toString().getBytes(StandardCharsets.UTF_8));
        builder.setLength(0);

        // Add closing boundary
        builder.append("--").append(boundary).append("--\r\n");
        dataParts.add(builder.toString().getBytes(StandardCharsets.UTF_8));

        // Calculate total size and create result array
        int totalSize = 0;
        for (byte[] part : dataParts) {
            totalSize += part.length;
        }

        byte[] result = new byte[totalSize];
        int currentPosition = 0;
        for (byte[] part : dataParts) {
            System.arraycopy(part, 0, result, currentPosition, part.length);
            currentPosition += part.length;
        }

        return result;
    }

    @Deprecated
    private String getContentType(Path file) {
        String filename = file.getFileName().toString().toLowerCase();
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (filename.endsWith(".png")) {
            return "image/png";
        } else if (filename.endsWith(".gif")) {
            return "image/gif";
        } else {
            return "application/octet-stream";
        }
    }

    /**
     * Get file name based on content type
     */
    private String getCoverImageFilename(String coverContentType) {
        if ("image/jpeg".equals(coverContentType)) {
            return "cover-image.jpeg";
        } else if ("image/jpg".equals(coverContentType)) {
            return "cover-image.jpg";
        } else if ("image/png".equals(coverContentType)) {
            return "cover-image.png";
        }

        return "application/octet-stream";
    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class PandocRequest {

        private Integer startIndex;
        private List<byte[]> markdownFiles;
        private String title;
        private String author;
        private byte[] coverImage;
        private String coverContentType;
        @Builder.Default
        private int tocDepth = 2;
        private String outputFilename;

        public static PandocRequest createNew(Function<PandocRequest.PandocRequestBuilder, PandocRequest.PandocRequestBuilder> creator) {
            return creator.apply(PandocRequest.builder())
                    .build();
        }

    }

}