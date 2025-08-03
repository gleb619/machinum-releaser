package machinum.telegram;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.audio.CoverArt;
import machinum.audio.TTSRestClient;
import machinum.audio.TTSRestClient.Metadata;
import machinum.audio.TextXmlReader.TextInfo;
import machinum.exception.AppException;
import machinum.minio.MinioService;
import machinum.util.Util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static machinum.util.CheckedRunnable.checked;
import static machinum.util.ZipUtil.createZipFile;
import static machinum.util.ZipUtil.readZipFile;

/**
 * This class is responsible for handling audio operations related to Telegram,
 * including fetching advertising and disclaimer audio files, creating a zip file
 * with these along with the main audio content, and joining them into a single MP3 file.
 */
@Slf4j
@RequiredArgsConstructor
public class TelegramAudio {

    /**
     * Service used to interact with MinIO for retrieving audio files by key.
     */
    private final MinioService minioService;

    /**
     * REST client for Text-to-Speech operations, specifically joining multiple MP3 files.
     */
    private final TTSRestClient ttsClient;

    /**
     * Initializer responsible for waiting until audio generation is complete before proceeding.
     */
    private final Initializer initializer;

    private final ObjectMapper objectMapper;

    /**
     * Key used to fetch the advertising audio file from MinIO.
     */
    private final String advertisingKey;

    /**
     * Key used to fetch the disclaimer audio file from MinIO.
     */
    private final String disclaimerKey;

    private final CoverArt coverArt;

    /**
     * Combines advertising, disclaimer, and main audio content into a single MP3 file.
     *
     * @param fileName      The name of the output file.
     * @param metadata      Metadata associated with the audio files.
     * @param returnZip
     * @param audioBytes    Byte array containing the main audio content.
     * @param coverArtBytes Byte array containing the covert art content.
     * @return A byte array representing the combined MP3 file.
     * @throws IOException If an I/O error occurs during zip file creation or joining MP3 files.
     */
    @SneakyThrows
    public FileMetadata putTogether(String fileName, Metadata metadata, boolean returnZip,
                                    byte[] audioBytes, byte[] coverArtBytes) {
        log.info("Prepare to create release mp3 file for: {}, size={}mb", fileName, audioBytes.length / 1024 / 1024);

        initializer.waitForAudioGeneration();

        var advertising = minioService.getByKey(advertisingKey);
        log.debug("Advertising audio retrieved from MinIO: key={}, size={}mb",
                advertisingKey, advertising.data().length / 1024 / 1024);

        var disclaimer = minioService.getByKey(disclaimerKey);
        log.debug("Disclaimer audio retrieved from MinIO: key={}, size={}mb",
                disclaimerKey, disclaimer.data().length / 1024 / 1024);

        var zipFile = createZipFile(Map.of(
                "aaaa.mp3", advertising.data(),
                "bbbb.mp3", disclaimer.data(),
                "cccc.mp3", audioBytes
        ));
        log.debug("ZIP file created containing advertising, disclaimer, and main audio: size={}mb", zipFile.length / 1024 / 1024);

        var targetCoverArt = coverArtBytes.length == 0 ? coverArt.content() : coverArtBytes;
        if (coverArtBytes.length == 0) {
            log.info("No cover art provided. Using default cover art.");
        } else {
            log.debug("Using provided cover art: size={}mb", coverArtBytes.length / 1024 / 1024);
        }

        byte[] result = ttsClient.joinMp3Files(zipFile, fileName.replace(".mp3", "_0001.mp3"), Boolean.FALSE, returnZip,
                targetCoverArt, 2, metadata);
        log.info("Successfully joined MP3 files, size={}mb", result.length / 1024 / 1024);

        if(returnZip) {
            return processZip(result, metadata);
        }

        return FileMetadata.builder()
                .mp3Data(result)
                .durationSeconds(0)
                .build();
    }

