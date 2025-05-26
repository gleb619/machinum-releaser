package machinum.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.*;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendMediaGroup;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.MessagesResponse;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import machinum.image.Image;
import machinum.image.TriangleWrapper;
import net.coobird.thumbnailator.Thumbnails;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
public class TelegramClient implements AutoCloseable {

    private final TelegramBot bot;
    @Getter
    private final String defaultChatId;
    private final ObjectMapper objectMapper;

    public TelegramClient(String token, String chatId, ObjectMapper objectMapper) {
        this.bot = new TelegramBot(token);
        this.defaultChatId = chatId;
        this.objectMapper = objectMapper;
        log.debug("Started telegram session");
    }

    @SneakyThrows
    public Integer sendMessage(@NonNull String messageText) {
        return sendMessage(defaultChatId, messageText);
    }

    @SneakyThrows
    public Integer sendMessage(@NonNull String chatId, @NonNull String messageText) {
        return replyToMessage(chatId, null, messageText);
    }

    @SneakyThrows
    public Response sendFileWithMessage(@NonNull String messageText, @NonNull String contentType, String fileName, @NonNull byte[] document) {
        return sendFileWithMessage(defaultChatId, messageText, contentType, fileName, document);
    }

    @SneakyThrows
    public Response sendFileWithMessage(@NonNull String chatId, @NonNull String messageText,
                                        @NonNull String contentType, String fileName, byte[] document) {
        if (document.length <= 0) {
            throw new IllegalArgumentException("File doesn't exists");
        }

        SendDocument request = new SendDocument(chatId, document)
                .fileName(fileName)
                .contentType(contentType)
                .caption(messageText)
                .parseMode(ParseMode.HTML)
                .allowSendingWithoutReply(false);

        var response = bot.execute(request);

        if (!response.isOk()) {
            log.error("Found mistake: code={}, description={}", response.errorCode(), response.description());
            throw new IllegalStateException("Error occurred: %s".formatted(objectMapper.writeValueAsString(response)));
        } else {
            log.info("Got success response: id={}, message={}", response.message().messageId(), response.message());
            var jsonNode = objectMapper.valueToTree(response);

            return Response.of(response.message().messageId(), jsonNode);
        }
    }

    @SneakyThrows
    public Integer sendFilesWithMessage(@NonNull String messageText, @NonNull String contentType, @NonNull File... documents) {
        return sendFilesWithMessage(defaultChatId, messageText, contentType, documents);
    }

    @SneakyThrows
    public Integer sendFilesWithMessage(@NonNull String chatId, @NonNull String messageText, @NonNull String contentType, @NonNull File... documents) {
        if (documents.length == 0) {
            throw new IllegalArgumentException("No files were provided, please use another method");
        }

        for (File document : documents) {
            if (!document.exists()) {
                throw new IllegalArgumentException("File doesn't exists: %s".formatted(document.getName()));
            }
        }

        var docs = IntStream.range(0, documents.length)
                .filter(i -> i <= documents[i].length())
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

        var request = new SendMediaGroup(chatId, docs)
                .allowSendingWithoutReply(false);

        MessagesResponse response = null;
        try {
            response = bot.execute(request);
        } catch (Exception e) {
            log.error("ERROR: ", e);
        }

        if (Objects.nonNull(response) && !response.isOk()) {
            log.error("Found mistake: code={}, description={}", response.errorCode(), response.description());
            throw new IllegalStateException("Error occurred: %s".formatted(objectMapper.writeValueAsString(response)));
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
    public Response sendImagesWithMessage(@NonNull String messageText, @NonNull List<Image> images) {
        return sendImagesWithMessage(defaultChatId, messageText, images);
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
            var tempImage = TriangleWrapper.addTriangleEffect(image);
            Thumbnails.of(tempImage)
                    .scale(0.25)
                    .outputFormat("jpg")
                    .toFile(tempImage);

            toDelete.add(tempImage);

            var cover = new InputMediaPhoto(image.getData())
                    .thumbnail(tempImage)
                    .contentType(contentType);

            if (isLast) {
                cover.hasSpoiler(true)
                        .caption(trim(messageText, 9_000))
                        .parseMode(ParseMode.HTML);
            }

            media.add(cover);
        }

        try {
            SendMediaGroup request = new SendMediaGroup(chatId, media.toArray(InputMediaPhoto[]::new))
                    .allowSendingWithoutReply(false);

            var response = bot.execute(request);

            if (!response.isOk()) {
                log.error("Found mistake: code={}, description={}", response.errorCode(), response.description());
                throw new IllegalStateException("Error occurred: %s".formatted(objectMapper.writeValueAsString(response)));
            } else {
                var jsonNode = objectMapper.valueToTree(response);
                var messageIds = Stream.of(response.messages()).map(Message::messageId).collect(Collectors.toList());

                return Response.of(messageIds, jsonNode);
            }
        } catch (Exception e) {
            log.error("Error executing send media group request: ", e);
            throw new IllegalStateException("Failed to execute send media group request", e);
        } finally {
            toDelete.forEach(File::delete);
        }
    }

    public Integer replyToMessage(Integer messageId, String messageText) {
        return replyToMessage(defaultChatId, messageId, messageText);
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
            throw new IllegalStateException("Error occurred: %s".formatted(objectMapper.writeValueAsString(response)));
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
                return getMessageIds().get(getMessageIds().size() - 1);
            }

            return -1;
        }

    }

}
