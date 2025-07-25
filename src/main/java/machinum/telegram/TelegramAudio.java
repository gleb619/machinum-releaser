package machinum.telegram;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.audio.TTSRestClient;
import machinum.audio.TTSRestClient.Metadata;
import machinum.audio.TextXmlReader.TextInfo;
import machinum.exception.AppException;
import machinum.minio.MinioService;
import machinum.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static machinum.util.CheckedRunnable.checked;

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

    /**
     * Key used to fetch the advertising audio file from MinIO.
     */
    private final String advertisingKey;

    /**
     * Key used to fetch the disclaimer audio file from MinIO.
     */
    private final String disclaimerKey;

    private final String coverUrl;

    private final byte[] defaultCover;

    /**
     * Combines advertising, disclaimer, and main audio content into a single MP3 file.
     *
     * @param fileName  The name of the output file.
     * @param metadata  Metadata associated with the audio files.
     * @param audioBytes Byte array containing the main audio content.
     * @return A byte array representing the combined MP3 file.
     * @throws IOException If an I/O error occurs during zip file creation or joining MP3 files.
     */
    @SneakyThrows
    public byte[] putTogether(String fileName, Metadata metadata, byte[] audioBytes) {
        initializer.waitForAudioGeneration();

        var advertising = minioService.getByKey(advertisingKey);
        var disclaimer = minioService.getByKey(disclaimerKey);

        var zipFile = createZipFile(Map.of(
                "aaaa.mp3", advertising.data(),
                "bbbb.mp3", disclaimer.data(),
                "cccc.mp3", audioBytes
        ));

        var coverArt = resolveCoverArt();

        return ttsClient.joinMp3Files(zipFile, "temp_" + fileName, Boolean.FALSE, coverArt, 2,
                metadata);
    }

    /**
     * Creates a zip file containing multiple MP3 files.
     *
     * @param files A map where the key is the name of the file and the value is the byte array content.
     * @return A byte array representing the created zip file.
     * @throws IOException If an I/O error occurs during zip file creation.
     */
    private byte[] createZipFile(Map<String, byte[]> files) throws IOException {
        var baos = new ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(baos)) {
            for (var entry : files.entrySet()) {
                var zipEntry = new ZipEntry(entry.getKey());
                zos.putNextEntry(zipEntry);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }

        return baos.toByteArray();
    }

    @SneakyThrows
    private byte[] resolveCoverArt() {
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
                    .enhance(false)
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
