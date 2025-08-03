package machinum.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.*;
import com.pengrad.telegrambot.request.*;
import com.pengrad.telegrambot.response.MessagesResponse;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppException;
import machinum.image.Image;
import net.coobird.thumbnailator.Thumbnails;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
public class TelegramClient implements AutoCloseable {

    public static final int TELEGRAM_LIMIT = 1024;
    public static final String MPEG_CONTENT_TYPE = "audio/mpeg";

    private final TelegramBot bot;
    private final TelegramProperties telegramProperties;
    private final ObjectMapper objectMapper;

    public TelegramClient(TelegramProperties telegramProperties, ObjectMapper objectMapper) {
        this.bot = new TelegramBot(telegramProperties.getToken());
        this.telegramProperties = telegramProperties;
        this.objectMapper = objectMapper;
        log.debug("Started telegram session");
    }

    @SneakyThrows
    public Integer sendMessage(@NonNull String chatId, @NonNull String messageText) {
        return replyToMessage(chatId, null, messageText);
    }

    @SneakyThrows
    public Response sendAudioFilesWithMessage(@NonNull String chatId, @NonNull String messageText,
                                              @NonNull String contentType, @NonNull String performer,
                                              @NonNull List<AudioRecord> audioRecords, byte[] thumbnail) {
        return AudioSender.create(b -> b
                    .chatId(chatId)
                    .messageText(messageText)
                    .contentType(contentType)
                    .performer(performer)
                    .audioRecords(audioRecords)
                    .thumbnail(thumbnail)
                    .objectMapper(objectMapper)
                    .bot(bot)
                )
                .validate()
                .splitToChunks()
                .processChunks()
                .result();
    }

    @SneakyThrows
    public Response sendAudioFileWithMessage(@NonNull String chatId, @NonNull String messageText,
                                             @NonNull String contentType, @NonNull String performer,
                                             int duration, String fileName, byte[] data, byte[] thumbnail) {
        if (data.length <= 0) {
            throw new IllegalArgumentException("File doesn't exists");
        }

        SendAudio request = new SendAudio(chatId, data)
                    .fileName(fileName)
                    .contentType(contentType)
                    .caption(messageText)
                    .parseMode(ParseMode.HTML)
                    .performer(performer)
                    .duration(duration)
                    .thumbnail(thumbnail);

        var response = bot.execute(request);

        if (!response.isOk()) {
            log.error("Found mistake: code={}, description={}", response.errorCode(), response.description());
            throw new AppException("Error occurred: %s".formatted(objectMapper.writeValueAsString(response)));
        } else {
            log.info("Got success response: id={}, message={}", response.message().messageId(), response.message());
            var jsonNode = objectMapper.valueToTree(response);

            return Response.of(response.message().messageId(), jsonNode);
        }
    }

    @SneakyThrows
    public Response sendFileWithMessage(@NonNull String chatId, @NonNull String messageText,
                                        @NonNull String contentType, String fileName, byte[] data) {
        if (data.length <= 0) {
            throw new IllegalArgumentException("File doesn't exists");
        }

        SendDocument request = new SendDocument(chatId, data)
                    .fileName(fileName)
                    .contentType(contentType)
                    .caption(messageText)
                    .parseMode(ParseMode.HTML);

        var response = bot.execute(request);

        if (!response.isOk()) {
            log.error("Found mistake: code={}, description={}", response.errorCode(), response.description());
            throw new AppException("Error occurred: %s".formatted(objectMapper.writeValueAsString(response)));
        } else {
            log.info("Got success response: id={}, message={}", response.message().messageId(), response.message());
            var jsonNode = objectMapper.valueToTree(response);

            return Response.of(response.message().messageId(), jsonNode);
        }
    }

    @SneakyThrows
    public Integer sendFilesWithMessage(@NonNull String chatId, @NonNull String messageText, @NonNull String contentType,
                                        @NonNull File... documents) {
        if (documents.length == 0) {
            throw new IllegalArgumentException("No files were provided, please use another method");
        }

        for (File document : documents) {
            if (!document.exists()) {
                throw new IllegalArgumentException("File doesn't exists: %s".formatted(document.getName()));
            }
        }

        var docs = IntStream.range(0, documents.length)
                .filter(i -> i <= documents.length)
                .mapToObj(i -> {
                    var document = documents[i];
                    var mediaDocument = new InputMediaDocument(document);

                    mediaDocument.contentType(contentType);
                    if (i == documents.length - 1) {
                        mediaDocument.caption(messageText)
                                .parseMode(ParseMode.HTML);
                    }

                    return mediaDocument;
                })
                .toArray(InputMedia[]::new);

        var request = new SendMediaGroup(chatId, docs);

        MessagesResponse response = null;
        try {
            response = bot.execute(request);
        } catch (Exception e) {
            log.error("ERROR: ", e);
        }

        if (Objects.nonNull(response) && !response.isOk()) {
            log.error("Found mistake: code={}, description={}", response.errorCode(), response.description());
            throw new AppException("Error occurred: %s".formatted(objectMapper.writeValueAsString(response)));
        } else if (Objects.nonNull(response)) {
            Message[] messages = response.messages();
            if (Objects.nonNull(messages) && messages.length > 0) {
                var first = messages[0];
                log.info("Got success response: id={}, message={}", first.messageId(), first);
                return first.messageId();
            }
        }

        return -1;
    }