    @SneakyThrows
    public List<FileMetadata> enhance(String fileNameTemplate, Metadata metadata, List<byte[]> files, byte[] coverArtBytes) {
        var targetCoverArt = coverArtBytes.length == 0 ? coverArt.content() : coverArtBytes;
        if (coverArtBytes.length == 0) {
            log.info("No cover art provided. Using default cover art.");
        } else {
            log.debug("Using provided cover art: size={}mb", coverArtBytes.length / 1024 / 1024);
        }

        var localTemplate = fileNameTemplate.replaceAll(".mp3$", "");

        record TempObj(int index, byte[] bytes){}

        var targetFiles = IntStream.range(0, files.size())
                .filter(i -> i <= files.size())
                //e.g. start from _0002
                .mapToObj(i -> new TempObj(i + 2, files.get(i)))
                .collect(Collectors.toMap(o -> "%s_%04d.mp3".formatted(localTemplate, o.index()),
                        TempObj::bytes, (f, s) -> f, LinkedHashMap::new));

        var zipBytes = ttsClient.enhanceFiles(targetCoverArt, targetFiles, "podcast", metadata);
        var enhancedFiles = readZipFile(zipBytes);

        return enhancedFiles.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith(".mp3"))
                .map(mp3Entry -> {
                    String jsonKey = mp3Entry.getKey().replace(".mp3", "_metadata.json");
                    byte[] jsonData = enhancedFiles.get(jsonKey);
                    try {
                        FileMetadata fileMetadata = objectMapper.readValue(jsonData, FileMetadata.class);
                        fileMetadata.setMp3Data(mp3Entry.getValue());
                        fileMetadata.setMetadata(metadata);
                        return fileMetadata;
                    } catch (IOException e) {
                        log.error("Error reading metadata for file: {}", mp3Entry.getKey(), e);
                        throw new AppException("Failed to read metadata", e);
                    }
                })
                .collect(Collectors.toList());
    }

    private FileMetadata processZip(byte[] data, Metadata metadata) throws IOException {
        byte[] mp3Data = null;
        FileMetadata fileMetadata = null;

        try (var zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                var name = entry.getName();
                log.debug("Found {} file in zip", name);

                if (name.endsWith(".mp3")) {
                    mp3Data = zis.readAllBytes();
                } else if (name.endsWith(".json")) {
                    byte[] jsonData = zis.readAllBytes();
                    log.debug("Extracted JSON metadata from zip entry: {}", new String(jsonData, StandardCharsets.UTF_8));
                    fileMetadata = objectMapper.readValue(jsonData, FileMetadata.class);
                } else {
                    log.warn("Found different file: {}", name);
                }
            }
        }

        if (mp3Data == null || mp3Data.length == 0) {
            throw new AppException("MP3 file was not found in result zip");
        }

        if (fileMetadata == null) {
            throw new AppException("JSON metadata file was not found in result zip");
        }

        fileMetadata.setMp3Data(mp3Data);
        fileMetadata.setMetadata(metadata);

        return fileMetadata;
    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @ToString(onlyExplicitlyIncluded = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class FileMetadata {

        @ToString.Include
        private String filename;
        private long fileSizeBytes;
        private double durationSeconds;
        private int bitrateBps;
        private int sampleRateHz;
        private int channels;
        private String format;
        @JsonAlias("id3Tags")
        @ToString.Include
        private Metadata metadata;
        private byte[] mp3Data;

    }

    @RequiredArgsConstructor
    public static class Initializer {

        private static final String CHECKSUM = "checksum";

        private final ReentrantLock lock = new ReentrantLock();

        /**
         * Service for interacting with MinIO.
         */
        private final MinioService minioService;

        /**
         * Client for TTS (Text-to-Speech) REST API.
         */
        private final TTSRestClient ttsClient;

        /**
         * Key used to store the advertising audio file in MinIO.
         */
        private final String advertisingKey;

        /**
         * Key used to store the disclaimer audio file in MinIO.
         */
        private final String disclaimerKey;

        /**
         * Information about text content, including TTS (Text-to-Speech) details.
         */
        private final TextInfo textInfo;

        /**
         * Initializes the audio files by checking their existence and updating if necessary.
         */
        @SneakyThrows
        public void init() {
            Util.runAsync(checked(() -> {
                try {
                    lock.lock();
                    log.info("Prepare to check files in MinIO...");
                    createIfNotExist(advertisingKey, textInfo.getTts().getAdvertising(), "advertising.mp3");
                    createIfNotExist(disclaimerKey, textInfo.getTts().getDisclaimer(), "disclaimer.mp3");
                } finally {
                    lock.unlock();
                }
            }));
        }

        /**
         * Waits until the audio generation is complete.
         *
         */
        @SneakyThrows
        public void waitForAudioGeneration() {
            try {
                log.trace("Checking to audio generation to complete...");
                boolean result = lock.tryLock(10, TimeUnit.MINUTES);
                if(!result) {
                    throw new AppException("Can't check audio generation status");
                }
            } finally {
                log.trace("Audio generation is completed.");
                lock.unlock();
            }
        }

        /**
         * Creates an audio file in MinIO if it does not exist or if the checksum of the existing file does not match.
         *
         * @param key      The unique identifier for the file in MinIO.
         * @param text     The text to be converted into speech and stored as an MP3 file.
         * @param fileName The name of the MP3 file.
         */
        private void createIfNotExist(String key, String text, String fileName) {
            log.debug("Checking if file exists with key: {}", key);
            var fileData = minioService.findByKey(key);
            var checksum = hash(text);

            var uploadToMinio = checked(() -> {
                byte[] bytes = textToSpeech(text, fileName);
                log.debug("Uploading new MP3 file to MinIO with key: {}", key);
                minioService.createMp3File(key, bytes, Map.of(CHECKSUM, checksum));
            });

            // If not exist, create
            if (fileData.isEmpty()) {
                log.debug("File does not exist. Uploading new file: {}", key);
                uploadToMinio.run();
            } else {
                // If the checksums are not equal, create
                var intro = fileData.get();
                if (!checksum.equals(intro.metadata().get(CHECKSUM))) {
                    log.warn("Checksum mismatch detected. Uploading updated file.");
                    uploadToMinio.run();
                } else {
                    log.debug("File already exists with matching checksum: {}", checksum);
                }
            }
        }

        /**
         * Converts text to speech and returns the audio data as a byte array.
         *
         * @param text     The text to be converted into speech.
         * @param fileName The name of the output file.
         * @return A byte array containing the MP3 audio data.
         * @throws IOException          If an I/O error occurs during the conversion process.
         * @throws InterruptedException If the thread is interrupted while waiting for the TTS service response.
         */
        private byte[] textToSpeech(String text, String fileName) throws IOException, InterruptedException {
            log.debug("Generating audio for given text in TTS service: {}", fileName);
            return ttsClient.generate(TTSRestClient.TTSRequest.builder()
                    .text(text)
                    .outputFile(fileName)
                    .enhance(true)
                    .returnZip(false)
                    .build());
        }

        /**
         * Generates a checksum for the given text using CRC32.
         *
         * @param text The text to generate a checksum for.
         * @return A string representation of the checksum in hexadecimal format.
         */
        private String hash(String text) {
            var fileCRC32 = new CRC32();
            fileCRC32.update(text.getBytes(StandardCharsets.UTF_8));
            return String.format(Locale.US, "%08X", fileCRC32.getValue());
        }

    }

}
