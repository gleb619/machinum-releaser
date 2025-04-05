package machinum.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.request.*;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendMediaGroup;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.MessagesResponse;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

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
        log.info("Started telegram session");
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
    public Integer sendFileWithMessage(@NonNull String messageText, @NonNull String contentType, @NonNull File document) {
        return sendFileWithMessage(defaultChatId, messageText, contentType, document);
    }

    @SneakyThrows
    public Integer sendFileWithMessage(@NonNull String chatId, @NonNull String messageText, @NonNull String contentType, @NonNull File document) {
        if (!document.exists()) {
            throw new IllegalArgumentException("File doesn't exists: %s".formatted(document.getName()));
        }

        SendDocument request = new SendDocument(chatId, document).contentType(contentType)
                .caption(messageText)
                .parseMode(ParseMode.HTML)
                .allowSendingWithoutReply(false);

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
    public Integer sendImagesWithMessage(@NonNull String messageText, @NonNull File... images) {
        return sendImagesWithMessage(defaultChatId, messageText, images);
    }

    @SneakyThrows
    public Integer sendImagesWithMessage(@NonNull String chatId, @NonNull String messageText, @NonNull File... images) {
        List<InputMediaPhoto> media = new ArrayList<>();

        for (File image : images) {
            if (!image.exists()) {
                throw new IllegalArgumentException("File doesn't exists: %s".formatted(image.getName()));
            }

            boolean isLast = media.size() == images.length - 2;
            String contentType = Files.probeContentType(image.toPath());
            //TODO fix images
//            File tempImage = TriangleWrapper.triangleThumbnail(image);
//            File thumbnail = File.createTempFile("machinum", "_thumb.jpg");
//            Thumbnails.of(tempImage)
//                    .scale(0.25)
//                    .outputFormat("jpg")
//                    .toFile(thumbnail);
//
//            tempImage.deleteOnExit();
//            thumbnail.deleteOnExit();
//
//            if(isLast) {
//                InputMediaPhoto cover = new InputMediaPhoto(image)
//                        .thumbnail(thumbnail)
//                        .contentType(contentType)
//                        .caption(messageText)
//                        .parseMode(ParseMode.HTML);
//                media.add(cover);
//            } else {
//                InputMediaPhoto cover = new InputMediaPhoto(image)
//                        .thumbnail(thumbnail)
//                        .contentType(contentType)
//                        .hasSpoiler(true);
//                media.add(cover);
//            }
        }

        SendMediaGroup request = new SendMediaGroup(chatId, media.toArray(InputMediaPhoto[]::new))
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
            for (Message message : messages) {
                log.info("Got success response: id={}, message={}", message.messageId(), message);
            }
        }

        if (response.messages().length > 0) {
            return response.messages()[0].messageId();
        } else {
            return null;
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

}
