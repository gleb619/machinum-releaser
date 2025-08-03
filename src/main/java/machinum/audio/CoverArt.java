package machinum.audio;

import lombok.Builder;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Accessors;
import machinum.minio.MinioService;

/**
 * Represents cover art for an audio release.
 * This class uses Lombok annotations for boilerplate code reduction,
 * providing immutability (@Value), a builder pattern (@Builder),
 * and fluent accessors (@Accessors(fluent = true)).
 */
@Value
@Builder
@Accessors(fluent = true)
public class CoverArt {

    /**
     * The content of the cover art as a byte array.
     */
    byte[] content;

    /**
     * Resolves the cover art content based on a given URL or a default cover.
     * If the {@code coverUrl} is "none" (case-insensitive), the {@code defaultCover} is returned.
     * If the {@code coverUrl} starts with "http", it's treated as a direct download URL.
     * Otherwise, it's assumed to be a Minio object path (bucket/objectName) and a pre-signed URL is generated
     * to download the content from Minio.
     *
     * @param minioService The MinioService instance used for downloading content or generating pre-signed URLs.
     * @param coverUrl The URL or Minio path to the cover art.
     * @param defaultCover The default cover art content to use if {@code coverUrl} is "none".
     * @return A byte array containing the resolved cover art content.
     * @throws RuntimeException if an error occurs during content download or URL resolution (due to @SneakyThrows).
     */
    @SneakyThrows
    public static byte[] resolveCoverArt(MinioService minioService, String coverUrl, byte[] defaultCover) {
        if (!"none".equalsIgnoreCase(coverUrl)) {
            if(coverUrl.startsWith("http")) {
                return minioService.downloadContent(coverUrl);
            } else {
                var parts = coverUrl.split("/");
                var url = minioService.getPreSignedUrl(parts[0], parts[1]);
                return minioService.downloadContent(url);
            }
        } else {
            return defaultCover;
        }
    }

}