    @SneakyThrows
    public Response sendImagesWithMessage(@NonNull String chatId, @NonNull String messageText, @NonNull List<Image> images) {
        List<InputMediaPhoto> media = new ArrayList<>();
        List<File> toDelete = new ArrayList<>();

        for (int i = 0; i < images.size(); i++) {
            var image = images.get(i);
            if (Objects.isNull(image.getData()) || image.getData().length == 0) {
                throw new IllegalArgumentException("File doesn't exists: %s".formatted(image.getName()));
            }

            boolean isLast = (i >= images.size() - 1);
            String contentType = image.getContentType();
            var tempImage = File.createTempFile("machinum", "_tr.jpg");
            //TODO rewrite to new Thumbnail library with blurhash algorithm
            Thumbnails.of(new ByteArrayInputStream(image.getData()))
                    .scale(0.25)
                    .outputFormat("jpg")
                    .toFile(tempImage);

            toDelete.add(tempImage);

            var cover = new InputMediaPhoto(image.getData())
                    .thumbnail(tempImage)
                    .contentType(contentType);

            if (isLast) {
                cover.hasSpoiler(true)
                        .caption(trim(messageText, TELEGRAM_LIMIT))
                        .parseMode(ParseMode.HTML);
            }

            media.add(cover);
        }

        try {
            SendMediaGroup request = new SendMediaGroup(chatId, media.toArray(InputMediaPhoto[]::new));

            var response = bot.execute(request);

            if (!response.isOk()) {
                log.error("Found mistake: code={}, description={}", response.errorCode(), response.description());
                throw new AppException("Error occurred: %s".formatted(objectMapper.writeValueAsString(response)));
            } else {
                var jsonNode = objectMapper.valueToTree(response);
                var messageIds = Stream.of(response.messages()).map(Message::messageId).collect(Collectors.toList());

                return Response.of(messageIds, jsonNode);
            }
        } catch (Exception e) {
            log.error("Error executing send media group request: ", e);
            throw new AppException("Failed to execute send media group request", e);
        } finally {
            toDelete.forEach(File::delete);
        }
    }

    @SneakyThrows
    public Integer replyToMessage(String chatId, Integer messageId, String messageText) {
        SendMessage request = new SendMessage(chatId, messageText)
                .parseMode(ParseMode.HTML);

        if (Objects.nonNull(messageId)) {
            request.replyParameters(new ReplyParameters(messageId));
        }

        SendResponse response = null;
        try {
            response = bot.execute(request);
        } catch (Exception e) {
            log.error("ERROR: ", e);
        }

        if (Objects.nonNull(response) && !response.isOk()) {
            log.error("Found mistake: code={}, description={}", response.errorCode(), response.description());
            throw new AppException("Error occurred: %s".formatted(objectMapper.writeValueAsString(response)));
        } else if (Objects.nonNull(response)) {
            log.info("Got success response: id={}, message={}", response.message().messageId(), response.message());
        }

        if (Objects.nonNull(response)) {
            return response.message().messageId();
        } else {
            return -1;
        }
    }

    @Override
    public void close() throws Exception {
        bot.shutdown();
    }

    /* ============= */

    private String trim(@NonNull String messageText, int maxLength) {
        if (messageText.length() > maxLength) {
            return messageText.substring(0, maxLength) + "...";
        }

        return messageText;
    }

    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    public static class Response {

        List<Integer> messageIds;

        JsonNode payload;

        public static Response of(Integer messageId) {
            return of(messageId, null);
        }

        public static Response of(Integer messageId, JsonNode jsonNode) {
            return of(List.of(messageId), jsonNode);
        }

        public static Response of(List<Integer> messageId, JsonNode jsonNode) {
            return Response.builder()
                    .messageIds(messageId)
                    .payload(jsonNode)
                    .build();
        }

        public Integer messageId() {
            if (!getMessageIds().isEmpty()) {
                return getMessageIds().getLast();
            }

            return -1;
        }

    }

