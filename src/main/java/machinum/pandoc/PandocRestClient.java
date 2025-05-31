package machinum.pandoc;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.cache.CacheService;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import static machinum.util.Util.isNonEmpty;

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
                convertToEpub(pandocRequest));
    }

    /**
     * Converts markdown files to EPUB format
     *
     * @param pandocRequest
     * @return byte array if conversion was successful, empty array otherwise
     * @throws IOException          If an I/O error occurs
     * @throws InterruptedException If the operation is interrupted
     */
    @SneakyThrows
    public byte[] convertToEpub(PandocRequest pandocRequest) {

        // Validate input
        if (pandocRequest.getMarkdownFiles() == null || pandocRequest.getMarkdownFiles().isEmpty()) {
            throw new IllegalArgumentException("No markdown files provided");
        }

        if (pandocRequest.getCoverImage() == null || pandocRequest.getCoverImage().length <= 0) {
            throw new IllegalArgumentException("Cover image does not exist");
        }

        // Create a unique boundary for multipart form data
        String boundary = UUID.randomUUID().toString();

        // Build the multipart request body
        byte[] requestBody = buildMultipartBody(
                boundary,
                pandocRequest);

        // Create the HTTP request
        var targetUrl = URI.create(baseUrl + "/api/transform");

        log.info(">> {}", targetUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(targetUrl)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();

        try {
            // Send the request and get the response
            HttpResponse<byte[]> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray());

            log.info("<< {} {}", targetUrl, response.statusCode());

            // Check if the request was successful
            byte[] result = response.body();
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return result;
            } else {
                log.error("Error: [{}] {}", response.statusCode(), new String(result, StandardCharsets.UTF_8));
                return new byte[0];
            }
        } catch (Exception e) {
            if (e instanceof ConnectException ce) {
                log.error("<X Server is not reachable: {}", ce.getMessage());
            } else {
                log.error("<< %s %s: ".formatted(targetUrl, -1), e);
            }

            return new byte[0];
        }
    }

    /**
     * Build the multipart/form-data request body
     */
    private byte[] buildMultipartBody(String boundary, PandocRequest pandocRequest) throws IOException {
        List<byte[]> dataParts = new ArrayList<>();

        // Add markdown files
        for (int i = 0; i < pandocRequest.getMarkdownFiles().size(); i++) {
            var markdownBytes = pandocRequest.getMarkdownFiles().get(i);
            var filename = String.format("chapter_%03d.md", i + 1);
            addFilePart(boundary, "markdown_files", filename, "text/markdown", markdownBytes, dataParts);
        }

        // Add cover image if provided
        if (pandocRequest.getCoverImage() != null) {
            var coverFilename = getCoverImageFilename(pandocRequest.getCoverContentType());
            addFilePart(boundary, "cover_image", coverFilename, pandocRequest.getCoverContentType(), pandocRequest.getCoverImage(), dataParts);
        }

        // Add title if provided
        if (isNonEmpty(pandocRequest.getTitle())) {
            addTextPart(boundary, "title", pandocRequest.getTitle(), dataParts);
        }
        // Add title if provided
        if (isNonEmpty(pandocRequest.getSubtitle())) {
            addTextPart(boundary, "subtitle", pandocRequest.getSubtitle(), dataParts);
        }

        // Add author if provided
        if (isNonEmpty(pandocRequest.getAuthor())) {
            addTextPart(boundary, "author", pandocRequest.getAuthor(), dataParts);
        }

        if (isNonEmpty(pandocRequest.getPublisher())) {
            addTextPart(boundary, "publisher", pandocRequest.getPublisher(), dataParts);
        }

        if (isNonEmpty(pandocRequest.getPublisherInfo())) {
            addTextPart(boundary, "publisher_info", pandocRequest.getPublisherInfo(), dataParts);
        }

        if (isNonEmpty(pandocRequest.getEdition())) {
            addTextPart(boundary, "edition", pandocRequest.getEdition(), dataParts);
        }

        if (isNonEmpty(pandocRequest.getRights())) {
            addTextPart(boundary, "rights", pandocRequest.getRights(), dataParts);
        }

        if (isNonEmpty(pandocRequest.getLegalRights())) {
            addTextPart(boundary, "legal_rights", pandocRequest.getLegalRights(), dataParts);
        }

        if (isNonEmpty(pandocRequest.getDisclaimer())) {
            addTextPart(boundary, "disclaimer", pandocRequest.getDisclaimer(), dataParts);
        }

        if (isNonEmpty(pandocRequest.getDescription())) {
            addTextPart(boundary, "description", pandocRequest.getDescription(), dataParts);
        }

        if (isNonEmpty(pandocRequest.getKeywords())) {
            addTextPart(boundary, "keywords", pandocRequest.getKeywords(), dataParts);
        }

        if (isNonEmpty(pandocRequest.getDate())) {
            addTextPart(boundary, "date", pandocRequest.getDate(), dataParts);
        }

        if (isNonEmpty(pandocRequest.getPubdate())) {
            addTextPart(boundary, "pubdate", pandocRequest.getPubdate(), dataParts);
        }

        if (isNonEmpty(pandocRequest.getWebsite())) {
            addTextPart(boundary, "website", pandocRequest.getWebsite(), dataParts);
        }

        if (Objects.nonNull(pandocRequest.getSocialLinks())) {
            for (String socialLink : pandocRequest.getSocialLinks()) {
                addTextPart(boundary, "social_links", socialLink, dataParts);
            }
        }

        // Add language
        addTextPart(boundary, "language", pandocRequest.getLanguage(), dataParts);

        // Add output filename
        addTextPart(boundary, "output_filename", pandocRequest.getOutputFilename(), dataParts);

        // Add toc depth
        addTextPart(boundary, "toc_depth", String.valueOf(pandocRequest.getTocDepth()), dataParts);

        // Add closing boundary
        dataParts.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        // Combine all parts into a single byte array
        return combineParts(dataParts);
    }

    // Helper method to add a file part
    private void addFilePart(String boundary, String name, String filename, String contentType, byte[] content, List<byte[]> dataParts) {
        String header = String.format("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\nContent-Type: %s\r\n\r\n",
                boundary, name, filename, contentType);
        dataParts.add(header.getBytes(StandardCharsets.UTF_8));
        dataParts.add(content);
        dataParts.add("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    // Helper method to add a text part
    private void addTextPart(String boundary, String name, String value, List<byte[]> dataParts) {
        String part = String.format("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n", boundary, name, value);
        dataParts.add(part.getBytes(StandardCharsets.UTF_8));
    }

    // Helper method to combine all parts into a single byte array
    private byte[] combineParts(List<byte[]> dataParts) {
        int totalSize = dataParts.stream().mapToInt(part -> part.length).sum();
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
        private byte[] coverImage;
        private String coverContentType;
        private String title;
        private String subtitle;
        private String author;
        private String publisher;
        private String publisherInfo;
        private String edition;
        private String rights;
        private String legalRights;
        private String disclaimer;
        private String description;
        private String keywords;
        private String date;
        private String pubdate;
        private String website;
        private List<String> socialLinks;
        private String outputFilename;
        private String language = "ru-RU";
        @Builder.Default
        private int tocDepth = 2;


        public static PandocRequest createNew(Function<PandocRequest.PandocRequestBuilder, PandocRequest.PandocRequestBuilder> creator) {
            return creator.apply(PandocRequest.builder())
                    .build();
        }

    }

}