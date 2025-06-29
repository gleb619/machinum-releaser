package machinum.machinimaserver;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class StaticFileHandler {

    private static final Map<String, String> MIME_TYPES = new ConcurrentHashMap<>();
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int BUFFER_SIZE = 8192;

    static {
        // Common MIME types
        MIME_TYPES.put("html", "text/html");
        MIME_TYPES.put("htm", "text/html");
        MIME_TYPES.put("css", "text/css");
        MIME_TYPES.put("js", "application/javascript");
        MIME_TYPES.put("json", "application/json");
        MIME_TYPES.put("xml", "application/xml");
        MIME_TYPES.put("txt", "text/plain");

        // Images
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("bmp", "image/bmp");
        MIME_TYPES.put("webp", "image/webp");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("ico", "image/x-icon");

        // Fonts
        MIME_TYPES.put("woff", "font/woff");
        MIME_TYPES.put("woff2", "font/woff2");
        MIME_TYPES.put("ttf", "font/ttf");
        MIME_TYPES.put("eot", "application/vnd.ms-fontobject");
        MIME_TYPES.put("otf", "font/otf");

        // Audio/Video
        MIME_TYPES.put("mp3", "audio/mpeg");
        MIME_TYPES.put("wav", "audio/wav");
        MIME_TYPES.put("mp4", "video/mp4");
        MIME_TYPES.put("webm", "video/webm");
        MIME_TYPES.put("ogg", "audio/ogg");

        // Documents
        MIME_TYPES.put("pdf", "application/pdf");
        MIME_TYPES.put("doc", "application/msword");
        MIME_TYPES.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        MIME_TYPES.put("xls", "application/vnd.ms-excel");
        MIME_TYPES.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        // Archives
        MIME_TYPES.put("zip", "application/zip");
        MIME_TYPES.put("tar", "application/x-tar");
        MIME_TYPES.put("gz", "application/gzip");
        MIME_TYPES.put("rar", "application/vnd.rar");

        // Default
        MIME_TYPES.put("", "application/octet-stream");
    }

    public static void files(MachinimaServer.Chain chain) {
        files(chain, "");
    }

    public static MachinimaServer.Chain files(MachinimaServer.Chain chain, String basePath) {
        return chain.get("/*", ctx -> {
            try {
                return handleStaticFile(ctx, basePath);
            } catch (Exception e) {
                log.error("Error serving static file", e);
                ctx.status(500);
                return ctx;
            }
        });
    }

    private static MachinimaServer.Context handleStaticFile(MachinimaServer.Context ctx, String basePath) {
        String requestPath = ctx.getExchange().getRequestURI().getPath();

        // Remove leading slash and resolve relative to base path
        String relativePath = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;

        // Security check - prevent directory traversal
        if (relativePath.contains("..") || relativePath.contains("./") || relativePath.contains("\\")) {
            ctx.status(403);
            return ctx;
        }

        // Resolve file path
        Path filePath;
        if (basePath.isEmpty()) {
            // Use upload directory from server config
            String uploadDir = System.getProperty("user.dir"); // Default fallback
            filePath = Paths.get(uploadDir, relativePath);
        } else {
            filePath = Paths.get(basePath, relativePath);
        }

        // Check if file exists and is readable
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            ctx.status(404);
            return ctx;
        }

        // Security check - ensure file is within allowed directory
        try {
            Path normalizedPath = filePath.normalize();
            Path allowedBasePath = basePath.isEmpty() ?
                    Paths.get(System.getProperty("user.dir")).normalize() :
                    Paths.get(basePath).normalize();

            if (!normalizedPath.startsWith(allowedBasePath)) {
                ctx.status(403);
                return ctx;
            }
        } catch (Exception e) {
            log.warn("Path security check failed for: {}", filePath, e);
            ctx.status(403);
            return ctx;
        }

        // Check if it's a directory
        if (Files.isDirectory(filePath)) {
            // Try to serve index.html if it exists
            Path indexFile = filePath.resolve("index.html");
            if (Files.exists(indexFile) && Files.isReadable(indexFile)) {
                filePath = indexFile;
            } else {
                ctx.status(404);
                return ctx;
            }
        }

        try {
            // Check file size
            long fileSize = Files.size(filePath);
            if (fileSize > MAX_FILE_SIZE) {
                log.warn("File too large: {} ({}MB)", filePath, fileSize / (1024 * 1024));
                ctx.status(413); // Payload Too Large
                return ctx;
            }

            // Get MIME type
            String mimeType = getMimeType(filePath);

            // Set headers
            ctx.header("Content-Type", mimeType);
            ctx.header("Content-Length", String.valueOf(fileSize));

            // Add caching headers for static assets
            if (isCacheableResource(mimeType)) {
                ctx.header("Cache-Control", "public, max-age=86400"); // 1 day
                ctx.header("Expires", ZonedDateTime.now(ZoneOffset.UTC)
                        .plusDays(1)
                        .format(DateTimeFormatter.RFC_1123_DATE_TIME));
            }

            // Handle conditional requests (If-Modified-Since)
            String ifModifiedSince = ctx.header("If-Modified-Since");
            long lastModified = Files.getLastModifiedTime(filePath).toMillis();

            if (ifModifiedSince != null) {
                try {
                    long ifModifiedSinceTime = ZonedDateTime.parse(ifModifiedSince,
                            DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();

                    if (lastModified <= ifModifiedSinceTime) {
                        ctx.status(304); // Not Modified
                        return ctx;
                    }
                } catch (Exception e) {
                    // Ignore invalid date format
                }
            }

            // Set Last-Modified header
            ctx.header("Last-Modified", ZonedDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(lastModified), ZoneOffset.UTC)
                    .format(DateTimeFormatter.RFC_1123_DATE_TIME));

            // Handle range requests for large files (basic implementation)
            String rangeHeader = ctx.header("Range");
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                return handleRangeRequest(ctx, filePath, rangeHeader, fileSize, mimeType);
            }

            // Read and serve file
            serveFile(ctx, filePath, mimeType);
            return ctx;
        } catch (IOException e) {
            log.error("Error reading file: {}", filePath, e);
            ctx.status(500);
            return ctx;
        }
    }

    private static String getMimeType(Path filePath) {
        String fileName = filePath.getFileName().toString();
        String extension = "";

        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            extension = fileName.substring(lastDotIndex + 1).toLowerCase();
        }

        return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    private static boolean isCacheableResource(String mimeType) {
        return mimeType.startsWith("image/") ||
                mimeType.startsWith("font/") ||
                mimeType.equals("text/css") ||
                mimeType.equals("application/javascript") ||
                mimeType.startsWith("audio/") ||
                mimeType.startsWith("video/");
    }

    private static void serveFile(MachinimaServer.Context ctx, Path filePath, String mimeType) throws IOException {
        try (InputStream fileStream = Files.newInputStream(filePath);
             OutputStream responseStream = ctx.getExchange().getResponseBody()) {

            // Send response headers
            ctx.getExchange().sendResponseHeaders(200, Files.size(filePath));

            // Stream file content
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = fileStream.read(buffer)) != -1) {
                responseStream.write(buffer, 0, bytesRead);
            }
        }
    }

    private static MachinimaServer.Context handleRangeRequest(MachinimaServer.Context ctx, Path filePath, String rangeHeader,
                                                              long fileSize, String mimeType) {
        try {
            // Parse range header (basic implementation for single range)
            String range = rangeHeader.substring(6); // Remove "bytes="
            String[] parts = range.split("-");

            long start = 0;
            long end = fileSize - 1;

            if (parts.length >= 1 && !parts[0].isEmpty()) {
                start = Long.parseLong(parts[0]);
            }
            if (parts.length >= 2 && !parts[1].isEmpty()) {
                end = Long.parseLong(parts[1]);
            }

            // Validate range
            if (start >= fileSize || end >= fileSize || start > end) {
                ctx.status(416); // Range Not Satisfiable
                ctx.header("Content-Range", "bytes */" + fileSize);
                return ctx;
            }

            long contentLength = end - start + 1;

            // Set partial content headers
            ctx.status(206); // Partial Content
            ctx.header("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
            ctx.header("Content-Length", String.valueOf(contentLength));
            ctx.header("Content-Type", mimeType);
            ctx.header("Accept-Ranges", "bytes");

            // Send partial content
            try (InputStream fileStream = Files.newInputStream(filePath);
                 OutputStream responseStream = ctx.getExchange().getResponseBody()) {

                ctx.getExchange().sendResponseHeaders(206, contentLength);

                // Skip to start position
                fileStream.skip(start);

                // Stream partial content
                byte[] buffer = new byte[BUFFER_SIZE];
                long remaining = contentLength;
                int bytesRead;

                while (remaining > 0 && (bytesRead = fileStream.read(buffer, 0,
                        (int) Math.min(buffer.length, remaining))) != -1) {
                    responseStream.write(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                }
            }

            return ctx;
        } catch (Exception e) {
            log.error("Error handling range request", e);
            ctx.status(500);
            return ctx;
        }
    }

    // Utility method to add MIME type
    public static void addMimeType(String extension, String mimeType) {
        MIME_TYPES.put(extension.toLowerCase(), mimeType);
    }

    // Enhanced Chain method with base directory support
    public static void filesFrom(MachinimaServer.Chain chain, String baseDirectory) {
        files(chain, baseDirectory);
    }

}
