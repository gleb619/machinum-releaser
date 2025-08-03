package machinum.minio;

import io.jooby.StatusCode;
import io.minio.*;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Service for interacting with MinIO object storage.
 * Provides methods for file operations including existence checks,
 * file creation, and file downloads.
 */
@Slf4j
@RequiredArgsConstructor
public class MinioService {

    private static final String NO_SUCH_KEY = "NoSuchKey";

    private final MinioClient minioClient;
    private final HttpClient httpClient;
    private final String bucketName;


    /**
     * Checks if a file exists in MinIO storage by its key.
     *
     * @param key the file key/path to check
     * @return true if file exists, false otherwise
     */
    public boolean fileExists(String key) {
        try {
            // Attempt to get object metadata
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(key)
                            .build()
            );
            log.debug("File exists: {}", key);
            return true;
        } catch (ErrorResponseException e) {
            // File doesn't exist if we get a NoSuchKey error
            if (NO_SUCH_KEY.equals(e.errorResponse().code())) {
                log.debug("File does not exist: {}", key);
                return false;
            }
            // Re-throw other errors
            log.error("Error checking file existence for key: {}", key, e);
            throw new AppException("Failed to check file existence", e);
        } catch (Exception e) {
            log.error("Unexpected error checking file existence for key: {}", key, e);
            throw new AppException("Failed to check file existence", e);
        }
    }

    /**
     * Retrieves file data from MinIO storage by its key.
     *
     * @param objectKey the file key/path to retrieve
     * @return FileData record containing byte array and metadata
     * @throws AppException if the file for the given key is not found or retrieval fails
     */
    public FileData getByKey(String objectKey) {
        return findByKey(objectKey)
                .orElseThrow(() -> new AppException("File for given key is not found: %s", objectKey));
    }

    /**
     * Gets file data and metadata from MinIO storage by its key.
     *
     * @param objectKey the file key/path to retrieve
     * @return FileData record containing byte array and metadata
     * @throws RuntimeException if the retrieval fails or file doesn't exist
     */
    public Optional<FileData> findByKey(String objectKey) {
        try {
            // Get object metadata first
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );

            // Get the object data
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            )) {
                byte[] data = stream.readAllBytes();

                log.debug("Successfully retrieved file with metadata: {}", objectKey);

                return Optional.of(new FileData(
                        data,
                        stat.contentType(),
                        stat.size(),
                        stat.lastModified(),
                        stat.etag(),
                        stat.userMetadata()
                ));
            }
        } catch (ErrorResponseException e) {
            if (NO_SUCH_KEY.equals(e.errorResponse().code())) {
                log.warn("Attempted to get non-existent file: {}", objectKey);
                return Optional.empty();
            }
            log.error("Error getting file with key: {}", objectKey, e);
            throw new AppException("Failed to get file", e);
        } catch (Exception e) {
            log.error("Unexpected error getting file with key: {}", objectKey, e);
            throw new AppException("Failed to get file", e);
        }
    }

    /**
     * Downloads a file from MinIO storage by its key.
     *
     * @param key the file key/path to download
     * @return InputStream containing the file data
     * @throws AppException if the download fails or file doesn't exist
     */
    public InputStream downloadFile(String key) {
        try {
            // Get the object as an input stream
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(key)
                            .build()
            );
            log.debug("Successfully retrieved file: {}", key);
            return stream;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                log.warn("Attempted to download non-existent file: {}", key);
                throw new AppException("File not found: " + key, e);
            }
            log.error("Error downloading file with key: {}", key, e);
            throw new AppException("Failed to download file", e);
        } catch (Exception e) {
            log.error("Unexpected error downloading file with key: {}", key, e);
            throw new AppException("Failed to download file", e);
        }
    }

    /**
     * Creates/uploads a file to MinIO storage with custom content type.
     *
     * @param key the file key/path where the file will be stored
     * @param data the input bytes containing the file data
     * @param contentType the MIME type of the file
     * @throws AppException if the upload fails
     */
    public void createFile(String key, byte[] data, String contentType, Map<String, String> metadata) {
        try {
            // Upload the file with specified content type
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(key)
                            .stream(new ByteArrayInputStream(data), data.length, -1)
                            .contentType(contentType)
                            .userMetadata(metadata)
                            .build()
            );
            log.info("Successfully uploaded file: {}, contentType={}", key, contentType);
        } catch (Exception e) {
            log.error("Failed to upload file with key: {} and content type: {}", key, contentType, e);
            throw new AppException("Failed to upload file", e);
        }
    }

    /**
     * Creates/uploads an MP3 file to MinIO storage.
     *
     * @param key the file key/path where the file will be stored
     * @param data the input data containing the MP3 bytes
     * @throws AppException if the upload fails
     */
    public void createMp3File(String key, byte[] data, Map<String, String> metadata) {
        createFile(key, data, "audio/mpeg", metadata);
    }

    /**
     * Record containing file data and metadata from MinIO.
     *
     * @param data            the file content as byte array
     * @param contentType     the MIME type of the file
     * @param contentLength   the size of the file in bytes
     * @param lastModified    the last modification date
     * @param etag            the ETag of the object
     * @param metadata        a map containing additional metadata key-value pairs associated with the file
     */
    public record FileData(
            byte[] data,
            String contentType,
            long contentLength,
            ZonedDateTime lastModified,
            String etag,
            Map<String, String> metadata) {}

    /**
     * Generates a pre-signed URL for an object in a specified MinIO bucket.
     * The URL is valid for 1 hour and allows GET access to the object.
     *
     * @param bucketName The name of the bucket where the object is located.
     * @param objectKey  The key (path) of the object for which to generate the URL.
     * @return A string representing the pre-signed URL.
     * @throws InvalidKeyException      If the access key is invalid.
     * @throws IOException              If an I/O error occurs during the operation.
     * @throws NoSuchAlgorithmException If the specified algorithm is not available.
     * @throws MinioException           If a MinIO-specific error occurs during URL generation.
     */
    public String getPreSignedUrl(String bucketName, String objectKey) throws InvalidKeyException, IOException, NoSuchAlgorithmException, MinioException {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(1, TimeUnit.HOURS)
                            .build()
            );
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidResponseException | XmlParserException | ServerException e) {
            throw new MinioException("MinIO pre-signed URL generation failed: " + e.getMessage());
        }
    }

    /**
     * Downloads content from a given pre-signed URL.
     * This method uses HttpClient to perform a GET request to the provided URL and returns the content as a byte array.
     *
     * @param preSignedUrl The pre-signed URL from which to download the content.
     * @return A byte array containing the downloaded content.
     * @throws IOException If an I/O error occurs during the download, or if the HTTP response status code is not OK (200).
     */
    @SneakyThrows
    public byte[] downloadContent(String preSignedUrl) {
        log.debug("Prepare to download content from minio: {}", preSignedUrl);

        var request = HttpRequest.newBuilder()
                .uri(URI.create(preSignedUrl))
                .GET()
                .build();

        log.debug("Sending GET request to: {}", preSignedUrl);

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != StatusCode.OK_CODE) {
            String errorMessage = "Failed to fetch MP3 from MinIO pre-signed URL. Status: " + response.statusCode();
            log.error(errorMessage);
            throw new IOException(errorMessage);
        }

        log.info("Successfully downloaded content from: {}", preSignedUrl);

        return response.body();
    }

}