    public record AudioRecord(String filename, String title, byte[] content, Integer duration) {}

    @Value
    @Builder
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class AudioSender {

        private static final long MAX_REQUEST_SIZE_BYTES = 49 * 1024 * 1024;
        private static final int MAX_FILES_PER_GROUP = 10;

        String chatId;
        String messageText;
        String contentType;
        String performer;
        List<AudioRecord> audioRecords;
        byte[] thumbnail;
        ObjectMapper objectMapper;
        TelegramBot bot;

        List<List<AudioRecord>> audioChunks = new ArrayList<>();
        AtomicInteger replyMessageId = new AtomicInteger();
        AtomicReference<MessagesResponse> firstSuccessfulResponse = new AtomicReference<>();

        public static AudioSender create(Function<AudioSender.AudioSenderBuilder, AudioSender.AudioSenderBuilder> creator) {
            return creator.apply(builder()).build() ;
        }

        public AudioSender validate() {
            if (audioRecords.isEmpty()) {
                throw new IllegalArgumentException("No files were provided, please use another method");
            }
            return this;
        }

        public AudioSender splitToChunks() {
            var currentChunk = new ArrayList<AudioRecord>();
            long currentChunkSizeBytes = 0;

            for (var audioRecord : audioRecords) {
                long fileSize = audioRecord.content().length;

                if (!currentChunk.isEmpty() &&
                        (currentChunk.size() >= MAX_FILES_PER_GROUP || currentChunkSizeBytes + fileSize > MAX_REQUEST_SIZE_BYTES)) {
                    audioChunks.add(currentChunk);
                    currentChunk = new ArrayList<>();
                    currentChunkSizeBytes = 0;
                }

                currentChunk.add(audioRecord);
                currentChunkSizeBytes += fileSize;
            }

            audioChunks.add(currentChunk);
            return this;
        }

        public AudioSender processChunks() {
            for (int i = 0; i < audioChunks.size(); i++) {
                var chunk = audioChunks.get(i);
                boolean isFirstChunk = (i == 0);

                var sendRequest = buildRequest(chunk, isFirstChunk, i);
                var response = executeRequestSafely(sendRequest, i);

                if (isFirstChunk) {
                    firstSuccessfulResponse.set(response);
                    replyMessageId.set(extractReplyMessageId(response));
                }

                log.info("Successfully sent media group chunk {}/{}.", i + 1, audioChunks.size());
            }
            return this;
        }

        public Response result() {
            int messageId = firstSuccessfulResponse.get().messages()[0].messageId();
            var jsonNode = objectMapper.valueToTree(firstSuccessfulResponse);
            log.info("All file chunks sent successfully. The primary message ID is {}.", messageId);
            return Response.of(messageId, jsonNode);
        }

        private SendMediaGroup buildRequest(List<AudioRecord> chunk, boolean isFirstChunk, int index) {
            var mediaDocuments = chunk.stream()
                    .map(audioRecord -> new InputMediaAudio(audioRecord.content())
                            .fileName(audioRecord.filename())
                            .contentType(contentType)
                            .performer(performer)
                            .title(audioRecord.title())
                            .duration(audioRecord.duration())
                            .thumbnail(TelegramThumbnailer.toThumbnail(thumbnail)))
                    .toArray(InputMedia[]::new);

            if (mediaDocuments.length > 0) {
                String caption;
                if(isFirstChunk) {
                    caption = messageText;
                } else {
                    caption = "%s/%s".formatted(index, audioChunks.size() - 1);
                }

                ((InputMediaAudio) mediaDocuments[mediaDocuments.length - 1])
                        .caption(caption).parseMode(ParseMode.HTML);
            }

            var request = new SendMediaGroup(chatId, mediaDocuments);

            if (replyMessageId.get() > -1) {
                request.replyParameters(new ReplyParameters(replyMessageId.get(), chatId))
                        .disableNotification(true);
            }

            return request;
        }

        @SneakyThrows
        private MessagesResponse executeRequestSafely(SendMediaGroup request, int chunkIndex) {
            var response = bot.execute(request);

            if (!response.isOk()) {
                log.error("Failed to send media group chunk {}: code={}, description={}",
                        chunkIndex + 1, response.errorCode(), response.description());
                throw new AppException("Error occurred during chunked send: %s"
                        .formatted(objectMapper.writeValueAsString(response)));
            }

            return response;
        }

        private int extractReplyMessageId(MessagesResponse response) {
            return (response.messages() != null && response.messages().length > 0)
                    ? response.messages()[0].messageId()
                    : -1;
        }
    }

}
